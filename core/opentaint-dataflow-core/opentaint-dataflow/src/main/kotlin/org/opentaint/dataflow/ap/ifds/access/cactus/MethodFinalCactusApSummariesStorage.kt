package org.opentaint.dataflow.ap.ifds.access.cactus

import org.opentaint.dataflow.ap.ifds.access.common.CommonZ2FSummary
import org.opentaint.ir.api.common.cfg.CommonInst

class MethodFinalTreeApSummariesStorage(
    methodInitialStatement: CommonInst,
) : CommonZ2FSummary<AccessCactus.AccessNode>(methodInitialStatement),
    CactusFinalApAccess {
    override fun createStorage(): Storage<AccessCactus.AccessNode> = MethodZeroToFactSummaryEdgeStorage()

    private class MethodZeroToFactSummaryEdgeStorage : Storage<AccessCactus.AccessNode> {
        private var summaryEdgeAccess: AccessCactus.AccessNode? = null

        override fun add(
            edges: List<AccessCactus.AccessNode>,
            added: MutableList<Z2FBBuilder<AccessCactus.AccessNode>>,
        ) {
            edges.mapNotNullTo(added) { add(it) }
        }

        private fun add(edgeAccess: AccessCactus.AccessNode): Z2FBBuilder<AccessCactus.AccessNode>? {
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

        override fun collectEdges(dst: MutableList<Z2FBBuilder<AccessCactus.AccessNode>>) {
            summaryEdgeAccess?.let { dst += ZeroEdgeBuilderBuilder().setNode(it) }
        }
    }

    private class ZeroEdgeBuilderBuilder : Z2FBBuilder<AccessCactus.AccessNode>(), CactusFinalApAccess
}
