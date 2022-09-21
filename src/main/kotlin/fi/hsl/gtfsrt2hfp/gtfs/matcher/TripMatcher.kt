package fi.hsl.gtfsrt2hfp.gtfs.matcher

import fi.hsl.gtfsrt2hfp.gtfs.utils.GtfsIndex
import xyz.malkki.gtfs.model.StopTime
import xyz.malkki.gtfs.utils.GtfsDateFormat

//TODO: extract interface
class TripMatcher(private val gtfsIndexA: GtfsIndex, private val gtfsIndexB: GtfsIndex, private val routeMatcher: RouteMatcher) {
    companion object {
        private val NUM_SAME_STOP_TIMES = 3 //TODO: make this configurable
    }

    private val stopMatcher = StopCodeStopMatcher(gtfsIndexA, gtfsIndexB)

    fun matchTrip(tripId: String, startDate: String): String? {
        val trip = gtfsIndexA.tripsById[tripId] ?: return null

        val routeIdA = trip.routeId
        val routeIdB = routeMatcher.matchRoute(routeIdA).firstOrNull()

        val startDateLocalDate = GtfsDateFormat.parseFromString(startDate)

        val stopTimesA = gtfsIndexA.stopTimesByTripId[trip.tripId] ?: return null

        return gtfsIndexB.tripsById.values.find { tripB ->
            val stopTimesB = gtfsIndexB.stopTimesByTripId[tripB.tripId]!!
            return@find tripB.routeId == routeIdB
                    && gtfsIndexB.serviceDatesByServiceId[tripB.serviceId]?.dates?.contains(startDateLocalDate) == true
                    && fuzzyIsSameTrip(stopTimesA, stopTimesB)
        }?.tripId
    }

    private fun fuzzyIsSameTrip(stopTimesA: Collection<StopTime>, stopTimesB: Collection<StopTime>): Boolean {
        var matchedCount = 0

        stopTimesA.forEach { stopTimeA ->
            val matchFound = stopTimesB.find { stopTimeB ->
                (stopTimeA.arrivalTime == stopTimeB.arrivalTime || stopTimeA.departureTime == stopTimeB.departureTime) && stopMatcher.matchStop(stopTimeA.stopId).contains(stopTimeB.stopId)
            } != null

            if (matchFound && ++matchedCount >= NUM_SAME_STOP_TIMES) {
                return true
            }
        }

        return false
    }
}