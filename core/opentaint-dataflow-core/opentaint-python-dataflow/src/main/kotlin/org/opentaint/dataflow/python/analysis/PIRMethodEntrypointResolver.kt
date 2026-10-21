package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.ap.ifds.analysis.MethodEntrypointResolver
import org.opentaint.dataflow.python.graph.PIRApplicationGraph
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.ir.api.python.PIRInstruction

class PIRMethodEntrypointResolver(private val graph: PIRApplicationGraph) : MethodEntrypointResolver {
    override fun resolveEntryPoints(method: CommonMethod, context: MethodContext): List<PIRInstruction> {
        return graph.methodGraph(method as PIRFunction).entryPoints().toList()
    }
}
