package org.opentaint.ir.api.python

/**
 * A basic block: a sequence of instructions with a single entry point
 * and a single terminator at the end.
 */
data class PIRBasicBlock(
    val label: Int,
    val instructions: List<PIRInstruction>,
    val exceptionHandlers: List<Int> = emptyList(),
)

/**
 * Control Flow Graph for a function.
 * Analogous to JIRGraph + JIRBlockGraph combined.
 */
interface PIRCFG {
    val blocks: List<PIRBasicBlock>
    val entry: PIRInstruction
    val exits: Set<PIRInstruction>
    val entryBlock: PIRBasicBlock
    val exitBlocks: Set<PIRBasicBlock>
    val instList: List<PIRInstruction>
    fun successors(inst: PIRInstruction): List<PIRInstruction>
    fun predecessors(inst: PIRInstruction): List<PIRInstruction>
    fun successors(block: PIRBasicBlock): List<PIRBasicBlock>
    fun predecessors(block: PIRBasicBlock): List<PIRBasicBlock>
    fun exceptionalSuccessors(block: PIRBasicBlock): List<PIRBasicBlock>
    fun block(label: Int): PIRBasicBlock
    fun block(inst: PIRInstruction): PIRBasicBlock
}
