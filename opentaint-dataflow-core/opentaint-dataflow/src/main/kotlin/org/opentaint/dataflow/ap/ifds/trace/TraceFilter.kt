package org.opentaint.dataflow.ap.ifds.trace

import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver.FullTrace
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver.TraceEntry

fun FullTrace.filter(predicate: (TraceEntry) -> Boolean): FullTrace? {
    val mutableSuccessors = successors.toMutableMap()

    val entriesToRemove = mutableSuccessors.keys.filter { !predicate(it) }
    if (entriesToRemove.isEmpty()) return this

    entriesToRemove.forEach { mutableSuccessors.remove(it) }

    val fullyMutableSuccessors = mutableSuccessors.mapValuesTo(hashMapOf()) { it.value.toMutableSet() }
    val resultSuccessors = removeUnreachableEntries(fullyMutableSuccessors, listOf(startEntry), listOf(final))

    val resultInitialSuccessors = resultSuccessors[startEntry]
    if (resultInitialSuccessors.isNullOrEmpty()) return null

    return this.copy(successors = resultSuccessors)
}
