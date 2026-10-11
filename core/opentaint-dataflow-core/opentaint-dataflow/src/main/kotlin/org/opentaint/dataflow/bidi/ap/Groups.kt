package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.SoftReferenceManager

/** FLOW values for one base, separated by layer and both exclusions. One store writer owns this group. */
class FlowGroup(private val m: ApManager, private val interner: TrieInterner<FlowLeaf>) {
    private val trees = arrayOf(ArrayList<FlowTree>(1), ArrayList<FlowTree>(1))

    fun all(): Sequence<FlowTree> = trees[0].asSequence() + trees[1].asSequence()

    /** T4: return the new part, or the merged value when T2 changes an annotation. */
    fun add(t: FlowTree): FlowTree? {
        val list = trees[t.layer.ordinal]
        var pending = t
        while (true) {
            m.cancellation.checkpoint()
            val i = list.indexOfFirst {
                m.cancellation.checkpoint()
                it.exclusion == pending.exclusion && it.markExclusion == pending.markExclusion
            }
            if (i >= 0) {
                val rest = subtractSubsumed(pending, list) ?: return null
                val (merged, delta) = FlowAlgebra.mergeAddDelta(list[i].root, rest.root, m.cancellation)
                if (delta == null) return null
                list[i] = list[i].withRoot(interner.internIfRequired(merged))
                return m.flowTree(pending.base, pending.layer, pending.exclusion, pending.markExclusion, delta)
            }

            val k = list.indexOfFirst {
                m.cancellation.checkpoint()
                it.root == pending.root &&
                    (it.markExclusion == pending.markExclusion || it.exclusion == pending.exclusion)
            }
            if (k >= 0) {
                val stored = list[k]
                val merged = m.flowTree(pending.base, pending.layer,
                    stored.exclusion.intersect(pending.exclusion),
                    stored.markExclusion.intersect(pending.markExclusion), stored.root)
                if (merged.exclusion == stored.exclusion && merged.markExclusion == stored.markExclusion) return null
                list.removeAt(k)
                pending = merged
                continue
            }

            val fresh = subtractSubsumed(pending, list) ?: return null
            list += fresh.withRoot(interner.internIfRequired(fresh.root))
            return fresh
        }
    }

    private fun subtractSubsumed(t: FlowTree, others: List<FlowTree>): FlowTree? {
        var root = t.root
        for (stored in others) {
            m.cancellation.checkpoint()
            val marks = stored.markExclusion.isSubsetOf(t.markExclusion)
            val atOk = marks && (t.layer == Layer.DEMAND || stored.exclusion.isSubsetOf(t.exclusion))
            val belowOk = marks && t.layer == Layer.DEMAND
            root = FlowAlgebra.subtract(root, stored.root, FlowLeaf.NONE,
                at = { if (atOk) it else FlowLeaf.NONE },
                below = { if (belowOk) it else FlowLeaf.NONE },
                cancellation = m.cancellation) ?: return null
        }
        return if (root === t.root) t else t.withRoot(root)
    }
}

/** TAINT values for one base. Normal must leaves retain E and never subsume exact leaves. */
class TaintGroup(private val m: ApManager, private val interner: TrieInterner<TaintLeaves>) {
    private val trees = arrayOf(ArrayList<TaintTree>(1), ArrayList<TaintTree>(1))

    fun all(): Sequence<TaintTree> = trees[0].asSequence() + trees[1].asSequence()

    fun add(t: TaintTree): TaintTree? {
        val alg = m.taintAlg
        val list = trees[t.layer.ordinal]
        var pending = t
        while (true) {
            m.cancellation.checkpoint()
            val sameKey = list.indexOfFirst {
                m.cancellation.checkpoint()
                it.exclusion == pending.exclusion
            }
            if (sameKey < 0) {
                val k = list.indexOfFirst {
                    m.cancellation.checkpoint()
                    it.root == pending.root
                }
                if (k >= 0) {
                    val stored = list[k]
                    val exclusion = stored.exclusion.intersect(pending.exclusion)
                    if (exclusion == stored.exclusion) return null
                    list.removeAt(k)
                    pending = m.taintTree(pending.base, pending.layer, stored.root, exclusion)
                    continue
                }
            }

            val rest = subtractSubsumed(pending, list) ?: return null
            // Subtraction can remove the last must leaf. Its canonical key is then Empty E.
            val i = list.indexOfFirst {
                m.cancellation.checkpoint()
                it.exclusion == rest.exclusion
            }
            if (i < 0) {
                list += rest.withRoot(interner.internIfRequired(rest.root))
                return rest
            }
            val (merged, delta) = alg.mergeAddDelta(list[i].root, rest.root, m.cancellation)
            if (delta == null) return null
            list[i] = list[i].withRoot(interner.internIfRequired(merged))
            return m.taintTree(pending.base, pending.layer, delta, rest.exclusion)
        }
    }

    private fun subtractSubsumed(t: TaintTree, others: List<TaintTree>): TaintTree? {
        val alg = m.taintAlg
        val normal = t.layer == Layer.NORMAL
        var root = t.root
        for (stored in others) {
            m.cancellation.checkpoint()
            val anyAt = stored.exclusion.isSubsetOf(t.exclusion)
            root = alg.subtract(root, stored.root, TaintLeaves.EMPTY,
                at = { alg.leaves(if (normal) it.exact else it.exact + it.any,
                    if (anyAt) it.any else MarkSet.EMPTY) },
                below = alg.belowIn(t.layer),
                admits = stored.exclusion::admits,
                cancellation = m.cancellation) ?: return null
        }
        return if (root === t.root) t else t.withRoot(root)
    }
}

/** All conclusion bases and kinds for one premise key. REACH has one bit per layer. */
class ConclusionGroup(private val m: ApManager, private val interners: StoreInterners) {
    private val reach = BooleanArray(2)
    private val flow = HashMap<AccessPathBase, FlowGroup>(2)
    private val taint = HashMap<AccessPathBase, TaintGroup>(2)

    fun add(f: Facts): Facts? {
        m.cancellation.checkpoint()
        return when (f) {
            is Reach -> if (reach[f.layer.ordinal]) null else f.also { reach[f.layer.ordinal] = true }
            is FlowTree -> flow.getOrPut(f.base) { FlowGroup(m, interners.flow) }.add(f)
            is TaintTree -> taint.getOrPut(f.base) { TaintGroup(m, interners.taint) }.add(f)
        }
    }

    fun all(): Sequence<Facts> = Layer.entries.asSequence().filter { reach[it.ordinal] }.map(Reach::of) +
        flow.values.asSequence().flatMap { it.all() } + taint.values.asSequence().flatMap { it.all() }
}

/** Soft trie caches for one store, with a separate table for each leaf kind. */
class StoreInterners(refs: SoftReferenceManager, private val cancellation: Cancellation) {
    val flow = TrieInterner<FlowLeaf>(refs, cancellation)
    val taint = TrieInterner<TaintLeaves>(refs, cancellation)

    fun internIfRequired(f: Facts): Facts {
        cancellation.checkpoint()
        return when (f) {
            is Reach -> f
            is FlowTree -> flow.internIfRequired(f.root).let { if (it === f.root) f else f.withRoot(it) }
            is TaintTree -> taint.internIfRequired(f.root).let { if (it === f.root) f else f.withRoot(it) }
        }
    }
}
