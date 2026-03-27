package org.opentaint.ir.go.cfg

import org.opentaint.ir.go.api.GoIRBody
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.inst.GoIRInstRef

/**
 * Instruction-level (flat) CFG.
 *
 * Within a basic block, each non-terminator instruction has exactly one successor:
 * the next instruction in the block. The terminator instruction's successors are
 * the first instructions of the successor blocks.
 */
interface GoIRInstGraph {
    val body: GoIRBody

    /** All instructions in program order */
    val instructions: List<GoIRInst>

    /** Entry instruction (first instruction of entry block) */
    val entry: GoIRInst

    /** Instructions with no successors (Return, Panic) */
    val exits: List<GoIRInst>

    // Navigation by instruction
    fun successors(inst: GoIRInst): List<GoIRInst>
    fun predecessors(inst: GoIRInst): List<GoIRInst>
    fun next(inst: GoIRInst): GoIRInst?
    fun previous(inst: GoIRInst): GoIRInst?

    // Navigation by ref
    fun successors(ref: GoIRInstRef): List<GoIRInstRef>
    fun predecessors(ref: GoIRInstRef): List<GoIRInstRef>
    fun next(ref: GoIRInstRef): GoIRInstRef?
    fun previous(ref: GoIRInstRef): GoIRInstRef?
}
