package fi.hsl.gtfsrt2hfp.gtfsrt

import com.google.transit.realtime.GtfsRealtime
import fi.hsl.gtfsrt2hfp.utils.executeSuspending
import fi.hsl.gtfsrt2hfp.utils.handleIfSuccessful
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request

class GtfsRtFeedFetcher(private val httpClient: OkHttpClient) {
    suspend fun fetchGtfsRtFeed(url: String, headers: Map<String, String> = emptyMap()): GtfsRealtime.FeedMessage {
        val httpRequest = Request.Builder().url(url).headers(headers.toHeaders()).build()

        httpClient.newCall(httpRequest).executeSuspending().handleIfSuccessful { response ->
            return withContext(Dispatchers.IO) {
                response.body!!.use {
                    GtfsRealtime.FeedMessage.parseFrom(it.byteStream())
                }
            }
        }
    }
}