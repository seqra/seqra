package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.jvm.util.concurrentReadSafeForEach

class MethodAnalyzerStorage {
    private val entryPoints = arrayListOf<JIRInst>()
    private val analyzers = arrayListOf<MethodAnalyzer>()

    fun add(runner: TaintAnalysisUnitRunner, entryPoint: JIRInst): Boolean {
        val epIdx = entryPoints.indexOf(entryPoint)
        if (epIdx != -1) return false

        val analyzer = if (entryPoint.location.method.instList.size > 0) {
            NormalMethodAnalyzer(runner, entryPoint)
        } else {
            val methodExitPoints = runner.graph.exitPoints(entryPoint.location.method).toList()

            check(methodExitPoints.isEmpty() || entryPoint in methodExitPoints) {
                "Empty method entry point not in exit points"
            }

            EmptyMethodAnalyzer(runner, entryPoint)
        }

        entryPoints.add(entryPoint)
        analyzers.add(analyzer)

        return true
    }

    fun getAnalyzer(entryPoint: JIRInst): MethodAnalyzer {
        val epIdx = entryPoints.indexOf(entryPoint)
        check(epIdx != -1) { "No analyzer for $entryPoint" }

        return analyzers[epIdx]
    }

    fun collectStats(stats: MethodStats) {
        analyzers.concurrentReadSafeForEach { _, methodAnalyzer ->
            methodAnalyzer.collectStats(stats)
        }
    }
}
