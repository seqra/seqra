package org.opentaint.dataflow.jvm.ap.ifds

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.opentaint.dataflow.jvm.util.concurrentReadSafeForEach

class MethodAnalyzerStorage {
    private val entryPoints = Object2IntOpenHashMap<MethodEntryPoint>()
        .also { it.defaultReturnValue(NO_ANALYZER) }

    private val analyzers = arrayListOf<MethodAnalyzer>()

    fun add(runner: TaintAnalysisUnitRunner, methodEntryPoint: MethodEntryPoint): Boolean {
        val epIdx = entryPoints.getInt(methodEntryPoint)
        if (epIdx != NO_ANALYZER) return false

        val analyzer = if (methodEntryPoint.method.instList.size > 0) {
            NormalMethodAnalyzer(runner, methodEntryPoint)
        } else {
            val methodExitPoints = runner.graph.exitPoints(methodEntryPoint.method).toList()

            check(methodExitPoints.isEmpty() || methodEntryPoint.statement in methodExitPoints) {
                "Empty method entry point not in exit points"
            }

            EmptyMethodAnalyzer(runner, methodEntryPoint)
        }

        entryPoints.put(methodEntryPoint, analyzers.size)
        analyzers.add(analyzer)

        return true
    }

    fun getAnalyzer(methodEntryPoint: MethodEntryPoint): MethodAnalyzer {
        val epIdx = entryPoints.getInt(methodEntryPoint)
        check(epIdx != NO_ANALYZER) { "No analyzer for $methodEntryPoint" }

        return analyzers[epIdx]
    }

    fun collectStats(stats: MethodStats) {
        analyzers.concurrentReadSafeForEach { _, methodAnalyzer ->
            methodAnalyzer.collectStats(stats)
        }
    }

    companion object {
        private const val NO_ANALYZER = -1
    }
}
