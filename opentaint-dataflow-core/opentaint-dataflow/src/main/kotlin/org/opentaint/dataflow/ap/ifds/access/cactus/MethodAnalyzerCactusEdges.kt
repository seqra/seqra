package org.opentaint.dataflow.ap.ifds.access.cactus

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageIdx
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageSize
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges.EdgeStorage
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.MethodEdgesFinalApSet
import org.opentaint.dataflow.ap.ifds.access.MethodEdgesInitialToFinalApSet
import org.opentaint.dataflow.ap.ifds.access.cactus.AccessCactus.AccessNode as AccessCactusNode

class MethodEdgesFinalCactusApSet(
    methodInitialStatement: CommonInst,
    maxInstIdx: Int,
    languageManager: LanguageManager,
) : MethodEdgesFinalApSet {
    private val storage = ZeroInitialFactEdgeStorage(methodInitialStatement, maxInstIdx, languageManager)
    override fun add(statement: CommonInst, ap: FinalFactAp): FinalFactAp? =
        add(statement, ap as AccessCactus)

    private fun add(statement: CommonInst, ap: AccessCactus): AccessCactus? {
        val edgeSet = storage.getOrCreate(ap.base)

        val edgeAccess = ap.access
        val addedAccess = edgeSet.addEdge(statement, edgeAccess) ?: return null
        if (addedAccess == edgeAccess) return ap

        return AccessCactus(ap.base, addedAccess, ExclusionSet.Universe)
    }

    override fun collectApAtStatement(collection: MutableCollection<FinalFactAp>, statement: CommonInst) {
        storage.forEachValue { base, factEdges ->
            val facts = factEdges.find(statement) ?: return@forEachValue
            collection += AccessCactus(base, facts, ExclusionSet.Universe)
        }
    }
}

class MethodEdgesInitialToFinalCactusApSet(
    methodInitialStatement: CommonInst,
    private val maxInstIdx: Int,
    private val languageManager: LanguageManager
) : MethodEdgesInitialToFinalApSet {
    private val edgeStorage = TaintedInitialFactEdgeStorage(methodInitialStatement)

    override fun collectApAtStatement(
        collection: MutableCollection<Pair<InitialFactAp, FinalFactAp>>,
        statement: CommonInst
    ) {
        edgeStorage.forEachValue { initialBase, edgeStorageForInitialFact ->
            edgeStorageForInitialFact.forEachValue { finalBase, edgeStorageForExitFact ->
                edgeStorageForExitFact.sameInitialAccessEdges.forEach { (initialAccess, edgeSet) ->
                    val (finalAccess, exclusion) = edgeSet.find(statement) ?: return@forEach
                    val initialAp = AccessPathWithCycles(initialBase, initialAccess, exclusion)
                    val finalAp = AccessCactus(finalBase, finalAccess, exclusion)
                    collection += initialAp to finalAp
                }
            }
        }
    }

    override fun collectApAtStatement(
        collection: MutableCollection<FinalFactAp>,
        statement: CommonInst,
        initialAp: InitialFactAp
    ) {
        val edgeStorageForInitialFact = edgeStorage.find(initialAp.base) ?: return
        val initialAccess = (initialAp as AccessPathWithCycles).access
        edgeStorageForInitialFact.forEachValue { finalBase, edgeStorageForExitFact ->
            val edgeSet = edgeStorageForExitFact.sameInitialAccessEdges[initialAccess] ?: return@forEachValue
            val (finalAccess, exclusion) = edgeSet.find(statement) ?: return@forEachValue
            collection += AccessCactus(finalBase, finalAccess, exclusion)
        }
    }

    override fun add(
        statement: CommonInst,
        initialAp: InitialFactAp,
        finalAp: FinalFactAp
    ): Pair<InitialFactAp, FinalFactAp>? = add(statement, initialAp as AccessPathWithCycles, finalAp as AccessCactus)

    private fun add(
        statement: CommonInst,
        initialAp: AccessPathWithCycles,
        finalAp: AccessCactus
    ): Pair<AccessPathWithCycles, AccessCactus>? {
        val edgeStorageForInitialFact = edgeStorage.getOrCreate(initialAp.base)
        val edgeStorageForExitFact = edgeStorageForInitialFact.getOrCreate(finalAp.base)

        check(initialAp.exclusions == finalAp.exclusions) { "Edge exclusion mismatch" }

        val edgeSet = edgeStorageForExitFact.getOrCreateNonUniverse(initialAp.access, maxInstIdx, languageManager)

        val accessWithExclusion = EdgeNonUniverseExclusionMergingStorage.AccessWithExclusion(
            finalAp.access, finalAp.exclusions
        )

        val addedAccessWithExclusion = edgeSet.add(statement, accessWithExclusion) ?: return null

        if (addedAccessWithExclusion === accessWithExclusion) return initialAp to finalAp

        val newInitialAp = AccessPathWithCycles(initialAp.base, initialAp.access, addedAccessWithExclusion.exclusion)

        val newExitAp = AccessCactus(
            finalAp.base, addedAccessWithExclusion.access, addedAccessWithExclusion.exclusion
        )

        return newInitialAp to newExitAp
    }
}

private class TaintedInitialFactEdgeStorage(private val initialStatement: CommonInst) :
    EdgeStorage<TaintedExitFactEdgeStorage>(initialStatement) {
    override fun createStorage(): TaintedExitFactEdgeStorage = TaintedExitFactEdgeStorage(initialStatement)
}

private class TaintedExitFactEdgeStorage(initialStatement: CommonInst) :
    EdgeStorage<TaintedFactAccessEdgeStorage>(initialStatement) {
    override fun createStorage() = TaintedFactAccessEdgeStorage()
}

private class TaintedFactAccessEdgeStorage {
    val sameInitialAccessEdges =
        Object2ObjectOpenHashMap<AccessPathWithCycles.AccessNode?, EdgeNonUniverseExclusionMergingStorage>()

    fun getOrCreateNonUniverse(
        initialAccess: AccessPathWithCycles.AccessNode?,
        maxInstIdx: Int,
        languageManager: LanguageManager
    ): EdgeNonUniverseExclusionMergingStorage = sameInitialAccessEdges.getOrPut(initialAccess) {
        EdgeNonUniverseExclusionMergingStorage(maxInstIdx, languageManager)
    }
}

private class EdgeNonUniverseExclusionMergingStorage(maxInstIdx: Int, private val languageManager: LanguageManager) {
    private val exclusions = arrayOfNulls<ExclusionSet>(instructionStorageSize(maxInstIdx))
    private val edges = arrayOfNulls<AccessCactusNode>(instructionStorageSize(maxInstIdx))

    fun add(statement: CommonInst, accessWithExclusion: AccessWithExclusion): AccessWithExclusion? {
        val edgeSetIdx = instructionStorageIdx(statement, languageManager)
        val currentExclusion = exclusions[edgeSetIdx]

        if (currentExclusion == null) {
            exclusions[edgeSetIdx] = accessWithExclusion.exclusion
            edges[edgeSetIdx] = accessWithExclusion.access
            return accessWithExclusion
        }

        val currentAccess = edges[edgeSetIdx]!!
        val mergedExclusion = currentExclusion.union(accessWithExclusion.exclusion)
        exclusions[edgeSetIdx] = mergedExclusion

        val mergedAccess = currentAccess.mergeAdd(accessWithExclusion.access)
        if (mergedAccess === currentAccess) return null

        edges[edgeSetIdx] = mergedAccess
        return AccessWithExclusion(mergedAccess, mergedExclusion)
    }

    fun find(statement: CommonInst): AccessWithExclusion? {
        val edgeSetIdx = instructionStorageIdx(statement, languageManager)
        val exclusion = exclusions[edgeSetIdx] ?: return null
        return AccessWithExclusion(edges[edgeSetIdx]!!, exclusion)
    }

    data class AccessWithExclusion(val access: AccessCactusNode, val exclusion: ExclusionSet)
}

private class ZeroInitialFactEdgeStorage(
    initialStatement: CommonInst,
    private val maxInstIdx: Int,
    private val languageManager: LanguageManager
) : EdgeStorage<ZeroInitialFactEdges>(initialStatement) {
    override fun createStorage(): ZeroInitialFactEdges = ZeroInitialFactEdges(maxInstIdx, languageManager)
}

private class ZeroInitialFactEdges(maxInstIdx: Int, private val languageManager: LanguageManager) {
    private val edges = arrayOfNulls<AccessCactusNode?>(instructionStorageSize(maxInstIdx))

    fun addEdge(statement: CommonInst, accessPath: AccessCactusNode): AccessCactusNode? {
        val factSetIdx = instructionStorageIdx(statement, languageManager)
        val factSet = edges[factSetIdx]

        if (factSet == null) {
            edges[factSetIdx] = accessPath
            return accessPath
        }

        val mergedFacts = factSet.mergeAdd(accessPath)
        if (mergedFacts == factSet) {
            return null
        }

        edges[factSetIdx] = mergedFacts
        return mergedFacts
    }

    fun find(statement: CommonInst): AccessCactusNode? =
        edges[instructionStorageIdx(statement, languageManager)]
}
