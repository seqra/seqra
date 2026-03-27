package org.opentaint.ir.go.impl

import org.opentaint.ir.go.api.GoIRBody
import org.opentaint.ir.go.cfg.GoIRBasicBlock
import org.opentaint.ir.go.cfg.GoIRBlockGraph
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.inst.GoIRInstRef
import org.opentaint.ir.go.inst.index

class GoIRBlockGraphImpl(override val body: GoIRBody) : GoIRBlockGraph {
    override val blocks: List<GoIRBasicBlock> get() = body.blocks
    override val entry: GoIRBasicBlock get() = body.entryBlock
    override val recover: GoIRBasicBlock? get() = body.recoverBlock

    // Map from instruction index to block
    private val instToBlock: Map<Int, GoIRBasicBlock> by lazy {
        val map = mutableMapOf<Int, GoIRBasicBlock>()
        for (block in blocks) {
            for (inst in block.instructions) {
                map[inst.index] = block
            }
        }
        map
    }

    override fun blockOf(inst: GoIRInst): GoIRBasicBlock =
        instToBlock[inst.index] ?: throw IllegalArgumentException("Instruction ${inst.index} not found")

    override fun blockOf(ref: GoIRInstRef): GoIRBasicBlock =
        instToBlock[ref.index] ?: throw IllegalArgumentException("Instruction ${ref.index} not found")

    override fun domPreorder(): List<GoIRBasicBlock> {
        val result = mutableListOf<GoIRBasicBlock>()
        fun visit(block: GoIRBasicBlock) {
            result.add(block)
            for (child in block.dominatedBlocks) {
                visit(child)
            }
        }
        visit(entry)
        return result
    }

    override fun domPostorder(): List<GoIRBasicBlock> {
        val result = mutableListOf<GoIRBasicBlock>()
        fun visit(block: GoIRBasicBlock) {
            for (child in block.dominatedBlocks) {
                visit(child)
            }
            result.add(block)
        }
        visit(entry)
        return result
    }
}
