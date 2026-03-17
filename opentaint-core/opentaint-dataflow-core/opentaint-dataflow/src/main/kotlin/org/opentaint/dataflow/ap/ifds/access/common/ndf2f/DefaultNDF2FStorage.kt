package org.opentaint.dataflow.ap.ifds.access.common.ndf2f

import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.common.ndf2f.DefaultNDF2FStorage.DefaultStorage
import org.opentaint.dataflow.util.forEach
import java.util.BitSet

abstract class DefaultNDF2FStorage<FAP, StorageResult : Any, Storage : DefaultStorage<FAP, StorageResult>> {
    abstract fun initialApIdx(ap: InitialFactAp): Int
    abstract fun getInitialApByIdx(idx: Int): InitialFactAp

    interface DefaultStorage<FAP, StorageResult : Any> {
        fun add(element: FAP): StorageResult?
    }

    abstract fun createStorage(idx: Int): Storage

    val initialApStorageIndex = arrayListOf<BitSet>()
    val initialApStorage = arrayListOf<BitSet>()
    val exitApStorage = arrayListOf<Storage>()

    fun addToStorage(
        callerInitial: Set<InitialFactAp>,
        callerExitAp: FAP,
    ): StorageResult? {
        val initialApIndices = BitSet()
        callerInitial.forEach {
            val apIdx = initialApIdx(it)
            while (apIdx >= initialApStorageIndex.size) {
                initialApStorageIndex.add(BitSet())
            }
            initialApIndices.set(apIdx)
        }

        val relevantInitialApStorageIdx = BitSet(initialApStorage.size).also { it.set(0, initialApStorage.size) }
        initialApIndices.forEach {
            relevantInitialApStorageIdx.and(initialApStorageIndex[it])
        }

        relevantInitialApStorageIdx.forEach { currentIndex ->
            if (initialApStorage[currentIndex] == initialApIndices) {
                val current = exitApStorage[currentIndex]
                return current.add(callerExitAp)
            }
        }

        val newIdx = initialApStorage.size
        initialApStorage.add(initialApIndices)
        initialApIndices.forEach { initialApStorageIndex[it].set(newIdx) }

        val storage = createStorage(newIdx)
        exitApStorage.add(storage)
        return storage.add(callerExitAp)
    }
}
