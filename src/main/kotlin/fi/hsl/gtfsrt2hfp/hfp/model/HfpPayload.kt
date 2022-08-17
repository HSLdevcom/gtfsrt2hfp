package fi.hsl.gtfsrt2hfp.hfp.model

import com.google.gson.GsonBuilder

data class HfpPayload(
    val desi: String,
    val dir: String,
    val oper: Int,
    val veh: Int,
    val tst: String,
    val tsi: Long,
    val spd: Double,
    val hdg: Int,
    val lat: Double,
    val long: Double,
    val acc: Double,
    val dl: Int?,
    val odo: Double?,
    val drst: Int?,
    val oday: String,
    val jrn: Int?,
    val line: Int?,
    val start: String,
    val loc: String,
    val stop: String?,
    val route: String,
    val occu: Int
) {
    companion object {
        private val GSON by lazy { GsonBuilder().serializeNulls().create()!! }
    }

    fun toJson(eventType: HfpTopic.EventType): String = GSON.toJson(mapOf(eventType.name to this))
}
