package org.opentaint.dataflow.go.rules

import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.PositionModifier
import org.opentaint.dataflow.configuration.jvm.serialized.beforeFirstAnyField
import org.opentaint.dataflow.configuration.mkAnd
import org.opentaint.dataflow.configuration.mkFalse
import org.opentaint.dataflow.configuration.mkOr
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.configuration.simplify
import org.opentaint.dataflow.go.GoFunctionSignature
import org.opentaint.ir.go.api.GoIRNamedType
import org.opentaint.ir.go.type.GoIRArrayType
import org.opentaint.ir.go.type.GoIRMapType
import org.opentaint.ir.go.type.GoIRNamedTypeRef
import org.opentaint.ir.go.type.GoIRPointerType
import org.opentaint.ir.go.type.GoIRSliceType
import org.opentaint.ir.go.type.GoIRStructType
import org.opentaint.ir.go.type.GoIRTupleType
import org.opentaint.ir.go.type.GoIRType

internal fun GoSerializedCondition?.resolveToRuleCondition(signature: GoFunctionSignature): CommonCondition<GoRuleCondition> {
    val resolved = this?.resolveImpl(signature) ?: mkTrue()
    return resolved.simplify()
}

private fun GoSerializedCondition.resolveImpl(signature: GoFunctionSignature): CommonCondition<GoRuleCondition> = when (this) {
    GoSerializedCondition.True -> mkTrue()

    is GoSerializedCondition.And -> mkAnd(allOf.map { it.resolveImpl(signature) })
    is GoSerializedCondition.Or -> mkOr(anyOf.map { it.resolveImpl(signature) })
    is GoSerializedCondition.Not -> CommonCondition.Not(not.resolveImpl(signature))

    is GoSerializedCondition.ContainsMark -> {
        val (position, hasAnyField) = pos.beforeFirstAnyField()
        position.resolveAny(signature, PositionBaseWithModifiers::resolve) {
            if (hasAnyField) {
                GoRuleCondition.ContainsMarkOnAnyAccessor(it, tainted)
            } else {
                GoRuleCondition.ContainsMark(it, tainted)
            }
        }
    }

    is GoSerializedCondition.ConstantCmp -> {
        val typedValue = value.toTypedConstantValue()
        pos.resolveAny(signature, PositionBase::resolve) {
            when (cmp) {
                GoSerializedCondition.ConstantCmpType.Eq -> GoRuleCondition.ConstantEq(it, typedValue)
                GoSerializedCondition.ConstantCmpType.Lt -> GoRuleCondition.ConstantLt(it, typedValue)
                GoSerializedCondition.ConstantCmpType.Gt -> GoRuleCondition.ConstantGt(it, typedValue)
            }
        }
    }

    is GoSerializedCondition.ConstantMatches -> pos.resolveAny(signature, PositionBase::resolve) {
        GoRuleCondition.ConstantMatches(it, Regex(pattern))
    }

    is GoSerializedCondition.IsNull -> pos.resolveAny(signature, PositionBase::resolve) {
        GoRuleCondition.IsNull(it)
    }

    is GoSerializedCondition.IsConstant -> pos.resolveAny(signature, PositionBase::resolve) {
        GoRuleCondition.IsConstant(it)
    }

    is GoSerializedCondition.NumberOfArgs -> if (n == signature.arity) mkTrue() else mkFalse()

    is GoSerializedCondition.IsType -> pos.resolveAnyBool(signature, PositionBase::resolve) {
        resolveIsType(signature, it, typeName)
    }
}

private fun <T, R> T.resolveAny(
    signature: GoFunctionSignature,
    resolve: T.(GoFunctionSignature) -> List<R>,
    body: (R) -> GoRuleCondition
): CommonCondition<GoRuleCondition> =
    mkOr(resolve(signature).map { CommonCondition.Atom(body(it)) })

private fun <T, R> T.resolveAnyBool(
    signature: GoFunctionSignature,
    resolve: T.(GoFunctionSignature) -> List<R>,
    body: (R) -> Boolean
): CommonCondition<GoRuleCondition> =
    mkOr(resolve(signature).map { if (body(it)) mkTrue() else mkFalse() })

private fun GoSerializedCondition.ConstantValue.toTypedConstantValue(): ConstantValue =
    when (type) {
        GoSerializedCondition.ConstantType.Str -> ConstantStringValue(value)
        GoSerializedCondition.ConstantType.Bool -> ConstantBooleanValue(value.toBoolean())
        GoSerializedCondition.ConstantType.Int -> ConstantIntValue(value.toInt())
    }

fun PositionBase.resolve(signature: GoFunctionSignature): List<Position.Simple> = when (this) {
    is PositionBase.AnyArgument -> {
        // todo: any arg classifier
        List(signature.arity) { Position.Argument(it) }
    }

    is PositionBase.Argument -> {
        val i = idx
        if (i == null) {
            List(signature.arity) { Position.Argument(it) }
        } else if (i >= signature.arity) {
            emptyList()
        } else {
            listOf(Position.Argument(i))
        }
    }

    is PositionBase.ClassStatic -> listOf(Position.ClassStatic(className))
    is PositionBase.Result -> listOf(Position.Result)
    is PositionBase.This -> if (signature.hasReceiver) listOf(Position.This) else emptyList()
}

fun PositionBaseWithModifiers.resolve(signature: GoFunctionSignature): List<Position> {
    val base = base.resolve(signature)
    val modifiers = when (this) {
        is PositionBaseWithModifiers.BaseOnly -> return base
        is PositionBaseWithModifiers.WithModifiers -> modifiers
    }

    val accessors = modifiers.resolveUntyped()
    if (accessors != null) {
        return base.map { b -> mkPosition(b, accessors) }
    }

    return base.mapNotNull {
        val types = signature.positionType(it)

        val accessors = types.firstNotNullOfOrNull { type ->
            modifiers.resolveWithType(type)
        }

        if (accessors == null) return@mapNotNull null

        mkPosition(it, accessors)
    }
}

private fun GoFunctionSignature.positionType(pos: Position.Simple): List<GoIRType> = when (pos) {
    is Position.Argument -> listOfNotNull(paramTypes.getOrNull(pos.index))
    is Position.ClassStatic -> emptyList()
    is Position.Result -> {
        val types = mutableListOf(resultType)
        if (resultType is GoIRTupleType) {
            // note: function return types are always represented as a tuple
            val elements = resultType.elements
            if (elements.size == 1) {
                types += elements.first()
            }
        }
        types
    }
    is Position.This -> listOfNotNull(receiverType)
}

private fun resolveIsType(signature: GoFunctionSignature, pos: Position.Simple, typeName: String): Boolean {
    val positionTypes = signature.positionType(pos)
    return positionTypes.any { matchesType(it, typeName) }
}

private fun mkPosition(
    base: Position.Simple,
    accessors: List<PositionAccessor>
): Position = accessors.fold(base as Position) { ac, accessor ->
    PositionWithAccess(ac, accessor)
}

private fun List<PositionModifier>.resolveUntyped(): List<PositionAccessor>? {
    return map { mod ->
        when (mod) {
            is PositionModifier.ArrayElement -> PositionAccessor.ElementAccessor
            is PositionModifier.Field -> {
                if (mod.className.isEmpty()) {
                    return null
                }
                PositionAccessor.FieldAccessor(mod.className, mod.fieldName, mod.fieldType)
            }

            is PositionModifier.AnyField -> error("AnyField must be specialized before resolving Position")
        }
    }
}

private fun List<PositionModifier>.resolveWithType(baseType: GoIRType): List<PositionAccessor>? {
    var type = baseType
    val accessors = mutableListOf<PositionAccessor>()
    for (mod in this) {
        type = type.unwrapPtr()

        var named: GoIRNamedType? = null
        if (type is GoIRNamedTypeRef) {
            named = type.namedType
            type = named.underlying
        }

        when (mod) {
            is PositionModifier.ArrayElement -> {
                accessors += PositionAccessor.ElementAccessor

                type = when (type) {
                    is GoIRArrayType -> type.elem
                    is GoIRSliceType -> type.elem
                    is GoIRMapType -> type.value
                    else -> return null
                }
            }

            is PositionModifier.Field -> {
                val tupleElement = tupleFieldNamePattern.matchEntire(mod.fieldName)
                if (tupleElement != null) {
                    if (type !is GoIRTupleType) return null

                    val tupleElementIdx = tupleElement.destructured.component1().toInt()
                    val elementType = type.elements.getOrNull(tupleElementIdx) ?: return null

                    accessors += PositionAccessor.FieldAccessor("tuple", "$$tupleElementIdx", elementType.typeName)
                    type = elementType
                    continue
                }

                if (type !is GoIRStructType) return null
                val field = type.fields.firstOrNull { it.name == mod.fieldName } ?: return null

                val structName = named?.fullName ?: type.namedType?.fullName ?: return null
                accessors += PositionAccessor.FieldAccessor(structName, field.name, field.type.typeName)

                type = field.type
            }

            is PositionModifier.AnyField -> error("Unused")
        }
    }
    return accessors
}

private val tupleFieldNamePattern = Regex("tuple\\$(\\d+)")

private fun GoIRType.unwrapPtr(): GoIRType = when (this) {
    is GoIRPointerType -> elem.unwrapPtr()
    else -> this
}
