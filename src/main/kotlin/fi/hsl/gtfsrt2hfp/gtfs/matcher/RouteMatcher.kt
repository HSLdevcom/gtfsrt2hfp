package fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.gtfs.matcher

interface RouteMatcher {
    /**
     * Route ID from GTFS feed A
     * @return List of possible routes IDs from GTFS feed B
     */
    fun matchRoute(routeId: String): List<String>
}