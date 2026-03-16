package org.opentaint.dataflow.jvm.ap.ifds.alias

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import java.util.BitSet

class AAInfoManager(
    private val elementToIndex: Object2IntOpenHashMap<AAInfo> = Object2IntOpenHashMap<AAInfo>(),
    private val indexToElement: MutableList<AAInfo> = mutableListOf(),
    private val heapElements: BitSet = BitSet()
) {
    init {
        elementToIndex.defaultReturnValue(NOT_PRESENT)
    }

    fun getOrAdd(x: AAInfo): Int {
        val index = elementToIndex.getInt(x)
        if (index != NOT_PRESENT) return index
        val newIndex = indexToElement.size
        elementToIndex[x] = newIndex
        indexToElement.add(x)

        if (x is HeapAlias) {
            heapElements.set(newIndex)
        }

        return newIndex
    }

    fun getElement(index: Int): AAInfo? {
        if (index >= indexToElement.size) return null
        return indexToElement[index]
    }

    fun getElementUncheck(index: Int): AAInfo {
        return getElement(index) ?: error("Expected element at $index, none found!")
    }

    fun isHeapAlias(index: Int): Boolean = heapElements.get(index)

    fun getHeapRefUnchecked(index: Int): HeapAlias =
        getElementUncheck(index) as? HeapAlias
            ?: error("Heap alias expected")

    fun replaceHeapInstance(index: Int, newInstance: Int): Int {
        val element = getHeapRefUnchecked(index)
        val newElement = element.copy(instance = newInstance)
        return getOrAdd(newElement)
    }

    companion object {
        private const val NOT_PRESENT: Int = -1
    }
}
