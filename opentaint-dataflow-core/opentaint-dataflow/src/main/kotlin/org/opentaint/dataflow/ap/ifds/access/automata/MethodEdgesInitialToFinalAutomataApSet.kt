package org.opentaint.dataflow.ap.ifds.access.automata

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageIdx
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageSize
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.MethodEdgesInitialToFinalApSet
import org.opentaint.dataflow.util.collectToListWithPostProcess

class MethodEdgesInitialToFinalAutomataApSet(
    methodInitialStatement: CommonInst,
    maxInstIdx: Int,
    languageManager: LanguageManager
) : MethodEdgesInitialToFinalApSet {
    private val storage = InitialFactBaseStorage(methodInitialStatement, maxInstIdx, languageManager)

    override fun add(
        statement: CommonInst,
        initialAp: InitialFactAp,
        finalAp: FinalFactAp
    ): Pair<InitialFactAp, FinalFactAp>? =
        add(statement, initialAp as AccessGraphInitialFactAp, finalAp as AccessGraphFinalFactAp)

    override fun collectApAtStatement(
        collection: MutableList<Pair<InitialFactAp, FinalFactAp>>,
        statement: CommonInst,
        finalFactPattern: InitialFactAp
    ) {
        storage.forEachValue { initialBase, initialFactStorage ->
            initialFactStorage.storage.forEach { (initialAg, storage) ->
                collectToListWithPostProcess(
                    collection,
                    { storage.collectTo(it, statement, finalFactPattern) },
                    { AccessGraphInitialFactAp(initialBase, initialAg, it.exclusions) to it }
                )
            }
        }
    }

    override fun collectApAtStatement(
        collection: MutableList<FinalFactAp>,
        statement: CommonInst,
        initialAp: InitialFactAp,
        finalFactPattern: InitialFactAp
    ) {
        val initialBaseStorage = storage.find(initialAp.base) ?: return
        val storage = initialBaseStorage.find((initialAp as AccessGraphInitialFactAp).access) ?: return
        storage.collectTo(collection, statement, finalFactPattern)
    }

    private fun add(
        statement: CommonInst,
        initialAp: AccessGraphInitialFactAp,
        finalAp: AccessGraphFinalFactAp
    ): Pair<InitialFactAp, FinalFactAp>? {
        check(initialAp.exclusions == finalAp.exclusions)

        val storage = this.storage
            .getOrCreate(initialAp.base)
            .getOrCreate(initialAp.access)

        val exclusion = initialAp.exclusions
        val addedExclusion = storage.add(statement, finalAp.base, finalAp.access, exclusion)

        if (addedExclusion === exclusion) return initialAp to finalAp
        if (addedExclusion == null) return null

        val newInitial = initialAp.replaceExclusions(addedExclusion)
        val newFinal = finalAp.replaceExclusions(addedExclusion)
        return newInitial to newFinal
    }

    override fun toString(): String = storage.toString()

    private class InitialFactBaseStorage(
        private val initialStatement: CommonInst,
        private val maxInstIdx: Int,
        private val languageManager: LanguageManager
    ) : MethodAnalyzerEdges.EdgeStorage<InitialFactStorage>(initialStatement) {
        override fun createStorage(): InitialFactStorage = InitialFactStorage(initialStatement, maxInstIdx, languageManager)
    }

    private class InitialFactStorage(
        private val initialStatement: CommonInst,
        private val maxInstIdx: Int,
        private val languageManager: LanguageManager
    ) {
        val storage = Object2ObjectOpenHashMap<AccessGraph, Storage>()

        fun getOrCreate(initialAccess: AccessGraph): Storage = storage.getOrPut(initialAccess) {
            Storage(initialStatement, maxInstIdx, languageManager)
        }

        fun find(initialAccess: AccessGraph): Storage? = storage[initialAccess]

        override fun toString(): String = storage.toString()
    }

    private class Storage(
        initialStatement: CommonInst,
        maxInstIdx: Int,
        languageManager: LanguageManager
    ) {
        private val factStorage = FinalFactBaseStorage(initialStatement, maxInstIdx, languageManager)

        fun add(statement: CommonInst, finalBase: AccessPathBase, finalAg: AccessGraph, exclusion: ExclusionSet): ExclusionSet? {
            val finalFactStorage = factStorage.getOrCreate(finalBase)
            val factUpdated = finalFactStorage.addFact(statement, finalAg)

            return finalFactStorage.addExclusion(
                statement, exclusion, returnNullIfNotUpdated = !factUpdated
            )
        }

        fun collectTo(collection: MutableList<FinalFactAp>, statement: CommonInst, finalFactPattern: InitialFactAp) {
            val base = finalFactPattern.base
            val storage = factStorage.find(base) ?: return
            val exclusion = storage.exclusion(statement) ?: return

            collectToListWithPostProcess(
                collection,
                { storage.collectTo(it, statement) },
                { AccessGraphFinalFactAp(base, it, exclusion) }
            )
        }
    }

    private class FinalFactBaseStorage(
        initialStatement: CommonInst,
        private val maxInstIdx: Int,
        private val languageManager: LanguageManager
    ) : MethodAnalyzerEdges.EdgeStorage<InstructionFactStorage>(initialStatement) {
        override fun createStorage(): InstructionFactStorage = InstructionFactStorage(maxInstIdx, languageManager)
    }

    private class InstructionFactStorage(
        maxInstIdx: Int,
        private val languageManager: LanguageManager
    ) {
        private val finalFacts = AccessGraphSetArray.create(instructionStorageSize(maxInstIdx))

        fun addFact(statement: CommonInst, final: AccessGraph): Boolean {
            val edgeSetIdx = instructionStorageIdx(statement, languageManager)
            val currentSet = finalFacts[edgeSetIdx]

            if (currentSet == null) {
                finalFacts[edgeSetIdx] = AccessGraphSet.single(final)
                return true
            }

            val modifiedFactSet = currentSet.add(final) ?: return false
            finalFacts[edgeSetIdx] = modifiedFactSet
            return true
        }

        fun collectTo(collection: MutableList<AccessGraph>, statement: CommonInst) {
            val edgeSetIdx = instructionStorageIdx(statement, languageManager)
            finalFacts[edgeSetIdx]?.toList(collection)
        }

        private val exclusions = arrayOfNulls<ExclusionSet>(instructionStorageSize(maxInstIdx))

        fun addExclusion(
            statement: CommonInst,
            exclusion: ExclusionSet,
            returnNullIfNotUpdated: Boolean
        ): ExclusionSet? {
            val exclusionIdx = instructionStorageIdx(statement, languageManager)
            val currentExclusion = exclusions[exclusionIdx]

            if (currentExclusion == null) {
                exclusions[exclusionIdx] = exclusion
                return exclusion
            }

            val merged = currentExclusion.union(exclusion)
            if (merged === currentExclusion) {
                return if (returnNullIfNotUpdated) null else merged
            }

            exclusions[exclusionIdx] = merged
            return merged
        }

        fun exclusion(statement: CommonInst): ExclusionSet? {
            val exclusionIdx = instructionStorageIdx(statement, languageManager)
            return exclusions[exclusionIdx]
        }

        override fun toString(): String = "${finalFacts.indices.sumOf { finalFacts[it]?.graphSize ?: 0 }}"
    }
}
