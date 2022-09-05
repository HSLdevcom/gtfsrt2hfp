package fi.hsl.gtfsrt2hfp

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.transit.realtime.GtfsRealtime
import fi.hsl.gtfsrt2hfp.gtfs.matcher.*
import fi.hsl.gtfsrt2hfp.gtfs.utils.GtfsIndex
import fi.hsl.gtfsrt2hfp.gtfs.utils.parseGtfsDate
import fi.hsl.gtfsrt2hfp.hfp.model.HfpPayload
import fi.hsl.gtfsrt2hfp.hfp.model.HfpTopic
import fi.hsl.gtfsrt2hfp.hfp.utils.GeohashCalculator
import fi.hsl.gtfsrt2hfp.hfp.utils.formatHfpTime
import fi.hsl.gtfsrt2hfp.hfp.utils.getGeohash
import fi.hsl.gtfsrt2hfp.utils.getLocation
import kotlinx.coroutines.future.await
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * @param operatorId Operator ID which is used in the HFP message
 */
class GtfsRtToHfpConverter(private val operatorId: String, tripIdCacheDuration: Duration = Duration.ofMinutes(20), private val distanceBasedStopStatus: Boolean, private val maxDistanceFromStop: Double?) {
    companion object {
        private val NO_DIGIT_REGEX = Regex("\\D")
        private val TST_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")
    }

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

    fun hasGtfsData(): Boolean = gtfsIndexA != null && gtfsIndexB != null

    fun updateGtfsData(gtfsIndexA: GtfsIndex, gtfsIndexB: GtfsIndex) {
        this.gtfsIndexA = gtfsIndexA
        this.gtfsIndexB = gtfsIndexB

        routeMatcher = RouteShortNameRouteMatcher(gtfsIndexB, gtfsIndexA)
        tripMatcher = TripMatcher(gtfsIndexB, gtfsIndexA, routeMatcher!!)
        stopMatcher = StopCodeStopMatcher(gtfsIndexB, gtfsIndexA)

        //Clear cache when GTFS data is updated, because trip IDs might have changed
        tripIdCache.synchronous().invalidateAll()
    }

    suspend fun createHfpForVehiclePosition(vehicle: GtfsRealtime.VehiclePosition): Pair<HfpTopic, HfpPayload>? {
        if (!hasGtfsData()) {
            throw IllegalStateException("No GTFS data available")
        }

        val tripId = tripIdCache.get(vehicle.trip.tripId to vehicle.trip.startDate).await()

        if (tripId != null) {
            val stopTimesA = gtfsIndexA!!.stopTimesByTripId[tripId]!!
            val stopTimesB = gtfsIndexB!!.stopTimesByTripId[vehicle.trip.tripId]!!

            /*
             * Find matching stop times by arrival and departure times
             * This is needed because other GTFS feed might contain stops that the other doesn't
             * (e.g. Matkahuolto GTFS contains stops outside HSL area)
             */
            val matchedStopTimes = stopTimesB.associateBy(
                keySelector = { it.stopId },
                valueTransform = { stopTimeB ->
                    stopTimesA.find { stopTimeA -> stopTimeA.departureTime == stopTimeB.departureTime || stopTimeA.arrivalTime == stopTimeB.arrivalTime }
                }
            )

            val trip = gtfsIndexA!!.tripsById[tripId]!!
            val route = gtfsIndexA!!.routesById[trip.routeId]!!
            val firstStopTime = stopTimesA.first()

            val directionId = (trip.directionId?.plus(1))

            val operatorId = operatorId.padStart(4, '0')
            //TODO: find alternative ways to create HFP-compatible vehicle IDs
            val vehicleId = vehicle.vehicle.id.replace(NO_DIGIT_REGEX, "").takeLast(5).padStart(5, '0')

            val timestamp = Instant.ofEpochSecond(vehicle.timestamp)

            val startTime = formatHfpTime(firstStopTime!!.departureTime)

            val firstPossibleNextStop = stopTimesB.find { stopTime -> stopTime.stopSequence == vehicle.currentStopSequence }
            //Next stop from GTFS feed B that also has a corresponding stop present in GTFS feed A
            val nextStopTimeB = stopTimesB.tailSet(firstPossibleNextStop, true).find { matchedStopTimes[it.stopId] != null }
            val nextStopTimeA = matchedStopTimes[nextStopTimeB?.stopId]

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
                trip.headsign,
                startTime,
                nextStopTimeA?.stopId ?: "",
                geohashCalculator.getGeohashLevel(operatorId, vehicleId, vehicle.position.latitude.toDouble(), vehicle.position.longitude.toDouble(), nextStopTimeA?.stopId, trip.routeId, startTime, directionId.toString()),
                getGeohash(vehicle.position.latitude.toDouble(), vehicle.position.longitude.toDouble())
            )

            val currentStop = if (distanceBasedStopStatus
                && nextStopA != null
                && nextStopA.location != null
                && vehicle.position.getLocation() != null
                && nextStopA.location.distanceTo(vehicle.position.getLocation()!!) <= maxDistanceFromStop!!) {
                nextStopA.id
            } else if (
                nextStopTimeB?.stopId == vehicle.stopId &&
                vehicle.currentStatus == GtfsRealtime.VehiclePosition.VehicleStopStatus.STOPPED_AT
            ) {
                nextStopTimeA?.stopId
            } else {
                null
            }

            val hfpPayload = HfpPayload(
                route.shortName,
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
                parseGtfsDate(vehicle.trip.startDate).format(DateTimeFormatter.ISO_LOCAL_DATE),
                null,
                null,
                startTime,
                "GPS",
                currentStop,
                route.id,
                0
            )

            return hfpTopic to hfpPayload
        }

        return null
    }
}