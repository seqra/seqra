package org.opentaint.dataflow.bidi.engine

/** One run counts accepted events, live handlers and its start-batch reservation. */
class InFlight(private val onZero: () -> Unit) {
    private val lock = Any()
    private var count = 0L
    private var handledCount = 0L

    val pending: Long get() = synchronized(lock) { count }
    val handled: Long get() = synchronized(lock) { handledCount }

    /** Reserve before an event becomes visible in a channel or a local queue. */
    fun beforeSend() {
        synchronized(lock) { count++ }
    }

    /** A completed handler releases only after all its sends and local-work enqueues. */
    fun afterHandler() = release(countHandled = true)

    /** A failed send releases its reservation without reporting a handled event. */
    fun rejectedSend() = release(countHandled = false)

    private fun release(countHandled: Boolean) {
        val zero = synchronized(lock) {
            check(count > 0L) { "An in-flight release requires a live reservation" }
            if (countHandled) handledCount++
            count--
            count == 0L
        }
        // The manager serializes admission and terminal selection with its outer lock.
        // Keep this callback outside our bookkeeping lock to avoid lock inversion.
        if (zero) onZero()
    }
}
