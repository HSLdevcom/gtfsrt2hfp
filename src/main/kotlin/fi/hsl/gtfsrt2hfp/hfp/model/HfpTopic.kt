package fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.hfp.model

data class HfpTopic(
    val prefix: String,
    val journeyType: JourneyType,
    val temporalType: TemporalType,
    val eventType: EventType,
    val transportMode: TransportMode,
    val operatorId: String,
    val vehicleId: String,
    val routeId: String?,
    val directionId: String?,
    val headsign: String?,
    val startTime: String?,
    val nextStop: String?,
    val geohashLevel: Int?,
    val geohash: String?
) {
    companion object {
        const val HFP_V2_PREFIX = "/hfp/v2"
    }

    override fun toString(): String {
        val deadrun = listOf(
            prefix,
            journeyType.toString(),
            temporalType.toString(),
            eventType.toString(),
            transportMode.toString(),
            operatorId.padStart(4, '0'),
            vehicleId.padStart(5, '0')
        ).joinToString("/")

        return if (journeyType != JourneyType.DEADRUN) {
            listOf(
                deadrun,
                routeId,
                directionId,
                headsign,
                startTime,
                nextStop,
                geohashLevel.toString(),
                geohash
            ).joinToString("/")
        } else {
            deadrun
        }
    }

    enum class JourneyType {
        JOURNEY, DEADRUN;

        override fun toString(): String {
            return name.lowercase()
        }
    }

    enum class TemporalType {
        ONGOING, UPCOMING;

        override fun toString(): String {
            return name.lowercase()
        }
    }

    enum class EventType {
        VP, DUE, ARR, DEP, ARS, PDE, PAS, WAIT, DOO, DOC, TLR, TLA, DA, DOUT, BA, VOUT, VJA, VJOUT;

        override fun toString(): String {
            return name.lowercase()
        }
    }

    enum class TransportMode {
        BUS, TRAM, TRAIN, FERRY, METRO, ROBOT, UBUS;

        override fun toString(): String {
            return name.lowercase()
        }
    }
}
