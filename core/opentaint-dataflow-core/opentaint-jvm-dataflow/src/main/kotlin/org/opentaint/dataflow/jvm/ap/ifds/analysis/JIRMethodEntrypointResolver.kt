package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.ap.ifds.analysis.MethodEntrypointResolver
import org.opentaint.dataflow.jvm.ap.ifds.jIRDowncast
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.jvm.graph.JApplicationGraph

class JIRMethodEntrypointResolver(private val graph: JApplicationGraph) : MethodEntrypointResolver {
    override fun resolveEntryPoints(method: CommonMethod, context: MethodContext): List<JIRInst> {
        jIRDowncast<JIRMethod>(method)
        return graph.methodGraph(method).entryPoints().toList()
    }
}
