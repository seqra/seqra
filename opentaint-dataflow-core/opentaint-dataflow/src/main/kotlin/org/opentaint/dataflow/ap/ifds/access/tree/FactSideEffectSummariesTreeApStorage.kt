package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.SideEffectKind
import org.opentaint.dataflow.ap.ifds.access.common.CommonFactSideEffectSummary
import org.opentaint.dataflow.ap.ifds.access.common.CommonFactSideEffectSummary.FactSEBuilder
import org.opentaint.dataflow.ap.ifds.access.common.CommonFactSideEffectSummary.SideEffectExclusionMergingStorage
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode as AccessTreeNode

class FactSideEffectSummariesTreeApStorage(
    methodInitialStatement: CommonInst
) : CommonFactSideEffectSummary<AccessPath.AccessNode?, AccessTreeNode>(methodInitialStatement),
    TreeInitialApAccess, TreeFinalApAccess {
    override fun createStorage(): Storage<AccessPath.AccessNode?, AccessTreeNode> =
        TaintedSESummariesGroupedByFactStorage()
}

private class TaintedSESummariesInitialApStorage :
    AccessBasedStorage<TaintedSESummariesInitialApStorage>() {
    private var current: TaintedSESummariesMergingStorage? = null

    override fun createStorage() = TaintedSESummariesInitialApStorage()

    fun getOrCreate(initialAccess: AccessPath.AccessNode?): TaintedSESummariesMergingStorage =
        getOrCreateNode(initialAccess).getOrCreateCurrent(initialAccess)

    fun filterSummariesTo(dst: MutableList<FactSEBuilder<AccessPath.AccessNode?>>, containsPattern: AccessTreeNode) {
        filterContains(containsPattern).forEach { node ->
            node.current?.summaries()?.let { dst.addAll(it) }
        }
    }

    fun collectAllSummariesTo(dst: MutableList<FactSEBuilder<AccessPath.AccessNode?>>) {
        allNodes().forEach { node ->
            node.current?.summaries()?.let { dst.addAll(it) }
        }
    }

    private fun getOrCreateCurrent(access: AccessPath.AccessNode?) =
        current ?: TaintedSESummariesMergingStorage(access).also { current = it }
}

private class TaintedSESummariesGroupedByFactStorage :
    CommonFactSideEffectSummary.Storage<AccessPath.AccessNode?, AccessTreeNode> {
    private val storageRoot = TaintedSESummariesInitialApStorage()

    override fun add(
        iap: AccessPath.AccessNode?,
        se: Map<SideEffectKind, ExclusionSet>,
        added: MutableList<FactSEBuilder<AccessPath.AccessNode?>>
    ) {
        val storageNode = storageRoot.getOrCreate(iap)
        for ((kind, exclusion) in se) {
            storageNode.add(kind, exclusion)?.let { added += it }
        }
    }

    override fun collectSummariesTo(
        dst: MutableList<FactSEBuilder<AccessPath.AccessNode?>>,
        initialFactPattern: AccessTree.AccessNode?
    ) {
        if (initialFactPattern != null) {
            filterSummariesTo(dst, initialFactPattern)
        } else {
            collectAllSummariesTo(dst)
        }
    }

    private fun filterSummariesTo(dst: MutableList<FactSEBuilder<AccessPath.AccessNode?>>, containsPattern: AccessTreeNode) {
        storageRoot.filterSummariesTo(dst, containsPattern)
    }

    private fun collectAllSummariesTo(dst: MutableList<FactSEBuilder<AccessPath.AccessNode?>>) {
        storageRoot.collectAllSummariesTo(dst)
    }
}

private class TaintedSESummariesMergingStorage(val initialAccess: AccessPath.AccessNode?):
    SideEffectExclusionMergingStorage<AccessPath.AccessNode?>() {
    override fun createBuilder() = FactSETreeApBuilder().setInitialAp(initialAccess)
}

private class FactSETreeApBuilder: FactSEBuilder<AccessPath.AccessNode?>(),
    TreeInitialApAccess, TreeFinalApAccess {
    override fun nonNullIAP(iap: AccessPath.AccessNode?): AccessPath.AccessNode? = iap
}
