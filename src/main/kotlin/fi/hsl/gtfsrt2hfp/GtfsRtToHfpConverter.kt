package fi.hsl.gtfsrt2hfp

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.transit.realtime.GtfsRealtime.VehiclePosition
import fi.hsl.gtfsrt2hfp.gtfs.matcher.*
import fi.hsl.gtfsrt2hfp.gtfs.utils.GtfsIndex
import fi.hsl.gtfsrt2hfp.gtfs.utils.location
import fi.hsl.gtfsrt2hfp.hfp.model.HfpPayload
import fi.hsl.gtfsrt2hfp.hfp.model.HfpTopic
import fi.hsl.gtfsrt2hfp.hfp.utils.GeohashCalculator
import fi.hsl.gtfsrt2hfp.hfp.utils.formatHfpTime
import fi.hsl.gtfsrt2hfp.hfp.utils.getGeohash
import fi.hsl.gtfsrt2hfp.utils.OdometerCalibrator
import fi.hsl.gtfsrt2hfp.utils.VisitedStopsCache
import fi.hsl.gtfsrt2hfp.utils.getLocation
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import xyz.malkki.gtfs.utils.GtfsDateFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val log = KotlinLogging.logger {}

/**
 * @param operatorId Operator ID which is used in the HFP message
 */
class GtfsRtToHfpConverter(private val operatorId: String, tripIdCacheDuration: Duration = Duration.ofMinutes(20), private val distanceBasedStopStatus: Boolean, private val maxDistanceFromStop: Double?, private val maxSpeedWhenStopped: Double?) {
    companion object {
        private val NO_DIGIT_REGEX = Regex("\\D")
        private val TST_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")
    }

    private val mutex = Mutex()

    private var gtfsIndexA: GtfsIndex? = null
    private var gtfsIndexB: GtfsIndex? = null

    private var routeMatcher: RouteMatcher? = null
    private var tripMatcher: TripMatcher? = null
    private var stopMatcher: StopMatcher? = null

    private val geohashCalculator = GeohashCalculator()

    //Computing matching trip IDs is expensive, so let's cache them
    private val tripIdCache: AsyncLoadingCache<Pair<String, String>, String> = Caffeine.newBuilder()
        .expireAfterAccess(tripIdCacheDuration)
        .buildAsync { (tripId, startDate) -> tripMatcher!!.matchTrip(tripId, startDate) }

    private val visitedStopsCache = VisitedStopsCache()
    private val stoppedStopsCache = VisitedStopsCache()

    //Cache previous positions to be able to filter duplicate positions, calculate average speeds and calibrate odometers
    private val previousPositionsCache: Cache<Pair<String, String>, VehiclePosition> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(5)) //TODO: this should be configurable
        .build()

    private val odometerCalibrator = OdometerCalibrator()

    fun hasGtfsData(): Boolean = gtfsIndexA != null && gtfsIndexB != null

    suspend fun updateGtfsData(gtfsIndexA: GtfsIndex, gtfsIndexB: GtfsIndex) = mutex.withLock {
        log.info { "Updating GTFS data" }

        this.gtfsIndexA = gtfsIndexA
        this.gtfsIndexB = gtfsIndexB

        routeMatcher = RouteShortNameRouteMatcher(gtfsIndexB, gtfsIndexA)
        tripMatcher = TripMatcher(gtfsIndexB, gtfsIndexA, routeMatcher!!)
        stopMatcher = StopCodeStopMatcher(gtfsIndexB, gtfsIndexA)

        //Clear cache when GTFS data is updated, because trip IDs might have changed
        tripIdCache.synchronous().invalidateAll()
    }

    suspend fun createHfpForVehiclePosition(vehicle: VehiclePosition): Pair<HfpTopic, HfpPayload>? = mutex.withLock {
        if (!hasGtfsData()) {
            throw IllegalStateException("No GTFS data available")
        }

        val operatorId = operatorId.padStart(4, '0')
        //TODO: find alternative ways to create HFP-compatible vehicle IDs
        val vehicleId = vehicle.vehicle.id.replace(NO_DIGIT_REGEX, "").takeLast(5).padStart(5, '0')

        val uniqueVehicleId = operatorId to vehicleId

        val previousPosition = previousPositionsCache.getIfPresent(uniqueVehicleId)
        val latestTimestampForVehicle = previousPosition?.timestamp
        if (latestTimestampForVehicle != null && latestTimestampForVehicle >= vehicle.timestamp) {
            log.debug { "Vehicle timestamp for vehicle $uniqueVehicleId (${vehicle.timestamp}) was not newer than previously published (${latestTimestampForVehicle}), ignoring vehicle position..." }
            return null
        }

        val tripId = tripIdCache.get(vehicle.trip.tripId to vehicle.trip.startDate).await()

        if (tripId != null) {
            val stopTimesA = gtfsIndexA!!.stopTimesByTripId[tripId]!!
            val stopTimesB = gtfsIndexB!!.stopTimesByTripId[vehicle.trip.tripId]!!

            val trip = gtfsIndexA!!.tripsById[tripId]!!
            val route = gtfsIndexA!!.routesById[trip.routeId]!!

            val directionId = (trip.directionId?.plus(1))

            val timestamp = Instant.ofEpochSecond(vehicle.timestamp)

            val firstStopTime = stopTimesA.first()
            val startTime = formatHfpTime(firstStopTime!!.departureTime!!)

            /*
             * Find matching stop times
             * This is needed because other GTFS feed might contain stops that the other doesn't
             * (e.g. Matkahuolto GTFS contains stops outside HSL area)
             */
            val matchedStopTimes = stopTimesB.associateBy(
                keySelector = { it.stopId },
                valueTransform = { stopTimeB ->
                    val stopIdsA = stopMatcher!!.matchStop(stopTimeB.stopId)

                    stopTimesA.find { stopTimeA -> stopIdsA.contains(stopTimeA.stopId) }
                }
            )

            val firstPossibleNextStop = stopTimesB.find { stopTime -> stopTime.stopSequence >= vehicle.currentStopSequence }
            if (firstPossibleNextStop == null) {
                log.warn { "No next possible stop found for vehicle ${vehicle.vehicle.id}, stop seq: ${vehicle.currentStopSequence}, stops of the trip: ${stopTimesB.joinToString { "${it.stopSequence}: ${it.stopId}" }}" }
                return null
            }

            //Add stops that are before the next stop in GTFS-RT to visited stops list
            stopTimesB.headSet(firstPossibleNextStop, false).map { it.stopId }.forEach { visitedStopsCache.addVisitedStop(uniqueVehicleId, tripId, it) }

            //Current and next stop from GTFS feed B that also has a corresponding stop present in GTFS feed A
            //Current stop = stop according to GTFS-RT current_stop_sequence
            //Next stop = next stop of the trip which has not yet been visited
            val currentStopTimeB = stopTimesB.tailSet(firstPossibleNextStop, true).find { matchedStopTimes[it.stopId] != null }
            val nextStopTimeB = stopTimesB.tailSet(firstPossibleNextStop, true).find {
                matchedStopTimes[it.stopId] != null
                        && !visitedStopsCache.hasVisitedStop(uniqueVehicleId, tripId, it.stopId)
            }

            val currentStopTimeA = matchedStopTimes[currentStopTimeB?.stopId]
            val nextStopTimeA = matchedStopTimes[nextStopTimeB?.stopId]

            val currentStopB = gtfsIndexB!!.stopsById[currentStopTimeB?.stopId]
            val currentStopA = gtfsIndexA!!.stopsById[currentStopTimeA?.stopId]
            val nextStopA = gtfsIndexA!!.stopsById[nextStopTimeA?.stopId]

            val nearCurrentStop =  if (distanceBasedStopStatus) {
                currentStopA != null
                        && currentStopA.location != null
                        && vehicle.position.getLocation() != null
                        && currentStopA.location!!.distanceTo(vehicle.position.getLocation()!!) <= maxDistanceFromStop!!
            } else {
                currentStopTimeB?.stopSequence == vehicle.currentStopSequence &&
                        vehicle.currentStatus == VehiclePosition.VehicleStopStatus.STOPPED_AT
            }

            val stoppedAtCurrentStop = nearCurrentStop &&
                    (!distanceBasedStopStatus //If not using distance based stop status, nearCurrentStop is true only if vehicle is stopped at the stop
                            || (firstStopTime.stopId == currentStopA?.stopId //Create stop event for the first stop even if the vehicle is not stopped to make sure that it gets displayed in Reittiloki
                                || (vehicle.position.hasSpeed() && vehicle.position.speed <= maxSpeedWhenStopped!!)))

            currentStopB?.stopId?.let {
                if (nearCurrentStop) {
                    visitedStopsCache.addVisitedStop(uniqueVehicleId, tripId, it)
                }
                if (stoppedAtCurrentStop) {
                    stoppedStopsCache.addVisitedStop(uniqueVehicleId, tripId, it)
                }
            }

            val hfpNextStopId = if (stoppedAtCurrentStop) {
                //If vehicle is stopped at current stop, use current stop ID in the topic
                currentStopA?.stopId
            } else if (nearCurrentStop && currentStopB != null && !stoppedStopsCache.hasVisitedStop(uniqueVehicleId, tripId, currentStopB.stopId)) {
                //If near current stop, but not yet stopped at current stop, use current stop ID in the topic
                currentStopA?.stopId
            } else {
                //By default use current stop ID
                nextStopA?.stopId
            }

            val hfpTopic = HfpTopic(
                HfpTopic.HFP_V2_PREFIX,
                HfpTopic.JourneyType.JOURNEY,
                HfpTopic.TemporalType.ONGOING,
                HfpTopic.EventType.VP,
                HfpTopic.TransportMode.UBUS,
                operatorId,
                vehicleId,
                trip.routeId,
                directionId.toString(),
                trip.tripHeadsign ?: "",
                startTime,
                hfpNextStopId,
                geohashCalculator.getGeohashLevel(operatorId, vehicleId, vehicle.position.latitude.toDouble(), vehicle.position.longitude.toDouble(), nextStopTimeA?.stopId, trip.routeId, startTime, directionId.toString()),
                getGeohash(vehicle.position.latitude.toDouble(), vehicle.position.longitude.toDouble())
            )

            val odoInKilometres = previousPosition?.let { odometerCalibrator.calibrateOdometer(it, vehicle) }

            val hfpPayload = HfpPayload(
                route.routeShortName!!,
                directionId.toString(),
                operatorId.toInt(),
                vehicleId.toInt(),
                TST_FORMATTER.format(timestamp.atZone(ZoneId.of("UTC"))),
                timestamp.epochSecond,
                vehicle.position.speed.toDouble(),
                vehicle.position.bearing.roundToInt(),
                vehicle.position.latitude.toDouble(),
                vehicle.position.longitude.toDouble(),
                0.0,
                null,
                if (odoInKilometres == true) { vehicle.position.odometer * 1000 } else if (odoInKilometres == false) { vehicle.position.odometer } else { null },
                null,
                GtfsDateFormat.parseFromString(vehicle.trip.startDate).format(DateTimeFormatter.ISO_LOCAL_DATE),
                null,
                null,
                startTime,
                "GPS",
                if (stoppedAtCurrentStop) { currentStopA?.stopId } else { null },
                route.routeId,
                0
            )

            previousPositionsCache.put(uniqueVehicleId, vehicle)

            return hfpTopic to hfpPayload
        }

        return null
    }
}