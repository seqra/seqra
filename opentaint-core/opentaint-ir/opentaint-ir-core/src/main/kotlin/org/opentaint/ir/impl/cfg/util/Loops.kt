
@file:JvmName("JIRLoops")
package org.opentaint.ir.impl.cfg.util

import org.opentaint.ir.api.jvm.cfg.JIRGraph
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.impl.cfg.graphs.findDominators
import java.util.*
import kotlin.LazyThreadSafetyMode.PUBLICATION


class JIRLoop(
    val graph: JIRGraph,
    val head: JIRInst,
    val instructions: List<JIRInst>
) {
    val exits: Collection<JIRInst> by lazy(PUBLICATION) {
        val result = hashSetOf<JIRInst>()
        for (s in instructions) {
            graph.successors(s).forEach {
                if (!instructions.contains(it)) {
                    result.add(s)
                }
            }
        }
        result
    }

    val backJump: JIRInst get() = instructions.last()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JIRLoop

        if (head != other.head) return false
        if (instructions != other.instructions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = head.hashCode()
        result = 31 * result + instructions.hashCode()
        return result
    }


}

val JIRGraph.loops: Set<JIRLoop>
    get() {
        val finder = findDominators()
        val loops = HashMap<JIRInst, MutableList<JIRInst>>()
        instructions.forEach { inst ->
            val dominators = finder.dominators(inst)

            val headers = arrayListOf<JIRInst>()
            successors(inst).forEach {
                if (dominators.contains(it)) {
                    headers.add(it)
                }
            }
            headers.forEach { header ->
                val loopBody = loopBodyOf(header, inst)
                loops[header] = loops[header]?.union(loopBody) ?: loopBody
            }
        }
        return loops.map { (key, value) ->
            newLoop(key, value)
        }.toSet()
    }

private fun JIRGraph.newLoop(head: JIRInst, loopStatements: MutableList<JIRInst>): JIRLoop {
    // put header to the top
    loopStatements.remove(head)
    loopStatements.add(0, head)

    // last statement
    val backJump = loopStatements.last()
    // must branch back to the head
    assert(successors(backJump).contains(head))
    return JIRLoop(this, head = head, instructions = loopStatements)
}


private fun JIRGraph.loopBodyOf(header: JIRInst, inst: JIRInst): MutableList<JIRInst> {
    val loopBody = arrayListOf(header)
    val stack = ArrayDeque<JIRInst>().also {
        it.push(inst)
    }
    while (!stack.isEmpty()) {
        val next = stack.pop()
        if (!loopBody.contains(next)) {
            loopBody.add(0, next)
            predecessors(next).forEach { stack.push(it) }
        }
    }
    assert(inst === header && loopBody.size == 1 || loopBody[loopBody.size - 2] === inst)
    assert(loopBody[loopBody.size - 1] === header)
    return loopBody
}

private fun MutableList<JIRInst>.union(another: List<JIRInst>): MutableList<JIRInst> = apply {
    addAll(another.filter { !contains(it) })
}
