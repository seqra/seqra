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
    val entry: PIRBasicBlock
    val blocks: List<PIRBasicBlock>
    val exits: Set<PIRBasicBlock>
    fun successors(block: PIRBasicBlock): List<PIRBasicBlock>
    fun predecessors(block: PIRBasicBlock): List<PIRBasicBlock>
    fun exceptionalSuccessors(block: PIRBasicBlock): List<PIRBasicBlock>
    fun block(label: Int): PIRBasicBlock
}
