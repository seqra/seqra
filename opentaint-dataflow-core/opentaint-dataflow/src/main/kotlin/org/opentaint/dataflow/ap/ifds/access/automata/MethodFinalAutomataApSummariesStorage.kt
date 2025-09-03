package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Edge
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.MethodSummaryZeroEdgesForExitPoint
import org.opentaint.dataflow.ap.ifds.SummaryFactStorage
import org.opentaint.dataflow.ap.ifds.ZeroToFactEdgeBuilder
import org.opentaint.dataflow.ap.ifds.access.MethodFinalApSummariesStorage

class MethodFinalAutomataApSummariesStorage(methodEntryPoint: CommonInst) : MethodFinalApSummariesStorage {
    private val storage = ExitPointStorage(methodEntryPoint)

    override fun toString(): String = storage.toString()

    override fun add(edges: List<Edge.ZeroToFact>, addedEdges: MutableList<ZeroToFactEdgeBuilder>) {
        storage.add(edges, addedEdges)
    }

    override fun allEdges(): Sequence<ZeroToFactEdgeBuilder> = storage.allEdges()

    override fun filterEdges(finalFactBase: AccessPathBase): Sequence<ZeroToFactEdgeBuilder> =
        storage.filterEdges(finalFactBase)

    private class ExitPointStorage(methodEntryPoint: CommonInst) :
        MethodSummaryZeroEdgesForExitPoint<ApStorage, AccessPathBase>(methodEntryPoint) {
        override fun createStorage(): ApStorage = ApStorage(methodEntryPoint)

        override fun storageFilterEdges(
            storage: ApStorage,
            containsPattern: AccessPathBase
        ): Sequence<ZeroToFactEdgeBuilder> =
            storage.filter(containsPattern)

        override fun storageAllEdges(storage: ApStorage): Sequence<ZeroToFactEdgeBuilder> = storage.allEdges()

        override fun storageAdd(
            storage: ApStorage,
            edges: List<Edge.ZeroToFact>,
            added: MutableList<ZeroToFactEdgeBuilder>
        ) {
            storage.add(edges, added)
        }
    }

    private class ApStorage(methodEntryPoint: CommonInst) :
        SummaryFactStorage<AccessGraphStorageWithCompression>(methodEntryPoint) {
        override fun createStorage(): AccessGraphStorageWithCompression = AccessGraphStorageWithCompression()

        fun add(edges: List<Edge.ZeroToFact>, addedEdges: MutableList<ZeroToFactEdgeBuilder>) {
            val basedEdges = edges.groupBy({ it.factAp.base }, { (it.factAp as AccessGraphFinalFactAp).access })

            for ((base, sameBaseAccess) in basedEdges) {
                val baseStorage = getOrCreate(base)
                sameBaseAccess.forEach { baseStorage.add(it) }
                baseStorage.mapAndResetDelta { ag ->
                    addedEdges += ZeroToFactEdgeBuilderBuilder()
                        .setGraph(ag)
                        .setBase(base)
                        .build()
                }
            }
        }

        fun allEdges(): Sequence<ZeroToFactEdgeBuilder> = mapValues { base, storage ->
            storage.allGraphs().map { ag ->
                ZeroToFactEdgeBuilderBuilder()
                    .setGraph(ag)
                    .setBase(base)
                    .build()
            }
        }.flatten()

        fun filter(finalFactBase: AccessPathBase): Sequence<ZeroToFactEdgeBuilder> {
            val storage = find(finalFactBase) ?: return emptySequence()
            return storage.allGraphs().map { ag ->
                ZeroToFactEdgeBuilderBuilder()
                    .setGraph(ag)
                    .setBase(finalFactBase)
                    .build()
            }
        }
    }

    private class ZeroToFactEdgeBuilderBuilder(
        private var graph: AccessGraph? = null,
        private var base: AccessPathBase? = null,
    ) {
        fun build(): ZeroToFactEdgeBuilder = ZeroToFactEdgeBuilder()
            .setExitAp(AccessGraphFinalFactAp(base!!, graph!!, ExclusionSet.Universe))

        fun setGraph(graph: AccessGraph) = also { this.graph = graph }
        fun setBase(base: AccessPathBase) = also { this.base = base }
    }
}
