package fi.hsl.gtfsrt2hfp.utils

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.transit.realtime.GtfsRealtime.VehiclePosition
import java.time.Duration
import kotlin.math.abs

class OdometerCalibrator(private val minEstimates: Int = 3, cacheDuration: Duration = Duration.ofHours(2)) {
    private val calibrationsCache: Cache<String, Pair<Long, Long>> = Caffeine.newBuilder()
        .expireAfterWrite(cacheDuration)
        .build()

    private fun isOdometerInKilometres(previousPosition: VehiclePosition, currentPosition: VehiclePosition): Boolean? {
        //Check if odometer values are in kilometres or metres
        //According to GTFS-RT spec they should be in metres, but some feeds have them incorrectly in kilometres
        val odoDiff = if (currentPosition.position.hasOdometer() && previousPosition.position.hasOdometer()) { abs(currentPosition.position.odometer - previousPosition.position.odometer) } else { null }
        val distanceDiff = currentPosition.position.getLocation()?.let { currentLocation -> previousPosition.position.getLocation()?.distanceTo(currentLocation) }

        return if (odoDiff != null && distanceDiff != null) {
            val diffIfMetres = abs(odoDiff - distanceDiff)
            val diffIfKilometres = abs(odoDiff*1000.0 - distanceDiff)

            diffIfKilometres < diffIfMetres
        } else {
            //Cannot be determined
            null
        }
    }

    /**
     * @return true if odometers is in kilometres, false if in metres. null if cannot be determined
     */
    fun calibrateOdometer(previousPosition: VehiclePosition, currentPosition: VehiclePosition): Boolean? {
        val vehicleId = currentPosition.vehicle.id

        val calibration = calibrationsCache.asMap().compute(vehicleId) { _, calibration ->
            val estimatesKilometres = calibration?.first ?: 0
            val estimatesMetres = calibration?.second ?: 0

            val inKilometres = isOdometerInKilometres(previousPosition, currentPosition)
            return@compute if (inKilometres == true) {
                estimatesKilometres + 1 to estimatesMetres
            } else if (inKilometres == false) {
                estimatesKilometres to estimatesMetres + 1
            } else {
                estimatesKilometres to estimatesMetres
            }
        }

        if (calibration != null && (calibration.first + calibration.second >= minEstimates)) {
            return calibration.first > calibration.second
        } else {
            return null
        }
    }
}