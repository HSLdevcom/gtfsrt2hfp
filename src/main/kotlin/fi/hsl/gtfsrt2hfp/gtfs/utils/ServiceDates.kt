package fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.gtfs.utils

import fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.gtfs.model.Calendar
import fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.gtfs.model.CalendarDate

class ServiceDates(private val calendar: Calendar?, private val calendarDates: List<CalendarDate>?) {
    val dates by lazy {
        val dates = calendar?.toList()?.toMutableSet() ?: mutableSetOf()

        if (calendarDates != null) {
            for (calendarDate in calendarDates) {
                if (calendarDate.exceptionType == 2) {
                    dates.remove(calendarDate.date)
                } else if (calendarDate.exceptionType == 1) {
                    dates.add(calendarDate.date)
                }
            }
        }

        return@lazy dates.toSortedSet()
    }
}