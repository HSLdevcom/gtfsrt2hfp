package fi.hsl.gtfsrt2hfp.gtfs.matcher

import fi.hsl.gtfsrt2hfp.gtfs.utils.GtfsIndex
import fi.hsl.gtfsrt2hfp.gtfs.utils.location
import xyz.malkki.gtfs.model.Stop

class DistanceStopMatcher(private val stopsByIdA: Map<String, Stop>, private val stopsByIdB: Map<String, Stop>, private val maxDistance: Double) : StopMatcher {
    constructor(gtfsIndexA: GtfsIndex, gtfsIndexB: GtfsIndex, maxDistance: Double) : this(gtfsIndexA.stopsById, gtfsIndexB.stopsById, maxDistance)

    private val matchedStops by lazy {
        stopsByIdA.values.associateBy(keySelector = { it.stopId }, valueTransform = { stopA ->
            stopsByIdB.values.filter { stopB -> stopB.location!!.distanceTo(stopA.location!!) < maxDistance }.map { it.stopId }
        })
    }

    /**
     * Stop ID from GTFS feed A
     * @return List of possible stop IDs from GTFS feed B. List is empty if no stops are found
     */
    override fun matchStop(stopId: String): List<String> = matchedStops[stopId] ?: emptyList()
}