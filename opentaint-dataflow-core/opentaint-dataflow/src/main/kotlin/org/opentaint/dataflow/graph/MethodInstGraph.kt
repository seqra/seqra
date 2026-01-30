package org.opentaint.dataflow.graph

import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.util.toBitSet
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.util.analysis.ApplicationGraph
import java.util.BitSet

class MethodInstGraph(
    val instructions: Array<CommonInst>,
    val graph: CompactGraph,
    val exitPoints: BitSet,
) {
    inline fun forEachSuccessor(languageManager: LanguageManager, inst: CommonInst, body: (CommonInst) -> Unit) =
        graph.forEachSuccessor(languageManager.getInstIndex(inst)) { body(instructions[it]) }

    inline fun forEachPredecessor(languageManager: LanguageManager, inst: CommonInst, body: (CommonInst) -> Unit) =
        graph.forEachPredecessor(languageManager.getInstIndex(inst)) { body(instructions[it]) }

    fun isExitPoint(languageManager: LanguageManager, inst: CommonInst): Boolean =
        exitPoints.get(languageManager.getInstIndex(inst))

    companion object {
        fun build(
            languageManager: LanguageManager,
            graph: ApplicationGraph<CommonMethod, CommonInst>,
            method: CommonMethod
        ): MethodInstGraph {
            val methodGraph = graph.methodGraph(method)
            val graphSize = languageManager.getMaxInstIndex(method) + 1
            val instructions = Array(graphSize) { languageManager.getInstByIndex(method, it) }

            val resultGraph = CompactGraph.build(
                graphSize = graphSize,
                getInstSuccessors = { s ->
                    methodGraph.successors(instructions[s]).asIterable()
                        .toBitSet { languageManager.getInstIndex(it) }
                }
            )

            val exitPoints = methodGraph.exitPoints().toList().toBitSet { languageManager.getInstIndex(it) }
            return MethodInstGraph(instructions, resultGraph, exitPoints)
        }
    }
}
