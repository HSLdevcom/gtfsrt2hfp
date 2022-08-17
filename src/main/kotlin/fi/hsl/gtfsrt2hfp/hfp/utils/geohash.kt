package fi.hsl.gtfsrt2hfp.hfp.utils

import java.math.BigDecimal
import java.math.RoundingMode

fun getGeohash(latitude: Double, longitude: Double): String {
    val latitudeString = BigDecimal.valueOf(latitude).setScale(3, RoundingMode.DOWN).toPlainString()
            .split("\\.".toRegex())
            .dropLastWhile { it.isEmpty() }

    val longitudeString =
        BigDecimal.valueOf(longitude).setScale(3, RoundingMode.DOWN).toPlainString()
            .split("\\.".toRegex())
            .dropLastWhile { it.isEmpty() }

    return arrayOf(
        latitudeString[0] + ";" + longitudeString[0],
        String(charArrayOf(latitudeString[1][0], longitudeString[1][0])),
        String(charArrayOf(latitudeString[1][1], longitudeString[1][1])),
        String(charArrayOf(latitudeString[1][2], longitudeString[1][2]))
    ).joinToString("/")
}
