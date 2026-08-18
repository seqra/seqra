package org.opentaint.dataflow.taint

import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.taint.TaintAnalysisContext.RuleWithCondition
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker.FactWithPreconditions
import org.opentaint.dataflow.util.cartesianProductMapTo

abstract class TaintUtil<C, Src, Sink, Trace>(val apManager: ApManager) {
    abstract fun sourceAssumptionsManager(): RuleAssumptionsManager<Src>
    abstract fun sinkAssumptionsManager(): RuleAssumptionsManager<Sink>

    interface RuleAssumptionsManager<T> {
        fun storeAssumptions(rule: T, assumptions: Map<InitialFactAp, Set<InitialFactAp>>)
        fun currentAssumptions(rule: T): Set<InitialFactAp>
        fun currentAssumptionPreconditions(rule: T, assumptions: List<InitialFactAp>): List<FactWithPreconditions> =
            assumptions.map { FactWithPreconditions(it, emptyList()) }
    }

    abstract fun conditionFact(factReader: FinalFactReader): List<FinalFactReader>

    abstract fun handleReachedSink(rule: Sink, factReader: FinalFactReader?, evaluatedFacts: List<InitialFactAp>)

    open fun patchSinkConditionFactReader(factReaders: List<FinalFactReader>): List<FactReader> = factReaders

    fun applySinkRules(
        sinkRules: List<RuleWithCondition<Sink>>,
        factReader: FinalFactReader?,
        markAfterAnyFieldResolver: FactWithMarkAfterAnyAccessorResolver?,
    ) {
        if (sinkRules.isEmpty()) return

        val normalConditionFactReaders = factReader?.let { conditionFact(it) }.orEmpty()
        val conditionFactReaders = patchSinkConditionFactReader(normalConditionFactReaders)

        sinkRules.applyRuleWithAssumptions(
            apManager,
            conditionFactReaders = conditionFactReaders,
            markAfterAnyFieldResolver = markAfterAnyFieldResolver,
            mkAssumptionsManager = sinkAssumptionsManager(),
        ) { rule, evaluatedFacts ->
            handleReachedSink(rule, factReader, evaluatedFacts)
            return@applyRuleWithAssumptions
        }

        factReader?.updateRefinement(normalConditionFactReaders)
    }


    abstract fun applySourceAction(
        rule: Src,
        sourceEvaluator: TaintSourceActionEvaluator,
        createFinalFact: (FinalFactAp, Trace) -> Unit,
    )

    fun applySourceRules(
        sourceRules: List<RuleWithCondition<Src>>,
        initialFacts: Set<InitialFactAp>,
        factReader: FinalFactReader?,
        exclusion: ExclusionSet,
        createFinalFact: (FinalFactAp, Trace) -> Unit,
        createEdge: (InitialFactAp, FinalFactAp, Trace) -> Unit,
        createNDEdge: (Set<InitialFactAp>, FinalFactAp, Trace) -> Unit,
        markAfterAnyFieldResolver: FactWithMarkAfterAnyAccessorResolver? = null,
    ) {
        if (sourceRules.isEmpty()) return

        val conditionFactReaders = factReader?.let { conditionFact(it) }.orEmpty()

        val sourceEvaluator = TaintSourceActionEvaluator(
            apManager, exclusion,
        )

        sourceRules.applyRuleWithAssumptions(
            apManager,
            initialFacts,
            conditionFactReaders,
            markAfterAnyFieldResolver = markAfterAnyFieldResolver,
            assumptionsManager = sourceAssumptionsManager(),
            applyRule = { rule, evaluatedFacts ->
                // unconditional sources handled with zero fact
                if (evaluatedFacts.isEmpty() && factReader != null) return@applyRuleWithAssumptions

                applySourceAction(rule, sourceEvaluator, createFinalFact)
            },
            applyRuleWithAssumptions = { rule, factsWithPreconditions ->
                val factPreconditions = factsWithPreconditions.map { it.preconditions }
                factPreconditions.cartesianProductMapTo { preconditions ->
                    val nonZeroPreconditions = hashSetOf<InitialFactAp>()
                    for (precondition in preconditions) {
                        if (precondition.isEmpty()) continue

                        nonZeroPreconditions.addAll(precondition)
                    }

                    if (nonZeroPreconditions.isEmpty()) {
                        check(initialFacts.isEmpty()) {
                            "Unexpected zero precondition"
                        }

                        applySourceAction(rule, sourceEvaluator, createFinalFact)
                        return@cartesianProductMapTo
                    }

                    if (nonZeroPreconditions.size == 1) {
                        val precondition = nonZeroPreconditions.first()

                        if (initialFacts.isEmpty()) {
                            // Here initial fact ends with taint mark and exclusion can be ignored
                            val newInitial = precondition.replaceExclusions(ExclusionSet.Empty)
                            applySourceAction(rule, sourceEvaluator) { fact, trace ->
                                createEdge(newInitial, fact.replaceExclusions(ExclusionSet.Empty), trace)
                            }

                            return@cartesianProductMapTo
                        }

                        if (initialFacts.size == 1) {
                            val initialFact = initialFacts.first()

                            check(precondition == initialFact.replaceExclusions(ExclusionSet.Universe)) {
                                "Unexpected fact precondition"
                            }

                            applySourceAction(rule, sourceEvaluator, createFinalFact)
                            return@cartesianProductMapTo
                        }

                        error("Multiple initial facts not expected here")
                    }

                    applySourceAction(rule, sourceEvaluator) { fact, trace ->
                        createNDEdge(
                            nonZeroPreconditions,
                            fact.replaceExclusions(ExclusionSet.Universe),
                            trace
                        )
                    }
                }
            }
        )

        factReader?.updateRefinement(conditionFactReaders)
    }

    inline fun <T> List<RuleWithCondition<T>>.applyRuleWithAssumptions(
        apManager: ApManager,
        conditionFactReaders: List<FactReader>,
        markAfterAnyFieldResolver: FactWithMarkAfterAnyAccessorResolver?,
        mkAssumptionsManager: RuleAssumptionsManager<T>,
        applyRule: (T, List<InitialFactAp>) -> Unit,
    ) {
        applyRuleWithAssumptions(
            apManager = apManager,
            initialFacts = emptySet(),
            conditionFactReaders = conditionFactReaders,
            markAfterAnyFieldResolver = markAfterAnyFieldResolver,
            assumptionsManager = mkAssumptionsManager,
            applyRule = applyRule,
            applyRuleWithAssumptions = { rule, facts ->
                applyRule(rule, facts.map { it.fact })
            }
        )
    }

    inline fun <T> List<RuleWithCondition<T>>.applyRuleWithAssumptions(
        apManager: ApManager,
        initialFacts: Set<InitialFactAp>,
        conditionFactReaders: List<FactReader>,
        assumptionsManager: RuleAssumptionsManager<T>,
        markAfterAnyFieldResolver: FactWithMarkAfterAnyAccessorResolver?,
        applyRule: (T, List<InitialFactAp>) -> Unit,
        applyRuleWithAssumptions: (T, List<FactWithPreconditions>) -> Unit
    ) {
        val conditionEvaluator = TaintFactAwareConditionEvaluator(conditionFactReaders, markAfterAnyFieldResolver)

        for (ruleWithCond in this) {
            val simplifiedCondition = ruleWithCond.condition
            val rule = ruleWithCond.rule

            val conditionExpr = when {
                simplifiedCondition.isFalse -> continue
                simplifiedCondition.isTrue -> {
                    applyRule(ruleWithCond.rule, emptyList())
                    continue
                }

                else -> simplifiedCondition.expr
            }

            val ruleApplicable = conditionEvaluator.evalWithAssumptionsCheck(conditionExpr)

            if (ruleApplicable) {
                applyRule(rule, conditionEvaluator.facts())
                continue
            }

            // no evaluated taint marks
            val assumptionExpr = conditionEvaluator.assumptionExpr() ?: continue

            val facts = conditionEvaluator.facts()
            val factPrecondition = initialFacts.mapTo(hashSetOf()) {
                it.replaceExclusions(ExclusionSet.Universe)
            }.ifEmpty { emptySet() }

            val newAssumptions = facts.associateWith { factPrecondition }

            assumptionsManager.storeAssumptions(rule, newAssumptions)

            val assumptions = assumptionsManager.currentAssumptions(rule)
            val assumptionReaders = assumptions.map { InitialFactReader(it, apManager) }

            val conditionEvaluatorWithAssumptions = TaintFactAwareConditionEvaluator(
                assumptionReaders,
                markAfterAnyAccessorResolver = null // note: mark resolved on first eval
            )
            if (!conditionEvaluatorWithAssumptions.evalWithAssumptionsCheck(assumptionExpr)) {
                continue
            }

            val currentFactPreconditions = facts.map { FactWithPreconditions(it, listOf(factPrecondition)) }

            val assumedFacts = conditionEvaluatorWithAssumptions.facts()

            if (assumedFacts.size == 1) {
                addRuleWithAssumption(
                    assumptionsManager,
                    applyRuleWithAssumptions,
                    rule,
                    assumedFacts,
                    currentFactPreconditions
                )
                continue
            }

            check(assumedFacts.size > 1) { "Multiple assumptions expected" }

            val assumptionExprDnf = assumptionExpr.explodeToDNF().distinct()
            for (cube in assumptionExprDnf) {
                val expr = TaintMarkAwareConditionExpr.And(cube.literals.toTypedArray())
                if (!conditionEvaluatorWithAssumptions.evalWithAssumptionsCheck(expr)) {
                    continue
                }

                val cubeAssumedFacts = conditionEvaluatorWithAssumptions.facts()
                addRuleWithAssumption(
                    assumptionsManager,
                    applyRuleWithAssumptions,
                    rule,
                    cubeAssumedFacts,
                    currentFactPreconditions
                )
            }
        }
    }

    inline fun <T> addRuleWithAssumption(
        assumptionsManager: RuleAssumptionsManager<T>,
        applyRuleWithAssumptions: (T, List<FactWithPreconditions>) -> Unit,
        rule: T,
        assumedFacts: List<InitialFactAp>,
        currentFactPreconditions: List<FactWithPreconditions>,
    ) {
        val assumedFactsPreconditions = assumptionsManager.currentAssumptionPreconditions(rule, assumedFacts)
        val allFacts = assumedFactsPreconditions + currentFactPreconditions
        applyRuleWithAssumptions(rule, allFacts)
    }

    private fun FinalFactReader.updateRefinement(conditionFactReaders: List<FinalFactReader>) {
        conditionFactReaders.forEach { updateRefinement(it) }
    }
}
