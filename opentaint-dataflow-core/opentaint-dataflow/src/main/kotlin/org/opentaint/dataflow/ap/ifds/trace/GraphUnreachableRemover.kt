package org.opentaint.dataflow.ap.ifds.trace

fun <T> removeUnreachableEntries(
    successors: MutableMap<T, MutableSet<T>>,
    startEntries: List<T>,
    finalEntries: List<T>
):Map<T, Set<T>> = removeUnreachableEntriesUtil(successors, startEntries, finalEntries) { it }

fun <T, Edge> removeUnreachableEntries(
    successors: MutableMap<T, MutableSet<Edge>>,
    startEntries: List<T>,
    finalEntries: List<T>,
    edgeEntry: (Edge) -> T
):Map<T, Set<Edge>> = removeUnreachableEntriesUtil(successors, startEntries, finalEntries, edgeEntry)

private inline fun <T, Edge> removeUnreachableEntriesUtil(
    successors: MutableMap<T, MutableSet<Edge>>,
    startEntries: List<T>,
    finalEntries: List<T>,
    crossinline edgeEntry: (Edge) -> T
): Map<T, Set<Edge>> {
    val forwardReachable = reachableEntries(successors, startEntries, edgeEntry)

    val predecessors = buildPredecessors(successors, edgeEntry)
    val backwardReachable = reachableEntries(predecessors, finalEntries) { it }

    val allReachable = forwardReachable.intersect(backwardReachable)
    if (allReachable.isEmpty()) return emptyMap()

    successors.keys.removeAll { it !in allReachable }
    successors.values.forEach { succ -> succ.removeAll { edgeEntry(it) !in allReachable } }
    successors.entries.removeAll { it.value.isEmpty() }

    return successors
}

private inline fun <T, Edge> buildPredecessors(
    successors: Map<T, Set<Edge>>,
    edgeEntry: (Edge) -> T
): Map<T, Set<T>> {
    val predecessors = hashMapOf<T, MutableSet<T>>()
    for ((entry, succ) in successors) {
        succ.forEach { predecessors.getOrPut(edgeEntry(it), ::hashSetOf).add(entry) }
    }
    return predecessors
}

private inline fun <T, Edge> reachableEntries(
    successors: Map<T, Set<Edge>>,
    start: List<T>,
    edgeEntry: (Edge) -> T
): Set<T> {
    val reachable = hashSetOf<T>()
    val unprocessed = start.toMutableList()
    while (unprocessed.isNotEmpty()) {
        val entry = unprocessed.removeLast()
        if (!reachable.add(entry)) continue
        unprocessed.addAll(successors[entry]?.map { edgeEntry(it) }.orEmpty())
    }
    return reachable
}
