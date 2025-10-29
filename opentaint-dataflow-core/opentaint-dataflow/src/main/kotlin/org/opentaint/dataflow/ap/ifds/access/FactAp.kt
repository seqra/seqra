package org.opentaint.dataflow.ap.ifds.access

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker

interface FactAp {
    val base: AccessPathBase
    val exclusions: ExclusionSet

    val size: Int

    fun startsWithAccessor(accessor: Accessor): Boolean
}

interface FactApDelta {
    val isEmpty: Boolean
}

interface InitialFactAp : FactAp {
    fun rebase(newBase: AccessPathBase): InitialFactAp
    fun exclude(accessor: Accessor): InitialFactAp
    fun replaceExclusions(exclusions: ExclusionSet): InitialFactAp

    fun readAccessor(accessor: Accessor): InitialFactAp?
    fun prependAccessor(accessor: Accessor): InitialFactAp
    fun clearAccessor(accessor: Accessor): InitialFactAp?

    fun splitDelta(other: FinalFactAp): List<Pair<InitialFactAp, FactApDelta>>
    fun concat(delta: FactApDelta): InitialFactAp

    fun contains(factAp: InitialFactAp): Boolean
}

interface FinalFactAp : FactAp {
    fun rebase(newBase: AccessPathBase): FinalFactAp
    fun exclude(accessor: Accessor): FinalFactAp
    fun replaceExclusions(exclusions: ExclusionSet): FinalFactAp

    fun isAbstract(): Boolean

    fun readAccessor(accessor: Accessor): FinalFactAp?
    fun prependAccessor(accessor: Accessor): FinalFactAp
    fun clearAccessor(accessor: Accessor): FinalFactAp?
    fun removeAbstraction(): FinalFactAp?

    fun delta(other: InitialFactAp): List<FactApDelta>
    fun concat(typeChecker: FactTypeChecker, delta: FactApDelta): FinalFactAp?

    fun filterFact(filter: FactTypeChecker.FactApFilter): FinalFactAp?

    fun contains(factAp: InitialFactAp): Boolean

    fun hasEmptyDelta(other: InitialFactAp): Boolean =
        delta(other).any { it.isEmpty }
}
