package org.opentaint.dataflow.jvm.ap.ifds.access.cactus

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.jvm.ap.ifds.ExclusionSet
import org.opentaint.dataflow.jvm.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageIdx
import org.opentaint.dataflow.jvm.ap.ifds.MethodAnalyzerEdges.Companion.instructionStorageSize
import org.opentaint.dataflow.jvm.ap.ifds.MethodAnalyzerEdges.EdgeStorage
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.MethodEdgesFinalApSet
import org.opentaint.dataflow.jvm.ap.ifds.access.MethodEdgesInitialToFinalApSet
import org.opentaint.dataflow.jvm.ap.ifds.access.cactus.AccessCactus.AccessNode as AccessCactusNode

class MethodEdgesFinalCactusApSet(
    methodInitialStatement: JIRInst, maxInstIdx: Int
) : MethodEdgesFinalApSet {
    private val storage = ZeroInitialFactEdgeStorage(methodInitialStatement, maxInstIdx)
    override fun add(statement: JIRInst, ap: FinalFactAp): FinalFactAp? =
        add(statement, ap as AccessCactus)

    private fun add(statement: JIRInst, ap: AccessCactus): AccessCactus? {
        val edgeSet = storage.getOrCreate(ap.base)

        val edgeAccess = ap.access
        val addedAccess = edgeSet.addEdge(statement, edgeAccess) ?: return null
        if (addedAccess == edgeAccess) return ap

        return AccessCactus(ap.base, addedAccess, ExclusionSet.Universe)
    }

    override fun collectApAtStatement(collection: MutableCollection<FinalFactAp>, statement: JIRInst) {
        storage.forEachValue { base, factEdges ->
            val facts = factEdges.find(statement) ?: return@forEachValue
            collection += AccessCactus(base, facts, ExclusionSet.Universe)
        }
    }
}

class MethodEdgesInitialToFinalCactusApSet(
    methodInitialStatement: JIRInst,
    private val maxInstIdx: Int
) : MethodEdgesInitialToFinalApSet {
    private val edgeStorage = TaintedInitialFactEdgeStorage(methodInitialStatement)

    override fun collectApAtStatement(
        collection: MutableCollection<Pair<InitialFactAp, FinalFactAp>>,
        statement: JIRInst
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
        statement: JIRInst,
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
        statement: JIRInst,
        initialAp: InitialFactAp,
        finalAp: FinalFactAp
    ): Pair<InitialFactAp, FinalFactAp>? = add(statement, initialAp as AccessPathWithCycles, finalAp as AccessCactus)

    private fun add(
        statement: JIRInst,
        initialAp: AccessPathWithCycles,
        finalAp: AccessCactus
    ): Pair<AccessPathWithCycles, AccessCactus>? {
        val edgeStorageForInitialFact = edgeStorage.getOrCreate(initialAp.base)
        val edgeStorageForExitFact = edgeStorageForInitialFact.getOrCreate(finalAp.base)

        check(initialAp.exclusions == finalAp.exclusions) { "Edge exclusion mismatch" }

        val edgeSet = edgeStorageForExitFact.getOrCreateNonUniverse(initialAp.access, maxInstIdx)

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

private class TaintedInitialFactEdgeStorage(private val initialStatement: JIRInst) :
    EdgeStorage<TaintedExitFactEdgeStorage>(initialStatement) {
    override fun createStorage(): TaintedExitFactEdgeStorage = TaintedExitFactEdgeStorage(initialStatement)
}

private class TaintedExitFactEdgeStorage(initialStatement: JIRInst) :
    EdgeStorage<TaintedFactAccessEdgeStorage>(initialStatement) {
    override fun createStorage() = TaintedFactAccessEdgeStorage()
}

private class TaintedFactAccessEdgeStorage {
    val sameInitialAccessEdges =
        Object2ObjectOpenHashMap<AccessPathWithCycles.AccessNode?, EdgeNonUniverseExclusionMergingStorage>()

    fun getOrCreateNonUniverse(
        initialAccess: AccessPathWithCycles.AccessNode?,
        maxInstIdx: Int
    ): EdgeNonUniverseExclusionMergingStorage = sameInitialAccessEdges.getOrPut(initialAccess) {
        EdgeNonUniverseExclusionMergingStorage(maxInstIdx)
    }
}

private class EdgeNonUniverseExclusionMergingStorage(maxInstIdx: Int) {
    private val exclusions = arrayOfNulls<ExclusionSet>(instructionStorageSize(maxInstIdx))
    private val edges = arrayOfNulls<AccessCactusNode>(instructionStorageSize(maxInstIdx))

    fun add(statement: JIRInst, accessWithExclusion: AccessWithExclusion): AccessWithExclusion? {
        val edgeSetIdx = instructionStorageIdx(statement)
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

    fun find(statement: JIRInst): AccessWithExclusion? {
        val edgeSetIdx = instructionStorageIdx(statement)
        val exclusion = exclusions[edgeSetIdx] ?: return null
        return AccessWithExclusion(edges[edgeSetIdx]!!, exclusion)
    }

    data class AccessWithExclusion(val access: AccessCactusNode, val exclusion: ExclusionSet)
}

private class ZeroInitialFactEdgeStorage(initialStatement: JIRInst, private val maxInstIdx: Int) :
    EdgeStorage<ZeroInitialFactEdges>(initialStatement) {
    override fun createStorage(): ZeroInitialFactEdges = ZeroInitialFactEdges(maxInstIdx)
}

private class ZeroInitialFactEdges(maxInstIdx: Int) {
    private val edges = arrayOfNulls<AccessCactusNode?>(instructionStorageSize(maxInstIdx))

    fun addEdge(statement: JIRInst, accessPath: AccessCactusNode): AccessCactusNode? {
        val factSetIdx = instructionStorageIdx(statement)
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

    fun find(statement: JIRInst): AccessCactusNode? =
        edges[instructionStorageIdx(statement)]
}
