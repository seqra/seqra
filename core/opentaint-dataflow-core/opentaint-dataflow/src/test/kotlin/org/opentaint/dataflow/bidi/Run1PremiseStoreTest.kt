package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.ConjunctionStore
import org.opentaint.dataflow.bidi.store.RulePredicateStore
import org.opentaint.dataflow.bidi.store.Run1OriginPredicateStore
import kotlin.test.*

class Run1PremiseStoreTest {
    private val fixture = StoreFixtures()
    private val m = fixture.manager
    private val node = fixture.inst(1)
    private val rule = RuleOccurrenceId(FormKey(FormOwner(), fixture.commonMethod, fixture.method, node), RuleKind.CALL_SOURCE, 0)
    private fun origin(i: Int) = m.initial(fixture.pattern(base = AccessPathBase.Argument(i)))
    private fun input(i: Int, layer: Layer = Layer.NORMAL) = ConjunctionStore.Input(origin(i), layer,
        fixture.fact(base = AccessPathBase.Argument(i), layer = layer))

    @Test fun `explicit run1 join keeps every complete binding and all facts and layers`() {
        val a = input(0)
        val b = input(1)
        val c = input(2, Layer.DEMAND)
        val slots = listOf(0 to a, 0 to b, 1 to c)
        for (order in listOf(slots, slots.reversed(), listOf(slots[1], slots[2], slots[0]))) {
            val store = ConjunctionStore(m)
            val out = order.flatMap { (slot, value) -> store.addBinding(rule, node, 2, slot, value) }
            assertEquals(setOf(listOf(a, c), listOf(b, c)), out.map { it.inputs }.toSet())
            assertTrue(out.all { it.layer == Layer.DEMAND })
            assertTrue(store.addBinding(rule, node, 2, 1, c).isEmpty())
        }
    }

    @Test fun `explicit complete binding waits for missing repeated slots and retains zero input`() {
        val store = ConjunctionStore(m)
        val a = input(1)
        val zero = ConjunctionStore.Input(m.zero, Layer.DEMAND)
        assertTrue(store.addBinding(rule, node, 3, 0, a).isEmpty())
        assertTrue(store.addBinding(rule, node, 3, 1, a).isEmpty())
        val out = store.addBinding(rule, node, 3, 2, zero).single()
        assertEquals(listOf(a, a, zero), out.inputs)
        assertEquals(Layer.DEMAND, out.layer)
    }

    @Test fun `predicate complete binding preserves exact admission proofs literals and checked facts`() {
        val builder = RulePredicate.Builder(fixture.cancellation)
        val patterns = List(3) { fixture.pattern(base = AccessPathBase.Argument(it)) }
        val leaves = patterns.map(builder::leaf)
        val predicate = builder.build(builder.and(listOf(builder.or(listOf(leaves[0], leaves[1])), leaves[2])))
        val events = listOf(0 to input(0), 1 to input(1), 2 to input(2, Layer.DEMAND))
        for (order in listOf(events, events.reversed())) {
            val exact = RulePredicateStore(m)
            val selected = RulePredicateStore(m)
            for ((leaf, value) in order) {
                val ordinal = predicate.leaves.indexOf(patterns[leaf])
                val before = ArrayList<RulePredicateStore.Combination>()
                val after = ArrayList<RulePredicateStore.CompletePredicateBinding>()
                exact.add(rule, node, predicate, ordinal, value, before::add)
                selected.addBinding(rule, node, predicate, ordinal, value, after::add)
                assertEquals(before.size, after.size)
                for ((old, new) in before.zip(after)) {
                    assertEquals(old.proof, new.proof)
                    assertEquals(old.leaves, new.leaves)
                    assertEquals(old.inputs, new.inputs)
                    assertEquals(old.layer, new.layer)
                }
            }
        }
    }

    @Test fun `predicate same origin keeps multi leaf classification and complete growing witness evidence`() {
        val builder = RulePredicate.Builder(fixture.cancellation)
        val patterns = List(2) { fixture.pattern(base = AccessPathBase.Argument(it)) }
        val predicate = builder.build(builder.and(patterns.map(builder::leaf)))
        val store = Run1OriginPredicateStore(m)
        val witnesses = RulePredicateStore(m)
        val a = input(0)
        val b = a.copy(facts = fixture.fact(base = AccessPathBase.Argument(1)), layer = Layer.DEMAND)
        val out = ArrayList<RulePredicateStore.CompletePredicateBinding>()
        val arms = ArrayList<InitialAp>()
        fun accept(leaf: Int, value: ConjunctionStore.Input) {
            val ordinal = predicate.leaves.indexOf(patterns[leaf])
            witnesses.addBinding(rule, node, predicate, ordinal, value, out::add)
            store.add(rule, node, predicate, ordinal, value) { cube ->
                assertEquals(2, cube.leaves.size)
                assertEquals(Layer.DEMAND, cube.layer)
                cube.emitOrigins("result", arms::add)
            }
        }
        accept(0, a)
        assertTrue(out.isEmpty())
        accept(1, b)
        val initial = out.single()
        assertEquals(listOf(a.premise), arms)
        assertEquals(2, initial.leaves.size)
        assertEquals(listOf(a, b), initial.inputs)
        assertEquals(Layer.DEMAND, initial.layer)
        out.clear()
        arms.clear()
        val growth = a.copy(facts = fixture.fact(base = AccessPathBase.Argument(0), path = listOf(fixture.field("child"))))
        accept(0, growth)
        val updated = out.single()
        assertEquals(initial.proof, updated.proof)
        assertEquals(2, fixture.ops.leaves(checkNotNull(updated.inputs[0].facts)).count())
        assertTrue(arms.isEmpty(), "A growing witness cannot reemit a fixed result arm")
    }

    @Test fun `run1 predicate result and complete witnesses do not intern a union`() {
        val builder = RulePredicate.Builder(fixture.cancellation)
        val patterns = List(2) { fixture.pattern(base = AccessPathBase.Argument(it)) }
        val predicate = builder.build(builder.and(patterns.map(builder::leaf)))
        val selected = Run1OriginPredicateStore(m)
        val witnesses = RulePredicateStore(m)
        val arms = ArrayList<InitialAp>()
        val bindings = ArrayList<RulePredicateStore.CompletePredicateBinding>()
        patterns.forEachIndexed { i, p ->
            val ordinal = predicate.leaves.indexOf(p)
            selected.add(rule, node, predicate, ordinal, input(i)) { it.emitOrigins("action", arms::add) }
            witnesses.addBinding(rule, node, predicate, ordinal, input(i), bindings::add)
        }
        assertEquals(setOf(origin(0), origin(1)), arms.toSet())
        assertEquals(2, bindings.single().inputs.size)
        val explicit = ConjunctionStore(m)
        explicit.addBinding(rule, node, 2, 0, input(0))
        assertEquals(2, explicit.addBinding(rule, node, 2, 1, input(1)).single().inputs.size)
        val sets = m.javaClass.getDeclaredField("premiseSets").apply { isAccessible = true }.get(m) as Map<*, *>
        assertTrue(sets.isEmpty(), "Neither an atomic result nor a complete witness needs an ND interner entry")
    }
}
