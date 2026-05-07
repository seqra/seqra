package org.opentaint.dataflow.ap.ifds.access.util

import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.TypeInfoAccessor
import org.opentaint.dataflow.ap.ifds.TypeInfoGroupAccessor
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
    private val types = AccessorStorage()
    private val storageByBasicKind = arrayOf(fields, statics, taints)

    fun index(accessor: Accessor): AccessorIdx {
        val kind = when (accessor) {
            is FieldAccessor -> FIELD_KIND
            is ClassStaticAccessor -> STATIC_KIND
            is TaintMarkAccessor -> TAINT_KIND
            is TypeInfoAccessor -> TYPES_KIND
            is AnyAccessor -> return ANY_ACCESSOR_IDX
            is ElementAccessor -> return ELEMENT_ACCESSOR_IDX
            is FinalAccessor -> return FINAL_ACCESSOR_IDX
            is TypeInfoGroupAccessor -> return TYPE_INFO_GROUP_ACCESSOR_IDX
            is ValueAccessor -> return VALUE_ACCESSOR_IDX
        }

        if (kind.getAccessorBasicKind() != TYPES_OR_MARKER_KIND) {
            val storage = storageByBasicKind[kind]
            val idx = storage.index(accessor)
            return setAccessorKind(idx, kind, BASIC_KIND_BITS)
        } else {
            check(kind == TYPES_KIND)
            val idx = types.index(accessor)
            return setAccessorKind(idx, TYPES_KIND, TYPES_OR_MARKER_KIND_BITS)
        }
    }

    fun accessor(idx: AccessorIdx): Accessor? {
        val kind = idx.getAccessorBasicKind()
        if (kind != TYPES_OR_MARKER_KIND) {
            val storage = storageByBasicKind[kind]
            val accessorIdx = idx.getAccessorIdx(BASIC_KIND_BITS)
            return storage.getOrNull(accessorIdx)
        }

        val typesOrMarkerKind = idx.getAccessorKind(TYPES_OR_MARKER_KIND_MASK)
        if (typesOrMarkerKind == TYPES_KIND) {
            val accessorIdx = idx.getAccessorIdx(TYPES_OR_MARKER_KIND_BITS)
            return types.getOrNull(accessorIdx)
        }

        return when (idx) {
            FINAL_ACCESSOR_IDX -> FinalAccessor
            ELEMENT_ACCESSOR_IDX -> ElementAccessor
            VALUE_ACCESSOR_IDX -> ValueAccessor
            ANY_ACCESSOR_IDX -> AnyAccessor
            TYPE_INFO_GROUP_ACCESSOR_IDX -> TypeInfoGroupAccessor
            else -> error("Unexpected accessor $idx")
        }
    }

    companion object {
        const val BASIC_KIND_BITS = 2
        const val BASIC_KIND_MASK = 0b11
        const val FIELD_KIND = 0b00
        const val STATIC_KIND = 0b01
        const val TAINT_KIND = 0b10
        const val TYPES_OR_MARKER_KIND = 0b11

        const val TYPES_OR_MARKER_KIND_BITS = 1 + BASIC_KIND_BITS
        const val TYPES_OR_MARKER_KIND_MASK = (1 shl BASIC_KIND_BITS) or BASIC_KIND_MASK

        const val MARKERS_KIND = (0 shl BASIC_KIND_BITS) or TYPES_OR_MARKER_KIND
        const val TYPES_KIND = (1 shl BASIC_KIND_BITS) or TYPES_OR_MARKER_KIND

        const val FINAL_ACCESSOR_IDX = (0 shl TYPES_OR_MARKER_KIND_BITS) or MARKERS_KIND
        const val ELEMENT_ACCESSOR_IDX = (1 shl TYPES_OR_MARKER_KIND_BITS) or MARKERS_KIND
        const val VALUE_ACCESSOR_IDX = (2 shl TYPES_OR_MARKER_KIND_BITS) or MARKERS_KIND
        const val ANY_ACCESSOR_IDX = (3 shl TYPES_OR_MARKER_KIND_BITS) or MARKERS_KIND
        const val TYPE_INFO_GROUP_ACCESSOR_IDX = (4 shl TYPES_OR_MARKER_KIND_BITS) or MARKERS_KIND

        @Suppress("NOTHING_TO_INLINE")
        inline fun AccessorIdx.getAccessorBasicKind(): Int = getAccessorKind(BASIC_KIND_MASK)

        @Suppress("NOTHING_TO_INLINE")
        inline fun AccessorIdx.getAccessorKind(kindMask: Int): Int = this and kindMask

        @Suppress("NOTHING_TO_INLINE")
        inline fun AccessorIdx.getAccessorIdx(kindBits: Int): Int = this shr kindBits

        @Suppress("NOTHING_TO_INLINE")
        inline fun setAccessorKind(accessorIdx: Int, kind: Int, kindBits: Int): AccessorIdx =
            (accessorIdx shl kindBits) or kind

        fun AccessorIdx.isFieldAccessor(): Boolean = getAccessorBasicKind() == FIELD_KIND
        fun AccessorIdx.isStaticAccessor(): Boolean = getAccessorBasicKind() == STATIC_KIND
        fun AccessorIdx.isTaintMarkAccessor(): Boolean = getAccessorBasicKind() == TAINT_KIND
        fun AccessorIdx.isTypeInfoAccessor(): Boolean = getAccessorKind(TYPES_OR_MARKER_KIND_MASK) == TYPES_KIND

        fun AccessorIdx.isAlwaysUnrollNext(): Boolean =
            isTaintMarkAccessor()
                    || (this == FINAL_ACCESSOR_IDX)
                    || (this == VALUE_ACCESSOR_IDX)
                    || (this == TYPE_INFO_GROUP_ACCESSOR_IDX)
                    || isTypeInfoAccessor()
    }
}
