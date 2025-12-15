package org.opentaint.dataflow.ap.ifds.access.common

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Edge
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.MethodSummaryZeroEdgesForExitPoint
import org.opentaint.dataflow.ap.ifds.SummaryFactStorage
import org.opentaint.dataflow.ap.ifds.ZeroToFactEdgeBuilder
import org.opentaint.dataflow.ap.ifds.access.MethodFinalApSummariesStorage
import org.opentaint.dataflow.util.collectToListWithPostProcess
import org.opentaint.ir.api.common.cfg.CommonInst

abstract class CommonZ2FSummary<FAP : Any>(
    private val methodEntryPoint: CommonInst,
) : MethodFinalApSummariesStorage, FinalApAccess<FAP> {

    interface Storage<FAP : Any> {
        fun add(edges: List<FAP>, added: MutableList<Z2FBBuilder<FAP>>)
        fun collectEdges(dst: MutableList<Z2FBBuilder<FAP>>)
    }

    abstract fun createStorage(): Storage<FAP>

    private val storage = MethodZeroToFactSummariesStorage()
    override fun add(edges: List<Edge.ZeroToFact>, addedEdges: MutableList<ZeroToFactEdgeBuilder>) {
        storage.add(edges, addedEdges)
    }

    override fun filterEdgesTo(dst: MutableList<ZeroToFactEdgeBuilder>, finalFactBase: AccessPathBase?) {
        storage.filterEdgesTo(dst, finalFactBase)
    }

    private inner class MethodZeroToFactSummariesStorage :
        MethodSummaryZeroEdgesForExitPoint<MethodZeroToFactSummaries, AccessPathBase?>(methodEntryPoint) {

        override fun createStorage(): MethodZeroToFactSummaries = MethodZeroToFactSummaries()

        override fun storageAdd(
            storage: MethodZeroToFactSummaries,
            edges: List<Edge.ZeroToFact>,
            added: MutableList<ZeroToFactEdgeBuilder>,
        ) = storage.add(edges, added)

        override fun storageFilterEdgesTo(
            dst: MutableList<ZeroToFactEdgeBuilder>,
            storage: MethodZeroToFactSummaries,
            containsPattern: AccessPathBase?,
        ) {
            storage.filterTo(dst, containsPattern)
        }
    }

    private inner class MethodZeroToFactSummaries : SummaryFactStorage<Storage<FAP>>(methodEntryPoint) {
        override fun createStorage() = this@CommonZ2FSummary.createStorage()

        fun add(edges: List<Edge.ZeroToFact>, added: MutableList<ZeroToFactEdgeBuilder>) {
            val sameExitBaseEdges = edges.groupBy { it.factAp.base }
            for ((exitBase, sameBaseEdges) in sameExitBaseEdges) {
                val storage = getOrCreate(exitBase)

                val ap = sameBaseEdges.map { getFinalAccess(it.factAp) }
                collectToListWithPostProcess(
                    added,
                    { storage.add(ap, it) },
                    { it.setBase(exitBase).build() }
                )
            }
        }

        fun filterTo(dst: MutableList<ZeroToFactEdgeBuilder>, finalFactBase: AccessPathBase?) {
            if (finalFactBase != null) {
                val storage = find(finalFactBase) ?: return
                storage.collectTo(dst, finalFactBase)
            } else {
                forEachValue { base, storage ->
                    storage.collectTo(dst, base)
                }
            }
        }

        private fun Storage<FAP>.collectTo(
            dst: MutableList<ZeroToFactEdgeBuilder>, base: AccessPathBase,
        ) {
            collectToListWithPostProcess(dst, { collectEdges(it) }, { it.setBase(base).build() })
        }
    }

    abstract class Z2FBBuilder<FAP : Any>(
        private var base: AccessPathBase? = null,
        private var node: FAP? = null,
    ) : FinalApAccess<FAP> {
        fun build(): ZeroToFactEdgeBuilder = ZeroToFactEdgeBuilder()
            .setExitAp(createFinal(base!!, node!!, ExclusionSet.Universe))

        fun setBase(base: AccessPathBase) = this.also { this.base = base }
        fun setNode(node: FAP) = this.also { this.node = node }
    }
}
