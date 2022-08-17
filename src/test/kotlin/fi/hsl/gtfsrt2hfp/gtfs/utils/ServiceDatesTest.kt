package fi.hsl.gtfsrt2hfp.gtfs.utils

import fi.hsl.gtfsrt2hfp.gtfs.model.Calendar
import fi.hsl.gtfsrt2hfp.gtfs.model.CalendarDate
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertContentEquals

class ServiceDatesTest {
    @Test
    fun `Test date set contains correct dates`() {
        val serviceDates = ServiceDates(
            Calendar("1", false, false, false, false, true, true, true, LocalDate.of(2022,7, 11), LocalDate.of(2022, 7, 17)),
            listOf(
                CalendarDate("1", LocalDate.of(2022, 7, 11), 1),
                CalendarDate("1", LocalDate.of(2022, 7, 17), 2),
            )
        )

        val datesAsList = serviceDates.dates.toList()
        assertContentEquals(listOf(LocalDate.of(2022, 7, 11), LocalDate.of(2022, 7, 15), LocalDate.of(2022, 7, 16)), datesAsList)
    }
}