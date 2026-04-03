package org.opentaint.dataflow.util

class Cancellation {
    class Cancelled : Exception("Operation cancelled") {
        override fun fillInStackTrace(): Throwable = this
    }

    @Volatile
    private var isActive: Boolean = true

    fun activate() {
        isActive = true
    }

    fun cancel() {
        isActive = false
    }

    fun isActive(): Boolean = isActive

    fun checkpoint() {
        if (isActive) return
        throw Cancelled()
    }
}
