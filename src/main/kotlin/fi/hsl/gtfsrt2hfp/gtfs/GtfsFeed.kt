package fi.hsl.gtfsrt2hfp.gtfs

import xyz.malkki.gtfs.model.*


data class GtfsFeed(
    val stops: List<Stop>,
    val routes: List<Route>,
    val trips: List<Trip>,
    val stopTimes: List<StopTime>,
    val calendars: List<Calendar>?,
    val calendarDates: List<CalendarDate>?
)
