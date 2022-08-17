package fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.gtfs.parser

import fi.hsl.gtfsrt2hfp.gtfs.GtfsFeed
import fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.gtfs.model.*
import fi.hsl.gtfsrt2hfp.gtfs.model.*
import fi.hsl.gtfsrt2hfp.gtfs.utils.parseGtfsDate
import fi.hsl.gtfsrt2hfp.gtfs.utils.parseGtfsTime
import kotlinx.coroutines.*
import mu.KotlinLogging
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

private val log = KotlinLogging.logger {}

@ExperimentalTime
class GtfsParser {
    companion object {
        private val GTFS_CSV_FORMAT = CSVFormat.Builder.create(CSVFormat.RFC4180).setHeader().setSkipHeaderRecord(true).build()!!
    }

    private fun createCsvParser(inputStream: InputStream): CSVParser = CSVParser.parse(inputStream, StandardCharsets.UTF_8, GTFS_CSV_FORMAT)

    private suspend fun <T> parseCsv(inputStream: InputStream, mappingFunction: (CSVRecord) -> T?): List<T> = withContext(Dispatchers.IO) {
        val output = mutableListOf<T>()

        createCsvParser(inputStream).use {
            val csvRecordIterator = it.iterator()

            while (csvRecordIterator.hasNext() && isActive) {
                val value = mappingFunction(csvRecordIterator.next())
                if (value != null) {
                    output.add(value)
                }
            }
        }

        return@withContext output.toList()
    }

    private suspend fun parseStops(inputStream: InputStream, stopIdFilter: Collection<String>? = null): List<Stop> = parseCsv(inputStream) { csvRecord ->
        val stopId = csvRecord["stop_id"]

        if (stopIdFilter == null || stopId in stopIdFilter)  {
            Stop(stopId, csvRecord["stop_name"], csvRecord["stop_code"])
        } else {
            null
        }
    }

    private suspend fun parseRoutes(inputStream: InputStream, routeIdFilter: Collection<String>? = null): List<Route> = parseCsv(inputStream) { csvRecord ->
        val routeId = csvRecord["route_id"]

        if (routeIdFilter == null || routeId in routeIdFilter) {
            Route(routeId, csvRecord["route_short_name"], csvRecord["route_long_name"])
        } else {
            null
        }
    }

    private suspend fun parseTrips(inputStream: InputStream, routeIdFilter: Collection<String>? = null): List<Trip> = parseCsv(inputStream) { csvRecord ->
        val routeId = csvRecord["route_id"]

        if (routeIdFilter == null || routeId in routeIdFilter) {
            Trip(
                csvRecord["trip_id"],
                routeId,
                csvRecord["service_id"],
                if (csvRecord.isMapped("direction_id")) { csvRecord["direction_id"]?.toIntOrNull() } else { null },
                if (csvRecord.isMapped("trip_headsign")) { csvRecord["trip_headsign"] } else { null }
            )
        } else {
            null
        }
    }

    private suspend fun parseStopTimes(inputStream: InputStream, tripIdFilter: Collection<String>? = null): List<StopTime> = parseCsv(inputStream) { csvRecord ->
        val tripId = if (csvRecord.isMapped("trip_id")) { csvRecord["trip_id"] } else { csvRecord["\uFEFFtrip_id"] }

        if (tripIdFilter == null || tripId in tripIdFilter) {
            StopTime(
                tripId,
                parseGtfsTime(csvRecord["arrival_time"]),
                parseGtfsTime(csvRecord["departure_time"]),
                csvRecord["stop_id"],
                csvRecord["stop_sequence"].toInt()
            )
        } else {
            null
        }
    }

    private suspend fun parseCalendars(inputStream: InputStream, serviceIdFilter: Collection<String>? = null): List<Calendar> = parseCsv(inputStream) { csvRecord ->
        val serviceId = csvRecord["service_id"]

        if (serviceIdFilter == null || serviceId in serviceIdFilter) {
            Calendar(
                serviceId,
                csvRecord["monday"].toIntOrNull() == 1,
                csvRecord["tuesday"].toIntOrNull() == 1,
                csvRecord["wednesday"].toIntOrNull() == 1,
                csvRecord["thursday"].toIntOrNull() == 1,
                csvRecord["friday"].toIntOrNull() == 1,
                csvRecord["saturday"].toIntOrNull() == 1,
                csvRecord["sunday"].toIntOrNull() == 1,
                parseGtfsDate(csvRecord["start_date"]),
                parseGtfsDate(csvRecord["end_date"])
            )
        } else {
            null
        }
    }

    private suspend fun parseCalendarDates(inputStream: InputStream, serviceIdFilter: Collection<String>? = null): List<CalendarDate> = parseCsv(inputStream) { csvRecord ->
        val serviceId = csvRecord["service_id"]

        if (serviceIdFilter == null || serviceId in serviceIdFilter) {
            CalendarDate(csvRecord["service_id"], parseGtfsDate(csvRecord["date"]), csvRecord["exception_type"].toInt())
        } else {
            null
        }
    }

    private suspend fun openZipFile(path: Path) = withContext(Dispatchers.IO) { ZipFile(path.toFile()) }

    /**
     * @param path Path to the GTFS .zip file
     * @param filterByRouteIds Only include data that is relevant to routes with these IDs. If null, no filtering is done
     */
    suspend fun parseGtfsFeed(file: Path, filterByRouteIds: Collection<String>? = null): GtfsFeed = coroutineScope {
        log.info { "Parsing GTFS from ${file.toAbsolutePath()}" }

        val zipFile = openZipFile(file)

        val (gtfsFeed, duration) = measureTimedValue {
            zipFile.use { zipFile ->
                val routes = async(Dispatchers.IO) { parseRoutes(zipFile.getInputStream(zipFile.getEntry("routes.txt")), filterByRouteIds) }
                val trips = async(Dispatchers.IO) { parseTrips(zipFile.getInputStream(zipFile.getEntry("trips.txt")), filterByRouteIds) }

                val serviceIdFilter = if (filterByRouteIds == null) { null } else { trips.await().map { it.serviceId }.toSet() }
                val tripIdFilter = if (filterByRouteIds == null) { null } else { trips.await().map { it.tripId }.toSet() }

                val stopTimes = async(Dispatchers.IO) { parseStopTimes(zipFile.getInputStream(zipFile.getEntry("stop_times.txt")), tripIdFilter) }

                val calendars = async(Dispatchers.IO) { zipFile.getEntry("calendar.txt")?.let { parseCalendars(zipFile.getInputStream(it), serviceIdFilter) } }
                val calendarDates = async(Dispatchers.IO) { zipFile.getEntry("calendar_dates.txt")?.let { parseCalendarDates(zipFile.getInputStream(it), serviceIdFilter) } }

                val stopIdFilter = if (filterByRouteIds == null) { null } else { stopTimes.await().map { it.stopId }.toSet() }

                val stops = async(Dispatchers.IO) { parseStops(zipFile.getInputStream(zipFile.getEntry("stops.txt")), stopIdFilter) }

                GtfsFeed(stops.await(), routes.await(), trips.await(), stopTimes.await(), calendars.await(), calendarDates.await())
            }
        }

        log.info { "GTFS parsed in ${duration.inWholeMilliseconds}ms" }

        return@coroutineScope gtfsFeed
    }
}