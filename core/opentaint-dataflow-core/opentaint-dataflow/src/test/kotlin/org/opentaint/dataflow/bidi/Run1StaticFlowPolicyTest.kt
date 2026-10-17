package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.CallerRef
import kotlin.test.*

class Run1StaticFlowPolicyTest {
    @Test fun `static FLOW demotion keeps every payload annotation and stays sticky on a local`() {
        val f = StoreFixtures()
        val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
        val field = f.field("f")
        val t = f.mark("T")
        val origin = f.manager.initial(f.pattern(STATIC, listOf(cls, field), Tail.STAR, MarkSlot.STAR))
        for (tail in listOf(Tail.STAR, Tail.ANY)) {
            val raw = f.fact(STATIC, listOf(cls, field), tail, MarkSlot.Star(f.manager.markSetOf(t))) as FlowTree
            val normalized = normalizeRun1StaticFlow(origin, raw) as FlowTree
            assertEquals(Layer.DEMAND, normalized.layer)
            assertEquals(tail, normalized.tail)
            assertSame(raw.root, normalized.root)
            assertEquals(raw.exclusion, normalized.exclusion)
            assertSame(raw.markExclusion, normalized.markExclusion)
            assertSame(normalized, normalizeRun1StaticFlow(origin, normalized))
        }
        val local = f.fact(AccessPathBase.Return, tail = Tail.STAR, mark = MarkSlot.STAR)
        assertEquals(Layer.DEMAND, normalizeRun1StaticFlow(origin, local).layer)
        val ordinaryOrigin = f.manager.initial(f.pattern(tail = Tail.STAR, mark = MarkSlot.STAR))
        val staticSupport = f.fact(STATIC, listOf(cls, field), Tail.STAR, MarkSlot.STAR)
        val returned = normalizeRun1StaticFlow(ordinaryOrigin, local, staticSupport)
        assertEquals(Layer.DEMAND, returned.layer)
        assertEquals(Tail.STAR, (returned as FlowTree).tail)
        assertSame(local, normalizeRun1StaticFlow(ordinaryOrigin, local))
    }

    @Test fun `concrete static TAINT is DEMAND while independent ZERO stays normal`() {
        val f = StoreFixtures()
        val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
        val field = f.field("f")
        val pattern = f.pattern(STATIC, listOf(cls, field))
        val origin = f.manager.initial(pattern)
        val taint = f.manager.factsOf(pattern.fact, pattern.exclusion, Layer.NORMAL)
        assertEquals(Layer.DEMAND, normalizeRun1StaticFlow(origin, taint).layer)
        assertSame((taint as TaintTree).root, (normalizeRun1StaticFlow(origin, taint) as TaintTree).root)
        assertSame(Reach.NORMAL, normalizeRun1StaticFlow(f.manager.zero, Reach.NORMAL))
    }

    @Test fun `compaction removes only the exact canonical identity leaf from a packed FLOW group`() {
        val f = StoreFixtures()
        val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
        val field = f.field("f")
        val sibling = f.field("g")
        val child = f.field("h")
        val origin = f.manager.initial(f.pattern(STATIC, listOf(cls, field), Tail.STAR, MarkSlot.STAR))
        val underField = FlowAlgebra.node(FlowLeaf.LEAF, intArrayOf(child), arrayOf(FlowAlgebra.LEAF_NODE))
        val classRoot = FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(field, sibling),
            arrayOf(underField, FlowAlgebra.LEAF_NODE))
        val root = FlowAlgebra.prepend(f.manager, f.manager.path(listOf(cls)), classRoot)
        val input = f.manager.flowTree(STATIC, Layer.DEMAND, ExclusionSet.Empty, MarkSet.EMPTY, root, Tail.STAR)
        val compact = assertNotNull(compactRun1StaticIdentity(f.ops, origin, input))
        assertEquals(setOf(listOf(cls, field, child), listOf(cls, sibling)),
            f.ops.leaves(compact).map { it.fact.path }.toSet())
        assertEquals(Layer.DEMAND, compact.layer)
        assertEquals(Tail.STAR, (compact as FlowTree).tail)
        assertNull(compactRun1StaticIdentity(f.ops, origin, f.ops.startFact(origin).withLayer(Layer.DEMAND)))
    }

    @Test fun `same-field cleaner tail and concrete identities are not compacted`() {
        val f = StoreFixtures()
        val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
        val field = f.field("f")
        val t = f.mark("T")
        val origin = f.manager.initial(f.pattern(STATIC, listOf(cls, field), Tail.STAR, MarkSlot.STAR))
        val identity = f.ops.startFact(origin)
        assertSame(identity, compactRun1StaticIdentity(f.ops, origin, identity))
        val changedTail = f.fact(STATIC, listOf(cls, field), Tail.ANY, MarkSlot.STAR, layer = Layer.DEMAND)
        assertSame(changedTail, compactRun1StaticIdentity(f.ops, origin, changedTail))
        val out = CollectingOut()
        f.ops.clean(identity.withLayer(Layer.DEMAND), origin,
            Cleaner(STATIC, f.manager.path(listOf(cls, field)), CleanReach.AT_AND_BELOW, t),
            ApMode(true, Direction.FORWARD, 1), out)
        assertEquals(listOf(t), out.markRequests)
        val cleaned = out.results.single()
        assertEquals(MarkSet(intArrayOf(t.id)), (cleaned as FlowTree).markExclusion)
        assertSame(cleaned, compactRun1StaticIdentity(f.ops, origin, cleaned))
        val concrete = f.pattern(STATIC, listOf(cls, field))
        val concreteInitial = f.manager.initial(concrete)
        val concreteIdentity = f.ops.startFact(concreteInitial)
        assertSame(concreteIdentity, compactRun1StaticIdentity(f.ops, concreteInitial, concreteIdentity))
    }

    @Test fun `ordinary method primitive sees DEMAND before a static-to-local transfer`() {
        MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val field = f.store.field("f")
            val input = f.store.fact(STATIC, listOf(cls, field), Tail.STAR, MarkSlot.STAR)
            val origin = f.manager.initial(f.store.pattern(tail = Tail.STAR, mark = MarkSlot.STAR))
            val core = f.core()
            val edge = PathEdge(PathFact(STATIC, listOf(cls, field), Tail.STAR, MarkSlot.STAR),
                PathFact(AccessPathBase.Return, emptyList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty)
            val summary = StatementSummary(setOf(STATIC), listOf(MicroEdge.of(edge)), emptyList(), emptyMap())
            val result = ArrayList<Facts>()
            core.applier.stage(summary, origin, input, Place(f.inst(0), false, false)) { _, facts, _ -> result += facts }
            val returned = result.single()
            assertEquals(AccessPathBase.Return, returned.base)
            assertEquals(Layer.DEMAND, returned.layer)
            assertEquals(Tail.STAR, (returned as FlowTree).tail)
            assertTrue(Guard.MemoryEffect.admits(true, returned.layer))
            f.drain(core)
        }
    }

    @Test fun `disjoint excluded target still requests on every original before geometry`() {
        MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val written = f.store.field("f")
            val supplied = f.store.field("g")
            val t = f.store.mark("T")
            val u = f.store.mark("U")
            val fPath = listOf(cls, written)
            val gPath = listOf(cls, supplied)
            f.declareStaticDomain(fPath, gPath)
            val from = PathFact(STATIC, fPath, Tail.STAR, MarkSlot.STAR)
            val excluded = PathEdge(from, from.copy(mark = MarkSlot.Star(f.manager.markSetOf(t))),
                ExclusionSet.Empty)
            f.statement(0, StatementSummary(setOf(STATIC),
                listOf(MicroEdge.of(keepEdge(STATIC)), MicroEdge.of(excluded)), emptyList(), emptyMap()))
            val core = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
            val slot = Run1Slot.StaticField(cls, supplied)
            // The from-f prescan request does not preinstall the changed original g/T.
            core.addRootZero()
            f.drain(core)
            assertFalse(f.shared.run1StaticProfiles!![f.key.method].precomputedRequests.borrow(slot).containsId(t.id))
            val actual = f.store.fact(STATIC, gPath, Tail.EXACT, MarkSlot.Concrete(u))
            f.admit(core, CallerRef(f.key, f.manager.zero, Layer.NORMAL, f.inst(1)), Layer.NORMAL, actual)
            f.drain(core)
            assertTrue(f.requestRow(core, slot).containsId(t.id),
                "The generic excluded-target creator runs even when f and g are disjoint")
            val entries = assertNotNull(core.staticEntryFacts())
            assertEquals(setOf(gPath), StaticIdentityEntries(f.ops).originals(entries)
                .map { it.pathArray.toList() }.toSet())
            val original = slot.initial(f.manager)
            val data = f.processed.filter { it.premise === original }
            assertTrue(data.isNotEmpty())
            assertTrue(data.all { row -> row.facts.layer == Layer.DEMAND &&
                (row.facts as FlowTree).markExclusion.isEmpty &&
                f.ops.leaves(row.facts).all { it.fact.path == gPath } },
                "The disjoint value stays the same identity after the request side effect")
            assertTrue(core.summaries.all().none { it.first === original },
                "Only the unchanged g identity remains, covered by the root default")
        }
    }

    @Test fun `unreachable normal exit does not gate the ordinary root DEMAND default`() {
        MethodAnalysisFixtures(successors = listOf(listOf(1), listOf(1), emptyList()), normalExit = 2).use { f ->
            val core = f.core()
            core.addRootZero()
            f.drain(core)
            assertTrue(f.processed.none { it.node.location.index == 2 })
            assertFalse(core.nonZeroInitial)
            val root = core.summaries.all().single { (premise, _) ->
                premise is InitialAp && isSpecialStaticRoot(premise)
            }
            assertEquals(Layer.DEMAND, root.second.layer)
            assertEquals(listOf(EMPTY_PATH.toList()), f.ops.leaves(root.second).map { it.fact.path }.toList())
            assertEquals(Tail.STAR, (root.second as FlowTree).tail)
            assertTrue(core.initials.all().none { it.base == STATIC })
        }
    }

    @Test fun `later runs retain normal static identities and their strong exclusions`() {
        MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val field = f.store.field("f")
            val sibling = f.store.field("g")
            val origin = f.manager.initial(f.store.pattern(STATIC, listOf(cls, field), Tail.STAR, MarkSlot.STAR))
            val actual = f.store.fact(STATIC, listOf(cls, field), Tail.STAR, MarkSlot.STAR,
                exclusion = ExclusionSet.of(sibling))
            val core = f.core(index = 3, L = 2)
            assertSame(actual, core.normalizeFlow(origin, actual))
            core.summaryDelta(origin, actual)
            f.drain(core)
            val stored = core.summaries.all().single { it.first === origin }.second
            assertEquals(Layer.NORMAL, stored.layer)
            assertEquals(ExclusionSet.of(sibling), (stored as FlowTree).exclusion)
            assertEquals(Tail.STAR, stored.tail)
        }
    }
}
