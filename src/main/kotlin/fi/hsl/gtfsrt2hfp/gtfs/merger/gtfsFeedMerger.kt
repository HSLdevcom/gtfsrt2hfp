package fi.hsl.gtfsrt2hfp.gtfs.merger

import fi.hsl.gtfsrt2hfp.gtfs.GtfsFeed

private fun <E, K> List<E>.mergeWith(other: List<E>, keyFunction: (E) -> K): List<E> {
    val valuesByKey = other.associateBy(keyFunction).toMutableMap()
    this.forEach { valuesByKey[keyFunction(it)] = it }
    return valuesByKey.values.toList()
}

//TODO: handle cases where IDs are same but contents are different
fun GtfsFeed.mergeWith(other: GtfsFeed): GtfsFeed {
    return GtfsFeed(
        stops.mergeWith(other.stops) { it.stopId },
        routes.mergeWith(other.routes) { it.routeId },
        trips.mergeWith(other.trips) { it.tripId },
        stopTimes.mergeWith(other.stopTimes) { it.tripId to it.stopSequence },
        (calendars ?: emptyList()).mergeWith((other.calendars ?: emptyList())) { it.serviceId },
        (calendarDates ?: emptyList()).mergeWith((other.calendarDates ?: emptyList())) { it.serviceId }
    )
}