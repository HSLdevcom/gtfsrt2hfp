package fi.hsl.gtfsrt2hfp.gtfs.model

import fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.gtfs.model.Calendar
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertContentEquals

class CalendarTest {
    @Test
    fun `Test calendar iterator`() {
        val calendar = Calendar("test",
            monday = true,
            tuesday = true,
            wednesday = false,
            thursday = false,
            friday = true,
            saturday = true,
            sunday = false,
            startDate = LocalDate.of(2022, 6, 20),
            endDate = LocalDate.of(2022, 6, 26)
        )

        val dates = calendar.toList()

        val expected = listOf(LocalDate.of(2022, 6, 20), LocalDate.of(2022, 6, 21), LocalDate.of(2022, 6, 24), LocalDate.of(2022, 6, 25))
        assertContentEquals(expected, dates)
    }
}