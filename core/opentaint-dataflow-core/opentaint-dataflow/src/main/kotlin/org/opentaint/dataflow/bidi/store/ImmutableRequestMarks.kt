package org.opentaint.dataflow.bidi.store

import org.opentaint.dataflow.bidi.ap.TaintMark

/** Immutable membership. Emptiness never enumerates marks or computes cardinality. */
interface ImmutableRequestMarks : RequestMarkSelection {
    val isEmpty: Boolean
}

/** Borrow the original segments; no merged array, seen set, or interner entry. */
internal fun distinctUnionView(a: RequestMarkSet, b: RequestMarkSet,
                               checkpoint: () -> Unit): ImmutableRequestMarks = when {
    a.isEmpty -> b
    b.isEmpty || a === b -> a
    else -> UnionRequestMarks(a, b, checkpoint)
}

private class UnionRequestMarks(
    private val a: RequestMarkSet,
    private val b: RequestMarkSet,
    private val checkpoint: () -> Unit,
) : ImmutableRequestMarks {
    override val isEmpty: Boolean get() = false
    override fun containsId(id: Int): Boolean = a.containsId(id) || b.containsId(id)
    override fun cursor(): RequestMarkCursor = object : RequestMarkCursor {
        private var i = 0
        private var j = 0
        private var hasLast = false
        private var last = 0
        override val isComplete: Boolean get() = i == a.size && j == b.size
        override fun step(budget: Int, visit: (TaintMark) -> Unit): Int {
            require(budget > 0)
            var used = 0
            while (used < budget && !isComplete) {
                checkpoint()
                val id = if (j == b.size || (i < a.size && a.idAt(i) <= b.idAt(j)))
                    a.idAt(i++) else b.idAt(j++)
                used++
                if (!hasLast || id != last) {
                    hasLast = true
                    last = id
                    visit(TaintMark(id))
                }
            }
            return used
        }
    }
}

/** Cursor work is bounded even when a step only consumes duplicate source IDs. */
internal fun ImmutableRequestMarks.forEachMark(checkpoint: () -> Unit = {}, visit: (TaintMark) -> Unit) {
    val values = cursor()
    while (!values.isComplete) {
        checkpoint()
        values.step(256, visit)
    }
}
