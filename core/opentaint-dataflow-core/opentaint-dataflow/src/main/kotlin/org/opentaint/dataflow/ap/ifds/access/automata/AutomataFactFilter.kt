package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.FactTypeChecker.CompatibilityFilterResult
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.util.forEach

fun AutomataApManager.createCompatibilityFilter(
    access: AccessGraph,
    typeChecker: FactTypeChecker
): FactTypeChecker.FactCompatibilityFilter = createFilter(
    access, CombinedCompatibilityFilter::combineFilters,
    singleAccessorFilter = {
        FactTypeChecker.AlwaysCompatibleFilter
    },
    accessorListFilter = {
        typeChecker.accessPathCompatibilityFilter(it)
    }
)

fun AutomataApManager.createFilter(
    access: AccessGraph,
    typeChecker: FactTypeChecker
): FactTypeChecker.FactApFilter = createFilter(
    access, CombinedFilter::combineFilters,
    singleAccessorFilter = { accessor ->
        when (accessor) {
            is AnyAccessor -> FactTypeChecker.AlwaysAcceptFilter
            is FinalAccessor -> FactTypeChecker.AlwaysRejectFilter
            is TaintMarkAccessor -> OnlyFinalAccessorAllowedFilter
            else -> error("Unexpected single accessor")
        }
    },
    accessorListFilter = {
        typeChecker.accessPathFilter(it)
    }
)

private inline fun <T> AutomataApManager.createFilter(
    access: AccessGraph,
    combine: (List<T>) -> T,
    singleAccessorFilter: (Accessor) -> T,
    accessorListFilter: (List<Accessor>) -> T,
): T {
    val finalPredAccessors = access.nodePredecessors(access.final)
    val filters = mutableListOf<T>()
    finalPredAccessors.forEach { accessorIdx ->
        val accessor = accessorIdx.accessor
        when (accessor) {
            is FinalAccessor -> filters += singleAccessorFilter(accessor)
            is AnyAccessor -> {
                return singleAccessorFilter(accessor)
            }

            is TaintMarkAccessor -> filters += singleAccessorFilter(accessor)
            is FieldAccessor,
            is ClassStaticAccessor -> filters += accessorListFilter(listOf(accessor))

            is ElementAccessor -> {
                val edge = access.getEdge(accessorIdx) ?: error("No edge for: $accessor")
                val predecessorNode = access.getEdgeFrom(edge)
                val predecessorPredAccessors = access.nodePredecessors(predecessorNode)
                if (predecessorPredAccessors.isEmpty) {
                    filters += accessorListFilter(listOf(accessor))
                } else {
                    predecessorPredAccessors.forEach { preAccessor ->
                        val preAccessorObj = preAccessor.accessor
                        accessorListFilter(listOf(preAccessorObj, accessor))
                    }
                }
            }
        }
    }

    return combine(filters)
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

private class CombinedCompatibilityFilter(
    private val filters: List<FactTypeChecker.FactCompatibilityFilter>
) : FactTypeChecker.FactCompatibilityFilter {
    override fun check(accessor: Accessor): CompatibilityFilterResult {
        for (filter in filters) {
            when (filter.check(accessor)) {
                CompatibilityFilterResult.Compatible -> return CompatibilityFilterResult.Compatible
                CompatibilityFilterResult.NotCompatible -> continue
            }
        }
        return CompatibilityFilterResult.NotCompatible
    }

    companion object {
        fun combineFilters(
            filters: List<FactTypeChecker.FactCompatibilityFilter>
        ): FactTypeChecker.FactCompatibilityFilter = when (filters.size) {
            0 -> FactTypeChecker.AlwaysCompatibleFilter
            1 -> filters.single()
            else -> CombinedCompatibilityFilter(filters)
        }
    }
}
