package org.opentaint.dataflow.ap.ifds.access

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker

interface AccessorList {
    fun startsWithAccessor(accessor: Accessor): Boolean
    fun getStartAccessors(): Set<Accessor>
    fun getAllAccessors(): Set<Accessor>
}

interface ReadableAccessorList<T : Any> : AccessorList {
    fun readAccessor(accessor: Accessor): T?
}

interface FactAp: AccessorList {
    val base: AccessPathBase
    val exclusions: ExclusionSet

    val size: Int
}

interface InitialFactAp : FactAp, ReadableAccessorList<InitialFactAp> {
    fun rebase(newBase: AccessPathBase): InitialFactAp
    fun exclude(accessor: Accessor): InitialFactAp
    fun replaceExclusions(exclusions: ExclusionSet): InitialFactAp

    fun prependAccessor(accessor: Accessor): InitialFactAp
    fun clearAccessor(accessor: Accessor): InitialFactAp?

    interface Delta: ReadableAccessorList<Delta> {
        val isEmpty: Boolean

        fun concat(other: Delta): Delta
    }

    fun splitDelta(other: FinalFactAp): List<Pair<InitialFactAp, Delta>>
    fun concat(delta: Delta): InitialFactAp

    fun contains(factAp: InitialFactAp): Boolean
}

interface FinalFactAp : FactAp, ReadableAccessorList<FinalFactAp> {
    fun rebase(newBase: AccessPathBase): FinalFactAp
    fun exclude(accessor: Accessor): FinalFactAp
    fun replaceExclusions(exclusions: ExclusionSet): FinalFactAp

    fun isAbstract(): Boolean

    fun prependAccessor(accessor: Accessor): FinalFactAp
    fun clearAccessor(accessor: Accessor): FinalFactAp?
    fun removeAbstraction(): FinalFactAp?

    interface Delta: ReadableAccessorList<Delta> {
        val isEmpty: Boolean
    }

    fun delta(other: InitialFactAp): List<Delta>
    fun concat(typeChecker: FactTypeChecker, delta: Delta): FinalFactAp?

    fun filterFact(filter: FactTypeChecker.FactApFilter): FinalFactAp?

    fun contains(factAp: InitialFactAp): Boolean
    fun equalTo(factAp: InitialFactAp): Boolean

    fun hasEmptyDelta(other: InitialFactAp): Boolean =
        delta(other).any { it.isEmpty }
}
