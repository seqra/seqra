package org.opentaint.dataflow.jvm.ap.ifds.access.tree

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
import org.opentaint.dataflow.jvm.ap.ifds.access.tree.AccessTree.AccessNode as AccessTreeNode

class MethodEdgesFinalTreeApSet(
    methodInitialStatement: JIRInst, maxInstIdx: Int
) : MethodEdgesFinalApSet {
    private val storage = ZeroInitialFactEdgeStorage(methodInitialStatement, maxInstIdx)
    override fun add(statement: JIRInst, ap: FinalFactAp): FinalFactAp? =
        add(statement, ap as AccessTree)

    private fun add(statement: JIRInst, ap: AccessTree): AccessTree? {
        val edgeSet = storage.getOrCreate(ap.base)

        val edgeAccess = ap.access
        val addedAccess = edgeSet.addEdge(statement, edgeAccess) ?: return null
        if (addedAccess === edgeAccess) return ap

        return AccessTree(ap.base, addedAccess, ExclusionSet.Universe)
    }

    override fun collectApAtStatement(collection: MutableCollection<FinalFactAp>, statement: JIRInst) {
        storage.forEachValue { base, edgeStore ->
            val ap = edgeStore.apAtStatement(statement)
            if (ap != null) {
                collection += AccessTree(base, ap, ExclusionSet.Universe)
            }
        }
    }
}

class MethodEdgesInitialToFinalTreeApSet(
    methodInitialStatement: JIRInst,
    private val maxInstIdx: Int
) : MethodEdgesInitialToFinalApSet {
    private val edgeStorage = TaintedInitialFactEdgeStorage(methodInitialStatement)

    override fun add(
        statement: JIRInst,
        initialAp: InitialFactAp,
        finalAp: FinalFactAp
    ): Pair<InitialFactAp, FinalFactAp>? = add(statement, initialAp as AccessPath, finalAp as AccessTree)

    private fun add(
        statement: JIRInst,
        initialAp: AccessPath,
        finalAp: AccessTree
    ): Pair<AccessPath, AccessTree>? {
        val edgeStorageForInitialFact = edgeStorage.getOrCreate(initialAp.base)
        val edgeStorageForExitFact = edgeStorageForInitialFact.getOrCreate(finalAp.base)

        check(initialAp.exclusions == finalAp.exclusions) { "Edge exclusion mismatch" }

        val edgeSet = edgeStorageForExitFact.getOrCreateNonUniverse(initialAp.access, maxInstIdx)

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
        collection: MutableCollection<Pair<InitialFactAp, FinalFactAp>>,
        statement: JIRInst
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
        collection: MutableCollection<FinalFactAp>,
        statement: JIRInst,
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

private class TaintedInitialFactEdgeStorage(private val initialStatement: JIRInst) :
    EdgeStorage<TaintedExitFactEdgeStorage>(initialStatement) {
    override fun createStorage(): TaintedExitFactEdgeStorage = TaintedExitFactEdgeStorage(initialStatement)
}

private class TaintedExitFactEdgeStorage(initialStatement: JIRInst) :
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
        maxInstIdx: Int
    ): EdgeNonUniverseExclusionMergingStorage = sameInitialAccessEdges.getOrPut(initialAccess) {
        EdgeNonUniverseExclusionMergingStorage(maxInstIdx)
    }

    fun allApAtStatement(statement: JIRInst): Sequence<EdgeAp> =
        sameInitialAccessEdges.asSequence().mapNotNull { (initialAp, storage) ->
            val finalAp = storage.allApAtStatement(statement) ?: return@mapNotNull null
            EdgeAp(finalAp.exclusion, initialAp, finalAp.access)
        }

    fun finalApAtStatement(
        statement: JIRInst,
        initialAp: AccessPath.AccessNode?
    ): EdgeNonUniverseExclusionMergingStorage.AccessWithExclusion? {
        val storage = sameInitialAccessEdges[initialAp] ?: return null
        return storage.allApAtStatement(statement)
    }
}

private class EdgeNonUniverseExclusionMergingStorage(maxInstIdx: Int) {
    private val exclusions = arrayOfNulls<ExclusionSet>(instructionStorageSize(maxInstIdx))
    private val edges = arrayOfNulls<AccessTreeNode>(instructionStorageSize(maxInstIdx))

    fun add(statement: JIRInst, accessWithExclusion: AccessWithExclusion): AccessWithExclusion? {
        val edgeSetIdx = instructionStorageIdx(statement)
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

    fun allApAtStatement(statement: JIRInst): AccessWithExclusion? {
        val edgeSetIdx = instructionStorageIdx(statement)
        val currentExclusion = exclusions[edgeSetIdx] ?: return null
        val access = edges[edgeSetIdx] ?: return null
        return AccessWithExclusion(access, currentExclusion)
    }

    data class AccessWithExclusion(val access: AccessTreeNode, val exclusion: ExclusionSet)
}

private class ZeroInitialFactEdgeStorage(initialStatement: JIRInst, private val maxInstIdx: Int) :
    EdgeStorage<ZeroInitialFactEdges>(initialStatement) {
    override fun createStorage(): ZeroInitialFactEdges = ZeroInitialFactEdges(maxInstIdx)
}

private class ZeroInitialFactEdges(maxInstIdx: Int) {
    private val edges = arrayOfNulls<AccessTreeNode?>(instructionStorageSize(maxInstIdx))

    fun addEdge(statement: JIRInst, accessPath: AccessTreeNode): AccessTreeNode? {
        val factSetIdx = instructionStorageIdx(statement)
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

    fun apAtStatement(statement: JIRInst): AccessTreeNode? =
        edges[instructionStorageIdx(statement)]
}
