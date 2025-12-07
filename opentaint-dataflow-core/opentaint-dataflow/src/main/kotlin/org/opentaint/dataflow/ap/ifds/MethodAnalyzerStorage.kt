package org.opentaint.dataflow.ap.ifds

import org.opentaint.dataflow.util.concurrentReadSafeForEach
import org.opentaint.dataflow.util.getOrCreateIndex
import org.opentaint.dataflow.util.getValue
import org.opentaint.dataflow.util.object2IntMap

class MethodAnalyzerStorage(
    private val languageManager: org.opentaint.dataflow.ap.ifds.LanguageManager,
    private val taintRulesStatsSamplingPeriod: Int?
) {
    private val entryPoints = object2IntMap<org.opentaint.dataflow.ap.ifds.MethodEntryPoint>()
    private val analyzers = arrayListOf<org.opentaint.dataflow.ap.ifds.MethodAnalyzer>()

    fun add(runner: org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunner, methodEntryPoint: org.opentaint.dataflow.ap.ifds.MethodEntryPoint): Boolean {
        entryPoints.getOrCreateIndex(methodEntryPoint) {
            val analyzer = if (!languageManager.isEmpty(methodEntryPoint.method)) {
                org.opentaint.dataflow.ap.ifds.NormalMethodAnalyzer(runner, methodEntryPoint, taintRulesStatsSamplingPeriod)
            } else {
                val methodExitPoints = runner.graph.exitPoints(methodEntryPoint.method).toList()

                check(methodExitPoints.isEmpty() || methodEntryPoint.statement in methodExitPoints) {
                    "Empty method entry point not in exit points"
                }

                org.opentaint.dataflow.ap.ifds.EmptyMethodAnalyzer(runner, methodEntryPoint)
            }
            analyzers.add(analyzer)
            return true
        }
        return false
    }

    fun getAnalyzer(methodEntryPoint: org.opentaint.dataflow.ap.ifds.MethodEntryPoint): org.opentaint.dataflow.ap.ifds.MethodAnalyzer {
        val epIdx = entryPoints.getValue(methodEntryPoint)
        return analyzers[epIdx]
    }

    fun collectStats(stats: org.opentaint.dataflow.ap.ifds.MethodStats) {
        analyzers.concurrentReadSafeForEach { _, methodAnalyzer ->
            methodAnalyzer.collectStats(stats)
        }
    }
}
