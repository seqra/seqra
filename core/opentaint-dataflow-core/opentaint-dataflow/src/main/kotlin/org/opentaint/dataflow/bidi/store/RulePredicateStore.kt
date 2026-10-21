package org.opentaint.dataflow.bidi.store

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.ProofKey
import org.opentaint.dataflow.bidi.interp.FormCacheStatistics
import org.opentaint.dataflow.bidi.interp.RuleOccurrenceId
import org.opentaint.dataflow.bidi.interp.RulePredicate
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.Collections
import java.util.IdentityHashMap

/** Standing supported branches for one actor. Records contain no compiled predicate topology. */
class RulePredicateStore(private val manager: ApManager, private val statistics: FormCacheStatistics? = null,
                         private val premises: PremiseCombination = ExactPremises(manager)) {
    private data class Scope(val rule: RuleOccurrenceId, val statement: CommonInst)
    private data class Header(val leaf: Int, val premise: PremiseKey, val layer: Layer, val group: GroupKey?)
    private data class Choice(val node: Int, val branch: Int)
    private class VersionedInput(val input: ConjunctionStore.Input, val version: Long = 0)

    /** Hashes use child hashes. Equality walks a DAG without recursion or repeated shared subtrees. */
    private sealed class Proof(val node: Int, private val hash: Int) {
        final override fun hashCode(): Int = hash
        final override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Proof || hash != other.hash) return false
            val work = ArrayDeque<Pair<Proof, Proof>>()
            val seen = IdentityHashMap<Proof, MutableSet<Proof>>()
            work.add(this to other)
            while (work.isNotEmpty()) {
                val (a, b) = work.removeLast()
                if (a === b) continue
                if (a.hash != b.hash || a.node != b.node || a.javaClass != b.javaClass) return false
                val peers = seen.getOrPut(a) { Collections.newSetFromMap(IdentityHashMap()) }
                if (!peers.add(b)) continue
                when (a) {
                    is LeafProof -> if (a.leaf != (b as LeafProof).leaf) return false
                    is OrProof -> {
                        b as OrProof
                        if (a.branch != b.branch) return false
                        if (a.child !== b.child) work.add(a.child to b.child)
                    }
                    is AndProof -> {
                        b as AndProof
                        if (a.children.size != b.children.size) return false
                        for (i in a.children.indices)
                            if (a.children[i] !== b.children[i]) work.add(a.children[i] to b.children[i])
                    }
                }
            }
            return true
        }
    }
    private class LeafProof(node: Int, val leaf: Int) : Proof(node, 31 * (13 + node) + leaf)
    private class OrProof(node: Int, val branch: Int, val child: Proof) :
        Proof(node, 31 * (31 * (31 + node) + branch) + child.hashCode())
    private class AndProof(node: Int, val children: Array<Proof>) :
        Proof(node, children.fold(47 + node) { hash, child -> 31 * hash + child.hashCode() })

    private class Shape(val proof: Proof, val headers: PersistentMap<Int, Header>, val headersHash: Int) {
        private val hash = 31 * proof.hashCode() + headersHash
        override fun hashCode(): Int = hash
        override fun equals(other: Any?): Boolean = this === other || other is Shape && hash == other.hash &&
            headers == other.headers && proof == other.proof
    }
    private class Support(val shape: Shape, val inputs: PersistentMap<Int, VersionedInput>)
    private class Columns(val headers: PersistentMap<Int, Header>, val headersHash: Int,
                          val inputs: PersistentMap<Int, VersionedInput>)
    private class ChildSupports(val node: Int, val values: List<Support>)
    private class RootKey(val proof: ProofKey, val leaves: List<Int>, val headers: PersistentMap<Int, Header>,
                          headersHash: Int) {
        private val hash = 31 * proof.hashCode() + headersHash
        override fun hashCode(): Int = hash
        override fun equals(other: Any?): Boolean = this === other || other is RootKey && hash == other.hash &&
            proof == other.proof && headers == other.headers
    }
    private class State {
        val nodes = HashMap<Int, LinkedHashMap<Shape, Support>>()
        val proofs = HashMap<Proof, Proof>()
        val rootKeys = HashMap<Shape, RootKey>()
        val emitted = HashMap<RootKey, PersistentMap<Int, VersionedInput>>()
        fun intern(proof: Proof, statistics: FormCacheStatistics?): Proof = proofs.getOrPut(proof) {
            statistics?.observedProof()
            proof
        }
    }
    private val states = HashMap<Scope, State>()

    class Combination(
        val proof: ProofKey,
        val premise: PremiseKey,
        val layer: Layer,
        val leaves: List<Int>,
        val inputs: List<ConjunctionStore.Input>,
    )

    /** Facts enter one canonical leaf. AND products are streamed when actual supports arrive. */
    fun add(rule: RuleOccurrenceId, statement: CommonInst, predicate: RulePredicate, leaf: Int,
            input: ConjunctionStore.Input, emit: (Combination) -> Unit) {
        manager.cancellation.checkpoint()
        require(leaf in predicate.leaves.indices)
        val state = states.getOrPut(Scope(rule, statement)) { State() }
        val queue = ArrayDeque<Pair<Int, Support>>()

        fun publish(node: Int, support: Support) {
            manager.cancellation.checkpoint()
            val standing = state.nodes.getOrPut(node) { LinkedHashMap() }
            val previous = standing[support.shape]
            val fresh = if (previous == null) support else merge(previous, support) ?: return
            standing[support.shape] = fresh
            if (previous == null) statistics?.observedSupport() else statistics?.supportGrowth()
            queue.addLast(node to fresh)
        }

        val node = predicate.leafNode(leaf)
        val header = Header(leaf, input.premise, input.layer, input.facts?.groupKey)
        val shape = Shape(state.intern(LeafProof(node, leaf), statistics), persistentHashMapOf(leaf to header),
            leaf.hashCode() xor header.hashCode())
        publish(node, Support(shape, persistentHashMapOf(leaf to VersionedInput(input))))
        while (queue.isNotEmpty()) {
            manager.cancellation.checkpoint()
            val (current, support) = queue.removeFirst()
            // A later publication contains the merged facts of an older queued version.
            if (state.nodes[current]?.get(support.shape) !== support) continue
            if (current == predicate.root) {
                combineFresh(rule, state, support)?.let(emit)
                continue
            }
            predicate.parentsOf(current) { parent ->
                manager.cancellation.checkpoint()
                when (predicate.kind(parent)) {
                    RulePredicate.Kind.OR -> {
                        val proof = state.intern(OrProof(parent, current, support.shape.proof), statistics)
                        publish(parent, Support(Shape(proof, support.shape.headers, support.shape.headersHash), support.inputs))
                    }
                    RulePredicate.Kind.AND -> {
                        val others = ArrayList<ChildSupports>()
                        val order = ArrayList<Int>()
                        var missing = false
                        predicate.childrenOf(parent) { child ->
                            manager.cancellation.checkpoint()
                            order += child
                            if (child != current) {
                                val standing = state.nodes[child]?.values
                                if (standing.isNullOrEmpty()) missing = true
                                else {
                                    val snapshot = ArrayList<Support>(standing.size)
                                    for (value in standing) {
                                        manager.cancellation.checkpoint()
                                        snapshot += value
                                    }
                                    others += ChildSupports(child, snapshot)
                                }
                            }
                        }
                        if (!missing) join(state, parent, current, support, others, order) { publish(parent, it) }
                    }
                    else -> error("A leaf or constant cannot be a predicate parent")
                }
            }
        }
    }

    /** Partial products share bindings and child records instead of copying whole support lists. */
    private fun join(state: State, parent: Int, child: Int, delta: Support, others: List<ChildSupports>,
                     order: List<Int>, emit: (Support) -> Unit) {
        val positions = IntArray(others.size)
        val partial = arrayOfNulls<Columns>(others.size + 1)
        val proofs = arrayOfNulls<PersistentMap<Int, Proof>>(others.size + 1)
        partial[0] = columns(delta)
        proofs[0] = persistentHashMapOf(child to delta.shape.proof)
        var depth = 0
        while (depth >= 0) {
            manager.cancellation.checkpoint()
            if (depth == others.size) {
                val selected = checkNotNull(proofs[depth])
                val children = Array(order.size) {
                    manager.cancellation.checkpoint()
                    checkNotNull(selected[order[it]])
                }
                val proof = state.intern(AndProof(parent, children), statistics)
                val value = checkNotNull(partial[depth])
                emit(Support(Shape(proof, value.headers, value.headersHash), value.inputs))
                depth--
                continue
            }
            val standing = others[depth]
            if (positions[depth] == standing.values.size) {
                positions[depth] = 0
                partial[depth + 1] = null
                proofs[depth + 1] = null
                depth--
                continue
            }
            val support = standing.values[positions[depth]++]
            val next = compatible(checkNotNull(partial[depth]), columns(support)) ?: continue
            partial[depth + 1] = next
            proofs[depth + 1] = checkNotNull(proofs[depth]).put(standing.node, support.shape.proof)
            depth++
        }
    }

    private fun columns(support: Support) = Columns(support.shape.headers, support.shape.headersHash, support.inputs)

    /** The binding for a repeated canonical leaf must agree, including its complete fact group. */
    private fun compatible(a: Columns, b: Columns): Columns? {
        val base = if (a.headers.size >= b.headers.size) a else b
        val other = if (base === a) b else a
        var headers = base.headers
        var hash = base.headersHash
        var inputs = base.inputs
        for ((leaf, header) in other.headers) {
            manager.cancellation.checkpoint()
            val previous = headers[leaf]
            if (previous == null) {
                headers = headers.put(leaf, header)
                hash += leaf.hashCode() xor header.hashCode()
                inputs = inputs.put(leaf, checkNotNull(other.inputs[leaf]))
            } else {
                if (previous != header) return null
                val current = checkNotNull(inputs[leaf])
                val merged = unionInput(current, checkNotNull(other.inputs[leaf]))
                if (merged !== current) inputs = inputs.put(leaf, merged)
            }
        }
        return if (headers === base.headers && inputs === base.inputs) base else Columns(headers, hash, inputs)
    }

    private fun merge(a: Support, b: Support): Support? =
        mergeInputs(a.inputs, b.inputs)?.let { Support(a.shape, it) }

    private fun mergeInputs(a: PersistentMap<Int, VersionedInput>, b: PersistentMap<Int, VersionedInput>):
        PersistentMap<Int, VersionedInput>? {
        var merged = a
        for ((leaf, input) in b) {
            manager.cancellation.checkpoint()
            val previous = checkNotNull(merged[leaf])
            val fresh = unionInput(previous, input)
            if (fresh !== previous) merged = merged.put(leaf, fresh)
        }
        return if (merged === a) null else merged
    }

    private fun unionInput(a: VersionedInput, b: VersionedInput): VersionedInput {
        if (a === b) return a
        val ai = a.input
        val bi = b.input
        check(ai.premise == bi.premise && ai.layer == bi.layer && ai.facts?.groupKey == bi.facts?.groupKey)
        val af = ai.facts ?: return a
        val (merged, delta) = manager.mergeAddDelta(af, checkNotNull(bi.facts))
        return if (delta == null) a else VersionedInput(ai.copy(facts = merged), maxOf(a.version, b.version) + 1)
    }

    /** Only complete root supports flatten their canonical leaf and OR-choice descriptors. */
    private fun rootKey(rule: RuleOccurrenceId, state: State, shape: Shape): RootKey =
        state.rootKeys.getOrPut(shape) {
            val leaves = Collections.unmodifiableList(shape.headers.keys.sorted())
            val choices = HashSet<Choice>()
            val seen = Collections.newSetFromMap(IdentityHashMap<Proof, Boolean>())
            val work = ArrayDeque<Proof>()
            work.add(shape.proof)
            while (work.isNotEmpty()) {
                manager.cancellation.checkpoint()
                val proof = work.removeLast()
                if (!seen.add(proof)) continue
                when (proof) {
                    is LeafProof -> Unit
                    is OrProof -> { choices += Choice(proof.node, proof.branch); work.add(proof.child) }
                    is AndProof -> for (child in proof.children) {
                        manager.cancellation.checkpoint()
                        work.add(child)
                    }
                }
            }
            val flat = ArrayList<Int>(2 * choices.size)
            for (choice in choices.sortedWith(choiceOrder)) {
                manager.cancellation.checkpoint()
                flat += choice.node
                flat += choice.branch
            }
            RootKey(ProofKey(rule, leaves, flat), leaves, shape.headers, shape.headersHash)
        }

    private fun combineFresh(rule: RuleOccurrenceId, state: State, support: Support): Combination? {
        val key = rootKey(rule, state, support.shape)
        val previous = state.emitted[key]
        val inputs = if (previous == null) support.inputs else mergeInputs(previous, support.inputs) ?: return null
        state.emitted[key] = inputs
        var layer = Layer.NORMAL
        val ordered = ArrayList<ConjunctionStore.Input>(key.leaves.size)
        for (leaf in key.leaves) {
            manager.cancellation.checkpoint()
            val input = checkNotNull(inputs[leaf]).input
            ordered += input
            if (input.layer == Layer.DEMAND) layer = Layer.DEMAND
        }
        val premise = premises.combine(ordered.asSequence().map { it.premise }.asIterable())
        val combination = Combination(key.proof, premise, layer, key.leaves, Collections.unmodifiableList(ordered))
        statistics?.rootOutcome(growth = previous != null)
        return combination
    }

    private companion object {
        val choiceOrder = compareBy<Choice> { it.node }.thenBy { it.branch }
    }
}
