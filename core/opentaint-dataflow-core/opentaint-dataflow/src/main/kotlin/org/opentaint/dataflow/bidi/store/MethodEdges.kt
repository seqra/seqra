package org.opentaint.dataflow.bidi.store

import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.GroundingSupport
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.BitSet
import java.util.IdentityHashMap

/** One writer owns the method's statement/premise values for a run. */
class MethodEdgeStore(private val m: ApManager, private val method: MethodKey, private val lm: LanguageManager,
                      private val fieldLimit: Int) {
    init { require(fieldLimit >= 1) { "The field limit must be positive" } }

    private val size = lm.getMaxInstIndex(method.method) + 1
    private val reach = IdentityHashMap<PremiseKey, Array<BitSet>>()
    private val groups = IdentityHashMap<PremiseKey, Array<ConclusionGroup?>>()
    private val interners = m.newInterners()
    private val ops = ApOps(m)
    private data class SupportKey(val statement: Int, val premise: PremiseKey)
    private data class SupportLeaf(val pattern: Pattern, val layer: Layer)
    /** Only a statement/premise with nontrivial support needs semantic leaf metadata.
     * T2 can replace headers, so the directory spans every header in that owned row.
     * Neither keys nor values retain old merged Facts roots. */
    private val supports = HashMap<SupportKey, LinkedHashMap<SupportLeaf, GroundingSupport>>()
    var groundingTrueRows: Long = 0
        private set
    var groundingGuardedRows: Long = 0
        private set

    /** Admit the AP value once, then emit only the cofactor that changed.
     * T4 may return a merged body containing older, differently guarded leaves. */
    fun addSupported(node: CommonInst, premise: PremiseKey, facts: Facts,
                     support: GroundingSupport, emit: (Facts, GroundingSupport) -> Unit) {
        m.cancellation.checkpoint()
        checkValue(premise, facts)
        val key = SupportKey(lm.getInstIndex(node), premise)
        val rows = supports[key] ?: if (support != GroundingSupport.TRUE) {
            LinkedHashMap<SupportLeaf, GroundingSupport>().also { rows ->
                // Read the old owned value BEFORE adding a new, possibly broader body.
                for ((_, previous) in edgesAt(node, premise)) {
                    for (leaf in ops.leaves(previous)) {
                        m.cancellation.checkpoint()
                        rows[SupportLeaf(leaf, previous.layer)] = GroundingSupport.TRUE
                    }
                }
                supports[key] = rows
                groundingTrueRows += rows.size
            }
        } else null
        val delta = add(node, premise, facts)
        if (rows == null) {
            delta?.let { emit(it, GroundingSupport.TRUE) }
            return
        }
        for (leaf in ops.leaves(facts)) {
            m.cancellation.checkpoint()
            val part = SupportLeaf(leaf, facts.layer)
            val before = rows[part]
            val after = before?.or(support) ?: support
            if (before == after) continue
            if (before == GroundingSupport.TRUE) groundingTrueRows--
            else if (before != null) groundingGuardedRows--
            if (after == GroundingSupport.TRUE) groundingTrueRows++ else groundingGuardedRows++
            rows[part] = after
            emit(m.factsOf(leaf.fact, leaf.exclusion, facts.layer), after)
        }
    }

    /** An unchanged TRUE traversal stays in the transient work burst. Earlier
     * traversals have either queued explicit TRUE work or already delivered it
     * to the ordinary rule/input/publication stores. Keep no second census. */
    fun forwardUnchanged(node: CommonInst, premise: PremiseKey, facts: Facts,
                         support: GroundingSupport, emit: (Facts, GroundingSupport) -> Unit) {
        m.cancellation.checkpoint()
        val key = SupportKey(lm.getInstIndex(node), premise)
        if (support == GroundingSupport.TRUE && key !in supports) emit(facts, support)
        else addSupported(node, premise, facts, support, emit)
    }

    /** T4: new leaves, or the whole merged value when a T2 annotation changes. */
    fun add(node: CommonInst, premise: PremiseKey, f: Facts): Facts? {
        m.cancellation.checkpoint()
        checkValue(premise, f)
        val index = lm.getInstIndex(node)
        when (f) {
            is Reach -> {
                val bits = reach.getOrPut(premise) { arrayOf(BitSet(size), BitSet(size)) }[f.layer.ordinal]
                if (bits[index]) return null
                bits.set(index)
                return f
            }
            is FlowTree, is TaintTree -> Unit
        }
        val statements = groups.getOrPut(premise) { arrayOfNulls(size) }
        val group = statements[index] ?: ConclusionGroup(m, interners).also { statements[index] = it }
        return group.add(f)
    }

    private fun checkValue(premise: PremiseKey, f: Facts) {
        check(f.base !is AccessPathBase.CleanerTemporary) { "A field-action temporary cannot enter method storage" }
        check((f is FlowTree) == premise.isFlow) { "K6: the conclusion kind follows the premise" }
        check(premise !is PremiseSet || f is TaintTree) { "A multi-premise edge must be TAINT" }
        when (f) {
            is Reach -> Unit
            is FlowTree -> assert(f.root.boundedDepth <= fieldLimit) { "W3: a stored conclusion must be within the field limit" }
            is TaintTree -> assert(f.root.boundedDepth <= fieldLimit) { "W3: a stored conclusion must be within the field limit" }
        }
    }

    /** Test view of all complete stored values at a statement. */
    fun edgesAt(node: CommonInst, premise: PremiseKey? = null): Sequence<Pair<PremiseKey, Facts>> = sequence {
        m.cancellation.checkpoint()
        val index = lm.getInstIndex(node)
        val keys = if (premise != null) listOf(premise) else LinkedHashSet<PremiseKey>().also { keys ->
            for (key in reach.keys) { m.cancellation.checkpoint(); keys += key }
            for (key in groups.keys) { m.cancellation.checkpoint(); keys += key }
        }
        for (key in keys) {
            m.cancellation.checkpoint()
            val bits = reach[key]
            for (layer in Layer.entries) if (bits != null && bits[layer.ordinal][index]) {
                m.cancellation.checkpoint()
                yield(key to Reach.of(layer))
            }
            val group = groups[key]?.get(index) ?: continue
            for (value in group.all()) {
                m.cancellation.checkpoint()
                yield(key to value)
            }
        }
    }

    /** Full leaf matching returns the whole stored value, including its off-query leaves. */
    fun edgesAt(node: CommonInst, pattern: Pattern): Sequence<Pair<PremiseKey, Facts>> = sequence {
        m.cancellation.checkpoint()
        val path = pattern.fact.path.toIntArray()
        val ops = ApOps(m)
        for ((premise, facts) in edgesAt(node)) {
            m.cancellation.checkpoint()
            if (facts.base == pattern.fact.base && ops.leavesNear(facts, path).any {
                m.cancellation.checkpoint()
                overlap(it, pattern)
            }) yield(premise to facts)
        }
    }
}
