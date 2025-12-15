package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker.FactWithPreconditions
import org.opentaint.dataflow.configuration.jvm.Action
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.configuration.jvm.Condition
import org.opentaint.dataflow.configuration.jvm.CopyAllMarks
import org.opentaint.dataflow.configuration.jvm.CopyMark
import org.opentaint.dataflow.configuration.jvm.RemoveAllMarks
import org.opentaint.dataflow.configuration.jvm.RemoveMark
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.configuration.jvm.TaintEntryPointSource
import org.opentaint.dataflow.jvm.ap.ifds.taint.ConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.FactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.FinalFactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.InitialFactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.PassActionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.SourceActionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintCleanActionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.util.Maybe
import org.opentaint.util.maybeFlatMap

object TaintConfigUtils {
    fun sinkRules(config: TaintRulesProvider, method: CommonMethod, statement: CommonInst) =
        config.sinkRulesForMethod(method, statement)

    fun <T> applyEntryPointConfig(
        config: TaintRulesProvider,
        method: CommonMethod,
        conditionEvaluator: ConditionEvaluator<Boolean>,
        taintActionEvaluator: SourceActionEvaluator<T>
    ) = applyAssignMark<TaintEntryPointSource, T>(
        config.entryPointRulesForMethod(method), conditionEvaluator, taintActionEvaluator,
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
                .filterIsInstance<AssignMark>()
                .maybeFlatMap { taintActionEvaluator.evaluate(item, it) }
        }

    fun <T> applyPassThrough(
        config: TaintRulesProvider,
        method: CommonMethod,
        statement: CommonInst,
        conditionEvaluator: ConditionEvaluator<Boolean>,
        taintActionEvaluator: PassActionEvaluator<T>
    ): Maybe<List<T>> =
        config.passTroughRulesForMethod(method, statement)
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
        initialFact: FinalFactReader?,
        conditionEvaluator: ConditionEvaluator<Boolean>,
        taintActionEvaluator: TaintCleanActionEvaluator
    ): FinalFactReader? =
        config.cleanerRulesForMethod(method, statement)
            .filter { conditionEvaluator.eval(it.condition) }
            .fold(initialFact) { startFact, rule ->
                rule.actionsAfter.fold(startFact) { fact, action ->
                    when (action) {
                        is RemoveMark -> taintActionEvaluator.evaluate(fact, action)
                        is RemoveAllMarks -> taintActionEvaluator.evaluate(fact, action)
                        else -> fact
                    }
                }
            }

    inline fun <T : TaintConfigurationItem> List<T>.applyRuleWithAssumptions(
        apManager: ApManager,
        conditionRewriter: JIRMarkAwareConditionRewriter,
        conditionFactReaders: List<FactReader>,
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
        condition: T.() -> Condition,
        storeAssumptions: (T, Map<InitialFactAp, Set<InitialFactAp>>) -> Unit,
        currentAssumptions: (T) -> Set<InitialFactAp>,
        currentAssumptionPreconditions: (T, List<InitialFactAp>) -> List<FactWithPreconditions>,
        applyRule: (T, List<InitialFactAp>) -> Unit,
        applyRuleWithAssumptions: (T, List<FactWithPreconditions>) -> Unit
    ) {
        val conditionEvaluator = JIRFactAwareConditionEvaluator(conditionFactReaders)

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

            val conditionEvaluatorWithAssumptions = JIRFactAwareConditionEvaluator(assumptionReaders)
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
