package fi.hsl.gtfsrt2hfp.gtfs.matcher

interface StopMatcher {
    /**
     * Stop ID from GTFS feed A
     * @return List of possible stop IDs from GTFS feed B. List is empty if no stops are found
     */
    fun matchStop(stopId: String): List<String>
}