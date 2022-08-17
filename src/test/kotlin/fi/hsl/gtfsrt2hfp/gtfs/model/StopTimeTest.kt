package fi.hsl.gtfsrt2hfp.gtfs.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StopTimeTest {
    @Test
    fun `Test StopTimes are sorted correctly by trip ID and stop sequence`() {
        val list = listOf(
            StopTime("1", 0, 0, "1", 0),
            StopTime("1", 1, 1, "2", 1),
            StopTime("1", 2, 2, "3", 2),
            StopTime("2", 0, 0, "10", 0),
            StopTime("2", 1, 1, "11", 1),
            StopTime("2", 2, 2, "12", 2),
        ).shuffled().sorted()

        assertEquals(StopTime("1", 0, 0, "1", 0), list.first())
        assertEquals(StopTime("2", 2, 2, "12", 2), list.last())
    }
}