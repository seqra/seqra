package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.*
import kotlin.test.*

class RequestSetStoreTest {
    private val x = AccessPathBase.Argument(0)
    private fun policy(f: StoreFixtures, path: List<Int> = emptyList(), base: AccessPathBase = x) =
        f.manager.initial(base, f.manager.path(path), Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)

    @Test
    fun `batched admission and captured mark table prefix survive later resize`(): Unit {
        val f = StoreFixtures()
        val store = RequestStore(f.cancellation)
        val initial = policy(f)
        val first = store.addMarks(initial, RequestMarkSet.fromIds(IntArray(12) { it }))
        assertEquals(12, first.fresh.size)
        val view = store.markMembers(first.row, first.horizon)
        store.addMarks(initial, RequestMarkSet.fromIds(IntArray(1024) { it }))
        assertTrue(view.containsId(0))
        assertTrue(view.containsId(11))
        assertFalse(view.containsId(12))
        val expected = (0 until 12).toSet()
        val actual = HashSet<Int>()
        val cursor = view.cursor()
        while (!cursor.isComplete) assertTrue(cursor.step(1) { actual += it.id } <= 1)
        assertEquals(expected, actual)
        assertEquals(1024, store.membershipHorizon)
        assertTrue(store.addMarks(initial, RequestMarkSet.fromIds(intArrayOf(0, 11, 1023))).fresh.isEmpty)
    }

    @Test
    fun `geometric row walk visits each mark row once and excludes positions and later child rows`(): Unit {
        val f = StoreFixtures()
        val store = RequestStore(f.cancellation)
        val a = f.field("a")
        val b = f.field("b")
        val c = f.field("c")
        val t = f.mark("T")
        val admitted = listOf(emptyList(), listOf(a), listOf(a, b), listOf(c)).map { policy(f, it) }
        for (initial in admitted) store.addMarks(initial, RequestMarkSet.of(t))
        store.add(policy(f, base = STATIC), RequestKind.Position(checkNotNull(f.manager.path(listOf(a)))))
        val horizon = store.membershipHorizon
        val group = f.fact(path = listOf(a), tail = Tail.STAR, mark = MarkSlot.STAR)
        val cursor = store.markRowsNear(group, horizon)
        val found = ArrayList<InitialAp>()
        cursor.step(1) { _, initial -> found += initial }
        for (n in 0 until 64) store.addMarks(policy(f, listOf(a, f.field("late$n"))), RequestMarkSet.of(t))
        while (!cursor.isComplete) assertTrue(cursor.step(1) { _, initial -> found += initial } <= 1)
        assertEquals(admitted.take(3).toSet(), found.toSet())
        assertEquals(3, found.size)
    }

    @Test
    fun `captured raw group values and group count survive reentrant link growth`(): Unit {
        val f = StoreFixtures()
        val store = AddedFactStore(f.manager)
        val ref = CallerRef(f.key("caller"), f.manager.zero, Layer.DEMAND, f.inst(1))
        val a = f.field("a")
        val b = f.field("b")
        val t = f.mark("T")
        val u = f.mark("U")
        val old = assertNotNull(store.addUpdate(ref, Layer.NORMAL, f.fact(path = listOf(a), mark = MarkSlot.Concrete(t))))
        val view = store.beginSnapshot()
        val cursor = view.groupsNear(x, null)
        val found = ArrayList<AddedFactStore.Snapshot>()
        cursor.step(1) { found += it }
        val mixed = f.manager.mergeAddDelta(old.facts, f.fact(path = listOf(b), mark = MarkSlot.Concrete(u))).first
        store.addUpdate(ref, Layer.NORMAL, mixed)
        for (n in 0 until 64) store.addUpdate(ref.copy(caller = f.key("new$n")), Layer.NORMAL, f.fact(path = listOf(f.field("p$n"))))
        while (!cursor.isComplete) assertTrue(cursor.step(1) { found += it } <= 1)
        assertEquals(listOf(old.handle), found.map { it.handle })
        assertEquals(old.facts, found.single().facts)
        view.close()
        val current = store.beginSnapshot()
        val currentCursor = current.groupsNear(x, null)
        val snapshots = ArrayList<AddedFactStore.Snapshot>()
        while (!currentCursor.isComplete) currentCursor.step(1) { snapshots += it }
        assertEquals(65, snapshots.size)
        assertEquals(mixed, snapshots.single { it.handle == old.handle }.facts)
        current.close()
    }

    @Test
    fun `pending exact delta keeps the admitted must exclusion when merged with later must marks`(): Unit {
        val f = StoreFixtures()
        val m = f.manager
        val store = AddedFactStore(m)
        val ref = CallerRef(f.key("caller"), m.zero, Layer.NORMAL, f.inst(1))
        val a = f.field("a")
        val blocked = f.field("blocked")
        val t = f.mark("T")
        val u = f.mark("U")
        val e = ExclusionSet.of(blocked)
        val initial = assertNotNull(store.addUpdate(ref, Layer.NORMAL,
            f.fact(tail = Tail.ANY_TAINT, exclusion = e, mark = MarkSlot.Concrete(t))))
        val mixedRoot = m.taintAlg.node(m.taintLeaves(m.markSetOf(t), m.markSetOf(t)), intArrayOf(a),
            arrayOf(m.taintAlg.leafNode(m.taintLeaves(m.markSetOf(u), MarkSet.EMPTY))))
        val mixed = m.taintTree(x, Layer.NORMAL, mixedRoot, e)
        val exact = assertNotNull(store.addUpdate(ref, Layer.NORMAL, mixed))
        assertEquals(e, exact.group.exclusion)
        assertEquals(ExclusionSet.Empty, exact.delta.groupKey.exclusion)
        val anyU = f.fact(path = listOf(a), tail = Tail.ANY_TAINT, exclusion = e, mark = MarkSlot.Concrete(u))
        val pending = assertIs<TaintTree>(store.mergePending(initial.group, exact.delta, anyU))
        assertEquals(e, pending.exclusion)
        assertEquals(setOf(Tail.EXACT, Tail.ANY_TAINT), f.ops.leaves(pending).filter { it.fact.path == listOf(a) }.map { it.fact.tail }.toSet())
    }
}
