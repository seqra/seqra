package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx

/** An immutable path cell. The manager interns the suffix before the parent. */
class PathNode internal constructor(
    @JvmField val accessor: AccessorIdx,
    @JvmField val next: PathNode?,
    internal val manager: ApManager,
) {
    init { check(accessor.isPathAccessor()) { "W5: $accessor is not a path accessor" } }

    @JvmField val size: Int = Math.addExact(1, next?.size ?: 0)
    @JvmField val counted: Int = Math.addExact(if (accessor.isCounted()) 1 else 0, next?.counted ?: 0)
    private val hash: Int = accessor + 17 * (next?.hashCode() ?: 0)

    override fun equals(other: Any?): Boolean = this === other ||
        (other is PathNode && accessor == other.accessor && next === other.next)
    override fun hashCode(): Int = hash

    fun toIntArray(): IntArray {
        val result = IntArray(size)
        var node: PathNode? = this
        var index = 0
        while (node != null) {
            result[index++] = node.accessor
            node = node.next
        }
        return result
    }

    fun toList(): List<AccessorIdx> = toIntArray().asList()
}
