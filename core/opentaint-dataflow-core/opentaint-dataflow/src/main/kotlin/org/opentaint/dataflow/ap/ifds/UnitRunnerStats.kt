package org.opentaint.dataflow.ap.ifds

import org.opentaint.ir.api.common.CommonMethod
import java.util.BitSet
import kotlin.time.Duration.Companion.nanoseconds

data class UnitRunnerStats(val processed: Long, val enqueued: Long)

class MethodStats {
    val stats = hashMapOf<CommonMethod, Stats>()

    fun subtract(other: MethodStats): MethodStats {
        val result = MethodStats()
        for ((m, s) in stats) {
            val otherS = other.stats[m]
            val subS = otherS?.let { os -> s.copy().apply { subtract(os) } } ?: s
            result.stats[m] = subS
        }
        return result
    }

    fun stats(method: CommonMethod): Stats = stats.getOrPut(method) {
        Stats(
            method,
            steps = 0,
            unprocessedEdges = 0,
            handledSummaries = 0,
            sourceSummaries = 0,
            passSummaries = 0,
            traceResolverSteps = 0,
            analysisTime = 0,
            stepTime = 0,
            summaryTime = 0,
            callTime = 0,
            otherTime = 0,
        )
    }

    data class Stats(
        val method: CommonMethod,
        var steps: Long,
        var unprocessedEdges: Long,
        var handledSummaries: Long,
        var sourceSummaries: Long,
        var passSummaries: Long,
        var traceResolverSteps: Long,
        var analysisTime: Long,
        var stepTime: Long,
        var summaryTime: Long,
        var callTime: Long,
        var otherTime: Long,
    ) {
        val stepsForTaintMark: MutableMap<String, Long> = hashMapOf()
        val coveredInstructions = BitSet()

        fun subtract(other: Stats) {
            steps -= other.steps
            unprocessedEdges -= other.unprocessedEdges
            handledSummaries -= other.handledSummaries
            sourceSummaries -= other.sourceSummaries
            passSummaries -= other.passSummaries
            traceResolverSteps -= other.traceResolverSteps
            analysisTime -= other.analysisTime
            stepTime -= other.stepTime
            summaryTime -= other.summaryTime
            callTime -= other.callTime
            otherTime -= other.otherTime
        }

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

            if (analysisTime > 0) {
                append(" | ")
                append("AT: ${analysisTime.nanoseconds.inWholeMilliseconds}")
                append(" | ")
                append("StT: ${stepTime.nanoseconds.inWholeMilliseconds}")
                append(" | ")
                append("SuT: ${summaryTime.nanoseconds.inWholeMilliseconds}")
                append(" | ")
                append("CT: ${callTime.nanoseconds.inWholeMilliseconds}")
                append(" | ")
                append("OT: ${otherTime.nanoseconds.inWholeMilliseconds}")
            }

            if (traceResolverSteps > 0) {
                append(" | ")
                append("trace: $traceResolverSteps")
            }
        }
    }
}
