package org.opentaint.dataflow.ap.ifds.trace

class ProcessingCancellation {
    @Volatile
    var isActive: Boolean = true

    fun cancel() {
        isActive = false
    }
}
