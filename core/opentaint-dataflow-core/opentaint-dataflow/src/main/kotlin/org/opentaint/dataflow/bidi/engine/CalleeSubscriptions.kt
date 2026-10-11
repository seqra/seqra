package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.store.AddedFactStore
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.PathTrie

/** The caller runner owns exact added groups, indexed as the transpose of replay. */
class CalleeSubscriptions(private val ops: ApOps) {
    private val trees = AddedFactStore(ops.manager)
    private val parts = ArrayList<Subscription>()
    private val index = PathTrie<Int>(ops.manager.cancellation)
    private val zeroParts = LinkedHashMap<CallerRef, Int>()

    fun add(sub: Subscription): Subscription? {
        ops.manager.cancellation.checkpoint()
        if (sub.zeroOnly) {
            check(sub.ref.premise.isZero) { "A zero-only subscription needs a zero caller premise" }
            check(sub.added is Reach) { "A zero-only subscription needs REACH" }
            check(sub.linkLayer == sub.added.layer) { "The link layer must be the added fact's layer" }
            if (sub.ref in zeroParts) return null
            zeroParts[sub.ref] = parts.size
            parts += sub
            return sub
        }
        val delta = trees.add(sub.ref, sub.linkLayer, sub.added) ?: return null
        val part = sub.copy(added = delta)
        val id = parts.size
        parts += part
        for (leaf in ops.leaves(delta)) {
            ops.manager.cancellation.checkpoint()
            index.add(leaf.fact.base, leaf.fact.path.toIntArray(), id)
        }
        return part
    }

    fun candidates(pub: Publication, member: Int, mode: ApMode): Sequence<Subscription> = sequence {
        ops.manager.cancellation.checkpoint()
        val initial = pub.premise.member(member)
        val ids = LinkedHashSet<Int>()
        if (mode.run1) ids += index.lookupExtensions(initial.base, initial.pathData)
        else {
            ids += index.lookupPrefixes(initial.base, initial.pathData)
            if (initial.mark is MarkSlot.Star) ids += index.lookupExtensions(initial.base, initial.pathData)
        }
        if (pub.premise.isZero) ids += zeroParts.values
        for (id in ids) {
            ops.manager.cancellation.checkpoint()
            yield(parts[id])
        }
    }
}
