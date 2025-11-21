package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.JIRArrayType
import org.opentaint.ir.api.jvm.JIRBoundedWildcard
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRParameter
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.JIRTypeVariable
import org.opentaint.ir.api.jvm.JIRUnboundWildcard
import org.opentaint.ir.api.jvm.cfg.JIRArgument
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.ext.objectType
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.ClassStatic
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.PositionAccessor
import org.opentaint.dataflow.configuration.jvm.PositionResolver
import org.opentaint.dataflow.configuration.jvm.PositionWithAccess
import org.opentaint.dataflow.configuration.jvm.Result
import org.opentaint.dataflow.configuration.jvm.This
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.accessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.taint.PositionAccess
import org.opentaint.dataflow.jvm.util.thisInstance
import org.opentaint.util.Maybe
import org.opentaint.util.fmap
import org.opentaint.util.toMaybe

class JIRCallPositionToAccessPathResolver(
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
        is Argument -> callExpr.args.getOrNull(position.index)?.base().toMaybe()
        This -> (callExpr as? JIRInstanceCallExpr)?.instance?.base().toMaybe()
        Result -> returnValue?.base().toMaybe()
        is PositionWithAccess -> resolvePosition(position.base).fmap { pos ->
            val accessor = when (val a = position.access) {
                PositionAccessor.ElementAccessor -> ElementAccessor
                is PositionAccessor.FieldAccessor -> FieldAccessor(a.className, a.fieldName, a.fieldType)
                PositionAccessor.AnyFieldAccessor -> {
                    // force loop in access path
                    return@fmap PositionAccess.Complex(
                        PositionAccess.Complex(pos, AnyAccessor),
                        AnyAccessor
                    )
                }
            }
            PositionAccess.Complex(pos, accessor)
        }

        is ClassStatic -> PositionAccess.Simple(AccessPathBase.ClassStatic(position.className)).toMaybe()
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
        is Argument -> callExpr.args.getOrNull(position.index).toMaybe()
        This -> (callExpr as? JIRInstanceCallExpr)?.instance.toMaybe()
        Result -> returnValue.toMaybe()
        is PositionWithAccess -> Maybe.none() // todo?
        is ClassStatic -> Maybe.none()
    }
}

class CalleePositionToJIRValueResolver(
    private val method: JIRMethod
) : PositionResolver<Maybe<JIRValue>> {
    private val cp = method.enclosingClass.classpath

  override fun resolve(position: Position): Maybe<JIRValue> = when (position) {
        is Argument -> cp.getArgument(method.parameters[position.index]).toMaybe()
        This -> method.thisInstance.toMaybe()
        is PositionWithAccess -> resolve(position.base).fmap { TODO() }
        // Inapplicable callee positions
        Result -> Maybe.none()
        is ClassStatic -> Maybe.none()
    }

    private fun JIRClasspath.getArgument(param: JIRParameter): JIRArgument? {
        val t = findTypeOrNull(param.type.typeName) ?: return null
        return JIRArgument.of(param.index, param.name, t)
    }
}

class JIRMethodPositionBaseTypeResolver(private val method: JIRMethod) : PositionResolver<JIRType?> {
    private val cp = method.enclosingClass.classpath

    override fun resolve(position: Position): JIRType? = when (position) {
        This -> method.enclosingClass.toType()
        is Argument -> method.parameters.getOrNull(position.index)?.let { cp.findTypeOrNull(it.type.typeName) }
        Result -> cp.findTypeOrNull(method.returnType.typeName)
        is PositionWithAccess -> resolve(position.base)
        is ClassStatic -> null
    }
}
