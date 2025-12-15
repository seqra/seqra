package org.opentaint.dataflow.ap.ifds.access.common

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.SummaryEdgeSubscriptionManager.FactEdgeSummarySubscription
import org.opentaint.dataflow.ap.ifds.SummaryEdgeSubscriptionManager.FactNDEdgeSummarySubscription
import org.opentaint.dataflow.ap.ifds.SummaryEdgeSubscriptionManager.ZeroEdgeSummarySubscription
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

abstract class CommonZeroEdgeSubBuilder<FAP: Any>(
    private var base: AccessPathBase? = null,
    private var ap: FAP? = null,
): FinalApAccess<FAP> {
    fun build(): ZeroEdgeSummarySubscription = ZeroEdgeSummarySubscription()
        .setCallerPathEdgeAp(createFinal(base!!, ap!!, ExclusionSet.Universe))

    fun setBase(base: AccessPathBase) = this.also { this.base = base }
    fun setNode(ap: FAP) = this.also { this.ap = ap }
}

abstract class CommonFactEdgeSubBuilder<FAP: Any>(
    private var callerInitialAp: InitialFactAp? = null,
    private var callerBase: AccessPathBase? = null,
    private var callerAp: FAP? = null,
    private var callerExclusion: ExclusionSet? = null,
): FinalApAccess<FAP> {
    fun build(): FactEdgeSummarySubscription = FactEdgeSummarySubscription()
        .setCallerAp(createFinal(callerBase!!, callerAp!!, callerExclusion!!))
        .setCallerInitialAp(callerInitialAp!!)

    fun setCallerInitialAp(callerInitialAp: InitialFactAp) = this.also { this.callerInitialAp = callerInitialAp }
    fun setCallerBase(callerBase: AccessPathBase) = this.also { this.callerBase = callerBase }
    fun setCallerNode(callerAp: FAP) = this.also { this.callerAp = callerAp }
    fun setCallerExclusion(exclusion: ExclusionSet) = this.also { this.callerExclusion = exclusion }
}

abstract class CommonFactNDEdgeSubBuilder<FAP: Any>(
    private var callerInitial: Set<InitialFactAp>? = null,
    private var callerBase: AccessPathBase? = null,
    private var callerNode: FAP? = null,
): FinalApAccess<FAP> {
    fun build(): FactNDEdgeSummarySubscription = FactNDEdgeSummarySubscription()
        .setCallerAp(createFinal(callerBase!!, callerNode!!, ExclusionSet.Universe))
        .setCallerInitial(callerInitial!!)

    fun setCallerInitial(callerInitial: Set<InitialFactAp>) = this.also { this.callerInitial = callerInitial }
    fun setCallerBase(callerBase: AccessPathBase) = this.also { this.callerBase = callerBase }
    fun setCallerNode(callerNode: FAP) = this.also { this.callerNode = callerNode }
}
