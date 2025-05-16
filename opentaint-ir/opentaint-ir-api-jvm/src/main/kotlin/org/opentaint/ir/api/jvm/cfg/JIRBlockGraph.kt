package org.opentaint.ir.api.jvm.cfg

/**
 * Basic block represents a list of instructions that:
 * - guaranteed to execute one after other during normal control flow
 * (i.e. no exceptions thrown)
 * - all have the same exception handlers (i.e. `jIRGraph.catchers(inst)`
 * returns the same result for all instructions of the basic block)
 *
 * Because of the current implementation of basic block API, block is *not*
 * guaranteed to end with a terminating (i.e. `JIRTerminatingInst` or `JIRBranchingInst`) instruction.
 * However, any terminating instruction is guaranteed to be the last instruction of a basic block.
 */
data class JIRBasicBlock(val start: JIRInstRef, val end: JIRInstRef) {

    fun contains(inst: JIRInst): Boolean {
        return inst.location.index <= end.index && inst.location.index >= start.index
    }

    fun contains(inst: JIRInstRef): Boolean {
        return inst.index <= end.index && inst.index >= start.index
    }

}

interface JIRBlockGraph : JIRBytecodeGraph<JIRBasicBlock> {
    val jIRGraph: JIRGraph
    val entry: JIRBasicBlock
    override val exits: List<JIRBasicBlock>
    fun instructions(block: JIRBasicBlock): List<JIRInst>

    fun block(inst: JIRInst): JIRBasicBlock

    /**
     * `successors` and `predecessors` represent normal control flow
     */
    override fun predecessors(node: JIRBasicBlock): Set<JIRBasicBlock>
    override fun successors(node: JIRBasicBlock): Set<JIRBasicBlock>

    /**
     * `throwers` and `catchers` represent control flow when an exception occurs
     */
    override fun catchers(node: JIRBasicBlock): Set<JIRBasicBlock>
    override fun throwers(node: JIRBasicBlock): Set<JIRBasicBlock>
}
