package fi.hsl.gtfsrt2hfp.gtfs.matcher

import xyz.malkki.gtfs.model.Stop
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StopCodeStopMatcherTest {
    private lateinit var stopMatcher: StopMatcher

    @BeforeTest
    fun setup() {
        stopMatcher = StopCodeStopMatcher(
            mapOf(
                "1" to Stop("1", "S1", "Stop_1", null, null, null, null, null, null, null, null, null, null, null),
                "2" to Stop("2", "S2", "Stop_2", null, null, null, null, null, null, null, null, null, null, null),
                    "3" to Stop("3", "S3", "Stop_3", null, null, null, null, null, null, null, null, null, null, null)
            ),
            mapOf(
                "0001" to Stop("0001", "S1", "stop-1", null, null, null, null, null, null, null, null, null, null, null),
                "0001-1" to Stop("0001-1", "S1", "stop-1", null, null, null, null, null, null, null, null, null, null, null),
                "0002" to Stop("0002", "S2", "stop-2", null, null, null, null, null, null, null, null, null, null, null),
                "0003" to Stop("0003", "S3", "stop-3", null, null, null, null, null, null, null, null, null, null, null)
            )
        )
    }

    @Test
    fun `Test matching stops are found`() {
        val stops = stopMatcher.matchStop("1")

        assertEquals(2, stops.size)
    }

    @Test
    fun `Test non-existing stops are not found`() {
        val stops = stopMatcher.matchStop("7")

        assertTrue(stops.isEmpty())
    }
}