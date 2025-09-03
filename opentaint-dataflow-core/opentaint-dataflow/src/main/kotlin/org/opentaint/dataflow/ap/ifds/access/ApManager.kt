package org.opentaint.dataflow.ap.ifds.access

import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.FactToFactEdgeBuilder
import org.opentaint.dataflow.ap.ifds.SummaryEdgeSubscriptionManager.ZeroEdgeSummarySubscription
import org.opentaint.dataflow.ap.ifds.SummaryEdgeSubscriptionManager.FactEdgeSummarySubscription
import org.opentaint.dataflow.ap.ifds.ZeroToFactEdgeBuilder
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Edge
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.LanguageManager

interface ApManager {
    fun initialFactAbstraction(): InitialFactAbstraction

    fun methodEdgesFinalApSet(methodInitialStatement: CommonInst, maxInstIdx: Int, languageManager: LanguageManager): MethodEdgesFinalApSet
    fun methodEdgesInitialToFinalApSet(methodInitialStatement: CommonInst, maxInstIdx: Int, languageManager: LanguageManager): MethodEdgesInitialToFinalApSet

    fun accessPathSubscription(): MethodAccessPathSubscription

    fun sideEffectRequirementApStorage(): SideEffectRequirementApStorage
    fun methodFinalApSummariesStorage(methodInitialStatement: CommonInst): MethodFinalApSummariesStorage
    fun methodInitialToFinalApSummariesStorage(methodInitialStatement: CommonInst): MethodInitialToFinalApSummariesStorage

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
    fun add(statement: CommonInst, ap: FinalFactAp): FinalFactAp?
    fun collectApAtStatement(collection: MutableCollection<FinalFactAp>, statement: CommonInst)
}

interface MethodEdgesInitialToFinalApSet {
    fun add(statement: CommonInst, initialAp: InitialFactAp, finalAp: FinalFactAp): Pair<InitialFactAp, FinalFactAp>?
    fun collectApAtStatement(collection: MutableCollection<Pair<InitialFactAp, FinalFactAp>>, statement: CommonInst)
    fun collectApAtStatement(collection: MutableCollection<FinalFactAp>, statement: CommonInst, initialAp: InitialFactAp)
}

interface SideEffectRequirementApStorage {
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
