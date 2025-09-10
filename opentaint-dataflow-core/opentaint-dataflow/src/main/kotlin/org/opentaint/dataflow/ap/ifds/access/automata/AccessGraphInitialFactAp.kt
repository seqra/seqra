package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.FactApDelta
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

data class AccessGraphInitialFactAp(
    override val base: AccessPathBase,
    val access: AccessGraph,
    override val exclusions: ExclusionSet,
) : InitialFactAp {
    override val size: Int get() = access.size

    override fun rebase(newBase: AccessPathBase): InitialFactAp =
        AccessGraphInitialFactAp(newBase, access, exclusions)

    override fun exclude(accessor: Accessor): InitialFactAp {
        check(accessor !is AnyAccessor)
        return AccessGraphInitialFactAp(base, access, exclusions.add(accessor))
    }

    override fun replaceExclusions(exclusions: ExclusionSet): InitialFactAp =
        AccessGraphInitialFactAp(base, access, exclusions)

    override fun startsWithAccessor(accessor: Accessor): Boolean {
        check(accessor !is AnyAccessor)
        return access.startsWith(accessor)
    }

    override fun readAccessor(accessor: Accessor): InitialFactAp? {
        check(accessor !is AnyAccessor)
        return access.read(accessor)?.let { AccessGraphInitialFactAp(base, it, exclusions) }
    }

    override fun prependAccessor(accessor: Accessor): InitialFactAp {
        check(accessor !is AnyAccessor)
        return AccessGraphInitialFactAp(base, access.prepend(accessor), exclusions)
    }

    override fun clearAccessor(accessor: Accessor): InitialFactAp? {
        check(accessor !is AnyAccessor)
        return access.clear(accessor)?.let { AccessGraphInitialFactAp(base, it, exclusions) }
    }

    data class Delta(val graph: AccessGraph) : FactApDelta {
        override val isEmpty: Boolean get() = graph.isEmpty()
    }

    override fun splitDelta(other: FinalFactAp): List<Pair<InitialFactAp, FactApDelta>> {
        other as AccessGraphFinalFactAp
        if (base != other.base) return emptyList()

        if (other.access.isEmpty()) {
            val filteredDelta = this.access.filter(other.exclusions) ?: return emptyList()

            val emptyFact = AccessGraphInitialFactAp(base, AccessGraph.empty(), exclusions)
            return listOf(emptyFact to Delta(filteredDelta))
        }

        return access.splitDelta(other.access).mapNotNull { (matchedAccess, delta) ->
            val filteredDelta = delta.filter(other.exclusions) ?: return@mapNotNull null

            val matchedFact = AccessGraphInitialFactAp(base, matchedAccess, exclusions)
            matchedFact to Delta(filteredDelta)
        }
    }

    override fun concat(delta: FactApDelta): InitialFactAp {
        if (delta.isEmpty) return this
        delta as Delta

        val concatenatedGraph = access.concat(delta.graph)
        return AccessGraphInitialFactAp(base, concatenatedGraph, exclusions)
    }

    override fun contains(factAp: InitialFactAp): Boolean {
        factAp as AccessGraphInitialFactAp

        if (base != factAp.base) return false
        return access.containsAll(factAp.access)
    }
}
