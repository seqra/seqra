package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.accessPathBase
import org.opentaint.ir.api.jvm.JIRArrayType
import org.opentaint.ir.api.jvm.JIRBoundedWildcard
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.JIRTypeVariable
import org.opentaint.ir.api.jvm.JIRUnboundWildcard
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.ext.objectType
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.ir.taint.configuration.AnyArgument
import org.opentaint.ir.taint.configuration.Argument
import org.opentaint.ir.taint.configuration.Position
import org.opentaint.ir.taint.configuration.PositionAccessor
import org.opentaint.ir.taint.configuration.PositionResolver
import org.opentaint.ir.taint.configuration.PositionWithAccess
import org.opentaint.ir.taint.configuration.Result
import org.opentaint.ir.taint.configuration.ResultAnyElement
import org.opentaint.ir.taint.configuration.This
import org.opentaint.dataflow.ifds.Maybe
import org.opentaint.dataflow.ifds.flatFmap
import org.opentaint.dataflow.ifds.fmap
import org.opentaint.dataflow.ifds.toMaybe
import org.opentaint.dataflow.jvm.util.getArgument
import org.opentaint.dataflow.jvm.util.thisInstance

sealed interface PositionAccess {
    data class Simple(val base: AccessPathBase) : PositionAccess
    data class Complex(val base: PositionAccess, val accessor: Accessor) : PositionAccess
}

class CallPositionToAccessPathResolver(
    private val callExpr: JIRCallExpr,
    private val returnValue: JIRImmediate?,
    private val readArgElementsIfArray: Boolean = false,
) : PositionResolver<Maybe<List<PositionAccess>>> {
    private val resolverCache = hashMapOf<Position, Maybe<List<PositionAccess>>>()

    override fun resolve(position: Position): Maybe<List<PositionAccess>> = resolverCache.getOrPut(position) {
        resolvePosition(position).fmap { resolvedPos ->
            when (position) {
                is Result -> if (returnValue != null && returnValue.type.mayBeArray()) {
                    val withArrayAccess = PositionAccess.Complex(resolvedPos, ElementAccessor)
                    listOf(resolvedPos, withArrayAccess)
                } else {
                    listOf(resolvedPos)
                }

                is Argument -> {
                    val arg = callExpr.args[position.index]
                    if (readArgElementsIfArray && arg.type.mayBeArray()) {
                        val withArrayAccess = PositionAccess.Complex(resolvedPos, ElementAccessor)
                        listOf(resolvedPos, withArrayAccess)
                    } else {
                        listOf(resolvedPos)
                    }
                }

                else -> listOf(resolvedPos)
            }
        }
    }

    private fun resolvePosition(position: Position): Maybe<PositionAccess> = when (position) {
        AnyArgument -> Maybe.none()
        is Argument -> callExpr.args.getOrNull(position.index)?.base().toMaybe()
        This -> (callExpr as? JIRInstanceCallExpr)?.instance?.base().toMaybe()
        Result -> returnValue?.base().toMaybe()
        ResultAnyElement -> returnValue?.arrayElem().toMaybe()
        is PositionWithAccess -> resolvePosition(position.base).fmap { pos ->
            val accessor = when (val a = position.access) {
                PositionAccessor.ElementAccessor -> ElementAccessor
                is PositionAccessor.FieldAccessor -> FieldAccessor(a.className, a.fieldName, a.fieldType)
            }
            PositionAccess.Complex(pos, accessor)
        }
    }

    private fun JIRValue.base() = (this as JIRImmediate).base()

    private fun JIRImmediate.base(): PositionAccess? =
        accessPathBase(this)?.let { PositionAccess.Simple(it) }

    private fun JIRImmediate.arrayElem(): PositionAccess? =
        accessPathBase(this)?.let { PositionAccess.Complex(PositionAccess.Simple(it), ElementAccessor) }

    private fun JIRType.mayBeArray(): Boolean = when (this) {
        is JIRArrayType -> true
        is JIRClassType -> this == this.classpath.objectType
        is JIRTypeVariable -> bounds.all { it.mayBeArray() }

        // todo: check wildcards
        is JIRUnboundWildcard, is JIRBoundedWildcard -> true

        else -> false
    }
}

class CallPositionToJIRValueResolver(
    private val callExpr: JIRCallExpr,
    private val returnValue: JIRImmediate?
) : PositionResolver<Maybe<JIRValue>> {
    override fun resolve(position: Position): Maybe<JIRValue> = when (position) {
        AnyArgument -> Maybe.none()
        is Argument -> callExpr.args.getOrNull(position.index).toMaybe()
        This -> (callExpr as? JIRInstanceCallExpr)?.instance.toMaybe()
        Result -> returnValue.toMaybe()
        ResultAnyElement -> Maybe.none()
        is PositionWithAccess -> Maybe.none() // todo?
    }
}

class CalleePositionToAccessPath : PositionResolver<Maybe<List<PositionAccess>>> {
    override fun resolve(position: Position): Maybe<List<PositionAccess>> = when (position) {
        AnyArgument -> Maybe.none()
        is Argument -> listOf(PositionAccess.Simple(AccessPathBase.Argument(position.index))).toMaybe()
        This -> listOf(PositionAccess.Simple(AccessPathBase.This)).toMaybe()

        is PositionWithAccess -> resolve(position.base).flatFmap { pos ->
            val accessor = when (val a = position.access) {
                PositionAccessor.ElementAccessor -> ElementAccessor
                is PositionAccessor.FieldAccessor -> FieldAccessor(a.className, a.fieldName, a.fieldType)
            }
            listOf(PositionAccess.Complex(pos, accessor))
        }

        // Inapplicable callee positions
        Result -> Maybe.none()
        ResultAnyElement -> Maybe.none()
    }
}

class CalleePositionToJIRValueResolver(
    private val method: JIRMethod
) : PositionResolver<Maybe<JIRValue>> {
    private val cp = method.enclosingClass.classpath

    override fun resolve(position: Position): Maybe<JIRValue> = when (position) {
        AnyArgument -> Maybe.none()
        is Argument -> cp.getArgument(method.parameters[position.index]).toMaybe()
        This -> method.thisInstance.toMaybe()
        is PositionWithAccess -> resolve(position.base).fmap { TODO() }
        // Inapplicable callee positions
        Result -> Maybe.none()
        ResultAnyElement -> Maybe.none()
    }
}

class MethodPositionBaseTypeResolver(private val method: JIRMethod) : PositionResolver<JIRType?> {
    val cp = method.enclosingClass.classpath

    override fun resolve(position: Position): JIRType? = when (position) {
        This -> method.enclosingClass.toType()
        is Argument -> method.parameters.getOrNull(position.index)?.let { cp.findTypeOrNull(it.type.typeName) }
        Result -> cp.findTypeOrNull(method.returnType.typeName)
        ResultAnyElement -> cp.findTypeOrNull(method.returnType.typeName)
        is PositionWithAccess -> resolve(position.base)
        AnyArgument -> error("Unexpected position: $position")
    }
}
