package org.opentaint.ir.analysis.util

import org.opentaint.ir.analysis.ifds.AccessPath
import org.opentaint.ir.analysis.ifds.Edge
import org.opentaint.ir.analysis.ifds.Runner
import org.opentaint.ir.analysis.ifds.UniRunner
import org.opentaint.ir.analysis.taint.TaintBidiRunner
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.JIRParameter
import org.opentaint.ir.api.cfg.JIRArgument
import org.opentaint.ir.api.cfg.JIRThis
import org.opentaint.ir.api.ext.toType

val JIRMethod.thisInstance: JIRThis
    get() = JIRThis(enclosingClass.toType())

fun JIRClasspath.getArgument(param: JIRParameter): JIRArgument? {
    val t = findTypeOrNull(param.type.typeName) ?: return null
    return JIRArgument.of(param.index, param.name, t)
}

fun JIRClasspath.getArgumentsOf(method: JIRMethod): List<JIRArgument> {
    return method.parameters.map { getArgument(it)!! }
}

internal fun Runner<*>.getPathEdges(): Set<Edge<*>> = when (this) {
    is UniRunner<*, *> -> pathEdges
    is TaintBidiRunner -> forwardRunner.getPathEdges() + backwardRunner.getPathEdges()
    else -> error("Cannot extract pathEdges for $this")
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
