package org.opentaint.dataflow.python.graph

import org.opentaint.ir.api.python.*
import org.opentaint.util.analysis.ApplicationGraph

class PIRApplicationGraph(
    val cp: PIRClasspath,
) : ApplicationGraph<PIRFunction, PIRInstruction> {

    override fun callees(node: PIRInstruction): Sequence<PIRFunction> {
        if (node !is PIRCall) return emptySequence()
        val calleeName = node.resolvedCallee ?: return emptySequence()
        val fn = cp.findFunctionOrNull(calleeName)
        return if (fn != null) sequenceOf(fn) else emptySequence()
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

        private val flatInstructions: List<PIRInstruction> by lazy {
            method.cfg.blocks.sortedBy { it.label }.flatMap { it.instructions }
        }

        private val succs: Map<PIRInstruction, List<PIRInstruction>> by lazy {
            buildSuccessors()
        }

        private val preds: Map<PIRInstruction, List<PIRInstruction>> by lazy {
            buildPredecessors()
        }

        private fun buildSuccessors(): Map<PIRInstruction, List<PIRInstruction>> {
            val cfg = method.cfg
            val result = java.util.IdentityHashMap<PIRInstruction, List<PIRInstruction>>()

            for (block in cfg.blocks) {
                val insts = block.instructions
                if (insts.isEmpty()) continue

                // Within-block successors
                for (i in 0 until insts.size - 1) {
                    result[insts[i]] = listOf(insts[i + 1])
                }

                // Last instruction → successor blocks' first instructions
                val terminator = insts.last()
                val succBlocks = cfg.successors(block)
                result[terminator] = succBlocks.mapNotNull { it.instructions.firstOrNull() }
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
            method.cfg.entry.instructions.firstOrNull()
                ?.let { sequenceOf(it) } ?: emptySequence()

        override fun exitPoints(): Sequence<PIRInstruction> =
            method.cfg.exits.asSequence()
                .mapNotNull { it.instructions.lastOrNull() }

        override fun statements(): Sequence<PIRInstruction> =
            flatInstructions.asSequence()
    }
}
