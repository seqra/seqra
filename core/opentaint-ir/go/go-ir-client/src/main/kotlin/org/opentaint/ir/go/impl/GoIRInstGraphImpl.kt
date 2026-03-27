package org.opentaint.ir.go.impl

import org.opentaint.ir.go.api.GoIRBody
import org.opentaint.ir.go.cfg.GoIRInstGraph
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.inst.GoIRInstRef
import org.opentaint.ir.go.inst.GoIRPanic
import org.opentaint.ir.go.inst.GoIRReturn
import org.opentaint.ir.go.inst.block
import org.opentaint.ir.go.inst.index

class GoIRInstGraphImpl(override val body: GoIRBody) : GoIRInstGraph {
    override val instructions: List<GoIRInst> = body.instructions
    override val entry: GoIRInst = body.entryBlock.instructions.first()
    override val exits: List<GoIRInst> = body.blocks
        .map { it.instructions.last() }
        .filter { it is GoIRReturn || it is GoIRPanic }

    // Pre-computed successor/predecessor maps for O(1) access
    private val succMap: Map<Int, List<Int>>
    private val predMap: Map<Int, List<Int>>

    init {
        val succs = mutableMapOf<Int, List<Int>>()
        val preds = mutableMapOf<Int, MutableList<Int>>()

        for (block in body.blocks) {
            val instrs = block.instructions
            // Intra-block edges: each instruction -> next instruction
            for (i in 0 until instrs.size - 1) {
                succs[instrs[i].index] = listOf(instrs[i + 1].index)
                preds.getOrPut(instrs[i + 1].index) { mutableListOf() }
                    .add(instrs[i].index)
            }
            // Terminator -> successor block first instructions
            val term = instrs.last()
            val termSuccs = block.successors.map { it.instructions.first().index }
            succs[term.index] = termSuccs
            for (s in termSuccs) {
                preds.getOrPut(s) { mutableListOf() }.add(term.index)
            }
        }

        this.succMap = succs
        this.predMap = preds
    }

    override fun successors(inst: GoIRInst): List<GoIRInst> =
        succMap[inst.index]?.map { instructions[it] } ?: emptyList()

    override fun predecessors(inst: GoIRInst): List<GoIRInst> =
        predMap[inst.index]?.map { instructions[it] } ?: emptyList()

    override fun next(inst: GoIRInst): GoIRInst? {
        val block = inst.block as GoIRBasicBlockImpl
        val localPos = inst.index - block.blockStartIndex
        return if (localPos < block.instructions.size - 1) block.inst(inst.index + 1) else null
    }

    override fun previous(inst: GoIRInst): GoIRInst? {
        val block = inst.block as GoIRBasicBlockImpl
        val localPos = inst.index - block.blockStartIndex
        return if (localPos > 0) block.inst(inst.index - 1) else null
    }

    override fun successors(ref: GoIRInstRef) =
        succMap[ref.index]?.map { GoIRInstRef(it) } ?: emptyList()

    override fun predecessors(ref: GoIRInstRef) =
        predMap[ref.index]?.map { GoIRInstRef(it) } ?: emptyList()

    override fun next(ref: GoIRInstRef): GoIRInstRef? {
        val inst = body.inst(ref)
        return next(inst)?.let { GoIRInstRef(it.index) }
    }

    override fun previous(ref: GoIRInstRef): GoIRInstRef? {
        val inst = body.inst(ref)
        return previous(inst)?.let { GoIRInstRef(it.index) }
    }
}
