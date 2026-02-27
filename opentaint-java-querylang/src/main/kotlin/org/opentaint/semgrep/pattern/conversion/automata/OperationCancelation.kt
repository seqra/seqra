package org.opentaint.semgrep.pattern.conversion.automata

import kotlin.time.Duration
import kotlin.time.TimeSource

class OperationCancelation(timeout: Duration) {
    private val endMark = TimeSource.Monotonic.markNow() + timeout
    private var checksSinceLastCheck = 0

    fun check() {
        if (checksSinceLastCheck++ < CHECK_RATE) return
        checksSinceLastCheck = 0

        if (endMark.hasPassedNow()) {
            throw OperationTimeout()
        }
    }

    class OperationTimeout : Exception("Operation timeout")

    companion object {
        private const val CHECK_RATE = 1000
    }
}
