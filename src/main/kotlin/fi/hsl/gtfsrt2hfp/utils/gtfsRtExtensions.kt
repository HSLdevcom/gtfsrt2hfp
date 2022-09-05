package fi.hsl.gtfsrt2hfp.utils

import com.google.transit.realtime.GtfsRealtime

fun GtfsRealtime.Position.getLocation(): LatLng? {
    if (this.hasLatitude() && this.hasLongitude()) {
        return LatLng(this.latitude.toDouble(), this.longitude.toDouble())
    } else {
        return null
    }
}