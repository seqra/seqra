package org.opentaint.dataflow.jvm.ap.ifds.access.automata

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.jvm.ap.ifds.ExclusionSet
import org.opentaint.dataflow.jvm.ap.ifds.MethodAnalyzerEdges
import org.opentaint.dataflow.jvm.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageIdx
import org.opentaint.dataflow.jvm.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageSize
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.MethodEdgesInitialToFinalApSet

class MethodEdgesInitialToFinalAutomataApSet(
    methodInitialStatement: JIRInst, maxInstIdx: Int
) : MethodEdgesInitialToFinalApSet {
    private val storage = InitialFactBaseStorage(methodInitialStatement, maxInstIdx)

    override fun add(
        statement: JIRInst,
        initialAp: InitialFactAp,
        finalAp: FinalFactAp
    ): Pair<InitialFactAp, FinalFactAp>? =
        add(statement, initialAp as AccessGraphInitialFactAp, finalAp as AccessGraphFinalFactAp)

    override fun collectApAtStatement(
        collection: MutableCollection<Pair<InitialFactAp, FinalFactAp>>,
        statement: JIRInst
    ) {
        storage.forEachValue { initialBase, initialFactStorage ->
            initialFactStorage.storage.forEach { initialAg, edgeStorage ->
                edgeStorage.forEachValue { base, factStorage ->
                    val finalExclusionAndAg = factStorage.find(statement)
                    if (finalExclusionAndAg != null) {
                        val (exclusion, agSet) = finalExclusionAndAg

                        val initialAp = AccessGraphInitialFactAp(initialBase, initialAg, exclusion)
                        agSet.toList().forEach { ag ->
                            val finalAp = AccessGraphFinalFactAp(base, ag, exclusion)
                            collection += initialAp to finalAp
                        }
                    }
                }
            }
        }
    }

    override fun collectApAtStatement(
        collection: MutableCollection<FinalFactAp>,
        statement: JIRInst,
        initialAp: InitialFactAp
    ) {
        val initialBaseStorage = storage.find(initialAp.base) ?: return
        val edgeStorage = initialBaseStorage.find((initialAp as AccessGraphInitialFactAp).access) ?: return
        edgeStorage.forEachValue { base, factStorage ->
            val (exclusion, agSet) = factStorage.find(statement) ?: return@forEachValue
            agSet.toList().forEach { ag ->
                collection += AccessGraphFinalFactAp(base, ag, exclusion)
            }
        }
    }

    private fun add(
        statement: JIRInst,
        initialAp: AccessGraphInitialFactAp,
        finalAp: AccessGraphFinalFactAp
    ): Pair<InitialFactAp, FinalFactAp>? {
        check(initialAp.exclusions == finalAp.exclusions)

        val edgeStorageForInitialFact = storage.getOrCreate(initialAp.base)
        val edgeStorageForExitFactBase = edgeStorageForInitialFact.getOrCreate(initialAp.access)
        val edgeStorageForExitFact = edgeStorageForExitFactBase.getOrCreate(finalAp.base)

        val exclusion = initialAp.exclusions
        val addedExclusion = edgeStorageForExitFact.add(statement, finalAp.access, exclusion)

        if (addedExclusion === exclusion) return initialAp to finalAp
        if (addedExclusion == null) return null

        val newInitial = initialAp.replaceExclusions(addedExclusion)
        val newFinal = finalAp.replaceExclusions(addedExclusion)
        return newInitial to newFinal
    }

    override fun toString(): String = storage.toString()

    private class InitialFactBaseStorage(
        private val initialStatement: JIRInst,
        private val maxInstIdx: Int
    ) : MethodAnalyzerEdges.EdgeStorage<InitialFactStorage>(initialStatement) {
        override fun createStorage(): InitialFactStorage = InitialFactStorage(initialStatement, maxInstIdx)
    }

    private class InitialFactStorage(
        private val initialStatement: JIRInst,
        private val maxInstIdx: Int
    ) {
        val storage = Object2ObjectOpenHashMap<AccessGraph, FinalFactBaseStorage>()

        fun getOrCreate(initialAccess: AccessGraph): FinalFactBaseStorage = storage.getOrPut(initialAccess) {
            FinalFactBaseStorage(initialStatement, maxInstIdx)
        }

        fun find(initialAccess: AccessGraph): FinalFactBaseStorage? = storage[initialAccess]

        override fun toString(): String = storage.toString()
    }

    private class FinalFactBaseStorage(
        initialStatement: JIRInst,
        private val maxInstIdx: Int
    ) : MethodAnalyzerEdges.EdgeStorage<InstructionFactStorage>(initialStatement) {
        override fun createStorage(): InstructionFactStorage = InstructionFactStorage(maxInstIdx)
    }

    private class InstructionFactStorage(maxInstIdx: Int) {
        private val exclusions = arrayOfNulls<ExclusionSet>(instructionStorageSize(maxInstIdx))
        private val finalFacts = arrayOfNulls<AccessGraphSet>(instructionStorageSize(maxInstIdx))

        fun add(
            statement: JIRInst,
            final: AccessGraph,
            exclusion: ExclusionSet
        ): ExclusionSet? {
            val edgeSetIdx = instructionStorageIdx(statement)
            val currentExclusion = exclusions[edgeSetIdx]

            if (currentExclusion == null) {
                exclusions[edgeSetIdx] = exclusion

                val factSet = AccessGraphSet.create().add(final)
                finalFacts[edgeSetIdx] = factSet

                return exclusion
            }

            val currentFactSet = finalFacts[edgeSetIdx]!!
            val mergedExclusion = currentExclusion.union(exclusion)
            if (mergedExclusion === currentExclusion) {
                val modifiedFactSet = currentFactSet.add(final)
                if (modifiedFactSet == null) return null
                finalFacts[edgeSetIdx] = modifiedFactSet

                return currentExclusion
            }

            exclusions[edgeSetIdx] = mergedExclusion

            val modifiedFactSet = currentFactSet.add(final)
            if (modifiedFactSet != null) {
                finalFacts[edgeSetIdx] = modifiedFactSet
            }

            return mergedExclusion
        }

        fun find(statement: JIRInst): Pair<ExclusionSet, AccessGraphSet>? {
            val edgeSetIdx = instructionStorageIdx(statement)
            val exclusion = exclusions[edgeSetIdx] ?: return null
            return exclusion to finalFacts[edgeSetIdx]!!
        }

        override fun toString(): String = "${finalFacts.sumOf { it?.graphSize ?: 0 }}"
    }
}
