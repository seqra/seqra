package org.opentaint.dataflow.jvm.ap.ifds.access.cactus

import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.jvm.ap.ifds.AccessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.ExclusionSet
import org.opentaint.dataflow.jvm.ap.ifds.ExclusionSet.Empty
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
import org.opentaint.dataflow.jvm.ap.ifds.access.cactus.AccessCactus.AccessNode

object CactusApManager : ApManager {
    override fun initialFactAbstraction(): InitialFactAbstraction =
        CactusInitialFactAbstraction()

    override fun methodEdgesFinalApSet(methodInitialStatement: JIRInst, maxInstIdx: Int): MethodEdgesFinalApSet =
        MethodEdgesFinalCactusApSet(methodInitialStatement, maxInstIdx)

    override fun methodEdgesInitialToFinalApSet(
        methodInitialStatement: JIRInst,
        maxInstIdx: Int
    ): MethodEdgesInitialToFinalApSet = MethodEdgesInitialToFinalCactusApSet(methodInitialStatement, maxInstIdx)

    override fun accessPathSubscription(): MethodAccessPathSubscription =
        MethodCactusAccessPathSubscription()

    override fun taintSinkRequirementApStorage(): TaintSinkRequirementApStorage =
        TaintSinkRequirementCactusApStorage()

    override fun methodFinalApSummariesStorage(methodInitialStatement: JIRInst): MethodFinalApSummariesStorage =
        MethodFinalTreeApSummariesStorage(methodInitialStatement)

    override fun methodInitialToFinalApSummariesStorage(methodInitialStatement: JIRInst): MethodInitialToFinalApSummariesStorage =
        MethodInitialToFinalApSummaries(methodInitialStatement)

    override fun mostAbstractInitialAp(base: AccessPathBase): InitialFactAp =
        AccessPathWithCycles(base, access = null, exclusions = Empty)

    override fun mostAbstractFinalAp(base: AccessPathBase): FinalFactAp =
        AccessCactus(base, AccessNode.abstractNode(), exclusions = Empty)

    override fun createFinalAp(base: AccessPathBase, exclusions: ExclusionSet): FinalFactAp =
        AccessCactus(base, AccessNode.create(isFinal = true), exclusions)

    override fun createAbstractAp(base: AccessPathBase, exclusions: ExclusionSet): FinalFactAp =
        AccessCactus(base, AccessNode.create(isAbstract = true), exclusions)

    override fun createFinalInitialAp(base: AccessPathBase, exclusions: ExclusionSet): InitialFactAp =
        AccessPathWithCycles(base, access = null, exclusions).prependAccessor(FinalAccessor)
}
