package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.Edge
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.ExclusionSet.Empty
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FactSideEffectSummariesApStorage
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.FinalFactList
import org.opentaint.dataflow.ap.ifds.access.InitialFactAbstraction
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.MethodAccessPathSubscription
import org.opentaint.dataflow.ap.ifds.access.MethodEdgesFinalApSet
import org.opentaint.dataflow.ap.ifds.access.MethodEdgesInitialToFinalApSet
import org.opentaint.dataflow.ap.ifds.access.MethodEdgesNDInitialToFinalApSet
import org.opentaint.dataflow.ap.ifds.access.MethodFinalApSummariesStorage
import org.opentaint.dataflow.ap.ifds.access.MethodInitialToFinalApSummariesStorage
import org.opentaint.dataflow.ap.ifds.access.MethodNDInitialToFinalApSummariesStorage
import org.opentaint.dataflow.ap.ifds.access.SideEffectRequirementApStorage
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner
import org.opentaint.dataflow.ap.ifds.serialization.ApSerializer
import org.opentaint.dataflow.ap.ifds.serialization.SummarySerializationContext
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.SoftReferenceManager
import org.opentaint.ir.api.common.cfg.CommonInst

class TreeApManager(
    override val anyAccessorUnrollStrategy: AnyAccessorUnrollStrategy,
    val refManager: SoftReferenceManager = SoftReferenceManager(),
    override val cancellation: Cancellation = Cancellation(),
) : ApManager {
    val interner = AccessorInterner()

    val Accessor.idx: AccessorIdx
        get() = interner.index(this)

    val AccessorIdx.accessor: Accessor
        get() = interner.accessor(this)
            ?: error("Accessor not found: $this")

    override fun initialFactAbstraction(methodInitialStatement: CommonInst): InitialFactAbstraction =
        TreeInitialFactAbstraction(this)

    override fun methodEdgesFinalApSet(
        methodInitialStatement: CommonInst,
        maxInstIdx: Int,
        languageManager: LanguageManager
    ): MethodEdgesFinalApSet =
        MethodEdgesFinalTreeApSet(methodInitialStatement, maxInstIdx, languageManager, this)

    override fun methodEdgesInitialToFinalApSet(
        methodInitialStatement: CommonInst,
        maxInstIdx: Int,
        languageManager: LanguageManager
    ): MethodEdgesInitialToFinalApSet = MethodEdgesInitialToFinalTreeApSet(methodInitialStatement, maxInstIdx, languageManager, this)

    override fun methodEdgesNDInitialToFinalApSet(
        methodInitialStatement: CommonInst,
        maxInstIdx: Int,
        languageManager: LanguageManager
    ): MethodEdgesNDInitialToFinalApSet =
        MethodEdgesNDInitialToFinalTreeApSet(methodInitialStatement, maxInstIdx, languageManager, this)

    override fun accessPathSubscription(): MethodAccessPathSubscription =
        MethodTreeAccessPathSubscription(this)

    override fun sideEffectRequirementApStorage(): SideEffectRequirementApStorage =
        SideEffectRequirementTreeApStorage(this)

    override fun methodFinalApSummariesStorage(methodInitialStatement: CommonInst): MethodFinalApSummariesStorage =
        MethodFinalTreeApSummariesStorage(methodInitialStatement, this)

    override fun methodInitialToFinalApSummariesStorage(methodInitialStatement: CommonInst): MethodInitialToFinalApSummariesStorage =
        MethodInitialToFinalApSummaries(methodInitialStatement, this)

    override fun methodNDInitialToFinalApSummariesStorage(methodInitialStatement: CommonInst): MethodNDInitialToFinalApSummariesStorage =
        MethodNDInitialToFinalApSummaries(methodInitialStatement, this)

    override fun factSideEffectSummariesApStorage(methodInitialStatement: CommonInst): FactSideEffectSummariesApStorage =
        FactSideEffectSummariesTreeApStorage(methodInitialStatement, this)

    override fun listEdgeCompressionRequired(edge: Edge): Boolean {
        val fact = when (edge) {
            is Edge.ZeroToZero -> return false
            is Edge.FactToFact -> edge.factAp
            is Edge.NDFactToFact -> edge.factAp
            is Edge.ZeroToFact -> edge.factAp
        }
        return TreeFinalFactList.factCompressionRequired(fact)
    }

    override fun finalFactList(): FinalFactList = TreeFinalFactList(this)

    override fun mostAbstractInitialAp(base: AccessPathBase): InitialFactAp =
        AccessPath(this, base, access = null, exclusions = Empty)

    override fun mostAbstractFinalAp(base: AccessPathBase): FinalFactAp =
        AccessTree(this, base, abstractNode, exclusions = Empty)

    override fun createFinalAp(base: AccessPathBase, exclusions: ExclusionSet): FinalFactAp =
        AccessTree(this,base, finalNode, exclusions)

    override fun createAbstractAp(base: AccessPathBase, exclusions: ExclusionSet): FinalFactAp =
        AccessTree(this,base, abstractNode, exclusions)

    override fun createFinalInitialAp(base: AccessPathBase, exclusions: ExclusionSet): InitialFactAp =
        AccessPath(this, base, access = null, exclusions).prependAccessor(FinalAccessor)

    override fun createSerializer(context: SummarySerializationContext): ApSerializer {
        return TreeSerializer(this, context)
    }

    val emptyNode = AccessNode.createInitialNode(
        this,
        isAbstract = false, isFinal = false,
    )

    val abstractNode = AccessNode.createInitialNode(
        this,
        isAbstract = true, isFinal = false,
    )

    val finalNode = AccessNode.createInitialNode(
        this,
        isAbstract = false, isFinal = true,
    )

    val abstractFinalNode = AccessNode.createInitialNode(
        this,
        isAbstract = true, isFinal = true,
    )
}
