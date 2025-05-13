package org.opentaint.ir.analysis.taint

import org.opentaint.ir.analysis.ifds.Summary
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.taint.configuration.TaintMethodSink

/**
 * Represents a path edge which starts in an entrypoint
 * and ends in an exit-point of a method.
 */
data class SummaryEdge(
    val edge: TaintEdge,
) : Summary {
    override val method: JIRMethod
        get() = edge.method
}

data class Vulnerability(
    val message: String,
    val sink: TaintVertex,
    val edge: TaintEdge? = null,
    val rule: TaintMethodSink? = null,
) : Summary {
    override val method: JIRMethod
        get() = sink.method
}
