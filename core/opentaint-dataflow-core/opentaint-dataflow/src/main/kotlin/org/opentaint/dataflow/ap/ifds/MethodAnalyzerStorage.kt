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
    private val analyzers = arrayListOf<MethodAnalyzer?>()

    fun add(runner: TaintAnalysisUnitRunner, methodEntryPoint: MethodEntryPoint): Boolean {
        entryPoints.getOrCreateIndex(methodEntryPoint) { analyzerIdx ->
            analyzers.add(null)

            val analyzer = if (!languageManager.isEmpty(methodEntryPoint.method)) {
                val emptyContextAnalyzer = getOrCreateMethodAnalyzerWithEmptyContext(runner, methodEntryPoint)
                NormalMethodAnalyzer(runner, methodEntryPoint, taintRulesStatsSamplingPeriod, emptyContextAnalyzer)
            } else {
                val methodExitPoints = runner.graph.methodGraph(methodEntryPoint.method).exitPoints().toList()

                check(methodExitPoints.isEmpty() || methodEntryPoint.statement in methodExitPoints) {
                    "Empty method entry point not in exit points"
                }

                EmptyMethodAnalyzer(runner, methodEntryPoint)
            }
            analyzers[analyzerIdx] = analyzer
            return true
        }
        return false
    }

    private fun getOrCreateMethodAnalyzerWithEmptyContext(
        runner: TaintAnalysisUnitRunner,
        methodEntryPoint: MethodEntryPoint
    ): MethodAnalyzer? {
        if (methodEntryPoint.context is EmptyMethodContext) return null

        val methodWithEmptyContext = MethodEntryPoint(EmptyMethodContext, methodEntryPoint.statement)
        add(runner, methodWithEmptyContext)
        return getAnalyzer(methodWithEmptyContext)
    }

    fun getAnalyzer(methodEntryPoint: MethodEntryPoint): MethodAnalyzer {
        val epIdx = entryPoints.getValue(methodEntryPoint)
        return analyzers.getOrNull(epIdx)
            ?: error("No analyzer for $methodEntryPoint")
    }

    fun forEachAnalyzer(body: (MethodAnalyzer) -> Unit) {
        analyzers.concurrentReadSafeForEach { _, methodAnalyzer ->
            methodAnalyzer?.let { body(it) }
        }
    }
}
