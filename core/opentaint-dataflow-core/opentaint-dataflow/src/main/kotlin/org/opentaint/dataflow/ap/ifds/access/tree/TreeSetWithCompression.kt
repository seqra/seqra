package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode as AccessTreeNode

/**
 * Per-instruction storage for the facts of one premise, held sparsely.
 *
 * This used to be one `arrayOfNulls(maxInstIdx + 1)` per column, allocated at full
 * instruction-count length whether or not the premise reached those instructions. Measured on a
 * ThingsBoard heap dump (`-Xmx12g`, `java/security/ssrf.yaml:ssrf`, 300 s, dumped at t=251 s):
 * across the 1,652,208 `EdgeNonUniverseExclusionMergingStorage` instances the arrays average
 * 29.65 slots of which **2.64 are non-null** - 8.9 % occupancy. 84.9 % of the storages use two
 * slots or fewer; the widest uses 377. The two dense arrays were 434.0 MiB of a 9.70 GiB live
 * heap.
 *
 * So instructions are kept as an ascending key array beside a value array of [columns] slots per
 * key. The two are wrapped in one immutable [Row] published through a single `@Volatile` field,
 * which makes a growing table strictly safer than the pair of plain arrays it replaces: a reader
 * takes one acquiring load and can never pair a resized key array with a stale value array.
 * In-place writes to an existing row keep exactly the visibility the plain arrays had.
 */
open class TreeSetWithCompression(
    private val columns: Int,
    val manager: TreeApManager,
) {
    /**
     * [keys] is ascending and distinct; [values] holds [columns] consecutive slots per key, so the
     * slots for `keys[i]` start at `i * columns`. Neither array is ever mutated in length or key
     * order once published - only the value slots of an existing row are overwritten.
     */
    protected class Row(@JvmField val keys: IntArray, @JvmField val values: Array<Any?>)

    @Volatile
    private var rows: Row? = null

    /** The current table, or `null` while no instruction has been written. */
    protected fun rows(): Row? = rows

    /** Offset into [Row.values] of the slots for [instIdx], or `-1` if it has no row. */
    protected fun offsetOf(row: Row, instIdx: Int): Int {
        val pos = findKey(row.keys, instIdx)
        return if (pos < 0) -1 else pos * columns
    }

    /**
     * Returns a table in which [instIdx] has a row, installing one if needed. Callers are the
     * storage's own writer, so the returned table is the current one until they publish another.
     */
    protected fun rowsForWrite(instIdx: Int): Row {
        val current = rows
        if (current != null && findKey(current.keys, instIdx) >= 0) return current

        val oldKeys = current?.keys ?: EMPTY_KEYS
        val oldValues = current?.values ?: EMPTY_VALUES
        val size = oldKeys.size
        val at = -findKey(oldKeys, instIdx) - 1

        val keys = IntArray(size + 1)
        System.arraycopy(oldKeys, 0, keys, 0, at)
        keys[at] = instIdx
        System.arraycopy(oldKeys, at, keys, at + 1, size - at)

        val values = arrayOfNulls<Any?>((size + 1) * columns)
        System.arraycopy(oldValues, 0, values, 0, at * columns)
        System.arraycopy(oldValues, at * columns, values, (at + 1) * columns, (size - at) * columns)

        return Row(keys, values).also { rows = it }
    }

    private var interner: AccessTreeSoftInterner? = null
    private var operationsBeforeIntern = INTERN_RATE
    private var maxTreeSize = 0L

    /**
     * Allocated on the first intern that actually reaches the interner, not on construction: only
     * 16.3 % of these storages ever pass the gates in [internImpl].
     */
    private fun interner(): AccessTreeSoftInterner =
        interner ?: AccessTreeSoftInterner(manager).also { interner = it }

    fun internIfRequired(node: AccessTreeNode): AccessTreeNode {
        if (node.size < SIZE_TO_FORCE_INTERN) return node
        return interner().intern(node)
    }

    /** [lastUpdated] is the node just stored in column 0, i.e. what `edges[idx]` used to be read back as. */
    fun intern(lastUpdated: AccessTreeNode?) {
        val row = rows ?: return

        internImpl(
            manager.cancellation,
            lastUpdated = lastUpdated,
            size = row.keys.size,
            maxNodeSize = maxTreeSize,
            updateMaxNodeSize = { maxTreeSize = it },
            decOperations = { operationsBeforeIntern-- },
            resetOperation = { operationsBeforeIntern = INTERN_RATE },
            getInterner = { interner() },
            getNode = { row.values[it * columns] as AccessTreeNode? },
            setNode = { i, n -> row.values[i * columns] = n }
        )
    }

    companion object {
        private val EMPTY_KEYS = IntArray(0)
        private val EMPTY_VALUES = arrayOfNulls<Any?>(0)

        /**
         * Rows are few - 93.8 % of the storages hold at most eight - so a scan beats the binary
         * search's branches up to [LINEAR_SCAN_LIMIT]. Returns the position of [key], or
         * `-(insertionPoint) - 1`, exactly like `Arrays.binarySearch`.
         */
        private fun findKey(keys: IntArray, key: Int): Int {
            if (keys.size > LINEAR_SCAN_LIMIT) return keys.binarySearch(key)

            for (i in keys.indices) {
                val k = keys[i]
                if (k == key) return i
                if (k > key) return -(i + 1)
            }
            return -(keys.size + 1)
        }

        private fun IntArray.binarySearch(key: Int): Int = java.util.Arrays.binarySearch(this, key)

        /**
         * `getInterner` is called only after every gate has passed, so a caller may allocate its
         * interner there rather than up front.
         */
        inline fun internImpl(
            cancellation: Cancellation,
            lastUpdated: AccessTreeNode?,
            size: Int,
            maxNodeSize: Long,
            updateMaxNodeSize: (Long) -> Unit,
            decOperations: () -> Int,
            resetOperation: () -> Unit,
            getInterner: () -> AccessTreeSoftInterner,
            crossinline getNode: (Int) -> AccessTreeNode?,
            crossinline setNode: (Int, AccessTreeNode) -> Unit,
        ) {
            lastUpdated?.let { updateMaxNodeSize(maxOf(maxNodeSize, it.size)) }

            if (decOperations() > 0) return
            if (maxNodeSize < MIN_SIZE_TO_INTERN) return
            resetOperation()

            getInterner().withInterner { interner, cache ->
                for (i in 0 until size) {
                    val node = getNode(i) ?: continue
                    setNode(i, node.internNodes(interner, cache))
                }
            }
        }

        const val MIN_SIZE_TO_INTERN = 100
        const val SIZE_TO_FORCE_INTERN = 100_000
        private const val INTERN_RATE = 100
        private const val LINEAR_SCAN_LIMIT = 8
    }
}
