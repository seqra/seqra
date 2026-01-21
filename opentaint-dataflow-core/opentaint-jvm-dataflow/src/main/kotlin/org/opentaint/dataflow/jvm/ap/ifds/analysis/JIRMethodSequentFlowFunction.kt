package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.FactTypeChecker.FilterResult
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction.Sequent
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction.TraceInfo
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker.VulnerabilityTriggerPosition
import org.opentaint.dataflow.configuration.jvm.ConstantTrue
import org.opentaint.dataflow.jvm.ap.ifds.CalleePositionToJIRValueResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionRewriter
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.accessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.clearField
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.excludeField
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.mayReadField
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.mayRemoveAfterWrite
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.readFieldTo
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.writeToField
import org.opentaint.dataflow.jvm.ap.ifds.TaintConfigUtils.applyRuleWithAssumptions
import org.opentaint.dataflow.jvm.ap.ifds.taint.FinalFactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.JIRFactWithMarkAfterAnyFieldResolver.Companion.createMarkAfterFieldsResolver
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintSourceActionEvaluator
import org.opentaint.ir.api.jvm.JIRType
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
import org.opentaint.util.onSome

class JIRMethodSequentFlowFunction(
    private val apManager: ApManager,
    private val analysisContext: JIRMethodAnalysisContext,
    private val currentInst: JIRInst,
    private val generateTrace: Boolean,
): MethodSequentFlowFunction {
    private val factTypeChecker get() = analysisContext.factTypeChecker

    override fun propagateZeroToZero(): Set<Sequent> = buildSet {
        add(Sequent.ZeroToZero)

        applyUnconditionalSources()
    }

    override fun propagateZeroToFact(currentFactAp: FinalFactAp) = buildSet {
        propagate(
            // todo: in trace mode we can't distinguish z2f from f2f
            initialFacts = emptySet<InitialFactAp>().takeIf { !generateTrace },
            factAp = currentFactAp,
            unchanged = { add(Sequent.Unchanged) },
            propagateFact = { fact, trace ->
                add(Sequent.ZeroToFact(fact, trace))
            },
            propagateFactWithRefinement = { refiner, fact, trace ->
                check(!refiner.hasRefinement) {
                    "Zero to Fact edge can't be refined: $currentFactAp"
                }
                add(Sequent.ZeroToFact(fact, trace))
            },
            propagateFactWithAccessorExclude = { _, _, _ ->
                error("Zero to Fact edge can't be refined: $currentFactAp")
            },
            sideEffect = { add(it) }
        )
    }

    override fun propagateFactToFact(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp
    ) = buildSet {
        propagate(
            initialFacts = setOf(initialFactAp),
            factAp = currentFactAp,
            unchanged = { add(Sequent.Unchanged) },
            propagateFact = { fact, trace ->
                add(Sequent.FactToFact(initialFactAp, fact, trace))
            },
            propagateFactWithRefinement = { refiner, fact, trace ->
                val refinedInitial = refiner.refineFact(initialFactAp)
                val refinedFact = refiner.refineFact(fact)
                add(Sequent.FactToFact(refinedInitial, refinedFact, trace))
            },
            propagateFactWithAccessorExclude = { fact, accessor, trace ->
                val refinedInitial = initialFactAp.excludeField(accessor)
                val refinedFact = fact.excludeField(accessor)
                add(Sequent.FactToFact(refinedInitial, refinedFact, trace))
            },
            sideEffect = { add(it) }
        )
    }

    override fun propagateNDFactToFact(
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp
    ) = buildSet {
        propagate(
            initialFacts = initialFacts,
            factAp = currentFactAp,
            unchanged = { add(Sequent.Unchanged) },
            propagateFact = { fact, trace ->
                add(Sequent.NDFactToFact(initialFacts, fact, trace))
            },
            propagateFactWithRefinement = { refiner, fact, trace ->
                check(!refiner.hasRefinement) {
                    "NDF2F edge can't be refined: $currentFactAp"
                }
                add(Sequent.NDFactToFact(initialFacts, fact, trace))
            },
            propagateFactWithAccessorExclude = { _, _, _ ->
                error("NDF2F edge can't be refined: $currentFactAp")
            },
            sideEffect = { add(it) }
        )
    }

    private fun propagate(
        initialFacts: Set<InitialFactAp>?,
        factAp: FinalFactAp,
        unchanged: () -> Unit,
        propagateFact: (FinalFactAp, TraceInfo) -> Unit,
        propagateFactWithRefinement: (FactRefiner, FinalFactAp, TraceInfo) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor, TraceInfo) -> Unit,
        sideEffect: (Sequent.SideEffect) -> Unit
    ) {
        when (currentInst) {
            is JIRAssignInst -> {
                sequentFlowAssign(
                    currentInst.rhv, currentInst.lhv, factAp,
                    unchanged,
                    { propagateFact(it, TraceInfo.Flow) },
                    { f, a -> propagateFactWithAccessorExclude(f, a, TraceInfo.Flow) }
                )
            }

            is JIRReturnInst -> {
                val access = currentInst.returnValue?.let { accessPathBase(it) }
                propagateExitFact(
                    initialFacts, AccessPathBase.Return,
                    access, factAp, unchanged, propagateFactWithRefinement, sideEffect
                )
            }

            is JIRThrowInst -> {
                val access = accessPathBase(currentInst.throwable)
                propagateExitFact(
                    initialFacts, AccessPathBase.Exception,
                    access, factAp, unchanged, propagateFactWithRefinement, sideEffect
                )
            }

            else -> {
                unchanged()
            }
        }
    }

    private fun propagateExitFact(
        initialFacts: Set<InitialFactAp>?,
        exitBase: AccessPathBase,
        access: AccessPathBase?,
        factAp: FinalFactAp,
        unchanged: () -> Unit,
        propagateFactWithRefinement: (FactRefiner, FinalFactAp, TraceInfo) -> Unit,
        sideEffect: (Sequent.SideEffect) -> Unit
    ) {
        val refiner = FactRefiner()

        val currentFact = if (access == factAp.base) {
            factAp.rebase(exitBase).also { propagateFactWithRefinement(refiner, it, TraceInfo.Flow) }
        } else {
            factAp
        }

        val resultFacts = mutableListOf<Pair<FinalFactAp, TraceInfo>>(currentFact to TraceInfo.Flow)
        resultFacts += applyMethodExitSourceRules(exitBase, currentFact, refiner)

        while (resultFacts.isNotEmpty()) {
            val (resultFact, factTrace) = resultFacts.removeLast()

            val (factsToDrop, newSources) = applyMethodExitSinkRules(exitBase, resultFact, initialFacts, sideEffect, refiner)
            resultFacts.addAll(newSources)

            val propagatedFact = resultFact.dropFinalFacts(factsToDrop)
                ?.dropArgumentsLocalTaintMarks(initialFacts != null && initialFacts.isEmpty())

            if (propagatedFact == factAp) {
                unchanged()
            } else if (propagatedFact != null) {
                propagateFactWithRefinement(refiner, propagatedFact, factTrace)
            }
        }

    }

    private fun sequentFlowAssign(
        assignFrom: JIRExpr,
        assignTo: JIRValue,
        currentFactAp: FinalFactAp,
        unchanged: () -> Unit,
        propagateFact: (FinalFactAp) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor) -> Unit
    ) {
        var fact = currentFactAp

        val assignFromAccess = when (assignFrom) {
            is JIRCastExpr -> MethodFlowFunctionUtils.mkAccess(assignFrom.operand)
                ?.apply { fact = filterFactBaseType(assignFrom.type, fact) ?: return }
                ?: return

            is JIRImmediate -> MethodFlowFunctionUtils.mkAccess(assignFrom)
                ?.apply { fact = filterFactBaseType(assignFrom.type, fact) ?: return }
                ?: return

            is JIRArrayAccess -> MethodFlowFunctionUtils.mkAccess(assignFrom)
                ?.apply { fact = filterFactBaseType(assignFrom.array.type, fact) ?: return }
                ?: return

            is JIRFieldRef -> MethodFlowFunctionUtils.mkAccess(assignFrom)
                ?.apply { fact = filterFactBaseType(assignFrom.instance?.type, fact) ?: return }
                ?.apply { fact = filterFactBaseType(assignFrom.field.enclosingType, fact) ?: return }
                ?: return

            else -> null
        }

        val assignToAccess = when (assignTo) {
            is JIRImmediate -> MethodFlowFunctionUtils.mkAccess(assignTo)
                ?.apply { fact = filterFactBaseType(assignTo.type, fact) ?: return }
                ?: return

            is JIRArrayAccess -> MethodFlowFunctionUtils.mkAccess(assignTo)
                ?.apply { fact = filterFactBaseType(assignTo.array.type, fact) ?: return }
                ?: return

            is JIRFieldRef -> MethodFlowFunctionUtils.mkAccess(assignTo)
                ?.apply { fact = filterFactBaseType(assignTo.instance?.type, fact) ?: return }
                ?.apply { fact = filterFactBaseType(assignTo.field.enclosingType, fact) ?: return }
                ?: return

            else -> error("Assign to complex value: $assignTo")
        }

        val factModified = fact != currentFactAp
        val onUnchanged: (FinalFactAp) -> Unit = if (factModified) propagateFact else { _ -> unchanged() }

        when {
            assignFromAccess?.accessor != null -> {
                check(assignToAccess.accessor == null) { "Complex assignment: $assignTo = $assignFrom" }
                fieldRead(
                    assignToAccess.base, assignFromAccess.base, assignFromAccess.accessor, fact,
                    onUnchanged, propagateFact, propagateFactWithAccessorExclude
                )
            }

            assignToAccess.accessor != null -> {
                fieldWrite(
                    assignToAccess.base, assignToAccess.accessor, assignFromAccess?.base, fact,
                    onUnchanged, propagateFact, propagateFactWithAccessorExclude
                )
            }

            else -> simpleAssign(assignToAccess.base, assignFromAccess?.base, fact, onUnchanged, propagateFact)
        }
    }

    private fun MethodFlowFunctionUtils.Access.filterFactBaseType(
        expectedType: JIRType?,
        factAp: FinalFactAp
    ): FinalFactAp? {
        if (factAp.base != this.base || expectedType == null) return factAp
        return factTypeChecker.filterFactByLocalType(expectedType, factAp)
    }

    private fun simpleAssign(
        assignTo: AccessPathBase,
        assignFrom: AccessPathBase?,
        factAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp) -> Unit,
    ) {
        if (assignTo == assignFrom) {
            unchanged(factAp)
            return
        }

        // Assign can't overwrite fact
        if (assignTo != factAp.base) {
            unchanged(factAp)
        }

        if (assignFrom == factAp.base) {
            propagateFact(factAp.rebase(assignTo))
        }
    }

    private fun fieldRead(
        assignTo: AccessPathBase,
        instance: AccessPathBase,
        accessor: Accessor,
        factAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor) -> Unit
    ) {
        if (!factAp.mayReadField(instance, accessor)) {
            // Fact is irrelevant to current reading
            unchanged(factAp)
            return
        }

        if (factAp.isAbstract() && accessor !in factAp.exclusions) {
            val nonAbstractAp = factAp.removeAbstraction()
            if (nonAbstractAp != null) {
                fieldRead(
                    assignTo, instance, accessor, nonAbstractAp,
                    unchanged, propagateFact, propagateFactWithAccessorExclude
                )
            }

            propagateAbstractFactWithFieldExcluded(factAp, accessor, propagateFactWithAccessorExclude)

            return
        }

        check(factAp.startsWithAccessor(accessor))

        val newAp = factAp.readFieldTo(newBase = assignTo, field = accessor)
        propagateFact(newAp)

        // Assign can't overwrite fact
        if (assignTo != factAp.base) {
            unchanged(factAp)
        }
    }

    private fun fieldWrite(
        instance: AccessPathBase,
        accessor: Accessor,
        assignFrom: AccessPathBase?,
        factAp: FinalFactAp,
        unchanged: (FinalFactAp) -> Unit,
        propagateFact: (FinalFactAp) -> Unit,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor) -> Unit
    ) {
        if (assignFrom == instance) {
            if (factAp.base != instance) {
                // Fact is irrelevant to current writing
                unchanged(factAp)
                return
            } else {
                /**
                 * a.x = a | f(a)
                 * -------------------
                 * b = a | f(a), f(b)
                 * a.x = b | f(b), f(b -> a.x), f(a -> a / {x})
                 */

                val auxiliaryBase = AccessPathBase.LocalVar(-1) // b
                check(auxiliaryBase != instance)

                fieldWrite(
                    instance = instance,
                    accessor = accessor,
                    assignFrom = auxiliaryBase,
                    factAp = factAp.rebase(auxiliaryBase), // f(b)
                    unchanged = {
                        if (it.base != auxiliaryBase) {
                            unchanged(it)
                        }
                    },
                    propagateFact = {
                        if (it.base != auxiliaryBase) {
                            propagateFact(it)
                        }
                    },
                    propagateFactWithAccessorExclude = { f, a ->
                        if (f.base != auxiliaryBase) {
                            propagateFactWithAccessorExclude(f, a)
                        }
                    }
                )

                fieldWrite(
                    instance = instance,
                    accessor = accessor,
                    assignFrom = auxiliaryBase,
                    factAp = factAp, // f(a)
                    unchanged = {
                        if (it.base != auxiliaryBase) {
                            unchanged(it)
                        }
                    },
                    propagateFact = {
                        if (it.base != auxiliaryBase) {
                            propagateFact(it)
                        }
                    },
                    propagateFactWithAccessorExclude = { f, a ->
                        if (f.base != auxiliaryBase) {
                            propagateFactWithAccessorExclude(f, a)
                        }
                    }
                )

                return
            }
        }

        if (factAp.base == assignFrom) {
            // Original rhs fact
            unchanged(factAp)

            // New lhs fact
            val newAp = factAp.writeToField(newBase = instance, field = accessor)
            propagateFact(newAp)

            analysisContext.aliasAnalysis?.forEachAliasAtStatement(currentInst, newAp) { aliased ->
                propagateFact(aliased)
            }

            return
        }

        // We have fact on lhs and NO fact on the rhs -> remove fact from lhs

        // todo hack: keep fact on the array elements
        if (factAp.base == instance && accessor is ElementAccessor) {
            propagateFact(factAp)
            return
        }

        if (!factAp.mayRemoveAfterWrite(instance, accessor)) {
            // Fact is irrelevant to current writing
            unchanged(factAp)
            return
        }

        if (factAp.isAbstract() && accessor !in factAp.exclusions) {
            val nonAbstractAp = factAp.removeAbstraction()
            if (nonAbstractAp != null) {
                fieldWrite(
                    instance, accessor, assignFrom, nonAbstractAp,
                    unchanged, propagateFact, propagateFactWithAccessorExclude
                )
            }

            propagateAbstractFactWithFieldExcluded(factAp, accessor, propagateFactWithAccessorExclude)

            return
        }

        check(factAp.startsWithAccessor(accessor))

        val newAp = factAp.clearField(accessor) ?: return
        propagateFact(newAp)
    }

    private fun propagateAbstractFactWithFieldExcluded(
        factAp: FinalFactAp,
        accessor: Accessor,
        propagateFactWithAccessorExclude: (FinalFactAp, Accessor) -> Unit
    ) {
        val abstractAp = apManager.createAbstractAp(factAp.base, factAp.exclusions)
        propagateFactWithAccessorExclude(abstractAp, accessor)

        analysisContext.aliasAnalysis?.forEachAliasAtStatement(currentInst, abstractAp) { aliased ->
            propagateFactWithAccessorExclude(aliased, accessor)
        }
    }

    private fun applyMethodExitSinkRules(
        methodResult: AccessPathBase, fact: FinalFactAp,
        initialFacts: Set<InitialFactAp>?,
        sideEffect: (Sequent.SideEffect) -> Unit,
        refiner: FactRefiner
    ): Pair<List<InitialFactAp>, List<Pair<FinalFactAp, TraceInfo>>> = with(analysisContext.taint) {
        val config = taintConfig as TaintRulesProvider
        val sinkRules = config.sinkRulesForMethodExit(currentInst.location.method, currentInst, fact, initialFacts).toList()
        if (sinkRules.isEmpty()) return emptyList<InitialFactAp>() to emptyList()

        val resultFact = if (fact.base == methodResult) fact.rebase(AccessPathBase.Return) else fact
        val conditionFactReader = FinalFactReader(resultFact, apManager)

        val valueResolver = CalleePositionToJIRValueResolver(currentInst.location.method)
        val conditionRewriter = JIRMarkAwareConditionRewriter(
            valueResolver,
            analysisContext.factTypeChecker
        )

        val sourceEvaluator = TaintSourceActionEvaluator(
            apManager, ExclusionSet.Universe,
            analysisContext.factTypeChecker, returnValueType = null,
        )

        val allEvaluatedFacts = hashSetOf<InitialFactAp>()
        val factsAfterSink = mutableListOf<Pair<FinalFactAp, TraceInfo>>()

        val markAfterAnyFieldResolver = initialFacts?.let {
            createMarkAfterFieldsResolver(analysisContext.methodEntryPoint, it) { i, k ->
                sideEffect(Sequent.FactSideEffect(i, k))
            }
        }

        sinkRules.applyRuleWithAssumptions(
            apManager, conditionRewriter,
            listOf(conditionFactReader),
            markAfterAnyFieldResolver = markAfterAnyFieldResolver,
            condition = { condition },
            storeAssumptions = { rule, facts ->
                storeInfo {
                    taintSinkTracker.addSinkRuleAssumptions(rule, currentInst, facts)
                }
            },
            currentAssumptions = { rule ->
                taintSinkTracker.currentSinkRuleAssumptions(rule, currentInst)
            }
        ) { rule, rawEvaluatedFacts ->
            val evaluatedFacts = rawEvaluatedFacts.map {
                if (it.base == AccessPathBase.Return) it.rebase(methodResult) else it
            }

            allEvaluatedFacts += evaluatedFacts

            if (rule.trackFactsReachAnalysisEnd.isEmpty()) {
                storeInfo {
                    taintSinkTracker.addVulnerability(
                        analysisContext.methodEntryPoint, evaluatedFacts.toHashSet(),
                        currentInst, rule,
                        vulnerabilityTriggerPosition = VulnerabilityTriggerPosition.AFTER_INST
                    )
                }
                
                return@applyRuleWithAssumptions
            }
            
            val requiredEndFacts = hashSetOf<FinalFactAp>()
            rule.trackFactsReachAnalysisEnd.forEach { action ->
                sourceEvaluator.evaluate(rule, action).onSome { facts ->
                    facts.forEach { f ->
                        requiredEndFacts += f

                        val trace = TraceInfo.Rule(rule, action)
                        factsAfterSink += f to trace
                    }
                }
            }

            storeInfo {
                taintSinkTracker.addUnconditionalVulnerabilityWithEndFactRequirement(
                    analysisContext.methodEntryPoint, currentInst, rule, requiredEndFacts
                )
            }
        }

        refiner.add(conditionFactReader)

        // todo: hack to drop global state var after exit sink
        val factsToDrop = allEvaluatedFacts.filter { it.base is AccessPathBase.ClassStatic }
        factsToDrop to factsAfterSink
    }

    private fun applyMethodExitSourceRules(
        methodResult: AccessPathBase, fact: FinalFactAp?, refiner: FactRefiner?,
    ): List<Pair<FinalFactAp, TraceInfo>> {
        val config = analysisContext.taint.taintConfig as TaintRulesProvider
        val sourceRules = config.exitSourceRulesForMethod(currentInst.location.method, currentInst, fact).toList()
        if (sourceRules.isEmpty()) return emptyList()

        val conditionFactReaders = if (fact != null) {
            val resultFact = if (fact.base == methodResult) fact.rebase(AccessPathBase.Return) else fact
            val conditionFactReader = FinalFactReader(resultFact, apManager)
            listOf(conditionFactReader)
        } else {
            emptyList()
        }

        val valueResolver = CalleePositionToJIRValueResolver(currentInst.location.method)
        val conditionRewriter = JIRMarkAwareConditionRewriter(
            valueResolver,
            analysisContext.factTypeChecker
        )

        val exclusion = fact?.exclusions ?: ExclusionSet.Universe
        val sourceEvaluator = TaintSourceActionEvaluator(
            apManager, exclusion, analysisContext.factTypeChecker, returnValueType = null,
        )

        val result = mutableListOf<Pair<FinalFactAp, TraceInfo>>()

        sourceRules.applyRuleWithAssumptions(
            apManager,
            conditionRewriter,
            emptySet(),
            conditionFactReaders,
            markAfterAnyFieldResolver = null, //
            condition = { condition },
            storeAssumptions = { _, _ ->  },
            currentAssumptions = { emptySet() },
            currentAssumptionPreconditions = { _, _ -> emptyList() },
            applyRule = { rule, evaluatedFacts ->
                // unconditional sources handled with zero fact
                if (evaluatedFacts.isEmpty() && fact != null) return@applyRuleWithAssumptions

                for (action in rule.actionsAfter) {
                    sourceEvaluator.evaluate(rule, action).onSome { facts ->
                        val trace = TraceInfo.Rule(rule, action)
                        facts.mapTo(result) { it to trace }
                    }
                }
            },
            applyRuleWithAssumptions = { _, _ -> TODO("Assumptions impossible here") }
        )

        refiner?.let { ref ->
            conditionFactReaders.forEach { ref.add(it) }
        }

        return result
    }

    private fun MutableSet<Sequent>.applyUnconditionalSources() {
        if (currentInst is JIRReturnInst) {
            applyMethodExitSourceRules(AccessPathBase.Return, fact = null, refiner = null).forEach { (fact, trace) ->
                this += Sequent.ZeroToFact(fact, trace)
            }
        }

        if (currentInst !is JIRAssignInst) return

        val rhvFieldRef = currentInst.rhv as? JIRFieldRef ?: return
        val field = rhvFieldRef.field.field
        if (!field.isStatic) return

        val config = analysisContext.taint.taintConfig as TaintRulesProvider
        val sourceRules = config.sourceRulesForStaticField(field, currentInst, fact = null).toList()
        if (sourceRules.isEmpty()) return

        val lhv = accessPathBase(currentInst.lhv) ?: return

        val sourceEvaluator = TaintSourceActionEvaluator(
            apManager, ExclusionSet.Universe, factTypeChecker, returnValueType = null
        )

        for (sourceRule in sourceRules) {
            if (sourceRule.condition !is ConstantTrue) {
                TODO("Field source with complex condition")
            }

            for (action in sourceRule.actionsAfter) {
                sourceEvaluator.evaluate(sourceRule, action).onSome { evaluatedFacts ->
                    val trace = TraceInfo.Rule(sourceRule, action)

                    evaluatedFacts.mapTo(this) {
                        if (it.base !is AccessPathBase.Return) {
                            TODO("Field source with non-result assign")
                        }

                        Sequent.ZeroToFact(it.rebase(lhv), trace)
                    }
                }
            }
        }
    }

    private fun FinalFactAp.dropFinalFacts(facts: List<InitialFactAp>): FinalFactAp? =
        facts.fold(this as FinalFactAp?) { acc, f -> acc?.dropFinalFact(f) }

    private fun FinalFactAp.dropFinalFact(fact: InitialFactAp): FinalFactAp? {
        if (base != fact.base) return this
        val res =  filterFact(FinalFactRemover(fact))
        return res
    }

    private class FinalFactRemover(val fact: InitialFactAp) : FactTypeChecker.FactApFilter {
        override fun check(accessor: Accessor): FilterResult {
            val nextFact = fact.readAccessor(accessor)
                ?: return FilterResult.Accept

            if (nextFact.startsWithAccessor(FinalAccessor)) {
                return FilterResult.FilterNext(FinalAccessorRemover)
            }

            return FilterResult.FilterNext(FinalFactRemover(nextFact))
        }
    }

    private object FinalAccessorRemover : FactTypeChecker.FactApFilter {
        override fun check(accessor: Accessor): FilterResult {
            if (accessor is FinalAccessor) return FilterResult.Reject
            return FilterResult.Accept
        }
    }

    // drop argument facts with tainted bases
    private fun FinalFactAp.dropArgumentsLocalTaintMarks(initialFactIsZero: Boolean): FinalFactAp? {
        if (!initialFactIsZero) return this
        if (base !is AccessPathBase.Argument && base !is AccessPathBase.This) return this
        return filterFact(TaintMarkRemover(analysisContext.taintMarksAssignedOnMethodEnter))
    }

    private class TaintMarkRemover(
        val marksToRemove: Set<TaintMarkAccessor>
    ) : FactTypeChecker.FactApFilter {
        override fun check(accessor: Accessor): FilterResult {
            if (accessor !is TaintMarkAccessor || accessor !in marksToRemove) return FilterResult.Accept
            return FilterResult.Reject
        }
    }

    private inline fun storeInfo(body: () -> Unit) {
        if (generateTrace) return
        body()
    }

    private class FactRefiner {
        private var refinement: ExclusionSet = ExclusionSet.Empty
        val hasRefinement: Boolean get() = refinement !is ExclusionSet.Empty

        fun add(reader: FinalFactReader) {
            if (reader.hasRefinement) {
                refinement = refinement.union(reader.getRefinement())
            }
        }

        fun refineFact(factAp: InitialFactAp): InitialFactAp {
            if (!hasRefinement) return factAp
            return factAp.replaceExclusions(factAp.exclusions.union(refinement))
        }

        fun refineFact(factAp: FinalFactAp): FinalFactAp {
            if (!hasRefinement) return factAp
            return factAp.replaceExclusions(factAp.exclusions.union(refinement))
        }
    }
}
