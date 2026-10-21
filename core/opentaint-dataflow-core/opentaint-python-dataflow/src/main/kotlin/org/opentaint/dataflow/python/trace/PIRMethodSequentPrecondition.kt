package org.opentaint.dataflow.python.trace

import mu.KLogging
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.trace.MethodSequentPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodSequentPrecondition.PreconditionFactsForInitialFact
import org.opentaint.dataflow.ap.ifds.trace.MethodSequentPrecondition.SequentPrecondition
import org.opentaint.dataflow.ap.ifds.trace.TaintRulePrecondition
import org.opentaint.dataflow.configuration.CommonTaintAction
import org.opentaint.dataflow.configuration.python.TaintPassAction
import org.opentaint.dataflow.configuration.python.TaintPassThrough
import org.opentaint.dataflow.python.PIRAttrLoadAnyArgumentResolver
import org.opentaint.dataflow.python.PIRCallResolver
import org.opentaint.dataflow.python.PIRConditionRewriter
import org.opentaint.dataflow.python.PIRFlowFunctionUtils
import org.opentaint.dataflow.python.PIRFlowFunctionUtils.SELF_ACCESSOR
import org.opentaint.dataflow.python.PIRFlowFunctionUtils.accessPathBase
import org.opentaint.dataflow.python.PIRFlowFunctionUtils.mkFieldAccessor
import org.opentaint.dataflow.python.PIRFlowFunctionUtils.resolveAp
import org.opentaint.dataflow.python.PIRSequentAtomEvaluator
import org.opentaint.dataflow.python.rulesWithConditions
import org.opentaint.dataflow.python.alias.forEachPossibleAliasBeforeStatement
import org.opentaint.dataflow.python.analysis.PIRMethodAnalysisContext
import org.opentaint.dataflow.python.analysis.PIRMethodCallFactMapper
import org.opentaint.dataflow.taint.InitialFactReader
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr
import org.opentaint.dataflow.taint.TaintPassActionPreconditionEvaluator
import org.opentaint.dataflow.taint.TaintSourceActionPreconditionEvaluator
import org.opentaint.dataflow.taint.evaluatePassRulePrecondition
import org.opentaint.dataflow.taint.evaluateSourceRulePrecondition
import org.opentaint.dataflow.taint.preconditionDnf
import org.opentaint.util.Maybe
import org.opentaint.ir.api.python.PIRAssign
import org.opentaint.ir.api.python.PIRBinaryExpr
import org.opentaint.ir.api.python.PIRDictExpr
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

class PIRMethodSequentPrecondition(
    private val apManager: ApManager,
    private val currentInst: PIRInstruction,
    private val analysisContext: PIRMethodAnalysisContext,
    private val callResolver: PIRCallResolver,
) : MethodSequentPrecondition {
    private val rulesProvider get() = analysisContext.taint.taintConfig

    override fun factPrecondition(fact: InitialFactAp): Set<SequentPrecondition> {
        val result = hashSetOf<SequentPrecondition>()
        result.computeFactPrecondition(fact)
        return result
    }

    private fun MutableSet<SequentPrecondition>.computeFactPrecondition(fact: InitialFactAp) {
        this += computePrecondition(fact).ifEmpty { setOf(SequentPrecondition.Unchanged) }

        analysisContext.aliasAnalysis?.forEachPossibleAliasBeforeStatement(currentInst, fact) { aliasedFact ->
            this += computePrecondition(aliasedFact)
        }
    }

    private fun computePrecondition(fact: InitialFactAp): Set<SequentPrecondition> = buildSet {
        val structural = preconditionForFact(fact)

        if (structural != null) {
            this += PreconditionFactsForInitialFact(fact, structural)
        }

        attributeSourceRulePrecondition(fact)
    }

    private fun MutableSet<SequentPrecondition>.attributeSourceRulePrecondition(fact: InitialFactAp) {
        val inst = currentInst as? PIRLoadAttr ?: return
        val calleeFact = mapFactToAttributeFrame(inst, fact) ?: return

        val sourceRules = attributeNames(inst).flatMap { rulesProvider.sourcesForAttribute(it) }
        if (sourceRules.isEmpty()) return

        val evaluator = TaintSourceActionPreconditionEvaluator(InitialFactReader(calleeFact, apManager))
        val conditionRewriter = attributeConditionRewriter()

        for (rule in conditionRewriter.rulesWithConditions(sourceRules)) {
            evaluateSourceRulePrecondition(
                rule,
                rule.rule.taint,
                sourcePreconditionEvaluator = evaluator,
                evalAction = { r, a ->
                    val pos = a.pos.resolveAp()
                    if (pos == null) Maybe.none() else evaluate(r, a, pos, TaintMarkAccessor(a.mark.name))
                },
                mkSource = { r, actions ->
                    this += MethodSequentPrecondition.SequentSource(fact, TaintRulePrecondition.Source(r, actions))
                },
                mkPass = { _, _, expr ->
                    conditionalSourcePrecondition(inst, fact, expr)
                },
            )
        }
    }

    private fun MutableSet<SequentPrecondition>.conditionalSourcePrecondition(
        inst: PIRLoadAttr,
        fact: InitialFactAp,
        condition: TaintMarkAwareConditionExpr,
    ) {
        val cubes = condition.preconditionDnf(
            apManager,
            allFactsAtStatement = { TODO("All facts enumeration is not supported") },
            mapFacts = { listOfNotNull(PIRMethodCallFactMapper.mapLoadAttributeFactToReturn(inst, it)) },
        )

        val preconditionFacts = cubes.mapNotNull {
            if (it.facts.size != 1) {
                logger.warn("Attribute source precondition is not resolved")
                null
            } else {
                it.facts.single()
            }
        }

        if (preconditionFacts.isNotEmpty()) {
            this += PreconditionFactsForInitialFact(fact, preconditionFacts)
        }
    }

    private fun TaintPassActionPreconditionEvaluator.acceptAttributePass(
        rule: TaintPassThrough,
        action: TaintPassAction,
    ): Maybe<List<Pair<CommonTaintAction, InitialFactAp>>> {
        val from = action.from.resolveAp() ?: return Maybe.none()
        val to = action.to.resolveAp() ?: return Maybe.none()
        val mark = action.mark
        return if (mark == null) {
            propagateData(rule, action, from, to)
        } else {
            propagateTaint(rule, action, from, to, TaintMarkAccessor(mark.name))
        }
    }

    private fun mapFactToAttributeFrame(inst: PIRLoadAttr, fact: InitialFactAp): InitialFactAp? {
        if (fact.base == base(inst.target)) return fact.rebase(AccessPathBase.Return)

        var result: InitialFactAp? = null
        PIRMethodCallFactMapper.mapLoadAttributeFactToStart(inst, fact) { mapped, newBase ->
            if (result == null) result = mapped.rebase(newBase)
        }
        return result
    }

    private fun attributeNames(inst: PIRLoadAttr): Set<String> = callResolver.resolveAttribute(inst)

    private fun attributeConditionRewriter() =
        PIRConditionRewriter(PIRAttrLoadAnyArgumentResolver, PIRSequentAtomEvaluator())

    private fun preconditionForFact(fact: InitialFactAp): List<InitialFactAp>? =
        when (val inst = currentInst) {
            is PIRAssign -> assignPrecondition(inst, fact)
            is PIRLoadAttr -> loadAttrPrecondition(inst, fact)
            is PIRStoreAttr ->
                accessorWritePrecondition(base(inst.obj), mkFieldAccessor(inst.attribute), valueBases(inst.value), fact)
            is PIRStoreSubscript ->
                accessorWritePrecondition(base(inst.obj), ElementAccessor, valueBases(inst.value), fact)
            is PIRStoreGlobal -> {
                val (dst, accessor) = PIRFlowFunctionUtils.globalAccess(inst.ref)
                accessorWritePrecondition(dst, accessor, valueBases(inst.value), fact)
            }
            is PIRReturn -> returnPrecondition(inst, fact)
            is PIRNextIter -> accessorReadPrecondition(base(inst.iterator), ElementAccessor, base(inst.target), fact)
            else -> null
        }

    private fun assignPrecondition(inst: PIRAssign, fact: InitialFactAp): List<InitialFactAp>? {
        val target = base(inst.target) ?: return null
        return when (val expr = inst.expr) {
            is PIRValue -> simpleAssignPrecondition(target, base(expr), fact)
            is PIRIterExpr -> simpleAssignPrecondition(target, base(expr.iterable), fact)
            is PIRSliceExpr -> expr.obj?.let { simpleAssignPrecondition(target, base(it), fact) }
            is PIRSubscriptExpr -> accessorReadPrecondition(base(expr.obj), ElementAccessor, target, fact)
            is PIRReadNameExpr -> {
                val (instance, accessor) = PIRFlowFunctionUtils.globalAccess(expr.ref)
                accessorReadPrecondition(instance, accessor, target, fact)
            }
            is PIRDictExpr -> containerPrecondition(target, expr.values.mapNotNull { base(it) }, fact)
            is PIRListExpr -> containerPrecondition(target, expr.elements.mapNotNull { base(it) }, fact)
            is PIRTupleExpr -> containerPrecondition(target, expr.elements.mapNotNull { base(it) }, fact)
            is PIRSetExpr -> containerPrecondition(target, expr.elements.mapNotNull { base(it) }, fact)
            is PIRBinaryExpr -> operandsPrecondition(target, listOfNotNull(base(expr.left), base(expr.right)), fact)
            is PIRStringExpr -> operandsPrecondition(target, expr.parts.mapNotNull { base(it) }, fact)
            else -> if (fact.base == target) emptyList() else null
        }
    }

    private fun loadAttrPrecondition(inst: PIRLoadAttr, fact: InitialFactAp): List<InitialFactAp>? {
        val target = base(inst.target) ?: return null
        if (fact.base != target) return null

        val objBase = base(inst.obj) ?: return emptyList()
        val accessor = mkFieldAccessor(inst.attribute)

        val pres = mutableListOf<InitialFactAp>()
        pres += fact.prependAccessor(accessor).rebase(objBase)

        if (fact.startsWithAccessor(SELF_ACCESSOR)) {
            fact.readAccessor(SELF_ACCESSOR)?.let { pres += it.rebase(objBase) }
        }

        return pres
    }

    private fun simpleAssignPrecondition(
        toBase: AccessPathBase,
        fromBase: AccessPathBase?,
        fact: InitialFactAp,
    ): List<InitialFactAp>? {
        if (fact.base != toBase) return null
        if (fromBase == null) return emptyList()
        if (fromBase == toBase) return null
        return listOf(fact.rebase(fromBase))
    }

    private fun accessorReadPrecondition(
        instance: AccessPathBase?,
        accessor: Accessor,
        target: AccessPathBase?,
        fact: InitialFactAp,
    ): List<InitialFactAp>? {
        if (target != fact.base && fact.base == instance && accessor is ElementAccessor) {
            return listOf(fact)
        }

        if (target == null || fact.base != target) return null
        if (instance == null) return emptyList()
        return listOf(fact.prependAccessor(accessor).rebase(instance))
    }

    private fun accessorWritePrecondition(
        destBase: AccessPathBase?,
        accessor: Accessor,
        valueBases: List<AccessPathBase>,
        fact: InitialFactAp,
    ): List<InitialFactAp>? {
        if (destBase == null || fact.base != destBase) return null
        if (!fact.startsWithAccessor(accessor)) return null

        val factAtAccessor = fact.readAccessor(accessor) ?: return null

        val pres = mutableListOf<InitialFactAp>()
        valueBases.mapTo(pres) { factAtAccessor.rebase(it) }

        fact.clearAccessor(accessor)?.let { pres += it }

        if (accessor is ElementAccessor) {
            pres += factAtAccessor.prependAccessor(ElementAccessor)
        }

        return pres
    }

    private fun containerPrecondition(
        target: AccessPathBase,
        elementBases: List<AccessPathBase>,
        fact: InitialFactAp,
    ): List<InitialFactAp>? {
        if (fact.base != target) return null
        val stripped = fact.readAccessor(ElementAccessor) ?: return emptyList()
        return elementBases.map { stripped.rebase(it) }
    }

    private fun operandsPrecondition(
        target: AccessPathBase,
        operandBases: List<AccessPathBase>,
        fact: InitialFactAp,
    ): List<InitialFactAp>? {
        if (fact.base != target) return null
        return operandBases.map { fact.rebase(it) }
    }

    private fun returnPrecondition(inst: PIRReturn, fact: InitialFactAp): List<InitialFactAp>? {
        if (fact.base !is AccessPathBase.Return) return null
        val retBase = inst.value?.let { base(it) } ?: return null
        return listOf(fact.rebase(retBase))
    }

    private fun base(value: PIRValue): AccessPathBase? = accessPathBase(value)

    private fun valueBases(value: PIRValue): List<AccessPathBase> = listOfNotNull(base(value))

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
