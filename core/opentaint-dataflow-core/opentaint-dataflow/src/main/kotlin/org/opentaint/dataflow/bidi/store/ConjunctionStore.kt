package org.opentaint.dataflow.bidi.store

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.N2Statistics
import org.opentaint.dataflow.bidi.engine.N2InputRef
import org.opentaint.dataflow.bidi.engine.RegionGroups
import org.opentaint.dataflow.bidi.engine.GroundingSupport
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.Collections

/** The semantic E6 member after full matching; no accepted Facts version is retained. */
internal data class NdBinding(val ref: CallerRef, val linkLayer: Layer, val initial: InitialAp? = null,
                              val inputRef: N2InputRef? = null, val staticInputs: List<RegionGroups> = emptyList())

/** Standing literal inputs for one method and run. One writer owns this store. */
class ConjunctionStore(private val m: ApManager, private val statistics: N2Statistics? = null) {
    /** Facts already accepted by the literal; layer is the effective contribution layer. */
    data class Input(val premise: PremiseKey, val layer: Layer, val facts: Facts? = null,
                     val requirement: Pattern? = null, val leafOrdinal: Int = -1,
                     val grounding: GroundingSupport? = null)
    class Combination(val premise: PremiseKey, val layer: Layer, val inputs: List<Input>)

    private val joins = HashMap<Pair<Any, CommonInst>, KaryJoin<Input>>()
    private data class InputKey(val premise: PremiseKey, val layer: Layer, val facts: Facts?,
                                val requirement: Pattern?, val leaf: Int)
    private val currentInputs = HashMap<Pair<Any, CommonInst>, MutableMap<Pair<Int, InputKey>, Input>>()

    /** Rule alternatives and statements have separate joins; repeated literals retain separate slots. */
    fun add(rule: Any, statement: CommonInst, arity: Int, literal: Int, input: Input): List<Combination> {
        m.cancellation.checkpoint()
        require(arity >= 2) { "A conjunction needs at least two literals" }
        require(literal in 0 until arity) { "A literal index must be within the conjunction's arity" }
        val key = rule to statement
        val join = joins[key]
        require(join == null || join.arity == arity) { "A rule at a statement has one fixed arity" }
        val active = join ?: KaryJoin<Input>(arity, m.cancellation).also { joins[key] = it }
        val rows = currentInputs.getOrPut(key) { HashMap() }
        val inputKey = literal to InputKey(input.premise, input.layer, input.facts, input.requirement, input.leafOrdinal)
        val previous = rows[inputKey]
        val next = if (previous?.grounding != null && input.grounding != null)
            input.copy(grounding = previous.grounding.or(input.grounding)) else input
        if (previous == next) return emptyList()
        rows[inputKey] = next
        val inputs = if (previous == null) active.add(literal, next) else active.replace(literal, previous, next)
        val out = ArrayList<Combination>(inputs.size)
        for (combination in inputs) {
            m.cancellation.checkpoint()
            out += combine(combination)
        }
        return out
    }

    private fun combine(inputs: List<Input>): Combination {
        var premise = inputs[0].premise
        var layer = Layer.NORMAL
        for (index in inputs.indices) {
            m.cancellation.checkpoint()
            val input = inputs[index]
            if (index != 0) premise = m.union(premise, input.premise)
            if (input.layer == Layer.DEMAND) layer = Layer.DEMAND
        }
        return Combination(premise, layer, inputs)
    }

    /** An E6 join belongs to one callee context, premise set, publication layer and caller call. */
    data class NdKey(val callee: MethodKey, val premise: PremiseKey, val layer: Layer,
                     val call: CommonInst, val stageOrdinal: Int = 0)
    private val ndJoins = HashMap<NdKey, NdSummaryJoin<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <S : Any> ndJoin(key: NdKey): NdSummaryJoin<S> {
        m.cancellation.checkpoint()
        return ndJoins.getOrPut(key) { NdSummaryJoin<S>(m, key.premise.size, statistics) } as NdSummaryJoin<S>
    }
}

/** E6 keeps every full member combination and growing TAINT conclusions. Matching is caller-owned. */
class NdSummaryJoin<S : Any>(private val m: ApManager, arity: Int, private val statistics: N2Statistics? = null) {
    private val members = KaryJoin<S>(arity, m.cancellation)
    private val full = ArrayList<List<S>>()
    private val interners = m.newInterners()
    private val conclusion = HashMap<AccessPathBase, TaintGroup>(2)
    private data class SupportedKey(val leaf: Pattern, val layer: Layer)
    private val supported = LinkedHashMap<SupportedKey, GroundingSupport>()
    private val supportedRevisions = HashMap<Pair<Int, S>, Long>()
    private val supportedRelationRevisions = HashMap<Pair<Int, S>, Long>()
    class SupportedResult<S>(val parts: List<S>, val facts: TaintTree, val support: GroundingSupport)

    /** Guard metadata belongs to exact accepted conclusion cofactors, never to a broader T2 union. */
    fun addSupportedConclusion(g: TaintTree, support: GroundingSupport): List<SupportedResult<S>> {
        val result = ArrayList<SupportedResult<S>>()
        for (leaf in ApOps(m).leaves(g)) {
            val key = SupportedKey(leaf, g.layer)
            val old = supported[key]
            val next = old?.or(support) ?: support
            if (old == next) continue
            supported[key] = next
            val fact = m.factsOf(leaf.fact, leaf.exclusion, g.layer) as TaintTree
            for (parts in full) result += SupportedResult(parts, fact, next)
        }
        return result
    }

    fun addSupportedSubscription(index: Int, s: S, revision: Long = 0,
                                 relationRevision: Long = 0): List<SupportedResult<S>> {
        val key = index to s
        val previousRevision = supportedRevisions[key]
        if (previousRevision == null || revision > previousRevision) supportedRevisions[key] = revision
        val previousRelationRevision = supportedRelationRevisions[key]
        if (previousRelationRevision == null || relationRevision > previousRelationRevision)
            supportedRelationRevisions[key] = relationRevision
        val before = members.memberCount
        val fresh = members.add(index, s)
        statistics?.recordE6(members.memberCount - before, 1 - (members.memberCount - before), fresh.size)
        val result = ArrayList<SupportedResult<S>>()
        for (inputs in fresh) {
            val parts: List<S> = Collections.unmodifiableList(ArrayList(inputs))
            full += parts
            for ((key, support) in supported) result += SupportedResult(parts,
                m.factsOf(key.leaf.fact, key.leaf.exclusion, key.layer) as TaintTree, support)
        }
        // Re-use the same binding identity and current cofactor rows after support-only
        // growth. Revisions are metadata, not additional standing member alternatives.
        if (fresh.isEmpty() && previousRevision != null && (revision > previousRevision ||
                relationRevision > (previousRelationRevision ?: 0))) {
            for (parts in full) if (parts[index] == s) for ((part, support) in supported)
                result += SupportedResult(parts,
                    m.factsOf(part.leaf.fact, part.leaf.exclusion, part.layer) as TaintTree, support)
        }
        return result
    }

    internal val memberCount: Int get() = members.memberCount
    internal val combinationCount: Int get() = full.size

    /** Put a subscription under every member index it satisfies; new combinations meet all stored values. */
    fun addSubscription(index: Int, s: S): List<Pair<List<S>, TaintTree>> {
        m.cancellation.checkpoint()
        val previousMembers = if (statistics != null) members.memberCount else 0
        val fresh = members.add(index, s)
        statistics?.let {
            val newBindings = members.memberCount - previousMembers
            it.recordE6(newBindings, 1 - newBindings, fresh.size)
        }
        val out = ArrayList<Pair<List<S>, TaintTree>>()
        for (inputs in fresh) {
            m.cancellation.checkpoint()
            val owned = ArrayList<S>(inputs.size)
            for (input in inputs) {
                m.cancellation.checkpoint()
                owned += input
            }
            val combination: List<S> = Collections.unmodifiableList(owned)
            full += combination
            for (group in conclusion.values) {
                m.cancellation.checkpoint()
                for (g in group.all()) {
                    m.cancellation.checkpoint()
                    out += combination to g
                }
            }
        }
        return out
    }

    /** T4 applies the new part, or a whole T2 merged value, to every standing combination. */
    fun addConclusion(g: TaintTree): List<Pair<List<S>, TaintTree>> {
        m.cancellation.checkpoint()
        val delta = conclusion.getOrPut(g.base) { TaintGroup(m, interners.taint) }.add(g) ?: return emptyList()
        val out = ArrayList<Pair<List<S>, TaintTree>>(full.size)
        for (combination in full) {
            m.cancellation.checkpoint()
            out += combination to delta
        }
        return out
    }
}
