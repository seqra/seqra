package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker.FactWithPreconditions
import org.opentaint.dataflow.configuration.jvm.Action
import org.opentaint.dataflow.configuration.jvm.AssignAction
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.configuration.jvm.Condition
import org.opentaint.dataflow.configuration.jvm.ContainsMarkOnAnyField
import org.opentaint.dataflow.configuration.jvm.CopyAllMarks
import org.opentaint.dataflow.configuration.jvm.CopyMark
import org.opentaint.dataflow.configuration.jvm.RemoveAllMarks
import org.opentaint.dataflow.configuration.jvm.RemoveMark
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.configuration.jvm.TaintEntryPointSource
import org.opentaint.dataflow.jvm.ap.ifds.taint.ConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.EvaluatedCleanAction
import org.opentaint.dataflow.jvm.ap.ifds.taint.FactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.FinalFactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.InitialFactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.JIRFactWithMarkAfterAnyFieldResolver
import org.opentaint.dataflow.jvm.ap.ifds.taint.PassActionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.SourceActionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintCleanActionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.util.Maybe
import org.opentaint.util.maybeFlatMap

object TaintConfigUtils {
    fun sinkRules(config: TaintRulesProvider, method: CommonMethod, statement: CommonInst, fact: FactAp?) =
        config.sinkRulesForMethod(method, statement, fact)

    fun <T> applyEntryPointConfig(
        config: TaintRulesProvider,
        method: CommonMethod,
        fact: FactAp?,
        conditionEvaluator: ConditionEvaluator<Boolean>,
        taintActionEvaluator: SourceActionEvaluator<T>
    ) = applyAssignMark<TaintEntryPointSource, T>(
        config.entryPointRulesForMethod(method, fact), conditionEvaluator, taintActionEvaluator,
        TaintEntryPointSource::condition, TaintEntryPointSource::actionsAfter
    )

    private inline fun <reified T : TaintConfigurationItem, R> applyAssignMark(
        rules: Iterable<T>,
        conditionEvaluator: ConditionEvaluator<Boolean>,
        taintActionEvaluator: SourceActionEvaluator<R>,
        condition: (T) -> Condition,
        actionsAfter: (T) -> List<Action>
    ): Maybe<List<R>> = rules
        .filter { conditionEvaluator.eval(condition(it)) }
        .maybeFlatMap { item ->
            actionsAfter(item)
                .filterIsInstance<AssignAction>()
                .maybeFlatMap { taintActionEvaluator.evaluate(item, it) }
        }

    fun <T> applyPassThrough(
        config: TaintRulesProvider,
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        conditionEvaluator: ConditionEvaluator<Boolean>,
        taintActionEvaluator: PassActionEvaluator<T>
    ): Maybe<List<T>> =
        config.passTroughRulesForMethod(method, statement, fact)
            .filter { conditionEvaluator.eval(it.condition) }
            .maybeFlatMap { item ->
                item.actionsAfter.maybeFlatMap {
                    when (it) {
                        is CopyMark -> taintActionEvaluator.evaluate(item, it)
                        is CopyAllMarks -> taintActionEvaluator.evaluate(item, it)
                        else -> Maybe.none()
                    }
                }
            }

    fun applyCleaner(
        config: TaintRulesProvider,
        method: CommonMethod,
        statement: CommonInst,
        initialFact: FinalFactReader,
        conditionEvaluator: ConditionEvaluator<Boolean>,
        taintActionEvaluator: TaintCleanActionEvaluator
    ): List<EvaluatedCleanAction> {
        val rules = config.cleanerRulesForMethod(method, statement, initialFact.factAp)
            .filter { conditionEvaluator.eval(it.condition) }

        return rules.applyCleanerActions(
            evaluator = taintActionEvaluator,
            itemRule = { it },
            itemActions = { it.actionsAfter },
            initial = EvaluatedCleanAction.initial(initialFact)
        )
    }

    inline fun <T> List<T>.applyCleanerActions(
        evaluator: TaintCleanActionEvaluator,
        itemRule: (T) -> TaintConfigurationItem,
        itemActions: (T) -> List<Action>,
        initial: EvaluatedCleanAction
    ): List<EvaluatedCleanAction> {
        val resultFacts = mutableListOf<EvaluatedCleanAction>()
        var unprocessedFacts = listOf(initial)
        for (item in this) {
            if (unprocessedFacts.isEmpty()) continue

            val rule = itemRule(item)
            val actions = itemActions(item)
            for (action in actions) {
                if (unprocessedFacts.isEmpty()) continue

                unprocessedFacts = unprocessedFacts.evaluatedCleanAction(evaluator, rule, action, resultFacts)
            }
        }

        resultFacts.addAll(unprocessedFacts)
        return resultFacts
    }

    fun List<EvaluatedCleanAction>.evaluatedCleanAction(
        evaluator: TaintCleanActionEvaluator,
        rule: TaintConfigurationItem,
        action: Action,
        resultFacts: MutableList<EvaluatedCleanAction>
    ): List<EvaluatedCleanAction> {
        val nextIterationFacts = mutableListOf<EvaluatedCleanAction>()

        for (fact in this) {
            val updatedFacts = when (action) {
                is RemoveMark -> evaluator.evaluate(fact, rule, action)
                is RemoveAllMarks -> evaluator.evaluate(fact, rule, action)
                else -> listOf(fact)
            }

            for (updatedFact in updatedFacts) {
                if (updatedFact.fact == null) {
                    resultFacts.add(updatedFact)
                    continue
                }
                nextIterationFacts.add(updatedFact)
            }
        }
        return nextIterationFacts
    }

    inline fun <T : TaintConfigurationItem> List<T>.applyRuleWithAssumptions(
        apManager: ApManager,
        conditionRewriter: JIRMarkAwareConditionRewriter,
        conditionFactReaders: List<FactReader>,
        markAfterAnyFieldResolver: JIRFactWithMarkAfterAnyFieldResolver?,
        condition: T.() -> Condition,
        storeAssumptions: (T, Map<InitialFactAp, Set<InitialFactAp>>) -> Unit,
        currentAssumptions: (T) -> Set<InitialFactAp>,
        applyRule: (T, List<InitialFactAp>) -> Unit,
    ) {
        applyRuleWithAssumptions(
            apManager = apManager,
            conditionRewriter = conditionRewriter,
            initialFacts = emptySet(),
            conditionFactReaders = conditionFactReaders,
            markAfterAnyFieldResolver = markAfterAnyFieldResolver,
            condition = condition,
            storeAssumptions = storeAssumptions,
            currentAssumptions = currentAssumptions,
            currentAssumptionPreconditions = { _, facts ->
                facts.map { FactWithPreconditions(it, emptyList()) }
            },
            applyRule = applyRule,
            applyRuleWithAssumptions = { rule, facts ->
                applyRule(rule, facts.map { it.fact })
            }
        )
    }

    inline fun <T : TaintConfigurationItem> List<T>.applyRuleWithAssumptions(
        apManager: ApManager,
        conditionRewriter: JIRMarkAwareConditionRewriter,
        initialFacts: Set<InitialFactAp>,
        conditionFactReaders: List<FactReader>,
        markAfterAnyFieldResolver: JIRFactWithMarkAfterAnyFieldResolver?,
        condition: T.() -> Condition,
        storeAssumptions: (T, Map<InitialFactAp, Set<InitialFactAp>>) -> Unit,
        currentAssumptions: (T) -> Set<InitialFactAp>,
        currentAssumptionPreconditions: (T, List<InitialFactAp>) -> List<FactWithPreconditions>,
        applyRule: (T, List<InitialFactAp>) -> Unit,
        applyRuleWithAssumptions: (T, List<FactWithPreconditions>) -> Unit
    ) {
        val conditionEvaluator = JIRFactAwareConditionEvaluator(conditionFactReaders, null)

        for (rule in this) {
            val ruleCondition = rule.condition()

            val simplifiedCondition = conditionRewriter.rewrite(ruleCondition)
            val conditionExpr = when {
                simplifiedCondition.isFalse -> continue
                simplifiedCondition.isTrue -> {
                    applyRule(rule, emptyList())
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
            storeAssumptions(rule, newAssumptions)

            val assumptions = currentAssumptions(rule)
            val assumptionReaders = assumptions.map { InitialFactReader(it, apManager) }

            val conditionEvaluatorWithAssumptions = JIRFactAwareConditionEvaluator(
                assumptionReaders,
                markAfterAnyFieldResolver.takeIf { assumptionExpr.mustCalculateAny() }
            )
            if (!conditionEvaluatorWithAssumptions.evalWithAssumptionsCheck(assumptionExpr)) {
                continue
            }

            val currentFactPreconditions = facts.map { FactWithPreconditions(it, listOf(factPrecondition)) }

            val assumedFacts = conditionEvaluatorWithAssumptions.facts()

            if (assumedFacts.size == 1) {
                addRuleWithAssumption(
                    currentAssumptionPreconditions,
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
                val expr = JIRMarkAwareConditionExpr.And(cube.literals.toTypedArray())
                if (!conditionEvaluatorWithAssumptions.evalWithAssumptionsCheck(expr)) {
                    continue
                }

                val cubeAssumedFacts = conditionEvaluatorWithAssumptions.facts()
                addRuleWithAssumption(
                    currentAssumptionPreconditions,
                    applyRuleWithAssumptions,
                    rule,
                    cubeAssumedFacts,
                    currentFactPreconditions
                )
            }
        }
    }

    inline fun <T : TaintConfigurationItem> addRuleWithAssumption(
        currentAssumptionPreconditions: (T, List<InitialFactAp>) -> List<FactWithPreconditions>,
        applyRuleWithAssumptions: (T, List<FactWithPreconditions>) -> Unit,
        rule: T,
        assumedFacts: List<InitialFactAp>,
        currentFactPreconditions: List<FactWithPreconditions>,
    ) {
        val assumedFactsPreconditions = currentAssumptionPreconditions(rule, assumedFacts)
        val allFacts = assumedFactsPreconditions + currentFactPreconditions
        applyRuleWithAssumptions(rule, allFacts)
    }
}

fun JIRMarkAwareConditionExpr.mustCalculateAny(): Boolean =
    when (this) {
        is JIRMarkAwareConditionExpr.Literal ->
            this is JIRMarkAwareConditionExpr.ContainsMarkOnAnyFieldLiteral

        is JIRMarkAwareConditionExpr.Or ->
            args.any { it.mustCalculateAny() }

        is JIRMarkAwareConditionExpr.And ->
            args.all { it.mustCalculateAny() }
    }

