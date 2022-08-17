package fi.hsl.gtfsrt2hfp.gtfs.model

import java.time.LocalDate

data class CalendarDate(
    val serviceId: String,
    val date: LocalDate,
    val exceptionType: Int // 1 = service has been added for the specified date, 2 = service has been removed for the specified date
)
