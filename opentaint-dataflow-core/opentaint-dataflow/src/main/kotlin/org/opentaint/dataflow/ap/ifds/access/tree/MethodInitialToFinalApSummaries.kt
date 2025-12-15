package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.common.CommonF2FSummary
import org.opentaint.dataflow.ap.ifds.access.common.CommonF2FSummary.F2FBBuilder
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode as AccessTreeNode

class MethodInitialToFinalApSummaries(
    methodInitialStatement: CommonInst,
) : CommonF2FSummary<AccessPath.AccessNode?, AccessTreeNode>(methodInitialStatement),
    TreeInitialApAccess, TreeFinalApAccess {
    override fun createStorage(): Storage<AccessPath.AccessNode?, AccessTree.AccessNode> =
        MethodTaintedSummariesGroupedByFactStorage()
}

private class MethodTaintedSummariesInitialApStorage :
    AccessBasedStorage<MethodTaintedSummariesInitialApStorage>() {
    private var current: MethodTaintedSummariesMergingStorage? = null

    override fun createStorage() = MethodTaintedSummariesInitialApStorage()

    fun getOrCreate(initialAccess: AccessPath.AccessNode?): MethodTaintedSummariesMergingStorage =
        getOrCreateNode(initialAccess).getOrCreateCurrent(initialAccess)

    fun filterSummariesTo(dst: MutableList<F2FBBuilder<AccessPath.AccessNode?, AccessTree.AccessNode>>, containsPattern: AccessTreeNode) {
        filterContains(containsPattern).forEach { node ->
            node.current?.summaries()?.let { dst.add(it) }
        }
    }

    fun collectAllSummariesTo(dst: MutableList<F2FBBuilder<AccessPath.AccessNode?, AccessTree.AccessNode>>) {
        allNodes().forEach { node ->
            node.current?.summaries()?.let { dst.add(it) }
        }
    }

    private fun getOrCreateCurrent(access: AccessPath.AccessNode?) =
        current ?: MethodTaintedSummariesMergingStorage(access).also { current = it }
}

private class MethodTaintedSummariesGroupedByFactStorage :
    CommonF2FSummary.Storage<AccessPath.AccessNode?, AccessTreeNode> {
    private val nonUniverseAccessPath = MethodTaintedSummariesInitialApStorage()

    override fun add(
        edges: List<CommonF2FSummary.StorageEdge<AccessPath.AccessNode?, AccessTree.AccessNode>>,
        added: MutableList<F2FBBuilder<AccessPath.AccessNode?, AccessTree.AccessNode>>
    ) {
        addNonUniverseEdges(edges, added)
    }

    private fun addNonUniverseEdges(
        edges: List<CommonF2FSummary.StorageEdge<AccessPath.AccessNode?, AccessTree.AccessNode>>,
        added: MutableList<F2FBBuilder<AccessPath.AccessNode?, AccessTreeNode>>
    ) {
        val modifiedStorages = mutableListOf<MethodTaintedSummariesMergingStorage>()

        for (edge in edges) {
            addNonUniverseEdge(edge.initial, edge.final, edge.exclusion, modifiedStorages)
        }

        modifiedStorages.flatMapTo(added) { it.getAndResetDelta() }
    }

    private fun addNonUniverseEdge(
        initialAccess: AccessPath.AccessNode?,
        exitAccess: AccessTreeNode,
        exclusion: ExclusionSet,
        modifiedStorages: MutableList<MethodTaintedSummariesMergingStorage>
    ) {
        val storage = nonUniverseAccessPath.getOrCreate(initialAccess)
        val storageModified = storage.add(exitAccess, exclusion)

        if (storageModified) {
            modifiedStorages.add(storage)
        }
    }

    override fun collectSummariesTo(
        dst: MutableList<F2FBBuilder<AccessPath.AccessNode?, AccessTree.AccessNode>>,
        initialFactPatter: AccessTree.AccessNode?
    ) {
        if (initialFactPatter != null) {
            filterSummariesTo(dst, initialFactPatter)
        } else {
            collectAllSummariesTo(dst)
        }
    }

    private fun filterSummariesTo(dst: MutableList<F2FBBuilder<AccessPath.AccessNode?, AccessTree.AccessNode>>, containsPattern: AccessTreeNode) {
        nonUniverseAccessPath.filterSummariesTo(dst, containsPattern)
    }

    private fun collectAllSummariesTo(dst: MutableList<F2FBBuilder<AccessPath.AccessNode?, AccessTree.AccessNode>>) {
        nonUniverseAccessPath.collectAllSummariesTo(dst)
    }
}

private class MethodTaintedSummariesMergingStorage(val initialAccess: AccessPath.AccessNode?) {
    private var exclusion: ExclusionSet? = null
    private var edges: AccessTreeNode? = null
    private var edgesDelta: AccessTreeNode? = null

    fun add(exitAccess: AccessTreeNode, addedEx: ExclusionSet): Boolean {
        val currentExclusion = exclusion
        if (currentExclusion == null) {
            exclusion = addedEx
            edges = exitAccess
            edgesDelta = exitAccess
            return true
        }

        val currentEdges = edges!!
        val mergedExclusion = currentExclusion.union(addedEx)
        if (mergedExclusion === currentExclusion) {
            val (modifiedEdges, modificationDelta) = currentEdges.mergeAddDelta(exitAccess)
            if (modificationDelta == null) return false

            edges = modifiedEdges
            edgesDelta = edgesDelta?.mergeAdd(modificationDelta) ?: modificationDelta
            return true
        }

        val mergedAp = currentEdges.mergeAdd(exitAccess)
        exclusion = mergedExclusion
        edges = mergedAp
        edgesDelta = mergedAp

        return true
    }

    fun getAndResetDelta(): Sequence<F2FBBuilder<AccessPath.AccessNode?, AccessTree.AccessNode>> {
        val delta = edgesDelta ?: return emptySequence()
        edgesDelta = null

        return FactToFactEdgeBuilderBuilder()
            .setInitialAp(initialAccess)
            .setExitAp(delta)
            .setExclusion(exclusion!!)
            .let { sequenceOf(it) }
    }

    fun summaries(): F2FBBuilder<AccessPath.AccessNode?, AccessTree.AccessNode>? {
        val exclusion = this.exclusion ?: return null
        val edges = this.edges!!
        return FactToFactEdgeBuilderBuilder()
            .setInitialAp(initialAccess)
            .setExitAp(edges)
            .setExclusion(exclusion)
    }
}

private class FactToFactEdgeBuilderBuilder: F2FBBuilder<AccessPath.AccessNode?, AccessTreeNode>(),
        TreeInitialApAccess, TreeFinalApAccess {
    override fun nonNullIAP(iap: AccessPath.AccessNode?): AccessPath.AccessNode? = iap
}
