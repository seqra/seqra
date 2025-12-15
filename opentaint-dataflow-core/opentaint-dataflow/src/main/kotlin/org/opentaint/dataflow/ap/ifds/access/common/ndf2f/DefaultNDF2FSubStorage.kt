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

    abstract fun relevantStorageIndices(summaryInitialFact: IAP): BitSet

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
        relevantStorageIndices(summaryInitialFact).forEach { storageIdx ->
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
