package org.opentaint.ir.impl.cfg.graphs

import org.opentaint.ir.api.common.cfg.BytecodeGraph
import org.opentaint.ir.api.jvm.cfg.JIRBasicBlock
import org.opentaint.ir.api.jvm.cfg.JIRBlockGraph
import org.opentaint.ir.api.jvm.cfg.JIRCatchInst
import org.opentaint.ir.api.jvm.cfg.JIRGraph
import org.opentaint.ir.api.jvm.cfg.JIRInst
import java.util.*

/**
 * Calculate dominators for basic blocks.
 *
 * Uses the algorithm contained in Dragon book, pg. 670-1.
 */
open class GraphDominators<NODE>(val graph: BytecodeGraph<NODE>) {

    private val nodes = graph.instructions
    private val size = nodes.size

    private val heads = graph.entries
    private val flowSets = HashMap<Int, BitSet>()

    fun find() {
        val fullSet = BitSet(size)
        fullSet.flip(0, size) // set all to true

        // set up domain for intersection: head nodes are only dominated by themselves,
        // other nodes are dominated by everything else
        nodes.forEachIndexed { index, node ->
            flowSets[index] = when {
                heads.contains(node) -> BitSet().also {
                    it.set(index)
                }

                else -> fullSet
            }
        }
        var changed: Boolean
        do {
            changed = false
            nodes.forEachIndexed { index, node ->
                if (!heads.contains(node)) {
                    val fullClone = fullSet.clone() as BitSet
                    val predecessors = when (node) {
                        !is JIRCatchInst -> graph.predecessors(node)
                        else -> graph.throwers(node)
                    }

                    predecessors.forEach { fullClone.and(it.dominatorsBitSet) }

                    val oldSet = node.dominatorsBitSet
                    fullClone.set(index)
                    if (fullClone != oldSet) {
                        flowSets[index] = fullClone
                        changed = true
                    }
                }
            }
        } while (changed)
    }

    private val NODE.indexOf: Int
        get() {
            val index = nodes.indexOf(this)
            return index.takeIf { it >= 0 } ?: error("No node with index $this in the graph")
        }

    private val Int.node: NODE
        get() {
            return nodes[this]
        }

    private val NODE.dominatorsBitSet: BitSet
        get() {
            return flowSets[indexOf] ?: error("Node $this is not in the graph!")
        }

    fun dominators(inst: NODE): List<NODE> {
        // reconstruct list of dominators from bitset
        val result = arrayListOf<NODE>()
        val bitSet = inst.dominatorsBitSet
        var i = bitSet.nextSetBit(0)
        while (i >= 0) {
            result.add(i.node)
            if (i == Int.MAX_VALUE) {
                break // or (i+1) would overflow
            }
            i = bitSet.nextSetBit(i + 1)
        }
        return result
    }

    fun immediateDominator(inst: NODE): NODE? {
        // root node
        if (heads.contains(inst)) {
            return null
        }
        val doms = inst.dominatorsBitSet.clone() as BitSet
        doms.clear(inst.indexOf)
        var i = doms.nextSetBit(0)
        while (i >= 0) {
            val dominator = i.node
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

    private fun NODE.isDominatedByAll(dominators: BitSet): Boolean {
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

    fun isDominatedBy(node: NODE, dominator: NODE): Boolean {
        return node.dominatorsBitSet[dominator.indexOf]
    }

    fun isDominatedByAll(node: NODE, dominators: Collection<NODE>): Boolean {
        val bitSet = node.dominatorsBitSet
        for (n in dominators) {
            if (!bitSet[n.indexOf]) {
                return false
            }
        }
        return true
    }
}

fun JIRGraph.findDominators(): GraphDominators<JIRInst> {
    return GraphDominators(this).also {
        it.find()
    }
}

fun JIRBlockGraph.findDominators(): GraphDominators<JIRBasicBlock> {
    return GraphDominators(this).also {
        it.find()
    }
}
