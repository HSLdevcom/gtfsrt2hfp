package fi.hsl.gtfsrt2hfp.gtfs

import fi.hsl.gtfsrt2hfp.gtfs.parser.GtfsParser
import fi.hsl.gtfsrt2hfp.utils.executeSuspending
import fi.hsl.gtfsrt2hfp.utils.handleIfSuccessful
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.outputStream
import kotlin.time.ExperimentalTime

private val log = KotlinLogging.logger {}

@ExperimentalTime
class GtfsFeedFetcher(private val httpClient: OkHttpClient) {
    private suspend fun createTempFile(): Path = withContext(Dispatchers.IO) { Files.createTempFile("gtfs", ".zip") }

    suspend fun fetchGtfsFeed(url: String, filterByRouteIds: Collection<String>? = null): GtfsFeed {
        val request = Request.Builder().url(url).build()
        val outputFile = createTempFile()

        log.info { "Downloading GTFS from $url to $outputFile" }

        httpClient.newCall(request).executeSuspending().handleIfSuccessful { response ->
            try {
                withContext(Dispatchers.IO) {
                    BufferedOutputStream(outputFile.outputStream()).use {
                        response.body!!.byteStream().transferTo(it)
                    }
                }

                return GtfsParser().parseGtfsFeed(outputFile, filterByRouteIds)
            } finally {
                withContext(Dispatchers.IO) {
                    Files.deleteIfExists(outputFile)
                }
            }
        }
    }
}