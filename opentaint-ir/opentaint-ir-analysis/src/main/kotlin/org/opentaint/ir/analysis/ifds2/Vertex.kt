package org.opentaint.ir.analysis.ifds2

import org.opentaint.ir.analysis.engine.IfdsVertex
import org.opentaint.ir.analysis.ifds2.taint.TaintFact
import org.opentaint.ir.analysis.ifds2.taint.toDomainFact
import org.opentaint.ir.analysis.ifds2.taint.toFact
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.cfg.JIRInst

data class Vertex<out Fact>(
    val statement: JIRInst,
    val fact: Fact,
) {
    val method: JIRMethod
        get() = statement.location.method

    companion object {
        // constructor
        operator fun invoke(vertex: IfdsVertex): Vertex<TaintFact> {
            return Vertex(vertex.statement, vertex.domainFact.toFact())
        }
    }
}

fun Vertex<TaintFact>.toIfds(): IfdsVertex =
    IfdsVertex(statement, fact.toDomainFact())
