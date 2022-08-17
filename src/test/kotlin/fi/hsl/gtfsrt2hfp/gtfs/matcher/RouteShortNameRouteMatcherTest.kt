package fi.hsl.gtfsrt2hfp.gtfs.matcher

import fi.hsl.gtfsrt2hfp.gtfs.model.Route
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RouteShortNameRouteMatcherTest {
    private lateinit var routeMatcher: RouteMatcher

    @BeforeTest
    fun setup() {
        routeMatcher = RouteShortNameRouteMatcher(mapOf("1" to Route("1", "U1", "")), mapOf("abc" to Route("abc", "1", "")))
    }

    @Test
    fun `Test matching route is found`() {
        val routes = routeMatcher.matchRoute("1")

        assertEquals(1, routes.size)
        assertEquals("abc", routes.first())
    }
}