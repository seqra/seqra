package org.opentaint.ir.go.cfg

import org.opentaint.ir.go.api.GoIRBody
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.inst.GoIRInstRef
import org.opentaint.ir.go.inst.GoIRReturn

/**
 * Block-level CFG.
 */
interface GoIRBlockGraph {
    val body: GoIRBody
    val blocks: List<GoIRBasicBlock>
    val entry: GoIRBasicBlock
    val recover: GoIRBasicBlock?

    fun successors(block: GoIRBasicBlock): List<GoIRBasicBlock> = block.successors
    fun predecessors(block: GoIRBasicBlock): List<GoIRBasicBlock> = block.predecessors

    /** Which block does this instruction belong to? */
    fun blockOf(inst: GoIRInst): GoIRBasicBlock
    fun blockOf(ref: GoIRInstRef): GoIRBasicBlock

    /** All instructions in a block */
    fun instructions(block: GoIRBasicBlock): List<GoIRInst> = block.instructions

    /** Exit blocks (ending with Return) */
    fun exitBlocks(): List<GoIRBasicBlock> =
        blocks.filter { it.terminator is GoIRReturn }

    /** Panic blocks (ending with Panic) */
    fun panicBlocks(): List<GoIRBasicBlock> =
        blocks.filter { it.terminator is org.opentaint.ir.go.inst.GoIRPanic }

    /** Dominator tree traversal */
    fun domPreorder(): List<GoIRBasicBlock>
    fun domPostorder(): List<GoIRBasicBlock>
}
