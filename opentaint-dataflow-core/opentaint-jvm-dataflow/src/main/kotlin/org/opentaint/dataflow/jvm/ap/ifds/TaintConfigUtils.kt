package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.taint.configuration.Action
import org.opentaint.ir.taint.configuration.AssignMark
import org.opentaint.ir.taint.configuration.Condition
import org.opentaint.ir.taint.configuration.ConditionVisitor
import org.opentaint.ir.taint.configuration.CopyAllMarks
import org.opentaint.ir.taint.configuration.CopyMark
import org.opentaint.ir.taint.configuration.RemoveAllMarks
import org.opentaint.ir.taint.configuration.RemoveMark
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.ir.taint.configuration.TaintEntryPointSource
import org.opentaint.ir.taint.configuration.TaintMethodSource
import org.opentaint.dataflow.ap.ifds.PassActionEvaluator
import org.opentaint.dataflow.ap.ifds.SourceActionEvaluator
import org.opentaint.dataflow.ap.ifds.TaintRulesProvider
import org.opentaint.util.Maybe
import org.opentaint.util.maybeFlatMap

object TaintConfigUtils {
    fun sinkRules(config: TaintRulesProvider, method: CommonMethod) =
        config.sinkRulesForMethod(method)

    fun <T> applySourceConfig(
        config: TaintRulesProvider,
        method: CommonMethod,
        conditionEvaluator: ConditionVisitor<Boolean>,
        taintActionEvaluator: SourceActionEvaluator<T>
    ) = applyAssignMark<TaintMethodSource, T>(
        config.sourceRulesForMethod(method), conditionEvaluator, taintActionEvaluator,
        TaintMethodSource::condition, TaintMethodSource::actionsAfter
    )

    fun <T> applyEntryPointConfig(
        config: TaintRulesProvider,
        method: CommonMethod,
        conditionEvaluator: ConditionVisitor<Boolean>,
        taintActionEvaluator: SourceActionEvaluator<T>
    ) = applyAssignMark<TaintEntryPointSource, T>(
        config.entryPointRulesForMethod(method), conditionEvaluator, taintActionEvaluator,
        TaintEntryPointSource::condition, TaintEntryPointSource::actionsAfter
    )

    private inline fun <reified T : TaintConfigurationItem, R> applyAssignMark(
        rules: Iterable<T>,
        conditionEvaluator: ConditionVisitor<Boolean>,
        taintActionEvaluator: SourceActionEvaluator<R>,
        condition: (T) -> Condition,
        actionsAfter: (T) -> List<Action>
    ): Maybe<List<R>> = rules
        .filter { condition(it).accept(conditionEvaluator) }
        .maybeFlatMap { item ->
            actionsAfter(item)
                .filterIsInstance<AssignMark>()
                .maybeFlatMap { taintActionEvaluator.evaluate(item, it) }
        }

    fun <T> applyPassThrough(
        config: TaintRulesProvider,
        method: CommonMethod,
        conditionEvaluator: ConditionVisitor<Boolean>,
        taintActionEvaluator: PassActionEvaluator<T>
    ): Maybe<List<T>> =
        config.passTroughRulesForMethod(method)
            .filter { it.condition.accept(conditionEvaluator) }
            .maybeFlatMap { item ->
                item.actionsAfter.maybeFlatMap {
                    when (it) {
                        is CopyMark -> taintActionEvaluator.evaluate(item, it)
                        is CopyAllMarks -> taintActionEvaluator.evaluate(item, it)
                        is RemoveMark -> taintActionEvaluator.evaluate(item, it)
                        is RemoveAllMarks -> taintActionEvaluator.evaluate(item, it)
                        else -> Maybe.none()
                    }
                }
            }

    fun <T> applyCleaner(
        config: TaintRulesProvider,
        method: CommonMethod,
        conditionEvaluator: ConditionVisitor<Boolean>,
        taintActionEvaluator: PassActionEvaluator<T>
    ): Maybe<List<T>> =
        config.cleanerRulesForMethod(method)
            .filter { it.condition.accept(conditionEvaluator) }
            .maybeFlatMap { item ->
                item.actionsAfter.maybeFlatMap {
                    when (it) {
                        is RemoveMark -> taintActionEvaluator.evaluate(item, it)
                        is RemoveAllMarks -> taintActionEvaluator.evaluate(item, it)
                        else -> Maybe.none()
                    }
                }
            }
}
