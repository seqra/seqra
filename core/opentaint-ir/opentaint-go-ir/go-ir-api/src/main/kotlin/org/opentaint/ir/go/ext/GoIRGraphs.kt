@file:JvmName("GoIRGraphs")
package org.opentaint.ir.go.ext

import org.opentaint.ir.go.cfg.GoIRInstGraph
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.inst.GoIRInstRef

/**
 * Iterate instructions in BFS order from entry.
 */
fun GoIRInstGraph.bfsOrder(): List<GoIRInst> {
    val visited = mutableSetOf<Int>()
    val queue = ArrayDeque<GoIRInst>()
    val result = mutableListOf<GoIRInst>()

    queue.add(entry)
    visited.add(entry.index)

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        result.add(current)
        for (succ in successors(current)) {
            if (visited.add(succ.index)) {
                queue.add(succ)
            }
        }
    }

    return result
}

/**
 * Iterate instructions in DFS postorder from entry.
 */
fun GoIRInstGraph.dfsPostorder(): List<GoIRInst> {
    val visited = mutableSetOf<Int>()
    val result = mutableListOf<GoIRInst>()

    fun dfs(inst: GoIRInst) {
        if (!visited.add(inst.index)) return
        for (succ in successors(inst)) {
            dfs(succ)
        }
        result.add(inst)
    }

    dfs(entry)
    return result
}
