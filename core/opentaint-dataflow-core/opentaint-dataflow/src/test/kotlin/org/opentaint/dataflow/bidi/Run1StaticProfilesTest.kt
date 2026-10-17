package org.opentaint.dataflow.bidi

import org.junit.jupiter.api.Test
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.fixtures.FiniteProgramFixtures
import org.opentaint.dataflow.bidi.fixtures.SelectedScalarPlan
import org.opentaint.dataflow.bidi.interp.*
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
        override fun flowGraph(): ControlFlowGraph<CommonInst> = error("Metadata does not build IR")
    }
    private data class Need(val path: List<Int>, val tail: Tail, val mark: Int)
    private data class Raw(val fields: Set<List<Int>> = emptySet(), val needs: Set<Need> = emptySet(),
                           val cleaners: Set<Int> = emptySet()) {
        fun union(other: Raw) = Raw(fields + other.fields, needs + other.needs, cleaners + other.cleaners)
    }
    private fun members(rows: RequestRows): Map<Run1Slot, Set<Int>> = rows.mapValues { (_, marks) ->
        buildSet { marks.forEachId { add(it) } }
    }
    private fun members(base: FrozenRequestBase): Map<Run1Slot, Set<Int>> = buildMap {
        base.forEachStaticRow({}) { slot ->
            val ids = buildSet { val cursor = base.borrow(slot).cursor(); while (!cursor.isComplete) cursor.step(256) { add(it.id) } }
            put(slot, ids)
        }
    }

    @Test fun `mark-free prescan retains local readers and no inherited static request rows across cycles and diamonds`() {
        val f = StoreFixtures(); val c = f.manager.accessors.index(ClassStaticAccessor("C"))
        val fields = List(7) { listOf(c, f.field("f$it")) }
        val t = f.mark("T"); val u = f.mark("U")
        val methods = List(9) { method("m$it") }
        val points = methods.indices.map { f.manager.accessors.index(ClassStaticAccessor("Point$it")) }
        val calls = mapOf<CommonMethod, Collection<CommonMethod>>(
            methods[0] to listOf(methods[1], methods[2], methods[1]),
            methods[1] to listOf(methods[3], methods[4]), methods[2] to listOf(methods[4]),
            methods[3] to listOf(methods[1], methods[5]), methods[4] to listOf(methods[6]),
            methods[5] to listOf(methods[6]), methods[6] to listOf(methods[6]))
        val raw = methods.mapIndexed { index, m -> m to Raw(setOf(fields[index % fields.size]), setOf(
            Need(fields[index % fields.size] + f.field("deep$index"), Tail.EXACT, t.id),
            Need(listOf(points[index]), Tail.ANY, u.id)), if (index % 3 == 0) setOf(t.id) else emptySet()) }.toMap()
        var closure = raw
        while (true) {
            val next = methods.associateWith { m -> calls[m].orEmpty().fold(raw.getValue(m)) { a, b -> a.union(closure.getValue(b)) } }
            if (next == closure) break
            closure = next
        }
        val locals = raw.mapValues { (_, r) -> Run1StaticLocalProfile(r.fields, r.fields, r.needs.map { n ->
            StaticMarkInterest(f.pattern(STATIC, n.path, n.tail, MarkSlot.Concrete(TaintMark(n.mark))),
                StaticMarkRole.SINK, RequestMarkSet.of(TaintMark(n.mark)))
        }, RequestMarkSet.fromIds(r.cleaners.toIntArray())) }
        val profiles = Run1StaticProfiles.build(f.manager, calls, locals)
        for (m in methods) {
            assertSame(EmptyFrozenRequestBase, profiles[m].precomputedRequests)
            assertTrue(members(profiles[m].precomputedRequests).isEmpty())
            assertEquals(closure.getValue(m).needs.filter { it.path.size == 1 }.map { it.path[0] }.toSet(),
                profiles[m].classPointClasses, "Only structural class consumers close along actual resolved callees")
            assertEquals(fields.map { Run1Slot.StaticField(it[0], it[1]) }.toSet(), profiles[m].footprint.fieldUniverse)
            assertEquals(raw.getValue(m).fields.map { Run1Slot.StaticField(it[0], it[1]) }.toSet(),
                profiles.localReads(m), "Reader metadata stays local even inside a recursive component")
            assertTrue(profiles[m].markInterests.isEmpty(), "Full-query scratch must not remain runtime policy")
            assertTrue(profiles[m].directory().isEmpty())
        }
        assertSame(profiles[methods[1]], profiles[methods[3]])
        assertEquals(10L, profiles.statistics.callEdges)
        for ((caller, targets) in calls) for (callee in targets) {
            val parent = members(profiles[caller].precomputedRequests)
            for ((slot, marks) in members(profiles[callee].precomputedRequests))
                assertTrue(parent.getValue(slot).containsAll(marks), "There are no inherited static mark rows")
        }
    }

    @Test fun `deep call chain has no frozen mark payload and shares only the legal coordinate inventory`() {
        val f = StoreFixtures(); val c = f.manager.accessors.index(ClassStaticAccessor("C"))
        val x = f.field("x"); val y = f.field("y"); val t = f.mark("T")
        val ms = List(1024) { method("chain$it") }; val extra = method("unrelated")
        val graph = ms.dropLast(1).mapIndexed { i, m -> m to listOf(ms[i + 1]) }.toMap()
        val profiles = Run1StaticProfiles.build(f.manager, graph, ms.associateWith { Run1StaticLocalProfile.EMPTY } + mapOf(
            ms.last() to Run1StaticLocalProfile(abstractions = listOf(listOf(c, x)), cleanerMarks = RequestMarkSet.of(t)),
            extra to Run1StaticLocalProfile(abstractions = listOf(listOf(c, y)))))
        val last = profiles[ms.last()]
        for (m in ms) assertSame(last.precomputedRequests, profiles[m].precomputedRequests)
        val sx = Run1Slot.StaticField(c, x); val sy = Run1Slot.StaticField(c, y)
        assertTrue(last.precomputedRequests.isEmpty)
        val lazy = last.wideRequests(f.fact(STATIC, listOf(c, y), Tail.EXACT))
        assertTrue(lazy.isEmpty())
        assertFalse(last.precomputedRequests.hasRow(sy), "No static mark scope is installed as a request row")
        assertSame(last.footprint.fieldUniverse, profiles[extra].footprint.fieldUniverse)
        assertEquals(0, profiles.statistics.precomputedBaseRoots)
        assertEquals(0L, profiles.statistics.physicalPrecomputedRows)
        assertEquals(0L, profiles.statistics.uniqueBaseLogicalRows)
        assertEquals(0L, profiles.statistics.sparseFieldRows)
        assertEquals(0L, profiles.statistics.logicalMethodRequestRows)
        assertEquals(0L, profiles.statistics.logicalMethodRequestMembers)
        assertEquals(0L, profiles.statistics.ownedMarkPrimitiveCells)
    }

    @Test fun `local mark templates certify coordinates without retaining static mark requests or predicate evidence`() {
        val f = StoreFixtures(); val m = method("roles")
        val c = f.manager.accessors.index(ClassStaticAccessor("C")); val x = f.field("x"); val g = f.field("g")
        val t = f.mark("T"); val zero = TaintMark.ZERO
        val declarations = listOf(
            StaticMarkInterest(f.pattern(STATIC, listOf(c, x), Tail.EXACT, MarkSlot.Concrete(t)), StaticMarkRole.SINK, RequestMarkSet.of(t)),
            StaticMarkInterest(f.pattern(STATIC, listOf(c, x, g), Tail.ANY, MarkSlot.Concrete(t), ExclusionSet.of(g)), StaticMarkRole.SOURCE, RequestMarkSet.of(t)),
            StaticMarkInterest(f.pattern(STATIC, listOf(c, x), Tail.ANY, MarkSlot.Concrete(zero)), StaticMarkRole.INPUT_GATE, RequestMarkSet.of(zero)))
        val profile = Run1StaticProfiles.build(f.manager, emptyMap(), mapOf(m to Run1StaticLocalProfile(marks = declarations)))[m]
        val slot = Run1Slot.StaticField(c, x)
        assertTrue(profile.precomputedRequests.isEmpty)
        assertEquals(setOf(slot), profile.footprint.fieldUniverse)
        assertFalse(profile.covers(slot.initial(f.manager), t))
        assertFalse(profile.covers(f.manager.initial(STATIC, f.manager.path(listOf(c)), Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR), t))
        assertTrue(profile.markInterests.isEmpty())
    }

    @Test fun `exact class metadata creates no abstract class slot or field Cartesian product`() {
        val f = StoreFixtures(); val m = method("point")
        val c = f.manager.accessors.index(ClassStaticAccessor("C")); val t = f.mark("T")
        val interest = StaticMarkInterest(f.pattern(STATIC, listOf(c), Tail.EXACT, MarkSlot.Concrete(t)),
            StaticMarkRole.SINK, RequestMarkSet.of(t))
        val p = Run1StaticProfiles.build(f.manager, emptyMap(), mapOf(m to Run1StaticLocalProfile(marks = listOf(interest))))[m]
        assertTrue(p.precomputedRequests.isEmpty); assertTrue(p.footprint.fieldSlots.isEmpty())
        assertEquals(setOf(c), p.classPointClasses)
        assertTrue(p.classPointMarks(c).isEmpty)
    }

    @Test fun `declared static universe is shared without making fields owned and exact points inherit separately`() {
        val f = StoreFixtures(); val c = f.manager.accessors.index(ClassStaticAccessor("C"))
        val x = Run1Slot.StaticField(c,f.field("x")); val y = Run1Slot.StaticField(c,f.field("y"))
        val caller = method("caller"); val callee = method("callee")
        val profile = Run1StaticProfiles.build(f.manager,mapOf(caller to listOf(callee)),
            mapOf(callee to Run1StaticLocalProfile(classPoints=setOf(c))),
            declaredFieldUniverse=setOf(x,y))[caller]
        assertEquals(setOf(x,y),profile.footprint.fieldUniverse)
        assertTrue(profile.footprint.fieldSlots.isEmpty())
        assertFalse(profile.footprint.owns(c,x.field)); assertFalse(profile.footprint.owns(c,y.field))
        assertEquals(setOf(c),profile.classPointClasses)
        assertTrue(profile.precomputedRequests.isEmpty)
    }

    @Test fun `metadata snapshots and graph bindings remain stable after caller mutation`() {
        val f = StoreFixtures(); val a = method("same"); val b = method("same"); val caller = method("caller")
        val c = f.manager.accessors.index(ClassStaticAccessor("C")); val x = f.field("x"); val t = f.mark("T")
        val path = arrayListOf(c, x)
        val local = Run1StaticLocalProfile(abstractions = listOf(path), marks = listOf(StaticMarkInterest(
            f.pattern(STATIC, path, Tail.ANY, MarkSlot.Concrete(t)), StaticMarkRole.SINK, RequestMarkSet.of(t))))
        path.clear()
        val edges = arrayListOf(a)
        val profiles = Run1StaticProfiles.build(f.manager, mapOf(caller to edges), mapOf(a to local, b to Run1StaticLocalProfile.EMPTY))
        edges.clear()
        assertTrue(profiles[caller].precomputedRequests.isEmpty)
        assertEquals(setOf(Run1Slot.StaticField(c, x)), profiles[caller].footprint.fieldUniverse)
        assertTrue(profiles[b].precomputedRequests.isEmpty)
        assertTrue(profiles.containsTarget(caller, a)); assertFalse(profiles.containsTarget(caller, b))
        assertFailsWith<IllegalStateException> { profiles[method("unknown")] }
    }

    @Test fun `cancellation rejects construction and active footprint reads`() {
        val cancellation = Cancellation(); val f = StoreFixtures(cancellation); val m = method("cancel")
        cancellation.cancel()
        assertFailsWith<Cancellation.Cancelled> { Run1StaticProfiles.build(f.manager, emptyMap(), emptyMap()) }
        cancellation.activate()
        val c = f.manager.accessors.index(ClassStaticAccessor("C")); val x = f.field("x")
        val p = Run1StaticProfiles.build(f.manager, emptyMap(), mapOf(m to Run1StaticLocalProfile(abstractions = listOf(listOf(c, x)))))[m]
        val input = f.fact(STATIC, listOf(c, x))
        cancellation.cancel()
        assertFailsWith<Cancellation.Cancelled> { p.footprint.suppliedFields(input, cancellation::checkpoint) { _, _ -> true } }
    }

    @Test fun `32768 method closure is iterative and retains one shared deepest base`() {
        val f = StoreFixtures(); val ms = List(32768) { method("deep$it") }
        val c = f.manager.accessors.index(ClassStaticAccessor("C")); val x = f.field("x"); val t = f.mark("T")
        val graph = ms.dropLast(1).mapIndexed { i, m -> m to listOf(ms[i + 1]) }.toMap()
        val last = Run1StaticLocalProfile(abstractions = listOf(listOf(c, x)), cleanerMarks = RequestMarkSet.of(t))
        val ps = Run1StaticProfiles.build(f.manager, graph,
            ms.associateWith { Run1StaticLocalProfile.EMPTY } + (ms.last() to last))
        assertSame(ps[ms.last()].precomputedRequests, ps[ms.first()].precomputedRequests)
        assertEquals(32768, ps.statistics.components)
        assertEquals(0L, ps.statistics.transitiveAbstractions)
        assertTrue(ps.localReads(ms.first()).isEmpty())
        assertEquals(setOf(Run1Slot.StaticField(c, x)), ps.localReads(ms.last()))
    }

    @Test fun `local readers local mark templates legal coordinates and write effects stay independent`() {
        val f = StoreFixtures(); val m = method("separate")
        val c = f.manager.accessors.index(ClassStaticAccessor("C"))
        val read = Run1Slot.StaticField(c, f.field("read"))
        val marked = Run1Slot.StaticField(c, f.field("marked"))
        val written = Run1Slot.StaticField(c, f.field("written"))
        val t = f.mark("T"); val u = f.mark("U")
        val profiles = Run1StaticProfiles.build(f.manager, emptyMap(), mapOf(m to Run1StaticLocalProfile(
            accesses = listOf(read.pathData.toList(), marked.pathData.toList(), written.pathData.toList()),
            abstractions = listOf(read.pathData.toList()),
            marks = listOf(
                StaticMarkInterest(f.pattern(STATIC, marked.pathData.toList(), Tail.EXACT, MarkSlot.Concrete(t)),
                    StaticMarkRole.SOURCE, RequestMarkSet.of(t)),
                StaticMarkInterest(f.pattern(STATIC, listOf(c), Tail.STAR, MarkSlot.Concrete(u)),
                    StaticMarkRole.SINK, RequestMarkSet.of(u))), staticMayWrite = true)))
        val p = profiles[m]
        assertEquals(setOf(read), profiles.localReads(m))
        assertTrue(p.precomputedRequests.isEmpty)
        assertEquals(setOf(read, marked, written), p.footprint.suppliedFields(
            f.fact(STATIC, emptyList(), Tail.ANY, layer = Layer.DEMAND), {}) { _, _ -> true })
        assertTrue(p.staticMayWrite)
        assertEquals(setOf(read, marked, written), p.footprint.fieldUniverse)
        assertTrue(p.wideRequests(f.fact(STATIC, written.pathData.toList())).isEmpty())
        assertFalse(p.precomputedRequests.hasRow(written), "Actual static marks do not wait for a row")
    }

    @Test fun `least static write closure distinguishes read-only cycle reachable writer and missing metadata`() {
        val f = StoreFixtures()
        val a = method("a"); val b = method("b"); val w = method("writer"); val unknown = method("unknown")
        val clean = Run1StaticProfiles.build(f.manager, mapOf(a to listOf(b), b to listOf(a)),
            mapOf(a to Run1StaticLocalProfile.EMPTY, b to Run1StaticLocalProfile.EMPTY))
        assertFalse(clean[a].staticMayWrite); assertFalse(clean[b].staticMayWrite)
        val dirty = Run1StaticProfiles.build(f.manager,
            mapOf(a to listOf(b), b to listOf(a, w)), mapOf(a to Run1StaticLocalProfile.EMPTY,
                b to Run1StaticLocalProfile.EMPTY, w to Run1StaticLocalProfile(staticMayWrite = true)))
        assertTrue(dirty[a].staticMayWrite); assertTrue(dirty[b].staticMayWrite)
        val missing = Run1StaticProfiles.build(f.manager, mapOf(a to listOf(unknown)),
            mapOf(a to Run1StaticLocalProfile.EMPTY))
        assertTrue(missing[a].staticMayWrite); assertTrue(missing[unknown].staticMayWrite)
        assertTrue(missing.localReads(a).isEmpty()); assertTrue(missing[a].precomputedRequests.isEmpty)
    }
    @Test fun `alias endpoints retain legal mark and write metadata without requesting a field read`() {
        FiniteProgramFixtures().use { f ->
            val owner = f.method("alias-only metadata", listOf(emptyList()), 0).key()
            val cls = f.ap.accessors.index(ClassStaticAccessor("AliasOwner"))
            val source = listOf(cls, f.field("source"))
            val target = listOf(cls, f.field("target"))
            val t = f.mark("T")
            val edge = starEdge(STATIC, source, org.opentaint.dataflow.ap.ifds.AccessPathBase.Return, emptyList())
            val excluded = edge.copy(to = edge.to.copy(mark = MarkSlot.Star(MarkSet(intArrayOf(t.id)))))
            val write = starEdge(org.opentaint.dataflow.ap.ifds.AccessPathBase.Argument(0), emptyList(), STATIC, target)
            val aliases = StatementSummary(setOf(STATIC, write.from.base), listOf(excluded, write).map(MicroEdge::of),
                emptyList(), emptyMap())
            f.call(owner, 0, CallPlan(aliases.touched, listOf(CallStage.Edges(CallPoint.BEFORE,
                CallPoint.AFTER, StageKind.ALIASES, aliases)), emptyList(), CallPoint.BEFORE, CallPoint.AFTER))
            val profiles = f.staticProfiles()
            val actual = profiles[owner.method]
            assertTrue(profiles.localReads(owner.method).isEmpty(), "An alias endpoint is not a value-read seed")
            assertEquals(setOf(source, target), actual.footprint.fieldUniverse.map { it.pathData.toList() }.toSet())
            assertTrue(actual.staticMayWrite)
            assertTrue(actual.precomputedRequests.isEmpty, "Exclusion interests are produced at the actual operation")
            val scalar = SelectedScalarPlan(f)
            assertTrue(scalar.fieldReads(owner).isEmpty())
            assertTrue(scalar.writes(owner))
            assertTrue(actual.markInterests.isEmpty(), "The runtime profile retains no local or inherited mark payload")
        }
    }

}
