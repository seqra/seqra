package org.opentaint.dataflow.graph

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.toBitSet
import org.opentaint.util.analysis.ApplicationGraph
import java.util.BitSet

class MethodInstGraph(
    val languageManager: LanguageManager,
    val instructions: Array<CommonInst>,
    val successors: IntArray,
    val multipleSuccessors: Array<BitSet?>,
    val exitPoints: BitSet,
) {
    inline fun forEachSuccessor(inst: CommonInst, body: (CommonInst) -> Unit) {
        val instIdx = languageManager.getInstIndex(inst)
        val instSuccessors = successors[instIdx]

        if (instSuccessors == NO_SUCCESSORS) return

        if (instSuccessors != MULTIPLE_SUCCESSORS) {
            body(instructions[instSuccessors])
            return
        }

        multipleSuccessors[instIdx]?.forEach {
            body(instructions[it])
        }
    }

    fun isExitPoint(inst: CommonInst): Boolean =
        exitPoints.get(languageManager.getInstIndex(inst))

    companion object {
        const val NO_SUCCESSORS = -1
        const val MULTIPLE_SUCCESSORS = -2

        fun build(
            languageManager: LanguageManager,
            graph: ApplicationGraph<CommonMethod, CommonInst>,
            method: CommonMethod
        ): MethodInstGraph {
            val successorsSize = languageManager.getMaxInstIndex(method) + 1

            val successors = IntArray(successorsSize)
            val multipleSuccessors = arrayOfNulls<BitSet>(successorsSize)
            val instructions = Array(successorsSize) {
                languageManager.getInstByIndex(method, it)
            }

            for (i in 0 until successorsSize) {
                val inst = instructions[i]
                val instSuccessors = graph.successors(inst).toList()

                when (instSuccessors.size) {
                    0 -> successors[i] = NO_SUCCESSORS
                    1 -> {
                        val successorIdx = languageManager.getInstIndex(instSuccessors.single())
                        successors[i] = successorIdx
                    }

                    else -> {
                        val successorIdx = instSuccessors.toBitSet { languageManager.getInstIndex(it) }
                        multipleSuccessors[i] = successorIdx
                        successors[i] = MULTIPLE_SUCCESSORS
                    }
                }
            }

            val exitPoints = graph.exitPoints(method).toList().toBitSet { languageManager.getInstIndex(it) }

            return MethodInstGraph(
                languageManager, instructions,
                successors, multipleSuccessors, exitPoints
            )
        }
    }
}
