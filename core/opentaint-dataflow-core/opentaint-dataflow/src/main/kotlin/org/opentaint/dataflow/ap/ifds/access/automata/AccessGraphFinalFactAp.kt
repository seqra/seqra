package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.access.DeepAccessorExclusion
import org.opentaint.dataflow.ap.ifds.access.DeepAccessorExclusion.Companion.addAccessorFromDepth0
import org.opentaint.dataflow.ap.ifds.access.DeepAccessorExclusion.Companion.addAccessorFromDepth1
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.tryAnyAccessorOrNull

data class AccessGraphFinalFactAp(
    override val base: AccessPathBase,
    override val access: AccessGraph,
    override val exclusions: ExclusionSet,
) : FinalFactAp, AccessGraphAccessorList {
    override val size: Int get() = access.size
    override val depth: Int get() = size

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

    override fun clearAllAccessorOccurrences(
        accessor: Accessor,
        keepStartAccessor: Boolean,
    ): FinalFactAp? = with(access.manager) {
        var result = access.clearAllAccessorOccurrences(accessor.idx, keepStartAccessor) ?: return null

        if (exclusions !is ExclusionSet.Universe) {
            val deepExclusion = if (keepStartAccessor && access.initialNodeIsFinal()) {
                result.deepAccessorExclusion.addAccessorFromDepth1(accessor.idx)
            } else {
                result.deepAccessorExclusion.addAccessorFromDepth0(accessor.idx)
            }
            result = result.withAnyFieldAccessorExclusions(deepExclusion)
        }

        if (result === access) this@AccessGraphFinalFactAp
        else AccessGraphFinalFactAp(base, result, exclusions)
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

    override fun abstractOnly(): FinalFactAp =
        AccessGraphFinalFactAp(
            base,
            access.manager.emptyGraph().withAnyFieldAccessorExclusions(access.deepAccessorExclusion),
            exclusions
        )

    data class Delta(override val access: AccessGraph) : FinalFactAp.Delta, AccessGraphAccessorList {
        override val isEmpty: Boolean get() = access.isEmpty()

        override fun readAccessor(accessor: Accessor): FinalFactAp.Delta? = with(access.manager) {
            val newGraph = access.read(accessor.idx)
                ?: tryAnyAccessorOrNull(accessor) { access.read(anyAccessorIdx) }

            return newGraph?.let { Delta(it) }
        }

        override fun isAbstract(): Boolean = access.initialNodeIsFinal()
    }

    override fun delta(other: InitialFactAp): List<FinalFactAp.Delta> {
        other as AccessGraphInitialFactAp
        if (base != other.base) return emptyList()

        return access.delta(other.access).mapNotNull { delta ->
            val filteredDelta = delta.filter(other.exclusions) ?: return@mapNotNull null
            Delta(filteredDelta.withAnyFieldAccessorExclusions(access.deepAccessorExclusion))
        }
    }

    override fun hasEmptyDelta(other: InitialFactAp): Boolean {
        other as AccessGraphInitialFactAp
        if (base != other.base) return false

        return access.containsAllAccessPaths(other.access)
    }

    override fun concat(typeChecker: FactTypeChecker, delta: FinalFactAp.Delta): FinalFactAp? {
        delta as Delta

        val composedAnyFieldAccessorExclusions = DeepAccessorExclusion.merge(
            access.deepAccessorExclusion, delta.access.deepAccessorExclusion
        )

        if (delta.isEmpty) {
            return AccessGraphFinalFactAp(
                base,
                access.withAnyFieldAccessorExclusions(composedAnyFieldAccessorExclusions),
                exclusions,
            )
        }

        val structurallyFilteredDelta = delta.access.enforceAnyFieldAccessorExclusions(
            composedAnyFieldAccessorExclusions, keepInitialLevel = true
        ) ?: return null

        val filter = access.manager.createFilter(access, typeChecker)
        val filteredDelta = structurallyFilteredDelta.filter(filter) ?: return null

        val resultAnyFieldAccessorExclusions = if (delta.access.initialNodeIsFinal()) {
            composedAnyFieldAccessorExclusions
        } else {
            DeepAccessorExclusion.merge(
                access.deepAccessorExclusion?.collapseToDepth0(), delta.access.deepAccessorExclusion
            )
        }

        if (access.isEmpty()) {
            return AccessGraphFinalFactAp(
                base,
                filteredDelta.withAnyFieldAccessorExclusions(resultAnyFieldAccessorExclusions),
                exclusions,
            )
        }

        val concatenatedGraph = access.concat(filteredDelta)
        return AccessGraphFinalFactAp(
            base,
            concatenatedGraph.withAnyFieldAccessorExclusions(resultAnyFieldAccessorExclusions),
            exclusions,
        )
    }

    override fun filterFact(filter: FactTypeChecker.FactApFilter): FinalFactAp? =
        access.filter(filter)?.let { AccessGraphFinalFactAp(base, it, exclusions) }

    override fun filterFact(filter: FactTypeChecker.FactCompatibilityFilter): FinalFactAp? =
        access.filter(filter)?.let { AccessGraphFinalFactAp(base, it, exclusions) }

    override fun contains(factAp: InitialFactAp): Boolean {
        factAp as AccessGraphInitialFactAp

        if (base != factAp.base) return false
        return access.containsAllAccessPaths(factAp.access)
    }

    override fun equalTo(factAp: InitialFactAp): Boolean {
        factAp as AccessGraphInitialFactAp

        if (base != factAp.base) return false
        return access == factAp.access
    }
}
