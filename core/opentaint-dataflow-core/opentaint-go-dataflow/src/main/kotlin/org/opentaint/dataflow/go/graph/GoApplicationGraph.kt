package org.opentaint.dataflow.go.graph

import org.opentaint.dataflow.go.GoCallResolver
import org.opentaint.dataflow.go.GoFlowFunctionUtils
import org.opentaint.ir.go.api.GoIRBody
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.api.GoIRProgram
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.util.analysis.ApplicationGraph

class GoApplicationGraph(
    val cp: GoIRProgram
) : ApplicationGraph<GoIRFunction, GoIRInst> {

    internal val callResolver = GoCallResolver(cp)

    override fun callees(node: GoIRInst): Sequence<GoIRFunction> {
        val callInfo = GoFlowFunctionUtils.extractCallInfo(node) ?: return emptySequence()
        return callResolver.resolve(callInfo, node).asSequence()
    }

    override fun callers(method: GoIRFunction): Sequence<GoIRInst> {
        // Scan all functions for call instructions that resolve to this method.
        // O(n) across all instructions — acceptable for MVP.
        return cp.allFunctions().asSequence()
            .filter { it.body != null }
            .flatMap { func ->
                func.body!!.instructions.asSequence().filter { inst ->
                    val callInfo = GoFlowFunctionUtils.extractCallInfo(inst)
                    callInfo != null && callResolver.resolve(callInfo, inst).any { it == method }
                }
            }
    }

    override fun methodOf(node: GoIRInst): GoIRFunction =
        node.location.functionBody.function

    override fun methodGraph(method: GoIRFunction): ApplicationGraph.MethodGraph<GoIRFunction, GoIRInst> {
        val body = method.body ?: error("Function ${method.fullName} has no body")
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
