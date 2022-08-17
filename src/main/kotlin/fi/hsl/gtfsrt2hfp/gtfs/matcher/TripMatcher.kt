package fi.hsl.gtfsrt2hfp.gtfs.matcher

import fi.hsl.gtfsrt2hfp.gtfs.utils.GtfsIndex
import java.time.LocalDate
import java.time.format.DateTimeFormatter

//TODO: extract interface
class TripMatcher(private val gtfsIndexA: GtfsIndex, private val gtfsIndexB: GtfsIndex, private val routeMatcher: RouteMatcher) {
    private val stopCodeStopMatcher = StopCodeStopMatcher(gtfsIndexA, gtfsIndexB)

    fun matchTrip(tripId: String, startDate: String): String? {
        val trip = gtfsIndexA.tripsById[tripId] ?: return null

        val stopTimes = gtfsIndexA.stopTimesByTripId[trip.tripId]
        val stopTimesInB = stopTimes?.mapNotNull { stopTime ->
            val stopIdB = stopCodeStopMatcher.matchStop(stopTime.stopId).toSet()
            if (stopIdB.isEmpty()) { null } else { stopIdB to stopTime.departureTime }
        } ?: emptyList()

        val routeIdA = trip.routeId
        val routeIdB = routeMatcher.matchRoute(routeIdA).firstOrNull()

        val startDateLocalDate = LocalDate.parse(startDate, DateTimeFormatter.ofPattern("yyyyMMdd"))

        return gtfsIndexB.tripsById.values.find { trip ->
            val stopTimes = gtfsIndexB.stopTimesByTripId[trip.tripId]!!
            return@find trip.routeId == routeIdB
                    && gtfsIndexB.serviceDatesByServiceId[trip.serviceId]?.dates?.contains(startDateLocalDate) == true
                    && stopTimes.first().stopId in stopTimesInB.first().first
                    && stopTimes.first().departureTime == stopTimesInB.first().second
        }?.tripId
    }
}