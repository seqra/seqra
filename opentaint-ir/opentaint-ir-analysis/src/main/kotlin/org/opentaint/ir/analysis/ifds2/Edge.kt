package org.opentaint.ir.analysis.ifds2

import org.opentaint.ir.analysis.engine.IfdsEdge
import org.opentaint.ir.analysis.ifds2.taint.TaintFact
import org.opentaint.ir.api.JIRMethod

data class Edge<out Fact>(
    val from: Vertex<Fact>,
    val to: Vertex<Fact>,
) {
    init {
        require(from.method == to.method)
    }

    val method: JIRMethod
        get() = from.method

    companion object {
        // constructor
        operator fun invoke(edge: IfdsEdge): Edge<TaintFact> {
            return Edge(Vertex(edge.from), Vertex(edge.to))
        }
    }
}

fun Edge<TaintFact>.toIfds(): IfdsEdge = IfdsEdge(from.toIfds(), to.toIfds())

sealed class Reason {

    object Initial : Reason()

    object External : Reason()

    data class Sequent<Fact>(
        val edge: Edge<Fact>,
    ) : Reason()

    data class CallToStart<Fact>(
        val edge: Edge<Fact>,
    ) : Reason()

    data class ThroughSummary<Fact>(
        val edge: Edge<Fact>,
        val summaryEdge: Edge<Fact>,
    ) : Reason()

}
