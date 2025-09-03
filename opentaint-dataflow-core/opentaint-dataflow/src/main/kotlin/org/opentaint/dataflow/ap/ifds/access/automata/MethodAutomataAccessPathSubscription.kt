package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.SummaryEdgeSubscriptionManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.MethodAccessPathSubscription

class MethodAutomataAccessPathSubscription : MethodAccessPathSubscription {
    private val zeroFacts = hashMapOf<AccessPathBase, MutableSet<FinalFactAp>>()
    private val factToFact = hashMapOf<AccessPathBase, MutableSet<Pair<InitialFactAp, FinalFactAp>>>()

    override fun addZeroToFact(
        calleeInitialFactBase: AccessPathBase,
        callerFactAp: FinalFactAp
    ): SummaryEdgeSubscriptionManager.ZeroEdgeSummarySubscription? {
        val basedFacts = zeroFacts.getOrPut(calleeInitialFactBase) { hashSetOf() }
        if (!basedFacts.add(callerFactAp)) return null

        return SummaryEdgeSubscriptionManager.ZeroEdgeSummarySubscription()
            .setCalleeBase(calleeInitialFactBase)
            .setCallerPathEdgeAp(callerFactAp)
    }

    override fun addFactToFact(
        calleeInitialBase: AccessPathBase,
        callerInitialAp: InitialFactAp,
        callerExitAp: FinalFactAp
    ): SummaryEdgeSubscriptionManager.FactEdgeSummarySubscription? {
        val basedFacts = factToFact.getOrPut(calleeInitialBase) { hashSetOf() }
        if (!basedFacts.add(callerInitialAp to callerExitAp)) return null

        return SummaryEdgeSubscriptionManager.FactEdgeSummarySubscription()
            .setCalleeBase(calleeInitialBase)
            .setCallerInitialAp(callerInitialAp)
            .setCallerAp(callerExitAp)
    }

    override fun findFactEdge(
        summaryInitialFactAp: InitialFactAp
    ): Sequence<SummaryEdgeSubscriptionManager.FactEdgeSummarySubscription> =
        factToFact[summaryInitialFactAp.base]?.asSequence()
            ?.map { (callerInitialAp, callerExitAp) ->
                SummaryEdgeSubscriptionManager.FactEdgeSummarySubscription()
                    .setCalleeBase(summaryInitialFactAp.base)
                    .setCallerInitialAp(callerInitialAp)
                    .setCallerAp(callerExitAp)
            }
            .orEmpty()

    override fun findZeroEdge(
        summaryInitialFactAp: InitialFactAp
    ): Sequence<SummaryEdgeSubscriptionManager.ZeroEdgeSummarySubscription> =
        zeroFacts[summaryInitialFactAp.base]?.asSequence()
            ?.map {
                SummaryEdgeSubscriptionManager.ZeroEdgeSummarySubscription()
                    .setCalleeBase(summaryInitialFactAp.base)
                    .setCallerPathEdgeAp(it)
            }
            .orEmpty()
}
