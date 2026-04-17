package org.opentaint.dataflow.python.graph

import org.opentaint.ir.api.python.*
import org.opentaint.util.analysis.ApplicationGraph

class PIRApplicationGraph(
    val cp: PIRClasspath,
) : ApplicationGraph<PIRFunction, PIRInstruction> {

    override fun callees(node: PIRInstruction): Sequence<PIRFunction> {
        if (node !is PIRCall) return emptySequence()
        val calleeName = node.resolvedCallee ?: return emptySequence()

        // Primary: direct lookup by qualified name
        val fn = cp.findFunctionOrNull(calleeName)
        if (fn != null) return sequenceOf(fn)

        // Fallback: for nested function calls, mypy may set resolvedCallee to just
        // the short name (e.g. "process" instead of "Module.outer.process").
        // Try prepending the enclosing method's qualified name.
        if ("." !in calleeName) {
            val enclosingMethod = node.location.method
            val candidate = "${enclosingMethod.qualifiedName}.$calleeName"
            val nested = cp.findFunctionOrNull(candidate)
            if (nested != null) return sequenceOf(nested)
        }

        return emptySequence()
    }

    override fun callers(method: PIRFunction): Sequence<PIRInstruction> =
        emptySequence()  // Not needed for forward analysis

    override fun methodOf(node: PIRInstruction): PIRFunction =
        node.location.method

    override fun methodGraph(method: PIRFunction): ApplicationGraph.MethodGraph<PIRFunction, PIRInstruction> =
        PIRFunctionGraph(method, this)

    class PIRFunctionGraph(
        override val method: PIRFunction,
        override val applicationGraph: ApplicationGraph<PIRFunction, PIRInstruction>,
    ) : ApplicationGraph.MethodGraph<PIRFunction, PIRInstruction> {

        private val flatInstructions: List<PIRInstruction> get() = method.instList

        private val succs: Map<PIRInstruction, List<PIRInstruction>> by lazy {
            buildSuccessors()
        }

        private val preds: Map<PIRInstruction, List<PIRInstruction>> by lazy {
            buildPredecessors()
        }

        private fun buildSuccessors(): Map<PIRInstruction, List<PIRInstruction>> {
            val result = hashMapOf<PIRInstruction, List<PIRInstruction>>()

            flatInstructions.forEach{ inst ->
                result[inst] = method.cfg.successors(inst)
            }

            return result
        }

        private fun buildPredecessors(): Map<PIRInstruction, List<PIRInstruction>> {
            val result = java.util.IdentityHashMap<PIRInstruction, MutableList<PIRInstruction>>()
            for ((inst, successors) in succs) {
                for (succ in successors) {
                    result.getOrPut(succ) { mutableListOf() }.add(inst)
                }
            }
            return result
        }

        override fun predecessors(node: PIRInstruction): Sequence<PIRInstruction> =
            (preds[node] ?: emptyList()).asSequence()

        override fun successors(node: PIRInstruction): Sequence<PIRInstruction> =
            (succs[node] ?: emptyList()).asSequence()

        override fun entryPoints(): Sequence<PIRInstruction> =
            method.cfg.entry.let { sequenceOf(it) }

        override fun exitPoints(): Sequence<PIRInstruction> =
            method.cfg.exits.asSequence()

        override fun statements(): Sequence<PIRInstruction> =
            flatInstructions.asSequence()
    }
}
