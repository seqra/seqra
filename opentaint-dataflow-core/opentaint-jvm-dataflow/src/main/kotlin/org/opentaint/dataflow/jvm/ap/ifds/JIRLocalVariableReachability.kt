package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCatchInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.locals
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.LocalVariableReachability
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageIdx
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageSize
import org.opentaint.dataflow.jvm.graph.JIRApplicationGraph
import java.util.BitSet

class JIRLocalVariableReachability(
    private val method: JIRMethod,
    private val graph: JIRApplicationGraph,
    private val languageManager: JIRLanguageManager
): LocalVariableReachability {
    private val maxInstIdx = method.instList.maxOf { it.location.index }

    private val reachabilityInfo by lazy { computeReachability() }

    override fun isReachable(base: AccessPathBase, statement: CommonInst): Boolean {
        if (base !is AccessPathBase.LocalVar) return true
        val storageIdx = instructionStorageIdx(statement, languageManager)
        val storage = reachabilityInfo[storageIdx] ?: return false
        return storage.get(base.idx)
    }

    private fun computeReachability(): Array<BitSet?> {
        val statementReachability = arrayOfNulls<BitSet?>(instructionStorageSize(maxInstIdx))
        val unprocessed = graph.exitPoints(method).mapTo(mutableListOf()) { it to BitSet() }

        while (unprocessed.isNotEmpty()) {
            val (statement, prevReachability) = unprocessed.removeLast()
            val storageIdx = instructionStorageIdx(statement, languageManager)
            val currentReachability = statementReachability[storageIdx]

            // no new reachability info
            if (currentReachability != null && prevReachability.includedIn(currentReachability)) {
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

            graph.predecessors(statement).forEach { unprocessed.add(it to nextReachable) }
        }

        return statementReachability
    }

    private fun JIRInst.assignedLocalVar(): JIRLocalVar? = when (this) {
        is JIRAssignInst -> lhv as? JIRLocalVar
        is JIRCatchInst -> throwable as? JIRLocalVar
        else -> null
    }

    companion object {
        @JvmStatic
        private fun BitSet.copy(): BitSet = clone() as BitSet

        private fun BitSet.includedIn(other: BitSet): Boolean {
            val copy = this.copy()
            copy.andNot(other)
            return copy.isEmpty
        }
    }
}
