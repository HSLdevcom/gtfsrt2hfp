package fi.hsl.gtfsrt2hfp.gtfs.model

import fi.hsl.gtfsrt2hfp.utils.LatLng

data class Stop(val id: String, val name: String, val code: String, val lat: Double?, val lon: Double?) {
    val location = if (lat != null && lon != null) { LatLng(lat, lon) } else { null }
}
