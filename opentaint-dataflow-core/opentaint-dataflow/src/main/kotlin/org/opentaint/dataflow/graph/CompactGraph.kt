package org.opentaint.dataflow.graph

import org.opentaint.dataflow.util.forEach
import java.util.BitSet

class CompactGraph(
    val successors: IntArray,
    val multipleSuccessors: Array<BitSet?>,
    val predecessors: IntArray,
    val multiplePredecessors: Array<BitSet?>,
) {
    val size: Int get() = successors.size

    inline fun forEachSuccessor(instIdx: Int, body: (Int) -> Unit) =
        forEach(successors, multipleSuccessors, instIdx, body)

    inline fun forEachPredecessor(instIdx: Int, body: (Int) -> Unit) =
        forEach(predecessors, multiplePredecessors, instIdx, body)

    inline fun forEach(
        dispatch: IntArray,
        multiple: Array<BitSet?>,
        instIdx: Int,
        body: (Int) -> Unit
    ) {
        val instDispatch = dispatch[instIdx]

        if (instDispatch == EMPTY) return

        if (instDispatch != MULTIPLE) {
            body(instDispatch)
            return
        }

        multiple[instIdx]?.forEach { body(it) }
    }

    companion object {
        const val EMPTY = -1
        const val MULTIPLE = -2

        inline fun build(
            graphSize: Int,
            getInstSuccessors: (Int) -> BitSet
        ): CompactGraph {
            val successors = IntArray(graphSize)
            val multipleSuccessors = arrayOfNulls<BitSet>(graphSize)

            val predecessors = IntArray(graphSize)
            val multiplePredecessors = arrayOfNulls<BitSet>(graphSize)

            val uncompressedPredecessors = arrayOfNulls<BitSet>(graphSize)

            for (i in 0 until graphSize) {
                val instSuccessors = getInstSuccessors(i)
                write(i, instSuccessors, successors, multipleSuccessors)

                instSuccessors.forEach { successorIdx ->
                    var successorPredecessors = uncompressedPredecessors[successorIdx]
                    if (successorPredecessors == null) {
                        successorPredecessors = BitSet().also { uncompressedPredecessors[successorIdx] = it }
                    }

                    successorPredecessors.set(i)
                }
            }

            for (i in 0 until graphSize) {
                val instPredecessors = uncompressedPredecessors[i]
                write(i, instPredecessors, predecessors, multiplePredecessors)
            }

            return CompactGraph(
                successors, multipleSuccessors,
                predecessors, multiplePredecessors
            )
        }

        fun write(
            idx: Int,
            stmtIdx: BitSet?,
            dispatch: IntArray,
            multiple: Array<BitSet?>
        ) {
            val size = stmtIdx?.cardinality() ?: 0
            when (size) {
                0 -> dispatch[idx] = EMPTY
                1 -> {
                    dispatch[idx] = stmtIdx!!.nextSetBit(0)
                }

                else -> {
                    multiple[idx] = stmtIdx
                    dispatch[idx] = MULTIPLE
                }
            }
        }
    }
}
