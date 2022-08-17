package fi.hsl.gtfsrt2hfp.gtfs.utils

import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class GtfsDateTimeParserTest {
    @Test
    fun `Parse valid time`() {
        val time = "10:15:15"

        val seconds = parseGtfsTime(time)

        assertEquals(36915, seconds)
    }

    @Test
    fun `Parse invalid time`() {
        assertThrows<IllegalArgumentException> { parseGtfsTime("fnsaiodnfa") }
        assertThrows<IllegalArgumentException> { parseGtfsTime("a:b:c") }
    }

    @Test
    fun `Parse valid date`() {
        val date = parseGtfsDate("20220101")

        assertEquals(LocalDate.of(2022, 1, 1), date)
    }
}