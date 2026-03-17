package org.opentaint.dataflow.ap.ifds.access.common

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Edge.NDFactToFact
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.MethodSummaryEdgesForExitPoint
import org.opentaint.dataflow.ap.ifds.NDFactToFactEdgeBuilder
import org.opentaint.dataflow.ap.ifds.SummaryFactStorage
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.MethodNDInitialToFinalApSummariesStorage
import org.opentaint.dataflow.util.collectToListWithPostProcess
import org.opentaint.ir.api.common.cfg.CommonInst

abstract class CommonNDF2FSummary<FAP : Any>(
    val methodEntryPoint: CommonInst,
) : MethodNDInitialToFinalApSummariesStorage, FinalApAccess<FAP> {
    private val storage = MethodNDStatementSummariesStorage()

    data class SimpleNdEdge<FAP>(val initial: Set<InitialFactAp>, val final: FAP)

    interface Storage<FAP : Any> {
        fun add(edges: List<SimpleNdEdge<FAP>>, added: MutableList<NDF2FBBuilder<FAP>>)
        fun filterTo(dst: MutableList<NDF2FBBuilder<FAP>>, initialFactPattern: FinalFactAp?)
    }

    abstract fun createStorage(): Storage<FAP>

    override fun add(edges: List<NDFactToFact>, added: MutableList<NDFactToFactEdgeBuilder>) {
        storage.add(edges, added)
    }

    override fun filterEdgesTo(
        dst: MutableList<NDFactToFactEdgeBuilder>,
        initialFactPattern: FinalFactAp?,
        finalFactBase: AccessPathBase?,
    ) {
        storage.filterEdgesTo(dst, NDEdgeStoragePattern(initialFactPattern, finalFactBase))
    }

    private class NDEdgeStoragePattern(
        val initialFactPattern: FinalFactAp?,
        val finalFactBase: AccessPathBase?,
    )

    private inner class MethodNDStatementSummariesStorage :
        MethodSummaryEdgesForExitPoint<NDFactToFact, NDFactToFactEdgeBuilder, MethodNDSummariesStorage, NDEdgeStoragePattern>(
            methodEntryPoint
        ) {

        override fun createStorage(): MethodNDSummariesStorage = MethodNDSummariesStorage()

        override fun storageAdd(
            storage: MethodNDSummariesStorage,
            edges: List<NDFactToFact>,
            added: MutableList<NDFactToFactEdgeBuilder>,
        ) = storage.add(edges, added)

        override fun storageFilterEdgesTo(
            dst: MutableList<NDFactToFactEdgeBuilder>,
            storage: MethodNDSummariesStorage,
            containsPattern: NDEdgeStoragePattern,
        ) {
            storage.filterTo(dst, containsPattern)
        }
    }

    private inner class MethodNDSummariesStorage {
        private val storage = MethodNDSummariesGroupedByFinalFactStorage()

        fun add(edges: List<NDFactToFact>, added: MutableList<NDFactToFactEdgeBuilder>) {
            val groupedEdges = edges.groupBy(
                { it.factAp.base },
                { SimpleNdEdge(it.initialFacts, getFinalAccess(it.factAp)) }
            )
            for ((finalFactBase, edgeGroup) in groupedEdges) {
                val edgeStorage = storage.getOrCreate(finalFactBase)

                collectToListWithPostProcess(added, {
                    edgeStorage.add(edgeGroup, it)
                }, {
                    it.setExitBase(finalFactBase).build()
                })
            }
        }

        fun filterTo(dst: MutableList<NDFactToFactEdgeBuilder>, pattern: NDEdgeStoragePattern) {
            val finalBase = pattern.finalFactBase
            if (finalBase != null) {
                val edgeStorage = storage.find(finalBase) ?: return
                collectToListWithPostProcess(
                    dst,
                    { edgeStorage.filterTo(it, pattern.initialFactPattern) },
                    { it.setExitBase(finalBase).build() }
                )
            } else {
                storage.forEachValue { base, edgeStorage ->
                    collectToListWithPostProcess(
                        dst,
                        { edgeStorage.filterTo(it, pattern.initialFactPattern) },
                        { it.setExitBase(base).build() }
                    )
                }
            }
        }
    }

    private inner class MethodNDSummariesGroupedByFinalFactStorage :
        SummaryFactStorage<Storage<FAP>>(methodEntryPoint) {
        override fun createStorage(): Storage<FAP> = this@CommonNDF2FSummary.createStorage()
    }

    abstract class NDF2FBBuilder<FAP : Any>(
        private var initial: Set<InitialFactAp>? = null,
        private var exitAp: FAP? = null,
        private var exitBase: AccessPathBase? = null,
    ) : FinalApAccess<FAP> {
        fun build() = NDFactToFactEdgeBuilder()
            .setInitial(initial!!)
            .setExitAp(createFinal(exitBase!!, exitAp!!, ExclusionSet.Universe))

        fun setInitial(initial: Set<InitialFactAp>) = also { this.initial = initial }
        fun setExitAp(exitAp: FAP) = also { this.exitAp = exitAp }
        fun setExitBase(exitBase: AccessPathBase) = also { this.exitBase = exitBase }
    }
}
