package org.opentaint.ir.analysis.util

import org.opentaint.ir.analysis.ifds.AccessPath
import org.opentaint.ir.analysis.ifds.Edge
import org.opentaint.ir.analysis.ifds.ElementAccessor
import org.opentaint.ir.analysis.ifds.Runner
import org.opentaint.ir.analysis.ifds.UniRunner
import org.opentaint.ir.analysis.taint.TaintBidiRunner
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.CommonMethodParameter
import org.opentaint.ir.api.common.Project
import org.opentaint.ir.api.common.cfg.CommonArgument
import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRParameter
import org.opentaint.ir.api.jvm.cfg.JIRArgument
import org.opentaint.ir.api.jvm.cfg.JIRThis
import org.opentaint.ir.api.jvm.ext.toType

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

val JIRMethod.thisInstance: JIRThis
    get() = JIRThis(enclosingClass.toType())

fun JIRClasspath.getArgument(param: JIRParameter): JIRArgument? {
    val t = findTypeOrNull(param.type.typeName) ?: return null
    return JIRArgument.of(param.index, param.name, t)
}

fun JIRClasspath.getArgumentsOf(method: JIRMethod): List<JIRArgument> {
    return method.parameters.map { getArgument(it)!! }
}

fun AccessPath?.startsWith(other: AccessPath?): Boolean {
    if (this == null || other == null) {
        return false
    }
    if (this.value != other.value) {
        return false
    }
    return this.accesses.take(other.accesses.size) == other.accesses
}

internal fun AccessPath.removeTrailingElementAccessors(): AccessPath {
    var index = accesses.size
    while (index > 0 && accesses[index - 1] is ElementAccessor) {
        index--
    }
    return AccessPath(value, accesses.subList(0, index))
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

val CommonExpr.values: Set<CommonValue>
    get() {
        val resolver = CommonValueResolver()
        accept(resolver)
        return resolver.result
    }

val CommonInst<*, *>.values: Set<CommonValue>
    get() {
        val resolver = CommonValueResolver()
        accept(resolver)
        return resolver.result
    }
