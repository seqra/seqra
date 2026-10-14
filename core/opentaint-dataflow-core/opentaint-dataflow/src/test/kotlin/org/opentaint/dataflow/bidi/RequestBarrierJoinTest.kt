package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.dataflow.bidi.fixtures.ScalarPositionLocalServices
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import kotlin.test.*

/** Expectations expand scalar request actions independently of the deferred join implementation. */
class RequestBarrierJoinTest {
    private val x = AccessPathBase.Argument(0)
    private fun policy(f: MethodAnalysisFixtures, base: AccessPathBase = x, path: List<Int> = emptyList()) =
        f.manager.initial(base, f.manager.path(path), Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
    private fun core(f: MethodAnalysisFixtures): RunMethodAnalyzer =
        f.core(localFactory = MethodLocalServicesFactory { ScalarPositionLocalServices(it, f.positionDeliveries::add) })
    private fun caller(f: MethodAnalysisFixtures, context: String = "caller", flow: Boolean = false,
                       layer: Layer = Layer.NORMAL, call: Int = 1) =
        CallerRef(f.store.key(context), if (flow) policy(f) else f.manager.zero, layer, f.inst(call))
    private fun local(actor: RunMethodAnalyzer) = actor.localServices
    private fun flush(actor: RunMethodAnalyzer, quantum: Int = 1): Int {
        var calls = 0
        do {
            calls++
            check(calls < 100_000) { "The finite request phase did not finish" }
        } while (!local(actor).flushRequests(quantum))
        assertFalse(local(actor).hasPendingRequests)
        return calls
    }
    private fun climbs(f: MethodAnalysisFixtures): Set<Triple<MethodKey, InitialAp, TaintMark>> = buildSet {
        for (event in f.events) when (event) {
            is RunEvent.MarkRequestsIn -> for (i in 0 until event.marks.size)
                add(Triple(event.method, event.premise, event.marks.markAt(i)))
            else -> Unit
        }
    }

    @Test
    fun `fresh requests and links in either arrival order give all exact answers only at flush`(): Unit {
        for (requestsFirst in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val actor = core(f)
            val i = policy(f)
            val t = f.mark("T")
            val u = f.mark("U")
            val marks = RequestMarkSet.fromIds(intArrayOf(t.id, u.id))
            fun addRequests() = actor.addMarkRequests(i, marks)
            fun addLinks() {
                actor.addLink(caller(f), Layer.NORMAL, f.fact(mark = MarkSlot.Concrete(t)))
                actor.addLink(caller(f), Layer.NORMAL, f.fact(mark = MarkSlot.Concrete(u)))
            }
            if (requestsFirst) { addRequests(); addLinks() } else { addLinks(); addRequests() }
            assertEquals(setOf(i), actor.initials.all().toSet())
            assertTrue(local(actor).hasPendingRequests)
            assertTrue(f.enqueued.isNotEmpty(), "The phase has counted Work ownership")
            flush(actor)
            val expected = setOf(i, f.manager.initial(f.pattern(mark = MarkSlot.Concrete(t))),
                f.manager.initial(f.pattern(mark = MarkSlot.Concrete(u))))
            assertEquals(expected, actor.initials.all().toSet())
            assertTrue(climbs(f).isEmpty())
            val enqueues = f.enqueued.size
            actor.addMarkRequests(i, marks)
            actor.addLink(caller(f), Layer.NORMAL, f.fact(mark = MarkSlot.Concrete(t)))
            assertFalse(local(actor).hasPendingRequests)
            assertEquals(enqueues, f.enqueued.size)
        }
    }

    @Test
    fun `overlapping FLOW paths produce one owned climb set while full raw caller groups remain`(): Unit {
        for (layer in Layer.entries) MethodAnalysisFixtures().use { f ->
            val actor = core(f)
            val i = policy(f)
            val marks = (0 until 32).map { f.mark("T$it") }
            val excluded = f.manager.intern(MarkSet(intArrayOf(marks[0].id, marks[5].id)))
            val field = f.field("f")
            val g = f.field("g")
            val h = f.field("h")
            val tail = if (layer == Layer.NORMAL) Tail.STAR else Tail.ANY
            val e = if (layer == Layer.NORMAL) ExclusionSet.of(h) else ExclusionSet.Empty
            val a = caller(f, flow = true)
            val b = a.copy(callerLayer = Layer.DEMAND, call = f.inst(2))
            actor.addMarkRequests(i, RequestMarkSet.fromIds(marks.map { it.id }.toIntArray()))
            for (ref in listOf(a, b)) for (path in listOf(listOf(field), listOf(field, g))) {
                actor.addLink(ref, layer, f.fact(path = path, tail = tail,
                    mark = MarkSlot.Star(excluded), exclusion = e, layer = layer))
            }
            assertTrue(f.events.isEmpty())
            assertTrue(flush(actor) > 1, "Quantum one must yield within the member scans")
            assertEquals(marks.filter { it !in excluded }.map { Triple(a.caller, a.premise as InitialAp, it) }.toSet(), climbs(f))
            val batches = f.events.filterIsInstance<RunEvent.MarkRequestsIn>()
            assertEquals(1, batches.size, "Equal full request targets combine across raw caller groups")
            assertEquals(30, batches.single().marks.size)
            assertEquals(setOf(a, b), actor.links.links().map { it.caller }.toSet())
            assertEquals(setOf(i), actor.initials.all().toSet())
        }
    }

    @Test
    fun `a late concrete link joins a previously flushed standing request`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val actor = core(f)
            val i = policy(f)
            val t = f.mark("T")
            actor.addMarkRequest(i, t)
            flush(actor)
            assertTrue(climbs(f).isEmpty())
            actor.addLink(caller(f), Layer.NORMAL, f.fact(mark = MarkSlot.Concrete(t)))
            flush(actor)
            assertEquals(setOf(i, f.manager.initial(f.pattern(mark = MarkSlot.Concrete(t)))), actor.initials.all().toSet())
        }
    }

    @Test
    fun `concrete same mark keeps exact chain and multiple static answer initials`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val actor = core(f)
            val t = f.mark("T")
            val field = f.field("f")
            val g = f.field("g")
            val h = f.field("h")
            val cls = f.manager.accessors.index(ClassStaticAccessor("Box"))
            val root = policy(f)
            val stat = policy(f, STATIC)
            actor.addMarkRequest(root, t)
            actor.addMarkRequest(stat, t)
            val ref = caller(f)
            for (path in listOf(emptyList(), listOf(field)))
                actor.addLink(ref, Layer.NORMAL, f.fact(path = path, mark = MarkSlot.Concrete(t)))
            actor.addLink(ref, Layer.NORMAL, f.fact(base = STATIC, path = listOf(cls, field), mark = MarkSlot.Concrete(t)))
            actor.addLink(ref, Layer.NORMAL, f.fact(base = STATIC, path = listOf(cls, g), tail = Tail.ANY_TAINT,
                mark = MarkSlot.Concrete(t), exclusion = ExclusionSet.of(h)))
            flush(actor)
            val expected = setOf(root, stat,
                f.manager.initial(f.pattern(mark = MarkSlot.Concrete(t))),
                f.manager.initial(f.pattern(tail = Tail.STAR, mark = MarkSlot.Concrete(t))),
                f.manager.initial(f.pattern(base = STATIC, path = listOf(cls, field), mark = MarkSlot.Concrete(t))),
                f.manager.initial(f.pattern(base = STATIC, path = listOf(cls, g), tail = Tail.ANY, mark = MarkSlot.Concrete(t))))
            assertEquals(expected, actor.initials.all().toSet())
            assertTrue(climbs(f).isEmpty())
        }
    }

    @Test
    fun `position answer remains immediate and reentrant marks belong to the next captured phase`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val actor = core(f)
            val cls = f.manager.accessors.index(ClassStaticAccessor("Box"))
            val field = f.field("f")
            val t = f.mark("T")
            val u = f.mark("U")
            val i = policy(f)
            val stat = policy(f, STATIC)
            val atClass = policy(f, STATIC, listOf(cls))
            assertIs<ScalarPositionLocalServices>(actor.localServices).addPositionRequest(stat, RequestKind.Position(checkNotNull(f.manager.path(listOf(cls)))))
            actor.addLink(caller(f), Layer.NORMAL, f.fact(base = STATIC, path = listOf(cls, field), mark = MarkSlot.Concrete(t)))
            assertTrue(atClass in actor.initials.all(), "Position startup does not wait for the mark phase")
            var reentered = false
            f.onCut = { point, _ ->
                if (point == Cut.ENTRY_RULES && !reentered) {
                    reentered = true
                    actor.addMarkRequest(i, u)
                    actor.addLink(caller(f, "late"), Layer.NORMAL, f.fact(mark = MarkSlot.Concrete(u)))
                }
            }
            actor.addMarkRequest(i, t)
            actor.addLink(caller(f), Layer.NORMAL, f.fact(mark = MarkSlot.Concrete(t)))
            flush(actor)
            assertTrue(reentered)
            assertTrue(f.manager.initial(f.pattern(mark = MarkSlot.Concrete(t))) in actor.initials.all())
            assertTrue(f.manager.initial(f.pattern(mark = MarkSlot.Concrete(u))) in actor.initials.all())
        }
    }

    @Test
    fun `an exact-only delta keeps its admitted must exclusion and every full caller target`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val actor = core(f)
            val t = f.mark("T")
            val g = f.field("g")
            val h = f.field("h")
            val i = policy(f, path = listOf(g))
            val ref = caller(f)
            val e = ExclusionSet.of(g)
            val must = f.fact(tail = Tail.ANY_TAINT, mark = MarkSlot.Concrete(t), exclusion = e)
            actor.addLink(ref, Layer.NORMAL, must)
            actor.addMarkRequest(i, t)
            flush(actor)
            assertFalse(actor.initials.all().any { it.mark == MarkSlot.Concrete(t) })
            // Add a concrete exception at the excluded child under the original admitted E key.
            val child = f.fact(path = listOf(g, h), mark = MarkSlot.Concrete(t))
            val root = f.manager.taintAlg.mergeAddDelta(assertIs<TaintTree>(must).root, assertIs<TaintTree>(child).root).first
            val mixed = f.manager.taintTree(x, Layer.NORMAL, root, e)
            actor.addLink(ref, Layer.NORMAL, mixed)
            flush(actor)
            assertTrue(f.manager.initial(f.pattern(path = listOf(g), tail = Tail.STAR,
                mark = MarkSlot.Concrete(t))) in actor.initials.all())
            assertTrue(actor.links.links().any { it.addedFact.fact.path == listOf(g, h) })
            assertTrue(actor.links.links().any { it.addedFact.exclusion == e && it.addedFact.fact.tail == Tail.ANY_TAINT })
        }
    }
}
