package org.opentaint.ir.go.cfg

import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.inst.GoIRInstRef
import org.opentaint.ir.go.inst.GoIRPhi
import org.opentaint.ir.go.inst.index

/**
 * A basic block in the CFG, defined by its index, label, instructions, and edges.
 *
 * Note: predecessors, successors, idom, and dominatedBlocks are resolved lazily
 * by the implementation (they cannot be in the constructor without circular references).
 */
interface GoIRBasicBlock {
    val index: Int
    val label: String?
    val instructions: List<GoIRInst>
    val predecessors: List<GoIRBasicBlock>
    val successors: List<GoIRBasicBlock>
    val idom: GoIRBasicBlock?
    val dominatedBlocks: List<GoIRBasicBlock>

    /** First instruction ref */
    val start: GoIRInstRef get() = GoIRInstRef(instructions.first().index)

    /** Last instruction ref (the terminator) */
    val end: GoIRInstRef get() = GoIRInstRef(instructions.last().index)

    /** The terminator instruction */
    val terminator: GoIRInst get() = instructions.last()

    /** Phi instructions at the start of this block */
    val phis: List<GoIRPhi> get() = instructions.takeWhile { it is GoIRPhi }.map { it as GoIRPhi }

    /** Check if an instruction belongs to this block */
    operator fun contains(inst: GoIRInst): Boolean =
        inst.index in start.index..end.index

    operator fun contains(ref: GoIRInstRef): Boolean =
        ref.index in start.index..end.index
}
