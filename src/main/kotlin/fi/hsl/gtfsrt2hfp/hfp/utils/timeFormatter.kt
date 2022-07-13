package fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.hfp.utils

fun formatHfpTime(seconds: Int): String {
    val time = seconds % 86400 //HFP time is always in 24h format

    val hours = time / 3600
    val minutes = time / 60 - hours * 60

    return "${"%02d".format(hours)}:${"%02d".format(minutes)}"
}