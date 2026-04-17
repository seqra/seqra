package org.opentaint.dataflow.ap.ifds.access.util

import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.ValueAccessor
import org.opentaint.dataflow.util.ConcurrentReadSafeObject2IntMap
import org.opentaint.dataflow.util.getOrCreateIndex
import org.opentaint.dataflow.util.object2IntMap

typealias AccessorIdx = Int

class AccessorInterner {
    private class AccessorStorage {
        private val indices = object2IntMap<Accessor>()
        private val accessors = ArrayList<Accessor>()

        fun index(accessor: Accessor): Int {
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

        fun getOrNull(idx: Int): Accessor? = accessors.getOrNull(idx)
    }

    private val fields = AccessorStorage()
    private val statics = AccessorStorage()
    private val taints = AccessorStorage()
    private val others = AccessorStorage()
    private val storageByKind = arrayOf(fields, statics, taints, others)

    fun index(accessor: Accessor): AccessorIdx {
        val kind = when (accessor) {
            is FieldAccessor -> FIELD_KIND
            is ClassStaticAccessor -> STATIC_KIND
            is TaintMarkAccessor -> TAINT_KIND
            else -> OTHER_KIND
        }

        if (kind != OTHER_KIND) {
            val storage = storageByKind[kind]
            val idx = storage.index(accessor)
            return setAccessorKind(idx, kind)
        }

        return when (accessor) {
            AnyAccessor -> ANY_ACCESSOR_IDX
            ElementAccessor -> ELEMENT_ACCESSOR_IDX
            FinalAccessor -> FINAL_ACCESSOR_IDX
            ValueAccessor -> VALUE_ACCESSOR_IDX
            else -> {
                val storage = storageByKind[OTHER_KIND]
                val idx = storage.index(accessor) + OTHER_ACCESSOR_START
                setAccessorKind(idx, kind)
            }
        }
    }

    fun accessor(idx: AccessorIdx): Accessor? {
        val kind = idx.getAccessorKind()
        if (kind != OTHER_KIND) {
            val storage = storageByKind[kind]
            return storage.getOrNull(idx.getAccessorIdx())
        }

        return when (idx) {
            FINAL_ACCESSOR_IDX -> FinalAccessor
            ELEMENT_ACCESSOR_IDX -> ElementAccessor
            VALUE_ACCESSOR_IDX -> ValueAccessor
            ANY_ACCESSOR_IDX -> AnyAccessor
            else -> {
                val storage = storageByKind[OTHER_KIND]
                storage.getOrNull(idx.getAccessorIdx() - OTHER_ACCESSOR_START)
            }
        }
    }

    companion object {
        const val FIELD_KIND = 0
        const val STATIC_KIND = 1
        const val TAINT_KIND = 2
        const val OTHER_KIND = 3

        const val OTHER_ACCESSOR_START = 4
        const val FINAL_ACCESSOR_IDX = (0 shl 2) or OTHER_KIND
        const val ELEMENT_ACCESSOR_IDX = (1 shl 2) or OTHER_KIND
        const val VALUE_ACCESSOR_IDX = (2 shl 2) or OTHER_KIND
        const val ANY_ACCESSOR_IDX = (3 shl 2) or OTHER_KIND

        @Suppress("NOTHING_TO_INLINE")
        inline fun AccessorIdx.getAccessorKind(): Int = this and 0x3

        @Suppress("NOTHING_TO_INLINE")
        inline fun AccessorIdx.getAccessorIdx(): Int = this shr 2

        @Suppress("NOTHING_TO_INLINE")
        inline fun setAccessorKind(accessorIdx: Int, kind: Int): AccessorIdx =
            (accessorIdx shl 2) or kind

        fun AccessorIdx.isFieldAccessor(): Boolean = getAccessorKind() == FIELD_KIND
        fun AccessorIdx.isStaticAccessor(): Boolean = getAccessorKind() == STATIC_KIND
        fun AccessorIdx.isTaintMarkAccessor(): Boolean = getAccessorKind() == TAINT_KIND
    }
}
