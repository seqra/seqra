package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.configuration.CommonTaintConfigurationItem
import org.opentaint.dataflow.configuration.TaintCleanReach
import org.opentaint.dataflow.configuration.jvm.ActionPosition
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.ClassStatic
import org.opentaint.dataflow.configuration.jvm.Condition
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.PositionAccessor
import org.opentaint.dataflow.configuration.jvm.PositionWithAccess
import org.opentaint.dataflow.configuration.jvm.RemoveAllMarks
import org.opentaint.dataflow.configuration.jvm.RemoveMark
import org.opentaint.dataflow.configuration.jvm.Result
import org.opentaint.dataflow.configuration.jvm.This
import org.opentaint.dataflow.taint.EvaluatedCleanAction
import org.opentaint.dataflow.taint.PositionAccess
import org.opentaint.dataflow.taint.TaintCleanActionEvaluator

interface ConditionEvaluator<T> {
    fun eval(condition: Condition): T
}

class JIRTaintCleanActionEvaluator {
    private val evaluator = TaintCleanActionEvaluator()

    fun evaluate(
        initialFact: EvaluatedCleanAction,
        rule: CommonTaintConfigurationItem,
        action: RemoveAllMarks,
    ): List<EvaluatedCleanAction> {
        val variable = action.position.resolveAp()
        return evaluator.removeAllFacts(initialFact, variable, rule, action)
    }

    fun evaluate(
        initialFact: EvaluatedCleanAction,
        rule: CommonTaintConfigurationItem,
        action: RemoveMark,
    ): List<EvaluatedCleanAction> {
        val variable = action.position.resolveAp()
        val mark = TaintMarkAccessor(action.mark.name)
        return evaluator.removeFinalFact(initialFact, variable, mark, rule, action, action.position.cleanReach())
    }

}

fun ActionPosition.resolveBaseAp(): AccessPathBase = when (this) {
    is ActionPosition.Exact -> position.resolveBaseAp()
    is ActionPosition.AnyAccessorAfter -> position.resolveBaseAp()
}

fun Position.resolveBaseAp(): AccessPathBase = when (this) {
    is Argument -> AccessPathBase.Argument(index)
    is This -> AccessPathBase.This
    is Result -> AccessPathBase.Return
    is ClassStatic -> AccessPathBase.ClassStatic
    is PositionWithAccess -> base.resolveBaseAp()
}

fun ActionPosition.resolveAp(): PositionAccess = when (this) {
    is ActionPosition.Exact -> position.resolveAp()
    is ActionPosition.AnyAccessorAfter -> PositionAccess.Complex(position.resolveAp(), AnyAccessor)
}

fun ActionPosition.cleanReach(): TaintCleanReach = when (this) {
    is ActionPosition.Exact -> TaintCleanReach.Exact
    is ActionPosition.AnyAccessorAfter -> TaintCleanReach.ExactAndAnyField
}


fun Position.resolveAp(): PositionAccess = resolveAp(resolveBaseAp())

fun Position.resolveAp(baseAp: AccessPathBase): PositionAccess {
    return when (this) {
        is Argument,
        is This,
        is Result -> PositionAccess.Simple(baseAp)

        is ClassStatic -> PositionAccess.Complex(
            PositionAccess.Simple(baseAp),
            ClassStaticAccessor(className)
        )

        is PositionWithAccess -> {
            val resolvedBaseAp = base.resolveAp(baseAp)
            val accessor = access.toApAccessor()

            PositionAccess.Complex(resolvedBaseAp, accessor)
        }
    }
}

fun PositionAccessor.toApAccessor() = when(this) {
    PositionAccessor.ElementAccessor -> ElementAccessor
    is PositionAccessor.FieldAccessor -> FieldAccessor(className, fieldName, fieldType)
}
