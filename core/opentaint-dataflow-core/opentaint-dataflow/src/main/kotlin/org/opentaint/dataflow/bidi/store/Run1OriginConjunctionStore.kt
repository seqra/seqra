package org.opentaint.dataflow.bidi.store

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.ir.api.common.cfg.CommonInst

/** One method actor owns accepted slot domains. Only complete header products are enumerated. */
internal class Run1OriginConjunctionStore(private val manager: ApManager) {
    private data class Header(val layer: Layer, val group: GroupKey?)
    private class Domain {
        val inputs = LinkedHashMap<InitialAp, ConjunctionStore.Input>()
    }
    private class Emitted {
        val nonzero = HashSet<InitialAp>()
        var zero = false
    }
    private class State(val arity: Int) {
        val slots = List(arity) { LinkedHashMap<Header, Domain>() }
        val emitted = HashMap<List<Header>, Emitted>()
    }
    private val states = HashMap<Pair<Any, CommonInst>, State>()
    internal var originMembershipVisits = 0L
        private set

    fun addOrigins(rule: Any, statement: CommonInst, arity: Int, literal: Int,
                   input: ConjunctionStore.Input, emit: (InitialAp, Layer) -> Unit) {
        manager.cancellation.checkpoint()
        require(arity >= 2) { "A conjunction needs at least two literals" }
        require(literal in 0 until arity) { "A literal index must be within the conjunction's arity" }
        val initial = input.premise
        check(initial is InitialAp && initial.manager === manager) { "A run-1 input needs one owned initial" }
        check(initial.exclusion == ExclusionSet.Empty) { "Run-1 initials have empty field exclusions" }
        val state = states.getOrPut(rule to statement) { State(arity) }
        require(state.arity == arity) { "A rule at a statement has one fixed arity" }
        val header = Header(input.layer, input.facts?.groupKey)
        val domain = state.slots[literal].getOrPut(header) { Domain() }
        val previous = domain.inputs[initial]
        if (previous != null) {
            // The fixed target and layer do not change when an accepted input's annotations grow.
            if (previous.facts != null && input.facts != null) {
                val (merged, delta) = manager.mergeAddDelta(previous.facts, input.facts)
                if (delta != null) domain.inputs[initial] = input.copy(facts = merged)
            }
            return
        }
        domain.inputs[initial] = input
        if (state.slots.any { it.isEmpty() }) return

        val choices = state.slots.mapIndexed { slot, headers ->
            if (slot == literal) listOf(header) else headers.keys.toList()
        }
        val positions = IntArray(arity)
        while (true) {
            manager.cancellation.checkpoint()
            val headers = choices.mapIndexed { slot, values -> values[positions[slot]] }
            val domains = headers.mapIndexed { slot, h -> state.slots[slot].getValue(h) }
            val layer = if (headers.any { it.layer == Layer.DEMAND }) Layer.DEMAND else Layer.NORMAL
            val old = state.emitted[headers]
            val emitted = old ?: Emitted().also { state.emitted[headers] = it }
            fun offer(i: InitialAp) {
                manager.cancellation.checkpoint()
                originMembershipVisits++
                if (!i.isZero && emitted.nonzero.add(i)) emit(i, layer)
            }
            if (old == null) {
                for (d in domains) for (i in d.inputs.keys) offer(i)
            } else offer(initial)
            // The same complete header partition has a ZERO choice in EVERY slot.
            if (!emitted.zero && domains.all { manager.zero in it.inputs }) {
                emitted.zero = true
                emit(manager.zero, layer)
            }
            var slot = arity - 1
            while (slot >= 0) {
                manager.cancellation.checkpoint()
                if (++positions[slot] < choices[slot].size) break
                positions[slot] = 0
                slot--
            }
            if (slot < 0) return
        }
    }
}
