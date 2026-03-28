package org.opentaint.dataflow.go.graph

import org.opentaint.ir.go.api.GoIRBody
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.api.GoIRProgram
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.util.analysis.ApplicationGraph

class GoApplicationGraph(
    val cp: GoIRProgram
) : ApplicationGraph<GoIRFunction, GoIRInst> {
    override fun callees(node: GoIRInst): Sequence<GoIRFunction> {
        TODO("Not yet implemented")
    }

    override fun callers(method: GoIRFunction): Sequence<GoIRInst> {
        TODO("Not yet implemented")
    }

    override fun methodOf(node: GoIRInst): GoIRFunction =
        node.location.functionBody.function

    override fun methodGraph(method: GoIRFunction): ApplicationGraph.MethodGraph<GoIRFunction, GoIRInst> {
        val body = method.body ?: TODO("No body")
        return GoFunctionGraph(this, method, body)
    }

    class GoFunctionGraph(
        override val applicationGraph: GoApplicationGraph,
        override val method: GoIRFunction,
        val body: GoIRBody
    ) : ApplicationGraph.MethodGraph<GoIRFunction, GoIRInst> {
        override fun predecessors(node: GoIRInst): Sequence<GoIRInst> =
            body.instGraph.predecessors(node).asSequence()

        override fun successors(node: GoIRInst): Sequence<GoIRInst> =
            body.instGraph.successors(node).asSequence()

        override fun entryPoints(): Sequence<GoIRInst> =
            body.instGraph.entries.asSequence()

        override fun exitPoints(): Sequence<GoIRInst> =
            body.instGraph.exits.asSequence()

        override fun statements(): Sequence<GoIRInst> =
            body.instructions.asSequence()
    }
}
