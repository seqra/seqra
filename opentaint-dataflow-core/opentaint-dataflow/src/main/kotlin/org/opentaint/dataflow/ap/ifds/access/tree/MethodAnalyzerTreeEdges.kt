package org.opentaint.dataflow.ap.ifds.access.tree

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
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode as AccessTreeNode

class MethodEdgesFinalTreeApSet(
    methodInitialStatement: CommonInst,
    maxInstIdx: Int,
    languageManager: LanguageManager
) : MethodEdgesFinalApSet {
    private val storage = ZeroInitialFactEdgeStorage(methodInitialStatement, maxInstIdx, languageManager)
    override fun add(statement: CommonInst, ap: FinalFactAp): FinalFactAp? =
        add(statement, ap as AccessTree)

    private fun add(statement: CommonInst, ap: AccessTree): AccessTree? {
        val edgeSet = storage.getOrCreate(ap.base)

        val edgeAccess = ap.access
        val addedAccess = edgeSet.addEdge(statement, edgeAccess) ?: return null
        if (addedAccess === edgeAccess) return ap

        return AccessTree(ap.base, addedAccess, ExclusionSet.Universe)
    }

    override fun collectApAtStatement(collection: MutableList<FinalFactAp>, statement: CommonInst) {
        storage.forEachValue { base, edgeStore ->
            val ap = edgeStore.apAtStatement(statement)
            if (ap != null) {
                collection += AccessTree(base, ap, ExclusionSet.Universe)
            }
        }
    }
}

class MethodEdgesInitialToFinalTreeApSet(
    methodInitialStatement: CommonInst,
    private val maxInstIdx: Int,
    private val languageManager: LanguageManager
) : MethodEdgesInitialToFinalApSet {
    private val edgeStorage = TaintedInitialFactEdgeStorage(methodInitialStatement)

    override fun add(
        statement: CommonInst,
        initialAp: InitialFactAp,
        finalAp: FinalFactAp
    ): Pair<InitialFactAp, FinalFactAp>? = add(statement, initialAp as AccessPath, finalAp as AccessTree)

    private fun add(
        statement: CommonInst,
        initialAp: AccessPath,
        finalAp: AccessTree
    ): Pair<AccessPath, AccessTree>? {
        val edgeStorageForInitialFact = edgeStorage.getOrCreate(initialAp.base)
        val edgeStorageForExitFact = edgeStorageForInitialFact.getOrCreate(finalAp.base)

        check(initialAp.exclusions == finalAp.exclusions) { "Edge exclusion mismatch" }

        val edgeSet = edgeStorageForExitFact.getOrCreateNonUniverse(initialAp.access, maxInstIdx, languageManager)

        val accessWithExclusion = EdgeNonUniverseExclusionMergingStorage.AccessWithExclusion(
            finalAp.access, finalAp.exclusions
        )

        val addedAccessWithExclusion = edgeSet.add(statement, accessWithExclusion) ?: return null

        if (addedAccessWithExclusion === accessWithExclusion) return initialAp to finalAp

        val newInitialAp = AccessPath(initialAp.base, initialAp.access, addedAccessWithExclusion.exclusion)

        val newExitAp = AccessTree(
            finalAp.base, addedAccessWithExclusion.access, addedAccessWithExclusion.exclusion
        )

        return newInitialAp to newExitAp
    }

    override fun collectApAtStatement(
        collection: MutableList<Pair<InitialFactAp, FinalFactAp>>,
        statement: CommonInst
    ) {
        edgeStorage.forEachValue { initialBase, storageForInitial ->
            storageForInitial.forEachValue { finalFactBase, storage ->
                storage.allApAtStatement(statement).forEach { edgeAp ->
                    val initialAp = AccessPath(initialBase, edgeAp.initialAp, edgeAp.exclusion)
                    val finalAp = AccessTree(finalFactBase, edgeAp.exitAp, edgeAp.exclusion)
                    collection += initialAp to finalAp
                }
            }
        }
    }

    override fun collectApAtStatement(
        collection: MutableList<FinalFactAp>,
        statement: CommonInst,
        initialAp: InitialFactAp
    ) {
        val initialStorage = edgeStorage.find(initialAp.base) ?: return
        initialStorage.forEachValue { finalFactBase, storage ->
            val finalAp = storage.finalApAtStatement(statement, (initialAp as AccessPath).access)
            if (finalAp != null) {
                collection += AccessTree(finalFactBase, finalAp.access, finalAp.exclusion)
            }
        }
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

private data class EdgeAp(
    val exclusion: ExclusionSet,
    val initialAp: AccessPath.AccessNode?,
    val exitAp: AccessTreeNode
)

private class TaintedFactAccessEdgeStorage {
    private val sameInitialAccessEdges =
        Object2ObjectOpenHashMap<AccessPath.AccessNode?, EdgeNonUniverseExclusionMergingStorage>()

    fun getOrCreateNonUniverse(
        initialAccess: AccessPath.AccessNode?,
        maxInstIdx: Int,
        languageManager: LanguageManager
    ): EdgeNonUniverseExclusionMergingStorage = sameInitialAccessEdges.getOrPut(initialAccess) {
        EdgeNonUniverseExclusionMergingStorage(maxInstIdx, languageManager)
    }

    fun allApAtStatement(statement: CommonInst): Sequence<EdgeAp> =
        sameInitialAccessEdges.asSequence().mapNotNull { (initialAp, storage) ->
            val finalAp = storage.allApAtStatement(statement) ?: return@mapNotNull null
            EdgeAp(finalAp.exclusion, initialAp, finalAp.access)
        }

    fun finalApAtStatement(
        statement: CommonInst,
        initialAp: AccessPath.AccessNode?
    ): EdgeNonUniverseExclusionMergingStorage.AccessWithExclusion? {
        val storage = sameInitialAccessEdges[initialAp] ?: return null
        return storage.allApAtStatement(statement)
    }
}

private class EdgeNonUniverseExclusionMergingStorage(
    maxInstIdx: Int,
    private val languageManager: LanguageManager
) {
    private val exclusions = arrayOfNulls<ExclusionSet>(instructionStorageSize(maxInstIdx))
    private val edges = arrayOfNulls<AccessTreeNode>(instructionStorageSize(maxInstIdx))

    fun add(statement: CommonInst, accessWithExclusion: AccessWithExclusion): AccessWithExclusion? {
        val edgeSetIdx = instructionStorageIdx(statement, languageManager)
        val currentExclusion = exclusions[edgeSetIdx]

        if (currentExclusion == null) {
            exclusions[edgeSetIdx] = accessWithExclusion.exclusion
            edges[edgeSetIdx] = accessWithExclusion.access
            return accessWithExclusion
        }

        val mergedExclusion = currentExclusion.union(accessWithExclusion.exclusion)
        exclusions[edgeSetIdx] = mergedExclusion

        val currentAccess = edges[edgeSetIdx]!!
        val mergedAccess = currentAccess.mergeAdd(accessWithExclusion.access)
        if (mergedAccess === currentAccess) return null

        edges[edgeSetIdx] = mergedAccess
        return AccessWithExclusion(mergedAccess, mergedExclusion)
    }

    fun allApAtStatement(statement: CommonInst): AccessWithExclusion? {
        val edgeSetIdx = instructionStorageIdx(statement, languageManager)
        val currentExclusion = exclusions[edgeSetIdx] ?: return null
        val access = edges[edgeSetIdx] ?: return null
        return AccessWithExclusion(access, currentExclusion)
    }

    data class AccessWithExclusion(val access: AccessTreeNode, val exclusion: ExclusionSet)
}

private class ZeroInitialFactEdgeStorage(
    initialStatement: CommonInst,
    private val maxInstIdx: Int,
    private val languageManager: LanguageManager
) : EdgeStorage<ZeroInitialFactEdges>(initialStatement) {
    override fun createStorage(): ZeroInitialFactEdges = ZeroInitialFactEdges(maxInstIdx, languageManager)
}

private class ZeroInitialFactEdges(
    maxInstIdx: Int,
    private val languageManager: LanguageManager
) {
    private val edges = arrayOfNulls<AccessTreeNode?>(instructionStorageSize(maxInstIdx))

    fun addEdge(statement: CommonInst, accessPath: AccessTreeNode): AccessTreeNode? {
        val factSetIdx = instructionStorageIdx(statement, languageManager)
        val factSet = edges[factSetIdx]

        if (factSet == null) {
            edges[factSetIdx] = accessPath
            return accessPath
        }

        val mergedFacts = factSet.mergeAdd(accessPath)
        if (mergedFacts === factSet) {
            return null
        }

        edges[factSetIdx] = mergedFacts
        return mergedFacts
    }

    fun apAtStatement(statement: CommonInst): AccessTreeNode? =
        edges[instructionStorageIdx(statement, languageManager)]
}
