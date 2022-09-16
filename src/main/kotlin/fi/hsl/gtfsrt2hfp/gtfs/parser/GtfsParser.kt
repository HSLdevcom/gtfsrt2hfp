package fi.hsl.gtfsrt2hfp.gtfs.parser

import fi.hsl.gtfsrt2hfp.gtfs.GtfsFeed
import kotlinx.coroutines.*
import mu.KotlinLogging
import xyz.malkki.gtfs.serialization.GtfsFeedParser
import java.nio.file.Path
import java.util.function.Predicate
import kotlin.streams.toList
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

private val log = KotlinLogging.logger {}

@ExperimentalTime
class GtfsParser {
    private inline fun <T> getFilterPredicate(filterCollection: Collection<String>?, crossinline keyFunction: (T) -> String): Predicate<T> = Predicate { value ->
        return@Predicate filterCollection == null || keyFunction(value) in filterCollection
    }

    /**
     * @param path Path to the GTFS .zip file
     * @param filterByRouteIds Only include data that is relevant to routes with these IDs. If null, no filtering is done
     */
    suspend fun parseGtfsFeed(file: Path, filterByRouteIds: Collection<String>? = null): GtfsFeed = withContext(Dispatchers.IO) {
        log.info { "Parsing GTFS from ${file.toAbsolutePath()}" }

        val (gtfsFeed, duration) = measureTimedValue {
            GtfsFeedParser(file).use { parser ->
                val routes = async { parser.parseRoutes().filter(getFilterPredicate(filterByRouteIds) { it.routeId }).toList() }
                val trips = async { parser.parseTrips().filter(getFilterPredicate(filterByRouteIds) { it.routeId }).toList()  }

                val serviceIdFilter = if (filterByRouteIds == null) { null } else { trips.await().map { it.serviceId }.toSet() }
                val tripIdFilter = if (filterByRouteIds == null) { null } else { trips.await().map { it.tripId }.toSet() }

                val stopTimes = async { parser.parseStopTimes().filter(getFilterPredicate(tripIdFilter) { it.tripId }).toList() }

                val calendars = async { parser.parseCalendars().filter(getFilterPredicate(serviceIdFilter) { it.serviceId }).toList() }
                val calendarDates = async { parser.parseCalendarDates().filter(getFilterPredicate(serviceIdFilter) { it.serviceId }).toList() }

                val stopIdFilter = if (filterByRouteIds == null) { null } else { stopTimes.await().map { it.stopId }.toSet() }

                val stops = async { parser.parseStops().filter(getFilterPredicate(stopIdFilter) { it.stopId }).toList() }

                GtfsFeed(stops.await(), routes.await(), trips.await(), stopTimes.await(), calendars.await(), calendarDates.await())
            }
        }

        log.info { "GTFS parsed in ${duration.inWholeMilliseconds}ms" }

        return@withContext gtfsFeed
    }
}