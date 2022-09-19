package fi.hsl.gtfsrt2hfp

import fi.hsl.gtfsrt2hfp.gtfs.GtfsFeedFetcher
import fi.hsl.gtfsrt2hfp.gtfs.merger.mergeWith
import fi.hsl.gtfsrt2hfp.gtfs.utils.GtfsIndex
import fi.hsl.gtfsrt2hfp.gtfsrt.GtfsRtFeedFetcher
import fi.hsl.gtfsrt2hfp.hfp.model.HfpPayload
import fi.hsl.gtfsrt2hfp.hfp.model.HfpTopic
import fi.hsl.gtfsrt2hfp.utils.ConfigLoader
import fi.hsl.gtfsrt2hfp.utils.connectAsync
import fi.hsl.gtfsrt2hfp.utils.launchTimedTask
import fi.hsl.gtfsrt2hfp.utils.publishAsync
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import okhttp3.OkHttpClient
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Options
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.time.Duration
import kotlin.math.roundToInt
import kotlin.properties.Delegates
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime

private val log = KotlinLogging.logger {}

@ExperimentalTime
fun main(vararg args: String) {
    val options = Options().apply {
        addOption("f", true, "Path to configuration file")
        addOption("r", true, "Configuration resource name")
    }
    val cli = DefaultParser().parse(options, args)

    val configuration = if (cli.hasOption("f")) {
        ConfigLoader.loadConfigFromFile(Paths.get(cli.getOptionValue("f")))
    } else if (cli.hasOption("r")) {
        ConfigLoader.loadConfigFromResource(cli.getOptionValue("r"))
    } else {
        HelpFormatter().printHelp("gtfsrt2hfp", options)
        exitProcess(1)
    }

    val gtfsFeedUrlA = configuration.getString("gtfs.url.a")!!
    val gtfsFeedUrlB = configuration.getString("gtfs.url.b")!!.split(",")

    val gtfsRtFeedUrl = configuration.getString("gtfsRt.url")!!
    val gtfsRtFeedApiKey = configuration.getString("gtfsRt.apiKey")

    val mqttBrokerUri = configuration.getString("mqtt.brokerUri")!!

    val routeIds = configuration.getString("routeIds")!!.split(",")

    val operatorId = configuration.getString("hfp.operatorId")

    val distanceBasedStopStatus = configuration.containsKey("stopStatus.type") && configuration.getString("stopStatus.type") == "BY_DISTANCE"
    val maxDistanceFromStop = if (configuration.containsKey("stopStatus.maxDistanceFromStop")) {
        //Get value as string to avoid exception thrown by conversion if value is missing
        configuration.getString("stopStatus.maxDistanceFromStop").toDoubleOrNull()
    } else {
        null
    }

    runBlocking {
        val httpClient = OkHttpClient.Builder().callTimeout(Duration.ofMinutes(2)).followRedirects(true).build()

        val gtfsFeedFetcher = GtfsFeedFetcher(httpClient)

        val gtfsRtFeedFetcher = GtfsRtFeedFetcher(httpClient)

        val gtfsRtHttpHeaders = if (gtfsRtFeedApiKey != null) {
            mapOf("x-api-key" to gtfsRtFeedApiKey)
        } else {
            emptyMap()
        }

        val mqttAsyncClient = createAndConnectMqttClient(mqttBrokerUri)

        val gtfsRtToHfpConverter = GtfsRtToHfpConverter(operatorId, distanceBasedStopStatus = distanceBasedStopStatus, maxDistanceFromStop = maxDistanceFromStop)

        log.info { "Starting GTFS-RT2HFP application" }
        //Update GTFS data every 12 hours
        launchTimedTask(12.hours) {
            try {
                val gtfsFeedA = async { gtfsFeedFetcher.fetchGtfsFeed(gtfsFeedUrlA, routeIds) }

                val gtfsFeedsB = gtfsFeedUrlB.map { async { gtfsFeedFetcher.fetchGtfsFeed(it) } }.map { it.await() }
                val gtfsFeedB = gtfsFeedsB.reduce { a, b -> a.mergeWith(b) }

                val gtfsIndexA = GtfsIndex(gtfsFeedA.await())
                val gtfsIndexB = GtfsIndex(gtfsFeedB)

                gtfsRtToHfpConverter.updateGtfsData(gtfsIndexA, gtfsIndexB)
            } catch (exception: Exception) {
                log.error { "Failed to update GTFS data: $exception, exiting application..." }
                exitProcess(1)
            }
        }

        launchTimedTask(1.seconds) {
            if (gtfsRtToHfpConverter.hasGtfsData()) {
                val mqttPublishJobs = mutableListOf<Job>()

                val gtfsRtFeed = try {
                    gtfsRtFeedFetcher.fetchGtfsRtFeed(gtfsRtFeedUrl, gtfsRtHttpHeaders)
                } catch (exception: Exception) {
                    log.warn { "Failed to fetch GTFS-RT feed from $gtfsRtFeedUrl: $exception" }
                    return@launchTimedTask
                }

                val feedEntitiesWithVehiclePosition = gtfsRtFeed.entityList.filter { it.hasVehicle() }

                val vehiclePositionsByVehicleId = feedEntitiesWithVehiclePosition.map { it.vehicle!! }.groupBy { it.vehicle.id!! }
                //Feed can contain multiple vehicle positions for same vehicle -> use vehicle position with latest timestamp
                val latestVehiclePositionByVehicleId = vehiclePositionsByVehicleId.mapValues { it.value.maxByOrNull { it.timestamp } }.filterValues { it != null }

                for (vehicle in latestVehiclePositionByVehicleId.values) {
                    gtfsRtToHfpConverter.createHfpForVehiclePosition(vehicle!!)?.let {
                        mqttPublishJobs += launch { mqttAsyncClient.sendHfp(it.first, it.second) }
                    }
                }

                mqttPublishJobs.forEach { it.join() }
            }
        }
    }
}

private const val MAX_MQTT_CONNECTION_LOST_COUNT = 5

private suspend fun createAndConnectMqttClient(serverUri: String, disconnectedBufferSize: Int = 50, maxMessagesInflight: Int = 100): MqttAsyncClient {
    val mqttAsyncClient = MqttAsyncClient(serverUri, MqttAsyncClient.generateClientId(), MemoryPersistence())
    mqttAsyncClient.setBufferOpts(DisconnectedBufferOptions().apply {
        isBufferEnabled = true
        isPersistBuffer = false
        isDeleteOldestMessages = true
        bufferSize = disconnectedBufferSize
    })
    mqttAsyncClient.setCallback(object : MqttCallbackExtended {
        private var connectionLostCount = 0

        private var start by Delegates.notNull<Long>()
        private var messagesSent: Int = 0

        private fun resetStats(logStats: Boolean) {
            if (logStats) {
                val seconds = (System.nanoTime() - start).nanoseconds.toDouble(DurationUnit.SECONDS)
                val msgPerSecond = (messagesSent / seconds).toBigDecimal().setScale(2, RoundingMode.HALF_UP)
                log.info { "$messagesSent MQTT messages sent in last ${seconds.roundToInt()} seconds (${msgPerSecond.toPlainString()} msg/s)" }
            }

            start = System.nanoTime()
            messagesSent = 0
        }

        override fun connectionLost(cause: Throwable) {
            log.warn { "Lost connection to MQTT broker at $serverUri: $cause" }

            //TODO: find better way to handle unreliable reconnecting
            if (++connectionLostCount > MAX_MQTT_CONNECTION_LOST_COUNT) {
                log.error { "Lost connection to MQTT more than $MAX_MQTT_CONNECTION_LOST_COUNT times, exiting application..." }
                exitProcess(1)
            }
        }

        override fun messageArrived(topic: String, message: MqttMessage) { }

        override fun deliveryComplete(token: IMqttDeliveryToken) {
            messagesSent++

            if ((System.nanoTime() - start).nanoseconds > 1.minutes) {
                resetStats(true)
            }
        }

        override fun connectComplete(reconnect: Boolean, serverURI: String) {
            log.info { "Connected to MQTT broker at $serverURI, reconnect: $reconnect" }

            resetStats(false)
        }
    })
    log.info { "Connecting to MQTT broker at $serverUri" }
    mqttAsyncClient.connectAsync(MqttConnectOptions().apply {
        isAutomaticReconnect = true
        isCleanSession = false
        maxInflight = maxMessagesInflight
        keepAliveInterval = 30 //Use shorter keep alive interval to make sure that client does not get disconnected
    })

    return mqttAsyncClient
}

private suspend fun MqttAsyncClient.sendHfp(hfpTopic: HfpTopic, hfpPayload: HfpPayload) {
    val mqttMessage = MqttMessage(hfpPayload.toJson(hfpTopic.eventType).toByteArray(StandardCharsets.UTF_8))
    mqttMessage.qos = 1

    try {
        publishAsync(
            hfpTopic.toString(),
            mqttMessage
        )
    } catch (exception: Exception) {
        //If message publishing fails, just log it instead of throwing exception
        log.warn { "Failed publishing MQTT message to topic $hfpTopic" }
    }
}