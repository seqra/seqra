package org.opentaint.ir.go.api

import org.opentaint.ir.go.cfg.GoIRBasicBlock
import org.opentaint.ir.go.cfg.GoIRBlockGraph
import org.opentaint.ir.go.cfg.GoIRInstGraph
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.inst.GoIRInstRef
import org.opentaint.ir.go.inst.index

/**
 * The body of a function: basic blocks + instructions + CFG views.
 */
interface GoIRBody {
    val function: GoIRFunction
    val blocks: List<GoIRBasicBlock>
    val entryBlock: GoIRBasicBlock get() = blocks[0]
    val recoverBlock: GoIRBasicBlock?

    /** Flat instruction list — all instructions across all blocks, with unique indices */
    val instructions: List<GoIRInst>
    val instructionCount: Int get() = instructions.size

    /** Graph views */
    val blockGraph: GoIRBlockGraph
    val instGraph: GoIRInstGraph

    fun blockByIndex(i: Int): GoIRBasicBlock = blocks[i]
    fun inst(ref: GoIRInstRef): GoIRInst = instructions[ref.index]
    fun ref(inst: GoIRInst): GoIRInstRef = GoIRInstRef(inst.index)
}
