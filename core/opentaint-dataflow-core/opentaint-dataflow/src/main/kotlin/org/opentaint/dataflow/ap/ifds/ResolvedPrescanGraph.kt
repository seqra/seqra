package org.opentaint.dataflow.ap.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.Collections

/** Method-level copy of an already resolved subscription. No subscriber, fact or context is retained. */
data class ResolvedPrescanCall(val caller: CommonMethod, val statement: CommonInst, val callee: CommonMethod)

class ResolvedPrescanGraph(methods: Collection<CommonMethod>, calls: Collection<ResolvedPrescanCall>) {
    val calls: Set<ResolvedPrescanCall> = Collections.unmodifiableSet(LinkedHashSet(calls))
    val methods: Set<CommonMethod> = Collections.unmodifiableSet(LinkedHashSet<CommonMethod>().apply {
        addAll(methods)
        for (call in this@ResolvedPrescanGraph.calls) { add(call.caller); add(call.callee) }
    })
    val adjacency: Map<CommonMethod, Set<CommonMethod>> = Collections.unmodifiableMap(
        LinkedHashMap<CommonMethod, Set<CommonMethod>>().apply {
            val scratch = LinkedHashMap<CommonMethod, MutableSet<CommonMethod>>()
            for (method in this@ResolvedPrescanGraph.methods) scratch[method] = LinkedHashSet()
            for (call in this@ResolvedPrescanGraph.calls) scratch.getValue(call.caller).add(call.callee)
            for ((method, targets) in scratch) put(method, Collections.unmodifiableSet(targets))
        })

    companion object { val EMPTY = ResolvedPrescanGraph(emptyList(), emptyList()) }
}
