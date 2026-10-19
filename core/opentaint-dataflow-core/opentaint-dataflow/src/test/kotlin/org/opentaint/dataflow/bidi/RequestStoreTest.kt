package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.fixtures.testApManager

import org.opentaint.dataflow.bidi.ap.ReferenceRequestAction as RequestAction

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.AddedFactStore
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.Link
import org.opentaint.dataflow.bidi.store.N1Statistics
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.dataflow.bidi.store.RequestStore
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RequestStoreTest {
    private val fixture = StoreFixtures()
    private val m = fixture.manager
    private val ops = fixture.ops
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val cls = m.accessors.index(ClassStaticAccessor("Box"))
    private val f = fixture.field("f")
    private val g = fixture.field("g")
    private val h = fixture.field("fresh")
    private val t = fixture.mark("T")
    private val u = fixture.mark("U")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private fun p(vararg a: Int) = a.toList()
    private fun ex(vararg a: Int) = ExclusionSet.of(a)
    private fun marks(vararg ids: Int) = MarkSet(ids)
    private fun pattern(path: List<Int>, tail: Tail, mark: MarkSlot = mt,
                        exclusion: ExclusionSet = ExclusionSet.Empty, base: AccessPathBase = x) =
        fixture.pattern(base, path, tail, mark, exclusion)
    private fun policy(path: List<Int> = p(), base: AccessPathBase = x) =
        m.initial(pattern(path, Tail.STAR, MarkSlot.STAR, base = base))
    private fun position(path: List<Int>) = RequestKind.Position(m.path(path)!!)
    private data class StatedRequest(val premise: InitialAp, val kind: RequestKind, val query: Pattern) {
        val key: Pair<InitialAp, RequestKind> get() = premise to kind
    }

    @Test
    fun `standing request index matches full scans after annotated overlap for all added shapes`() {
        val store = RequestStore()
        val requests = buildList {
            for (base in listOf(x, y, STATIC)) {
                val initial = policy(base = base)
                for (mark in listOf(t, u)) add(StatedRequest(initial, RequestKind.Mark(mark), initial.toPattern()))
            }
            for (path in listOf(p(cls), p(cls, f), p(cls, g))) {
                val initial = policy(path, STATIC)
                for (mark in listOf(t, u)) add(StatedRequest(initial, RequestKind.Mark(mark), initial.toPattern()))
            }
            for (initial in listOf(policy(base = STATIC), policy(p(cls), STATIC))) {
                for (path in listOf(p(cls, f), p(cls, g))) add(StatedRequest(initial, position(path),
                    pattern(path, Tail.STAR, MarkSlot.STAR, base = STATIC)))
            }
        }
        for (request in requests) assertTrue(store.add(request.premise, request.kind))
        val paths = listOf(p(), p(f), p(g), p(f, g), p(g, f), p(cls), p(cls, f), p(cls, g),
            p(cls, f, g), p(cls, f, h), p(cls, g, f), p(cls, h), p(cls, h, h))
        for (base in listOf(x, y, STATIC)) for (path in paths) {
            val shapes = buildList {
                for (mark in listOf(mt, mu)) {
                    add(pattern(path, Tail.EXACT, mark, base = base))
                    add(pattern(path, Tail.ANY, mark, base = base))
                    for (e in listOf(ExclusionSet.Empty, ex(f), ex(g))) add(pattern(path, Tail.ANY_TAINT, mark, e, base))
                }
                for (mx in listOf(MarkSet.EMPTY, marks(t.id), marks(u.id))) {
                    add(pattern(path, Tail.ANY, MarkSlot.Star(mx), base = base))
                    for (e in listOf(ExclusionSet.Empty, ex(f), ex(g))) add(pattern(path, Tail.STAR, MarkSlot.Star(mx), e, base))
                }
            }
            for (added in shapes) {
                val actual = store.overlapping(added).toList()
                assertEquals(requests.filter { overlap(added, it.query) }.map { it.key }.toSet(), actual.toSet(), added.toString())
                assertEquals(actual.toSet().size, actual.size, "exact request pair occurs once: $added")
            }
        }
        assertTrue(store.overlapping(m.zero.toPattern()).none())
    }

    @Test
    fun `deduplication keys retain premise identity and mark or canonical position kind`() {
        val store = RequestStore()
        val root = policy(base = STATIC)
        val atClass = policy(p(cls), STATIC)
        val markT = RequestKind.Mark(t)
        assertTrue(store.add(root, markT))
        assertFalse(store.add(m.initial(root.toPattern()), RequestKind.Mark(t)))
        assertTrue(store.add(root, RequestKind.Mark(u)))
        val path = p(cls, f)
        assertTrue(store.add(root, position(path)))
        assertFalse(store.add(root, position(path.toList())))
        assertTrue(store.add(atClass, position(path)))
        assertTrue(store.add(atClass, markT))
        assertFalse(store.add(atClass, RequestKind.Mark(t)))
        val all = store.overlapping(pattern(p(), Tail.ANY, MarkSlot.STAR, base = STATIC)).toList()
        assertEquals(5, all.size)
        assertEquals(5, all.toSet().size)
        assertTrue(root to markT in all)
        assertTrue(atClass to markT in all)
    }

    @Test
    fun `position requests are indexed by their static position rather than the policy premise`() {
        val store = RequestStore()
        val root = policy(base = STATIC)
        val fieldF = position(p(cls, f))
        val fieldG = position(p(cls, g))
        val atClass = policy(p(cls), STATIC)
        val classRequest = position(p(cls))
        val mark = RequestKind.Mark(t)
        for ((initial, kind) in listOf(root to fieldF, root to fieldG, root to classRequest,
            root to mark, atClass to fieldF)) assertTrue(store.add(initial, kind))
        assertEquals(setOf(root to mark), store.overlapping(pattern(p(), Tail.EXACT, base = STATIC)).toSet())
        assertEquals(setOf(root to mark, root to classRequest),
            store.overlapping(pattern(p(cls), Tail.EXACT, base = STATIC)).toSet())
        assertEquals(setOf(root to mark, root to classRequest, root to fieldF, atClass to fieldF),
            store.overlapping(pattern(p(cls, f, h), Tail.EXACT, base = STATIC)).toSet())
        assertEquals(setOf(root to mark, root to classRequest),
            store.overlapping(pattern(p(cls), Tail.ANY_TAINT, mt, ex(f, g), STATIC)).toSet())
        assertEquals(setOf(root to mark, root to classRequest),
            store.overlapping(pattern(p(cls, h), Tail.EXACT, base = STATIC)).toSet())
        assertTrue(store.overlapping(pattern(p(cls, f), Tail.ANY, base = x)).none())
    }

    @Test
    fun `request matching ignores marks but honors first-accessor exclusions and exact prefixes`() {
        val store = RequestStore()
        val root = policy(base = STATIC)
        val atF = policy(p(cls, f), STATIC)
        val atG = policy(p(cls, g), STATIC)
        val atClass = policy(p(cls), STATIC)
        val rt = RequestKind.Mark(t)
        val ru = RequestKind.Mark(u)
        for ((initial, kind) in listOf(atF to rt, atF to ru, atG to rt, root to position(p(cls, f)))) store.add(initial, kind)
        assertTrue(store.overlapping(pattern(p(cls), Tail.EXACT, mt, base = STATIC)).none())
        val blocked = pattern(p(cls), Tail.ANY_TAINT, mt, ex(f), STATIC)
        assertEquals(setOf(atG to rt), store.overlapping(blocked).toSet())
        val abstract = pattern(p(cls), Tail.STAR, MarkSlot.Star(marks(t.id)), ex(g), STATIC)
        assertEquals(setOf(atF to rt, atF to ru, root to position(p(cls, f))), store.overlapping(abstract).toSet())
        // The same excluded accessor later in the continuation does not block the first admitted step.
        assertTrue(store.add(atClass, rt))
        assertEquals(setOf(atF to rt, atF to ru, atClass to rt, root to position(p(cls, f))),
            store.overlapping(pattern(p(cls, f, g), Tail.ANY_TAINT, mt, ex(f), STATIC)).toSet())
    }

    @Test
    fun `invalid request policy headers reject before adding a request`() {
        val invalid = listOf(
            m.initial(pattern(p(), Tail.EXACT)), m.initial(pattern(p(), Tail.ANY)),
            m.initial(pattern(p(), Tail.ANY, MarkSlot.STAR)), m.initial(pattern(p(), Tail.ANY_TAINT)),
            m.initial(pattern(p(), Tail.STAR)), m.initial(pattern(p(), Tail.STAR, MarkSlot.STAR, ex(f))), m.zero,
        )
        for (initial in invalid) for (kind in listOf(RequestKind.Mark(t), position(p(cls, f)))) {
            val store = RequestStore()
            assertFails { store.add(initial, kind) }
            assertTrue(store.overlapping(pattern(p(), Tail.ANY, MarkSlot.STAR)).none())
            assertTrue(store.overlapping(pattern(p(), Tail.ANY, MarkSlot.STAR, base = STATIC)).none())
        }
    }

    @Test
    fun `a request reads existing links and a second caller of the same added fact gets a new standing match`() {
        val addedStore = AddedFactStore(m)
        val requestStore = RequestStore()
        val initialA = policy(base = y)
        val initialB = policy(p(g), y)
        val callerA = CallerRef(fixture.key("A"), initialA, Layer.NORMAL, fixture.inst(10))
        val callerB = CallerRef(fixture.key("B", 1), initialB, Layer.DEMAND, fixture.inst(11))
        val added = pattern(p(), Tail.STAR, MarkSlot.STAR)
        val facts = m.factsOf(added.fact, added.exclusion, Layer.NORMAL)
        assertNotNull(addedStore.add(callerA, Layer.NORMAL, facts))
        val requested = policy()
        val kind = RequestKind.Mark(t)
        assertTrue(requestStore.add(requested, kind))
        val existing = addedStore.overlapping(x, requested.path).toList()
        assertEquals(listOf(Link(added, Layer.NORMAL, callerA)), existing)
        val firstAction = assertIs<RequestAction.Climb>(ops.referenceRequestAction(requested, kind, existing.single().addedFact, existing.single().caller))
        assertSame(initialA, firstAction.premise)

        // No new added content is necessary: a new caller edge is itself event E2.
        assertSame(facts, addedStore.add(callerB, Layer.NORMAL, facts))
        assertEquals(listOf(requested to kind), requestStore.overlapping(added).toList())
        val secondAction = assertIs<RequestAction.Climb>(ops.referenceRequestAction(requested, kind, added, callerB))
        assertSame(initialB, secondAction.premise)
        assertSame(kind, secondAction.request)
        assertEquals(setOf(Link(added, Layer.NORMAL, callerA), Link(added, Layer.NORMAL, callerB)), addedStore.links().toSet())
        assertNull(addedStore.add(callerB.copy(), Layer.NORMAL, facts))
        assertFalse(requestStore.add(requested, RequestKind.Mark(t)))
        assertEquals(listOf(requested to kind), requestStore.overlapping(added).toList())
    }

    @Test
    fun `a request arriving before links still matches every new leaf and caller`() {
        val requests = RequestStore()
        val added = AddedFactStore(m)
        val initial = policy()
        val kind = RequestKind.Mark(t)
        assertTrue(requests.add(initial, kind))
        val raw = listOf(pattern(p(f), Tail.EXACT), pattern(p(f, g), Tail.ANY_TAINT, mt, ex(h)))
        val refs = listOf(
            CallerRef(fixture.key("A"), m.initial(pattern(p(), Tail.EXACT)), Layer.NORMAL, fixture.inst(10)),
            CallerRef(fixture.key("B"), m.initial(pattern(p(), Tail.EXACT, mu)), Layer.NORMAL, fixture.inst(11)),
        )
        val actions = LinkedHashSet<Pair<CallerRef, Pattern>>()
        for (ref in refs) for (leaf in raw) {
            assertNotNull(added.add(ref, Layer.NORMAL, m.factsOf(leaf.fact, leaf.exclusion, Layer.NORMAL)))
            val matches = requests.overlapping(leaf).toList()
            assertEquals(listOf(initial to kind), matches)
            assertIs<RequestAction.Answer>(ops.referenceRequestAction(initial, kind, leaf, ref))
            actions += ref to leaf
        }
        assertEquals(refs.flatMap { ref -> raw.map { ref to it } }.toSet(), actions)
        assertEquals(actions.map { (ref, leaf) -> Link(leaf, Layer.NORMAL, ref) }.toSet(), added.links().toSet())
        assertEquals(actions.size, added.overlapping(x, null).count())
    }

    @Test
    fun `optional cancellation rejects insertion and queries without leaving a partial request`() {
        val store = RequestStore(fixture.cancellation)
        val initial = policy()
        val existing = RequestKind.Mark(t)
        val rejected = RequestKind.Mark(u)
        val added = pattern(p(f), Tail.EXACT)
        assertTrue(store.add(initial, existing))
        fixture.cancellation.cancel()
        assertFailsWith<Cancellation.Cancelled> { store.add(initial, rejected) }
        assertFailsWith<Cancellation.Cancelled> { store.overlapping(added).toList() }

        fixture.cancellation.activate()
        assertEquals(listOf(initial to existing), store.overlapping(added).toList())
        assertTrue(store.add(initial, rejected))
        assertFalse(store.add(initial, rejected))
        assertEquals(setOf(initial to existing, initial to rejected), store.overlapping(added).toSet())
    }

    @Test
    fun `selective visitor preserves mixed mark and position encounter order in one bucket`(): Unit {
        val store = RequestStore()
        val q = p(cls, f)
        val root = policy(base = STATIC)
        val atClass = policy(p(cls), STATIC)
        val atField = policy(q, STATIC)
        val v = fixture.mark("V")
        val requested = listOf(root to position(q), atField to RequestKind.Mark(u),
            atClass to position(q), atField to RequestKind.Mark(t), atField to position(q),
            atField to RequestKind.Mark(v))
        for ((initial, kind) in requested) assertTrue(store.add(initial, kind))
        val concrete = pattern(q, Tail.EXACT, mt, base = STATIC)
        assertEquals(requested, store.overlapping(concrete).toList(), "The raw view does not filter marks")
        val selected = ArrayList<Pair<InitialAp, RequestKind>>()
        store.visitActionCandidates(concrete) { initial, kind -> selected += initial to kind }
        assertEquals(listOf(requested[0], requested[2], requested[3], requested[4]), selected)
        selected.clear()
        val abstract = pattern(q, Tail.STAR, MarkSlot.Star(marks(t.id, u.id)), base = STATIC)
        store.visitActionCandidates(abstract) { initial, kind -> selected += initial to kind }
        assertEquals(listOf(requested[0], requested[2], requested[4], requested[5]), selected)
    }

    @Test
    fun `query horizon excludes callback insertions in existing and new later buckets`(): Unit {
        val store = RequestStore()
        val root = policy()
        val atF = policy(p(f))
        val deep = policy(p(f, g))
        val foreign = testApManager(Cancellation())
        val other = foreign.initial(x, foreign.path(p(f)), Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
        val original: List<Pair<InitialAp, RequestKind>> = listOf(root to RequestKind.Mark(t), atF to RequestKind.Mark(t))
        original.forEach { (initial, kind) -> store.add(initial, kind) }
        val added = pattern(p(), Tail.STAR, MarkSlot.STAR)
        val rawAtEntry = store.overlapping(added)
        val visited = ArrayList<Pair<InitialAp, RequestKind>>()
        var inserted = false
        store.visitActionCandidates(added) { initial, kind ->
            visited += initial to kind
            if (!inserted) {
                inserted = true
                assertTrue(store.add(atF, RequestKind.Mark(u)))
                assertTrue(store.add(other, RequestKind.Mark(t)))
                assertTrue(store.add(deep, RequestKind.Mark(t)))
            }
        }
        assertEquals(original, visited)
        assertEquals(original, rawAtEntry.toList(), "Raw sequences also capture their call-entry horizon")
        val all = listOf(original[0], original[1], atF to RequestKind.Mark(u), other to RequestKind.Mark(t),
            deep to RequestKind.Mark(t))
        assertEquals(all, store.overlapping(added).toList())
        visited.clear()
        store.visitActionCandidates(added) { initial, kind -> visited += initial to kind }
        assertEquals(all, visited)
    }

    @Test
    fun `sparse membership promotion accepts signed and widely separated mark IDs`(): Unit {
        val store = RequestStore()
        val initial = policy()
        val ids = listOf(Int.MIN_VALUE, Int.MAX_VALUE, -1, -1_000_000_000) + (1..257).map { it * 7919 }
        val requested: List<Pair<InitialAp, RequestKind>> = ids.map { initial to RequestKind.Mark(TaintMark(it)) }
        for ((i, kind) in requested) assertTrue(store.add(i, kind))
        for ((i, kind) in requested) assertFalse(store.add(i, kind))
        assertEquals(requested, store.overlapping(pattern(p(), Tail.STAR, MarkSlot.STAR)).toList())
        for (id in ids) {
            val selected = ArrayList<Pair<InitialAp, RequestKind>>()
            store.visitActionCandidates(pattern(p(), Tail.EXACT, MarkSlot.Concrete(TaintMark(id)))) { i, kind -> selected += i to kind }
            assertEquals(listOf<Pair<InitialAp, RequestKind>>(initial to RequestKind.Mark(TaintMark(id))), selected, "mark=$id")
        }
        val excluded = ids.filterIndexed { index, _ -> index % 3 == 0 }.toSet()
        val selected = ArrayList<Pair<InitialAp, RequestKind>>()
        store.visitActionCandidates(pattern(p(), Tail.STAR, MarkSlot.Star(MarkSet(excluded.toIntArray())))) { i, kind -> selected += i to kind }
        assertEquals(requested.filter { (it.second as RequestKind.Mark).mark.id !in excluded }, selected)
    }

    @Test
    fun `local origin handles preserve manager ID collisions and grouped position origins`(): Unit {
        val store = RequestStore()
        val origins = List(33) { testApManager(Cancellation()).initial(STATIC, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR) }
        assertEquals(1, origins.map { it.id }.toSet().size)
        val kind = position(p(cls, f))
        for (initial in origins) {
            assertTrue(store.add(initial, RequestKind.Mark(t)))
            assertTrue(store.add(initial, kind))
        }
        for (initial in origins) {
            assertFalse(store.add(initial, RequestKind.Mark(t)))
            assertFalse(store.add(initial, position(p(cls, f))))
        }
        val expected = origins.map { it to RequestKind.Mark(t) } + origins.map { it to kind }
        val added = pattern(p(cls, f), Tail.EXACT, base = STATIC)
        assertEquals(expected, store.overlapping(added).toList())
        val visited = ArrayList<Pair<InitialAp, RequestKind>>()
        store.visitActionCandidates(added) { initial, request -> visited += initial to request }
        assertEquals(expected, visited)
    }

    @Test
    fun `deep single-row index is iterative and preserves the complete request chain`(): Unit {
        val statistics = N1Statistics()
        val store = RequestStore(fixture.cancellation, statistics)
        val path = List(32768) { f }
        val initial = policy(path)
        val kind = RequestKind.Mark(t)
        assertTrue(store.add(initial, kind))
        val added = pattern(path, Tail.EXACT)
        assertEquals(listOf(initial to kind), store.overlapping(added).toList())
        val selected = ArrayList<Pair<InitialAp, RequestKind>>()
        store.visitActionCandidates(added) { i, request -> selected += i to request }
        assertEquals(listOf<Pair<InitialAp, RequestKind>>(initial to kind), selected)
        assertEquals(32769L, statistics.snapshot().liveIndexNodes)
        assertEquals(1L, statistics.snapshot().liveIndexBuckets)
        store.release()
        assertEquals(0L, statistics.snapshot().liveIndexNodes)
    }

    @Test
    fun `visitor checkpoints between callbacks without changing standing membership`(): Unit {
        val store = RequestStore(fixture.cancellation)
        val initial = policy()
        val requested: List<Pair<InitialAp, RequestKind>> = listOf(initial to RequestKind.Mark(t), initial to RequestKind.Mark(u))
        requested.forEach { (i, kind) -> store.add(i, kind) }
        val added = pattern(p(), Tail.STAR, MarkSlot.STAR)
        val selected = ArrayList<Pair<InitialAp, RequestKind>>()
        assertFailsWith<Cancellation.Cancelled> {
            store.visitActionCandidates(added) { i, kind ->
                selected += i to kind
                fixture.cancellation.cancel()
            }
        }
        assertEquals(listOf(requested[0]), selected)
        fixture.cancellation.activate()
        assertEquals(requested, store.overlapping(added).toList())
        store.release()
        store.release()
        assertFails { store.add(initial, RequestKind.Mark(t)) }
        assertFails { store.overlapping(added) }
        assertFails { store.visitActionCandidates(added) { _, _ -> error("A released store cannot call back") } }
    }

    @Test
    fun `optional census distinguishes live storage final row density and cumulative attempts`(): Unit {
        val statistics = N1Statistics()
        val store = RequestStore(statistics = statistics)
        val other = RequestStore(statistics = statistics)
        val root = policy(base = STATIC)
        val atClass = policy(p(cls), STATIC)
        store.add(root, RequestKind.Mark(t)); store.add(root, RequestKind.Mark(u))
        store.add(root, position(p(cls, f))); store.add(atClass, position(p(cls, f)))
        store.add(atClass, RequestKind.Mark(t))
        assertFalse(store.add(root, RequestKind.Mark(t)))
        other.add(policy(), RequestKind.Mark(u))
        val added = pattern(p(cls, f), Tail.EXACT, base = STATIC)
        assertEquals(5, store.overlapping(added).count())
        store.visitActionCandidates(added) { _, _ -> }
        val live = statistics.snapshot()
        assertEquals(2L, live.liveStores)
        assertEquals(6L, live.insertions)
        assertEquals(1L, live.duplicates)
        assertEquals(4L, live.liveMarkRequests)
        assertEquals(2L, live.livePositionRequests)
        assertEquals(3L, live.liveMarkRows)
        assertEquals(1L, live.livePositionRows)
        assertEquals(listOf(2L, 2L), live.liveRowSizeHistogram.take(2))
        assertEquals(5L, live.coarseCandidates)
        assertEquals(4L, live.selectedCandidates)
        assertEquals(4L, live.overlappingCandidates)
        assertTrue(live.livePrimitiveCells > 0)
        assertTrue(live.liveReferenceCells > 0)
        assertFailsWith<UnsupportedOperationException> { (live.liveRowSizeHistogram as MutableList<Long>).add(1) }
        store.release()
        val remaining = statistics.snapshot()
        assertEquals(1L, remaining.liveStores)
        assertEquals(1L, remaining.liveMarkRequests)
        assertEquals(listOf(1L, 0L), remaining.liveRowSizeHistogram.take(2))
        assertEquals(listOf(1L, 2L), remaining.releasedRowSizeHistogram.take(2))
        other.release()
        val final = statistics.snapshot()
        assertEquals(0L, final.liveStores)
        assertEquals(0L, final.livePrimitiveCells)
        assertEquals(0L, final.liveReferenceCells)
        assertEquals(0L, final.liveIndexNodes)
        assertEquals(0L, final.liveRowSizeHistogram.sum())
        assertEquals(listOf(2L, 2L), final.releasedRowSizeHistogram.take(2))
        assertEquals(live.allocatedPrimitiveCells, final.allocatedPrimitiveCells)
        assertEquals(6L, final.insertions)
    }
}
