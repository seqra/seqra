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
import org.opentaint.ir.taint.configuration.PositionResolver
import org.opentaint.ir.taint.configuration.Result
import org.opentaint.ir.taint.configuration.ResultAnyElement
import org.opentaint.ir.taint.configuration.This
import org.opentaint.dataflow.ifds.Maybe
import org.opentaint.dataflow.ifds.toMaybe
import org.opentaint.dataflow.jvm.util.getArgument
import org.opentaint.dataflow.jvm.util.thisInstance

sealed interface PositionAccess {
    val base: AccessPathBase
    data class Base(override val base: AccessPathBase) : PositionAccess
    data class ArrayElement(override val base: AccessPathBase) : PositionAccess
}

class CallPositionToAccessPathResolver(
    private val callExpr: JIRCallExpr,
    private val returnValue: JIRImmediate?,
    private val readArgElementsIfArray: Boolean = false,
) : PositionResolver<Maybe<List<PositionAccess>>> {
    override fun resolve(position: Position): Maybe<List<PositionAccess>> = when (position) {
        AnyArgument -> Maybe.none()
        is Argument -> callExpr.args.getOrNull(position.index)?.let { arg ->
            if (readArgElementsIfArray && arg.type.mayBeArray()) {
                // todo: hack for rules with arrays
                arg.base()?.plus(arg.arrayElem() ?: emptyList())
            } else {
                arg.base()
            }
        }.toMaybe()
        This -> (callExpr as? JIRInstanceCallExpr)?.instance?.base().toMaybe()
        ResultAnyElement -> returnValue?.arrayElem().toMaybe()
        Result -> returnValue?.let {
            if (it.type.mayBeArray()) {
                // todo: hack for rules with arrays
                it.base()?.plus(it.arrayElem() ?: emptyList())
            } else {
                it.base()
            }
        }.toMaybe()
    }

    private fun JIRValue.base() = (this as JIRImmediate).base()

    private fun JIRImmediate.base(): List<PositionAccess>? =
        accessPathBase(this)?.let { listOf(PositionAccess.Base(it)) }

    private fun JIRValue.arrayElem() = (this as JIRImmediate).arrayElem()

    private fun JIRImmediate.arrayElem(): List<PositionAccess>? =
        accessPathBase(this)?.let { listOf(PositionAccess.ArrayElement(it)) }

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
    }
}

class CalleePositionToAccessPath : PositionResolver<Maybe<List<PositionAccess>>> {
    override fun resolve(position: Position): Maybe<List<PositionAccess>> = when (position) {
        AnyArgument -> Maybe.none()
        is Argument -> listOf(PositionAccess.Base(AccessPathBase.Argument(position.index))).toMaybe()
        This -> listOf(PositionAccess.Base(AccessPathBase.This)).toMaybe()
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
        // Inapplicable callee positions
        Result -> Maybe.none()
        ResultAnyElement -> Maybe.none()
    }
}

class MethodPositionTypeResolver(private val method: JIRMethod) : PositionResolver<JIRType?> {
    private val cp = method.enclosingClass.classpath

    override fun resolve(position: Position): JIRType? = when (position) {
        This -> method.enclosingClass.toType()
        is Argument -> method.parameters.getOrNull(position.index)?.let { cp.findTypeOrNull(it.type.typeName) }
        Result -> cp.findTypeOrNull(method.returnType.typeName)
        ResultAnyElement -> cp.findTypeOrNull(method.returnType.typeName)
        AnyArgument -> error("Unexpected position: $position")
    }
}
