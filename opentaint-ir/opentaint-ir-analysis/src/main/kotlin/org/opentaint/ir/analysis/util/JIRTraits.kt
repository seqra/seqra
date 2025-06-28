package org.opentaint.ir.analysis.util

import org.opentaint.ir.analysis.ifds.AccessPath
import org.opentaint.ir.analysis.ifds.ElementAccessor
import org.opentaint.ir.analysis.ifds.FieldAccessor
import org.opentaint.ir.analysis.util.getArgument
import org.opentaint.ir.analysis.util.toPathOrNull
import org.opentaint.ir.api.common.CommonMethodParameter
import org.opentaint.ir.api.common.CommonProject
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRParameter
import org.opentaint.ir.api.jvm.cfg.JIRArgument
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRBool
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRCastExpr
import org.opentaint.ir.api.jvm.cfg.JIRConstant
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInt
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRStringConstant
import org.opentaint.ir.api.jvm.cfg.JIRThis
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.cfg.values
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.ir.taint.configuration.ConstantBooleanValue
import org.opentaint.ir.taint.configuration.ConstantIntValue
import org.opentaint.ir.taint.configuration.ConstantStringValue
import org.opentaint.ir.taint.configuration.ConstantValue
import org.opentaint.ir.analysis.util.callee as _callee
import org.opentaint.ir.analysis.util.getArgument as _getArgument
import org.opentaint.ir.analysis.util.getArgumentsOf as _getArgumentsOf
import org.opentaint.ir.analysis.util.thisInstance as _thisInstance
import org.opentaint.ir.analysis.util.toPath as _toPath
import org.opentaint.ir.analysis.util.toPathOrNull as _toPathOrNull
import org.opentaint.ir.api.jvm.ext.cfg.callExpr as _callExpr

/**
 * JVM-specific extensions for analysis.
 *
 * ### Usage:
 * ```
 * class MyClass {
 *     companion object : JIRTraits
 * }
 * ```
 */
interface JIRTraits : Traits<JIRMethod, JIRInst> {

    override val JIRMethod.thisInstance: JIRThis
        get() = _thisInstance

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER")
    override val JIRMethod.isConstructor: Boolean
        get() = isConstructor

    override fun CommonExpr.toPathOrNull(): AccessPath? {
        check(this is JIRExpr)
        return _toPathOrNull()
    }

    override fun CommonValue.toPathOrNull(): AccessPath? {
        check(this is JIRValue)
        return _toPathOrNull()
    }

    override fun CommonValue.toPath(): AccessPath {
        check(this is JIRValue)
        return _toPath()
    }

    override val CommonCallExpr.callee: JIRMethod
        get() {
            check(this is JIRCallExpr)
            return _callee
        }

    override fun CommonProject.getArgument(param: CommonMethodParameter): JIRArgument? {
        check(this is JIRClasspath)
        check(param is JIRParameter)
        return _getArgument(param)
    }

    override fun CommonProject.getArgumentsOf(method: JIRMethod): List<JIRArgument> {
        check(this is JIRClasspath)
        return _getArgumentsOf(method)
    }

    override fun CommonValue.isConstant(): Boolean {
        check(this is JIRValue)
        return this is JIRConstant
    }

    override fun CommonValue.eqConstant(constant: ConstantValue): Boolean {
        check(this is JIRValue)
        return when (constant) {
            is ConstantBooleanValue -> {
                this is JIRBool && value == constant.value
            }

            is ConstantIntValue -> {
                this is JIRInt && value == constant.value
            }

            is ConstantStringValue -> {
                // TODO: if 'value' is not string, convert it to string and compare with 'constant.value'
                this is JIRStringConstant && value == constant.value
            }
        }
    }

    override fun CommonValue.ltConstant(constant: ConstantValue): Boolean {
        check(this is JIRValue)
        return when (constant) {
            is ConstantIntValue -> {
                this is JIRInt && value < constant.value
            }

            else -> error("Unexpected constant: $constant")
        }
    }

    override fun CommonValue.gtConstant(constant: ConstantValue): Boolean {
        check(this is JIRValue)
        return when (constant) {
            is ConstantIntValue -> {
                this is JIRInt && value > constant.value
            }

            else -> error("Unexpected constant: $constant")
        }
    }

    override fun CommonValue.matches(pattern: String): Boolean {
        check(this is JIRValue)
        val s = this.toString()
        val re = pattern.toRegex()
        return re.matches(s)
    }

    override fun JIRInst.getCallExpr(): CommonCallExpr? {
        return _callExpr
    }

    override fun CommonExpr.getValues(): Set<CommonValue> {
        check(this is JIRExpr)
        return values
    }

    override fun JIRInst.getOperands(): List<JIRExpr> {
        return operands
    }

    // Ensure that all methods are default-implemented in the interface itself:
    companion object : JIRTraits
}

val JIRMethod.thisInstance: JIRThis
    get() = JIRThis(enclosingClass.toType())

val JIRCallExpr.callee: JIRMethod
    get() = method.method

fun JIRExpr.toPathOrNull(): AccessPath? = when (this) {
    is JIRValue -> toPathOrNull()
    is JIRCastExpr -> operand.toPathOrNull()
    else -> null
}

fun JIRValue.toPathOrNull(): AccessPath? = when (this) {
    is JIRImmediate -> AccessPath(this, emptyList())

    is JIRThis -> AccessPath(this, emptyList())

    is JIRArrayAccess -> {
        array.toPathOrNull()?.let {
            it + ElementAccessor
        }
    }

    is JIRFieldRef -> {
        val instance = instance
        if (instance == null) {
            require(field.isStatic) { "Expected static field" }
            AccessPath(null, listOf(FieldAccessor(field.name, isStatic = true)))
        } else {
            instance.toPathOrNull()?.let {
                it + FieldAccessor(field.name)
            }
        }
    }

    else -> null
}

fun JIRValue.toPath(): AccessPath {
    return toPathOrNull() ?: error("Unable to build access path for value $this")
}

fun JIRClasspath.getArgument(param: JIRParameter): JIRArgument? {
    val t = findTypeOrNull(param.type.typeName) ?: return null
    return JIRArgument.of(param.index, param.name, t)
}

fun JIRClasspath.getArgumentsOf(method: JIRMethod): List<JIRArgument> {
    return method.parameters.map { getArgument(it)!! }
}
