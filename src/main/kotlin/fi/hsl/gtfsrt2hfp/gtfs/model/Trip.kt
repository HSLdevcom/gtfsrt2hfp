package fi.hsl.gtfsrt2hfp.gtfs.model

data class Trip(val tripId: String, val routeId: String, val serviceId: String, val directionId: Int?, val headsign: String?)
