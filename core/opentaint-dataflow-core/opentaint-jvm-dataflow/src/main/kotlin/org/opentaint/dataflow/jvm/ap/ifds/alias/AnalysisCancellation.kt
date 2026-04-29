package org.opentaint.dataflow.jvm.ap.ifds.alias

import org.opentaint.dataflow.util.Cancellation
import kotlin.time.Duration
import kotlin.time.TimeSource

class AnalysisCancellation(
    timeLimit: Duration,
    val parentCancellation: Cancellation?
) {
    private val finishTime = TimeSource.Monotonic.markNow() + timeLimit

    class AnalysisCancelled : Exception("Analysis cancelled") {
        override fun fillInStackTrace(): Throwable = this
    }

    fun checkpoint() {
        parentCancellation?.checkpoint()

        if (finishTime.hasNotPassedNow()) return
        throw AnalysisCancelled()
    }
}

inline fun <T> withAnalysisCancellation(
    timeLimit: Duration,
    parentCancellation: Cancellation?,
    body: (AnalysisCancellation) -> T,
    onAnalysisCancelled: () -> T,
): T {
    val cancellation = AnalysisCancellation(timeLimit, parentCancellation)
    return try {
        body(cancellation)
    } catch (_: AnalysisCancellation.AnalysisCancelled) {
        onAnalysisCancelled()
    }
}
