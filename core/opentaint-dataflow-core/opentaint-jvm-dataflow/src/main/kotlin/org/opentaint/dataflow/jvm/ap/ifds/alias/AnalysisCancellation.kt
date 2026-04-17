package org.opentaint.dataflow.jvm.ap.ifds.alias

import kotlin.time.Duration
import kotlin.time.TimeSource

class AnalysisCancellation(timeLimit: Duration) {
    private val finishTime = TimeSource.Monotonic.markNow() + timeLimit

    class AnalysisCancelled : Exception("Analysis cancelled") {
        override fun fillInStackTrace(): Throwable = this
    }

    fun checkpoint() {
        if (finishTime.hasNotPassedNow()) return
        throw AnalysisCancelled()
    }
}

inline fun <T> withAnalysisCancellation(
    timeLimit: Duration,
    body: (AnalysisCancellation) -> T,
    onAnalysisCancelled: () -> T,
): T {
    val cancellation = AnalysisCancellation(timeLimit)
    return try {
        body(cancellation)
    } catch (_: AnalysisCancellation.AnalysisCancelled) {
        onAnalysisCancelled()
    }
}
