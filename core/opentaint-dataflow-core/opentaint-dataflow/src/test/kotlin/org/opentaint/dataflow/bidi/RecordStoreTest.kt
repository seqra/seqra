package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.PersistentRecordStore
import org.opentaint.dataflow.bidi.store.Record
import org.opentaint.dataflow.bidi.store.RecordStore
import org.opentaint.dataflow.bidi.store.RunSummaryStore
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Native inputs and leaf lists remain separate from the positional indexes under test. */
class RecordStoreTest {
    private val fixture = StoreFixtures()
    private val m = fixture.manager
    private val ops = fixture.ops
    private val v = StoreValues(fixture)

    private data class Row(val record: Record, val leaves: List<Conclusion>)
    private fun row(key: InitialAp, leaves: List<Conclusion>, direction: Direction = Direction.FORWARD,
                    method: MethodKey = fixture.method): Row =
        Row(Record(method, direction, key, v.pack(*leaves.toTypedArray())), leaves)
    private fun related(a: List<Int>, b: List<Int>): Boolean =
        (a.size <= b.size && a.indices.all { a[it] == b[it] }) ||
            (b.size <= a.size && b.indices.all { b[it] == a[it] })
    private fun entryMatch(record: Record, a: Pattern): Boolean =
        applicable(record.premise.toPattern(), a) || inside(record.premise.toPattern(), a)
    private fun allAtEntry(store: RecordStore, method: MethodKey = fixture.method): List<Record> =
        listOf(v.x, v.y, STATIC, AccessPathBase.Zero).flatMap { base ->
            store.byEntry(method, fixture.pattern(base = base, tail = Tail.STAR, mark = MarkSlot.STAR)).toList()
        }
    private fun patterns(): List<Pattern> = with(v) {
        buildList {
            for (base in listOf(x, y, STATIC, AccessPathBase.Zero))
                for (path in listOf(p(), p(f), p(f, g), p(f, h), p(g), p(g, f), p(h)))
                    for (mark in listOf(mt, mu, MarkSlot.STAR)) for (tail in Tail.entries)
                        for (e in listOf(ExclusionSet.Empty, ex(g), ex(h)))
                            add(Pattern(PathFact(base, path, tail, mark), e))
        }
    }

    @Test
    fun `direct insertion accepts every native initial kind and preserves both orientations`(): Unit = with(v) {
        val store = PersistentRecordStore(m)
        val rows = buildList {
            add(row(m.zero, listOf(leaf(p(), mark = MarkSlot.Concrete(TaintMark.ZERO), base = AccessPathBase.Zero))))
            add(row(m.zero, listOf(leaf(p(g), base = y))))
            for (direction in Direction.entries) {
                for (tail in listOf(Tail.EXACT, Tail.ANY)) {
                    add(row(premise(p(f), tail), listOf(leaf(p(g), base = y)), direction))
                    add(row(premise(p(f), tail), listOf(leaf(p(), mark = MarkSlot.Concrete(TaintMark.ZERO), base = AccessPathBase.Zero)), direction))
                }
                add(row(premise(p(f), Tail.STAR, MarkSlot.STAR), listOf(flow(p(g), e = ex(h))), direction))
            }
            for (e in listOf(ExclusionSet.Empty, ex(h))) {
                add(row(premise(p(f), Tail.ANY_TAINT, e = e), listOf(leaf(p(g), base = y))))
                add(row(premise(p(f), Tail.ANY_TAINT, e = e), listOf(leaf(p(g), Tail.ANY_TAINT, mu, e = ex(h), base = y))))
            }
            add(row(premise(p(f)), listOf(leaf(p(g), Tail.ANY_TAINT, mu, e = ex(h), base = y))))
        }
        rows.forEach { store.add(it.record) }
        assertEquals(rows.map { it.record }.toSet(), allAtEntry(store).toSet())
        assertTrue(rows.all { it.record.conclusion.layer == Layer.NORMAL })
        for (item in rows) assertEquals(item.leaves.toSet(), decoded(item.record.conclusion))
    }

    @Test
    fun `direct insertion rejects nonnative shapes layers kinds and backward must or zero records`(): Unit = with(v) {
        val store = PersistentRecordStore(m)
        val exact = premise(p(f))
        val abstract = premise(p(f), Tail.STAR, MarkSlot.STAR)
        val invalid = buildList {
            for (direction in Direction.entries) {
                add(row(premise(p(f), Tail.STAR), listOf(leaf(p(g))), direction))
                add(row(premise(p(f), Tail.ANY, MarkSlot.STAR), listOf(flow(p(g))), direction))
                add(row(premise(p(f), Tail.STAR, MarkSlot.STAR, ex(h)), listOf(flow(p(g))), direction))
                add(row(exact, listOf(leaf(p(g), layer = Layer.DEMAND)), direction))
                add(row(abstract, listOf(flow(p(g), Layer.DEMAND)), direction))
                add(row(exact, listOf(flow(p(g))), direction))
                add(row(abstract, listOf(leaf(p(g))), direction))
                add(row(abstract, listOf(leaf(p(), mark = MarkSlot.Concrete(TaintMark.ZERO), base = AccessPathBase.Zero)), direction))
            }
            add(row(m.zero, listOf(leaf(p(g))), Direction.BACKWARD))
            add(row(premise(p(f), Tail.ANY_TAINT, e = ex(h)), listOf(leaf(p(g))), Direction.BACKWARD))
            add(row(exact, listOf(leaf(p(g), Tail.ANY_TAINT)), Direction.BACKWARD))
            add(row(exact, listOf(leaf(p(g)), leaf(p(h), Tail.ANY_TAINT, mu)), Direction.BACKWARD))
        }
        for (item in invalid) {
            assertFails(item.record.premise.toPattern().toString()) { store.add(item.record) }
            assertTrue(allAtEntry(store).isEmpty())
            for (base in listOf(x, y, AccessPathBase.Zero))
                assertTrue(store.byExit(fixture.method, fixture.pattern(base = base)).none())
        }
    }

    @Test
    fun `entry candidates followed by full annotated matching equal an independent scan`(): Unit = with(v) {
        val store = PersistentRecordStore(m)
        val other = fixture.key("other-context")
        val otherEntry = fixture.key(entry = 1)
        val rows = buildList {
            for (method in listOf(fixture.method, other, otherEntry)) for (direction in Direction.entries) {
                for (path in listOf(p(), p(f), p(f, g), p(f, h), p(g), p(h))) {
                    add(row(premise(path), listOf(leaf(p(g), base = y)), direction, method))
                    add(row(premise(path, Tail.ANY, mu), listOf(leaf(p(h), base = y)), direction, method))
                    add(row(premise(path, Tail.STAR, MarkSlot.STAR), listOf(flow(p(g), e = ex(h))), direction, method))
                    if (direction == Direction.FORWARD)
                        add(row(premise(path, Tail.ANY_TAINT, e = ex(h)), listOf(leaf(p(g), Tail.ANY_TAINT, base = y)), direction, method))
                }
            }
            add(row(m.zero, listOf(leaf(p(g), base = y))))
        }
        rows.forEach { store.add(it.record) }
        for (method in listOf(fixture.method, other, otherEntry, fixture.key("missing"))) for (query in patterns()) {
            val candidates = store.byEntry(method, query).toList()
            val expectedCandidates = rows.filter { it.record.method == method && it.record.premise.base == query.fact.base &&
                related(it.record.premise.toPattern().fact.path, query.fact.path) }.map { it.record }.toSet()
            assertEquals(expectedCandidates, candidates.toSet(), "$method $query")
            assertEquals(candidates.size, candidates.toSet().size)
            val expected = rows.filter { it.record.method == method && entryMatch(it.record, query) }.map { it.record }.toSet()
            assertEquals(expected, candidates.filter { entryMatch(it, query) }.toSet(), "$method $query")
        }
    }

    @Test
    fun `exit candidates and transient leaf matching equal a complete native leaf scan`(): Unit = with(v) {
        val store = PersistentRecordStore(m)
        val other = fixture.key("other-context", 1)
        val rows = listOf(
            row(premise(p(f)), listOf(leaf(p(g), base = y), leaf(p(g, f), mark = mu, base = y), leaf(p(h), base = y))),
            row(premise(p(h), Tail.ANY_TAINT, mu, ex(g)), listOf(leaf(p(), Tail.ANY_TAINT, e = ex(h), base = y), leaf(p(g), Tail.ANY_TAINT, e = ex(h), base = y))),
            row(premise(p(f), Tail.STAR, MarkSlot.STAR), listOf(flow(p(g), e = ex(h)), flow(p(g, f), e = ex(h)), flow(p(h), e = ex(h)))),
            row(premise(p(g), Tail.ANY), listOf(leaf(p(g), base = y)), Direction.BACKWARD),
            row(premise(p(f)), listOf(leaf(p(g), base = y)), method = other),
        )
        rows.forEach { store.add(it.record) }
        for (method in listOf(fixture.method, other, fixture.key("missing"))) for (query in patterns()) {
            val candidates = store.byExit(method, query).toList()
            val expectedCandidates = rows.filter { it.record.method == method && it.leaves.any { leaf ->
                leaf.fact.base == query.fact.base && related(leaf.fact.path, query.fact.path)
            } }.map { it.record }.toSet()
            assertEquals(expectedCandidates, candidates.toSet(), "$method $query")
            assertEquals(candidates.size, candidates.toSet().size)
            val expected = rows.filter { it.record.method == method }.flatMap { item -> item.leaves.mapNotNull { leaf ->
                reverseRecordLeaf(item.record.direction, item.record.premise.toPattern(), leaf)?.takeIf {
                    applicable(it.premise, query) || inside(it.premise, query)
                }?.let { item.record to it }
            } }.toSet()
            val actual = candidates.flatMap { record -> record.reversedAt(query).filter {
                applicable(it.premise, query) || inside(it.premise, query)
            }.map { record to it }.toList() }.toSet()
            assertEquals(expected, actual, "$method $query")
        }
    }

    @Test
    fun `a read-only run view keeps reads and rejects both write APIs including nested views`(): Unit = with(v) {
        val store = PersistentRecordStore(m)
        val item = row(premise(p(f)), listOf(leaf(p(g), base = y)))
        store.add(item.record)
        val view = store.view()
        assertSame(view, view.view())
        assertEquals(listOf(item.record), view.byEntry(fixture.method, fixture.pattern(path = p(f))).toList())
        assertEquals(listOf(item.record), view.byExit(fixture.method, fixture.pattern(base = y, path = p(g))).toList())
        assertFails { view.add(row(premise(p(h)), listOf(leaf(p(h)))).record) }
        assertFails { view.persist(Direction.FORWARD, emptySequence()) }
        assertEquals(listOf(item.record), allAtEntry(store))
    }

    @Test
    fun `R1 persists only raw normal singleton groups and skips backward zero and every publication`(): Unit = with(v) {
        for (direction in Direction.entries) {
            val store = PersistentRecordStore(m)
            val raw = RunSummaryStore(m)
            val keys = buildList<PremiseKey> {
                add(m.zero)
                add(premise(p(f)))
                add(premise(p(g), Tail.ANY, mu))
                add(premise(p(h), Tail.STAR, MarkSlot.STAR))
                add(nd())
                if (direction == Direction.FORWARD) add(premise(p(f, h), Tail.ANY_TAINT, mu, ex(g)))
            }
            val expected = LinkedHashSet<Pair<InitialAp, Conclusion>>()
            for (key in keys) {
                val normal = if (key.isFlow) flow(p(g), e = ex(h)) else leaf(p(g), base = y)
                val demand = if (key.isFlow) flow(p(h), Layer.DEMAND) else leaf(p(h), layer = Layer.DEMAND, base = y)
                raw.add(key, pack(normal))
                raw.add(key, pack(demand))
                raw.addDemand(key, pack(if (key.isFlow) flow(p(f), e = ex(g)) else leaf(p(f), base = y)))
                if (key is InitialAp && !(direction == Direction.BACKWARD && key.isZero)) expected += key to normal
            }
            val publicationOnly = premise(p(g, h))
            raw.addDemand(publicationOnly, pack(leaf(p(h), base = y)))
            store.persist(direction, sequenceOf(fixture.method to raw))
            val records = allAtEntry(store)
            assertEquals(expected, records.flatMap { record -> decoded(record.conclusion).map { record.premise to it } }.toSet())
            assertTrue(records.all { it.direction == direction && it.method == fixture.method && it.conclusion.layer == Layer.NORMAL })
            assertFalse(records.any { it.premise === publicationOnly })
            store.persist(direction, sequenceOf(fixture.method to raw))
            assertEquals(records.toSet(), allAtEntry(store).toSet())
        }
    }

    @Test
    fun `persist rejects invalid normal singleton native kinds before indexing them`(): Unit = with(v) {
        val bad = listOf(
            Triple(Direction.FORWARD, premise(p(f), Tail.STAR), leaf(p(g))),
            Triple(Direction.FORWARD, premise(p(f), Tail.ANY, MarkSlot.STAR), flow(p(g))),
            Triple(Direction.FORWARD, premise(p(f), Tail.STAR, MarkSlot.STAR, ex(h)), flow(p(g))),
            Triple(Direction.BACKWARD, premise(p(f), Tail.ANY_TAINT, e = ex(h)), leaf(p(g))),
            Triple(Direction.BACKWARD, premise(p(f)), leaf(p(g), Tail.ANY_TAINT)),
        )
        for ((direction, key, leaf) in bad) {
            val store = PersistentRecordStore(m)
            val raw = RunSummaryStore(m)
            raw.add(key, pack(leaf))
            assertFails { store.persist(direction, sequenceOf(fixture.method to raw)) }
            assertTrue(allAtEntry(store).isEmpty())
        }
    }

    @Test
    fun `raw must records retain native exclusions while each F76 view converts its sides independently`(): Unit = with(v) {
        val store = PersistentRecordStore(m)
        val raw = RunSummaryStore(m)
        val exact = premise(p(f), mark = mu)
        val mustToMust = premise(p(f), Tail.ANY_TAINT, mu, ex(g))
        val mustToExact = premise(p(h), Tail.ANY_TAINT, mu, ex(g))
        val targetMust = leaf(p(g), Tail.ANY_TAINT, mt, e = ex(h), base = y)
        val targetExact = leaf(p(g), mark = mt, base = y)
        val cases = listOf(
            Triple(exact, targetMust, RecordLeafView(Pattern(targetMust.fact.copy(tail = Tail.STAR), ex(h)), leaf(p(f), mark = mu))),
            Triple(mustToMust, targetMust, RecordLeafView(Pattern(targetMust.fact.copy(tail = Tail.STAR), ex(h)), leaf(p(f), Tail.ANY, mu, Layer.DEMAND))),
            Triple(mustToExact, targetExact, RecordLeafView(Pattern(targetExact.fact, ExclusionSet.Empty), leaf(p(h), Tail.ANY, mu, Layer.DEMAND))),
        )
        for ((key, native, _) in cases) {
            val facts = pack(native)
            raw.add(key, facts)
            assertNull(ops.demandPart(key, facts, Direction.FORWARD))
            assertNull(ops.demandPart(key, facts, Direction.BACKWARD))
            raw.addDemand(key, pack(leaf(p(h), layer = Layer.DEMAND, base = y)))
        }
        store.persist(Direction.FORWARD, sequenceOf(fixture.method to raw))
        val records = store.byExit(fixture.method, fixture.pattern(base = y, path = p(g))).toList()
        assertEquals(3, records.size)
        for ((key, native, expectedView) in cases) {
            val record = records.single { it.premise === key }
            assertEquals(setOf(native), decoded(record.conclusion))
            assertEquals(key.exclusion, record.premise.exclusion)
            assertEquals(listOf(expectedView), record.reversedAt(fixture.pattern(base = y, path = p(g))).toList())
            assertFalse(expectedView.result.fact.tail == Tail.ANY_TAINT)
            assertEquals(ExclusionSet.Empty, expectedView.result.exclusion)
            if (native.fact.tail == Tail.ANY_TAINT) {
                val admitted = fixture.pattern(base = y, path = p(g, f, h), mark = mt)
                val blocked = fixture.pattern(base = y, path = p(g, h, f), mark = mt)
                assertTrue(applicable(expectedView.premise, admitted) || inside(expectedView.premise, admitted))
                assertFalse(applicable(expectedView.premise, blocked) || inside(expectedView.premise, blocked))
            }
        }
        assertEquals(3, allAtEntry(store).size)
    }

    @Test
    fun `persistent T1 unions index only new deltas and deduplicate every multi-leaf candidate`(): Unit = with(v) {
        for (forwardOrder in listOf(false, true)) {
            val store = PersistentRecordStore(m)
            val key = premise(p(f))
            val left = listOf(leaf(p(g), base = y), leaf(p(g, f), base = y))
            val right = listOf(leaf(p(g), base = y), leaf(p(g, f), mark = mu, base = y), leaf(p(h), base = y))
            val order = if (forwardOrder) listOf(left, right) else listOf(right, left)
            for (leaves in order) {
                val raw = RunSummaryStore(m)
                raw.add(key, pack(*leaves.toTypedArray()))
                store.persist(Direction.FORWARD, sequenceOf(fixture.method to raw))
            }
            val records = allAtEntry(store)
            val expectedDeltas = setOf(order[0].toSet(), order[1].toSet() - order[0].toSet())
            assertEquals(expectedDeltas, records.map { decoded(it.conclusion) }.toSet())
            assertEquals(2, records.size)
            assertEquals((left + right).toSet(), records.flatMap { decoded(it.conclusion) }.toSet())
            for (path in listOf(p(), p(g), p(g, f), p(h))) {
                val query = fixture.pattern(base = y, path = path)
                val found = store.byExit(fixture.method, query).toList()
                assertEquals(records.filter { record -> decoded(record.conclusion).any { related(it.fact.path, path) } }.toSet(), found.toSet())
                assertEquals(found.size, found.toSet().size)
            }
            val repeated = RunSummaryStore(m)
            repeated.add(key, pack(*(left + right).toTypedArray()))
            store.persist(Direction.FORWARD, sequenceOf(fixture.method to repeated))
            records.forEach(store::add)
            assertEquals(records.toSet(), allAtEntry(store).toSet())
        }
    }

    @Test
    fun `persistent T2 indexes the whole annotation delta in either insertion order`(): Unit = with(v) {
        data class Case(val key: InitialAp, val left: List<Conclusion>, val right: List<Conclusion>, val merged: Set<Conclusion>)
        val flowKey = premise(p(f), Tail.STAR, MarkSlot.STAR)
        val exact = leaf(p(h), mark = mu)
        val cases = listOf(
            Case(flowKey, listOf(flow(p(), e = ex(f, h)), flow(p(g), e = ex(f, h))),
                listOf(flow(p(), e = ex(g, h)), flow(p(g), e = ex(g, h))), setOf(flow(p(), e = ex(h)), flow(p(g), e = ex(h)))),
            Case(flowKey, listOf(flow(p(g), mx = marks(t.id))), listOf(flow(p(g), mx = marks(u.id))), setOf(flow(p(g)))),
            Case(premise(p(f)), listOf(leaf(p(), Tail.ANY_TAINT, e = ex(f)), exact),
                listOf(leaf(p(), Tail.ANY_TAINT, e = ex(g)), exact), setOf(leaf(p(), Tail.ANY_TAINT), exact)),
        )
        for (case in cases) for (order in listOf(listOf(case.left, case.right), listOf(case.right, case.left))) {
            val store = PersistentRecordStore(m)
            for (leaves in order) {
                val raw = RunSummaryStore(m)
                raw.add(case.key, pack(*leaves.toTypedArray()))
                store.persist(Direction.FORWARD, sequenceOf(fixture.method to raw))
            }
            val records = allAtEntry(store)
            assertEquals(setOf(order[0].toSet(), case.merged), records.map { decoded(it.conclusion) }.toSet())
            assertEquals(2, records.size)
            for (path in case.merged.map { it.fact.path }.distinct()) {
                val candidates = store.byExit(fixture.method, fixture.pattern(path = path)).toList()
                assertTrue(candidates.any { decoded(it.conclusion) == case.merged })
                assertEquals(candidates.size, candidates.toSet().size)
            }
            for (leaves in listOf(case.left, case.right)) {
                val raw = RunSummaryStore(m)
                raw.add(case.key, pack(*leaves.toTypedArray()))
                store.persist(Direction.FORWARD, sequenceOf(fixture.method to raw))
            }
            assertEquals(records.toSet(), allAtEntry(store).toSet())
        }
    }

    @Test
    fun `persistent unions keep method context entry point direction and premise independent`(): Unit = with(v) {
        val store = PersistentRecordStore(m)
        val key = premise(p(f))
        val first = leaf(p(g), base = y)
        val methods = listOf(fixture.method, fixture.key("other-context"), fixture.key(entry = 1))
        for (method in methods) for (direction in Direction.entries) {
            val raw = RunSummaryStore(m)
            raw.add(key, pack(first))
            store.persist(direction, sequenceOf(method to raw))
        }
        val otherKey = premise(p(h))
        val later = leaf(p(h), mark = mu, base = y)
        val raw = RunSummaryStore(m)
        raw.add(key, pack(later))
        raw.add(otherKey, pack(first))
        store.persist(Direction.FORWARD, sequenceOf(fixture.method to raw))
        for (method in methods) {
            val records = allAtEntry(store, method)
            assertTrue(records.all { it.method == method })
            val expected = buildSet {
                for (direction in Direction.entries) add(Triple(direction, key, setOf(first)))
                if (method == fixture.method) {
                    add(Triple(Direction.FORWARD, key, setOf(later)))
                    add(Triple(Direction.FORWARD, otherKey, setOf(first)))
                }
            }
            assertEquals(expected, records.map { Triple(it.direction, it.premise, decoded(it.conclusion)) }.toSet())
        }
    }

    @Test
    fun `32768-accessor record indexing queries views and persistence remain iterative after cache loss`(): Unit = with(v) {
        val path = List(32768) { f }
        val key = premise(path)
        val target = leaf(path, Tail.ANY_TAINT, mu, e = ex(g), base = y)
        val raw = RunSummaryStore(m)
        raw.add(key, pack(target))
        val store = PersistentRecordStore(m)
        store.persist(Direction.FORWARD, sequenceOf(fixture.method to raw))
        val record = allAtEntry(store).single()
        assertEquals(setOf(target), decoded(record.conclusion))
        assertEquals(listOf(record), store.byEntry(fixture.method, fixture.pattern(path = path)).toList())
        assertEquals(listOf(record), store.byExit(fixture.method, fixture.pattern(base = y, path = path)).toList())
        assertEquals(listOf(record), store.byExit(fixture.method, fixture.pattern(base = y, path = path.take(16384))).toList())
        assertTrue(store.byEntry(fixture.method, fixture.pattern(path = path.dropLast(1) + g)).none())
        val view = RecordLeafView(Pattern(target.fact.copy(tail = Tail.STAR), ex(g)), leaf(path))
        assertEquals(listOf(view), record.reversedAt(fixture.pattern(base = y, path = path)).toList())
        m.softRefs.cleanup()
        m.softRefs.enable()
        val rebuilt = RunSummaryStore(m)
        rebuilt.add(key, pack(target))
        store.persist(Direction.FORWARD, sequenceOf(fixture.method to rebuilt))
        assertEquals(listOf(record), allAtEntry(store))
        val next = leaf(path.dropLast(1) + g, mark = mu, base = y)
        rebuilt.add(key, pack(next))
        store.persist(Direction.FORWARD, sequenceOf(fixture.method to rebuilt))
        assertEquals(setOf(target, next), allAtEntry(store).flatMap { decoded(it.conclusion) }.toSet())
        assertEquals(2, allAtEntry(store).size)
    }

    @Test
    fun `cancelled writes candidate queries and native view reads stop before new work`(): Unit = with(v) {
        val store = PersistentRecordStore(m)
        val item = row(premise(p(f)), listOf(leaf(p(g), base = y)))
        store.add(item.record)
        val next = row(premise(p(h)), listOf(leaf(p(h), base = y)))
        val raw = RunSummaryStore(m)
        raw.add(next.record.premise, next.record.conclusion)
        fixture.cancellation.cancel()
        try {
            assertFailsWith<Cancellation.Cancelled> { store.add(next.record) }
            assertFailsWith<Cancellation.Cancelled> { store.persist(Direction.FORWARD, sequenceOf(fixture.method to raw)) }
            assertFailsWith<Cancellation.Cancelled> { store.byEntry(fixture.method, fixture.pattern()).toList() }
            assertFailsWith<Cancellation.Cancelled> { store.byExit(fixture.method, fixture.pattern(base = y)).toList() }
            assertFailsWith<Cancellation.Cancelled> { item.record.reversedAt(fixture.pattern(base = y)).toList() }
        } finally { fixture.cancellation.activate() }
        assertEquals(listOf(item.record), allAtEntry(store))
    }

    @Test
    fun `cancellation between candidates or views is checked before the next yielded item`(): Unit = with(v) {
        val store = PersistentRecordStore(m)
        val first = row(premise(p(f)), listOf(leaf(p(g), base = y), leaf(p(h), mark = mu, base = y)))
        val second = row(premise(p(g)), listOf(leaf(p(g), mark = mu, base = y)))
        store.add(first.record)
        store.add(second.record)
        val sequences = listOf(
            store.byEntry(fixture.method, fixture.pattern()),
            store.byExit(fixture.method, fixture.pattern(base = y)),
            first.record.reversedAt(fixture.pattern(base = y)),
        )
        for (sequence in sequences) {
            val iterator = sequence.iterator()
            assertTrue(iterator.hasNext())
            iterator.next()
            fixture.cancellation.cancel()
            try { assertFailsWith<Cancellation.Cancelled> { iterator.next() } }
            finally { fixture.cancellation.activate() }
        }
    }

    @Test
    fun `cancellation during the summary source iteration stops the next barrier insertion`(): Unit = with(v) {
        val store = PersistentRecordStore(m)
        val first = RunSummaryStore(m)
        val second = RunSummaryStore(m)
        val firstKey = premise(p(f))
        val secondKey = premise(p(h))
        val firstLeaf = leaf(p(g), base = y)
        first.add(firstKey, pack(firstLeaf))
        second.add(secondKey, pack(leaf(p(h), base = y)))
        val summaries = sequence {
            yield(fixture.method to first)
            fixture.cancellation.cancel()
            yield(fixture.method to second)
        }
        try { assertFailsWith<Cancellation.Cancelled> { store.persist(Direction.FORWARD, summaries) } }
        finally { fixture.cancellation.activate() }
        val records = allAtEntry(store)
        assertEquals(1, records.size)
        assertSame(firstKey, records.single().premise)
        assertEquals(setOf(firstLeaf), decoded(records.single().conclusion))
    }

    @Test
    fun `zero source and REACH records retain native zero sides without emission or demand selection`(): Unit = with(v) {
        val source = row(m.zero, listOf(leaf(p(g), mark = mu, base = y)))
        val reach = row(premise(p(f)), listOf(leaf(p(), mark = MarkSlot.Concrete(TaintMark.ZERO), base = AccessPathBase.Zero)), Direction.BACKWARD)
        val store = PersistentRecordStore(m)
        for (item in listOf(source, reach)) {
            store.add(item.record)
            assertNull(ops.demandPart(item.record.premise, item.record.conclusion, item.record.direction))
        }
        assertEquals(listOf(source.record), store.byEntry(fixture.method, m.zero.toPattern()).toList())
        assertEquals(listOf(reach.record), store.byExit(fixture.method, m.zero.toPattern()).toList())
        val sourceView = RecordLeafView(fixture.pattern(base = y, path = p(g), mark = mu),
            leaf(p(), mark = MarkSlot.Concrete(TaintMark.ZERO), base = AccessPathBase.Zero))
        assertEquals(listOf(sourceView), source.record.reversedAt(fixture.pattern(base = y, path = p(g))).toList())
        val reachView = RecordLeafView(m.zero.toPattern(), leaf(p(f)))
        assertEquals(listOf(reachView), reach.record.reversedAt(m.zero.toPattern()).toList())
        assertEquals(2, allAtEntry(store).size)
    }
}
