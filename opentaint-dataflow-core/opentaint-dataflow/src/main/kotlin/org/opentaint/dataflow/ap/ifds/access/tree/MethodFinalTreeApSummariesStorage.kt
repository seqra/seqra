package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.access.common.CommonZ2FSummary
import org.opentaint.ir.api.common.cfg.CommonInst

class MethodFinalTreeApSummariesStorage(
    methodInitialStatement: CommonInst,
) : CommonZ2FSummary<AccessTree.AccessNode>(methodInitialStatement),
    TreeFinalApAccess {
    override fun createStorage(): Storage<AccessTree.AccessNode> = MethodZeroToFactSummaryEdgeStorage()

    private class MethodZeroToFactSummaryEdgeStorage: Storage<AccessTree.AccessNode> {
        private var summaryEdgeAccess: AccessTree.AccessNode? = null

        override fun add(edges: List<AccessTree.AccessNode>, added: MutableList<Z2FBBuilder<AccessTree.AccessNode>>) {
            edges.mapNotNullTo(added) { add(it) }
        }

        private fun add(edgeAccess: AccessTree.AccessNode): Z2FBBuilder<AccessTree.AccessNode>? {
            val summaryAccess = summaryEdgeAccess
            if (summaryAccess == null) {
                summaryEdgeAccess = edgeAccess
                return ZeroEdgeBuilderBuilder().setNode(edgeAccess)
            }

            val mergedAccess = summaryAccess.mergeAdd(edgeAccess)
            if (summaryAccess === mergedAccess) return null

            summaryEdgeAccess = mergedAccess
            return ZeroEdgeBuilderBuilder().setNode(mergedAccess)
        }

        override fun collectEdges(dst: MutableList<Z2FBBuilder<AccessTree.AccessNode>>) {
            summaryEdgeAccess?.let { dst += ZeroEdgeBuilderBuilder().setNode(it) }
        }
    }

    private class ZeroEdgeBuilderBuilder : Z2FBBuilder<AccessTree.AccessNode>(), TreeFinalApAccess
}
