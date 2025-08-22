package org.opentaint.dataflow.jvm.ap.ifds.access.cactus

import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.jvm.ap.ifds.*
import org.opentaint.dataflow.jvm.ap.ifds.access.MethodFinalApSummariesStorage

class MethodFinalTreeApSummariesStorage(
    methodInitialStatement: JIRInst
) : MethodFinalApSummariesStorage {
    private val storage = MethodZeroToFactSummariesStorage(methodInitialStatement)
    override fun add(edges: List<Edge.ZeroToFact>, addedEdges: MutableList<ZeroToFactEdgeBuilder>) {
        storage.add(edges, addedEdges)
    }

    override fun allEdges(): Sequence<ZeroToFactEdgeBuilder> = storage.allEdges()
}

private class MethodZeroToFactSummariesStorage(methodEntryPoint: JIRInst) :
    MethodSummaryEdgesForExitPoint<Edge.ZeroToFact, ZeroToFactEdgeBuilder, MethodZeroToFactSummaries, AccessCactus.AccessNode>(
        methodEntryPoint
    ) {

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
        containsPattern: AccessCactus.AccessNode
    ): Sequence<ZeroToFactEdgeBuilder> {
        error("Can't filter edges")
    }
}

private class MethodZeroToFactSummaries(methodEntryPoint: JIRInst) :
    SummaryFactStorage<MethodZeroToFactSummaryEdgeStorage>(methodEntryPoint) {
    override fun createStorage() = MethodZeroToFactSummaryEdgeStorage()

    fun add(edges: List<Edge.ZeroToFact>, added: MutableList<ZeroToFactEdgeBuilder>) {
        val sameExitBaseEdges = edges.groupBy { it.factAp.base }
        for ((exitBase, sameBaseEdges) in sameExitBaseEdges) {
            val storage = getOrCreate(exitBase)

            val addedEdge = sameBaseEdges.fold(null as ZeroEdgeBuilderBuilder?) { addedEdge, edge ->
                storage.add((edge.factAp as AccessCactus).access) ?: addedEdge
            }

            if (addedEdge != null) {
                added.add(addedEdge.setBase(exitBase).build())
            }
        }
    }

    fun edgeSequence(): Sequence<ZeroToFactEdgeBuilder> = mapValues { base, storage ->
        storage.summaryEdge()?.setBase(base)?.build()?.let { sequenceOf(it) }.orEmpty()
    }.flatten()
}

private class MethodZeroToFactSummaryEdgeStorage {
    private var summaryEdgeAccess: AccessCactus.AccessNode? = null

    fun add(edgeAccess: AccessCactus.AccessNode): ZeroEdgeBuilderBuilder? {
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
    private var node: AccessCactus.AccessNode? = null,
) {
    fun build(): ZeroToFactEdgeBuilder = ZeroToFactEdgeBuilder()
        .setExitAp(AccessCactus(base!!, node!!, ExclusionSet.Universe))

    fun setBase(base: AccessPathBase) = this.also {
        this.base = base
    }

    fun setNode(node: AccessCactus.AccessNode) = this.also {
        this.node = node
    }
}
