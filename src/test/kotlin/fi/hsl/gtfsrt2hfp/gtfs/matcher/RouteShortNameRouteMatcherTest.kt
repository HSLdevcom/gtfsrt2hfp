package fi.hsl.gtfsrt2hfp.gtfs.matcher

import xyz.malkki.gtfs.model.Route
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RouteShortNameRouteMatcherTest {
    private lateinit var routeMatcher: RouteMatcher

    @BeforeTest
    fun setup() {
        routeMatcher = RouteShortNameRouteMatcher(
            mapOf("1" to Route("1", "U1", "R1", null, null, 1, null, null, null, null, null, null)),
            mapOf("abc" to Route("abc", "1", "R1", null, null, 1, null, null, null, null, null, null)))
    }

    @Test
    fun `Test matching route is found`() {
        val routes = routeMatcher.matchRoute("1")

        assertEquals(1, routes.size)
        assertEquals("abc", routes.first())
    }
}