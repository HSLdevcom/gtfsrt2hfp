package fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.gtfs.matcher

import fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.gtfs.model.Route
import fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.gtfs.utils.GtfsIndex

/**
 *
 * @param compareDigitsOnly If only digits in the route short name are compared (e.g. if true, route with short name 280 would match with U280)
 */
class RouteShortNameRouteMatcher(private val routesByIdA: Map<String, Route>, private val routesByIdB: Map<String, Route>, private val compareDigitsOnly: Boolean = true) : RouteMatcher {
    constructor(gtfsIndexA: GtfsIndex, gtfsIndexB: GtfsIndex, compareDigitsOnly: Boolean = true) : this(gtfsIndexA.routesById, gtfsIndexB.routesById, compareDigitsOnly)

    companion object {
        private val NO_DIGIT_REGEX = Regex("\\D")
    }

    override fun matchRoute(routeId: String): List<String> {
        val routeA = routesByIdA[routeId] ?: return emptyList()

        return routesByIdB.values.filter { routeB ->
            if (compareDigitsOnly) {
                routeA.shortName.replace(NO_DIGIT_REGEX, "") == routeB.shortName.replace(NO_DIGIT_REGEX, "")
            } else {
                routeA.shortName == routeB.shortName
            }
        }.map { it.id }
    }
}