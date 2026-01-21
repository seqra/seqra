package org.opentaint.dataflow.ap.ifds.access

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Edge
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactToFactEdgeBuilder
import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.NDFactToFactEdgeBuilder
import org.opentaint.dataflow.ap.ifds.SideEffectSummary.FactSideEffectSummary
import org.opentaint.dataflow.ap.ifds.SummaryEdgeSubscriptionManager.FactEdgeSummarySubscription
import org.opentaint.dataflow.ap.ifds.SummaryEdgeSubscriptionManager.FactNDEdgeSummarySubscription
import org.opentaint.dataflow.ap.ifds.SummaryEdgeSubscriptionManager.ZeroEdgeSummarySubscription
import org.opentaint.dataflow.ap.ifds.ZeroToFactEdgeBuilder
import org.opentaint.dataflow.ap.ifds.serialization.ApSerializer
import org.opentaint.dataflow.ap.ifds.serialization.SummarySerializationContext
import org.opentaint.ir.api.common.cfg.CommonInst

interface ApManager {
    fun initialFactAbstraction(methodInitialStatement: CommonInst): InitialFactAbstraction

    fun methodEdgesFinalApSet(methodInitialStatement: CommonInst, maxInstIdx: Int, languageManager: LanguageManager): MethodEdgesFinalApSet
    fun methodEdgesInitialToFinalApSet(methodInitialStatement: CommonInst, maxInstIdx: Int, languageManager: LanguageManager): MethodEdgesInitialToFinalApSet
    fun methodEdgesNDInitialToFinalApSet(methodInitialStatement: CommonInst, maxInstIdx: Int, languageManager: LanguageManager): MethodEdgesNDInitialToFinalApSet

    fun accessPathSubscription(): MethodAccessPathSubscription

    fun sideEffectRequirementApStorage(): SideEffectRequirementApStorage
    fun methodFinalApSummariesStorage(methodInitialStatement: CommonInst): MethodFinalApSummariesStorage
    fun methodInitialToFinalApSummariesStorage(methodInitialStatement: CommonInst): MethodInitialToFinalApSummariesStorage
    fun methodNDInitialToFinalApSummariesStorage(methodInitialStatement: CommonInst): MethodNDInitialToFinalApSummariesStorage
    fun factSideEffectSummariesApStorage(methodInitialStatement: CommonInst): FactSideEffectSummariesApStorage

    fun mostAbstractInitialAp(base: AccessPathBase): InitialFactAp
    fun mostAbstractFinalAp(base: AccessPathBase): FinalFactAp

    fun createFinalAp(base: AccessPathBase, exclusions: ExclusionSet): FinalFactAp
    fun createAbstractAp(base: AccessPathBase, exclusions: ExclusionSet): FinalFactAp

    fun createFinalInitialAp(base: AccessPathBase, exclusions: ExclusionSet): InitialFactAp

    fun createSerializer(context: SummarySerializationContext): ApSerializer
}

interface InitialFactAbstraction {
    fun addAbstractedInitialFact(factAp: FinalFactAp): List<Pair<InitialFactAp, FinalFactAp>>
    fun registerNewInitialFact(factAp: InitialFactAp): List<Pair<InitialFactAp, FinalFactAp>>
}

interface MethodAccessPathSubscription {
    fun addZeroToFact(
        callerEp: CommonInst,
        calleeInitialFactBase: AccessPathBase,
        callerFactAp: FinalFactAp
    ): ZeroEdgeSummarySubscription?

    fun addFactToFact(
        callerEp: CommonInst,
        calleeInitialBase: AccessPathBase,
        callerInitialAp: InitialFactAp,
        callerExitAp: FinalFactAp
    ): FactEdgeSummarySubscription?

    fun addNDFactToFact(
        callerEp: CommonInst,
        calleeInitialBase: AccessPathBase,
        callerInitial: Set<InitialFactAp>,
        callerExitAp: FinalFactAp
    ): FactNDEdgeSummarySubscription?

    fun collectFactEdge(
        collection: MutableList<FactEdgeSummarySubscription>,
        summaryInitialFactAp: InitialFactAp,
        emptyDeltaRequired: Boolean
    )

    fun collectFactNDEdge(
        collection: MutableList<FactNDEdgeSummarySubscription>,
        summaryInitialFactAp: InitialFactAp,
        emptyDeltaRequired: Boolean
    )

    fun collectZeroEdge(collection: MutableList<ZeroEdgeSummarySubscription>, summaryInitialFactAp: InitialFactAp)
}

interface MethodEdgesFinalApSet {
    fun add(statement: CommonInst, ap: FinalFactAp): FinalFactAp?
    fun collectApAtStatement(collection: MutableList<FinalFactAp>, statement: CommonInst)
    fun collectApAtStatement(collection: MutableList<FinalFactAp>, statement: CommonInst, finalFactPattern: InitialFactAp)
}

interface MethodEdgesInitialToFinalApSet {
    fun add(statement: CommonInst, initialAp: InitialFactAp, finalAp: FinalFactAp): Pair<InitialFactAp, FinalFactAp>?
    fun collectApAtStatement(collection: MutableList<Pair<InitialFactAp, FinalFactAp>>, statement: CommonInst)
    fun collectApAtStatement(collection: MutableList<Pair<InitialFactAp, FinalFactAp>>, statement: CommonInst, finalFactPattern: InitialFactAp)
    fun collectApAtStatement(collection: MutableList<FinalFactAp>, statement: CommonInst, initialAp: InitialFactAp, finalFactPattern: InitialFactAp)
}

interface MethodEdgesNDInitialToFinalApSet {
    fun add(statement: CommonInst, initial: Set<InitialFactAp>, finalAp: FinalFactAp): Pair<Set<InitialFactAp>, FinalFactAp>?
    fun collectApAtStatement(collection: MutableList<Pair<Set<InitialFactAp>, FinalFactAp>>, statement: CommonInst)
    fun collectApAtStatement(collection: MutableList<Pair<Set<InitialFactAp>, FinalFactAp>>, statement: CommonInst, finalFactPattern: InitialFactAp)
    fun collectApAtStatement(collection: MutableList<FinalFactAp>, statement: CommonInst, initial: Set<InitialFactAp>, finalFactPattern: InitialFactAp)
}

interface SideEffectRequirementApStorage {
    fun add(requirements: List<InitialFactAp>): List<InitialFactAp>
    fun filterTo(dst: MutableList<InitialFactAp>, fact: FinalFactAp)
    fun collectAllRequirementsTo(dst: MutableList<InitialFactAp>)
}

interface FactSideEffectSummariesApStorage {
    fun add(sideEffects: List<FactSideEffectSummary>, added: MutableList<FactSideEffectSummary>)
    fun filterTaintedTo(dst: MutableList<FactSideEffectSummary>, pattern: FinalFactAp?)
}

interface MethodFinalApSummariesStorage {
    fun add(edges: List<Edge.ZeroToFact>, addedEdges: MutableList<ZeroToFactEdgeBuilder>)
    fun filterEdgesTo(dst: MutableList<ZeroToFactEdgeBuilder>, finalFactBase: AccessPathBase?)
}

interface MethodInitialToFinalApSummariesStorage {
    fun add(edges: List<Edge.FactToFact>, added: MutableList<FactToFactEdgeBuilder>)
    fun filterEdgesTo(dst: MutableList<FactToFactEdgeBuilder>, initialFactPattern: FinalFactAp?, finalFactBase: AccessPathBase?)
}

interface MethodNDInitialToFinalApSummariesStorage {
    fun add(edges: List<Edge.NDFactToFact>, added: MutableList<NDFactToFactEdgeBuilder>)
    fun filterEdgesTo(dst: MutableList<NDFactToFactEdgeBuilder>, initialFactPattern: FinalFactAp?, finalFactBase: AccessPathBase?)
}
