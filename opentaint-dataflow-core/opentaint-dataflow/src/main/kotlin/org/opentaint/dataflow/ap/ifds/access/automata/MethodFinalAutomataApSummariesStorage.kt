package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Edge
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.MethodSummaryZeroEdgesForExitPoint
import org.opentaint.dataflow.ap.ifds.SummaryFactStorage
import org.opentaint.dataflow.ap.ifds.ZeroToFactEdgeBuilder
import org.opentaint.dataflow.ap.ifds.access.MethodFinalApSummariesStorage
import org.opentaint.dataflow.util.collectToListWithPostProcess

class MethodFinalAutomataApSummariesStorage(methodEntryPoint: CommonInst) : MethodFinalApSummariesStorage {
    private val storage = ExitPointStorage(methodEntryPoint)

    override fun toString(): String = storage.toString()

    override fun add(edges: List<Edge.ZeroToFact>, addedEdges: MutableList<ZeroToFactEdgeBuilder>) {
        storage.add(edges, addedEdges)
    }

    override fun collectAllEdgesTo(dst: MutableList<ZeroToFactEdgeBuilder>) {
        storage.collectAllEdgesTo(dst)
    }

    override fun filterEdgesTo(dst: MutableList<ZeroToFactEdgeBuilder>, finalFactBase: AccessPathBase) {
        storage.filterEdgesTo(dst, finalFactBase)
    }

    private class ExitPointStorage(methodEntryPoint: CommonInst) :
        MethodSummaryZeroEdgesForExitPoint<ApStorage, AccessPathBase>(methodEntryPoint) {
        override fun createStorage(): ApStorage = ApStorage(methodEntryPoint)

        override fun storageFilterEdgesTo(
            dst: MutableList<ZeroToFactEdgeBuilder>,
            storage: ApStorage,
            containsPattern: AccessPathBase
        ) {
            storage.filterTo(dst, containsPattern)
        }

        override fun storageCollectAllEdgesTo(dst: MutableList<ZeroToFactEdgeBuilder>, storage: ApStorage) {
            storage.collectAllEdgesTo(dst)
        }

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

        fun filterTo(dst: MutableList<ZeroToFactEdgeBuilder>, finalFactBase: AccessPathBase) {
            val storage = find(finalFactBase) ?: return
            collectToListWithPostProcess(dst, {
                storage.allGraphsTo(it)
            }, {
                ZeroToFactEdgeBuilderBuilder()
                    .setGraph(it)
                    .setBase(finalFactBase)
                    .build()
            })
        }

        fun collectAllEdgesTo(dst: MutableList<ZeroToFactEdgeBuilder>) {
            forEachValue { base, storage ->
                collectToListWithPostProcess(dst, {
                    storage.allGraphsTo(it)
                }, {
                    ZeroToFactEdgeBuilderBuilder()
                        .setGraph(it)
                        .setBase(base)
                        .build()
                })
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
