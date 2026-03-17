package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.util.ConcurrentReadSafeObject2IntMap
import org.opentaint.dataflow.util.getOrCreateIndex
import org.opentaint.dataflow.util.object2IntMap

typealias AccessorIdx = Int

class AccessorInterner {
    private val indices = object2IntMap<Accessor>()
    private val accessors = ArrayList<Accessor>()

    fun index(accessor: Accessor): AccessorIdx {
        val currentIndex = indices.getInt(accessor)
        if (currentIndex != ConcurrentReadSafeObject2IntMap.NO_VALUE) return currentIndex

        synchronized(this) {
            return indices.getOrCreateIndex(accessor) { newIdx ->
                check(newIdx == accessors.size)
                accessors.add(accessor)
                return newIdx
            }
        }
    }

    fun accessor(idx: AccessorIdx): Accessor? =
        accessors.getOrNull(idx)
}
