package org.opentaint.ir.impl.cfg

import org.opentaint.ir.api.jvm.cfg.JIRBasicBlock
import org.opentaint.ir.api.jvm.cfg.JIRBlockGraph
import org.opentaint.ir.api.jvm.cfg.JIRBranchingInst
import org.opentaint.ir.api.jvm.cfg.JIRGraph
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstRef
import org.opentaint.ir.api.jvm.cfg.JIRTerminatingInst

class JIRBlockGraphImpl(
    override val jIRGraph: JIRGraph,
) : JIRBlockGraph {

    private val _basicBlocks = mutableListOf<JIRBasicBlock>()
    private val predecessorMap = mutableMapOf<JIRBasicBlock, MutableSet<JIRBasicBlock>>()
    private val successorMap = mutableMapOf<JIRBasicBlock, MutableSet<JIRBasicBlock>>()
    private val catchersMap = mutableMapOf<JIRBasicBlock, MutableSet<JIRBasicBlock>>()
    private val throwersMap = mutableMapOf<JIRBasicBlock, MutableSet<JIRBasicBlock>>()

    override val instructions: List<JIRBasicBlock>
        get() = _basicBlocks

    override val entry: JIRBasicBlock
        get() = instructions.first()

    override val entries: List<JIRBasicBlock>
        get() = listOf(entry)

    override val exits: List<JIRBasicBlock>
        get() = instructions.filter { successors(it).isEmpty() }

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

    override fun block(inst: JIRInst): JIRBasicBlock {
        assert(inst.location.method == jIRGraph.method) {
            "required method of instruction ${jIRGraph.method} but got ${inst.location.method}"
        }
        for (basicBlock in entries) {
            if (basicBlock.contains(inst)) {
                return basicBlock
            }
        }
        throw IllegalStateException("block not found for $inst in ${jIRGraph.method}")
    }

    /**
     * `successors` and `predecessors` represent normal control flow
     */
    override fun predecessors(node: JIRBasicBlock): Set<JIRBasicBlock> = predecessorMap[node].orEmpty()
    override fun successors(node: JIRBasicBlock): Set<JIRBasicBlock> = successorMap[node].orEmpty()

    /**
     * `throwers` and `catchers` represent control flow when an exception occurs
     */
    override fun catchers(node: JIRBasicBlock): Set<JIRBasicBlock> = catchersMap[node].orEmpty()
    override fun throwers(node: JIRBasicBlock): Set<JIRBasicBlock> = throwersMap[node].orEmpty()
}
