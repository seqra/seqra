package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.configuration.CommonTaintConfigurationItem
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
import org.opentaint.dataflow.taint.PositionTypeResolver
import org.opentaint.dataflow.taint.TaintCleanActionEvaluator

interface ConditionEvaluator<T> {
    fun eval(condition: Condition): T
}

class JIRTaintCleanActionEvaluator(
    private val positionTypeResolver: PositionTypeResolver,
) {
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
        val cleaned = evaluator.removeFinalFact(initialFact, variable, mark, rule, action, action.reach)

        val positionType = positionTypeResolver.resolve(variable)
        if (positionType?.typeName != STRING) {
            return cleaned
        }

        val stringBytesVar = PositionWithAccess(action.position, stringBytes).resolveAp()
        return cleaned.flatMap { f ->
            evaluator.removeFinalFact(f, stringBytesVar, mark, rule, action, action.reach)
        }
    }

    companion object {
        private const val STRING = "java.lang.String"

        // todo: fix in config?
        // string bytes virtual field fully reflects the string content.
        // So, if we clean string, we should clean its byte content
        private val stringBytes = PositionAccessor.FieldAccessor(STRING, "<string-bytes>", "byte[]")
    }
}

fun Position.resolveBaseAp(): AccessPathBase = when (this) {
    is Argument -> AccessPathBase.Argument(index)
    is This -> AccessPathBase.This
    is Result -> AccessPathBase.Return
    is ClassStatic -> AccessPathBase.ClassStatic
    is PositionWithAccess -> base.resolveBaseAp()
}

fun Position.resolveAp(): PositionAccess = resolveAp(resolveBaseAp())

fun Position.resolveAp(baseAp: AccessPathBase): PositionAccess {
    check(baseAp != AccessPathBase.Zero && baseAp !is AccessPathBase.CleanerTemporary) {
        "Internal base is not a JVM position: $baseAp"
    }
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
    PositionAccessor.AnyFieldAccessor -> AnyAccessor
    PositionAccessor.ElementAccessor -> ElementAccessor
    is PositionAccessor.FieldAccessor -> FieldAccessor(className, fieldName, fieldType)
}
