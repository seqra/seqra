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
import org.opentaint.ir.taint.configuration.TaintCleaner
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.ir.taint.configuration.TaintEntryPointSource
import org.opentaint.ir.taint.configuration.TaintMethodSink
import org.opentaint.ir.taint.configuration.TaintMethodSource
import org.opentaint.ir.taint.configuration.TaintPassThrough
import org.opentaint.dataflow.ap.ifds.PassActionEvaluator
import org.opentaint.dataflow.ap.ifds.SourceActionEvaluator
import org.opentaint.dataflow.ap.ifds.TaintRulesProvider
import org.opentaint.dataflow.ifds.Maybe
import org.opentaint.dataflow.ifds.maybeFlatMap

object TaintConfigUtils {
    fun sinkRules(config: TaintRulesProvider, method: CommonMethod) =
        config.rulesForMethod(method).filterIsInstance<TaintMethodSink>()

    fun <T> applySourceConfig(
        config: TaintRulesProvider,
        method: CommonMethod,
        conditionEvaluator: ConditionVisitor<Boolean>,
        taintActionEvaluator: SourceActionEvaluator<T>
    ) = applyAssignMark<TaintMethodSource, T>(
        config, method, conditionEvaluator, taintActionEvaluator,
        TaintMethodSource::condition, TaintMethodSource::actionsAfter
    )

    fun <T> applyEntryPointConfig(
        config: TaintRulesProvider,
        method: CommonMethod,
        conditionEvaluator: ConditionVisitor<Boolean>,
        taintActionEvaluator: SourceActionEvaluator<T>
    ) = applyAssignMark<TaintEntryPointSource, T>(
        config, method, conditionEvaluator, taintActionEvaluator,
        TaintEntryPointSource::condition, TaintEntryPointSource::actionsAfter
    )

    private inline fun <reified T : TaintConfigurationItem, R> applyAssignMark(
        config: TaintRulesProvider,
        method: CommonMethod,
        conditionEvaluator: ConditionVisitor<Boolean>,
        taintActionEvaluator: SourceActionEvaluator<R>,
        condition: (T) -> Condition,
        actionsAfter: (T) -> List<Action>
    ): Maybe<List<R>> =
        config.rulesForMethod(method)
            .filterIsInstance<T>()
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
        config.rulesForMethod(method)
            .filterIsInstance<TaintPassThrough>()
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
        config.rulesForMethod(method)
            .filterIsInstance<TaintCleaner>()
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
