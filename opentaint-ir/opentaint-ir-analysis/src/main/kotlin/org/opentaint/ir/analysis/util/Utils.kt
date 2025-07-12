package org.opentaint.ir.analysis.util

import org.opentaint.ir.analysis.ifds.AccessPath
import org.opentaint.ir.analysis.ifds.Edge
import org.opentaint.ir.analysis.ifds.ElementAccessor
import org.opentaint.ir.analysis.ifds.Runner
import org.opentaint.ir.analysis.ifds.UniRunner
import org.opentaint.ir.analysis.taint.TaintBidiRunner

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

fun Runner<*, *, *>.getPathEdges(): Set<Edge<*, *>> = when (this) {
    is UniRunner<*, *, *, *> -> pathEdges
    is TaintBidiRunner<*, *> -> forwardRunner.getPathEdges() + backwardRunner.getPathEdges()
    else -> error("Cannot extract pathEdges for $this")
}
