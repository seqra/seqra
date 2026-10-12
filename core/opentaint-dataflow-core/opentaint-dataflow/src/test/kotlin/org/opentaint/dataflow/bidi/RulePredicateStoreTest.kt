package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.ConjunctionStore
import org.opentaint.dataflow.bidi.store.RulePredicateStore
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.test.*

class RulePredicateStoreTest {
    private val fixture = StoreFixtures()
    private val manager = fixture.manager
    private val owner = FormOwner()
    private val statement = fixture.inst(1)
    private val rule = RuleOccurrenceId(FormKey(owner, fixture.commonMethod, fixture.method, statement), RuleKind.CALL_SINK, 0)
    private fun pattern(i: Int) = fixture.pattern(base = AccessPathBase.Argument(i))
    private fun premise(i: Int) = manager.initial(pattern(i))
    private fun input(i: Int, layer: Layer = Layer.NORMAL) = ConjunctionStore.Input(premise(i), layer)
    private fun build(root: (RulePredicate.Builder, List<Int>) -> Int): RulePredicate {
        val builder = RulePredicate.Builder(fixture.cancellation)
        val leaves = List(4) { builder.leaf(pattern(it)) }
        return builder.build(root(builder, leaves))
    }
    private fun ordinal(predicate: RulePredicate, i: Int) = predicate.leaves.indexOf(pattern(i))
    private fun RulePredicateStore.accept(predicate: RulePredicate, leaf: Int, value: ConjunctionStore.Input,
                                          id: RuleOccurrenceId = rule): List<RulePredicateStore.Combination> =
        ArrayList<RulePredicateStore.Combination>().also { add(id, statement, predicate, ordinal(predicate, leaf), value, it::add) }

    @Test fun `AND waits for all children and keeps OR alternatives in every input order`() {
        val predicate = build { b, l -> b.and(listOf(b.or(listOf(l[0], l[1])), l[2])) }
        for (order in listOf(listOf(0, 1, 2), listOf(2, 0, 1), listOf(1, 2, 0))) {
            val store = RulePredicateStore(manager)
            val outcomes = order.flatMap { store.accept(predicate, it, input(it)) }
            assertEquals(setOf(setOf(premise(0), premise(2)), setOf(premise(1), premise(2))),
                outcomes.map { it.premise.members.toSet() }.toSet())
            assertEquals(2, outcomes.size)
            for (leaf in order) assertTrue(store.accept(predicate, leaf, input(leaf)).isEmpty())
        }
    }

    @Test fun `overlapping subexpressions bind repeated leaf to the same contribution`() {
        val predicate = build { b, l -> b.and(listOf(b.or(listOf(l[0], l[1])), l[0])) }
        val store = RulePredicateStore(manager)
        val first = store.accept(predicate, 0, input(0))
        assertEquals(1, first.size)
        assertEquals(setOf(premise(0)), first.single().premise.members.toSet())
        val otherA = store.accept(predicate, 0, input(2))
        assertEquals(1, otherA.size)
        assertEquals(setOf(premise(2)), otherA.single().premise.members.toSet())
        val fromB = store.accept(predicate, 1, input(1))
        assertEquals(setOf(setOf(premise(0), premise(1)), setOf(premise(2), premise(1))),
            fromB.map { it.premise.members.toSet() }.toSet())
    }

    @Test fun `shared OR graph nodes can take different branches through separate AND operands`() {
        val predicate = build { b, l ->
            val shared = b.or(listOf(l[0], l[1]))
            b.and(listOf(shared, b.or(listOf(shared, l[2]))))
        }
        val store = RulePredicateStore(manager)
        val outcomes = listOf(0, 1, 2).flatMap { store.accept(predicate, it, input(it)) }
        val expected = setOf(setOf(premise(0)), setOf(premise(1)), setOf(premise(0), premise(1)),
            setOf(premise(0), premise(2)), setOf(premise(1), premise(2)))
        assertEquals(expected, outcomes.map { it.premise.members.toSet() }.toSet())
    }

    @Test fun `layers zero and occurrence scopes survive independent predicate rebuilds`() {
        fun predicate() = build { b, l -> b.and(listOf(l[0], l[1])) }
        val first = predicate()
        val rebuilt = predicate()
        assertNotSame(first, rebuilt)
        val store = RulePredicateStore(manager)
        assertTrue(store.accept(first, 0, ConjunctionStore.Input(manager.zero, Layer.NORMAL)).isEmpty())
        assertTrue(store.accept(rebuilt, 1, input(1), rule.copy(ordinal = 1)).isEmpty())
        val result = store.accept(rebuilt, 1, input(1, Layer.DEMAND)).single()
        assertSame(premise(1), result.premise)
        assertEquals(Layer.DEMAND, result.layer)
        val others = store.accept(rebuilt, 0, input(0), rule.copy(ordinal = 1)).single()
        assertEquals(Layer.NORMAL, others.layer)
    }

    @Test fun `growing checked sink facts emit new versions within one structural proof`() {
        val predicate = build { b, l -> b.and(listOf(l[0], l[1])) }
        val store = RulePredicateStore(manager)
        val field = fixture.field("child")
        val a = ConjunctionStore.Input(premise(0), Layer.NORMAL, fixture.fact(base = AccessPathBase.Argument(0)))
        val b = ConjunctionStore.Input(premise(1), Layer.NORMAL, fixture.fact(base = AccessPathBase.Argument(1)))
        assertTrue(store.accept(predicate, 0, a).isEmpty())
        val initial = store.accept(predicate, 1, b).single()
        val growth = a.copy(facts = fixture.fact(base = AccessPathBase.Argument(0), path = listOf(field)))
        val updated = store.accept(predicate, 0, growth).single()
        assertEquals(initial.proof, updated.proof)
        val facts = checkNotNull(updated.inputs[0].facts)
        assertEquals(2, fixture.ops.leaves(facts).count())
        assertTrue(store.accept(predicate, 0, a).isEmpty())
        assertTrue(store.accept(predicate, 0, growth).isEmpty())
    }

    @Test fun `wide predicates join supported branches iteratively`() {
        val builder = RulePredicate.Builder(fixture.cancellation)
        val count = 1024
        val leaves = List(count) { builder.leaf(pattern(it)) }
        val predicate = builder.build(builder.and(leaves))
        val store = RulePredicateStore(manager)
        repeat(count - 1) { assertTrue(store.accept(predicate, it, ConjunctionStore.Input(manager.zero, Layer.NORMAL)).isEmpty()) }
        val result = store.accept(predicate, count - 1, ConjunctionStore.Input(manager.zero, Layer.NORMAL)).single()
        assertEquals(count, result.inputs.size)
        assertSame(manager.zero, result.premise)
    }

    @Test fun `equal structural branches in distinct occurrences have separate proof keys`(): Unit {
        val predicate = build { _, l -> l[0] }
        val store = RulePredicateStore(manager)
        val first = store.accept(predicate, 0, input(0)).single()
        val otherRule = rule.copy(ordinal = 1)
        val other = store.accept(predicate, 0, input(0), otherRule).single()
        assertNotEquals(first.proof, other.proof)
        assertEquals(ProofKey(rule, listOf(0)), first.proof)
        assertEquals(ProofKey(otherRule, listOf(0)), other.proof)
        val leaves = arrayListOf(0)
        val choices = arrayListOf(1, 2)
        val owned = ProofKey(rule, leaves, choices)
        leaves.clear(); choices.clear()
        assertEquals(ProofKey(rule, listOf(0), listOf(1, 2)), owned)
        assertEquals(ProofKey(rule, listOf(0), listOf(1, 2)).hashCode(), owned.hashCode())
    }

    @Test fun `shared OR branch permutations emit each complete proof and growing version once`(): Unit {
        fun predicate() = build { b, l ->
            val shared = b.or(listOf(l[0], l[1]))
            b.and(listOf(b.or(listOf(shared, l[2])), b.or(listOf(shared, l[3]))))
        }
        val firstPredicate = predicate()
        val rebuilt = predicate()
        val store = RulePredicateStore(manager)
        val a = ConjunctionStore.Input(premise(0), Layer.NORMAL,
            fixture.fact(base = AccessPathBase.Argument(0)))
        val b = ConjunctionStore.Input(premise(1), Layer.NORMAL,
            fixture.fact(base = AccessPathBase.Argument(1)))
        assertEquals(1, store.accept(firstPredicate, 0, a).size)
        val new = store.accept(rebuilt, 1, b)
        assertEquals(setOf(setOf(premise(1)), setOf(premise(0), premise(1))),
            new.map { it.premise.members.toSet() }.toSet())
        assertEquals(new.size, new.map { it.proof to it.premise }.toSet().size,
            "Swapping the same shared OR choices cannot emit an exact duplicate")
        val child = fixture.field("child")
        val growth = a.copy(facts = fixture.fact(base = AccessPathBase.Argument(0), path = listOf(child)))
        val updated = store.accept(rebuilt, 0, growth)
        assertEquals(updated.size, updated.map { it.proof to it.premise }.toSet().size)
        assertEquals(setOf(setOf(premise(0)), setOf(premise(0), premise(1))),
            updated.map { it.premise.members.toSet() }.toSet())
        for (combination in updated) {
            val ai = combination.leaves.indexOf(ordinal(rebuilt, 0))
            assertEquals(2, fixture.ops.leaves(checkNotNull(combination.inputs[ai].facts)).count())
        }
        assertTrue(store.accept(rebuilt, 0, growth).isEmpty())
    }

    @Test fun `deep supported proof chains retain shared records and compare across rebuilds`(): Unit {
        fun predicate(): RulePredicate {
            val builder = RulePredicate.Builder(fixture.cancellation)
            val a = builder.leaf(pattern(0))
            val b = builder.leaf(pattern(1))
            var root = a
            repeat(32768) { depth ->
                root = if (depth % 2 == 0) builder.or(listOf(root, b)) else builder.and(listOf(root, a))
            }
            return builder.build(root)
        }
        val first = predicate()
        val rebuilt = predicate()
        val store = RulePredicateStore(manager)
        val other = RulePredicateStore(manager)
        val value = ConjunctionStore.Input(premise(0), Layer.NORMAL,
            fixture.fact(base = AccessPathBase.Argument(0)))
        val initial = store.accept(first, 0, value).single()
        val independent = other.accept(rebuilt, 0, value).single()
        assertEquals(initial.proof, independent.proof)
        assertEquals(initial.proof.hashCode(), independent.proof.hashCode())
        assertEquals(listOf(0), initial.leaves)
        val field = fixture.field("child")
        val growth = value.copy(facts = fixture.fact(base = AccessPathBase.Argument(0), path = listOf(field)))
        val updated = store.accept(rebuilt, 0, growth).single()
        assertEquals(initial.proof, updated.proof)
        assertEquals(2, fixture.ops.leaves(checkNotNull(updated.inputs.single().facts)).count())
        assertTrue(store.accept(rebuilt, 0, growth).isEmpty())

        val retained = ownedObjects(store)
        assertTrue(retained.none { it is RulePredicate }, "Standing proofs must not retain compiled topology")
        val proofs = retained.filter { it.javaClass.simpleName.endsWith("Proof") }
        assertTrue(proofs.size >= 32769, "Supported nodes must retain shared proof records")
        val internedBefore = proofs.size
        store.accept(rebuilt, 0, value)
        assertEquals(internedBefore, ownedObjects(store).count { it.javaClass.simpleName.endsWith("Proof") })
    }

    /** Inspect only the store's owned records and containers, not its AP manager or IR owners. */
    private fun ownedObjects(store: RulePredicateStore): Set<Any> {
        val states = store.javaClass.getDeclaredField("states").apply { isAccessible = true }.get(store)
        val seen = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        val work = ArrayDeque<Any>()
        work.add(states)
        while (work.isNotEmpty()) {
            val value = work.removeLast()
            if (!seen.add(value)) continue
            when (value) {
                is Map<*, *> -> value.forEach { (key, item) ->
                    if (key != null) work.add(key)
                    if (item != null) work.add(item)
                }
                is Iterable<*> -> value.forEach { if (it != null) work.add(it) }
                is Array<*> -> value.forEach { if (it != null) work.add(it) }
                else -> {
                    var type: Class<*>? = value.javaClass
                    while (type != null && type.name.startsWith(RulePredicateStore::class.java.name + "$")) {
                        for (field in type.declaredFields) {
                            if (java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
                            field.isAccessible = true
                            field.get(value)?.let(work::add)
                        }
                        type = type.superclass
                    }
                }
            }
        }
        return seen
    }
}
