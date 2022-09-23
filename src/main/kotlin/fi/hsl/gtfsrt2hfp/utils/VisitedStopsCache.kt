package fi.hsl.gtfsrt2hfp.utils

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.time.Duration

/**
 * Helper class for keeping track of which stops the vehicle has visited. This is needed to handle cases where the stop status does not update correctly in GTFS-RT after the vehicle has left a stop
 */
class VisitedStopsCache(cacheDuration: Duration = Duration.ofHours(2)) {
    private val visitedStopsCache: Cache<Triple<String, String, String>, Set<String>> = Caffeine.newBuilder()
        .expireAfterWrite(cacheDuration)
        .build()

    private fun createCacheKey(vehicleId: Pair<String, String>, tripId: String): Triple<String, String, String> {
        return Triple(vehicleId.first, vehicleId.second, tripId)
    }

    fun hasVisitedStop(vehicleId: Pair<String, String>, tripId: String, stopId: String): Boolean {
        val cacheKey = createCacheKey(vehicleId, tripId)

        return (visitedStopsCache.getIfPresent(cacheKey) ?: emptySet()).contains(stopId)
    }

    fun addVisitedStop(vehicleId: Pair<String, String>, tripId: String, stopId: String) {
        val cacheKey = createCacheKey(vehicleId, tripId)

        visitedStopsCache.asMap().compute(cacheKey) { _, existing ->
            val visitedStops = (existing ?: emptyList()).toMutableSet()
            visitedStops.add(stopId)
            return@compute visitedStops
        }
    }
}