package fi.hsl.gtfsrt2hfp.gtfsrt

import com.google.transit.realtime.GtfsRealtime
import fi.hsl.gtfsrt2hfp.utils.executeSuspending
import fi.hsl.gtfsrt2hfp.utils.readString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class GtfsRtFeedFetcher(private val httpClient: OkHttpClient) {
    suspend fun fetchGtfsRtFeed(url: String, headers: Map<String, String> = emptyMap()): GtfsRealtime.FeedMessage {
        val httpRequest = Request.Builder().url(url).headers(headers.toHeaders()).build()

        val httpResponse = httpClient.newCall(httpRequest).executeSuspending()
        if (httpResponse.isSuccessful) {
            return withContext(Dispatchers.IO) {
                httpResponse.body!!.use {
                    GtfsRealtime.FeedMessage.parseFrom(it.byteStream())
                }
            }
        } else {
            throw IOException("HTTP request to $url failed (status ${httpResponse.code}), response: ${httpResponse.body?.charStream()?.readString(200)}")
        }
    }
}