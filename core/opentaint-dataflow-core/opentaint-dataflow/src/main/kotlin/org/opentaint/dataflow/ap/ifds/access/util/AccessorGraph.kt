package org.opentaint.dataflow.ap.ifds.access.util

import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.graph.IntGraph
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.printer.PrintableGraph

class AccessorGraph(private val interner: AccessorInterner = AccessorInterner()) {
    private val graph = IntGraph()

    fun addEdge(from: Accessor, to: Accessor) {
        graph.addEdge(interner.index(from), interner.index(to))
    }

    fun nonTrivialConnectedComponents(): List<Set<Accessor>>{
        val accessorScc = graph.nonTrivialSccs()
        return accessorScc.map { component ->
            val componentAccessors = hashSetOf<Accessor>()
            component.forEach {
                val accessor = interner.accessor(it) ?: error("Interner failure")
                componentAccessors.add(accessor)
            }
            componentAccessors
        }
    }

    @Suppress("unused")
    fun print() = Printer(graph.print())

    inner class Printer(val graphPrinter: IntGraph.Printable) : PrintableGraph<Int, Unit> {
        override fun nodeLabel(node: Int): String {
            val accessor = interner.accessor(node) ?: error("Interner failure")
            return accessor.toString()
        }

        override fun allNodes(): List<Int> = graphPrinter.allNodes()
        override fun successors(node: Int): List<Pair<Unit, Int>> = graphPrinter.successors(node)
        override fun edgeLabel(edge: Unit): String = graphPrinter.edgeLabel(edge)
    }
}
