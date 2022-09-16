package fi.hsl.gtfsrt2hfp.gtfs.utils

import xyz.malkki.gtfs.model.Calendar
import xyz.malkki.gtfs.model.CalendarDate

class ServiceDates(private val calendar: Calendar?, private val calendarDates: List<CalendarDate>?) {
    val dates by lazy {
        val dates = calendar?.toList()?.toMutableSet() ?: mutableSetOf()

        if (calendarDates != null) {
            for (calendarDate in calendarDates) {
                if (calendarDate.exceptionType == CalendarDate.EXCEPTION_TYPE_REMOVED) {
                    dates.remove(calendarDate.date)
                } else if (calendarDate.exceptionType == CalendarDate.EXCEPTION_TYPE_ADDED) {
                    dates.add(calendarDate.date)
                }
            }
        }

        return@lazy dates.toSortedSet()
    }
}