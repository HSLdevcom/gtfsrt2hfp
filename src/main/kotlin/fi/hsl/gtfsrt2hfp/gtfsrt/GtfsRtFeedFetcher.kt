package fi.hsl.gtfsrt2hfp.gtfsrt

import com.google.transit.realtime.GtfsRealtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers

class GtfsRtFeedFetcher(private val httpClient: HttpClient) {
    suspend fun fetchGtfsRtFeed(url: String, headers: Map<String, String> = emptyMap()): GtfsRealtime.FeedMessage {
        val httpRequestBuilder = HttpRequest.newBuilder().uri(URI.create(url)).GET()

        for ((key, value) in headers) {
            httpRequestBuilder.setHeader(key, value)
        }

        val httpResponse = httpClient.sendAsync(httpRequestBuilder.build(), BodyHandlers.ofInputStream()).await()
        if (httpResponse.statusCode() == 200) {
            return withContext(Dispatchers.IO) {
                httpResponse.body().use {
                    GtfsRealtime.FeedMessage.parseFrom(it)
                }
            }
        } else {
            throw IOException("HTTP request to $url failed, status ${httpResponse.statusCode()}")
        }
    }
}