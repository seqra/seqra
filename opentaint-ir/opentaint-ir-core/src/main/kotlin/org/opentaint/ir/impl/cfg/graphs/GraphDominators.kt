package org.opentaint.opentaint-ir.impl.cfg.graphs

import org.opentaint.opentaint-ir.api.cfg.JIRCatchInst
import org.opentaint.opentaint-ir.api.cfg.JIRGraph
import org.opentaint.opentaint-ir.api.cfg.JIRInst
import java.util.*

/**
 * Calculate dominators for basic blocks.
 *
 * Uses the algorithm contained in Dragon book, pg. 670-1.
 */
open class GraphDominators(val graph: JIRGraph) {

    private val size = graph.instructions.size

    private val head = graph.entry
    private val flowSets = HashMap<Int, BitSet>(size)

    fun find() {
        val fullSet = BitSet(size)
        fullSet.flip(0, size) // set all to true

        // set up domain for intersection: head nodes are only dominated by themselves,
        // other nodes are dominated by everything else
        graph.instructions.forEachIndexed { index, inst ->
            flowSets[index] = when {
                inst === head -> BitSet().also {
                    it.set(index)
                }

                else -> fullSet
            }
        }
        var changed: Boolean
        do {
            changed = false
            graph.instructions.forEachIndexed { index, inst ->
                if (inst !== head) {
                    val fullClone = fullSet.clone() as BitSet
                    val predecessors = when (inst) {
                        !is JIRCatchInst -> graph.predecessors(inst)
                        else -> graph.throwers(inst)
                    }

                    predecessors.forEach { fullClone.and(it.dominatorsBitSet) }

                    val oldSet = inst.dominatorsBitSet
                    fullClone.set(index)
                    if (fullClone != oldSet) {
                        flowSets[index] = fullClone
                        changed = true
                    }
                }
            }
        } while (changed)
    }

    private val JIRInst.indexOf: Int
        get() {
            val index = graph.instructions.indexOf(this)
            return index.takeIf { it >= 0 } ?: error("No with index ${this} in the graph!")
        }

    private val Int.instruction: JIRInst
        get() {
            return graph.instructions[this]
        }

    private val JIRInst.dominatorsBitSet: BitSet
        get() {
            return flowSets[indexOf] ?: error("Node $this is not in the graph!")
        }

    fun dominators(inst: JIRInst): List<JIRInst> {
        // reconstruct list of dominators from bitset
        val result = arrayListOf<JIRInst>()
        val bitSet = inst.dominatorsBitSet
        var i = bitSet.nextSetBit(0)
        while (i >= 0) {
            result.add(i.instruction)
            if (i == Int.MAX_VALUE) {
                break // or (i+1) would overflow
            }
            i = bitSet.nextSetBit(i + 1)
        }
        return result
    }

    fun immediateDominator(inst: JIRInst): JIRInst? {
        // root node
        if (head === inst) {
            return null
        }
        val doms = inst.dominatorsBitSet.clone() as BitSet
        doms.clear(inst.indexOf)
        var i = doms.nextSetBit(0)
        while (i >= 0) {
            val dominator = i.instruction
            if (dominator.isDominatedByAll(doms)) {
                return dominator
            }
            if (i == Int.MAX_VALUE) {
                break // or (i+1) would overflow
            }
            i = doms.nextSetBit(i + 1)
        }
        return null
    }

    private fun JIRInst.isDominatedByAll(dominators: BitSet): Boolean {
        val bitSet = dominatorsBitSet
        var i = dominators.nextSetBit(0)
        while (i >= 0) {
            if (!bitSet[i]) {
                return false
            }
            if (i == Int.MAX_VALUE) {
                break // or (i+1) would overflow
            }
            i = dominators.nextSetBit(i + 1)
        }
        return true
    }

    fun isDominatedBy(node: JIRInst, dominator: JIRInst): Boolean {
        return node.dominatorsBitSet[dominator.indexOf]
    }

    fun isDominatedByAll(node: JIRInst, dominators: Collection<JIRInst>): Boolean {
        val bitSet = node.dominatorsBitSet
        for (n in dominators) {
            if (!bitSet[n.indexOf]) {
                return false
            }
        }
        return true
    }
}

fun JIRGraph.findDominators(): GraphDominators {
    return GraphDominators(this).also {
        it.find()
    }
}
