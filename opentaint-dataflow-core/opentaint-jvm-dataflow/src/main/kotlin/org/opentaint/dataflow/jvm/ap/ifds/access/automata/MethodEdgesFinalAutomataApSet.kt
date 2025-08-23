package org.opentaint.dataflow.jvm.ap.ifds.access.automata

import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.jvm.ap.ifds.ExclusionSet
import org.opentaint.dataflow.jvm.ap.ifds.MethodAnalyzerEdges
import org.opentaint.dataflow.jvm.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageIdx
import org.opentaint.dataflow.jvm.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageSize
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.MethodEdgesFinalApSet

class MethodEdgesFinalAutomataApSet(
    methodInitialStatement: JIRInst, maxInstIdx: Int
) : MethodEdgesFinalApSet {
    private val storage = Storage(methodInitialStatement, maxInstIdx)

    override fun add(statement: JIRInst, ap: FinalFactAp): FinalFactAp? =
        add(statement, ap as AccessGraphFinalFactAp)

    private fun add(statement: JIRInst, ap: AccessGraphFinalFactAp): AccessGraphFinalFactAp? {
        check(ap.exclusions is ExclusionSet.Universe)

        val edgeSet = storage.getOrCreate(ap.base)

        val edgeAccess = ap.access
        val addedAccess = edgeSet.addEdge(statement, edgeAccess) ?: return null
        if (addedAccess === edgeAccess) return ap

        return AccessGraphFinalFactAp(ap.base, addedAccess, ExclusionSet.Universe)
    }

    override fun toString(): String = storage.toString()

    private class Storage(
        initialStatement: JIRInst,
        private val maxInstIdx: Int
    ) : MethodAnalyzerEdges.EdgeStorage<InstructionFactSet>(initialStatement) {
        override fun createStorage(): InstructionFactSet = InstructionFactSet(maxInstIdx)
    }

    private class InstructionFactSet(maxInstIdx: Int) {
        private val finalFacts = arrayOfNulls<AccessGraphSet>(instructionStorageSize(maxInstIdx))

        fun addEdge(statement: JIRInst, accessGraph: AccessGraph): AccessGraph? {
            val factSetIdx = instructionStorageIdx(statement)
            var factSet = finalFacts[factSetIdx]

            if (factSet == null) {
                factSet = AccessGraphSet.create()
            }

            val modifiedSet = factSet.add(accessGraph) ?: return null
            finalFacts[factSetIdx] = modifiedSet
            return accessGraph
        }

        override fun toString(): String = "${finalFacts.sumOf { it?.graphSize ?: 0 }}"
    }
}