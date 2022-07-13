package fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@ExperimentalTime
/**
 * Starts a job that periodically runs the specified function once in the period. If executing the function takes longer than the specified period, there is a 10 millisecond delay before next execution
 */
suspend fun CoroutineScope.launchTimedTask(delay: Duration, func: suspend () -> Unit): Job = launch {
    while (true) {
        val duration = measureTime { func() }

        //Add small minimum delay
        val timeUntilNext = maxOf(10.milliseconds, delay.minus(duration))
        delay(timeUntilNext)
    }
}