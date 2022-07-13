package fi.hsl.gtfsrt2hfp.hfp.utils

import fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.hfp.utils.getGeohash
import kotlin.test.Test
import kotlin.test.assertEquals

class GeohashTest {
    @Test
    fun `Test generating geohash`() {
        val geohash = getGeohash(60.1234, 24.1234)

        assertEquals("60;24/11/22/33", geohash)
    }
}