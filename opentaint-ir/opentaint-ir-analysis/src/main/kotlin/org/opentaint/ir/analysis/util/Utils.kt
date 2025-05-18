package org.opentaint.ir.analysis.util

import org.opentaint.ir.analysis.ifds.CommonAccessPath
import org.opentaint.ir.analysis.ifds.Edge
import org.opentaint.ir.analysis.ifds.ElementAccessor
import org.opentaint.ir.analysis.ifds.JIRAccessPath
import org.opentaint.ir.analysis.ifds.Runner
import org.opentaint.ir.analysis.ifds.UniRunner
import org.opentaint.ir.analysis.taint.TaintBidiRunner
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.CommonMethodParameter
import org.opentaint.ir.api.common.Project
import org.opentaint.ir.api.common.cfg.CommonArgument
import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonThis
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRParameter
import org.opentaint.ir.api.jvm.cfg.JIRArgument
import org.opentaint.ir.api.jvm.cfg.JIRThis
import org.opentaint.ir.api.jvm.ext.toType

// TODO: rewrite
internal val CommonMethod<*, *>.isConstructor: Boolean
    get() = when (this) {
        is JIRMethod -> isConstructor
        else -> error("Cannot determine whether method is constructor: $this")
    }

val CommonMethod<*, *>.thisInstance: CommonThis
    get() = when (this) {
        is JIRMethod -> thisInstance
        else -> error("Cannot get 'this' for method: $this")
    }

fun Project.getArgument(param: CommonMethodParameter): CommonArgument? {
    return when {
        this is JIRClasspath && param is JIRParameter -> getArgument(param)
        else -> error("Cannot get argument from parameter: $param")
    }
}

fun Project.getArgumentsOf(method: CommonMethod<*, *>): List<CommonArgument> {
    return when {
        this is JIRClasspath && method is JIRMethod -> getArgumentsOf(method)
        else -> error("Cannot get arguments of method: $method")
    }
}

fun CommonAccessPath?.startsWith(other: CommonAccessPath?): Boolean {
    if (this == null || other == null) {
        return false
    }
    if (this is JIRAccessPath && other is JIRAccessPath) {
        return startsWith(other)
    }
    }
    error("Cannot determine whether the path $this starts with other path: $other")
}

internal fun CommonAccessPath.removeTrailingElementAccessors(): CommonAccessPath = when (this) {
    is JIRAccessPath -> removeTrailingElementAccessors()
    else -> error("Cannot remove trailing element accessors for path: $this")
}

val JIRMethod.thisInstance: JIRThis
    get() = JIRThis(enclosingClass.toType())

fun JIRClasspath.getArgument(param: JIRParameter): JIRArgument? {
    val t = findTypeOrNull(param.type.typeName) ?: return null
    return JIRArgument.of(param.index, param.name, t)
}

fun JIRClasspath.getArgumentsOf(method: JIRMethod): List<JIRArgument> {
    return method.parameters.map { getArgument(it)!! }
}

fun JIRAccessPath?.startsWith(other: JIRAccessPath?): Boolean {
    if (this == null || other == null) {
        return false
    }
    if (this.value != other.value) {
        return false
    }
    return this.accesses.take(other.accesses.size) == other.accesses
}

    if (this == null || other == null) {
        return false
    }
    if (this.value != other.value) {
        return false
    }
    return this.accesses.take(other.accesses.size) == other.accesses
}

internal fun JIRAccessPath.removeTrailingElementAccessors(): JIRAccessPath {
    val accesses = accesses.toMutableList()
    while (accesses.lastOrNull() is ElementAccessor) {
        accesses.removeLast()
    }
    return JIRAccessPath(value, accesses)
}

    val accesses = accesses.toMutableList()
    while (accesses.lastOrNull() is ElementAccessor) {
        accesses.removeLast()
    }
}

internal fun Runner<*, *, *>.getPathEdges(): Set<Edge<*, *, *>> = when (this) {
    is UniRunner<*, *, *, *> -> pathEdges
    is TaintBidiRunner<*, *> -> forwardRunner.getPathEdges() + backwardRunner.getPathEdges()
    else -> error("Cannot extract pathEdges for $this")
}

abstract class AbstractFullCommonExprSetCollector :
    CommonExpr.Visitor.Default<Any>,
    CommonInst.Visitor.Default<Any> {

    abstract fun ifMatches(expr: CommonExpr)

    override fun defaultVisitCommonExpr(expr: CommonExpr) {
        ifMatches(expr)
        expr.operands.forEach { it.accept(this) }
    }

    override fun defaultVisitCommonInst(inst: CommonInst<*, *>) {
        inst.operands.forEach { it.accept(this) }
    }
}

abstract class TypedCommonExprResolver<T : CommonExpr> : AbstractFullCommonExprSetCollector() {
    val result: MutableSet<T> = hashSetOf()
}

class CommonValueResolver : TypedCommonExprResolver<CommonValue>() {
    override fun ifMatches(expr: CommonExpr) {
        if (expr is CommonValue) {
            result.add(expr)
        }
    }
}

// TODO: consider renaming to "values"
val CommonExpr.coreValues: Set<CommonValue>
    get() {
        val resolver = CommonValueResolver()
        accept(resolver)
        return resolver.result
    }

// TODO: consider renaming to "values"
val CommonInst<*, *>.coreValues: Set<CommonValue>
    get() {
        val resolver = CommonValueResolver()
        accept(resolver)
        return resolver.result
    }
