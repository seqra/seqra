package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.JIRMethod

data class UnitRunnerStats(val processed: Long, val enqueued: Long)

class MethodStats {
    val stats = hashMapOf<JIRMethod, Stats>()

    fun stats(method: JIRMethod): Stats = stats.getOrPut(method) {
        Stats(method, steps = 0, unprocessedEdges = 0, handledSummaries = 0, sourceSummaries = 0, passSummaries = 0)
    }

    data class Stats(
        val method: JIRMethod,
        var steps: Long,
        var unprocessedEdges: Long,
        var handledSummaries: Long,
        var sourceSummaries: Long,
        var passSummaries: Long
    )
}
