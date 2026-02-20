package org.opentaint.dataflow.graph

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntHeapPriorityQueue
import org.opentaint.dataflow.util.add
import java.util.BitSet

inline fun <reified State : Any> simulateGraph(
    graph: CompactGraph,
    initialStmtIdx: Int,
    initialState: State,
    merge: (Int, Int2ObjectMap<State?>) -> State,
    eval: (Int, State) -> State,
): Array<State?> {
    if (graph.size == 0) return emptyArray<State?>()
    val statesAfter = arrayOfNulls<State>(graph.size)
    simulateGraph(statesAfter, graph, initialStmtIdx, initialState, merge, eval)
    return statesAfter
}

inline fun <reified State : Any> simulateGraph(
    statesAfter: Array<State?>,
    graph: CompactGraph,
    initialStmtIdx: Int,
    initialState: State,
    merge: (Int, Int2ObjectMap<State?>) -> State,
    eval: (Int, State) -> State,
) {
    val topOrderComparator = CompactGraphTopSort(graph).graphTopOrderNodeComparator(initialStmtIdx)

    val enqueuedStmts = BitSet()
    val unprocessed = IntHeapPriorityQueue(topOrderComparator)

    unprocessed.enqueue(initialStmtIdx)
    enqueuedStmts.set(initialStmtIdx)

    while (!unprocessed.isEmpty) {
        val stmtIdx = unprocessed.dequeueInt()
        enqueuedStmts.clear(stmtIdx)

        val stateBefore = if (stmtIdx == initialStmtIdx) {
            initialState
        } else {
            val preStates = Int2ObjectOpenHashMap<State>()
            graph.forEachPredecessor(stmtIdx) { predIdx ->
                preStates.put(predIdx, statesAfter[predIdx])
            }

            when (preStates.size) {
                0 -> error("Non-initial node without predecessors")

                1 -> preStates.values.first()
                    ?: error("Node predecessor was not computed")

                else -> merge(stmtIdx, preStates)
            }
        }

        val stateAfter = eval(stmtIdx, stateBefore)

        val currentStateAfter = statesAfter[stmtIdx]
        if (currentStateAfter != null && currentStateAfter == stateAfter) {
            continue
        }
        statesAfter[stmtIdx] = stateAfter

        graph.forEachSuccessor(stmtIdx) { successorIdx ->
            if (enqueuedStmts.add(successorIdx)) {
                unprocessed.enqueue(successorIdx)
            }
        }
    }
}
