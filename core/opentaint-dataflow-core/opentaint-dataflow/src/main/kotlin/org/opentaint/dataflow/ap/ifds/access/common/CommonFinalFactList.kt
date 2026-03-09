package org.opentaint.dataflow.ap.ifds.access.common

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.FinalFactList

abstract class CommonFinalFactList<FAP> : FinalFactList, FinalApAccess<FAP> {
    abstract val storage: AccessStorage<FAP>

    interface AccessStorage<FAP> {
        fun add(fact: FAP)
        fun get(idx: Int): FAP
        fun removeLast(): FAP
    }

    class Default<FAP> : AccessStorage<FAP> {
        private val storage = mutableListOf<FAP>()
        override fun add(fact: FAP) {
            storage.add(fact)
        }

        override fun get(idx: Int): FAP = storage[idx]
        override fun removeLast(): FAP = storage.removeLast()
    }

    private val bases = mutableListOf<AccessPathBase>()
    private val exclusions = mutableListOf<ExclusionSet>()

    override fun add(fact: FinalFactAp) {
        bases.add(fact.base)
        exclusions.add(fact.exclusions)
        storage.add(getFinalAccess(fact))
    }

    override operator fun get(idx: Int): FinalFactAp =
        createFinal(bases[idx], storage.get(idx), exclusions[idx])

    override fun removeLast(): FinalFactAp =
        createFinal(bases.removeLast(), storage.removeLast(), exclusions.removeLast())
}
