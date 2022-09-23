package fi.hsl.gtfsrt2hfp.utils

import java.time.Duration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisitedStopsCacheTest {
    private lateinit var visitedStopsCache: VisitedStopsCache

    @BeforeTest
    fun init() {
        visitedStopsCache = VisitedStopsCache(Duration.ofSeconds(15))
    }

    @Test
    fun `Test stops can be saved to visited stops cache`() {
        visitedStopsCache.addVisitedStop("1" to "1", "trip_1", "stop_1")

        assertTrue(visitedStopsCache.hasVisitedStop("1" to "1", "trip_1", "stop_1"))
    }

    @Test
    fun `Test stops not saved to the cache are not found from cache`() {
        assertFalse(visitedStopsCache.hasVisitedStop("1" to "1", "trip_1", "stop_1"))
    }
}