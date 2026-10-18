package org.opentaint.dataflow.bidi.engine

import org.opentaint.ir.api.common.CommonMethod
import java.util.concurrent.ConcurrentHashMap

/** Fixed method-call graph. Reuses prescan's SCCs; queries do not depend on
 * fields, marks, call contexts or later binding admission. */
class FixedPrescanReachability private constructor(
    private val components: Map<CommonMethod, Int>,
    private val successors: Array<IntArray>,
    private val targets: Map<CommonMethod, Set<CommonMethod>>,
    private val checkpoint: () -> Unit,
) {
    private val answers = ConcurrentHashMap<Long, Boolean>()

    val componentCount: Int get() = successors.size
    val cachedPairs: Int get() = answers.size

    fun component(method: CommonMethod): Int? = components[method]
    fun contains(method: CommonMethod): Boolean = method in components

    /** Only a recorded direct call certifies omission of its actual relay.
     * Unknown/dynamic calls must retain their ordinary request subscription. */
    fun containsEdge(caller: CommonMethod, callee: CommonMethod): Boolean =
        targets[caller]?.contains(callee) == true

    fun isReachable(from: CommonMethod, target: CommonMethod): Boolean {
        checkpoint()
        if (from == target) return true
        val start = components[from] ?: return false
        val finish = components[target] ?: return false
        if (start == finish) return true
        val key = (start.toLong() shl 32) or (finish.toLong() and 0xffffffffL)
        return answers.computeIfAbsent(key) { search(start, finish) }
    }

    private fun search(start: Int, finish: Int): Boolean {
        val seen = BooleanArray(successors.size)
        val stack = IntArray(successors.size)
        var count = 1
        stack[0] = start
        seen[start] = true
        while (count != 0) {
            checkpoint()
            val at = stack[--count]
            for (next in successors[at]) {
                checkpoint()
                if (next == finish) return true
                if (!seen[next]) {
                    seen[next] = true
                    stack[count++] = next
                }
            }
        }
        return false
    }

    companion object {
        /** Reflexivity remains useful for synthetic adapters. With no graph,
         * distinct methods are never covered and no relay may be omitted. */
        fun empty(checkpoint: () -> Unit = {}): FixedPrescanReachability =
            FixedPrescanReachability(emptyMap(), emptyArray(), emptyMap(), checkpoint)

        internal fun fromComponents(
            components: Map<CommonMethod, Int>,
            successors: Array<IntArray>,
            targets: Map<CommonMethod, Set<CommonMethod>>,
            checkpoint: () -> Unit,
        ): FixedPrescanReachability = FixedPrescanReachability(components, successors, targets, checkpoint)
    }
}
