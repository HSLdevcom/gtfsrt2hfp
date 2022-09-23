package fi.hsl.gtfsrt2hfp.extensions

import org.eclipse.paho.client.mqttv3.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private class ContinuationMqttActionListener(private val continuation: Continuation<Unit>) : IMqttActionListener {
    override fun onSuccess(asyncActionToken: IMqttToken) {
        continuation.resume(Unit)
    }

    override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
        continuation.resumeWithException(exception)
    }
}

suspend fun MqttAsyncClient.connectAsync(mqttConnectOptions: MqttConnectOptions) = suspendCoroutine<Unit> {
    connect(mqttConnectOptions, null, ContinuationMqttActionListener(it))
}

suspend fun MqttAsyncClient.publishAsync(topic: String, message: MqttMessage) = suspendCoroutine<Unit> {
    publish(topic, message, null, ContinuationMqttActionListener(it))
}