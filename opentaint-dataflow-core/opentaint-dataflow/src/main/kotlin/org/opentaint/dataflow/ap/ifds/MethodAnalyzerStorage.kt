package org.opentaint.dataflow.ap.ifds

import org.opentaint.dataflow.util.concurrentReadSafeForEach
import org.opentaint.dataflow.util.getOrCreateIndex
import org.opentaint.dataflow.util.getValue
import org.opentaint.dataflow.util.object2IntMap

class MethodAnalyzerStorage(
    private val languageManager: LanguageManager,
    private val taintRulesStatsSamplingPeriod: Int?
) {
    private val entryPoints = object2IntMap<MethodEntryPoint>()
    private val analyzers = arrayListOf<MethodAnalyzer>()

    fun add(runner: TaintAnalysisUnitRunner, methodEntryPoint: MethodEntryPoint): Boolean {
        entryPoints.getOrCreateIndex(methodEntryPoint) {
            val analyzer = if (!languageManager.isEmpty(methodEntryPoint.method)) {
                NormalMethodAnalyzer(runner, methodEntryPoint, taintRulesStatsSamplingPeriod)
            } else {
                val methodExitPoints = runner.graph.exitPoints(methodEntryPoint.method).toList()

                check(methodExitPoints.isEmpty() || methodEntryPoint.statement in methodExitPoints) {
                    "Empty method entry point not in exit points"
                }

                EmptyMethodAnalyzer(runner, methodEntryPoint)
            }
            analyzers.add(analyzer)
            return true
        }
        return false
    }

    fun getAnalyzer(methodEntryPoint: MethodEntryPoint): MethodAnalyzer {
        val epIdx = entryPoints.getValue(methodEntryPoint)
        return analyzers[epIdx]
    }

    fun forEachAnalyzer(body: (MethodAnalyzer) -> Unit) {
        analyzers.concurrentReadSafeForEach { _, methodAnalyzer ->
            body(methodAnalyzer)
        }
    }
}
