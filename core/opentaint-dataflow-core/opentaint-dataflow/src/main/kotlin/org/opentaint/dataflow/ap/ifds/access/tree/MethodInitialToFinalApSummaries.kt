package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.common.CommonF2FSummary
import org.opentaint.dataflow.ap.ifds.access.common.CommonF2FSummary.F2FBBuilder
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.ANY_ACCESSOR_IDX
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode as AccessTreeNode

class MethodInitialToFinalApSummaries(
    methodInitialStatement: CommonInst,
    override val apManager: TreeApManager,
) : CommonF2FSummary<AccessPath.AccessNode?, AccessTreeNode>(methodInitialStatement),
    TreeInitialApAccess, TreeFinalApAccess {
    override fun createStorage(): Storage<AccessPath.AccessNode?, AccessTreeNode> =
        MethodTaintedSummariesGroupedByFactStorage(apManager)
}

private class MethodTaintedSummariesInitialApStorage(
    val apManager: TreeApManager,
) : AccessBasedStorage<MethodTaintedSummariesInitialApStorage>() {
    private var current: MethodTaintedSummariesMergingStorage? = null

    override fun createStorage() = MethodTaintedSummariesInitialApStorage(apManager)

    fun getOrCreate(initialAccess: AccessPath.AccessNode?): MethodTaintedSummariesMergingStorage =
        getOrCreateNode(initialAccess).getOrCreateCurrent(initialAccess)

    fun filterSummariesTo(dst: MutableList<F2FBBuilder<AccessPath.AccessNode?, AccessTreeNode>>, containsPattern: AccessTreeNode) {
        filterContains(containsPattern).forEach { node ->
            node.current?.summaries()?.let { dst.add(it) }
        }
    }

    override fun collectNodesContainsAccessor(
        pattern: AccessTreeNode,
        accessor: AccessorIdx,
        nodes: MutableList<MethodTaintedSummariesInitialApStorage>
    ) {
        if (accessor == ANY_ACCESSOR_IDX) {
            nodes += allNodes()
            return
        }

        super.collectNodesContainsAccessor(pattern, accessor, nodes)
    }

    fun collectAllSummariesTo(dst: MutableList<F2FBBuilder<AccessPath.AccessNode?, AccessTreeNode>>) {
        allNodes().forEach { node ->
            node.current?.summaries()?.let { dst.add(it) }
        }
    }

    private fun getOrCreateCurrent(access: AccessPath.AccessNode?) =
        current ?: MethodTaintedSummariesMergingStorage(apManager, access).also { current = it }
}

private class MethodTaintedSummariesGroupedByFactStorage(
    apManager: TreeApManager,
) : CommonF2FSummary.Storage<AccessPath.AccessNode?, AccessTreeNode> {
    private val nonUniverseAccessPath = MethodTaintedSummariesInitialApStorage(apManager)

    override fun add(
        edges: List<CommonF2FSummary.StorageEdge<AccessPath.AccessNode?, AccessTreeNode>>,
        added: MutableList<F2FBBuilder<AccessPath.AccessNode?, AccessTreeNode>>
    ) {
        addNonUniverseEdges(edges, added)
    }

    private fun addNonUniverseEdges(
        edges: List<CommonF2FSummary.StorageEdge<AccessPath.AccessNode?, AccessTreeNode>>,
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
        dst: MutableList<F2FBBuilder<AccessPath.AccessNode?, AccessTreeNode>>,
        initialFactPatter: AccessTreeNode?
    ) {
        if (initialFactPatter != null) {
            filterSummariesTo(dst, initialFactPatter)
        } else {
            collectAllSummariesTo(dst)
        }
    }

    private fun filterSummariesTo(dst: MutableList<F2FBBuilder<AccessPath.AccessNode?, AccessTreeNode>>, containsPattern: AccessTreeNode) {
        nonUniverseAccessPath.filterSummariesTo(dst, containsPattern)
    }

    private fun collectAllSummariesTo(dst: MutableList<F2FBBuilder<AccessPath.AccessNode?, AccessTreeNode>>) {
        nonUniverseAccessPath.collectAllSummariesTo(dst)
    }
}

private class MethodTaintedSummariesMergingStorage(
    val apManager: TreeApManager,
    val initialAccess: AccessPath.AccessNode?
) {
    private var exclusion: ExclusionSet? = null
    private val treeStorage = MergingTreeSummaryStorage(apManager)

    fun add(exitAccess: AccessTreeNode, addedEx: ExclusionSet): Boolean {
        val currentExclusion = exclusion
        if (currentExclusion == null) {
            exclusion = addedEx
            treeStorage.add(exitAccess)
            return true
        }

        val mergedExclusion = currentExclusion.union(addedEx)
        if (mergedExclusion === currentExclusion) {
            return treeStorage.add(exitAccess)
        }

        treeStorage.add(exitAccess)
        exclusion = mergedExclusion

        return true
    }

    fun getAndResetDelta(): Sequence<F2FBBuilder<AccessPath.AccessNode?, AccessTreeNode>> {
        val delta = treeStorage.getAndResetDelta() ?: return emptySequence()

        return FactToFactEdgeBuilderBuilder(apManager)
            .setInitialAp(initialAccess)
            .setExitAp(delta)
            .setExclusion(exclusion!!)
            .let { sequenceOf(it) }
    }

    fun summaries(): F2FBBuilder<AccessPath.AccessNode?, AccessTreeNode>? {
        val exclusion = this.exclusion ?: return null
        val edges = this.treeStorage.edges() ?: return null
        return FactToFactEdgeBuilderBuilder(apManager)
            .setInitialAp(initialAccess)
            .setExitAp(edges)
            .setExclusion(exclusion)
    }
}

private class FactToFactEdgeBuilderBuilder(
    override val apManager: TreeApManager,
) : F2FBBuilder<AccessPath.AccessNode?, AccessTreeNode>(),
    TreeInitialApAccess, TreeFinalApAccess {
    override fun nonNullIAP(iap: AccessPath.AccessNode?): AccessPath.AccessNode? = iap
}
