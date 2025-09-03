package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAbstraction
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.MethodAccessPathSubscription
import org.opentaint.dataflow.ap.ifds.access.MethodEdgesFinalApSet
import org.opentaint.dataflow.ap.ifds.access.MethodEdgesInitialToFinalApSet
import org.opentaint.dataflow.ap.ifds.access.MethodFinalApSummariesStorage
import org.opentaint.dataflow.ap.ifds.access.MethodInitialToFinalApSummariesStorage
import org.opentaint.dataflow.ap.ifds.access.SideEffectRequirementApStorage

object AutomataApManager : ApManager {
    override fun initialFactAbstraction(): InitialFactAbstraction =
        AutomataInitialFactAbstraction()

    override fun methodEdgesFinalApSet(
        methodInitialStatement: CommonInst,
        maxInstIdx: Int,
        languageManager: LanguageManager
    ): MethodEdgesFinalApSet =
        MethodEdgesFinalAutomataApSet(methodInitialStatement, maxInstIdx, languageManager)

    override fun methodEdgesInitialToFinalApSet(
        methodInitialStatement: CommonInst,
        maxInstIdx: Int,
        languageManager: LanguageManager
    ): MethodEdgesInitialToFinalApSet =
        MethodEdgesInitialToFinalAutomataApSet(methodInitialStatement, maxInstIdx, languageManager)

    override fun accessPathSubscription(): MethodAccessPathSubscription = MethodAutomataAccessPathSubscription()

    override fun sideEffectRequirementApStorage(): SideEffectRequirementApStorage =
        SideEffectRequirementAutomataApStorage()

    override fun methodFinalApSummariesStorage(methodInitialStatement: CommonInst): MethodFinalApSummariesStorage =
        MethodFinalAutomataApSummariesStorage(methodInitialStatement)

    override fun methodInitialToFinalApSummariesStorage(methodInitialStatement: CommonInst): MethodInitialToFinalApSummariesStorage =
        MethodInitialToFinalAutomataApSummariesStorage(methodInitialStatement)

    override fun mostAbstractInitialAp(base: AccessPathBase): InitialFactAp =
        AccessGraphInitialFactAp(base, AccessGraph.empty(), ExclusionSet.Empty)

    override fun mostAbstractFinalAp(base: AccessPathBase): FinalFactAp =
        AccessGraphFinalFactAp(base, AccessGraph.empty(), ExclusionSet.Empty)

    private val finalAp by lazy { AccessGraph.empty().prepend(FinalAccessor) }

    override fun createFinalAp(base: AccessPathBase, exclusions: ExclusionSet): FinalFactAp =
        AccessGraphFinalFactAp(base, finalAp, exclusions)

    override fun createAbstractAp(base: AccessPathBase, exclusions: ExclusionSet): FinalFactAp =
        AccessGraphFinalFactAp(base, AccessGraph.empty(), exclusions)

    override fun createFinalInitialAp(base: AccessPathBase, exclusions: ExclusionSet): InitialFactAp =
        AccessGraphInitialFactAp(base, finalAp, exclusions)
}
