package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Edge
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.MethodSummaryZeroEdgesForExitPoint
import org.opentaint.dataflow.ap.ifds.SummaryFactStorage
import org.opentaint.dataflow.ap.ifds.ZeroToFactEdgeBuilder
import org.opentaint.dataflow.ap.ifds.access.MethodFinalApSummariesStorage

class MethodFinalTreeApSummariesStorage(
    methodInitialStatement: CommonInst
) : MethodFinalApSummariesStorage {
    private val storage = MethodZeroToFactSummariesStorage(methodInitialStatement)
    override fun add(edges: List<Edge.ZeroToFact>, addedEdges: MutableList<ZeroToFactEdgeBuilder>) {
        storage.add(edges, addedEdges)
    }

    override fun collectAllEdgesTo(dst: MutableList<ZeroToFactEdgeBuilder>) {
        storage.collectAllEdgesTo(dst)
    }

    override fun filterEdgesTo(dst: MutableList<ZeroToFactEdgeBuilder>, finalFactBase: AccessPathBase) {
        storage.filterEdgesTo(dst, finalFactBase)
    }
}

private class MethodZeroToFactSummariesStorage(methodEntryPoint: CommonInst) :
    MethodSummaryZeroEdgesForExitPoint<MethodZeroToFactSummaries, AccessPathBase>(methodEntryPoint) {

    override fun createStorage(): MethodZeroToFactSummaries = MethodZeroToFactSummaries(methodEntryPoint)

    override fun storageAdd(
        storage: MethodZeroToFactSummaries,
        edges: List<Edge.ZeroToFact>,
        added: MutableList<ZeroToFactEdgeBuilder>
    ) = storage.add(edges, added)

    override fun storageCollectAllEdgesTo(dst: MutableList<ZeroToFactEdgeBuilder>, storage: MethodZeroToFactSummaries) {
        storage.collectAddEdgesTo(dst)
    }

    override fun storageFilterEdgesTo(
        dst: MutableList<ZeroToFactEdgeBuilder>,
        storage: MethodZeroToFactSummaries,
        containsPattern: AccessPathBase
    ) {
        storage.filterTo(dst, containsPattern)
    }
}

private class MethodZeroToFactSummaries(methodEntryPoint: CommonInst) :
    SummaryFactStorage<MethodZeroToFactSummaryEdgeStorage>(methodEntryPoint) {
    override fun createStorage() = MethodZeroToFactSummaryEdgeStorage()

    fun add(edges: List<Edge.ZeroToFact>, added: MutableList<ZeroToFactEdgeBuilder>) {
        val sameExitBaseEdges = edges.groupBy { it.factAp.base }
        for ((exitBase, sameBaseEdges) in sameExitBaseEdges) {
            val storage = getOrCreate(exitBase)

            val addedEdge = sameBaseEdges.fold(null as ZeroEdgeBuilderBuilder?) { addedEdge, edge ->
                storage.add((edge.factAp as AccessTree).access) ?: addedEdge
            }

            if (addedEdge != null) {
                added.add(addedEdge.setBase(exitBase).build())
            }
        }
    }

    fun collectAddEdgesTo(dst: MutableList<ZeroToFactEdgeBuilder>) {
        forEachValue { base, storage ->
            storage.collectTo(dst, base)
        }
    }

    fun filterTo(dst: MutableList<ZeroToFactEdgeBuilder>, finalFactBase: AccessPathBase) {
        val storage = find(finalFactBase) ?: return
        storage.collectTo(dst, finalFactBase)
    }

    private fun MethodZeroToFactSummaryEdgeStorage.collectTo(
        dst: MutableList<ZeroToFactEdgeBuilder>, base: AccessPathBase
    ) {
        val edge = summaryEdge() ?: return
        dst.add(edge.setBase(base).build())
    }
}

private class MethodZeroToFactSummaryEdgeStorage {
    private var summaryEdgeAccess: AccessTree.AccessNode? = null

    fun add(edgeAccess: AccessTree.AccessNode): ZeroEdgeBuilderBuilder? {
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

    fun summaryEdge(): ZeroEdgeBuilderBuilder? = summaryEdgeAccess?.let {
        ZeroEdgeBuilderBuilder().setNode(it)
    }
}

private data class ZeroEdgeBuilderBuilder(
    private var base: AccessPathBase? = null,
    private var node: AccessTree.AccessNode? = null,
) {
    fun build(): ZeroToFactEdgeBuilder = ZeroToFactEdgeBuilder()
        .setExitAp(AccessTree(base!!, node!!, ExclusionSet.Universe))

    fun setBase(base: AccessPathBase) = this.also {
        this.base = base
    }

    fun setNode(node: AccessTree.AccessNode) = this.also {
        this.node = node
    }
}
