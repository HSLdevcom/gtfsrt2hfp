package fi.hsl.gtfsrt2hfp.gtfs.matcher

import fi.hsl.gtfsrt2hfp.gtfs.utils.GtfsIndex
import xyz.malkki.gtfs.model.Stop

/**
 * Matches stops from GTFS feed A to GTFS feed B by comparing their stop codes (stop_code column in stops.txt)
 */
class StopCodeStopMatcher(private val stopsByIdA: Map<String, Stop>, private val stopsByIdB: Map<String, Stop>) :
    StopMatcher {
    constructor(gtfsIndexA: GtfsIndex, gtfsIndexB: GtfsIndex) : this(gtfsIndexA.stopsById, gtfsIndexB.stopsById)

    private val matchedStops by lazy {
        val aByCode = stopsByIdA.values.filter { it.stopCode!!.isNotBlank() }.groupBy { it.stopCode!!.trim() }
        val bByCode = stopsByIdB.values.filter { it.stopCode!!.isNotBlank() }.groupBy { it.stopCode!!.trim() }

        val intersection = HashSet(aByCode.keys)
        intersection.retainAll(bByCode.keys)

        val output = mutableMapOf<String, List<String>>()
        intersection.forEach { stopCode ->
            val stopsB = bByCode[stopCode]?.map { stop -> stop.stopId } ?: emptyList()
            aByCode[stopCode]?.forEach { stop -> output[stop.stopId] = stopsB }
        }

        return@lazy output.toMap()
    }

    /**
     * Stop ID from GTFS feed A
     * @return List of possible stop IDs from GTFS feed B. List is empty if no stops are found
     */
    override fun matchStop(stopId: String): List<String> = matchedStops[stopId] ?: emptyList()
}