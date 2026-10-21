package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.ConjunctionStore
import org.opentaint.dataflow.bidi.store.RulePredicateStore
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
            val store = ConjunctionStore(m, premises = Run1Premises(m))
            val out = order.flatMap { (slot, value) -> store.add(rule, node, 2, slot, value) }
            assertEquals(setOf(a.premise, b.premise), out.map { it.premise }.toSet())
            assertEquals(setOf(listOf(a, c), listOf(b, c)), out.map { it.inputs }.toSet())
            assertTrue(out.all { it.layer == Layer.DEMAND && it.premise is InitialAp })
            assertTrue(store.add(rule, node, 2, 1, c).isEmpty())
        }
    }

    @Test fun `explicit run1 join waits for missing repeated slots and drops only zero origin`() {
        val store = ConjunctionStore(m, premises = Run1Premises(m))
        val a = input(1)
        val zero = ConjunctionStore.Input(m.zero, Layer.DEMAND)
        assertTrue(store.add(rule, node, 3, 0, a).isEmpty())
        assertTrue(store.add(rule, node, 3, 1, a).isEmpty())
        val out = store.add(rule, node, 3, 2, zero).single()
        assertSame(a.premise, out.premise)
        assertEquals(listOf(a, a, zero), out.inputs)
        assertEquals(Layer.DEMAND, out.layer)
    }

    @Test fun `predicate run1 collapse preserves exact admission proofs literals and checked facts`() {
        val builder = RulePredicate.Builder(fixture.cancellation)
        val patterns = List(3) { fixture.pattern(base = AccessPathBase.Argument(it)) }
        val leaves = patterns.map(builder::leaf)
        val predicate = builder.build(builder.and(listOf(builder.or(listOf(leaves[0], leaves[1])), leaves[2])))
        val events = listOf(0 to input(0), 1 to input(1), 2 to input(2, Layer.DEMAND))
        for (order in listOf(events, events.reversed())) {
            val exact = RulePredicateStore(m)
            val selected = RulePredicateStore(m, premises = Run1Premises(m))
            for ((leaf, value) in order) {
                val ordinal = predicate.leaves.indexOf(patterns[leaf])
                val before = ArrayList<RulePredicateStore.Combination>()
                val after = ArrayList<RulePredicateStore.Combination>()
                exact.add(rule, node, predicate, ordinal, value, before::add)
                selected.add(rule, node, predicate, ordinal, value, after::add)
                assertEquals(before.size, after.size)
                for ((old, new) in before.zip(after)) {
                    assertEquals(old.proof, new.proof)
                    assertEquals(old.leaves, new.leaves)
                    assertEquals(old.inputs, new.inputs)
                    assertEquals(old.layer, new.layer)
                    val expected = if (origin(0) in old.premise.members) origin(0) else origin(1)
                    assertSame(expected, new.premise)
                }
            }
        }
    }

    @Test fun `predicate same origin still keeps multi leaf classification and growing evidence`() {
        val builder = RulePredicate.Builder(fixture.cancellation)
        val patterns = List(2) { fixture.pattern(base = AccessPathBase.Argument(it)) }
        val predicate = builder.build(builder.and(patterns.map(builder::leaf)))
        val store = RulePredicateStore(m, premises = Run1Premises(m))
        val a = input(0)
        val b = a.copy(facts = fixture.fact(base = AccessPathBase.Argument(1)), layer = Layer.DEMAND)
        val out = ArrayList<RulePredicateStore.Combination>()
        store.add(rule, node, predicate, predicate.leaves.indexOf(patterns[0]), a, out::add)
        assertTrue(out.isEmpty())
        store.add(rule, node, predicate, predicate.leaves.indexOf(patterns[1]), b, out::add)
        val initial = out.single()
        assertSame(a.premise, initial.premise)
        assertEquals(2, initial.leaves.size)
        assertEquals(listOf(a, b), initial.inputs)
        assertEquals(Layer.DEMAND, initial.layer)
        out.clear()
        val growth = a.copy(facts = fixture.fact(base = AccessPathBase.Argument(0), path = listOf(fixture.field("child"))))
        store.add(rule, node, predicate, predicate.leaves.indexOf(patterns[0]), growth, out::add)
        val updated = out.single()
        assertEquals(initial.proof, updated.proof)
        assertSame(a.premise, updated.premise)
        assertEquals(2, fixture.ops.leaves(checkNotNull(updated.inputs[0].facts)).count())
    }

    @Test fun `run1 stores do not intern a union for completed roots`() {
        val predicateBuilder = RulePredicate.Builder(fixture.cancellation)
        val patterns = List(2) { fixture.pattern(base = AccessPathBase.Argument(it)) }
        val predicate = predicateBuilder.build(predicateBuilder.and(patterns.map(predicateBuilder::leaf)))
        val selected = RulePredicateStore(m, premises = Run1Premises(m))
        val out = ArrayList<RulePredicateStore.Combination>()
        patterns.forEachIndexed { i, p -> selected.add(rule, node, predicate, predicate.leaves.indexOf(p), input(i), out::add) }
        assertIs<InitialAp>(out.single().premise)
        val explicit = ConjunctionStore(m, premises = Run1Premises(m))
        explicit.add(rule, node, 2, 0, input(0))
        assertIs<InitialAp>(explicit.add(rule, node, 2, 1, input(1)).single().premise)
        val sets = m.javaClass.getDeclaredField("premiseSets").apply { isAccessible = true }.get(m) as Map<*, *>
        assertTrue(sets.isEmpty(), "No complete root may allocate an ND interner entry")
    }
}
