package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageIdx
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageSize
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.MethodEdgesFinalApSet
import org.opentaint.dataflow.util.collectToListWithPostProcess

class MethodEdgesFinalAutomataApSet(
    methodInitialStatement: CommonInst,
    maxInstIdx: Int,
    languageManager: LanguageManager
) : MethodEdgesFinalApSet {
    private val storage = Storage(methodInitialStatement, maxInstIdx, languageManager)

    override fun add(statement: CommonInst, ap: FinalFactAp): FinalFactAp? =
        add(statement, ap as AccessGraphFinalFactAp)

    override fun collectApAtStatement(collection: MutableList<FinalFactAp>, statement: CommonInst) {
        storage.forEachValue { base, storedFacts ->
            val agSet = storedFacts.allFactsAtStatement(statement) ?: return@forEachValue
            collectToListWithPostProcess(
                collection,
                { agSet.toList(it) },
                { AccessGraphFinalFactAp(base, it, ExclusionSet.Universe) }
            )
        }
    }

    private fun add(statement: CommonInst, ap: AccessGraphFinalFactAp): AccessGraphFinalFactAp? {
        check(ap.exclusions is ExclusionSet.Universe)

        val edgeSet = storage.getOrCreate(ap.base)

        val edgeAccess = ap.access
        val addedAccess = edgeSet.addEdge(statement, edgeAccess) ?: return null
        if (addedAccess === edgeAccess) return ap

        return AccessGraphFinalFactAp(ap.base, addedAccess, ExclusionSet.Universe)
    }

    override fun toString(): String = storage.toString()

    private class Storage(
        initialStatement: CommonInst,
        private val maxInstIdx: Int,
        private val languageManager: LanguageManager
    ) : MethodAnalyzerEdges.EdgeStorage<InstructionFactSet>(initialStatement) {
        override fun createStorage(): InstructionFactSet = InstructionFactSet(maxInstIdx, languageManager)
    }

    private class InstructionFactSet(
        maxInstIdx: Int,
        private val languageManager: LanguageManager,
    ) {
        private val finalFacts = AccessGraphSetArray.create(instructionStorageSize(maxInstIdx))

        fun addEdge(statement: CommonInst, accessGraph: AccessGraph): AccessGraph? {
            val factSetIdx = instructionStorageIdx(statement, languageManager)
            var factSet = finalFacts[factSetIdx]

            if (factSet == null) {
                factSet = AccessGraphSet.create()
            }

            val modifiedSet = factSet.add(accessGraph) ?: return null
            finalFacts[factSetIdx] = modifiedSet
            return accessGraph
        }

        fun allFactsAtStatement(statement: CommonInst): AccessGraphSet? =
            finalFacts[instructionStorageIdx(statement, languageManager)]

        override fun toString(): String = "${finalFacts.indices.sumOf { finalFacts[it]?.graphSize ?: 0 }}"
    }
}