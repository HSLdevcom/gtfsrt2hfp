package fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.gtfs.model

data class StopTime(val tripId: String, val arrivalTime: Int, val departureTime: Int, val stopId: String, val stopSequence: Int) : Comparable<StopTime> {
    override fun compareTo(other: StopTime): Int {
        val byTrip = tripId.compareTo(other.tripId)
        if (byTrip == 0) {
            //Same trip, compare by stop sequence

            return stopSequence.compareTo(other.stopSequence)
        }

        return byTrip
    }
}
