package fi.hsl.gtfsrt2hfp.utils;

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun Call.executeSuspending(): Response = suspendCancellableCoroutine { cancellableContinuation ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            cancellableContinuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cancellableContinuation.resume(response)
        }
    })

    cancellableContinuation.invokeOnCancellation { if (!isCanceled()) { cancel() } }
}

inline fun <T> Response.handleIfSuccessful(handler: (Response) -> T): T {
    if (isSuccessful) {
        return handler(this)
    } else {
        val responseText = try {
            body?.charStream()?.readString(200)
        } catch (e: Exception) {
            null
        }

        throw IOException("HTTP request to ${request.url} failed (status $code - ${message}), response: $responseText")
    }
}