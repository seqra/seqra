package org.opentaint.dataflow.jvm.ap.ifds.alias

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap

class AAInfoManager(
    private val elementToIndex: Object2IntOpenHashMap<DSUAliasAnalysis.AAInfo> = Object2IntOpenHashMap<DSUAliasAnalysis.AAInfo>(),
    private val indexToElement: MutableList<DSUAliasAnalysis.AAInfo> = mutableListOf()
) {
    init {
        elementToIndex.defaultReturnValue(NOT_PRESENT)
    }

    fun getOrAdd(x: DSUAliasAnalysis.AAInfo): Int {
        val index = elementToIndex.getInt(x)
        if (index != NOT_PRESENT) return index
        val newIndex = indexToElement.size
        elementToIndex[x] = newIndex
        indexToElement.add(x)
        return newIndex
    }

    fun getElement(index: Int): DSUAliasAnalysis.AAInfo? {
        if (index >= indexToElement.size) return null
        return indexToElement[index]
    }

    fun getElementUncheck(index: Int): DSUAliasAnalysis.AAInfo {
        return getElement(index) ?: error("Expected element at $index, none found!")
    }

    companion object {
        private const val NOT_PRESENT: Int = -1
    }
}
