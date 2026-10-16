package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.ir.api.common.cfg.CommonInst

/** Bounded reference joins: rescan the positive DAG, without production indexes or delta propagation. */
internal class RawPredicateSupports(private val cancellation: Cancellation) {
    private data class Scope(val method: MethodKey, val node: CommonInst, val rule: RuleOccurrenceId)
    private data class Header(val premise: Set<Pattern>, val layer: Layer, val group: GroupKey)
    private data class Proof(val bindings: Map<Int, Header>, val choices: Set<Pair<Int, Int>>)
    private class State {
        val leaves = HashMap<Int, LinkedHashMap<Header, Input>>()
        val emitted = HashMap<Proof, List<Input>>()
    }
    data class Input(val premise: Set<Pattern>, val layer: Layer, val group: GroupKey, val facts: Set<Conclusion>)
    data class Combination(val proof: ProofKey, val inputs: List<Input>) {
        val layer: Layer get() = if (inputs.any { it.layer == Layer.DEMAND }) Layer.DEMAND else Layer.NORMAL
    }
    private val states = HashMap<Scope, State>()

    fun add(method: MethodKey, node: CommonInst, rule: RuleOccurrenceId, predicate: RulePredicate,
            leaf: Int, premise: Set<Pattern>, conclusion: Conclusion): List<Combination> {
        cancellation.checkpoint()
        val layer = if (conclusion.demand) Layer.DEMAND else Layer.NORMAL
        val group = rawGroup(conclusion)
        val header = Header(premise.toSet(), layer, group)
        val state = states.getOrPut(Scope(method, node, rule)) { State() }
        val standing = state.leaves.getOrPut(leaf) { LinkedHashMap() }
        val old = standing[header]
        if (old != null && conclusion in old.facts) return emptyList()
        standing[header] = Input(header.premise, layer, group, old?.facts.orEmpty() + conclusion)

        val values = arrayOfNulls<List<Proof>>(predicate.nodeCount)
        for (n in 0 until predicate.nodeCount) {
            cancellation.checkpoint()
            values[n] = when (predicate.kind(n)) {
                RulePredicate.Kind.FALSE -> emptyList()
                RulePredicate.Kind.TRUE -> listOf(Proof(emptyMap(), emptySet()))
                RulePredicate.Kind.LEAF -> {
                    val ordinal = predicate.leafOrdinal(n)
                    state.leaves[ordinal].orEmpty().keys.map { Proof(mapOf(ordinal to it), emptySet()) }
                }
                RulePredicate.Kind.OR -> buildList {
                    predicate.childrenOf(n) { child ->
                        for (proof in checkNotNull(values[child])) {
                            cancellation.checkpoint()
                            add(proof.copy(choices = proof.choices + (n to child)))
                        }
                    }
                }
                RulePredicate.Kind.AND -> {
                    var products = listOf(Proof(emptyMap(), emptySet()))
                    predicate.childrenOf(n) { child ->
                        val next = ArrayList<Proof>()
                        for (a in products) for (b in checkNotNull(values[child])) {
                            cancellation.checkpoint()
                            // One canonical requirement must bind the same complete input each time.
                            if (b.bindings.any { (key, value) -> key in a.bindings && a.bindings[key] != value }) continue
                            next += Proof(a.bindings + b.bindings, a.choices + b.choices)
                        }
                        products = next
                    }
                    products
                }
            }
        }
        return buildList {
            for (proof in checkNotNull(values[predicate.root]).distinct()) {
                cancellation.checkpoint()
                val ordinals = proof.bindings.keys.sorted()
                val inputs = ordinals.map { ordinal -> state.leaves.getValue(ordinal).getValue(proof.bindings.getValue(ordinal)) }
                if (state.emitted[proof] == inputs) continue
                state.emitted[proof] = inputs
                val choices = proof.choices.sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
                    .flatMap { listOf(it.first, it.second) }
                add(Combination(ProofKey(rule, ordinals, choices), inputs))
            }
        }
    }
}

private fun rawGroup(c: Conclusion): GroupKey {
    val layer = if (c.demand) Layer.DEMAND else Layer.NORMAL
    val mark = c.fact.mark
    return when {
        c.fact.base == AccessPathBase.Zero -> GroupKey(FactsKind.REACH, layer, c.fact.base)
        mark is MarkSlot.Star -> GroupKey(FactsKind.FLOW, layer, c.fact.base, c.exclusion, mark.excluded, c.fact.tail)
        else -> GroupKey(FactsKind.TAINT, layer, c.fact.base, c.exclusion, tail =
            if (c.fact.tail == Tail.EXACT) Tail.ANY_TAINT else c.fact.tail)
    }
}

/** Decode physical full-condition handles only at the requested full method/site, without seed indexes. */
internal fun rawSinkSeedRequirements(program: FiniteProgramFixtures, inputs: ProgramRunInputs,
                                     method: MethodKey, node: CommonInst): List<Pattern> = buildList {
    for (seed in inputs.seeds) {
        program.cancellation.checkpoint()
        if (seed.method != method || seed.statement != node) continue
        when (seed) {
            is org.opentaint.dataflow.bidi.driver.Seed.Source -> Unit
            is org.opentaint.dataflow.bidi.driver.Seed.Sink -> add(seed.requirement)
            is org.opentaint.dataflow.bidi.driver.Seed.PredicateSink -> {
                val forms = program.forms(method)
                val sinks = program.callExpression(method, node.location.index)?.let {
                    forms.call(Direction.FORWARD, node, it).sinks
                } ?: if (node == method.statement) forms.entryRules(Direction.FORWARD).sinks
                else forms.exitRules(Direction.FORWARD, node).rules.sinks
                val sink = sinks.singleOrNull { it.occurrenceId == seed.occurrence }
                    ?: error("Unresolved raw predicate seed at $method/$node: ${seed.occurrence}")
                val predicate = checkNotNull(sink.predicate)
                for (literal in predicate.leaves) {
                    program.cancellation.checkpoint()
                    add(literal)
                }
            }
        }
    }
}
