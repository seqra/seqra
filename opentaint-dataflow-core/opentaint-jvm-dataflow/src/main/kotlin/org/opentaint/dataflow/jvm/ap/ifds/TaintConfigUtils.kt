package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.dataflow.configuration.jvm.Action
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.configuration.jvm.Condition
import org.opentaint.dataflow.configuration.jvm.CopyAllMarks
import org.opentaint.dataflow.configuration.jvm.CopyMark
import org.opentaint.dataflow.configuration.jvm.PositionResolver
import org.opentaint.dataflow.configuration.jvm.RemoveAllMarks
import org.opentaint.dataflow.configuration.jvm.RemoveMark
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.configuration.jvm.TaintEntryPointSource
import org.opentaint.util.Maybe
import org.opentaint.util.maybeFlatMap
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.jvm.ap.ifds.taint.ConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.FactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.FinalFactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.InitialFactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.PassActionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.SourceActionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintCleanActionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider

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
        valueResolver: PositionResolver<Maybe<JIRValue>>,
        conditionFactReaders: List<FactReader>,
        typeChecker: JIRFactTypeChecker,
        condition: T.() -> Condition,
        storeAssumptions: (T, Set<InitialFactAp>) -> Unit,
        currentAssumptions: (T) -> Set<InitialFactAp>,
        applyRule: (T, List<InitialFactAp>) -> Unit,
    ) {
        val conditionEvaluator = JIRFactAwareConditionEvaluator(
            conditionFactReaders, valueResolver, typeChecker
        )

        for (rule in this) {
            val ruleCondition = rule.condition()
            val ruleApplicable = conditionEvaluator.evalWithAssumptionsCheck(ruleCondition)

            if (ruleApplicable) {
                applyRule(rule, conditionEvaluator.facts())
                continue
            }

            // no evaluated taint marks
            if (!conditionEvaluator.assumptionsPossible()) continue

            val facts = conditionEvaluator.facts()
            storeAssumptions(rule, facts.toSet())

            val assumptions = currentAssumptions(rule)
            val assumptionReaders = assumptions.map { InitialFactReader(it, apManager) }

            val conditionEvaluatorWithAssumptions = JIRFactAwareConditionEvaluator(
                assumptionReaders, valueResolver, typeChecker
            )

            if (!conditionEvaluatorWithAssumptions.evalWithAssumptionsCheck(ruleCondition)) {
                continue
            }

            applyRule(rule, conditionEvaluatorWithAssumptions.facts())
        }
    }
}
