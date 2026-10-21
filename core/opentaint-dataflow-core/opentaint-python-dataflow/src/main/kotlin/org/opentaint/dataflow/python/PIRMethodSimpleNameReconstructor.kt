package org.opentaint.dataflow.python

import org.opentaint.dataflow.python.graph.PIRApplicationGraph
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.ir.api.python.PIRInstruction
import org.opentaint.ir.api.python.PIRLoadAttr

class PIRMethodSimpleNameReconstructor private constructor(
    method: PIRFunction,
    applicationGraph: PIRApplicationGraph,
) : PIRMethodIntraproceduralWalker<String>(method, applicationGraph) {

    private val result = mutableMapOf<PIRCall, MutableSet<String>>()

    private fun compute(): Map<PIRCall, Set<String>> {
        walk()
        return result
    }

    override fun seed(inst: PIRInstruction): List<String> = when (inst) {
        is PIRLoadAttr -> listOf(inst.attribute)
        else -> emptyList()
    }

    override fun call(inst: PIRCall, callee: String): List<String> {
        result.getOrPut(inst) { hashSetOf() }.add(callee)
        return emptyList()
    }

    companion object {
        fun compute(method: PIRFunction, applicationGraph: PIRApplicationGraph): Map<PIRCall, Set<String>> =
            PIRMethodSimpleNameReconstructor(method, applicationGraph).compute()
    }
}
