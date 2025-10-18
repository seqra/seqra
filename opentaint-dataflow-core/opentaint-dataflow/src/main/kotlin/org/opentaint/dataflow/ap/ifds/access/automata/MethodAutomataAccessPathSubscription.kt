package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.SummaryEdgeSubscriptionManager.FactEdgeSummarySubscription
import org.opentaint.dataflow.ap.ifds.SummaryEdgeSubscriptionManager.ZeroEdgeSummarySubscription
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.MethodAccessPathSubscription

class MethodAutomataAccessPathSubscription : MethodAccessPathSubscription {
    private val zeroFacts = hashMapOf<AccessPathBase, MutableSet<FinalFactAp>>()
    private val factToFact = hashMapOf<AccessPathBase, MutableSet<Pair<InitialFactAp, FinalFactAp>>>()

    override fun addZeroToFact(
        calleeInitialFactBase: AccessPathBase,
        callerFactAp: FinalFactAp
    ): ZeroEdgeSummarySubscription? {
        val basedFacts = zeroFacts.getOrPut(calleeInitialFactBase) { hashSetOf() }
        if (!basedFacts.add(callerFactAp)) return null

        return ZeroEdgeSummarySubscription()
            .setCalleeBase(calleeInitialFactBase)
            .setCallerPathEdgeAp(callerFactAp)
    }

    override fun addFactToFact(
        calleeInitialBase: AccessPathBase,
        callerInitialAp: InitialFactAp,
        callerExitAp: FinalFactAp
    ): FactEdgeSummarySubscription? {
        val basedFacts = factToFact.getOrPut(calleeInitialBase) { hashSetOf() }
        if (!basedFacts.add(callerInitialAp to callerExitAp)) return null

        return FactEdgeSummarySubscription()
            .setCalleeBase(calleeInitialBase)
            .setCallerInitialAp(callerInitialAp)
            .setCallerAp(callerExitAp)
    }

    override fun collectFactEdge(
        collection: MutableList<FactEdgeSummarySubscription>,
        summaryInitialFactAp: InitialFactAp
    ) {
        val edges = factToFact[summaryInitialFactAp.base] ?: return
        edges.mapTo(collection) { (callerInitialAp, callerExitAp) ->
            FactEdgeSummarySubscription()
                .setCalleeBase(summaryInitialFactAp.base)
                .setCallerInitialAp(callerInitialAp)
                .setCallerAp(callerExitAp)
        }
    }

    override fun collectZeroEdge(
        collection: MutableList<ZeroEdgeSummarySubscription>,
        summaryInitialFactAp: InitialFactAp
    ) {
        val edges = zeroFacts[summaryInitialFactAp.base] ?: return
        edges.mapTo(collection) {
            ZeroEdgeSummarySubscription()
                .setCalleeBase(summaryInitialFactAp.base)
                .setCallerPathEdgeAp(it)
        }
    }
}
