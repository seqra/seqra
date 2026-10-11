package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.AddedFactStore
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.Link
import org.opentaint.dataflow.bidi.store.RequestKind
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
        val firstAction = assertIs<RequestAction.Climb>(ops.requestAction(requested, kind, existing.single().addedFact, existing.single().caller))
        assertSame(initialA, firstAction.premise)

        // No new added content is necessary: a new caller edge is itself event E2.
        assertSame(facts, addedStore.add(callerB, Layer.NORMAL, facts))
        assertEquals(listOf(requested to kind), requestStore.overlapping(added).toList())
        val secondAction = assertIs<RequestAction.Climb>(ops.requestAction(requested, kind, added, callerB))
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
            assertIs<RequestAction.Answer>(ops.requestAction(initial, kind, leaf, ref))
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
}
