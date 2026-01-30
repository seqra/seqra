package org.opentaint.dataflow.graph

import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.IntComparator
import java.util.BitSet

class CompactGraphTopSort(val graph: CompactGraph) {
    private val startTime = IntArray(graph.size)
    private val endTime = IntArray(graph.size)

    fun graphInTopOrder(startNode: Int): IntArray {
        findBackEdges(startNode)
        return createTopSortFromStart(startNode)
    }

    fun graphTopOrderNodePriority(startNode: Int): Int2IntMap {
        val topOrder = graphInTopOrder(startNode)

        val priority = Int2IntOpenHashMap().also { it.defaultReturnValue(NO_NODE) }
        for (i in topOrder.indices) {
            val node = topOrder[i]
            if (node == NO_NODE) break

            priority.put(node, i)
        }
        return priority
    }

    fun graphTopOrderNodeComparator(startNode: Int): IntComparator {
        val topOrderPriority = graphTopOrderNodePriority(startNode)
        return IntComparator { k1: Int, k2: Int ->
            val k1Priority = topOrderPriority.get(k1)
            check(k1Priority != NO_NODE) {
                "Node: $k1 missed in top order"
            }

            val k2Priority = topOrderPriority.get(k2)
            check(k2Priority != NO_NODE) {
                "Node: $k2 missed in top order"
            }

            k1Priority.compareTo(k2Priority)
        }
    }

    private data class FinderAction(val node: Int, val isForward: Boolean)

    private fun findBackEdges(startNode: Int) {
        var time = 0
        val visited = BitSet(graph.size)

        val unprocessed = mutableListOf(FinderAction(startNode, isForward = true))

        while (unprocessed.isNotEmpty()) {
            val action = unprocessed.removeLast()

            if (!action.isForward) {
                endTime[action.node] = time++
                continue
            }

            if (visited.get(action.node)) continue
            visited.set(action.node)

            val node = action.node
            startTime[node] = time++
            unprocessed.add(FinderAction(node, isForward = false))

            graph.forEachSuccessor(node) { successor ->
                if (!visited.get(successor)) {
                    unprocessed.add(FinderAction(successor, isForward = true))
                }
            }
        }
    }

    private fun isBackEdge(edgeFrom: Int, edgeTo: Int): Boolean {
        if (edgeFrom == edgeTo) return true
        return startTime[edgeFrom] > startTime[edgeTo] && endTime[edgeFrom] < endTime[edgeTo]
    }

    private fun createTopSortFromStart(startNode: Int): IntArray {
        var resultPos = 0
        val result = IntArray(graph.size)

        val currentNodeDegree = IntArray(graph.size)
        for (node in 0 until graph.size) {
            graph.forEachSuccessor(node) { successor ->
                if (isBackEdge(node, successor)) return@forEachSuccessor

                currentNodeDegree[successor]++
            }
        }

        check(currentNodeDegree[startNode] == 0)

        val unprocessed = mutableListOf<Int>()
        unprocessed.add(startNode)

        while (unprocessed.isNotEmpty()) {
            val node = unprocessed.removeLast()
            result[resultPos++] = node

            graph.forEachSuccessor(node) { successor ->
                if (isBackEdge(node, successor)) return@forEachSuccessor

                if (--currentNodeDegree[successor] == 0) {
                    unprocessed.add(successor)
                }
            }
        }

        if (resultPos != result.size) {
            result[resultPos] = NO_NODE
        }

        return result
    }

    companion object {
        const val NO_NODE = -1
    }
}
