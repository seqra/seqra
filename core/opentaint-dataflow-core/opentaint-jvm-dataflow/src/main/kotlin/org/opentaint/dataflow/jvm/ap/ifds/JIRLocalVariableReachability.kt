package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCatchInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.locals
import org.opentaint.jvm.graph.JApplicationGraph
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageIdx
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageSize
import org.opentaint.dataflow.util.containsAll
import org.opentaint.dataflow.util.copy
import java.util.BitSet

class JIRLocalVariableReachability(
    val method: JIRMethod,
    val graph: JApplicationGraph,
    val languageManager: JIRLanguageManager
) {
    private val maxInstIdx = method.instList.maxOf { it.location.index }

    private val reachabilityInfo by lazy { computeReachability() }

    fun isReachable(base: AccessPathBase, statement: CommonInst): Boolean {
        if (base !is AccessPathBase.LocalVar) return true
        val storageIdx = instructionStorageIdx(statement, languageManager)
        return isReachable(base.idx, storageIdx)
    }

    fun isReachable(base: AccessPathBase, statementIdx: Int): Boolean {
        if (base !is AccessPathBase.LocalVar) return true
        return isReachable(base.idx, statementIdx)
    }

    fun isReachable(localIdx: Int, statementIdx: Int): Boolean {
        val storage = reachabilityInfo[statementIdx] ?: return false
        return storage.get(localIdx)
    }

    private fun computeReachability(): Array<BitSet?> {
        val methodGraph = graph.methodGraph(method)

        val statementReachability = arrayOfNulls<BitSet?>(instructionStorageSize(maxInstIdx))
        val unprocessed = methodGraph.exitPoints().mapTo(mutableListOf()) { it to BitSet() }

        while (unprocessed.isNotEmpty()) {
            val (statement, prevReachability) = unprocessed.removeLast()
            val storageIdx = instructionStorageIdx(statement, languageManager)
            val currentReachability = statementReachability[storageIdx]

            // no new reachability info
            if (currentReachability != null && currentReachability.containsAll(prevReachability)) {
                continue
            }

            val reachableLocalsAtStatement = BitSet()
            reachableLocalsAtStatement.or(prevReachability)
            if (currentReachability != null) {
                reachableLocalsAtStatement.or(currentReachability)
            }

            statement.locals.forEach {
                if (it is JIRLocalVar) {
                    reachableLocalsAtStatement.set(it.index)
                }
            }

            statementReachability[storageIdx] = reachableLocalsAtStatement

            val removedVar = statement.assignedLocalVar()
            val nextReachable = if (removedVar != null) {
                reachableLocalsAtStatement.copy().also { it.clear(removedVar.index) }
            } else {
                reachableLocalsAtStatement
            }

            methodGraph.predecessors(statement).forEach { unprocessed.add(it to nextReachable) }
        }

        return statementReachability
    }

    private fun JIRInst.assignedLocalVar(): JIRLocalVar? = when (this) {
        is JIRAssignInst -> lhv as? JIRLocalVar
        is JIRCatchInst -> throwable as? JIRLocalVar
        else -> null
    }
}
