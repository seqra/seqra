package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.access.common.CommonZ2FSummary
import org.opentaint.ir.api.common.cfg.CommonInst

class MethodFinalTreeApSummariesStorage(
    methodInitialStatement: CommonInst,
    override val apManager: TreeApManager,
) : CommonZ2FSummary<AccessTree.AccessNode>(methodInitialStatement),
    TreeFinalApAccess {
    override fun createStorage(): Storage<AccessTree.AccessNode> = MethodZeroToFactSummaryEdgeStorage(apManager)

    private class MethodZeroToFactSummaryEdgeStorage(val apManager: TreeApManager): Storage<AccessTree.AccessNode> {
        private val treeStorage = MergingTreeSummaryStorage(apManager.refManager)

        override fun add(edges: List<AccessTree.AccessNode>, added: MutableList<Z2FBBuilder<AccessTree.AccessNode>>) {
            edges.forEach { treeStorage.add(it) }

            val delta = treeStorage.getAndResetDelta() ?: return
            added += ZeroEdgeBuilderBuilder(apManager).setNode(delta)
        }

        override fun collectEdges(dst: MutableList<Z2FBBuilder<AccessTree.AccessNode>>) {
            treeStorage.edges()?.let { dst += ZeroEdgeBuilderBuilder(apManager).setNode(it) }
        }
    }

    private class ZeroEdgeBuilderBuilder(
        override val apManager: TreeApManager,
    ) : Z2FBBuilder<AccessTree.AccessNode>(), TreeFinalApAccess
}
