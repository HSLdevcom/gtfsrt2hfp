package fi.hsl.gtfsrt2hfp.hfp.utils

import fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.hfp.utils.formatHfpTime
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeFormatterTest {
    @Test
    fun `Test formatting HFP time`() {
        assertEquals("10:30", formatHfpTime(10 * 60 * 60 + 30 * 60))

        assertEquals("02:30", formatHfpTime(2 * 60 * 60 + 30 * 60))

        assertEquals("11:05", formatHfpTime(11 * 60 * 60 + 5 * 60))
    }
}