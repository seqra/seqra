package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.tryAnyAccessorOrNull

data class AccessGraphFinalFactAp(
    override val base: AccessPathBase,
    override val access: AccessGraph,
    override val exclusions: ExclusionSet
) : FinalFactAp, AccessGraphAccessorList {
    override val size: Int get() = access.size

    override fun rebase(newBase: AccessPathBase): FinalFactAp =
        AccessGraphFinalFactAp(newBase, access, exclusions)

    override fun exclude(accessor: Accessor): FinalFactAp {
        check(accessor !is AnyAccessor)
        return AccessGraphFinalFactAp(base, access, exclusions.add(accessor))
    }

    override fun replaceExclusions(exclusions: ExclusionSet): FinalFactAp =
        AccessGraphFinalFactAp(base, access, exclusions)

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

    data class Delta(override val access: AccessGraph) : FinalFactAp.Delta, AccessGraphAccessorList {
        override val isEmpty: Boolean get() = access.isEmpty()

        override fun readAccessor(accessor: Accessor): FinalFactAp.Delta? = with(access.manager) {
            val newGraph = access.read(accessor.idx)
                ?: tryAnyAccessorOrNull(accessor) { access.read(anyAccessorIdx) }

            return newGraph?.let { Delta(it) }
        }
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

        val filter = access.manager.createFilter(access, typeChecker)
        val filteredDelta = delta.access.filter(filter) ?: return null

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
}
