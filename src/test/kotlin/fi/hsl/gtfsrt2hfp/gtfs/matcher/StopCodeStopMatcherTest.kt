package fi.hsl.gtfsrt2hfp.gtfs.matcher

import fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.gtfs.matcher.StopCodeStopMatcher
import fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.gtfs.matcher.StopMatcher
import fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.gtfs.model.Stop
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
                "1" to Stop("1", "Stop_1", "S1"),
                "2" to Stop("2", "Stop_2", "S2"),
                "3" to Stop("3", "Stop_3", "S3")
            ),
            mapOf(
                "0001" to Stop("0001", "stop-1", "S1"),
                "0001-1" to Stop("0001-1", "stop-1", "S1"),
                "0002" to Stop("0002", "stop-2", "S2"),
                "0003" to Stop("0003", "stop-3", "S3")
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