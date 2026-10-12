package org.opentaint.dataflow.ap.ifds.access.common.ndf2f

import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.common.CommonAPSub
import org.opentaint.dataflow.ap.ifds.access.common.CommonFactNDEdgeSubBuilder
import org.opentaint.dataflow.util.collectToListWithPostProcess
import org.opentaint.dataflow.util.forEach
import java.util.BitSet

abstract class DefaultNDF2FSubStorage<IAP, FAP : Any> :
    DefaultNDF2FStorage<FAP, FAP, DefaultNDF2FSubStorage.Storage<IAP, FAP>>(),
    CommonAPSub.NDF2FSubStorage<IAP, FAP> {

    abstract fun createBuilder(): CommonFactNDEdgeSubBuilder<FAP>

    interface Storage<IAP, FAP : Any> : DefaultStorage<FAP, FAP> {
        fun collect(dst: MutableList<FAP>)
        fun collect(dst: MutableList<FAP>, summaryInitialFact: IAP)
    }

    /**
     * Feeds [body] every storage index whose stored fact may match [summaryInitialFact], ascending.
     *
     * This used to hand back a `BitSet`. The tree implementation's index is sparse - 87.75 % of its
     * nodes hold a single item - so materialising one would have cost more than the set it
     * describes, the backing `long[]` being sized by the largest index rather than by the count.
     */
    abstract fun forEachRelevantStorageIndex(summaryInitialFact: IAP, body: (Int) -> Unit)

    override fun add(
        callerInitial: Set<InitialFactAp>,
        callerExitAp: FAP,
    ): CommonFactNDEdgeSubBuilder<FAP>? {
        val delta = addToStorage(callerInitial, callerExitAp) ?: return null
        return createBuilder()
            .setCallerNode(delta)
            .setCallerInitial(callerInitial)
    }

    override fun find(
        dst: MutableList<CommonFactNDEdgeSubBuilder<FAP>>,
        summaryInitialFact: IAP,
        emptyDeltaRequired: Boolean,
    ) {
        forEachRelevantStorageIndex(summaryInitialFact) { storageIdx ->
            val callerInitialAp = initialApStorage[storageIdx]
            val callerExitAp = exitApStorage[storageIdx]
            collectToListWithPostProcess(
                dst,
                { callerExitAp.collect(it, summaryInitialFact) },
                { build(it, callerInitialAp) })
        }
    }

    private fun build(
        exitAp: FAP,
        initialIndices: BitSet,
    ): CommonFactNDEdgeSubBuilder<FAP> {
        val initialAp = hashSetOf<InitialFactAp>()
        initialIndices.forEach { initialAp.add(getInitialApByIdx(it)) }

        return createBuilder()
            .setCallerNode(exitAp)
            .setCallerInitial(initialAp)
    }
}
