package org.opentaint.ir.analysis.impl.custom

import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRBasicBlock
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstRef
import org.opentaint.ir.api.cfg.JIRValue
import org.opentaint.ir.impl.cfg.JIRBlockGraphImpl
import java.util.*
import kotlin.collections.ArrayDeque

class ReachingDefinitionsAnalysis(val blockGraph: JIRBlockGraphImpl) {
    val jIRGraph get() = blockGraph.jIRGraph

    private val nDefinitions = jIRGraph.instructions.size
    private val ins = mutableMapOf<JIRBasicBlock, BitSet>()
    private val outs = mutableMapOf<JIRBasicBlock, BitSet>()
    private val assignmentsMap = mutableMapOf<JIRValue, MutableSet<JIRInstRef>>()

    init {
        initAssignmentsMap()
        val entry = blockGraph.entry
        for (block in blockGraph)
            outs[block] = emptySet()

        val queue = ArrayDeque<JIRBasicBlock>().also { it += entry }
        val notVisited = blockGraph.toMutableSet()
        while (queue.isNotEmpty() || notVisited.isNotEmpty()) {
            val current = when {
                queue.isNotEmpty() -> queue.removeFirst()
                else -> notVisited.random()
            }
            notVisited -= current

            ins[current] = fullPredecessors(current).map { outs[it]!! }.fold(emptySet()) { acc, bitSet ->
                acc.or(bitSet)
                acc
            }

            val oldOut = outs[current]!!.clone() as BitSet
            val newOut = gen(current)

            if (oldOut != newOut) {
                outs[current] = newOut
                for (successor in fullSuccessors(current)) {
                    queue += successor
                }
            }
        }
    }

    private fun initAssignmentsMap() {
        for (inst in jIRGraph) {
            if (inst is JIRAssignInst) {
                assignmentsMap.getOrPut(inst.lhv, ::mutableSetOf) += jIRGraph.ref(inst)
            }
        }
    }

    private fun emptySet(): BitSet = BitSet(nDefinitions)

    private fun gen(block: JIRBasicBlock): BitSet {
        val inSet = ins[block]!!.clone() as BitSet
        for (inst in blockGraph.instructions(block)) {
            if (inst is JIRAssignInst) {
                for (kill in assignmentsMap.getOrDefault(inst.lhv, mutableSetOf())) {
                    inSet[kill] = false
                }
                inSet[jIRGraph.ref(inst)] = true
            }
        }
        return inSet
    }

    private fun fullPredecessors(block: JIRBasicBlock) = blockGraph.predecessors(block) + blockGraph.throwers(block)
    private fun fullSuccessors(block: JIRBasicBlock) = blockGraph.successors(block) + blockGraph.catchers(block)

    private operator fun BitSet.set(ref: JIRInstRef, value: Boolean) {
        this.set(ref.index, value)
    }

    fun outs(block: JIRBasicBlock): List<JIRInst> {
        val defs = outs.getOrDefault(block, emptySet())
        return (0 until nDefinitions).filter { defs[it] }.map { jIRGraph.instructions[it] }
    }

}