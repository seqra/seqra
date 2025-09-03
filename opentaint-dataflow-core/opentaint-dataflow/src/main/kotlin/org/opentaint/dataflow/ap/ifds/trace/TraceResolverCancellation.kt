package org.opentaint.dataflow.ap.ifds.trace

class TraceResolverCancellation {
    @Volatile
    var isActive: Boolean = true

    fun cancel() {
        isActive = false
    }
}
