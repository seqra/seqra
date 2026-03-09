package org.opentaint.dataflow.graph

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.printer.PrintableGraph
import org.opentaint.dataflow.util.toBitSet
import org.opentaint.dataflow.util.toSet
import java.util.BitSet

class IntGraph {
    private val successors = Int2ObjectOpenHashMap<BitSet>()

    fun addEdge(from: Int, to: Int) {
        var current = successors.get(from)
        if (current == null) {
            current = BitSet().also { successors.put(from, it) }
        }
        current.set(to)
    }

    fun nonTrivialSccs(): List<BitSet> {
        val allSccs = allSccs()
        return allSccs.filter { component ->
            if (component.cardinality() > 1) return@filter true

            val element = component.nextSetBit(0)
            val elementSuccessors = successors[element]
            elementSuccessors != null && elementSuccessors.get(element)
        }
    }

    private fun allNodes(): BitSet{
        val allNodes = successors.keys.toBitSet()
        successors.values.forEach { allNodes.or(it) }
        return allNodes
    }

    fun allSccs(): List<BitSet> {
        val allNodes = allNodes()

        var index = 0
        val nodeIndex = Int2IntOpenHashMap().apply { defaultReturnValue(NO_VALUE) }
        val nodeLowLink = Int2IntOpenHashMap().apply { defaultReturnValue(NO_VALUE) }
        val onStack = BitSet()
        val stack = IntArrayList()
        val result = mutableListOf<BitSet>()

        fun strongConnect(v: Int) {
            nodeIndex[v] = index
            nodeLowLink[v] = index
            index++
            stack.add(v)
            onStack.set(v)

            val vSuccessors = successors[v]
            vSuccessors?.forEach { w ->
                val indexed = nodeIndex[w]
                if (indexed == NO_VALUE) {
                    strongConnect(w)
                    nodeLowLink[v] = minOf(nodeLowLink[v], nodeLowLink[w])
                } else if (onStack.get(w)) {
                    nodeLowLink[v] = minOf(nodeLowLink[v], indexed)
                }
            }

            if (nodeLowLink[v] == nodeIndex[v]) {
                val component = BitSet()
                do {
                    val w = stack.removeInt(stack.lastIndex)
                    onStack.clear(w)
                    component.set(w)
                } while (w != v)

                result.add(component)
            }
        }

        allNodes.forEach { node ->
            if (nodeIndex[node] == NO_VALUE) {
                strongConnect(node)
            }
        }

        return result
    }

    @Suppress("unused")
    fun print() = Printable()

    inner class Printable: PrintableGraph<Int, Unit>{
        override fun allNodes(): List<Int> = this@IntGraph.allNodes().toSet().toList()
        override fun nodeLabel(node: Int): String = "$node"

        override fun successors(node: Int): List<Pair<Unit, Int>> =
            successors[node]?.toSet()?.map { Unit to it }.orEmpty()

        override fun edgeLabel(edge: Unit): String = ""
    }

    companion object {
        private const val NO_VALUE = -1
    }
}
