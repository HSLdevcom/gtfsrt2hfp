package fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.gtfs.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val GTFS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")!!

fun parseGtfsTime(timeString: String): Int {
    val timeStringSplit = timeString.split(":", limit = 3)
    if (timeStringSplit.size != 3) {
        throw IllegalArgumentException("Invalid time string ($timeString), must be in format HH:mm:ss")
    }
    val (hours, minutes, seconds) = timeStringSplit.map {
        return@map it.toIntOrNull() ?: throw IllegalArgumentException("Invalid time string ($timeString)")
    }

    return hours * 60 * 60 + minutes * 60 + seconds
}

fun parseGtfsDate(dateString: String): LocalDate = LocalDate.parse(dateString, GTFS_DATE_FORMAT)