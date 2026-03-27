package org.opentaint.ir.go.cfg

import org.opentaint.ir.api.common.cfg.ControlFlowGraph
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
interface GoIRInstGraph: ControlFlowGraph<GoIRInst> {
    val body: GoIRBody

    /** All instructions in program order */
    override val instructions: List<GoIRInst>

    /** Entry instruction (first instruction of entry block) */
    val entry: GoIRInst

    override val entries: List<GoIRInst> get() = listOf(entry)

    /** Instructions with no successors (Return, Panic) */
    override val exits: List<GoIRInst>

    // Navigation by instruction
    fun successorsList(inst: GoIRInst): List<GoIRInst>
    override fun successors(node: GoIRInst): Set<GoIRInst> = successorsList(node).toSet()
    fun predecessorsList(inst: GoIRInst): List<GoIRInst>
    override fun predecessors(node: GoIRInst): Set<GoIRInst> = predecessorsList(node).toSet()
    fun next(inst: GoIRInst): GoIRInst?
    fun previous(inst: GoIRInst): GoIRInst?

    // Navigation by ref
    fun successors(ref: GoIRInstRef): List<GoIRInstRef>
    fun predecessors(ref: GoIRInstRef): List<GoIRInstRef>
    fun next(ref: GoIRInstRef): GoIRInstRef?
    fun previous(ref: GoIRInstRef): GoIRInstRef?
}
