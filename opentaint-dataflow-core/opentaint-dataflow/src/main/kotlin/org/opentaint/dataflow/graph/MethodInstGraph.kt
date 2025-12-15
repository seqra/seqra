package org.opentaint.dataflow.graph

import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.toBitSet
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.util.analysis.ApplicationGraph
import java.util.BitSet

class MethodInstGraph(
    val languageManager: LanguageManager,
    val instructions: Array<CommonInst>,
    val successors: IntArray,
    val multipleSuccessors: Array<BitSet?>,
    val predecessors: IntArray,
    val multiplePredecessors: Array<BitSet?>,
    val exitPoints: BitSet,
) {
    inline fun forEachSuccessor(inst: CommonInst, body: (CommonInst) -> Unit) {
        val instIdx = languageManager.getInstIndex(inst)
        val instSuccessors = successors[instIdx]

        if (instSuccessors == EMPTY) return

        if (instSuccessors != MULTIPLE) {
            body(instructions[instSuccessors])
            return
        }

        multipleSuccessors[instIdx]?.forEach {
            body(instructions[it])
        }
    }

    inline fun forEachPredecessor(inst: CommonInst, body: (CommonInst) -> Unit) {
        val instIdx = languageManager.getInstIndex(inst)
        val instPredecessors = predecessors[instIdx]

        if (instPredecessors == EMPTY) return

        if (instPredecessors != MULTIPLE) {
            body(instructions[instPredecessors])
            return
        }

        multiplePredecessors[instIdx]?.forEach {
            body(instructions[it])
        }
    }

    fun isExitPoint(inst: CommonInst): Boolean =
        exitPoints.get(languageManager.getInstIndex(inst))

    companion object {
        const val EMPTY = -1
        const val MULTIPLE = -2

        fun build(
            languageManager: LanguageManager,
            graph: ApplicationGraph<CommonMethod, CommonInst>,
            method: CommonMethod
        ): MethodInstGraph {
            val graphSize = languageManager.getMaxInstIndex(method) + 1

            val successors = IntArray(graphSize)
            val multipleSuccessors = arrayOfNulls<BitSet>(graphSize)

            val predecessors = IntArray(graphSize)
            val multiplePredecessors = arrayOfNulls<BitSet>(graphSize)

            val instructions = Array(graphSize) {
                languageManager.getInstByIndex(method, it)
            }

            for (i in 0 until graphSize) {
                val inst = instructions[i]

                val instSuccessors = graph.successors(inst).toList()
                when (instSuccessors.size) {
                    0 -> successors[i] = EMPTY
                    1 -> {
                        successors[i] = languageManager.getInstIndex(instSuccessors.single())
                    }

                    else -> {
                        multipleSuccessors[i] = instSuccessors.toBitSet { languageManager.getInstIndex(it) }
                        successors[i] = MULTIPLE
                    }
                }

                val instPredecessors = graph.predecessors(inst).toList()
                when (instPredecessors.size) {
                    0 -> predecessors[i] = EMPTY
                    1 -> {
                        predecessors[i] = languageManager.getInstIndex(instPredecessors.single())
                    }

                    else -> {
                        multiplePredecessors[i] = instPredecessors.toBitSet { languageManager.getInstIndex(it) }
                        predecessors[i] = MULTIPLE
                    }
                }
            }

            val exitPoints = graph.exitPoints(method).toList().toBitSet { languageManager.getInstIndex(it) }

            return MethodInstGraph(
                languageManager, instructions,
                successors, multipleSuccessors,
                predecessors, multiplePredecessors,
                exitPoints
            )
        }
    }
}
