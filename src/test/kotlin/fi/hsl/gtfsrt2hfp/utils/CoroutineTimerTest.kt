package fi.hsl.gtfsrt2hfp.utils

import fi.hsl.gtfsrt2hfp.fi.hsl.gtfsrt2hfp.utils.launchTimedTask
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@ExperimentalTime
class CoroutineTimerTest {
    @Test
    fun `Test coroutine timer`() {
        val duration = runBlocking {
            val job = launch(start = CoroutineStart.LAZY) {
                var count = 0

                launchTimedTask(100.milliseconds) {
                    count++
                    if (count > 5) {
                        cancel()
                    }
                }
            }

            return@runBlocking measureTime {
                job.start()
                job.join()
            }
        }
        assertTrue(duration >= 500.milliseconds, "Running the timer took less time than expected")
    }
}