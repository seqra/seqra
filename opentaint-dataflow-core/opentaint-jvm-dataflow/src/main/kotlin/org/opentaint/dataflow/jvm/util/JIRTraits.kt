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
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
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
import org.opentaint.dataflow.util.Traits

/**
 * JVM-specific extensions for analysis.
 */
class JIRTraits(
    val cp: JIRClasspath,
) : Traits<JIRMethod, JIRInst> {

    override fun convertToPathOrNull(expr: CommonExpr): AccessPath? {
        check(expr is JIRExpr)
        return expr.toPathOrNull()
    }

    override fun convertToPathOrNull(value: CommonValue): AccessPath? {
        check(value is JIRValue)
        return value.toPathOrNull()
    }

    override fun convertToPath(value: CommonValue): AccessPath {
        check(value is JIRValue)
        return value.toPath()
    }

    override fun getThisInstance(method: JIRMethod): JIRThis {
        return method.thisInstance
    }

    override fun isConstructor(method: JIRMethod): Boolean {
        return method.isConstructor
    }

    override fun getArgument(param: CommonMethodParameter): JIRArgument? {
        check(param is JIRParameter)
        return cp.getArgument(param)
    }

    override fun getArgumentsOf(method: JIRMethod): List<JIRArgument> {
        return cp.getArgumentsOf(method)
    }

    override fun getCallee(callExpr: CommonCallExpr): JIRMethod {
        check(callExpr is JIRCallExpr)
        return callExpr.callee
    }

    override fun getCallExpr(statement: JIRInst): JIRCallExpr? {
        return statement.callExpr
    }

    override fun getValues(expr: CommonExpr): Set<JIRValue> {
        check(expr is JIRExpr)
        return expr.values
    }

    override fun getOperands(statement: JIRInst): List<JIRExpr> {
        return statement.operands
    }

    override fun getArrayAllocation(statement: JIRInst): JIRExpr? {
        if (statement !is JIRAssignInst) return null
        return statement.rhv as? JIRNewArrayExpr
    }

    override fun getArrayAccessIndex(statement: JIRInst): JIRValue? {
        if (statement !is JIRAssignInst) return null

        val lhv = statement.lhv
        if (lhv is JIRArrayAccess) return lhv.index

        val rhv = statement.rhv
        if (rhv is JIRArrayAccess) return rhv.index

        return null
    }

    override fun getBranchExprCondition(statement: JIRInst): JIRExpr? {
        if (statement !is JIRIfInst) return null
        return statement.condition
    }

    override fun isConstant(value: CommonValue): Boolean {
        check(value is JIRValue)
        return value is JIRConstant
    }

    override fun eqConstant(value: CommonValue, constant: ConstantValue): Boolean {
        check(value is JIRValue)
        return when (constant) {
            is ConstantBooleanValue -> {
                value is JIRBool && value.value == constant.value
            }

            is ConstantIntValue -> {
                value is JIRInt && value.value == constant.value
            }

            is ConstantStringValue -> {
                // TODO: if 'value' is not string, convert it to string and compare with 'constant.value'
                value is JIRStringConstant && value.value == constant.value
            }
        }
    }

    override fun ltConstant(value: CommonValue, constant: ConstantValue): Boolean {
        check(value is JIRValue)
        return when (constant) {
            is ConstantIntValue -> {
                value is JIRInt && value.value < constant.value
            }

            else -> error("Unexpected constant: $constant")
        }
    }

    override fun gtConstant(value: CommonValue, constant: ConstantValue): Boolean {
        check(value is JIRValue)
        return when (constant) {
            is ConstantIntValue -> {
                value is JIRInt && value.value > constant.value
            }

            else -> error("Unexpected constant: $constant")
        }
    }

    override fun matches(value: CommonValue, pattern: Regex): Boolean {
        check(value is JIRValue)
        val s = value.toString()
        return pattern.matches(s)
    }

    override fun typeMatches(value: CommonValue, condition: TypeMatches): Boolean {
        check(value is JIRValue)
        return value.type.isAssignable(condition.type)
    }

    override fun isLoopHead(statement: JIRInst): Boolean {
        val loops = statement.location.method.flowGraph().loops
        return loops.any { loop -> statement == loop.head }
    }

    override fun lineNumber(statement: JIRInst): Int {
        return statement.lineNumber
    }

    override fun locationFQN(statement: JIRInst): String {
        val method = statement.location.method
        return "${method.enclosingClass.name}#${method.name}"
    }

    override fun locationMachineName(statement: JIRInst): String =
        "${statement.method}:${statement.location.index}:($statement)"

    override fun taintFlowRhsValues(statement: CommonAssignInst): List<JIRExpr> {
        check(statement is JIRAssignInst)
        return when (val rhv = statement.rhv) {
            is JIRBinaryExpr -> listOf(rhv.lhv, rhv.rhv)
            is JIRNegExpr -> listOf(rhv.operand)
            is JIRCastExpr -> listOf(rhv.operand)
            else -> listOf(rhv)
        }
    }

    override fun taintPassThrough(statement: JIRInst): List<Pair<JIRValue, JIRValue>>? {
        if (statement !is JIRAssignInst) return null

        // FIXME: handle taint pass-through on invokedynamic-based String concatenation:
        val callExpr = statement.rhv as? JIRDynamicCallExpr ?: return null
        if (callExpr.callee.enclosingClass.name != "java.lang.invoke.StringConcatFactory") return null

        return callExpr.args.map { it to statement.lhv }
    }
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
