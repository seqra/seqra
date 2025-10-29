package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.SummaryEdgeSubscriptionManager.FactEdgeSummarySubscription
import org.opentaint.dataflow.ap.ifds.SummaryEdgeSubscriptionManager.ZeroEdgeSummarySubscription
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.MethodAccessPathSubscription
import org.opentaint.dataflow.util.collectToListWithPostProcess
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.getOrCreateIndex
import org.opentaint.dataflow.util.object2IntMap

class MethodAutomataAccessPathSubscription : MethodAccessPathSubscription {
    private val zeroFacts = hashMapOf<AccessPathBase, ZeroFactSubscriptionStorage>()
    private val factToFact = hashMapOf<AccessPathBase, FactToFactSubscriptionStorage>()

    override fun addZeroToFact(
        calleeInitialFactBase: AccessPathBase,
        callerFactAp: FinalFactAp
    ): ZeroEdgeSummarySubscription? {
        val basedFacts = zeroFacts.getOrPut(calleeInitialFactBase, ::ZeroFactSubscriptionStorage)
        return basedFacts.add(callerFactAp as AccessGraphFinalFactAp)
            ?.setCalleeBase(calleeInitialFactBase)
    }

    override fun addFactToFact(
        calleeInitialBase: AccessPathBase,
        callerInitialAp: InitialFactAp,
        callerExitAp: FinalFactAp
    ): FactEdgeSummarySubscription? {
        val basedFacts = factToFact.getOrPut(calleeInitialBase, ::FactToFactSubscriptionStorage)
        return basedFacts
            .add(callerInitialAp as AccessGraphInitialFactAp, callerExitAp as AccessGraphFinalFactAp)
            ?.setCalleeBase(calleeInitialBase)
    }

    override fun collectFactEdge(
        collection: MutableList<FactEdgeSummarySubscription>,
        summaryInitialFactAp: InitialFactAp,
        emptyDeltaRequired: Boolean
    ) {
        val edges = factToFact[summaryInitialFactAp.base] ?: return
        collectToListWithPostProcess(
            collection,
            { edges.collect(it, (summaryInitialFactAp as AccessGraphInitialFactAp).access, emptyDeltaRequired) },
            { it.setCalleeBase(summaryInitialFactAp.base) }
        )
    }

    override fun collectZeroEdge(
        collection: MutableList<ZeroEdgeSummarySubscription>,
        summaryInitialFactAp: InitialFactAp
    ) {
        val edges = zeroFacts[summaryInitialFactAp.base] ?: return
        collectToListWithPostProcess(
            collection,
            { edges.collect(it, (summaryInitialFactAp as AccessGraphInitialFactAp).access) },
            { it.setCalleeBase(summaryInitialFactAp.base) }
        )
    }

    private class ZeroFactSubscriptionStorage {
        private val edgeFacts = hashMapOf<AccessPathBase, FactGraphs>()

        fun add(fact: AccessGraphFinalFactAp): ZeroEdgeSummarySubscription? {
            check(fact.exclusions is ExclusionSet.Universe)

            val factGraphs = edgeFacts.getOrPut(fact.base, ::FactGraphs)

            return factGraphs.add(fact.access)?.let {
                val ap = AccessGraphFinalFactAp(fact.base, it, ExclusionSet.Universe)
                ZeroEdgeSummarySubscription()
                    .setCallerPathEdgeAp(ap)
            }
        }

        fun collect(
            collection: MutableList<ZeroEdgeSummarySubscription>,
            summaryInitialFactAp: AccessGraph
        ) {
            for ((base, graphs) in edgeFacts) {
                collectToListWithPostProcess(
                    collection,
                    { graphs.collect(it, summaryInitialFactAp) },
                    {
                        val ap = AccessGraphFinalFactAp(base, it, ExclusionSet.Universe)
                        ZeroEdgeSummarySubscription()
                            .setCallerPathEdgeAp(ap)
                    }
                )
            }
        }

        private class FactGraphs {
            private val facts = hashSetOf<AccessGraph>()

            fun add(fact: AccessGraph): AccessGraph? {
                if (!facts.add(fact)) return null
                return fact
            }

            fun collect(
                collection: MutableList<AccessGraph>,
                summaryInitialFactAp: AccessGraph
            ) {
                facts.mapNotNullTo(collection) {
                    val delta = it.delta(summaryInitialFactAp)
                    if (delta.isEmpty()) return@mapNotNullTo null

                    it
                }
            }
        }
    }

    private class FactToFactSubscriptionStorage {
        private val finalBaseEdges = hashMapOf<AccessPathBase, FactGraphs>()

        fun add(initial: AccessGraphInitialFactAp, final: AccessGraphFinalFactAp): FactEdgeSummarySubscription? {
            check(final.exclusions == initial.exclusions) { "Edge invariant" }

            val factGraphs = finalBaseEdges.getOrPut(final.base, ::FactGraphs)

            return factGraphs.add(initial, final.access)?.let { (initialAp, finalAccess) ->
                val ap = AccessGraphFinalFactAp(final.base, finalAccess, initialAp.exclusions)
                FactEdgeSummarySubscription()
                    .setCallerAp(ap)
                    .setCallerInitialAp(initialAp)
            }
        }

        fun collect(
            collection: MutableList<FactEdgeSummarySubscription>,
            summaryInitialFactAp: AccessGraph,
            emptyDeltaRequired: Boolean
        ) {
            for ((finalBase, graphs) in finalBaseEdges) {
                collectToListWithPostProcess(
                    collection,
                    { graphs.collect(it, summaryInitialFactAp, emptyDeltaRequired) },
                    { (initialAp, finalAccess) ->
                        val ap = AccessGraphFinalFactAp(finalBase, finalAccess, initialAp.exclusions)
                        FactEdgeSummarySubscription()
                            .setCallerAp(ap)
                            .setCallerInitialAp(initialAp)
                    }
                )
            }
        }
        
        private class FactGraphs {
            private val edgeIndex = object2IntMap<Pair<AccessGraphInitialFactAp, AccessGraph>>()
            private val edges = arrayListOf<Pair<AccessGraphInitialFactAp, AccessGraph>>()

            private val graphIndex = GraphIndex()

            fun add(
                initial: AccessGraphInitialFactAp,
                final: AccessGraph
            ): Pair<AccessGraphInitialFactAp, AccessGraph>? {
                val entry = Pair(initial, final)
                edgeIndex.getOrCreateIndex(entry) { newIndex ->
                    edges.add(entry)

                    updateGraphIndex(entry.second, newIndex)

                    return entry
                }

                return null
            }

            private fun updateGraphIndex(graph: AccessGraph, idx: Int) {
                graphIndex.add(graph, idx)
            }

            fun collect(
                collection: MutableList<Pair<AccessGraphInitialFactAp, AccessGraph>>,
                summaryInitialFactAp: AccessGraph,
                emptyDeltaRequired: Boolean,
            ) {
                if (!emptyDeltaRequired) {
                    graphIndex.localizeIndexedGraphHasDeltaWithGraph(summaryInitialFactAp).forEach { edgeIdx ->
                        val (initialAp, final) = edges[edgeIdx]

                        val delta = final.delta(summaryInitialFactAp)
                        if (delta.isEmpty()) return@forEach

                        collection.add(initialAp to final)
                    }
                } else {
                    collectEmptyDelta(collection, summaryInitialFactAp)
                }
            }

            private fun collectEmptyDelta(
                collection: MutableList<Pair<AccessGraphInitialFactAp, AccessGraph>>,
                summaryInitialFactAp: AccessGraph,
            ) {
                graphIndex.localizeIndexedGraphContainsAllGraph(summaryInitialFactAp).forEach { edgeIdx ->
                    val (initialAp, final) = edges[edgeIdx]

                    if (!final.containsAll(summaryInitialFactAp)) {
                        return@forEach
                    }

                    collection.add(initialAp to final)
                }
            }
        }
    }
}
