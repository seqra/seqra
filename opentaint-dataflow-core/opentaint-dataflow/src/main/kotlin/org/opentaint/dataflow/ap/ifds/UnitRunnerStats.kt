package org.opentaint.dataflow.ap.ifds

import org.opentaint.ir.api.common.CommonMethod
import java.util.BitSet

data class UnitRunnerStats(val processed: Long, val enqueued: Long)

class MethodStats {
    val stats = hashMapOf<CommonMethod, Stats>()

    fun stats(method: CommonMethod): Stats = stats.getOrPut(method) {
        Stats(method, steps = 0, unprocessedEdges = 0, handledSummaries = 0, sourceSummaries = 0, passSummaries = 0, traceResolverSteps = 0)
    }

    data class Stats(
        val method: CommonMethod,
        var steps: Long,
        var unprocessedEdges: Long,
        var handledSummaries: Long,
        var sourceSummaries: Long,
        var passSummaries: Long,
        var traceResolverSteps: Long,
    ) {
        val stepsForTaintMark: MutableMap<String, Long> = hashMapOf()
        val coveredInstructions = BitSet()

        override fun toString(): String = buildString {
            append(method)
            append(" | ")
            append("steps: $steps")
            append(" | ")
            append("unp: $unprocessedEdges")
            append(" | ")
            append("sum: $handledSummaries")
            append(" | ")
            append("source: $sourceSummaries")
            append(" | ")
            append("pass: $passSummaries")

            if (traceResolverSteps > 0) {
                append(" | ")
                append("trace: $traceResolverSteps")
            }
        }
    }
}
