package org.opentaint.dataflow.jvm.ap.ifds.access.tree

import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.jvm.ap.ifds.AccessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.ExclusionSet
import org.opentaint.dataflow.jvm.ap.ifds.ExclusionSet.Empty
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
import org.opentaint.dataflow.jvm.ap.ifds.access.tree.AccessTree.AccessNode

object TreeApManager : ApManager {
    override fun initialFactAbstraction(): InitialFactAbstraction =
        TreeInitialFactAbstraction()

    override fun methodEdgesFinalApSet(methodInitialStatement: JIRInst, maxInstIdx: Int): MethodEdgesFinalApSet =
        MethodEdgesFinalTreeApSet(methodInitialStatement, maxInstIdx)

    override fun methodEdgesInitialToFinalApSet(
        methodInitialStatement: JIRInst,
        maxInstIdx: Int
    ): MethodEdgesInitialToFinalApSet = MethodEdgesInitialToFinalTreeApSet(methodInitialStatement, maxInstIdx)

    override fun accessPathSubscription(): MethodAccessPathSubscription =
        MethodTreeAccessPathSubscription()

    override fun taintSinkRequirementApStorage(): TaintSinkRequirementApStorage =
        TaintSinkRequirementTreeApStorage()

    override fun methodFinalApSummariesStorage(methodInitialStatement: JIRInst): MethodFinalApSummariesStorage =
        MethodFinalTreeApSummariesStorage(methodInitialStatement)

    override fun methodInitialToFinalApSummariesStorage(methodInitialStatement: JIRInst): MethodInitialToFinalApSummariesStorage =
        MethodInitialToFinalApSummaries(methodInitialStatement)

    override fun mostAbstractInitialAp(base: AccessPathBase): InitialFactAp =
        AccessPath(base, access = null, exclusions = Empty)

    override fun mostAbstractFinalAp(base: AccessPathBase): FinalFactAp =
        AccessTree(base, AccessNode.abstractNode(), exclusions = Empty)

    override fun createFinalAp(base: AccessPathBase, exclusions: ExclusionSet): FinalFactAp =
        AccessTree(base, AccessNode.create(isFinal = true), exclusions)

    override fun createAbstractAp(base: AccessPathBase, exclusions: ExclusionSet): FinalFactAp =
        AccessTree(base, AccessNode.create(isAbstract = true), exclusions)
}
