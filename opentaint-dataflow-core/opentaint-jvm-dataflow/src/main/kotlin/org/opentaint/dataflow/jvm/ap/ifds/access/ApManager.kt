package org.opentaint.dataflow.jvm.ap.ifds.access

import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.jvm.ap.ifds.AccessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.Accessor
import org.opentaint.dataflow.jvm.ap.ifds.Edge
import org.opentaint.dataflow.jvm.ap.ifds.ExclusionSet
import org.opentaint.dataflow.jvm.ap.ifds.Fact
import org.opentaint.dataflow.jvm.ap.ifds.FactToFactEdgeBuilder
import org.opentaint.dataflow.jvm.ap.ifds.SummaryEdgeSubscriptionManager.FactEdgeSummarySubscription
import org.opentaint.dataflow.jvm.ap.ifds.SummaryEdgeSubscriptionManager.ZeroEdgeSummarySubscription
import org.opentaint.dataflow.jvm.ap.ifds.ZeroToFactEdgeBuilder

interface ApManager {
    fun initialFactAbstraction(): InitialFactAbstraction

    fun methodEdgesFinalApSet(methodInitialStatement: JIRInst, maxInstIdx: Int): MethodEdgesFinalApSet
    fun methodEdgesInitialToFinalApSet(methodInitialStatement: JIRInst, maxInstIdx: Int): MethodEdgesInitialToFinalApSet

    fun accessPathSubscription(): MethodAccessPathSubscription

    fun taintSinkRequirementApStorage(): TaintSinkRequirementApStorage
    fun methodFinalApSummariesStorage(methodInitialStatement: JIRInst): MethodFinalApSummariesStorage
    fun methodInitialToFinalApSummariesStorage(methodInitialStatement: JIRInst): MethodInitialToFinalApSummariesStorage

    fun mostAbstractInitialAp(base: AccessPathBase): InitialFactAp
    fun mostAbstractFinalAp(base: AccessPathBase): FinalFactAp

    fun createFinalAp(base: AccessPathBase, exclusions: ExclusionSet): FinalFactAp
    fun createAbstractAp(base: AccessPathBase, exclusions: ExclusionSet): FinalFactAp
}

interface InitialFactAbstraction {
    fun addAbstractedInitialFact(fact: Fact.FinalFact): List<Pair<Fact.InitialFact, Fact.FinalFact>>
    fun registerNewInitialFact(fact: Fact.InitialFact): List<Pair<Fact.InitialFact, Fact.FinalFact>>
}

interface MethodAccessPathSubscription {
    fun addZeroToFact(
        calleeInitialFactBase: AccessPathBase,
        callerFactAp: FinalFactAp
    ): ZeroEdgeSummarySubscription?

    fun addFactToFact(
        calleeInitialBase: AccessPathBase,
        callerInitialAp: InitialFactAp,
        callerExitAp: FinalFactAp
    ): FactEdgeSummarySubscription?

    fun findFactEdge(summaryInitialFactAp: InitialFactAp): Sequence<FactEdgeSummarySubscription>

    fun findZeroEdge(summaryInitialFactAp: InitialFactAp): Sequence<ZeroEdgeSummarySubscription>
}

interface MethodEdgesFinalApSet {
    fun add(statement: JIRInst, ap: FinalFactAp): FinalFactAp?
}

interface MethodEdgesInitialToFinalApSet {
    fun add(statement: JIRInst, initialAp: InitialFactAp, finalAp: FinalFactAp): Pair<InitialFactAp, FinalFactAp>?
}

interface TaintSinkRequirementApStorage {
    fun add(ap: InitialFactAp): InitialFactAp?
    fun find(fact: FinalFactAp): Sequence<InitialFactAp>?
}

interface MethodFinalApSummariesStorage {
    fun add(edges: List<Edge.ZeroToFact>, addedEdges: MutableList<ZeroToFactEdgeBuilder>)
    fun allEdges(): Sequence<ZeroToFactEdgeBuilder>
}

interface MethodInitialToFinalApSummariesStorage {
    fun add(edges: List<Edge.FactToFact>, added: MutableList<FactToFactEdgeBuilder>)
    fun filterEdges(pattern: FinalFactAp): Sequence<FactToFactEdgeBuilder>
    fun allEdges(): Sequence<FactToFactEdgeBuilder>
}
