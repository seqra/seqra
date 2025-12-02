package org.opentaint.dataflow.jvm.ap.ifds

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
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.ClassStatic
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.PositionResolver
import org.opentaint.dataflow.configuration.jvm.PositionWithAccess
import org.opentaint.dataflow.configuration.jvm.Result
import org.opentaint.dataflow.configuration.jvm.This
import org.opentaint.dataflow.jvm.util.thisInstance
import org.opentaint.util.Maybe
import org.opentaint.util.fmap
import org.opentaint.util.toMaybe

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
