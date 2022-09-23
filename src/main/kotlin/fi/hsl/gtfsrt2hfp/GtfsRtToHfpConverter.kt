package fi.hsl.gtfsrt2hfp

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.transit.realtime.GtfsRealtime
import fi.hsl.gtfsrt2hfp.gtfs.matcher.*
import fi.hsl.gtfsrt2hfp.gtfs.utils.GtfsIndex
import fi.hsl.gtfsrt2hfp.gtfs.utils.location
import fi.hsl.gtfsrt2hfp.hfp.model.HfpPayload
import fi.hsl.gtfsrt2hfp.hfp.model.HfpTopic
import fi.hsl.gtfsrt2hfp.hfp.utils.GeohashCalculator
import fi.hsl.gtfsrt2hfp.hfp.utils.formatHfpTime
import fi.hsl.gtfsrt2hfp.hfp.utils.getGeohash
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
class GtfsRtToHfpConverter(private val operatorId: String, tripIdCacheDuration: Duration = Duration.ofMinutes(20), private val distanceBasedStopStatus: Boolean, private val maxDistanceFromStop: Double?) {
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

    private val latestTimestamp: Cache<Pair<String, String>, Long> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(1)) //TODO: this should be configurable
        .build()

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

    suspend fun createHfpForVehiclePosition(vehicle: GtfsRealtime.VehiclePosition): Pair<HfpTopic, HfpPayload>? = mutex.withLock {
        if (!hasGtfsData()) {
            throw IllegalStateException("No GTFS data available")
        }

        val operatorId = operatorId.padStart(4, '0')
        //TODO: find alternative ways to create HFP-compatible vehicle IDs
        val vehicleId = vehicle.vehicle.id.replace(NO_DIGIT_REGEX, "").takeLast(5).padStart(5, '0')

        val uniqueVehicleId = operatorId to vehicleId

        val latestTimestampForVehicle = latestTimestamp.getIfPresent(uniqueVehicleId)
        if (latestTimestampForVehicle != null && latestTimestampForVehicle >= vehicle.timestamp) {
            log.debug { "Vehicle timestamp (${vehicle.timestamp}) was not newer than previously published (${latestTimestampForVehicle}), ignoring vehicle position..." }
            return null
        }

        val tripId = tripIdCache.get(vehicle.trip.tripId to vehicle.trip.startDate).await()

        if (tripId != null) {
            val stopTimesA = gtfsIndexA!!.stopTimesByTripId[tripId]!!
            val stopTimesB = gtfsIndexB!!.stopTimesByTripId[vehicle.trip.tripId]!!

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

            val trip = gtfsIndexA!!.tripsById[tripId]!!
            val route = gtfsIndexA!!.routesById[trip.routeId]!!
            val firstStopTime = stopTimesA.first()

            val directionId = (trip.directionId?.plus(1))

            val timestamp = Instant.ofEpochSecond(vehicle.timestamp)

            val startTime = formatHfpTime(firstStopTime!!.departureTime!!)

            val firstPossibleNextStop = stopTimesB.find { stopTime -> stopTime.stopSequence == vehicle.currentStopSequence }
            //Add stops that are before the next stop in GTFS-RT to visited stops list
            stopTimesB.headSet(firstPossibleNextStop, false).map { it.stopId }.forEach { visitedStopsCache.addVisitedStop(uniqueVehicleId, tripId, it) }

            //Next stop from GTFS feed B that also has a corresponding stop present in GTFS feed A
            val nextStopTimeB = stopTimesB.tailSet(firstPossibleNextStop, true).find { matchedStopTimes[it.stopId] != null && !visitedStopsCache.hasVisitedStop(uniqueVehicleId, tripId, it.stopId) }
            val nextStopTimeA = matchedStopTimes[nextStopTimeB?.stopId]

            val nextStopB = gtfsIndexB!!.stopsById[nextStopTimeB?.stopId]
            val nextStopA = nextStopTimeA?.let { gtfsIndexA!!.stopsById[it.stopId] }

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
                nextStopTimeA?.stopId ?: "",
                geohashCalculator.getGeohashLevel(operatorId, vehicleId, vehicle.position.latitude.toDouble(), vehicle.position.longitude.toDouble(), nextStopTimeA?.stopId, trip.routeId, startTime, directionId.toString()),
                getGeohash(vehicle.position.latitude.toDouble(), vehicle.position.longitude.toDouble())
            )

            val stoppedAtCurrentStop = if (distanceBasedStopStatus) {
                nextStopA != null
                        && nextStopA.location != null
                        && vehicle.position.getLocation() != null
                        && nextStopA.location!!.distanceTo(vehicle.position.getLocation()!!) <= maxDistanceFromStop!!
            } else {
                nextStopTimeB?.stopSequence == vehicle.currentStopSequence &&
                        vehicle.currentStatus == GtfsRealtime.VehiclePosition.VehicleStopStatus.STOPPED_AT
            }

            if (stoppedAtCurrentStop) {
                nextStopB?.stopId?.let { visitedStopsCache.addVisitedStop(uniqueVehicleId, tripId, it) }
            }

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
                null,
                null,
                GtfsDateFormat.parseFromString(vehicle.trip.startDate).format(DateTimeFormatter.ISO_LOCAL_DATE),
                null,
                null,
                startTime,
                "GPS",
                if (stoppedAtCurrentStop) { nextStopA?.stopId } else { null },
                route.routeId,
                0
            )

            latestTimestamp.put(uniqueVehicleId, vehicle.timestamp)

            return hfpTopic to hfpPayload
        }

        return null
    }
}