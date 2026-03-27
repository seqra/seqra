package org.opentaint.ir.go.impl

import org.opentaint.ir.go.cfg.GoIRBasicBlock
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.inst.GoIRInstRef

class GoIRBasicBlockImpl(
    override val index: Int,
    override val label: String?,
) : GoIRBasicBlock {
    private var _instructions: List<GoIRInst> = emptyList()
    private var _predecessors: List<GoIRBasicBlock> = emptyList()
    private var _successors: List<GoIRBasicBlock> = emptyList()
    private var _idom: GoIRBasicBlock? = null
    private var _dominatedBlocks: List<GoIRBasicBlock> = emptyList()

    /**
     * Global index of the first instruction in this block.
     * Invariant: `instructions[i].index == i + blockStartIndex`
     */
    var blockStartIndex: Int = 0
        private set

    override val instructions: List<GoIRInst> get() = _instructions
    override val start: GoIRInstRef get() = GoIRInstRef(blockStartIndex)
    override val predecessors: List<GoIRBasicBlock> get() = _predecessors
    override val successors: List<GoIRBasicBlock> get() = _successors
    override val idom: GoIRBasicBlock? get() = _idom
    override val dominatedBlocks: List<GoIRBasicBlock> get() = _dominatedBlocks

    // Deferred resolution data
    internal var predIndices: List<Int> = emptyList()
    internal var succIndices: List<Int> = emptyList()
    internal var idomIndex: Int = -1
    internal var domineeIndices: List<Int> = emptyList()

    /** O(1) instruction lookup by global index */
    fun inst(globalIndex: Int): GoIRInst = _instructions[globalIndex - blockStartIndex]

    fun setInstructions(instructions: List<GoIRInst>) {
        _instructions = instructions
        blockStartIndex = if (instructions.isNotEmpty()) instructions.first().index else 0
    }

    fun resolvePredecessors(blocks: List<GoIRBasicBlockImpl>) {
        _predecessors = predIndices.map { blocks[it] }
    }

    fun resolveSuccessors(blocks: List<GoIRBasicBlockImpl>) {
        _successors = succIndices.map { blocks[it] }
    }

    fun resolveIdom(blocks: List<GoIRBasicBlockImpl>) {
        if (idomIndex >= 0) {
            _idom = blocks[idomIndex]
        }
    }

    fun resolveDominees(blocks: List<GoIRBasicBlockImpl>) {
        _dominatedBlocks = domineeIndices.map { blocks[it] }
    }

    override fun toString(): String = "GoIRBasicBlock($index, label=$label)"
}
