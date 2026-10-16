package org.opentaint.dataflow.bidi.interp

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.ir.api.common.cfg.CommonInst

/** A small per-path reference join, independent of the production incremental predicate store. */
internal class ReferenceSourcePredicates(private val manager: ApManager) {
    private data class Contribution(val premise: Set<Pattern>, val demand: Boolean)
    private data class Proof(val bindings: Map<Int, Contribution>, val order: List<Int>, val choices: List<Int>)
    private class State {
        val leaves = HashMap<Int, LinkedHashSet<Contribution>>()
        val emitted = HashSet<Proof>()
    }
    private val states = HashMap<Pair<RuleOccurrenceId, CommonInst>, State>()

    fun apply(source: SourceRuleOccurrence, premise: Set<Pattern>, c: Conclusion, node: CommonInst,
              mode: ApMode, statementEdge: Boolean,
              edge: (MicroEdge, Conclusion, Set<Pattern>, Boolean, ReferenceSink) -> Unit,
              request: (Set<Pattern>, ReferenceRequestKind) -> Unit,
              out: ReferenceSink) {
        manager.cancellation.checkpoint()
        if (source.actions.isEmpty()) return
        val predicate = source.predicate
        if (source.backward) {
            // FormsReference historically has a forward default algebra. The descriptor selects
            // reversal; backward source reads are always restricted and never raise requests.
            for (action in source.actions) for ((ordinal, literal) in predicate.leaves.withIndex()) {
                manager.cancellation.checkpoint()
                fun transfer(multi: Boolean) {
                    val me = source.transfer(literal, action, multi)
                    val result = concat(c, me.edge, edgeDemand = me.may, restricted = true)
                    if (result is EdgeOutcome.Fact) out(premise, result.conclusion)
                }
                if (predicate.isUnary(ordinal)) transfer(false)
                if (predicate.isMulti(ordinal)) transfer(true)
            }
            return
        }

        var state: State? = null
        var changed = false
        for ((ordinal, literal) in predicate.leaves.withIndex()) {
            manager.cancellation.checkpoint()
            if (predicate.isUnary(ordinal)) for (action in source.actions) {
                edge(source.transfer(literal, action), c, premise, statementEdge, out)
            }
            if (!predicate.isMulti(ordinal)) continue
            val initial = if (c.fact.mark is MarkSlot.Star) {
                check(premise.size == 1) { "A FLOW reference input has one premise" }
                premise.single()
            } else premise.first()
            when (val checked = markCheck(initial, c, literal, restricted = mode.restricted)) {
                CheckResult.None -> Unit
                is CheckResult.Request -> if (mode.run1)
                    request(premise, ReferenceRequestKind.Mark(checked.mark))
                CheckResult.Holds -> {
                    val owner = state ?: states.getOrPut(source.id to node) { State() }.also { state = it }
                    val owned = ownedSet(premise.map { p -> p.copy(fact = p.fact.copy(path = ownedList(p.fact.path))) })
                    changed = owner.leaves.getOrPut(ordinal) { LinkedHashSet() }
                        .add(Contribution(owned, conjDemand(c, literal))) || changed
                }
            }
        }
        if (!changed) return
        val owner = checkNotNull(state)
        for (proof in proofs(predicate, owner)) {
            manager.cancellation.checkpoint()
            if (proof.bindings.size < 2 || !owner.emitted.add(proof)) continue
            val allPremises = proof.order.flatMap { proof.bindings.getValue(it).premise }
            val nonzero = allPremises.filter { it.fact.base != AccessPathBase.Zero }
            val pr = if (nonzero.isEmpty()) setOf(ZERO_PATTERN) else ownedSet(nonzero)
            val demand = proof.bindings.values.any { it.demand }
            for (action in source.actions) {
                manager.cancellation.checkpoint()
                val target = action.target
                out(pr, Conclusion(target, ExclusionSet.Empty, demand))
            }
        }
    }

    /** Re-evaluate bounded standing inputs bottom-up; no production indexes or delta joins are used. */
    private fun proofs(predicate: RulePredicate, state: State): List<Proof> {
        val values = arrayOfNulls<List<Proof>>(predicate.nodeCount)
        for (node in 0 until predicate.nodeCount) {
            manager.cancellation.checkpoint()
            values[node] = when (predicate.kind(node)) {
                RulePredicate.Kind.FALSE -> emptyList()
                RulePredicate.Kind.TRUE -> listOf(Proof(emptyMap(), emptyList(), emptyList()))
                RulePredicate.Kind.LEAF -> {
                    val leaf = predicate.leafOrdinal(node)
                    state.leaves[leaf].orEmpty().map { Proof(mapOf(leaf to it), listOf(leaf), emptyList()) }
                }
                RulePredicate.Kind.OR -> {
                    val result = ArrayList<Proof>()
                    predicate.childrenOf(node) { child ->
                        manager.cancellation.checkpoint()
                        for (proof in checkNotNull(values[child])) {
                            manager.cancellation.checkpoint()
                            result += proof.copy(choices = listOf(node, child) + proof.choices)
                        }
                    }
                    result
                }
                RulePredicate.Kind.AND -> {
                    var products = listOf(Proof(emptyMap(), emptyList(), emptyList()))
                    predicate.childrenOf(node) { child ->
                        val next = ArrayList<Proof>()
                        for (a in products) for (b in checkNotNull(values[child])) {
                            manager.cancellation.checkpoint()
                            merge(a, b)?.let(next::add)
                        }
                        products = next
                    }
                    products
                }
            }
        }
        return checkNotNull(values[predicate.root])
    }

    private fun merge(a: Proof, b: Proof): Proof? {
        for ((leaf, input) in b.bindings) if (leaf in a.bindings && a.bindings[leaf] != input) return null
        val bindings = LinkedHashMap(a.bindings)
        bindings.putAll(b.bindings)
        return Proof(bindings, a.order + b.order.filterNot { it in a.bindings }, a.choices + b.choices)
    }
}
