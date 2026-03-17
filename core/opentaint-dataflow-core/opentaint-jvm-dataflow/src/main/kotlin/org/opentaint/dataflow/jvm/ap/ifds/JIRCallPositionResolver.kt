package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.ClassStatic
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.PositionResolver
import org.opentaint.dataflow.configuration.jvm.PositionWithAccess
import org.opentaint.dataflow.configuration.jvm.Result
import org.opentaint.dataflow.configuration.jvm.This
import org.opentaint.dataflow.jvm.util.isVararg
import org.opentaint.dataflow.jvm.util.thisInstance
import org.opentaint.dataflow.jvm.util.varargParamIdx
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRParameter
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.cfg.JIRArgument
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.ext.toType

sealed interface CallPositionValue {
    data object None : CallPositionValue
    data class Value(val value: JIRValue) : CallPositionValue
    data class VarArgValue(val value: JIRValue) : CallPositionValue
}

class CallPositionToJIRValueResolver(
    private val callExpr: JIRCallExpr,
    private val returnValue: JIRImmediate?
) : PositionResolver<CallPositionValue> {
    override fun resolve(position: Position): CallPositionValue = when (position) {
        is Argument -> callExpr.args.getOrNull(position.index)
            .toCallArgValue(callExpr.method.method, position.index)

        is This -> (callExpr as? JIRInstanceCallExpr)?.instance.toCallValue()
        is Result -> returnValue.toCallValue()

        is PositionWithAccess, // todo?
        is ClassStatic -> CallPositionValue.None
    }
}

class CalleePositionToJIRValueResolver(
    private val method: JIRMethod
) : PositionResolver<CallPositionValue> {
    private val cp = method.enclosingClass.classpath

  override fun resolve(position: Position): CallPositionValue = when (position) {
        is Argument -> method.parameters.getOrNull(position.index)
            ?.let { cp.getArgument(it) }
            .toCallArgValue(method, position.index)

        is This -> method.thisInstance.toCallValue()

        // todo
        is PositionWithAccess -> CallPositionValue.None

        // Inapplicable callee positions
        is Result,
        is ClassStatic -> CallPositionValue.None
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

private fun JIRValue?.toCallArgValue(method: JIRMethod, argumentIdx: Int): CallPositionValue {
    val value = this ?: return CallPositionValue.None
    if (method.isVararg() && argumentIdx == method.varargParamIdx()) {
        return CallPositionValue.VarArgValue(value)
    }
    return CallPositionValue.Value(value)
}

private fun JIRValue?.toCallValue(): CallPositionValue =
    this?.let { CallPositionValue.Value(it) } ?: CallPositionValue.None
