package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.tryAnyAccessorOrNull
import org.opentaint.dataflow.util.forEach

data class AccessGraphFinalFactAp(
    override val base: AccessPathBase,
    val access: AccessGraph,
    override val exclusions: ExclusionSet
) : FinalFactAp {
    override val size: Int get() = access.size

    override fun rebase(newBase: AccessPathBase): FinalFactAp =
        AccessGraphFinalFactAp(newBase, access, exclusions)

    override fun exclude(accessor: Accessor): FinalFactAp {
        check(accessor !is AnyAccessor)
        return AccessGraphFinalFactAp(base, access, exclusions.add(accessor))
    }

    override fun replaceExclusions(exclusions: ExclusionSet): FinalFactAp =
        AccessGraphFinalFactAp(base, access, exclusions)

    override fun getAllAccessors(): Set<Accessor> = access.getAllOwnAccessors()

    override fun startsWithAccessor(accessor: Accessor): Boolean = with(access.manager) {
        access.startsWith(accessor.idx) || (access.startsWith(anyAccessorIdx) && AnyAccessor.containsAccessor(accessor))
    }

    override fun isAbstract(): Boolean =
        exclusions !is ExclusionSet.Universe && access.initialNodeIsFinal()

    override fun readAccessor(accessor: Accessor): FinalFactAp? = with(access.manager) {
        val graph = access.read(accessor.idx)
            ?: tryAnyAccessorOrNull(accessor) { access.read(anyAccessorIdx) }

        return graph?.let { AccessGraphFinalFactAp(base, it, exclusions) }
    }

    override fun prependAccessor(accessor: Accessor): FinalFactAp = with(access.manager) {
        AccessGraphFinalFactAp(base, access.prepend(accessor.idx), exclusions)
    }

    override fun clearAccessor(accessor: Accessor): FinalFactAp? = with(access.manager) {
        check(accessor !is AnyAccessor)
        return access.clear(accessor.idx)?.let { AccessGraphFinalFactAp(base, it, exclusions) }
    }

    override fun removeAbstraction(): FinalFactAp? {
        /**
         * Automata is at an abstraction point when its
         * initial node and final node are the same node.
         * If we remove the abstraction point we remove the final and initial nodes.
         * So, we remove entire automata.
         * */
        return null
    }

    data class Delta(val graph: AccessGraph) : FinalFactAp.Delta {
        override val isEmpty: Boolean get() = graph.isEmpty()
    }

    override fun delta(other: InitialFactAp): List<FinalFactAp.Delta> {
        other as AccessGraphInitialFactAp
        if (base != other.base) return emptyList()

        return access.delta(other.access).mapNotNull { delta ->
            val filteredDelta = delta.filter(other.exclusions)
            filteredDelta?.let { Delta(it) }
        }
    }

    override fun hasEmptyDelta(other: InitialFactAp): Boolean {
        other as AccessGraphInitialFactAp
        if (base != other.base) return false

        return access.containsAll(other.access)
    }

    override fun concat(typeChecker: FactTypeChecker, delta: FinalFactAp.Delta): FinalFactAp? {
        if (delta.isEmpty) return this
        delta as Delta

        val filter = access.createFilter(typeChecker)
        val filteredDelta = delta.graph.filter(filter) ?: return null

        if (access.isEmpty()) {
            return AccessGraphFinalFactAp(base, filteredDelta, exclusions)
        }

        val concatenatedGraph = access.concat(filteredDelta)
        return AccessGraphFinalFactAp(base, concatenatedGraph, exclusions)
    }

    override fun filterFact(filter: FactTypeChecker.FactApFilter): FinalFactAp? =
        access.filter(filter)?.let { AccessGraphFinalFactAp(base, it, exclusions) }

    override fun contains(factAp: InitialFactAp): Boolean {
        factAp as AccessGraphInitialFactAp

        if (base != factAp.base) return false
        return access.containsAll(factAp.access)
    }

    private fun AccessGraph.createFilter(typeChecker: FactTypeChecker): FactTypeChecker.FactApFilter {
        val finalPredAccessors = nodePredecessors(final)
        val filters = mutableListOf<FactTypeChecker.FactApFilter>()
        finalPredAccessors.forEach { accessorIdx ->
            val accessor = with(access.manager) { accessorIdx.accessor }
            when (accessor) {
                FinalAccessor -> filters += FactTypeChecker.AlwaysRejectFilter
                is AnyAccessor -> {
                    return FactTypeChecker.AlwaysAcceptFilter
                }
                is TaintMarkAccessor -> filters += OnlyFinalAccessorAllowedFilter
                is FieldAccessor -> filters += typeChecker.accessPathFilter(listOf(accessor))
                ElementAccessor -> {
                    val edge = getEdge(accessorIdx) ?: error("No edge for: $accessor")
                    val predecessorNode = getEdgeFrom(edge)
                    val predecessorPredAccessors = nodePredecessors(predecessorNode)
                    if (predecessorPredAccessors.isEmpty) {
                        filters += typeChecker.accessPathFilter(listOf(accessor))
                    } else {
                        predecessorPredAccessors.forEach { preAccessor ->
                            val preAccessorObj = with(access.manager) { preAccessor.accessor }
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
}
