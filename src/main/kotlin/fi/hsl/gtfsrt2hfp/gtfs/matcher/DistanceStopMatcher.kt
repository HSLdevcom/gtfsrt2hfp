package fi.hsl.gtfsrt2hfp.gtfs.matcher

import fi.hsl.gtfsrt2hfp.gtfs.model.Stop
import fi.hsl.gtfsrt2hfp.gtfs.utils.GtfsIndex

class DistanceStopMatcher(private val stopsByIdA: Map<String, Stop>, private val stopsByIdB: Map<String, Stop>, private val maxDistance: Double) : StopMatcher {
    constructor(gtfsIndexA: GtfsIndex, gtfsIndexB: GtfsIndex, maxDistance: Double) : this(gtfsIndexA.stopsById, gtfsIndexB.stopsById, maxDistance)

    private val matchedStops by lazy {
        stopsByIdA.values.associateBy(keySelector = { it.id }, valueTransform = { stopA ->
            stopsByIdB.values.filter { stopB -> stopB.location!!.distanceTo(stopA.location!!) < maxDistance }.map { it.id }
        })
    }

    /**
     * Stop ID from GTFS feed A
     * @return List of possible stop IDs from GTFS feed B. List is empty if no stops are found
     */
    override fun matchStop(stopId: String): List<String> = matchedStops[stopId] ?: emptyList()
}