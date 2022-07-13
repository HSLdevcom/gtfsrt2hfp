package fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.hfp.utils

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.math.BigDecimal
import java.time.Duration
import kotlin.math.min

/**
 * @param cacheDuration Time to keep old vehicle states in cache
 */
class GeohashCalculator(cacheDuration: Duration = Duration.ofHours(2)) {
    companion object {
        private const val MAX_GEOHASH_LEVEL = 5

        private fun getGeohashLevel(coordinateA: Double, coordinateB: Double): Int {
            val a = BigDecimal.valueOf(coordinateA)
            val aAsString = a.remainder(BigDecimal.ONE).movePointRight(a.scale()).abs().toBigInteger().toString()
            val b = BigDecimal.valueOf(coordinateB)
            val bAsString = b.remainder(BigDecimal.ONE).movePointRight(b.scale()).abs().toBigInteger().toString()
            for (i in 0 until aAsString.length.coerceAtMost(bAsString.length)) {
                if (aAsString[i] != bAsString[i]) {
                    return i + 1
                }
            }
            return MAX_GEOHASH_LEVEL
        }
    }

    private val vehicleStates: Cache<Pair<String, String>, VehicleState> = Caffeine.newBuilder()
        .expireAfterWrite(cacheDuration)
        .build()

    fun getGeohashLevel(operatorId: String, vehicleId: String, latitude: Double?, longitude: Double?, nextStop: String?, route: String?, startTime: String?, directionId: String?): Int {
        return synchronized(vehicleStates) {
            val cacheKey = operatorId to vehicleId
            val cacheValueNew = VehicleState(latitude, longitude, nextStop, route, startTime, directionId)
            val cacheValuePrev = vehicleStates.getIfPresent(cacheKey)

            vehicleStates.put(cacheKey, cacheValueNew)

            if (cacheValuePrev == null
                || cacheValuePrev.nextStop != cacheValueNew.nextStop
                || cacheValuePrev.route != cacheValueNew.route
                || cacheValuePrev.directionId != cacheValueNew.directionId
                || cacheValuePrev.startTime != cacheValueNew.startTime) {
                0
            } else if (cacheValuePrev.latitude?.toInt() != cacheValueNew.latitude?.toInt() || cacheValuePrev.longitude?.toInt() != cacheValueNew.longitude?.toInt() ){
                0
            } else {
                min(Companion.getGeohashLevel(cacheValuePrev.latitude!!, cacheValueNew.latitude!!), Companion.getGeohashLevel(cacheValuePrev.longitude!!, cacheValueNew.longitude!!)).coerceAtMost(MAX_GEOHASH_LEVEL)
            }
        }
    }

    private data class VehicleState(
        val latitude: Double?,
        val longitude: Double?,
        val nextStop: String?,
        val route: String?,
        val startTime: String?,
        val directionId: String?
    )
}

