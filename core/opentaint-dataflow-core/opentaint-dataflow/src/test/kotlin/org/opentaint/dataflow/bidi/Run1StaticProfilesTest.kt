package org.opentaint.dataflow.bidi

import org.junit.jupiter.api.Test
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.ir.api.common.*
import org.opentaint.ir.api.common.cfg.*
import kotlin.test.*

class Run1StaticProfilesTest {
    private fun method(name: String): CommonMethod = object : CommonMethod {
        override val name = name
        override val parameters: List<CommonMethodParameter> = emptyList()
        override val returnType = object : CommonTypeName { override val typeName = "void" }
        override fun flowGraph(): ControlFlowGraph<CommonInst> = error("Metadata tests do not build IR graphs")
        override fun toString(): String = name
    }

    private data class Raw(
        val accesses: Set<List<Int>> = emptySet(),
        val abstractions: Set<List<Int>> = emptySet(),
        val interests: Set<StaticMarkInterest> = emptySet(),
        val cleaners: Set<Int> = emptySet(),
    ) {
        fun union(other: Raw): Raw = Raw(accesses + other.accesses, abstractions + other.abstractions,
            interests + other.interests, cleaners + other.cleaners)
    }

    private data class Declaration(val pattern: Pattern, val role: StaticMarkRole, val mark: Int)

    /** Expand only input/observed membership; no production grouping or SCC result builds the expected tuples. */
    private fun declarations(interests: Iterable<StaticMarkInterest>): Set<Declaration> = buildSet {
        for (interest in interests) {
            val cursor = interest.marks.cursor()
            while (!cursor.isComplete) assertTrue(cursor.step(1) { mark ->
                add(Declaration(interest.pattern.copy(fact = interest.pattern.fact.copy(mark = MarkSlot.Concrete(mark))),
                    interest.role, mark.id))
            } > 0)
        }
    }

    /** Plain repeated graph equations; independent of production SCCs and persistent union code. */
    private fun fixedPoint(methods: List<CommonMethod>, calls: Map<CommonMethod, Collection<CommonMethod>>,
                           local: Map<CommonMethod, Raw>): Map<CommonMethod, Raw> {
        var current = methods.associateWith { local[it] ?: Raw() }
        while (true) {
            val next = methods.associateWith { caller ->
                calls[caller].orEmpty().fold(local[caller] ?: Raw()) { result, callee -> result.union(current.getValue(callee)) }
            }
            if (next == current) return next
            current = next
        }
    }

    private fun cleaners(profile: Run1StaticProfile): Set<Int> {
        val result = LinkedHashSet<Int>()
        val cursor = profile.cleanerMarks.cursor()
        while (!cursor.isComplete) assertTrue(cursor.step(1) { result += it.id } > 0)
        return result
    }

    @Test
    fun `all four transitive parts match independent graph equations with cycles diamonds and duplicate calls`(): Unit {
        val f = StoreFixtures()
        val cls = f.manager.accessors.index(ClassStaticAccessor("Box"))
        val paths = List(7) { listOf(cls, f.field("field$it")) }
        val t = f.mark("T"); val u = f.mark("U")
        val methods = List(9) { method("m$it") }
        val calls = mapOf<CommonMethod, Collection<CommonMethod>>(
            methods[0] to listOf(methods[1], methods[2], methods[1]),
            methods[1] to listOf(methods[3], methods[4]), methods[2] to listOf(methods[4]),
            methods[3] to listOf(methods[1], methods[5]), methods[4] to listOf(methods[6]),
            methods[5] to listOf(methods[6]), methods[6] to listOf(methods[6]),
        )
        val raw = methods.mapIndexed { index, m ->
            val path = paths[index % paths.size]
            val mark = if (index % 2 == 0) t else u
            val interest = StaticMarkInterest(f.pattern(STATIC, path + f.field("deep$index"),
                if (index % 2 == 0) Tail.EXACT else Tail.ANY, MarkSlot.Concrete(mark),
                ExclusionSet.of(f.field("excluded$index"))), StaticMarkRole.entries[index % 3], RequestMarkSet.of(mark))
            m to Raw(setOf(path), setOf(if (index % 2 == 0) path else listOf(cls)), setOf(interest), setOf(mark.id))
        }.toMap()
        val local = raw.mapValues { (_, value) -> Run1StaticLocalProfile(value.accesses, value.abstractions,
            value.interests, RequestMarkSet.fromIds(value.cleaners.toIntArray())) }
        val expected = fixedPoint(methods, calls, raw)
        val actual = Run1StaticProfiles.build(f.manager, calls, local)
        for (m in methods) {
            val e = expected.getValue(m); val a = actual[m]
            assertEquals(e.accesses, a.accesses, m.name)
            assertEquals(e.abstractions, a.abstractions, m.name)
            assertEquals(declarations(e.interests), declarations(a.markInterests), m.name)
            assertEquals(e.cleaners, cleaners(a), m.name)
            for (path in paths) assertEquals(path in e.abstractions, a.containsAbstraction(path))
            for (slot in paths + listOf(listOf(cls), emptyList()))
                assertEquals(declarations(e.interests.filter { it.slot == slot }), declarations(a.markInterestsAt(slot).asIterable()))
        }
        assertSame(actual[methods[1]], actual[methods[3]], "An SCC has one physical profile")
        assertEquals(10L, actual.statistics.callEdges, "Duplicate call rows are not additional graph edges")
        assertEquals(expected.values.sumOf { it.accesses.size }.toLong(), actual.statistics.transitiveAccesses)
        assertEquals(expected.values.sumOf { declarations(it.interests).size }.toLong(), actual.statistics.transitiveMarkInterests)
        assertEquals(expected.values.sumOf { it.cleaners.size }.toLong(), actual.statistics.transitiveCleanerMarks)
        assertTrue(actual.statistics.cancellationCheckpoints > actual.statistics.methods)
        assertNull(actual.statistics.persistentNodeBytes, "Logical cardinality is not measured retained bytes")
    }

    @Test
    fun `sparse closure shares unchanged set roots without method by position or mark arrays`(): Unit {
        val f = StoreFixtures()
        val cls = f.manager.accessors.index(ClassStaticAccessor("Sparse"))
        val methods = List(1024) { method("sparse$it") }
        val paths = List(128) { listOf(cls, f.field("slot$it")) }
        val t = f.mark("T")
        val interests = paths.map { StaticMarkInterest(f.pattern(STATIC, it, Tail.ANY, MarkSlot.Concrete(t)),
            StaticMarkRole.SINK, RequestMarkSet.of(t)) }
        val calls = methods.dropLast(1).mapIndexed { index, m -> m to listOf(methods[index + 1]) }.toMap()
        val actual = Run1StaticProfiles.build(f.manager, calls, mapOf(methods.last() to
            Run1StaticLocalProfile(paths, paths, interests, RequestMarkSet.of(t))))
        val last = actual[methods.last()]
        for (m in methods) {
            val p = actual[m]
            assertSame(last.accesses, p.accesses)
            assertSame(last.abstractions, p.abstractions)
            assertSame(last.directory(), p.directory())
            assertSame(last.cleanerIds, p.cleanerIds)
        }
        assertEquals(128L * methods.size, actual.statistics.transitiveAbstractions)
        assertEquals(1, actual.statistics.abstractionSetRoots)
        assertEquals(1, actual.statistics.markDirectoryRoots)
        assertEquals(1, actual.statistics.cleanerSetRoots)
        assertEquals(128, actual.statistics.uniquePositions)
        assertEquals(0L, actual.statistics.ownedMarkPrimitiveCells, "Persistent memberships own no primitive mark arrays")
        val sharedMarks = last.markInterests.first().marks.ids
        for (interest in last.markInterests) assertSame(sharedMarks, interest.marks.ids)
        // Profile lookup against one incoming supplier needs only its two structural prefixes.
        val supplier = paths[17] + f.field("deep")
        assertFalse(actual[methods.first()].containsAbstraction(supplier.take(1)))
        assertTrue(actual[methods.first()].containsAbstraction(supplier.take(2)))
        assertEquals(setOf(interests[17]), actual[methods.first()].markInterestsAt(supplier.take(2)).toSet())
    }

    @Test
    fun `many declared marks share one full query while tail exclusion role and deep geometry stay separate`(): Unit {
        val f = StoreFixtures()
        val methods = List(4) { method("group$it") }
        val cls = f.manager.accessors.index(ClassStaticAccessor("Grouped"))
        val field = f.field("f"); val deepField = f.field("g")
        val slot = listOf(cls, field)
        val marks = List(32) { f.mark("T$it") }
        fun interest(mark: TaintMark, role: StaticMarkRole = StaticMarkRole.SINK,
                     tail: Tail = Tail.ANY, exclusion: ExclusionSet = ExclusionSet.Empty,
                     path: List<Int> = slot, members: RequestMarkSet = RequestMarkSet.of(mark)) =
            StaticMarkInterest(f.pattern(STATIC, path, tail, MarkSlot.Concrete(mark), exclusion), role, members)
        val left = marks.take(16).map { interest(it) } + listOf(
            interest(marks[0], role = StaticMarkRole.SOURCE),
            interest(marks[0], tail = Tail.EXACT),
            interest(marks[0], exclusion = ExclusionSet.of(deepField)),
            interest(marks[0], path = slot + deepField),
        )
        val right = marks.drop(12).map { interest(it) } + listOf(
            interest(marks[24], members = RequestMarkSet.fromIds(marks.drop(24).map { it.id }.toIntArray())),
            interest(marks[12]),
        )
        val calls = mapOf<CommonMethod, Collection<CommonMethod>>(
            methods[0] to listOf(methods[1]), methods[1] to listOf(methods[2], methods[3]),
            methods[2] to listOf(methods[2]),
        )
        val raw = mapOf(methods[2] to Raw(interests = left.toSet()), methods[3] to Raw(interests = right.toSet()))
        val expected = fixedPoint(methods, calls, raw)
        val actual = Run1StaticProfiles.build(f.manager, calls, mapOf(
            methods[2] to Run1StaticLocalProfile(marks = left), methods[3] to Run1StaticLocalProfile(marks = right)))
        for (m in methods) assertEquals(declarations(expected.getValue(m).interests), declarations(actual[m].markInterests))
        val root = actual[methods[1]]
        assertEquals(5, root.markInterests.size)
        assertEquals(36, root.declaredMarkCount)
        val query = root.markInterests.single { it.role == StaticMarkRole.SINK && it.pattern.fact.path == slot &&
            it.pattern.fact.tail == Tail.ANY && it.pattern.exclusion == ExclusionSet.Empty }
        assertEquals(MarkSlot.STAR, query.pattern.fact.mark)
        assertEquals(marks.map { it.id }.toSet(), query.marks.ids)
        assertEquals(32, query.marks.size, "One cursor joins the full shared mark selection")
        assertSame(root.directory(), actual[methods[0]].directory(), "An unchanged caller reuses the directory")
        assertSame(query.marks, actual[methods[0]].markInterests.single { it.shape == query.shape }.marks)
        assertEquals(40L, actual.statistics.directMarkInterests)
        assertEquals(6L, actual.statistics.directMarkGroups)
        assertEquals(112L, actual.statistics.transitiveMarkInterests)
        assertEquals(16L, actual.statistics.transitiveMarkGroups)
        assertEquals(36, actual.statistics.uniqueMarkInterests)
        assertEquals(5, actual.statistics.uniqueMarkQueries)
        assertEquals(0L, actual.statistics.ownedMarkPrimitiveCells)
    }

    @Test
    fun `declared exact deep tail role and exclusion remain distinct and never manufacture predicate support`(): Unit {
        val f = StoreFixtures()
        val m = method("roles")
        val cls = f.manager.accessors.index(ClassStaticAccessor("Box"))
        val slot = listOf(cls, f.field("f")); val deep = slot + f.field("g")
        val t = f.mark("T"); val u = f.mark("U")
        val values = listOf(
            StaticMarkInterest(f.pattern(STATIC, slot, Tail.EXACT, MarkSlot.Concrete(t)), StaticMarkRole.SINK, RequestMarkSet.of(t)),
            StaticMarkInterest(f.pattern(STATIC, slot, Tail.ANY, MarkSlot.Concrete(t)), StaticMarkRole.SOURCE, RequestMarkSet.of(t)),
            StaticMarkInterest(f.pattern(STATIC, deep, Tail.ANY, MarkSlot.Concrete(u), ExclusionSet.of(f.field("h"))),
                StaticMarkRole.INPUT_GATE, RequestMarkSet.of(u)),
        )
        val actual = Run1StaticProfiles.build(f.manager, emptyMap(), mapOf(m to Run1StaticLocalProfile(marks = values)))[m]
        assertTrue(actual.abstractions.isEmpty(), "Mark interest does not silently add structural roles")
        assertEquals(values.toSet(), actual.markInterestsAt(slot).toSet())
        assertEquals(values.map { it.pattern }.toSet(), actual.markInterests.map { it.pattern }.toSet())
        assertTrue(actual.markInterestsAt(listOf(cls)).none())
        assertTrue(actual.markInterestsAt(deep).none())
    }

    @Test
    fun `exact transport coverage requires a whole chain interest and root only cleaner coverage`(): Unit {
        val f = StoreFixtures()
        val m = method("coverage")
        val cls = f.manager.accessors.index(ClassStaticAccessor("Box"))
        val field = f.field("f"); val slot = listOf(cls, field); val deep = slot + f.field("g")
        val t = f.mark("T"); val u = f.mark("U")
        fun initial(path: List<Int>, tail: Tail = Tail.STAR, e: ExclusionSet = ExclusionSet.Empty,
                    mark: MarkSlot = MarkSlot.STAR, base: org.opentaint.dataflow.ap.ifds.AccessPathBase = STATIC) =
            f.manager.initial(f.pattern(base, path, tail, mark, e))
        fun profile(pattern: Pattern): Run1StaticProfile = Run1StaticProfiles.build(f.manager, emptyMap(),
            mapOf(m to Run1StaticLocalProfile(marks = listOf(StaticMarkInterest(pattern,
                StaticMarkRole.SINK, RequestMarkSet.of(t))), cleanerMarks = RequestMarkSet.of(u))))[m]
        val exact = profile(f.pattern(STATIC, slot, Tail.EXACT, MarkSlot.Concrete(t)))
        assertFalse(exact.covers(initial(slot), t))
        assertFalse(profile(f.pattern(STATIC, deep, Tail.ANY, MarkSlot.Concrete(t))).covers(initial(slot), t))
        assertFalse(profile(f.pattern(STATIC, slot, Tail.ANY, MarkSlot.Concrete(t), ExclusionSet.of(field)))
            .covers(initial(slot), t))
        val whole = profile(f.pattern(STATIC, slot, Tail.ANY, MarkSlot.Concrete(t)))
        assertTrue(whole.covers(initial(slot), t))
        assertFalse(whole.covers(initial(listOf(cls)), t))
        assertFalse(whole.covers(initial(emptyList()), t))
        assertFalse(whole.covers(initial(slot), u))
        assertTrue(whole.covers(initial(emptyList()), u))
        assertFalse(whole.covers(initial(listOf(cls)), u))
        assertFalse(whole.covers(initial(slot, Tail.ANY), t))
        assertFalse(whole.covers(initial(slot, e = ExclusionSet.of(field)), t))
        assertFalse(whole.covers(initial(slot, mark = MarkSlot.Concrete(t)), t))
        assertFalse(whole.covers(initial(slot, base = org.opentaint.dataflow.ap.ifds.AccessPathBase.Argument(0)), t))
    }

    @Test
    fun `input snapshots survive mutation and method identity is not its display name`(): Unit {
        val f = StoreFixtures()
        val first = method("sameName"); val second = method("sameName"); val caller = method("caller")
        val cls = f.manager.accessors.index(ClassStaticAccessor("Box"))
        val field = f.field("f"); val other = f.field("other"); val t = f.mark("T")
        val path = arrayListOf(cls, field)
        val deep = arrayListOf(cls, field, other)
        val mark = StaticMarkInterest(f.pattern(STATIC, deep, Tail.EXACT, MarkSlot.Concrete(t)),
            StaticMarkRole.SOURCE, RequestMarkSet.of(t))
        val accesses = arrayListOf<List<Int>>(path)
        val declarations = arrayListOf(mark)
        val local = Run1StaticLocalProfile(accesses, accesses, declarations)
        path[1] = other; deep.clear(); accesses.clear(); declarations.clear()
        val callees = arrayListOf(first)
        val graph = linkedMapOf<CommonMethod, Collection<CommonMethod>>(caller to callees)
        val locals = linkedMapOf(first to local, second to Run1StaticLocalProfile.EMPTY)
        val profiles = Run1StaticProfiles.build(f.manager, graph, locals)
        callees.clear(); graph.clear(); locals.clear()
        assertEquals(setOf(listOf(cls, field)), profiles[caller].abstractions)
        assertEquals(listOf(cls, field, other), profiles[caller].markInterests.single().pattern.fact.path)
        assertTrue(profiles[second].abstractions.isEmpty())
        assertTrue(profiles.containsTarget(caller, first))
        assertFalse(profiles.containsTarget(caller, second))
        assertFailsWith<IllegalStateException> { profiles[method("unknown")] }
    }

    @Test
    fun `empty and target only methods have explicit empty profiles without a missing catalog fallback`(): Unit {
        val f = StoreFixtures()
        val caller = method("caller"); val leaf = method("targetOnly")
        val profiles = Run1StaticProfiles.build(f.manager, mapOf(caller to listOf(leaf)), emptyMap())
        assertEquals(setOf(caller, leaf), profiles.methods)
        for (m in profiles.methods) {
            assertTrue(profiles[m].accesses.isEmpty())
            assertTrue(profiles[m].abstractions.isEmpty())
            assertTrue(profiles[m].markInterests.isEmpty())
            assertEquals(emptySet<Int>(), cleaners(profiles[m]))
        }
        assertEquals(0L, profiles.statistics.maximumProfileSize)
        assertEquals(0, Run1StaticProfiles.build(f.manager, emptyMap(), emptyMap()).statistics.methods)
    }

    @Test
    fun `cancellation rejects collection and active reads without exposing a partial profile`(): Unit {
        val cancellation = Cancellation()
        val f = StoreFixtures(cancellation)
        val a = method("a"); val b = method("b"); val t = f.mark("T")
        cancellation.cancel()
        assertFailsWith<Cancellation.Cancelled> { Run1StaticProfiles.build(f.manager, emptyMap(), emptyMap()) }
        cancellation.activate()
        val targets = object : AbstractCollection<CommonMethod>() {
            override val size: Int get() = 1
            override fun iterator(): Iterator<CommonMethod> = object : Iterator<CommonMethod> {
                private var pending = true
                override fun hasNext(): Boolean = pending
                override fun next(): CommonMethod { pending = false; cancellation.cancel(); return b }
            }
        }
        assertFailsWith<Cancellation.Cancelled> { Run1StaticProfiles.build(f.manager, mapOf(a to targets), emptyMap()) }
        cancellation.activate()
        val profiles = Run1StaticProfiles.build(f.manager, mapOf(a to listOf(b)),
            mapOf(b to Run1StaticLocalProfile(cleanerMarks = RequestMarkSet.of(t))))
        val cursor = profiles[a].cleanerMarks.cursor()
        cancellation.cancel()
        assertFailsWith<Cancellation.Cancelled> { profiles[a].containsAbstraction(emptyList()) }
        assertFailsWith<Cancellation.Cancelled> { cursor.step(1) { error("No cancelled callback") } }
        cancellation.activate()
        assertEquals(setOf(t.id), cleaners(profiles[a]))
    }

    @Test
    fun `32768 method closure stays iterative and shares a sole deepest structural need`(): Unit {
        val f = StoreFixtures()
        val methods = List(32768) { method("deep$it") }
        val cls = f.manager.accessors.index(ClassStaticAccessor("Deep"))
        val path = listOf(cls, f.field("only"))
        val calls = methods.dropLast(1).mapIndexed { index, m -> m to listOf(methods[index + 1]) }.toMap()
        val profiles = Run1StaticProfiles.build(f.manager, calls,
            mapOf(methods.last() to Run1StaticLocalProfile(abstractions = listOf(path))))
        assertEquals(setOf(path), profiles[methods.first()].abstractions)
        assertSame(profiles[methods.last()].abstractions, profiles[methods.first()].abstractions)
        assertEquals(32768, profiles.statistics.components)
        assertEquals(32768L, profiles.statistics.transitiveAbstractions)
        assertEquals(1, profiles.statistics.abstractionSetRoots)
        assertEquals(1L, profiles.statistics.maximumProfileSize)
    }
}
