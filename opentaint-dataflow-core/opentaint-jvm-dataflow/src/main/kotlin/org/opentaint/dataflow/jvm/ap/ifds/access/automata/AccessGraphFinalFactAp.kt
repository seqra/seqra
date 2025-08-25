package org.opentaint.dataflow.jvm.ap.ifds.access.automata

import org.opentaint.dataflow.jvm.ap.ifds.AccessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.Accessor
import org.opentaint.dataflow.jvm.ap.ifds.ElementAccessor
import org.opentaint.dataflow.jvm.ap.ifds.ExclusionSet
import org.opentaint.dataflow.jvm.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.jvm.ap.ifds.FieldAccessor
import org.opentaint.dataflow.jvm.ap.ifds.FinalAccessor
import org.opentaint.dataflow.jvm.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.jvm.ap.ifds.access.FactApDelta
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp

data class AccessGraphFinalFactAp(
    override val base: AccessPathBase,
    val access: AccessGraph,
    override val exclusions: ExclusionSet
) : FinalFactAp {
    override val size: Int get() = access.size

    override fun rebase(newBase: AccessPathBase): FinalFactAp =
        AccessGraphFinalFactAp(newBase, access, exclusions)

    override fun exclude(accessor: Accessor): FinalFactAp =
        AccessGraphFinalFactAp(base, access, exclusions.add(accessor))

    override fun replaceExclusions(exclusions: ExclusionSet): FinalFactAp =
        AccessGraphFinalFactAp(base, access, exclusions)

    override fun startsWithAccessor(accessor: Accessor): Boolean =
        access.startsWith(accessor)

    override fun isAbstract(): Boolean =
        exclusions !is ExclusionSet.Universe && access.initialNodeIsFinal()

    override fun readAccessor(accessor: Accessor): FinalFactAp? =
        access.read(accessor)?.let { AccessGraphFinalFactAp(base, it, exclusions) }

    override fun prependAccessor(accessor: Accessor): FinalFactAp =
        AccessGraphFinalFactAp(base, access.prepend(accessor), exclusions)

    override fun clearAccessor(accessor: Accessor): FinalFactAp? =
        access.clear(accessor)?.let { AccessGraphFinalFactAp(base, it, exclusions) }

    override fun removeAbstraction(): FinalFactAp? {
        /**
         * Automata is at an abstraction point when its
         * initial node and final node are the same node.
         * If we remove the abstraction point we remove the final and initial nodes.
         * So, we remove entire automata.
         * */
        return null
    }

    data class Delta(val graph: AccessGraph) : FactApDelta {
        override val isEmpty: Boolean get() = graph.isEmpty()
    }

    override fun delta(other: InitialFactAp): List<FactApDelta> {
        other as AccessGraphInitialFactAp
        if (base != other.base) return emptyList()

        if (other.access.isEmpty()) {
            val filteredDelta = this.access.filter(other.exclusions)
            return listOfNotNull(filteredDelta?.let { Delta(it) })
        }

        return access.delta(other.access).mapNotNull { delta ->
            val filteredDelta = delta.filter(other.exclusions)
            filteredDelta?.let { Delta(it) }
        }
    }

    override fun concat(typeChecker: FactTypeChecker, delta: FactApDelta): FinalFactAp? {
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
        for (accessor in finalPredAccessors) {
            when (accessor) {
                FinalAccessor -> filters += FactTypeChecker.AlwaysRejectFilter
                is TaintMarkAccessor -> filters += OnlyFinalAccessorAllowedFilter
                is FieldAccessor -> filters += typeChecker.accessPathFilter(listOf(accessor))
                ElementAccessor -> {
                    val (predecessorNode, _) = edges[accessor] ?: error("No edge for: $accessor")
                    val predecessorPredAccessors = nodePredecessors(predecessorNode)
                    if (predecessorPredAccessors.isEmpty()) {
                        filters += typeChecker.accessPathFilter(listOf(accessor))
                    } else {
                        predecessorPredAccessors.mapTo(filters) { preAccessor ->
                            typeChecker.accessPathFilter(listOf(preAccessor, accessor))
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
