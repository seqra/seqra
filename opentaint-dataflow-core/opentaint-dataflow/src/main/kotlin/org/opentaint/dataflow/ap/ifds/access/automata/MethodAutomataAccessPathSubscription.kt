package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.common.CommonAPSub
import org.opentaint.dataflow.ap.ifds.access.common.CommonFactEdgeSubBuilder
import org.opentaint.dataflow.ap.ifds.access.common.CommonFactNDEdgeSubBuilder
import org.opentaint.dataflow.ap.ifds.access.common.CommonZeroEdgeSubBuilder
import org.opentaint.dataflow.ap.ifds.access.common.ndf2f.DefaultNDF2FSubStorageWithAp
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.getOrCreateIndex
import org.opentaint.dataflow.util.object2IntMap
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.BitSet

class MethodAutomataAccessPathSubscription : CommonAPSub<AccessGraph, AccessGraph>(),
    AutomataInitialApAccess, AutomataFinalApAccess {

    override fun createZ2FSubStorage(callerEp: CommonInst): Z2FSubStorage<AccessGraph, AccessGraph> = Z2FFactGraphs()

    override fun createF2FSubStorage(callerEp: CommonInst): F2FSubStorage<AccessGraph, AccessGraph> = F2FFactGraphs()

    override fun createNDF2FSubStorage(callerEp: CommonInst): NDF2FSubStorage<AccessGraph, AccessGraph> = NdF2f(callerEp)

    private class Z2FFactGraphs : Z2FSubStorage<AccessGraph, AccessGraph> {
        private val facts = hashSetOf<AccessGraph>()

        override fun add(callerExitAp: AccessGraph): CommonZeroEdgeSubBuilder<AccessGraph>? {
            if (!facts.add(callerExitAp)) return null
            return ZeroEdgeSubBuilder().setNode(callerExitAp)
        }

        override fun find(
            dst: MutableList<CommonZeroEdgeSubBuilder<AccessGraph>>,
            summaryInitialFact: AccessGraph,
        ) {
            facts.mapNotNullTo(dst) {
                val delta = it.delta(summaryInitialFact)
                if (delta.isEmpty()) return@mapNotNullTo null

                ZeroEdgeSubBuilder().setNode(it)
            }
        }
    }

    private class F2FFactGraphs : F2FSubStorage<AccessGraph, AccessGraph> {
        private val edgeIndex = object2IntMap<Pair<AccessGraphInitialFactAp, AccessGraph>>()
        private val edges = arrayListOf<Pair<AccessGraphInitialFactAp, AccessGraph>>()

        private val graphIndex = GraphIndex()

        override fun add(
            callerInitialAp: InitialFactAp,
            callerExitAp: AccessGraph,
        ): CommonFactEdgeSubBuilder<AccessGraph>? {
            callerInitialAp as AccessGraphInitialFactAp

            val entry = Pair(callerInitialAp, callerExitAp)
            edgeIndex.getOrCreateIndex(entry) { newIndex ->
                edges.add(entry)

                updateGraphIndex(entry.second, newIndex)

                return FactEdgeSubBuilder()
                    .setCallerNode(callerExitAp)
                    .setCallerInitialAp(callerInitialAp)
                    .setCallerExclusion(callerInitialAp.exclusions)
            }

            return null
        }

        private fun updateGraphIndex(graph: AccessGraph, idx: Int) {
            graphIndex.add(graph, idx)
        }

        override fun find(
            dst: MutableList<CommonFactEdgeSubBuilder<AccessGraph>>,
            summaryInitialFact: AccessGraph,
            emptyDeltaRequired: Boolean,
        ) {
            if (!emptyDeltaRequired) {
                graphIndex.localizeIndexedGraphHasDeltaWithGraph(summaryInitialFact).forEach { edgeIdx ->
                    val (initialAp, final) = edges[edgeIdx]

                    val delta = final.delta(summaryInitialFact)
                    if (delta.isEmpty()) return@forEach

                    dst += FactEdgeSubBuilder()
                        .setCallerInitialAp(initialAp)
                        .setCallerNode(final)
                        .setCallerExclusion(initialAp.exclusions)
                }
            } else {
                collectEmptyDelta(dst, summaryInitialFact)
            }
        }

        private fun collectEmptyDelta(
            collection: MutableList<CommonFactEdgeSubBuilder<AccessGraph>>,
            summaryInitialFactAp: AccessGraph,
        ) {
            graphIndex.localizeIndexedGraphContainsAllGraph(summaryInitialFactAp).forEach { edgeIdx ->
                val (initialAp, final) = edges[edgeIdx]

                if (!final.containsAll(summaryInitialFactAp)) {
                    return@forEach
                }

                collection += FactEdgeSubBuilder()
                    .setCallerInitialAp(initialAp)
                    .setCallerNode(final)
                    .setCallerExclusion(initialAp.exclusions)
            }
        }
    }

    private class NdF2f(callerEp: CommonInst) :
        DefaultNDF2FSubStorageWithAp<AccessGraph, AccessGraph>(callerEp), AutomataInitialApAccess {
        private val graphIndex = GraphIndex()

        override fun createBuilder(): CommonFactNDEdgeSubBuilder<AccessGraph> = FactNDEdgeSubBuilder()

        private inner class FactStorage(
            private val storageIdx: Int,
        ) : Storage<AccessGraph, AccessGraph> {
            private val graphs = object2IntMap<AccessGraph>()
            private val graphList = arrayListOf<AccessGraph>()

            override fun add(element: AccessGraph): AccessGraph? {
                graphs.getOrCreateIndex(element) {
                    graphList.add(element)
                    graphIndex.add(element, storageIdx)
                    return element
                }

                return null
            }

            override fun collect(dst: MutableList<AccessGraph>) {
                dst.addAll(graphList)
            }

            override fun collect(dst: MutableList<AccessGraph>, summaryInitialFact: AccessGraph) {
                for (graph in graphList) {
                    if (graph.containsAll(summaryInitialFact)) {
                        dst.add(graph)
                    }
                }
            }
        }

        override fun createStorage(idx: Int): Storage<AccessGraph, AccessGraph> = FactStorage(idx)

        override fun relevantStorageIndices(summaryInitialFact: AccessGraph): BitSet =
            graphIndex.localizeIndexedGraphContainsAllGraph(summaryInitialFact)
    }
}

private class ZeroEdgeSubBuilder : CommonZeroEdgeSubBuilder<AccessGraph>(), AutomataFinalApAccess
private class FactEdgeSubBuilder : CommonFactEdgeSubBuilder<AccessGraph>(), AutomataFinalApAccess
private class FactNDEdgeSubBuilder : CommonFactNDEdgeSubBuilder<AccessGraph>(), AutomataFinalApAccess
