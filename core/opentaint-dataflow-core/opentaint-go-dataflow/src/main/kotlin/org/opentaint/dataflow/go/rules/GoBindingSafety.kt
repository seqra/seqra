package org.opentaint.dataflow.go.rules

import org.opentaint.dataflow.configuration.go.serialized.GoSerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.PositionModifier

/** A sufficient purity check. Unknown/type-dependent bindings keep the original
 * complete bind-then-filter path, including its validation failures. */
internal object GoBindingSafety {
    fun position(position: PositionBaseWithModifiers): Boolean = when (position) {
        is PositionBaseWithModifiers.BaseOnly -> base(position.base)
        is PositionBaseWithModifiers.WithModifiers -> base(position.base) && position.modifiers.all {
            it !is PositionModifier.Field || it.className.isNotEmpty()
        }
    }

    fun base(position: PositionBase): Boolean = position !is PositionBase.ClassStatic

    fun fieldPosition(position: PositionBaseWithModifiers): Boolean =
        (position.base is PositionBase.Result || position.base is PositionBase.This) && GoBindingSafety.position(position)

    fun condition(condition: GoSerializedCondition?, field: Boolean = false): Boolean {
        fun pos(position: PositionBaseWithModifiers) = if (field) fieldPosition(position) else GoBindingSafety.position(position)
        fun simple(position: PositionBase) = base(position) &&
            (!field || position is PositionBase.Result || position is PositionBase.This)
        return when (condition) {
            null, GoSerializedCondition.True -> true
            is GoSerializedCondition.And -> condition.allOf.all { GoBindingSafety.condition(it, field) }
            is GoSerializedCondition.Or -> condition.anyOf.all { GoBindingSafety.condition(it, field) }
            is GoSerializedCondition.Not -> GoBindingSafety.condition(condition.not, field)
            is GoSerializedCondition.ContainsMark -> pos(condition.pos)
            is GoSerializedCondition.ContainsMarkOnAnyAccessor -> pos(condition.pos)
            is GoSerializedCondition.NumberOfArgs -> true
            is GoSerializedCondition.IsNull -> simple(condition.pos)
            is GoSerializedCondition.IsConstant -> simple(condition.pos)
            is GoSerializedCondition.ConstantCmp -> simple(condition.pos) &&
                (condition.value.type != GoSerializedCondition.ConstantType.Int || condition.value.value.toIntOrNull() != null)
            is GoSerializedCondition.ConstantMatches -> simple(condition.pos) &&
                runCatching { Regex(condition.pattern) }.isSuccess
            // Type resolution can consult external IR state. Keep its old lane.
            is GoSerializedCondition.IsType -> false
        }
    }
}
