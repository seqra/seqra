package org.opentaint.dataflow.jvm.ap.ifds.access.automata

import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.jvm.ap.ifds.AccessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.ExclusionSet
import org.opentaint.dataflow.jvm.ap.ifds.FinalAccessor
import org.opentaint.dataflow.jvm.ap.ifds.access.ApManager
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAbstraction
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.MethodAccessPathSubscription
import org.opentaint.dataflow.jvm.ap.ifds.access.MethodEdgesFinalApSet
import org.opentaint.dataflow.jvm.ap.ifds.access.MethodEdgesInitialToFinalApSet
import org.opentaint.dataflow.jvm.ap.ifds.access.MethodFinalApSummariesStorage
import org.opentaint.dataflow.jvm.ap.ifds.access.MethodInitialToFinalApSummariesStorage
import org.opentaint.dataflow.jvm.ap.ifds.access.TaintSinkRequirementApStorage

object AutomataApManager : ApManager {
    override fun initialFactAbstraction(): InitialFactAbstraction =
        AutomataInitialFactAbstraction()

    override fun methodEdgesFinalApSet(methodInitialStatement: JIRInst, maxInstIdx: Int): MethodEdgesFinalApSet =
        MethodEdgesFinalAutomataApSet(methodInitialStatement, maxInstIdx)

    override fun methodEdgesInitialToFinalApSet(
        methodInitialStatement: JIRInst,
        maxInstIdx: Int
    ): MethodEdgesInitialToFinalApSet = MethodEdgesInitialToFinalAutomataApSet(methodInitialStatement, maxInstIdx)

    override fun accessPathSubscription(): MethodAccessPathSubscription = MethodAutomataAccessPathSubscription()

    override fun taintSinkRequirementApStorage(): TaintSinkRequirementApStorage =
        TaintSinkRequirementAutomataApStorage()

    override fun methodFinalApSummariesStorage(methodInitialStatement: JIRInst): MethodFinalApSummariesStorage =
        MethodFinalAutomataApSummariesStorage(methodInitialStatement)

    override fun methodInitialToFinalApSummariesStorage(methodInitialStatement: JIRInst): MethodInitialToFinalApSummariesStorage =
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
}
