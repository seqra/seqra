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

    override fun allEdges(): Sequence<ZeroToFactEdgeBuilder> = storage.allEdges()

    override fun filterEdges(finalFactBase: AccessPathBase): Sequence<ZeroToFactEdgeBuilder> =
        storage.filterEdges(finalFactBase)
}

private class MethodZeroToFactSummariesStorage(methodEntryPoint: CommonInst) :
    MethodSummaryZeroEdgesForExitPoint<MethodZeroToFactSummaries, AccessPathBase>(methodEntryPoint) {

    override fun createStorage(): MethodZeroToFactSummaries = MethodZeroToFactSummaries(methodEntryPoint)

    override fun storageAdd(
        storage: MethodZeroToFactSummaries,
        edges: List<Edge.ZeroToFact>,
        added: MutableList<ZeroToFactEdgeBuilder>
    ) = storage.add(edges, added)

    override fun storageAllEdges(storage: MethodZeroToFactSummaries): Sequence<ZeroToFactEdgeBuilder> =
        storage.edgeSequence()

    override fun storageFilterEdges(
        storage: MethodZeroToFactSummaries,
        containsPattern: AccessPathBase
    ): Sequence<ZeroToFactEdgeBuilder> =
        storage.filter(containsPattern)
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

    fun edgeSequence(): Sequence<ZeroToFactEdgeBuilder> = mapValues { base, storage ->
        storage.summaryEdgeSequence(base)
    }.flatten()

    fun filter(finalFactBase: AccessPathBase): Sequence<ZeroToFactEdgeBuilder> {
        val storage = find(finalFactBase) ?: return emptySequence()
        return storage.summaryEdgeSequence(finalFactBase)
    }

    private fun MethodZeroToFactSummaryEdgeStorage.summaryEdgeSequence(base: AccessPathBase) =
        summaryEdge()?.setBase(base)?.build()?.let { sequenceOf(it) }.orEmpty()
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
