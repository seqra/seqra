package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import kotlin.test.*

/** F78 actor barriers and caller replay; historical full-query joins are not the request policy. */
class RequestBarrierJoinTest {
    private fun mark(f: MethodAnalysisFixtures, name: String): TaintMark = f.groupMark(f.mark(name)).mark
    private val x = AccessPathBase.Argument(0)
    private fun policy(f: MethodAnalysisFixtures, base: AccessPathBase = x, path: List<Int> = emptyList()) =
        f.manager.initial(base, f.manager.path(path), Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
    private fun core(f: MethodAnalysisFixtures): RunMethodAnalyzer =
        f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
    private fun caller(f: MethodAnalysisFixtures, context: String = "caller", flow: Boolean = false,
                       layer: Layer = Layer.NORMAL, call: Int = 1) =
        CallerRef(f.store.key(context), if (flow) policy(f) else f.manager.zero, layer, f.inst(call))
    private fun local(actor: RunMethodAnalyzer) = actor.localServices
    private fun flush(f: MethodAnalysisFixtures, actor: RunMethodAnalyzer, quantum: Int = 1): Int =
        f.drain(actor, quantum)
    private fun climbs(f: MethodAnalysisFixtures): Set<Triple<MethodKey, InitialAp, TaintMark>> = buildSet {
        for (event in f.events) when (event) {
            is RunEvent.MarkRequestsIn -> for ((slot, marks) in event.rows)
                marks.forEachMark { add(Triple(event.method, slot.initial(f.manager), it)) }
            else -> Unit
        }
    }

    @Test
    fun `fresh requests and links in either arrival order give all exact answers only at flush`(): Unit {
        for (requestsFirst in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val actor = core(f)
            val i = policy(f)
            val t = mark(f, "T")
            val u = mark(f, "U")
            val marks = RequestMarkSet.fromIds(intArrayOf(t.id, u.id))
            fun addRequests() = actor.addMarkRequests(i, marks)
            fun addLinks() {
                f.admit(actor, caller(f), Layer.NORMAL, f.fact(mark = MarkSlot.Concrete(t)))
                f.admit(actor, caller(f), Layer.NORMAL, f.fact(mark = MarkSlot.Concrete(u)))
            }
            if (requestsFirst) { addRequests(); addLinks() } else { addLinks(); addRequests() }
            assertEquals(setOf(i), actor.initials.all().toSet())
            assertFails { actor.freeze() }
            assertTrue(f.enqueued.isNotEmpty(), "The phase has counted Work ownership")
            flush(f, actor)
            val expected = setOf(i, f.manager.initial(f.pattern(mark = MarkSlot.Concrete(t))),
                f.manager.initial(f.pattern(mark = MarkSlot.Concrete(u))))
            assertEquals(expected, actor.initials.all().toSet())
            assertTrue(climbs(f).isEmpty())
            actor.addMarkRequests(i, marks)
            f.admit(actor, caller(f), Layer.NORMAL, f.fact(mark = MarkSlot.Concrete(t)))
            flush(f, actor)
            assertEquals(expected, actor.initials.all().toSet(), "Repeated input may repeat intersection but not method starts")
        }
    }

    @Test
    fun `overlapping FLOW paths replay packed climbs from caller N2`(): Unit {
        for (layer in Layer.entries) MethodAnalysisFixtures().use { f ->
            val actor = core(f)
            val i = policy(f)
            val marks = (0 until 32).map { mark(f, "T$it") }
            val excluded = f.manager.intern(MarkSet(intArrayOf(marks[0].id, marks[5].id)))
            val field = f.field("f")
            val g = f.field("g")
            val h = f.field("h")
            val tail = if (layer == Layer.NORMAL) Tail.STAR else Tail.ANY
            val e = if (layer == Layer.NORMAL) ExclusionSet.of(h) else ExclusionSet.Empty
            val a = caller(f, flow = true)
            val b = a.copy(callerLayer = Layer.DEMAND, call = f.inst(2))
            actor.addMarkRequests(i, RequestMarkSet.fromIds(marks.map { it.id }.toIntArray()))
            for (ref in listOf(a, b)) for (path in listOf(emptyList(), listOf(field))) {
                f.admit(actor, ref, layer, f.fact(path = path, tail = tail,
                    mark = MarkSlot.Star(excluded), exclusion = e, layer = layer))
            }
            assertTrue(f.events.isEmpty())
            flush(f, actor)
            assertEquals(marks.map { Triple(a.caller, a.premise as InitialAp, it) }.toSet(), climbs(f),
                "F78 ignores incoming FLOW mark exclusions")
            val batches = f.events.filterIsInstance<RunEvent.MarkRequestsIn>()
            assertEquals(1, batches.size, "Equal full request targets combine in one replay visit")
            assertEquals(32, batches.single().rows.getValue(assertNotNull(Run1Slot.fromInitial(i))).size)
            assertEquals(setOf(a, b), f.links(actor).asSequence().map { it.caller }.toSet())
            assertEquals(setOf(i), actor.initials.all().toSet())
        }
    }

    @Test
    fun `a late concrete link joins a previously flushed standing request`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val actor = core(f)
            val i = policy(f)
            val t = mark(f, "T")
            actor.addMarkRequest(i, t)
            flush(f, actor)
            assertTrue(climbs(f).isEmpty())
            f.admit(actor, caller(f), Layer.NORMAL, f.fact(mark = MarkSlot.Concrete(t)))
            flush(f, actor)
            assertEquals(setOf(i, f.manager.initial(f.pattern(mark = MarkSlot.Concrete(t)))), actor.initials.all().toSet())
        }
    }

    @Test
    fun `same group keeps nonstatic exact and chain answers while static inputs never seed entry`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val t = mark(f, "T")
            val field = f.field("f")
            val g = f.field("g")
            val h = f.field("h")
            val cls = f.manager.accessors.index(ClassStaticAccessor("Box"))
            f.declareStaticDomain(listOf(cls, field), listOf(cls, g))
            val actor = core(f)
            val root = policy(f)
            actor.addMarkRequest(root, t)
            actor.addMarkRequest(policy(f, STATIC, listOf(cls, field)), t)
            actor.addMarkRequest(policy(f, STATIC, listOf(cls, g)), t)
            val ref = caller(f)
            for (path in listOf(emptyList(), listOf(field)))
                f.admit(actor, ref, Layer.NORMAL, f.fact(path = path, mark = MarkSlot.Concrete(t)))
            f.admit(actor, ref, Layer.NORMAL, f.fact(base = STATIC, path = listOf(cls, field), mark = MarkSlot.Concrete(t)))
            f.admit(actor, ref, Layer.NORMAL, f.fact(base = STATIC, path = listOf(cls, g), tail = Tail.ANY_TAINT,
                mark = MarkSlot.Concrete(t), exclusion = ExclusionSet.of(h)))
            flush(f, actor)
            val expected = setOf(root,
                f.manager.initial(f.pattern(mark = MarkSlot.Concrete(t))),
                f.manager.initial(f.pattern(tail = Tail.STAR, mark = MarkSlot.Concrete(t))))
            assertEquals(expected, actor.initials.all().toSet())
            assertTrue(climbs(f).isEmpty())
        }
    }

    @Test
    fun `a request and input produced reentrantly by fact work join after the method drains`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val actor = core(f)
            val t = mark(f, "T")
            val u = mark(f, "U")
            val i = policy(f)
            var reentered = false
            f.onProcess = { _, _ ->
                if (!reentered) {
                    reentered = true
                    actor.addMarkRequest(i, u)
                    f.admit(actor, caller(f, "late"), Layer.NORMAL, f.fact(mark = MarkSlot.Concrete(u)))
                }
            }
            actor.addMarkRequest(i, t)
            f.admit(actor, caller(f), Layer.NORMAL, f.fact(mark = MarkSlot.Concrete(t)))
            assertFalse(reentered)
            flush(f, actor)
            assertTrue(reentered)
            assertTrue(f.manager.initial(f.pattern(mark = MarkSlot.Concrete(t))) in actor.initials.all())
            assertTrue(f.manager.initial(f.pattern(mark = MarkSlot.Concrete(u))) in actor.initials.all())
        }
    }

    @Test
    fun `an exact-only delta retains its admitted E and both exact and chain nonstatic answers`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val g = f.field("g")
            val actor = core(f)
            val t = mark(f, "T")
            val slot = policy(f)
            val ref = caller(f)
            val e = ExclusionSet.of(g)
            val must = f.fact(x, emptyList(), Tail.ANY_TAINT, MarkSlot.Concrete(t), e)
            f.admit(actor, ref, Layer.NORMAL, must)
            actor.addMarkRequest(slot, t)
            flush(f, actor)
            val chain = f.manager.initial(x, slot.path, Tail.STAR, ExclusionSet.Empty, MarkSlot.Concrete(t))
            assertContains(actor.initials.all(), chain, "Request matching ignores the supplier E")
            val child = f.fact(x, emptyList(), mark = MarkSlot.Concrete(t))
            val root = f.manager.taintAlg.mergeAddDelta(assertIs<TaintTree>(must).root, assertIs<TaintTree>(child).root).first
            f.admit(actor, ref, Layer.NORMAL, f.manager.taintTree(x, Layer.NORMAL, root, e))
            flush(f, actor)
            val exact = f.manager.initial(x, slot.path, Tail.EXACT, ExclusionSet.Empty, MarkSlot.Concrete(t))
            assertContains(actor.initials.all(), exact)
            assertContains(actor.initials.all(), chain)
            assertTrue(f.links(actor).any { it.addedFact.fact.path.isEmpty() && it.addedFact.fact.tail == Tail.EXACT })
            assertTrue(f.links(actor).any { it.addedFact.exclusion == e && it.addedFact.fact.tail == Tail.ANY_TAINT })
        }
    }
}
