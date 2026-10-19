package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.BASIC_KIND_BITS
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.ELEMENT_ACCESSOR_IDX
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.FIELD_KIND
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.STATIC_KIND
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.getAccessorIdx
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isFieldAccessor
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isStaticAccessor
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.setAccessorKind
import java.util.concurrent.ConcurrentHashMap

/** Publish each value before its ID. Reads need one volatile array read and no retry loop. */
class AccessorTable {
    internal class Storage<T : Any> {
        private val ids = ConcurrentHashMap<T, Int>()
        @Volatile private var values: Array<Any?> = arrayOfNulls(64)
        private var size = 0

        fun index(value: T): Int = ids[value] ?: synchronized(this) {
            ids[value] ?: run {
                val index = size++
                val array = if (index < values.size) values else values.copyOf(Math.multiplyExact(2, values.size))
                array[index] = value
                values = array
                ids[value] = index
                index
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun value(index: Int): T = checkNotNull(values.getOrNull(index)) { "Unknown value ID: $index" } as T
    }

    private val fields = Storage<Accessor>()
    private val statics = Storage<Accessor>()

    fun index(accessor: Accessor): AccessorIdx = when (accessor) {
        is FieldAccessor -> setAccessorKind(fields.index(accessor), FIELD_KIND, BASIC_KIND_BITS)
        is ClassStaticAccessor -> setAccessorKind(statics.index(accessor), STATIC_KIND, BASIC_KIND_BITS)
        ElementAccessor -> ELEMENT_ACCESSOR_IDX
        else -> error("W5: $accessor is not a path accessor")
    }

    fun accessor(index: AccessorIdx): Accessor = when {
        index.isFieldAccessor() -> fields.value(index.getAccessorIdx(BASIC_KIND_BITS))
        index.isStaticAccessor() -> statics.value(index.getAccessorIdx(BASIC_KIND_BITS))
        index == ELEMENT_ACCESSOR_IDX -> ElementAccessor
        else -> error("W5: $index is not a path accessor")
    }
}

fun AccessorIdx.isCounted(): Boolean = isFieldAccessor() || this == ELEMENT_ACCESSOR_IDX
fun AccessorIdx.isClass(): Boolean = isStaticAccessor()
internal fun AccessorIdx.isPathAccessor(): Boolean = this >= 0 && (isCounted() || isClass())
