package org.opentaint.dataflow.python

import org.opentaint.dataflow.python.graph.PIRApplicationGraph
import org.opentaint.ir.api.python.PIRAssign
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.ir.api.python.PIRInstruction
import org.opentaint.ir.api.python.PIRLoadAttr
import org.opentaint.ir.api.python.PIRLocal
import org.opentaint.ir.api.python.PIRParameter
import org.opentaint.ir.api.python.targets

abstract class PIRMethodIntraproceduralWalker<V : Any>(
    val method: PIRFunction,
    applicationGraph: PIRApplicationGraph,
) {
    protected val graph = applicationGraph.methodGraph(method)

    private val queue = mutableListOf<Pair<PIRInstruction, Fact<V>>>()
    private val storage = MutableList(method.instList.size) { hashSetOf<Fact<V>>() }

    protected fun walk() {
        for (param in method.parameters) {
            val values = seedParameter(param)
            graph.entryPoints().forEach { entry -> values.forEach { addEntry(entry, Fact(param.index, it)) } }
        }
        for (inst in graph.statements()) {
            // seed runs before the target check: it may record names for target-less calls
            val values = seed(inst)
            val targetIdx = inst.targets.singleOrNull()?.index ?: continue
            values.forEach { propagateToSuccessors(inst, Fact(targetIdx, it)) }
        }
        while (queue.isNotEmpty()) {
            val (inst, fact) = queue.removeLast()
            transfer(inst, fact).forEach { propagateToSuccessors(inst, it) }
        }
    }

    protected abstract fun seed(inst: PIRInstruction): List<V>

    protected open fun seedParameter(param: PIRParameter): List<V> = emptyList()

    protected open fun attributeRead(inst: PIRLoadAttr, obj: V): List<V> = emptyList()

    protected open fun call(inst: PIRCall, callee: V): List<V> = emptyList()

    private fun transfer(inst: PIRInstruction, fact: Fact<V>): List<Fact<V>> = buildList {
        val idx = fact.idx

        when (inst) {
            is PIRLoadAttr -> if ((inst.obj as? PIRLocal)?.index == idx) {
                attributeRead(inst, fact.value).forEach { this += Fact(inst.target.index, it) }
            }

            is PIRCall -> if ((inst.callee as? PIRLocal)?.index == idx) {
                val results = call(inst, fact.value)
                inst.target?.let { target -> results.forEach { this += Fact(target.index, it) } }
            }

            is PIRAssign -> if ((inst.expr as? PIRLocal)?.index == idx) {
                this += Fact(inst.target.index, fact.value)
            }

            else -> {}
        }

        if (inst.targets.none { it.index == idx }) {
            this += fact
        }
    }

    private fun propagateToSuccessors(inst: PIRInstruction, fact: Fact<V>) {
        graph.successors(inst).forEach { addEntry(it, fact) }
    }

    private fun addEntry(inst: PIRInstruction, fact: Fact<V>) {
        if (storage[inst.location.index].add(fact)) {
            queue += inst to fact
        }
    }

    private data class Fact<V>(val idx: Int, val value: V)
}
