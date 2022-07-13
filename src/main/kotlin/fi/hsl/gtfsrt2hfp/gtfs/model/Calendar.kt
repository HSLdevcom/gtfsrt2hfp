package fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.gtfs.model

import java.time.DayOfWeek
import java.time.LocalDate

class Calendar(
    val serviceId: String,
    val monday: Boolean,
    val tuesday: Boolean,
    val wednesday: Boolean,
    val thursday: Boolean,
    val friday: Boolean,
    val saturday: Boolean,
    val sunday: Boolean,
    val startDate: LocalDate,
    val endDate: LocalDate) : Iterable<LocalDate> {

    val availableDaysOfWeek by lazy {
        setOfNotNull(
            if (monday) DayOfWeek.MONDAY else null,
            if (tuesday) DayOfWeek.TUESDAY else null,
            if (wednesday) DayOfWeek.WEDNESDAY else null,
            if (thursday) DayOfWeek.THURSDAY else null,
            if (friday) DayOfWeek.FRIDAY else null,
            if (saturday) DayOfWeek.SATURDAY else null,
            if (sunday) DayOfWeek.SUNDAY else null,
        )
    }

    override fun iterator(): Iterator<LocalDate> = object : Iterator<LocalDate> {
        private var current = findNext(startDate)

        private fun findNext(startDate: LocalDate): LocalDate? {
            var date = startDate
            while (date <= endDate) {
                if (date.dayOfWeek in availableDaysOfWeek) {
                    return date
                } else {
                    date = date.plusDays(1)
                }
            }

            return null
        }

        override fun hasNext(): Boolean = current != null

        override fun next(): LocalDate {
            val output = current!!
            current = findNext(output.plusDays(1))
            return output
        }
    }
}