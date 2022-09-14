package fi.hsl.gtfsrt2hfp.gtfs.utils

import fi.hsl.gtfsrt2hfp.utils.LatLng
import xyz.malkki.gtfs.model.Stop

val Stop.location: LatLng?
    get() = if (stopLat != null && stopLon != null) {
        LatLng(stopLat!!, stopLon!!)
    } else {
        null
    }