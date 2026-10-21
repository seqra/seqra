package org.opentaint.dataflow.python.graph

import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRClasspath
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.ir.api.python.PIRInstruction
import org.opentaint.util.analysis.ApplicationGraph

class PIRApplicationGraph(
    val cp: PIRClasspath,
) : ApplicationGraph<PIRFunction, PIRInstruction> {

    override fun callees(node: PIRInstruction): Sequence<PIRFunction> {
        if (node !is PIRCall) return emptySequence()
        val calleeName = node.resolvedCallee ?: return emptySequence()
        val fn = cp.findFunctionOrNull(calleeName) ?: return emptySequence()
        return sequenceOf(fn)
    }

    override fun callers(method: PIRFunction): Sequence<PIRInstruction> =
        emptySequence()

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
