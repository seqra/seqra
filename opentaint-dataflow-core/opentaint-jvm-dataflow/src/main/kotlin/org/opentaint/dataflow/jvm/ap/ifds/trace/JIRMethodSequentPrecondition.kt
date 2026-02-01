package org.opentaint.dataflow.jvm.ap.ifds.trace

import mu.KLogging
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.trace.MethodSequentPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodSequentPrecondition.PreconditionFactsForInitialFact
import org.opentaint.dataflow.ap.ifds.trace.MethodSequentPrecondition.SequentPrecondition
import org.opentaint.dataflow.ap.ifds.trace.TaintRulePrecondition
import org.opentaint.dataflow.configuration.jvm.ConstantTrue
import org.opentaint.dataflow.jvm.ap.ifds.CalleePositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionRewriter
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.accessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodAnalysisContext
import org.opentaint.dataflow.jvm.ap.ifds.analysis.forEachPossibleAliasAtStatement
import org.opentaint.dataflow.jvm.ap.ifds.taint.InitialFactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintSourceActionPreconditionEvaluator
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCastExpr
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRThrowInst
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.util.maybeFlatMap

class JIRMethodSequentPrecondition(
    private val apManager: ApManager,
    private val currentInst: JIRInst,
    private val analysisContext: JIRMethodAnalysisContext,
) : MethodSequentPrecondition {

    override fun factPrecondition(
        fact: InitialFactAp,
    ): Set<SequentPrecondition> {
        val results = mutableSetOf<SequentPrecondition>()
        results.computeFactPrecondition(fact, applyExitSourceRules = true)
        return results
    }

    private fun MutableSet<SequentPrecondition>.computeFactPrecondition(
        fact: InitialFactAp,
        applyExitSourceRules: Boolean
    ) {
        val factPrecondition = computePrecondition(fact, applyExitSourceRules)
        this += factPrecondition.ifEmpty { setOf(SequentPrecondition.Unchanged) }

        analysisContext.aliasAnalysis?.forEachPossibleAliasAtStatement(currentInst, fact) { aliasedFact ->
            this += computePrecondition(aliasedFact, applyExitSourceRules)
        }
    }

    private fun computePrecondition(
        aliasedFact: InitialFactAp,
        applyExitSourceRules: Boolean
    ): Set<SequentPrecondition> {
        val precondition = mutableSetOf<SequentPrecondition>()
        preconditionForFact(aliasedFact)?.let {
            precondition += PreconditionFactsForInitialFact(aliasedFact, it)
        }

        precondition.unconditionalSourcesPrecondition(aliasedFact)

        if (applyExitSourceRules) {
            precondition.methodExitSourcePrecondition(aliasedFact)
        }

        return precondition
    }

    private fun preconditionForFact(fact: InitialFactAp): List<InitialFactAp>? {
        when (currentInst) {
            is JIRAssignInst -> {
                return sequentAssignPrecondition(currentInst.rhv, currentInst.lhv, fact)
            }

            is JIRReturnInst -> {
                if (fact.base !is AccessPathBase.Return) {
                    return null
                }

                val base = currentInst.returnValue
                    ?.let { accessPathBase(it) }
                    ?: return null

                return listOf(fact.rebase(base))
            }

            is JIRThrowInst -> {
                if (fact.base !is AccessPathBase.Exception) {
                    return null
                }

                val base = currentInst.throwable
                    .let { accessPathBase(it) }
                    ?: return null

                return listOf(fact.rebase(base))
            }

            else -> return null
        }
    }

    private fun sequentAssignPrecondition(
        assignFrom: JIRExpr,
        assignTo: JIRValue,
        fact: InitialFactAp,
    ): List<InitialFactAp>? {
        val assignFromAccess = when (assignFrom) {
            is JIRCastExpr -> MethodFlowFunctionUtils.mkAccess(assignFrom.operand)
            is JIRImmediate -> MethodFlowFunctionUtils.mkAccess(assignFrom)
            is JIRArrayAccess -> MethodFlowFunctionUtils.mkAccess(assignFrom)
            is JIRFieldRef -> MethodFlowFunctionUtils.mkAccess(assignFrom)
            else -> null
        }

        val assignToAccess = when (assignTo) {
            is JIRImmediate -> MethodFlowFunctionUtils.mkAccess(assignTo)
            is JIRArrayAccess -> MethodFlowFunctionUtils.mkAccess(assignTo)
            is JIRFieldRef -> MethodFlowFunctionUtils.mkAccess(assignTo)
            else -> null
        }

        return when {
            assignFromAccess?.accessor != null -> {
                check(assignToAccess?.accessor == null) { "Complex assignment: $assignTo = $assignFrom" }
                fieldRead(
                    assignToAccess?.base, assignFromAccess.base, assignFromAccess.accessor, fact
                )
            }

            assignToAccess?.accessor != null -> {
                fieldWrite(
                    assignToAccess.base, assignToAccess.accessor, assignFromAccess?.base, fact
                )
            }

            else -> simpleAssign(assignToAccess?.base, assignFromAccess?.base, fact)
        }
    }

    private fun simpleAssign(
        assignTo: AccessPathBase?,
        assignFrom: AccessPathBase?,
        fact: InitialFactAp,
    ): List<InitialFactAp>? {
        if (assignTo == assignFrom || assignTo != fact.base) {
            return null
        }

        if (assignFrom != null) {
            return listOf(fact.rebase(assignFrom))
        }

        // kill fact
        return emptyList()
    }

    private fun fieldRead(
        assignTo: AccessPathBase?,
        instance: AccessPathBase,
        accessor: Accessor,
        fact: InitialFactAp,
    ): List<InitialFactAp>? {
        if (fact.base != assignTo) {
            return null
        }

        return listOf(fact.prependAccessor(accessor).rebase(instance))
    }

    private fun fieldWrite(
        instance: AccessPathBase,
        accessor: Accessor,
        assignFrom: AccessPathBase?,
        fact: InitialFactAp,
    ): List<InitialFactAp>? {
        if (fact.base != instance || !fact.startsWithAccessor(accessor)) {
            return null
        }

        val facts = buildList {
            val factAtAccessor = fact.readAccessor(accessor) ?: error("No fact")
            if (assignFrom != null) {
                this += factAtAccessor.rebase(assignFrom)
            }

            val otherFact = fact.clearAccessor(accessor)
            if (otherFact != null) {
                this += otherFact
            }

            if (accessor is ElementAccessor) {
                this += factAtAccessor.prependAccessor(ElementAccessor)
            }
        }

        return facts
    }

    private fun MutableSet<SequentPrecondition>.unconditionalSourcesPrecondition(fact: InitialFactAp) {
        if (currentInst !is JIRAssignInst) return

        val rhvFieldRef = currentInst.rhv as? JIRFieldRef ?: return
        val field = rhvFieldRef.field.field
        if (!field.isStatic) return

        val lhv = accessPathBase(currentInst.lhv) ?: return
        if (fact.base != lhv) return

        val config = analysisContext.taint.taintConfig as TaintRulesProvider
        val sourceRules = config.sourceRulesForStaticField(field, currentInst, fact = null).toList()
        if (sourceRules.isEmpty()) return

        val entryFactReader = InitialFactReader(fact.rebase(AccessPathBase.Return), apManager)
        val sourcePreconditionEvaluator = TaintSourceActionPreconditionEvaluator(
            entryFactReader, analysisContext.factTypeChecker, returnValueType = null
        )

        for (sourceRule in sourceRules) {
            if (sourceRule.condition !is ConstantTrue) {
                TODO("Field source with complex condition")
            }

            val assignedMarks = sourceRule.actionsAfter.maybeFlatMap {
                sourcePreconditionEvaluator.evaluate(sourceRule, it)
            }
            if (assignedMarks.isNone) continue

            val sourceActions = assignedMarks.getOrThrow().mapTo(hashSetOf()) { it.second }

            this += MethodSequentPrecondition.SequentSource(
                fact, TaintRulePrecondition.Source(sourceRule, sourceActions)
            )
        }
    }

    private fun MutableSet<SequentPrecondition>.methodExitSourcePrecondition(fact: InitialFactAp) {
        val config = analysisContext.taint.taintConfig as TaintRulesProvider
        val sourceRules = config.exitSourceRulesForMethod(currentInst.location.method, currentInst, fact = null).toList()
        if (sourceRules.isEmpty()) return

        val entryFactReader = InitialFactReader(fact, apManager)
        val sourcePreconditionEvaluator = TaintSourceActionPreconditionEvaluator(
            entryFactReader, analysisContext.factTypeChecker, returnValueType = null
        )

        val valueResolver = CalleePositionToJIRValueResolver(currentInst.location.method)
        val conditionRewriter = JIRMarkAwareConditionRewriter(
            valueResolver, analysisContext.factTypeChecker
        )

        for (rule in sourceRules) {
            evaluateSourceRulePrecondition(
                rule,
                sourcePreconditionEvaluator,
                conditionRewriter,
                { r, a ->
                    val src = TaintRulePrecondition.Source(r, a)
                    this += MethodSequentPrecondition.SequentSource(fact, src)
                },
                { _, _, e ->
                    val preconditionFacts = e.preconditionDnf(apManager) { listOf(it) }
                    for (factCube in preconditionFacts) {
                        if (factCube.facts.size != 1) {
                            logger.warn("Exit source precondition is not resolved")
                            continue
                        }

                        val preFact = factCube.facts.single()
                        computeFactPrecondition(preFact, applyExitSourceRules = false)
                    }
                }
            )
        }
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
