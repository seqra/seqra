package org.opentaint.dataflow.jvm.ap.ifds.access

import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.jvm.ap.ifds.AccessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.Edge
import org.opentaint.dataflow.jvm.ap.ifds.ExclusionSet
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

    fun createFinalInitialAp(base: AccessPathBase, exclusions: ExclusionSet): InitialFactAp
}

interface InitialFactAbstraction {
    fun addAbstractedInitialFact(factAp: FinalFactAp): List<Pair<InitialFactAp, FinalFactAp>>
    fun registerNewInitialFact(factAp: InitialFactAp): List<Pair<InitialFactAp, FinalFactAp>>
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
    fun collectApAtStatement(collection: MutableCollection<FinalFactAp>, statement: JIRInst)
}

interface MethodEdgesInitialToFinalApSet {
    fun add(statement: JIRInst, initialAp: InitialFactAp, finalAp: FinalFactAp): Pair<InitialFactAp, FinalFactAp>?
    fun collectApAtStatement(collection: MutableCollection<Pair<InitialFactAp, FinalFactAp>>, statement: JIRInst)
    fun collectApAtStatement(collection: MutableCollection<FinalFactAp>, statement: JIRInst, initialAp: InitialFactAp)
}

interface TaintSinkRequirementApStorage {
    fun add(ap: InitialFactAp): InitialFactAp?
    fun find(fact: FinalFactAp): Sequence<InitialFactAp>?
}

interface MethodFinalApSummariesStorage {
    fun add(edges: List<Edge.ZeroToFact>, addedEdges: MutableList<ZeroToFactEdgeBuilder>)
    fun allEdges(): Sequence<ZeroToFactEdgeBuilder>
    fun filterEdges(finalFactBase: AccessPathBase): Sequence<ZeroToFactEdgeBuilder>
}

interface MethodInitialToFinalApSummariesStorage {
    fun add(edges: List<Edge.FactToFact>, added: MutableList<FactToFactEdgeBuilder>)
    fun filterEdges(pattern: FinalFactAp, finalFactBase: AccessPathBase?): Sequence<FactToFactEdgeBuilder>
    fun allEdges(): Sequence<FactToFactEdgeBuilder>
}
