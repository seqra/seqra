/*
 *  Copyright 2022 Opentaint contributors (opentaint.dev)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opentaint.dataflow.jvm.util

import org.opentaint.ir.api.common.CommonMethodParameter
import org.opentaint.ir.api.common.CommonProject
import org.opentaint.ir.api.common.cfg.CommonAssignInst
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRParameter
import org.opentaint.ir.api.jvm.cfg.JIRArgument
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRBinaryExpr
import org.opentaint.ir.api.jvm.cfg.JIRBool
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRCastExpr
import org.opentaint.ir.api.jvm.cfg.JIRConstant
import org.opentaint.ir.api.jvm.cfg.JIRDynamicCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRIfInst
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInt
import org.opentaint.ir.api.jvm.cfg.JIRNegExpr
import org.opentaint.ir.api.jvm.cfg.JIRNewArrayExpr
import org.opentaint.ir.api.jvm.cfg.JIRStringConstant
import org.opentaint.ir.api.jvm.cfg.JIRThis
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.cfg.values
import org.opentaint.ir.api.jvm.ext.isAssignable
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.ir.impl.cfg.util.loops
import org.opentaint.ir.taint.configuration.ConstantBooleanValue
import org.opentaint.ir.taint.configuration.ConstantIntValue
import org.opentaint.ir.taint.configuration.ConstantStringValue
import org.opentaint.ir.taint.configuration.ConstantValue
import org.opentaint.ir.taint.configuration.TypeMatches
import org.opentaint.dataflow.ifds.AccessPath
import org.opentaint.dataflow.ifds.ElementAccessor
import org.opentaint.dataflow.ifds.FieldAccessor
import org.opentaint.dataflow.jvm.util.JIRTraits.Companion.getArgument
import org.opentaint.dataflow.jvm.util.JIRTraits.Companion.toPathOrNull
import org.opentaint.dataflow.util.Traits
import org.opentaint.ir.api.jvm.ext.cfg.callExpr as _callExpr
import org.opentaint.dataflow.jvm.util.callee as _callee
import org.opentaint.dataflow.jvm.util.getArgument as _getArgument
import org.opentaint.dataflow.jvm.util.getArgumentsOf as _getArgumentsOf
import org.opentaint.dataflow.jvm.util.thisInstance as _thisInstance
import org.opentaint.dataflow.jvm.util.toPath as _toPath
import org.opentaint.dataflow.jvm.util.toPathOrNull as _toPathOrNull

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

    override fun JIRInst.isLoopHead(): Boolean {
        val loops = location.method.flowGraph().loops
        return loops.any { loop -> this == loop.head }
    }

    override fun JIRInst.getBranchExprCondition(): CommonExpr? {
        if (this !is JIRIfInst) return null
        return condition
    }

    override fun JIRInst.getArrayAllocation(): CommonExpr? {
        if (this !is JIRAssignInst) return null
        return rhv as? JIRNewArrayExpr
    }

    override fun JIRInst.getArrayAccessIndex(): CommonValue? {
        if (this !is JIRAssignInst) return null

        val lhv = this.lhv
        if (lhv is JIRArrayAccess) return lhv.index

        val rhv = this.rhv
        if (rhv is JIRArrayAccess) return rhv.index

        return null
    }

    override fun JIRInst.lineNumber(): Int? = location.lineNumber

    override fun JIRInst.locationFQN(): String? {
        val method = location.method
        return "${method.enclosingClass.name}#${method.name}"
    }

    override fun CommonValue.typeMatches(condition: TypeMatches): Boolean {
        check(this is JIRValue)
        return this.type.isAssignable(condition.type)
    }

    override fun CommonAssignInst.taintFlowRhsValues(): List<CommonExpr> =
        when (val rhs = this.rhv as JIRExpr) {
            is JIRBinaryExpr -> listOf(rhs.lhv, rhs.rhv)
            is JIRNegExpr -> listOf(rhs.operand)
            is JIRCastExpr -> listOf(rhs.operand)
            else -> listOf(rhs)
        }

    override fun JIRInst.taintPassThrough(): List<Pair<CommonValue, CommonValue>>? {
        if (this !is JIRAssignInst) return null

        // FIXME: handle taint pass-through on invokedynamic-based String concatenation:
        val callExpr = rhv as? JIRDynamicCallExpr ?: return null
        if (callExpr.callee.enclosingClass.name != "java.lang.invoke.StringConcatFactory") return null

        return callExpr.args.map { it to this.lhv }
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
