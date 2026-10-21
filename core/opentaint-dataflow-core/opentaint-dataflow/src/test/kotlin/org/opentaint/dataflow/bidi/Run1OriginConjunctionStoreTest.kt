package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.ConjunctionStore
import org.opentaint.dataflow.bidi.store.Run1OriginConjunctionStore
import kotlin.test.*

class Run1OriginConjunctionStoreTest {
    private val f = StoreFixtures()
    private val m = f.manager
    private val node = f.inst(1)
    private fun origin(i: Int) = m.initial(f.pattern(base = AccessPathBase.Argument(i)))
    private fun input(i: Int, layer: Layer = Layer.NORMAL) = ConjunctionStore.Input(origin(i), layer)

    private fun oracle(slots: List<List<ConjunctionStore.Input>>): Set<Pair<InitialAp, Layer>> {
        val tuples = slots.fold(listOf(emptyList<ConjunctionStore.Input>())) { rows, slot ->
            rows.flatMap { row -> slot.map { row + it } }
        }
        return tuples.flatMap { row ->
            val nonzero = row.map { it.premise as InitialAp }.filterNot { it.isZero }.toSet()
            val layer = if (row.any { it.layer == Layer.DEMAND }) Layer.DEMAND else Layer.NORMAL
            nonzero.ifEmpty { setOf(m.zero) }.map { it to layer }
        }.toSet()
    }

    @Test fun `complete origin arms equal independent scalar products after each arrival`() {
        val events = listOf(0 to input(0), 1 to input(1, Layer.DEMAND), 0 to input(2),
            0 to ConjunctionStore.Input(m.zero, Layer.NORMAL),
            1 to ConjunctionStore.Input(m.zero, Layer.DEMAND))
        for (order in listOf(events, events.reversed(), events.drop(2) + events.take(2))) {
            val store = Run1OriginConjunctionStore(m)
            val rule = Any()
            val slots = List(2) { arrayListOf<ConjunctionStore.Input>() }
            val seen = linkedSetOf<Pair<InitialAp, Layer>>()
            for ((slot, value) in order) {
                slots[slot] += value
                store.addOrigins(rule, node, 2, slot, value) { i, layer -> seen += i to layer }
                assertEquals(oracle(slots), seen)
                val delta = arrayListOf<InitialAp>()
                store.addOrigins(rule, node, 2, slot, value) { i, _ -> delta += i }
                assertTrue(delta.isEmpty())
            }
        }
    }

    @Test fun `ZERO requires same complete partition and late all zero binding`() {
        val store = Run1OriginConjunctionStore(m)
        val rule = Any()
        val out = arrayListOf<InitialAp>()
        fun add(slot: Int, value: ConjunctionStore.Input) =
            store.addOrigins(rule, node, 3, slot, value) { i, _ -> out += i }
        val z = ConjunctionStore.Input(m.zero, Layer.NORMAL)
        add(0, z)
        add(1, z)
        assertTrue(out.isEmpty())
        add(2, input(3))
        assertEquals(listOf(origin(3)), out)
        add(2, z)
        assertEquals(listOf(origin(3), m.zero), out)
        add(2, z)
        assertEquals(2, out.size)
    }

    @Test fun `fixed independent headers visit memberships without origin products`() {
        val store = Run1OriginConjunctionStore(m)
        val rule = Any()
        val seen = hashSetOf<InitialAp>()
        // The second domain arrives first. Filling the first domain must not enumerate 40*60 bindings.
        for (i in 40 until 100) store.addOrigins(rule, node, 2, 1, input(i)) { origin, _ -> seen += origin }
        for (i in 0 until 40) store.addOrigins(rule, node, 2, 0, input(i)) { origin, _ -> seen += origin }
        assertEquals((0 until 100).map(::origin).toSet(), seen)
        assertEquals(100, store.originMembershipVisits)
        val sets = m.javaClass.getDeclaredField("premiseSets").apply { isAccessible = true }.get(m) as Map<*, *>
        assertTrue(sets.isEmpty())
    }

    @Test fun `local field and exact class origins all contribute without a preference`() {
        val cls = m.accessors.index(ClassStaticAccessor("Owner"))
        val origins = listOf(origin(0), m.initial(f.pattern(base = AccessPathBase.This)),
            m.initial(f.pattern(base = STATIC, path = listOf(cls, f.field("shared")), tail = Tail.STAR)),
            m.initial(f.pattern(base = STATIC, path = listOf(cls))))
        val store = Run1OriginConjunctionStore(m)
        val rule = Any()
        val observed = hashSetOf<InitialAp>()
        origins.forEachIndexed { slot, initial ->
            store.addOrigins(rule, node, origins.size, slot, ConjunctionStore.Input(initial, Layer.DEMAND)) { i, layer ->
                assertEquals(Layer.DEMAND, layer)
                observed += i
            }
            if (slot != origins.lastIndex) assertTrue(observed.isEmpty())
        }
        assertEquals(origins.toSet(), observed)
    }

    @Test fun `distinct slots scope validation atomic inputs and cancellation remain enforced`() {
        val store = Run1OriginConjunctionStore(m)
        val rule = Any()
        val a = input(0)
        val out = arrayListOf<InitialAp>()
        val emit: (InitialAp, Layer) -> Unit = { i, _ -> out += i }
        store.addOrigins(rule, node, 3, 0, a, emit)
        store.addOrigins(rule, node, 3, 1, a, emit)
        assertTrue(out.isEmpty())
        store.addOrigins(rule, node, 3, 2, a, emit)
        assertEquals(listOf(origin(0)), out)
        assertFails { store.addOrigins(rule, node, 2, 1, a, emit) }
        assertFails { store.addOrigins(Any(), node, 1, 0, a, emit) }
        assertFails { store.addOrigins(Any(), node, 2, 2, a, emit) }
        assertFails { store.addOrigins(Any(), node, 2, 0, a.copy(premise = m.union(origin(0), origin(1))), emit) }
        assertFails { store.addOrigins(Any(), node, 2, 0, a.copy(premise = StoreFixtures().manager.zero), emit) }
        f.cancellation.cancel()
        try { assertFails { store.addOrigins(rule, node, 3, 2, input(9), emit) } }
        finally { f.cancellation.activate() }
        store.addOrigins(rule, node, 3, 2, input(9), emit)
        assertEquals(listOf(origin(0), origin(9)), out)
    }
}
