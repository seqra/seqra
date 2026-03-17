package org.opentaint.dataflow.ap.ifds.access.common.ndf2f

import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.common.CommonNDF2FSummary
import org.opentaint.dataflow.ap.ifds.access.common.ndf2f.DefaultNDF2FSummaryStorage.Storage
import org.opentaint.dataflow.util.collectToListWithPostProcess
import org.opentaint.dataflow.util.forEach
import java.util.BitSet

abstract class DefaultNDF2FSummaryStorage<IAP, FAP : Any> :
    DefaultNDF2FStorage<FAP, Storage<IAP, FAP>, Storage<IAP, FAP>>(),
    CommonNDF2FSummary.Storage<FAP> {

    abstract fun createBuilder(): CommonNDF2FSummary.NDF2FBBuilder<FAP>

    interface Storage<IAP, FAP> : DefaultStorage<FAP, Storage<IAP, FAP>> {
        val storageIdx: Int

        fun getAndResetDelta(delta: MutableList<FAP>)
        fun collectTo(dst: MutableList<FAP>)
    }

    override fun add(
        edges: List<CommonNDF2FSummary.SimpleNdEdge<FAP>>,
        added: MutableList<CommonNDF2FSummary.NDF2FBBuilder<FAP>>,
    ) {
        val modifiedIndices = BitSet()
        for (edge in edges) {
            val modifiedStorage = addToStorage(edge.initial, edge.final) ?: continue
            modifiedIndices.set(modifiedStorage.storageIdx)
        }

        modifiedIndices.forEach { storageIdx ->
            collectToListWithPostProcess(
                added,
                { exitApStorage[storageIdx].getAndResetDelta(it) },
                { build(it, initialApStorage[storageIdx]) }
            )
        }
    }

    abstract fun relevantInitialAp(summaryInitialFactPattern: FinalFactAp): BitSet

    override fun filterTo(
        dst: MutableList<CommonNDF2FSummary.NDF2FBBuilder<FAP>>,
        initialFactPattern: FinalFactAp?,
    ) {
        if (initialFactPattern == null) {
            for (i in exitApStorage.indices) {
                collectFromStorage(dst, i)
            }

            return
        }

        val relevantInitialAp = relevantInitialAp(initialFactPattern)
        relevantInitialAp.forEach { apIdx ->
            initialApStorageIndex.getOrNull(apIdx)?.forEach { storageIdx ->
                collectFromStorage(dst, storageIdx)
            }
        }
    }

    private fun collectFromStorage(
        dst: MutableList<CommonNDF2FSummary.NDF2FBBuilder<FAP>>,
        storageIdx: Int,
    ) {
        val initialStorage = initialApStorage.getOrNull(storageIdx) ?: return
        val storage = exitApStorage.getOrNull(storageIdx) ?: return

        collectToListWithPostProcess(dst, { storage.collectTo(it) }, { build(it, initialStorage) })
    }

    private fun build(
        exitAp: FAP,
        initialIndices: BitSet,
    ): CommonNDF2FSummary.NDF2FBBuilder<FAP> {
        val initialAp = hashSetOf<InitialFactAp>()
        initialIndices.forEach { initialAp.add(getInitialApByIdx(it)) }

        return createBuilder()
            .setExitAp(exitAp)
            .setInitial(initialAp)
    }
}
