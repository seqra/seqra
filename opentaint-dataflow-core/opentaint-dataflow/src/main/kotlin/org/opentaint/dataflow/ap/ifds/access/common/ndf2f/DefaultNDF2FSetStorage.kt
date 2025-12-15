package org.opentaint.dataflow.ap.ifds.access.common.ndf2f

import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.common.CommonNDF2FSet.ApStorage
import org.opentaint.dataflow.util.collectToListWithPostProcess

abstract class DefaultNDF2FSetStorage<IAP, FAP> : ApStorage<IAP, FAP> {
    interface Storage<FAP> {
        fun add(element: FAP): FAP?
        fun collect(dst: MutableList<FAP>)
    }

    abstract fun createStorage(): Storage<FAP>

    private val storage = hashMapOf<Set<InitialFactAp>, Storage<FAP>>()

    override fun add(initial: Set<InitialFactAp>, final: FAP): FAP? {
        val currentFapStorage = storage[initial]
        if (currentFapStorage == null) {
            storage[initial] = createStorage().also { it.add(final) }
            return final
        }

        return currentFapStorage.add(final)
    }

    override fun filter(
        dst: MutableList<Pair<Set<InitialFactAp>, FAP>>,
        finalPattern: IAP,
    ) {
        storage.forEach { (initial, s) ->
            collectToListWithPostProcess(dst, { s.collect(it) }, { initial to it })
        }
    }

    override fun filter(
        dst: MutableList<FAP>,
        initial: Set<InitialFactAp>,
        finalPattern: IAP,
    ) {
        val initialWithExclusion = initial.mapTo(hashSetOf()) {
            it.replaceExclusions(ExclusionSet.Universe)
        }

        val finalFacts = storage[initialWithExclusion] ?: return
        finalFacts.collect(dst)
    }
}
