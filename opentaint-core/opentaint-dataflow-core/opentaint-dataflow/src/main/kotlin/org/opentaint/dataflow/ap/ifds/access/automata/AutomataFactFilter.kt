package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.util.forEach

fun AutomataApManager.createFilter(access: AccessGraph, typeChecker: FactTypeChecker): FactTypeChecker.FactApFilter {
    val finalPredAccessors = access.nodePredecessors(access.final)
    val filters = mutableListOf<FactTypeChecker.FactApFilter>()
    finalPredAccessors.forEach { accessorIdx ->
        val accessor = accessorIdx.accessor
        when (accessor) {
            FinalAccessor -> filters += FactTypeChecker.AlwaysRejectFilter
            is AnyAccessor -> {
                return FactTypeChecker.AlwaysAcceptFilter
            }
            is TaintMarkAccessor -> filters += OnlyFinalAccessorAllowedFilter
            is FieldAccessor -> filters += typeChecker.accessPathFilter(listOf(accessor))
            ElementAccessor -> {
                val edge = access.getEdge(accessorIdx) ?: error("No edge for: $accessor")
                val predecessorNode = access.getEdgeFrom(edge)
                val predecessorPredAccessors = access.nodePredecessors(predecessorNode)
                if (predecessorPredAccessors.isEmpty) {
                    filters += typeChecker.accessPathFilter(listOf(accessor))
                } else {
                    predecessorPredAccessors.forEach { preAccessor ->
                        val preAccessorObj = preAccessor.accessor
                        typeChecker.accessPathFilter(listOf(preAccessorObj, accessor))
                    }
                }
            }
        }
    }

    return CombinedFilter.combineFilters(filters)
}

private object OnlyFinalAccessorAllowedFilter : FactTypeChecker.FactApFilter {
    override fun check(accessor: Accessor): FactTypeChecker.FilterResult =
        if (accessor is FinalAccessor) {
            FactTypeChecker.FilterResult.Accept
        } else {
            FactTypeChecker.FilterResult.Reject
        }
}

private class CombinedFilter(
    private val filters: List<FactTypeChecker.FactApFilter>
) : FactTypeChecker.FactApFilter {
    override fun check(accessor: Accessor): FactTypeChecker.FilterResult {
        val nextFilters = mutableListOf<FactTypeChecker.FactApFilter>()
        for (filter in filters) {
            when (val status = filter.check(accessor)) {
                FactTypeChecker.FilterResult.Accept -> return FactTypeChecker.FilterResult.Accept
                FactTypeChecker.FilterResult.Reject -> continue
                is FactTypeChecker.FilterResult.FilterNext -> {
                    nextFilters.add(status.filter)
                }
            }
        }

        if (nextFilters.isEmpty()) {
            // No accepted and no next
            return FactTypeChecker.FilterResult.Reject
        }

        return FactTypeChecker.FilterResult.FilterNext(combineFilters(nextFilters))
    }

    companion object {
        fun combineFilters(filters: List<FactTypeChecker.FactApFilter>) = when (filters.size) {
            0 -> FactTypeChecker.AlwaysAcceptFilter
            1 -> filters.single()
            else -> CombinedFilter(filters)
        }
    }
}
