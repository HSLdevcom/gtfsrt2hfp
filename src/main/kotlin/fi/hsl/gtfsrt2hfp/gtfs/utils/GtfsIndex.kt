package fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.gtfs.utils

import fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.gtfs.GtfsFeed
import fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.gtfs.model.*
import fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.gtfs.model.Calendar
import java.util.*

class GtfsIndex(stops: List<Stop>, routes: List<Route>, trips: List<Trip>, stopTimes: List<StopTime>, calendars: List<Calendar>?, calendarDates: List<CalendarDate>?) {
    constructor(gtfsFeed: GtfsFeed) : this(gtfsFeed.stops, gtfsFeed.routes, gtfsFeed. trips, gtfsFeed.stopTimes, gtfsFeed.calendars, gtfsFeed.calendarDates)

    val stopsById = stops.associateBy { stop -> stop.id }
    val routesById = routes.associateBy { route -> route.id }
    val tripsById = trips.associateBy { trip -> trip.tripId }

    val stopTimesByTripId: Map<String, NavigableSet<StopTime>> = stopTimes.groupBy { stopTime -> stopTime.tripId }.mapValues { stopTimes -> TreeSet(stopTimes.value) }

    val calendarsByServiceId = calendars?.associateBy { calendar -> calendar.serviceId } ?: emptyMap()
    val calendarDatesByServiceId = calendarDates?.groupBy { calendarDate -> calendarDate.serviceId } ?: emptyMap()

    private val serviceIds = calendarsByServiceId.keys + calendarDatesByServiceId.keys

    val serviceDatesByServiceId = serviceIds.associateWith { serviceId -> ServiceDates(calendarsByServiceId[serviceId], calendarDatesByServiceId[serviceId]) }
}