package org.opentaint.opentaint-ir.impl.cfg

import org.opentaint.opentaint-ir.api.cfg.JIRBasicBlock
import org.opentaint.opentaint-ir.api.cfg.JIRBlockGraph
import org.opentaint.opentaint-ir.api.cfg.JIRBranchingInst
import org.opentaint.opentaint-ir.api.cfg.JIRInst
import org.opentaint.opentaint-ir.api.cfg.JIRInstRef
import org.opentaint.opentaint-ir.api.cfg.JIRTerminatingInst

class JIRBlockGraphImpl(
    override val jIRGraph: JIRGraphImpl
) : Iterable<JIRBasicBlock>, JIRBlockGraph {
    private val _basicBlocks = mutableListOf<JIRBasicBlock>()
    private val predecessorMap = mutableMapOf<JIRBasicBlock, MutableSet<JIRBasicBlock>>()
    private val successorMap = mutableMapOf<JIRBasicBlock, MutableSet<JIRBasicBlock>>()
    private val catchersMap = mutableMapOf<JIRBasicBlock, MutableSet<JIRBasicBlock>>()
    private val throwersMap = mutableMapOf<JIRBasicBlock, MutableSet<JIRBasicBlock>>()

    override val basicBlocks: List<JIRBasicBlock> get() = _basicBlocks
    override val entry: JIRBasicBlock get() = basicBlocks.first()

    override val exits: List<JIRBasicBlock> get() = basicBlocks.filter { successors(it).isEmpty() }

    init {
        val inst2Block = mutableMapOf<JIRInst, JIRBasicBlock>()

        val currentRefs = mutableListOf<JIRInstRef>()

        val createBlock = {
            val block = JIRBasicBlock(currentRefs.first(), currentRefs.last())
            for (ref in currentRefs) {
                inst2Block[jIRGraph.inst(ref)] = block
            }
            currentRefs.clear()
            _basicBlocks.add(block)
        }
        for (inst in jIRGraph.instructions) {
            val currentRef = jIRGraph.ref(inst)
            val shouldBeAddedBefore = jIRGraph.predecessors(inst).size <= 1 || currentRefs.isEmpty()
            val shouldTerminate = when {
                currentRefs.isEmpty() -> false
                else -> jIRGraph.catchers(currentRefs.first()) != jIRGraph.catchers(currentRef)
            }
            if (shouldTerminate) {
                createBlock()
            }
            when {
                inst is JIRBranchingInst
                        || inst is JIRTerminatingInst
                        || jIRGraph.predecessors(inst).size > 1 -> {
                    if (shouldBeAddedBefore) currentRefs += currentRef
                    createBlock()
                    if (!shouldBeAddedBefore) {
                        currentRefs += currentRef
                        createBlock()
                    }
                }

                else -> {
                    currentRefs += currentRef
                }
            }
        }
        if (currentRefs.isNotEmpty()) {
            val block = JIRBasicBlock(currentRefs.first(), currentRefs.last())
            for (ref in currentRefs) {
                inst2Block[jIRGraph.inst(ref)] = block
            }
            currentRefs.clear()
            _basicBlocks.add(block)
        }
        for (block in _basicBlocks) {
            predecessorMap.getOrPut(block, ::mutableSetOf) += jIRGraph.predecessors(block.start).map { inst2Block[it]!! }
            successorMap.getOrPut(block, ::mutableSetOf) += jIRGraph.successors(block.end).map { inst2Block[it]!! }
            catchersMap.getOrPut(block, ::mutableSetOf) += jIRGraph.catchers(block.start).map { inst2Block[it]!! }.also {
                for (catcher in it) {
                    throwersMap.getOrPut(catcher, ::mutableSetOf) += block
                }
            }
        }
    }

    override fun instructions(block: JIRBasicBlock): List<JIRInst> =
        (block.start.index..block.end.index).map { jIRGraph.instructions[it] }

    /**
     * `successors` and `predecessors` represent normal control flow
     */
    override fun predecessors(block: JIRBasicBlock): Set<JIRBasicBlock> = predecessorMap.getOrDefault(block, emptySet())
    override fun successors(block: JIRBasicBlock): Set<JIRBasicBlock> = successorMap.getOrDefault(block, emptySet())

    /**
     * `throwers` and `catchers` represent control flow when an exception occurs
     */
    override fun catchers(block: JIRBasicBlock): Set<JIRBasicBlock> = catchersMap.getOrDefault(block, emptySet())
    override fun throwers(block: JIRBasicBlock): Set<JIRBasicBlock> = throwersMap.getOrDefault(block, emptySet())

    override fun iterator(): Iterator<JIRBasicBlock> = basicBlocks.iterator()
}