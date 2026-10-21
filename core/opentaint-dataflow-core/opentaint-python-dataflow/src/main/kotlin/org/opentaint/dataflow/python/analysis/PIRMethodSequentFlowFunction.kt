package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction.Sequent
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction.TraceInfo
import org.opentaint.dataflow.python.PIRAttrLoadAnyArgumentResolver
import org.opentaint.dataflow.python.PIRSequentAtomEvaluator
import org.opentaint.dataflow.python.PIRCallResolver
import org.opentaint.dataflow.python.PIRConditionRewriter
import org.opentaint.dataflow.python.PIRFlowFunctionUtils
import org.opentaint.dataflow.python.PIRFlowFunctionUtils.SELF_ACCESSOR
import org.opentaint.dataflow.python.PIRFlowFunctionUtils.mayReadAccessor
import org.opentaint.dataflow.python.PIRFlowFunctionUtils.mkFieldAccessor
import org.opentaint.dataflow.python.rulesWithConditions
import org.opentaint.dataflow.python.alias.forEachAliasBeforeStatement
import org.opentaint.dataflow.taint.DefaultFactWithMarkAfterAnyFieldResolver.Companion.createMarkAfterAccessorResolver
import org.opentaint.dataflow.taint.FinalFactReader
import org.opentaint.ir.api.python.PIRAssign
import org.opentaint.ir.api.python.PIRBinaryExpr
import org.opentaint.ir.api.python.PIRDictExpr
import org.opentaint.ir.api.python.PIRExpr
import org.opentaint.ir.api.python.PIRInstruction
import org.opentaint.ir.api.python.PIRIterExpr
import org.opentaint.ir.api.python.PIRListExpr
import org.opentaint.ir.api.python.PIRLoadAttr
import org.opentaint.ir.api.python.PIRNextIter
import org.opentaint.ir.api.python.PIRReadNameExpr
import org.opentaint.ir.api.python.PIRReturn
import org.opentaint.ir.api.python.PIRSetExpr
import org.opentaint.ir.api.python.PIRSliceExpr
import org.opentaint.ir.api.python.PIRStoreAttr
import org.opentaint.ir.api.python.PIRStoreGlobal
import org.opentaint.ir.api.python.PIRStoreSubscript
import org.opentaint.ir.api.python.PIRStringExpr
import org.opentaint.ir.api.python.PIRSubscriptExpr
import org.opentaint.ir.api.python.PIRTupleExpr
import org.opentaint.ir.api.python.PIRValue

class PIRMethodSequentFlowFunction(
    private val instruction: PIRInstruction,
    private val ctx: PIRMethodAnalysisContext,
    private val apManager: ApManager,
    private val callResolver: PIRCallResolver,
) : MethodSequentFlowFunction {
    private val rulesProvider get() = ctx.taint.taintConfig

    private val resolvedNames by lazy {
        check(instruction is PIRLoadAttr) { "Unexpected resolvedNames access on inst: $instruction" }
        callResolver.resolveAttribute(instruction)
    }

    override fun propagateZeroToZero(): Set<Sequent> = buildSet {
        this += Sequent.ZeroToZero

        if (instruction is PIRReturn) {
            applyExitSinkRules(instruction, initialFacts = emptySet(), fact = null) {
                this += it
            }
        }

        if (instruction !is PIRLoadAttr) return@buildSet

        applyLoadAttrSourceRules(instruction, emptySet(), null, ExclusionSet.Universe,
            createFinalFact = { it, trace ->
                this += Sequent.ZeroToFact(factAp = it, trace)
            },
            createEdge = { initial, it, trace ->
                this += Sequent.FactToFact(initial, it, trace)
            },
            createNDEdge = { initial, it, trace ->
                this += Sequent.NDFactToFact(initial, it, trace)
            }
        )
    }

    override fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<Sequent> = buildSet {
        propagateFact(
            initialFacts = emptySet(),
            currentFactAp = currentFactAp,
            unchanged = { this += Sequent.Unchanged },
            propagateFact = { it, traceInfo -> this += Sequent.ZeroToFact(it, traceInfo) },
            propagateFactWithAccessorExclude = { _, _, _ -> error("Zero fact can't carry an accessor exclusion") },
            addSideEffectRequirement = { error("Can't refine Zero fact") },
            addUnchecked = { this += it }
        )
    }

    override fun propagateFactToFact(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> = buildSet {
        propagateFact(
            initialFacts = setOf(initialFactAp),
            currentFactAp = currentFactAp,
            unchanged = { this += Sequent.Unchanged },
            propagateFact = { it, traceInfo -> this += Sequent.FactToFact(initialFactAp, it, traceInfo) },
            propagateFactWithAccessorExclude = { fact, accessor, traceInfo ->
                val refinedInitial = initialFactAp.exclude(accessor)
                this += Sequent.FactToFact(refinedInitial, fact.exclude(accessor), traceInfo)
                this += Sequent.SideEffectRequirement(refinedInitial)
            },
            addSideEffectRequirement = { reader ->
                this += Sequent.SideEffectRequirement(
                    reader.refineFact(initialFactAp.replaceExclusions(ExclusionSet.Empty))
                )
            },
            addUnchecked = { this += it }
        )
    }

    override fun propagateNDFactToFact(
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> = buildSet {
        propagateFact(
            initialFacts = initialFacts,
            currentFactAp = currentFactAp,
            unchanged = { this += Sequent.Unchanged },
            propagateFact = { it, traceInfo -> this += Sequent.NDFactToFact(initialFacts, it, traceInfo) },
            propagateFactWithAccessorExclude = { _, _, _ -> error("NDF2F edge can't be refined: $currentFactAp") },
            addSideEffectRequirement = { reader ->
                check(!reader.hasRefinement) { "NDF2F edge can't be refined: $currentFactAp" }
            },
            addUnchecked = { this += it }
        )
    }

    private fun propagateFact(
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp, TraceInfo) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor, TraceInfo) -> Unit,
        addSideEffectRequirement: (FinalFactReader) -> Unit,
        addUnchecked: (Sequent) -> Unit
    ) {
        when (instruction) {
            is PIRAssign -> handleAssign(
                instruction, currentFactAp, unchanged, propagateFact, propagateFactWithAccessorExclude,
            )
            is PIRLoadAttr -> handleAttrRead(
                instruction, initialFacts, currentFactAp, unchanged, propagateFact,
                propagateFactWithAccessorExclude, addSideEffectRequirement, addUnchecked
            )
            is PIRReturn -> handleReturn(instruction, initialFacts, currentFactAp, unchanged, propagateFact, addUnchecked)
            is PIRStoreAttr -> handleStoreAttr(instruction, currentFactAp, unchanged, propagateFact, propagateFactWithAccessorExclude)
            is PIRStoreSubscript -> handleStoreSubscript(instruction, currentFactAp, unchanged, propagateFact, propagateFactWithAccessorExclude)
            is PIRStoreGlobal -> handleStoreGlobal(instruction, currentFactAp, unchanged, propagateFact, propagateFactWithAccessorExclude)
            is PIRNextIter -> handleNextIter(
                instruction, currentFactAp, unchanged, propagateFact, propagateFactWithAccessorExclude,
            )
            else -> unchanged(currentFactAp)
        }
    }

    private fun handleAssign(
        assign: PIRAssign,
        currentFactAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp, TraceInfo) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor, TraceInfo) -> Unit,
    ) {
        val assignTo = PIRFlowFunctionUtils.accessPathBase(assign.target) ?: return unchanged(currentFactAp)
        val expr = assign.expr

        if (expr is PIRValue) {
            handleSimpleAssign(expr, assignTo, currentFactAp, unchanged, propagateFact)
            return
        }

        if (expr is PIRIterExpr) {
            handleSimpleAssign(expr.iterable, assignTo, currentFactAp, unchanged, propagateFact)
            return
        }

        if (expr is PIRSubscriptExpr) {
            handleSubscriptRead(expr, assignTo, currentFactAp, unchanged, propagateFact, propagateFactWithAccessorExclude)
            return
        }

        if (expr is PIRSliceExpr) {
            handleSliceExpr(expr, assignTo, currentFactAp, unchanged, propagateFact)
            return
        }

        if (expr is PIRDictExpr || expr is PIRListExpr || expr is PIRTupleExpr || expr is PIRSetExpr) {
            handleContainerLiteral(expr, assignTo, currentFactAp, unchanged, propagateFact)
            return
        }

        if (expr is PIRBinaryExpr) {
            handleBinExpr(expr, assignTo, currentFactAp, unchanged, propagateFact)
            return
        }

        if (expr is PIRStringExpr) {
            handleStringExpr(expr, assignTo, currentFactAp, unchanged, propagateFact)
            return
        }

        if (expr is PIRReadNameExpr) {
            handleReadNameExpr(expr, assignTo, currentFactAp, unchanged, propagateFact, propagateFactWithAccessorExclude)
            return
        }

        if (currentFactAp.base != assignTo) unchanged(currentFactAp)
    }

    private fun handleNextIter(
        nextIter: PIRNextIter,
        currentFactAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp, TraceInfo) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor, TraceInfo) -> Unit,
    ) {
        val assignTo = PIRFlowFunctionUtils.accessPathBase(nextIter.target) ?: return unchanged(currentFactAp)
        val objBase = PIRFlowFunctionUtils.accessPathBase(nextIter.iterator)
        val accessor = ElementAccessor

        handleAccessorRead(assignTo, objBase, accessor, currentFactAp, unchanged, propagateFact, propagateFactWithAccessorExclude)
    }

    private fun handleReadNameExpr(
        expr: PIRReadNameExpr,
        assignTo: AccessPathBase,
        currentFactAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp, TraceInfo) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor, TraceInfo) -> Unit,
    ) {
        val (instance, accessor) = PIRFlowFunctionUtils.globalAccess(expr.ref)
        handleAccessorRead(assignTo, instance, accessor, currentFactAp, unchanged, propagateFact, propagateFactWithAccessorExclude)
    }

    private fun handleAttrRead(
        inst: PIRLoadAttr,
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp, TraceInfo) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor, TraceInfo) -> Unit,
        addSideEffectRequirement: (FinalFactReader) -> Unit,
        addUnchecked: (Sequent) -> Unit
    ) {
        val factReader = FinalFactReader(currentFactAp, apManager)

        applyLoadAttrSourceRules(
            inst, initialFacts, factReader, currentFactAp.exclusions,
            createFinalFact = { it, trace ->
                propagateFact(it, trace)
            },
            createEdge = { initial, it, trace ->
                addUnchecked(Sequent.FactToFact(initial, it, trace))
            },
            createNDEdge = { initial, it, trace ->
                addUnchecked(Sequent.NDFactToFact(initial, it, trace))
            }
        )

        if (factReader.hasRefinement) {
            addSideEffectRequirement(factReader)
        }

        val assignTo = PIRFlowFunctionUtils.accessPathBase(inst.target) ?: return unchanged(currentFactAp)
        val objBase = PIRFlowFunctionUtils.accessPathBase(inst.obj) ?: run {
            if (currentFactAp.base != assignTo) unchanged(currentFactAp)
            return
        }
        val accessor = mkFieldAccessor(inst.attribute)

        handleAccessorRead(assignTo, objBase, accessor, currentFactAp, unchanged, propagateFact, propagateFactWithAccessorExclude)

        if (currentFactAp.base == objBase) {
            propagateFact(currentFactAp.rebase(assignTo).prependAccessor(SELF_ACCESSOR), TraceInfo.Flow)
        }
    }

    private fun applyLoadAttrSourceRules(
        inst: PIRLoadAttr,
        initialFacts: Set<InitialFactAp>,
        factReader: FinalFactReader?,
        exclusionSet: ExclusionSet,
        createFinalFact: (FinalFactAp, TraceInfo) -> Unit,
        createEdge: (InitialFactAp, FinalFactAp, TraceInfo) -> Unit,
        createNDEdge: (Set<InitialFactAp>, FinalFactAp, TraceInfo) -> Unit,
    ) {
        val sourceRules = resolvedNames.flatMapTo(mutableListOf()) { attr ->
            rulesProvider.sourcesForAttribute(attr)
        }
        val conditionRewriter = PIRConditionRewriter(PIRAttrLoadAnyArgumentResolver, PIRSequentAtomEvaluator())

        val taintUtil = PIRSequentTaintUtil(ctx, inst, apManager)
        taintUtil.applySourceRules(
            sourceRules = conditionRewriter.rulesWithConditions(sourceRules),
            initialFacts = initialFacts,
            factReader = factReader,
            exclusion = exclusionSet,
            createFinalFact = { srcF, trace ->
                createFinalFact(srcF, trace)
            },
            createEdge = { initial, srcF, trace ->
                createEdge(initial, srcF, trace)
            },
            createNDEdge = { initial, srcF, trace ->
                createNDEdge(initial, srcF, trace)
            },
        )
    }

    private fun handleSliceExpr(
        value: PIRSliceExpr,
        assignTo: AccessPathBase,
        currentFactAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp, TraceInfo) -> Unit,
    ) {
        val obj = value.obj ?: return unchanged(currentFactAp)
        handleSimpleAssign(obj, assignTo, currentFactAp, unchanged, propagateFact)
    }

    private fun handleSimpleAssign(
        value: PIRValue,
        assignTo: AccessPathBase,
        currentFactAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp, TraceInfo) -> Unit,
    ) {
        if (currentFactAp.base != assignTo) unchanged(currentFactAp)

        val assignFrom = PIRFlowFunctionUtils.accessPathBase(value) ?: return

        if (assignFrom == assignTo) {
            unchanged(currentFactAp)
            return
        }

        if (currentFactAp.base == assignFrom) {
            propagateFact(currentFactAp.rebase(assignTo), TraceInfo.Flow)
        }
    }

    private fun handleSubscriptRead(
        expr: PIRSubscriptExpr,
        assignTo: AccessPathBase,
        currentFactAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp, TraceInfo) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor, TraceInfo) -> Unit,
    ) {
        val objBase = PIRFlowFunctionUtils.accessPathBase(expr.obj)
        val accessor = ElementAccessor

        handleAccessorRead(assignTo, objBase, accessor, currentFactAp, unchanged, propagateFact, propagateFactWithAccessorExclude)
    }

    private fun handleAccessorRead(
        assignTo: AccessPathBase,
        instance: AccessPathBase?,
        accessor: Accessor,
        factAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp, TraceInfo) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor, TraceInfo) -> Unit
    ) {
        if (assignTo != factAp.base) {
            if (accessor !is ElementAccessor) {
                unchanged(factAp)
            } else {
                propagateFact(factAp, TraceInfo.Flow)
            }
        }

        if (instance == null || !factAp.mayReadAccessor(instance, accessor)) {
            return
        }

        if (factAp.isAbstract() && accessor !in factAp.exclusions) {
            val nonAbstractAp = factAp.removeAbstraction()
            if (nonAbstractAp != null) {
                handleAccessorRead(
                    assignTo, instance, accessor, nonAbstractAp,
                    unchanged, propagateFact, propagateFactWithAccessorExclude
                )
            }

            propagateFactWithAccessorExclude(factAp, accessor, TraceInfo.Flow)

            return
        }

        check(factAp.startsWithAccessor(accessor))

        val newAp = factAp.readAccessor(accessor)?.rebase(assignTo) ?: error("Impossible")
        propagateFact(newAp, TraceInfo.Flow)
    }

    private fun handleContainerLiteral(
        expr: PIRExpr,
        assignTo: AccessPathBase,
        currentFactAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp, TraceInfo) -> Unit,
    ) {
        if (currentFactAp.base != assignTo) unchanged(currentFactAp)

        val valueExpressions: List<PIRValue> = when (expr) {
            is PIRDictExpr -> expr.values
            is PIRListExpr -> expr.elements
            is PIRTupleExpr -> expr.elements
            is PIRSetExpr -> expr.elements
            else -> return
        }

        for (elem in valueExpressions) {
            val elemBase = PIRFlowFunctionUtils.accessPathBase(elem)
            if (currentFactAp.base != elemBase) continue

            val elementFact = currentFactAp.rebase(assignTo)
                .prependAccessor(ElementAccessor)

            propagateFact(elementFact, TraceInfo.Flow)
            return
        }
    }

    private fun handleBinExpr(
        expr: PIRBinaryExpr,
        assignTo: AccessPathBase,
        currentFactAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp, TraceInfo) -> Unit,
    ) {
        if (currentFactAp.base != assignTo) unchanged(currentFactAp)

        val leftBase = PIRFlowFunctionUtils.accessPathBase(expr.left)
        val rightBase = PIRFlowFunctionUtils.accessPathBase(expr.right)

        if (currentFactAp.base == leftBase || currentFactAp.base == rightBase) {
            propagateFact(currentFactAp.rebase(assignTo), TraceInfo.Flow)
        }
    }

    private fun handleStringExpr(
        expr: PIRStringExpr,
        assignTo: AccessPathBase,
        currentFactAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp, TraceInfo) -> Unit,
    ) {
        if (currentFactAp.base != assignTo) unchanged(currentFactAp)

        for (part in expr.parts) {
            val partBase = PIRFlowFunctionUtils.accessPathBase(part)
            if (currentFactAp.base != partBase) continue

            propagateFact(currentFactAp.rebase(assignTo), TraceInfo.Flow)
            return
        }
    }

    private fun handleReturn(
        ret: PIRReturn,
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp, TraceInfo) -> Unit,
        addUnchecked: (Sequent) -> Unit
    ) {
        unchanged(currentFactAp)

        val retVal = ret.value ?: return
        val retBase = PIRFlowFunctionUtils.accessPathBase(retVal)

        if (currentFactAp.base == retBase) {
            propagateFact(currentFactAp.rebase(AccessPathBase.Return), TraceInfo.Flow)
        }

        applyExitSinkRules(ret, initialFacts, FinalFactReader(currentFactAp, apManager), addUnchecked)
    }

    private fun applyExitSinkRules(ret: PIRReturn, initialFacts: Set<InitialFactAp>, fact: FinalFactReader?, addUnchecked: (Sequent) -> Unit) {
        if (initialFacts.isNotEmpty()) return

        val exitSinks = rulesProvider.exitSinksForMethod(ctx.method)
        if (exitSinks.isEmpty()) return

        val markAfterAnyAccessorResolver = createMarkAfterAccessorResolver(
            ctx.methodEntryPoint, initialFacts
        ) { i, k ->
            addUnchecked(Sequent.FactSideEffect(i, k))
        }

        val atomEvaluator = PIRSequentAtomEvaluator(ret.value)
        val conditionRewriter = PIRConditionRewriter(PIRAttrLoadAnyArgumentResolver, atomEvaluator, call = null)
        val taintUtil = PIRSequentTaintUtil(ctx, ret, apManager)

        taintUtil.applySinkRules(
            conditionRewriter.rulesWithConditions(exitSinks), fact, markAfterAnyAccessorResolver
        )

        taintUtil.factsAfterSink.forEach { (factAp, trace) ->
            addUnchecked(Sequent.ZeroToFact(factAp, trace))
        }
    }

    private fun handleStoreAttr(
        store: PIRStoreAttr,
        currentFactAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp, TraceInfo) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor, TraceInfo) -> Unit
    ) {
        val objBase = PIRFlowFunctionUtils.accessPathBase(store.obj) ?: return unchanged(currentFactAp)
        val valueBase = PIRFlowFunctionUtils.accessPathBase(store.value)
        val accessor = mkFieldAccessor(store.attribute)

        handleAccessorWrite(objBase, accessor, valueBase, currentFactAp, unchanged, propagateFact, propagateFactWithAccessorExclude)
    }

    private fun handleAccessorWrite(
        destObj: AccessPathBase,
        accessor: Accessor,
        assignFrom: AccessPathBase?,
        currentFactAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp, TraceInfo) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor, TraceInfo) -> Unit
    ) {
        if (destObj == assignFrom) {
            if (currentFactAp.base != destObj) {
                unchanged(currentFactAp)
                return
            }

            val auxiliaryBase = AccessPathBase.LocalVar.create(-1)
            check(auxiliaryBase != destObj)

            handleAccessorWrite(
                destObj = destObj,
                accessor = accessor,
                assignFrom = auxiliaryBase,
                currentFactAp = currentFactAp,
                unchanged = {
                    if (it.base != auxiliaryBase) {
                        unchanged(it)
                    }
                },
                propagateFact = { f, traceInfo ->
                    if (f.base != auxiliaryBase) {
                        propagateFact(f, traceInfo)
                    }
                },
                propagateFactWithAccessorExclude = { f, a, traceInfo ->
                    if (f.base != auxiliaryBase) {
                        propagateFactWithAccessorExclude(f, a, traceInfo)
                    }
                }
            )

            handleAccessorWrite(
                destObj = destObj,
                accessor = accessor,
                assignFrom = auxiliaryBase,
                currentFactAp = currentFactAp.rebase(auxiliaryBase),
                unchanged = {
                    if (it.base != auxiliaryBase) {
                        unchanged(it)
                    }
                },
                propagateFact = { f, traceInfo ->
                    if (f.base != auxiliaryBase) {
                        propagateFact(f, traceInfo)
                    }
                },
                propagateFactWithAccessorExclude = { f, a, traceInfo ->
                    if (f.base != auxiliaryBase) {
                        propagateFactWithAccessorExclude(f, a, traceInfo)
                    }
                }
            )

            return
        }

        if (currentFactAp.base == assignFrom) {
            val newFact = currentFactAp.rebase(destObj).prependAccessor(accessor)
            propagateFact(newFact, TraceInfo.Flow)

            ctx.aliasAnalysis?.forEachAliasBeforeStatement(instruction, newFact) {
                propagateFact(it, TraceInfo.Flow)
            }

            unchanged(currentFactAp)
            return
        }

        if (currentFactAp.base != destObj) {
            unchanged(currentFactAp)
            return
        }

        if (accessor is ElementAccessor) {
            propagateFact(currentFactAp, TraceInfo.Flow)
            return
        }

        if (currentFactAp.isAbstract() && !currentFactAp.exclusions.contains(accessor)) {
            propagateFactWithAccessorExclude(currentFactAp, accessor, TraceInfo.Flow)

            val nonAbstractFact = currentFactAp.removeAbstraction()
            if (nonAbstractFact != null) {
                handleAccessorWrite(
                    destObj, accessor, assignFrom, nonAbstractFact,
                    unchanged, propagateFact, propagateFactWithAccessorExclude
                )
            }
            return
        }

        if (!currentFactAp.startsWithAccessor(accessor)) {
            propagateFact(currentFactAp, TraceInfo.Flow)
            return
        }

        val cleaned = currentFactAp.clearAccessor(accessor)
        if (cleaned != null) {
            propagateFact(cleaned, TraceInfo.Flow)
        }
    }

    private fun handleStoreSubscript(
        store: PIRStoreSubscript,
        currentFactAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp, TraceInfo) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor, TraceInfo) -> Unit
    ) {
        val objBase = PIRFlowFunctionUtils.accessPathBase(store.obj) ?: return unchanged(currentFactAp)
        val valueBase = PIRFlowFunctionUtils.accessPathBase(store.value)
        val accessor = ElementAccessor

        handleAccessorWrite(objBase, accessor, valueBase, currentFactAp, unchanged, propagateFact, propagateFactWithAccessorExclude)
    }

    private fun handleStoreGlobal(
        store: PIRStoreGlobal,
        currentFactAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp, TraceInfo) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor, TraceInfo) -> Unit
    ) {
        val (objBase, accessor) = PIRFlowFunctionUtils.globalAccess(store.ref)
        val valueBase = PIRFlowFunctionUtils.accessPathBase(store.value)

        handleAccessorWrite(objBase, accessor, valueBase, currentFactAp, unchanged, propagateFact, propagateFactWithAccessorExclude)
    }
}
