package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageIdx
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageSize
import org.opentaint.dataflow.ap.ifds.access.common.CommonZ2FSet
import org.opentaint.ir.api.common.cfg.CommonInst

class MethodEdgesFinalAutomataApSet(
    methodInitialStatement: CommonInst,
    private val maxInstIdx: Int,
    private val languageManager: LanguageManager
) : CommonZ2FSet<AccessGraph>(methodInitialStatement), AutomataFinalApAccess {
    override fun createApStorage(): ApStorage<AccessGraph> = InstructionFactSet(maxInstIdx, languageManager)

    private class InstructionFactSet(
        maxInstIdx: Int,
        private val languageManager: LanguageManager,
    ): ApStorage<AccessGraph> {
        private val finalFacts = AccessGraphSetArray.create(instructionStorageSize(maxInstIdx))

        override fun addEdge(statement: CommonInst, accessPath: AccessGraph): AccessGraph? {
            val factSetIdx = instructionStorageIdx(statement, languageManager)
            var factSet = finalFacts[factSetIdx]

            if (factSet == null) {
                factSet = AccessGraphSet.create()
            }

            val modifiedSet = factSet.add(accessPath) ?: return null
            finalFacts[factSetIdx] = modifiedSet
            return accessPath
        }

        override fun collectApAtStatement(statement: CommonInst, dst: MutableList<AccessGraph>) {
            val agSet = finalFacts[instructionStorageIdx(statement, languageManager)] ?: return
            agSet.toList(dst)
        }

        override fun toString(): String = "${finalFacts.indices.sumOf { finalFacts[it]?.graphSize ?: 0 }}"
    }
}
