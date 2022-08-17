package fi.hsl.gtfsrt2hfp.gtfs

import fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.gtfs.parser.GtfsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.ExperimentalTime

private val log = KotlinLogging.logger {}

@ExperimentalTime
class GtfsFeedFetcher(private val httpClient: HttpClient) {
    private suspend fun createTempFile(): Path = withContext(Dispatchers.IO) { Files.createTempFile("gtfs", "zip") }

    suspend fun fetchGtfsFeed(url: String, filterByRouteIds: Collection<String>? = null): GtfsFeed {
        val request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build()
        val outputFile = createTempFile()

        log.info { "Downloading GTFS from $url to $outputFile" }

        val httpResponse = httpClient.sendAsync(request, BodyHandlers.ofFile(outputFile)).await()
        if (httpResponse.statusCode() == 200) {
            val file = httpResponse.body()

            try {
                return GtfsParser().parseGtfsFeed(file, filterByRouteIds)
            } finally {
                withContext(Dispatchers.IO) {
                    Files.deleteIfExists(file)
                }
            }
        } else {
            throw IOException("HTTP request to $url failed, status ${httpResponse.statusCode()}")
        }
    }
}