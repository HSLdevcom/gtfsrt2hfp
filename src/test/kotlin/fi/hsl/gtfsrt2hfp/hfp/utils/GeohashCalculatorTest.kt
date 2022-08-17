package fi.hsl.gtfsrt2hfp.hfp.utils

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GeohashCalculatorTest {
    private lateinit var geohashCalculator: GeohashCalculator

    @BeforeTest
    fun setup() {
        geohashCalculator = GeohashCalculator()
    }

    @Test
    fun `Test geohash calculator with different integer coordinates`() {
        val initial = geohashCalculator.getGeohashLevel("1", "1", 60.1, 24.0, "1", "1", "10:00", "1")
        assertEquals(0, initial)

        val level = geohashCalculator.getGeohashLevel("1", "1", 61.2, 24.0, "1", "1", "10:00", "1")
        assertEquals(0, level)
    }

    @Test
    fun `Test geohash calculator with different decimal coordinates`() {
        val initial = geohashCalculator.getGeohashLevel("1", "1", 65.521516, 24.512616, "1", "1", "10:00", "1")
        assertEquals(0, initial)

        val first = geohashCalculator.getGeohashLevel("1", "1", 65.523516, 24.512616, "1", "1", "10:00", "1")
        assertEquals(3, first)

        val second = geohashCalculator.getGeohashLevel("1", "1", 65.523516, 24.512617, "1", "1", "10:00", "1")
        assertEquals(5, second)

        val third =  geohashCalculator.getGeohashLevel("1", "1", 65.423516, 24.512617, "1", "1", "10:00", "1")
        assertEquals(1, third)
    }

    @Test
    fun `Test geohash calculator with different stop`() {
        geohashCalculator.getGeohashLevel("1", "1", 60.1, 24.0, "1", "1", "10:00", "1")
        val level = geohashCalculator.getGeohashLevel("1", "1", 60.2, 24.0, "2", "1", "10:00", "1")
        assertEquals(0, level)
    }
}