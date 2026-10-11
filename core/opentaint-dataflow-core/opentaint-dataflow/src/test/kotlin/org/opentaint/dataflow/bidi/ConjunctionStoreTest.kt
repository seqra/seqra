package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.ConjunctionStore
import org.opentaint.dataflow.bidi.store.NdSummaryJoin
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Finite slot products and explicit conclusion leaves supply independent join expectations. */
class ConjunctionStoreTest {
    private val fixture = StoreFixtures()
    private val v = StoreValues(fixture)
    private val m = fixture.manager

    private fun <T> orders(xs: List<T>): List<List<T>> =
        if (xs.isEmpty()) listOf(emptyList()) else xs.indices.flatMap { i ->
            orders(xs.filterIndexed { j, _ -> i != j }).map { listOf(xs[i]) + it }
        }

    private fun <T> product(slots: List<List<T>>): Set<List<T>> =
        slots.fold(setOf<List<T>>(emptyList())) { rows, slot -> rows.flatMap { row -> slot.map { row + it } }.toSet() }

    private fun members(key: PremiseKey): Set<InitialAp> = key.members.toSet()

    @Test
    fun `each finite full literal product appears once in every arrival order including duplicate deliveries`(): Unit = with(v) {
        val a = ConjunctionStore.Input(premise(), Layer.NORMAL, pack(leaf(p(f))))
        val b = ConjunctionStore.Input(premise(p(f)), Layer.DEMAND, pack(leaf(p(g), layer = Layer.DEMAND)))
        val c = ConjunctionStore.Input(premise(mark = mu), Layer.NORMAL, pack(leaf(p(h), mark = mu)))
        val d = ConjunctionStore.Input(m.zero, Layer.NORMAL, pack(leaf(p(h))))
        for (slots in listOf(listOf(listOf(a, b), listOf(c, d)), listOf(listOf(a, b), listOf(c), listOf(d)))) {
            val events = slots.flatMapIndexed { index, inputs -> inputs.map { index to it } }
            for (order in orders(events)) {
                val store = ConjunctionStore(m)
                val rule = Any()
                val seen = Array(slots.size) { LinkedHashSet<ConjunctionStore.Input>() }
                val emitted = ArrayList<List<ConjunctionStore.Input>>()
                for ((slot, input) in order) {
                    val previous = product(seen.map { it.toList() })
                    seen[slot] += input
                    val expected = product(seen.map { it.toList() }) - previous
                    val actual = store.add(rule, fixture.inst(1), slots.size, slot, input)
                    assertEquals(expected, actual.map { it.inputs }.toSet(), "$slots $order $slot")
                    assertEquals(expected.size, actual.size)
                    for (combo in actual) {
                        val nonzero = combo.inputs.flatMap { it.premise.members }.filterNot { it.isZero }.toSet()
                        assertEquals(nonzero.ifEmpty { setOf(m.zero) }, members(combo.premise))
                        assertEquals(if (combo.inputs.any { it.layer == Layer.DEMAND }) Layer.DEMAND else Layer.NORMAL, combo.layer)
                    }
                    emitted += actual.map { it.inputs }
                    assertTrue(store.add(rule, fixture.inst(1), slots.size, slot, input.copy()).isEmpty())
                }
                assertEquals(product(slots), emitted.toSet())
                assertEquals(emitted.toSet().size, emitted.size)
            }
        }
    }

    @Test
    fun `repeated literal positions retain separate slots and one accepted input may satisfy both`(): Unit = with(v) {
        val literal = fixture.pattern(path = p(f))
        val rule = ConjunctiveEdge(listOf(literal, literal, literal), fixture.pattern(path = p(g)).fact)
        val input = ConjunctionStore.Input(premise(), Layer.NORMAL, pack(leaf(p(f))))
        for (order in orders(listOf(0, 1, 2))) {
            val store = ConjunctionStore(m)
            for (slot in order.take(2)) assertTrue(store.add(rule, fixture.inst(1), 3, slot, input).isEmpty())
            val combo = store.add(rule, fixture.inst(1), 3, order.last(), input).single()
            assertEquals(listOf(input, input, input), combo.inputs)
            assertSame(input.premise, combo.premise)
            assertEquals(Layer.NORMAL, combo.layer)
            assertTrue(store.add(rule, fixture.inst(1), 3, order.last(), input).isEmpty())
        }
    }

    @Test
    fun `accepted facts and effective contribution layers are part of literal input identity`(): Unit = with(v) {
        val store = ConjunctionStore(m)
        val rule = Any()
        val key = premise()
        val firstFacts = pack(leaf(p(f)))
        val otherFacts = pack(leaf(p(g)))
        val inputs = listOf(ConjunctionStore.Input(key, Layer.NORMAL, firstFacts),
            ConjunctionStore.Input(key, Layer.NORMAL, otherFacts),
            ConjunctionStore.Input(key, Layer.DEMAND, firstFacts),
            ConjunctionStore.Input(key, Layer.NORMAL))
        for (input in inputs) assertTrue(store.add(rule, fixture.inst(1), 2, 0, input).isEmpty())
        val right = ConjunctionStore.Input(key, Layer.NORMAL, firstFacts)
        val actual = store.add(rule, fixture.inst(1), 2, 1, right)
        assertEquals(inputs.map { listOf(it, right) }.toSet(), actual.map { it.inputs }.toSet())
        assertEquals(4, actual.size)
        assertEquals(listOf(Layer.NORMAL, Layer.NORMAL, Layer.DEMAND, Layer.NORMAL), actual.map { it.layer })
        for (input in inputs) assertTrue(store.add(rule, fixture.inst(1), 2, 0, input.copy()).isEmpty())
    }

    @Test
    fun `zero is dropped from unions while all-zero combinations keep zero independent of joined layers`(): Unit = with(v) {
        val p1 = premise()
        val p2 = premise(mark = mu)
        val p3 = premise(p(f))
        val cases = listOf(
            listOf(m.zero, m.zero, m.zero) to setOf(m.zero),
            listOf(m.zero, p1, p1) to setOf(p1),
            listOf(m.zero, p1, p2) to setOf(p1, p2),
            listOf(m.premiseOf(listOf(m.zero, p1, p2)), p3, p2) to setOf(p1, p2, p3))
        for ((keys, expectedMembers) in cases) for (demandSlot in -1..2) {
            val store = ConjunctionStore(m)
            val rule = Any()
            val input = keys.mapIndexed { index, key -> ConjunctionStore.Input(key,
                if (index == demandSlot) Layer.DEMAND else Layer.NORMAL) }
            assertTrue(store.add(rule, fixture.inst(1), 3, 0, input[0]).isEmpty())
            assertTrue(store.add(rule, fixture.inst(1), 3, 1, input[1]).isEmpty())
            val combo = store.add(rule, fixture.inst(1), 3, 2, input[2]).single()
            assertEquals(expectedMembers, members(combo.premise))
            assertSame(m.premiseOf(expectedMembers), combo.premise)
            assertEquals(if (demandSlot < 0) Layer.NORMAL else Layer.DEMAND, combo.layer)
        }
    }

    @Test
    fun `alternatives rule objects statements and method-owned contexts never supply each other's slots`(): Unit = with(v) {
        val literal = fixture.pattern(path = p(f))
        fun rule() = ConjunctiveEdge(listOf(literal, literal), fixture.pattern(path = p(g)).fact)
        val rules = listOf(rule(), rule(), Any(), Any())
        val statements = listOf(fixture.inst(1), fixture.inst(2))
        val contexts = listOf(fixture.key("first"), fixture.key("second"))
        val stores = contexts.associateWith { ConjunctionStore(m) }
        val input = ConjunctionStore.Input(premise(), Layer.NORMAL, pack(leaf(p(f))))
        for (context in contexts) for (r in rules) for (statement in statements) {
            val store = stores.getValue(context)
            assertTrue(store.add(r, statement, 2, 0, input).isEmpty())
            for (otherContext in contexts) for (otherRule in rules) for (otherStatement in statements) {
                if (otherContext == context && otherRule === r && otherStatement === statement) continue
                // A fresh probe store has only one occupied slot in each distinct key.
                val probes = contexts.associateWith { ConjunctionStore(m) }
                probes.getValue(context).add(r, statement, 2, 0, input)
                assertTrue(probes.getValue(otherContext).add(otherRule, otherStatement, 2, 1, input).isEmpty())
            }
            assertEquals(listOf(input, input), store.add(r, statement, 2, 1, input).single().inputs)
        }
    }

    @Test
    fun `ND key includes callee context complete premise publication layer and caller call`(): Unit = with(v) {
        val store = ConjunctionStore(m)
        val keys = listOf(
            ConjunctionStore.NdKey(fixture.key("callee-a"), nd(), Layer.NORMAL, fixture.inst(1)),
            ConjunctionStore.NdKey(fixture.key("callee-b"), nd(), Layer.NORMAL, fixture.inst(1)),
            ConjunctionStore.NdKey(fixture.key("callee-a", 2), nd(), Layer.NORMAL, fixture.inst(1)),
            ConjunctionStore.NdKey(fixture.key("callee-a"), m.premiseOf(listOf(premise(), premise(p(f)))), Layer.NORMAL, fixture.inst(1)),
            ConjunctionStore.NdKey(fixture.key("callee-a"), nd(), Layer.DEMAND, fixture.inst(1)),
            ConjunctionStore.NdKey(fixture.key("callee-a"), nd(), Layer.NORMAL, fixture.inst(2)))
        val joins = keys.map { key -> store.ndJoin<String>(key) }
        for (i in keys.indices) {
            assertSame(joins[i], store.ndJoin<String>(keys[i].copy()))
            for (j in keys.indices) if (i != j) assertNotSame(joins[i], joins[j])
            assertTrue(joins[i].addSubscription(0, "left-$i").isEmpty())
            assertTrue(joins[i].addConclusion(pack(leaf(p(f), layer = keys[i].layer)) as TaintTree).isEmpty())
        }
        for (i in keys.indices) {
            val actual = joins[i].addSubscription(1, "right-$i")
            assertEquals(listOf("left-$i", "right-$i"), actual.single().first)
            assertEquals(setOf(leaf(p(f), layer = keys[i].layer)), decoded(actual.single().second))
        }
    }

    private data class NdEvent(val slot: Int, val subscription: String? = null, val leaves: List<Conclusion>? = null)

    @Test
    fun `ND subscriptions before or after conclusions enumerate every accepted finite combination once`(): Unit = with(v) {
        val slots = listOf(listOf("a", "b"), listOf("c", "d"))
        val outputs = listOf(leaf(p(f)), leaf(p(g), mark = mu))
        val events = slots.flatMapIndexed { slot, inputs -> inputs.map { NdEvent(slot, it) } } +
            outputs.map { NdEvent(-1, leaves = listOf(it)) }
        val expected = product(slots).flatMap { combo -> outputs.map { combo to it } }.toSet()
        for (order in orders(events)) {
            val join = NdSummaryJoin<String>(m, 2)
            val observed = ArrayList<Pair<List<String>, Conclusion>>()
            for (event in order) {
                val actual = if (event.subscription != null) join.addSubscription(event.slot, event.subscription)
                    else join.addConclusion(pack(*event.leaves!!.toTypedArray()) as TaintTree)
                for ((combo, facts) in actual) observed += decoded(facts).map { combo to it }
                val duplicate = if (event.subscription != null) join.addSubscription(event.slot, event.subscription)
                    else join.addConclusion(pack(*event.leaves!!.toTypedArray()) as TaintTree)
                assertTrue(duplicate.isEmpty())
            }
            assertEquals(expected, observed.toSet(), "$order")
            assertEquals(expected.size, observed.size)
        }
    }

    @Test
    fun `ND same accepted input occupies every matching member slot and needs every member`(): Unit = with(v) {
        val join = NdSummaryJoin<ConjunctionStore.Input>(m, 3)
        val input = ConjunctionStore.Input(premise(), Layer.NORMAL, pack(leaf(p(f))))
        val target = pack(leaf(p(g), Tail.ANY_TAINT, e = ex(h))) as TaintTree
        assertTrue(join.addConclusion(target).isEmpty())
        assertTrue(join.addSubscription(2, input).isEmpty())
        assertTrue(join.addSubscription(0, input).isEmpty())
        val pair = join.addSubscription(1, input).single()
        assertEquals(listOf(input, input, input), pair.first)
        assertEquals(setOf(leaf(p(g), Tail.ANY_TAINT, e = ex(h))), decoded(pair.second))
        assertTrue(join.addSubscription(1, input.copy()).isEmpty())
        assertTrue(join.addConclusion(target).isEmpty())
    }

    @Test
    fun `E6 stores already matched TAINT parts and leaves publication and input layer joining to application`(): Unit = with(v) {
        val initial = listOf(premise(), premise(p(f), mark = mu))
        val publicationPremise = m.premiseOf(initial)
        val mode = ApMode(run1 = false, direction = Direction.FORWARD, fieldLimit = 8)
        for (publicationLayer in Layer.entries) for (leftLayer in Layer.entries) for (rightLayer in Layer.entries) {
            val store = ConjunctionStore(m)
            val key = ConjunctionStore.NdKey(fixture.method, publicationPremise, publicationLayer, fixture.inst(1))
            val join = store.ndJoin<ConjunctionStore.Input>(key)
            val inputFacts = listOf(
                pack(leaf(p(), layer = leftLayer), leaf(p(h), mark = mu, layer = leftLayer)),
                pack(leaf(p(f), mark = mu, layer = rightLayer), leaf(p(g), layer = rightLayer)))
            val accepted = inputFacts.mapIndexed { index, facts -> assertNotNull(fixture.ops.satisfying(facts, initial[index], mode)) }
            assertEquals(setOf(leaf(p(), layer = leftLayer)), decoded(accepted[0]))
            assertEquals(setOf(leaf(p(f), mark = mu, layer = rightLayer)), decoded(accepted[1]))
            val callerPremises = listOf(m.zero, premise(p(h)))
            val inputs = accepted.mapIndexed { index, facts -> ConjunctionStore.Input(callerPremises[index], facts.layer, facts) }
            val target = pack(leaf(p(g), if (publicationLayer == Layer.NORMAL) Tail.ANY_TAINT else Tail.ANY,
                e = if (publicationLayer == Layer.NORMAL) ex(h) else ExclusionSet.Empty, layer = publicationLayer)) as TaintTree
            assertTrue(join.addSubscription(0, inputs[0]).isEmpty())
            assertTrue(join.addConclusion(target).isEmpty())
            val (combo, stored) = join.addSubscription(1, inputs[1]).single()
            assertEquals(inputs, combo)
            assertEquals(decoded(target), decoded(stored))
            val out = CollectingOut()
            fixture.ops.applyCombination(combo.mapIndexed { index, input -> input.facts!! to initial[index] }, stored, mode, out)
            val effective = if (publicationLayer == Layer.DEMAND || leftLayer == Layer.DEMAND || rightLayer == Layer.DEMAND)
                Layer.DEMAND else Layer.NORMAL
            val expected = leaf(p(g), if (effective == Layer.NORMAL) Tail.ANY_TAINT else Tail.ANY,
                e = if (effective == Layer.NORMAL) ex(h) else ExclusionSet.Empty, layer = effective)
            assertEquals(setOf(expected), out.results.flatMap { decoded(it) }.toSet())
            assertSame(callerPremises[1], combo.map { it.premise }.reduce(m::union))
            assertTrue(out.markRequests.isEmpty() && out.positionRequests.isEmpty())
        }
    }

    @Test
    fun `ND T1 conclusion growth applies only new leaves to each full combination and late subscribers see merged payloads`(): Unit = with(v) {
        val join = NdSummaryJoin<String>(m, 2)
        join.addSubscription(0, "a")
        join.addSubscription(0, "b")
        assertTrue(join.addSubscription(1, "c").isEmpty())
        val first = listOf(leaf(p(f)), leaf(p(g), mark = mu))
        val fresh = leaf(p(h))
        val combos = setOf(listOf("a", "c"), listOf("b", "c"))
        val initial = join.addConclusion(pack(*first.toTypedArray()) as TaintTree)
        assertEquals(combos.flatMap { c -> first.map { c to it } }.toSet(),
            initial.flatMap { (c, facts) -> decoded(facts).map { c to it } }.toSet())
        val grown = join.addConclusion(pack(*(first + fresh).toTypedArray()) as TaintTree)
        assertEquals(combos.map { it to fresh }.toSet(), grown.flatMap { (c, facts) -> decoded(facts).map { c to it } }.toSet())
        assertTrue(join.addConclusion(pack(*first.toTypedArray()) as TaintTree).isEmpty())
        val late = join.addSubscription(1, "d")
        assertEquals(setOf(listOf("a", "d"), listOf("b", "d")).flatMap { c -> (first + fresh).map { c to it } }.toSet(),
            late.flatMap { (c, facts) -> decoded(facts).map { c to it } }.toSet())
    }

    @Test
    fun `ND T2 exclusion intersection returns the whole merged payload for all standing combinations`(): Unit = with(v) {
        val exact = leaf(p(h), mark = mu)
        val left = listOf(leaf(p(f), Tail.ANY_TAINT, e = ex(g)), exact)
        val right = listOf(leaf(p(f), Tail.ANY_TAINT, e = ex(h)), exact)
        val merged = setOf(leaf(p(f), Tail.ANY_TAINT), exact)
        for (order in listOf(listOf(left, right), listOf(right, left))) {
            val join = NdSummaryJoin<String>(m, 2)
            join.addSubscription(0, "a")
            join.addSubscription(0, "b")
            join.addSubscription(1, "c")
            join.addConclusion(pack(*order[0].toTypedArray()) as TaintTree)
            val delta = join.addConclusion(pack(*order[1].toTypedArray()) as TaintTree)
            assertEquals(setOf(listOf("a", "c"), listOf("b", "c")).flatMap { c -> merged.map { c to it } }.toSet(),
                delta.flatMap { (c, facts) -> decoded(facts).map { c to it } }.toSet())
            assertEquals(2, delta.size)
            val late = join.addSubscription(1, "d")
            assertEquals(setOf(listOf("a", "d"), listOf("b", "d")).flatMap { c -> merged.map { c to it } }.toSet(),
                late.flatMap { (c, facts) -> decoded(facts).map { c to it } }.toSet())
            assertTrue(join.addConclusion(pack(*left.toTypedArray()) as TaintTree).isEmpty())
            assertTrue(join.addConclusion(pack(*right.toTypedArray()) as TaintTree).isEmpty())
        }
    }

    @Test
    fun `ND stored bases layers and exclusion groups survive until a late subscription completes them`(): Unit = with(v) {
        val join = NdSummaryJoin<String>(m, 2)
        val leaves = listOf(leaf(p(f), Tail.ANY_TAINT, e = ex(g)), leaf(p(h), Tail.ANY_TAINT, e = ex(f)),
            leaf(p(f), base = y), leaf(p(g), layer = Layer.DEMAND))
        for (value in leaves) assertTrue(join.addConclusion(pack(value) as TaintTree).isEmpty())
        assertTrue(join.addSubscription(0, "accepted-left").isEmpty())
        val pairs = join.addSubscription(1, "accepted-right")
        assertEquals(leaves.toSet(), pairs.flatMap { decoded(it.second) }.toSet())
        assertTrue(pairs.all { it.first == listOf("accepted-left", "accepted-right") })
        for (value in leaves) assertTrue(join.addConclusion(pack(value) as TaintTree).isEmpty())
    }

    @Test
    fun `returned ND tuples cannot change retained accepted subscription combinations`(): Unit = with(v) {
        for (fromSubscription in listOf(true, false)) {
            val join = NdSummaryJoin<String>(m, 2)
            val first = pack(leaf(p(f))) as TaintTree
            val next = pack(leaf(p(g), mark = mu)) as TaintTree
            val last = pack(leaf(p(h))) as TaintTree
            val returned = if (fromSubscription) {
                join.addConclusion(first)
                join.addSubscription(0, "accepted-left")
                join.addSubscription(1, "accepted-right").single()
            } else {
                join.addSubscription(0, "accepted-left")
                join.addSubscription(1, "accepted-right")
                join.addConclusion(first).single()
            }
            @Suppress("UNCHECKED_CAST")
            val writable = returned.first as? MutableList<String>
            try { writable?.set(0, "alien-left") } catch (_: UnsupportedOperationException) { }
            val later = join.addConclusion(next).single()
            assertEquals(listOf("accepted-left", "accepted-right"), later.first, "returned by subscription=$fromSubscription")
            assertEquals(decoded(next), decoded(later.second))
            @Suppress("UNCHECKED_CAST")
            val laterWritable = later.first as? MutableList<String>
            try { laterWritable?.set(1, "alien-right") } catch (_: UnsupportedOperationException) { }
            val final = join.addConclusion(last).single()
            assertEquals(listOf("accepted-left", "accepted-right"), final.first)
            assertEquals(decoded(last), decoded(final.second))
            assertTrue(join.addSubscription(0, "accepted-left").isEmpty())
            assertTrue(join.addSubscription(1, "accepted-right").isEmpty())
        }
    }

    @Test
    fun `invalid arities and literal slots fail before storing any useful input`(): Unit = with(v) {
        for (arity in listOf(-1, 0, 1)) {
            assertFails { NdSummaryJoin<String>(m, arity) }
            assertFails { ConjunctionStore(m).add(Any(), fixture.inst(1), arity, 0,
                ConjunctionStore.Input(premise(), Layer.NORMAL)) }
        }
        val rule = Any()
        val store = ConjunctionStore(m)
        val input = ConjunctionStore.Input(premise(), Layer.NORMAL)
        for (slot in listOf(-1, 2, Int.MAX_VALUE)) assertFails { store.add(rule, fixture.inst(1), 2, slot, input) }
        assertTrue(store.add(rule, fixture.inst(1), 2, 0, input).isEmpty())
        assertFails { store.add(rule, fixture.inst(1), 3, 1, input) }
        assertEquals(listOf(input, input), store.add(rule, fixture.inst(1), 2, 1, input).single().inputs)
        val freshRule = Any()
        assertFails { store.add(freshRule, fixture.inst(1), 2, 2, input) }
        assertTrue(store.add(freshRule, fixture.inst(1), 3, 0, input).isEmpty())
        assertTrue(store.add(freshRule, fixture.inst(1), 3, 1, input).isEmpty())
        assertEquals(listOf(input, input, input), store.add(freshRule, fixture.inst(1), 3, 2, input).single().inputs)
        val join = NdSummaryJoin<String>(m, 2)
        for (slot in listOf(-1, 2, Int.MAX_VALUE)) assertFails { join.addSubscription(slot, "bad") }
        assertTrue(join.addSubscription(0, "left").isEmpty())
        assertTrue(join.addSubscription(1, "right").isEmpty())
        assertEquals(listOf("left", "right"), join.addConclusion(pack(leaf(p(f))) as TaintTree).single().first)
        assertFails { store.ndJoin<String>(ConjunctionStore.NdKey(fixture.method, premise(), Layer.NORMAL, fixture.inst(1))) }
        assertFails { store.ndJoin<String>(ConjunctionStore.NdKey(fixture.method, m.zero, Layer.NORMAL, fixture.inst(1))) }
    }

    @Test
    fun `cancelled joins retain existing state and resume new subscriptions and conclusions after activation`(): Unit = with(v) {
        val store = ConjunctionStore(m)
        val rule = Any()
        val input = ConjunctionStore.Input(premise(), Layer.NORMAL)
        store.add(rule, fixture.inst(1), 2, 0, input)
        val join = NdSummaryJoin<String>(m, 2)
        join.addSubscription(0, "left")
        val first = pack(leaf(p(f))) as TaintTree
        val fresh = pack(leaf(p(g), mark = mu)) as TaintTree
        join.addConclusion(first)
        val key = ConjunctionStore.NdKey(fixture.method, nd(), Layer.NORMAL, fixture.inst(1))
        fixture.cancellation.cancel()
        try {
            assertFailsWith<Cancellation.Cancelled> { store.add(rule, fixture.inst(1), 2, 1, input) }
            assertFailsWith<Cancellation.Cancelled> { store.ndJoin<String>(key) }
            assertFailsWith<Cancellation.Cancelled> { join.addSubscription(1, "right") }
            assertFailsWith<Cancellation.Cancelled> { join.addConclusion(fresh) }
        } finally { fixture.cancellation.activate() }
        assertEquals(listOf(input, input), store.add(rule, fixture.inst(1), 2, 1, input).single().inputs)
        val resumed = join.addSubscription(1, "right").single()
        assertEquals(listOf("left", "right"), resumed.first)
        assertEquals(decoded(first), decoded(resumed.second))
        val next = join.addConclusion(fresh).single()
        assertEquals(listOf("left", "right"), next.first)
        assertEquals(decoded(fresh), decoded(next.second))
    }
}
