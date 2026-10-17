package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.interp.StatementSummary
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.dataflow.bidi.fixtures.ReferenceRequestDelivery
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

class RequestJoinTest {
    private val x = AccessPathBase.Argument(0)
    private val static = AccessPathBase.ClassStatic
    private fun policy(f: MethodAnalysisFixtures, base: AccessPathBase = x, path: List<Int> = emptyList()): InitialAp =
        f.manager.initial(base, f.manager.path(path), Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
    private fun initial(f: MethodAnalysisFixtures, base: AccessPathBase, path: List<Int>, tail: Tail, mark: TaintMark,
                        exclusion: ExclusionSet = ExclusionSet.Empty): InitialAp =
        f.manager.initial(base, f.manager.path(path), tail, exclusion, MarkSlot.Concrete(mark))
    private fun core(f: MethodAnalysisFixtures, key: MethodKey = f.key, index: Int = 1): RunMethodAnalyzer =
        f.core(index = index, key = key, localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
    private fun caller(f: MethodAnalysisFixtures, context: String = "caller", base: AccessPathBase = x,
                       path: List<Int> = emptyList(), node: Int = 1, flow: Boolean = false): CallerRef =
        CallerRef(f.store.key(context), if (flow) policy(f, base, path) else f.manager.zero, Layer.NORMAL, f.inst(node))
    private fun arrive(f: MethodAnalysisFixtures, core: RunMethodAnalyzer, firstRequest: Boolean, premise: InitialAp, kind: RequestKind,
                       ref: CallerRef, added: Facts) {
        if (firstRequest) request(core, premise, kind)
        f.admit(core, ref, added.layer, added)
        if (!firstRequest) request(core, premise, kind)
        if (kind is RequestKind.Mark) {
            if (added.base != STATIC) assertTrue(core.initials.all().none { !it.isZero && it.mark is MarkSlot.Concrete },
                "Mark answers wait for the explicit method barrier")
            barrier(f, core)
        }
    }
    private fun barrier(f: MethodAnalysisFixtures, actor: RunMethodAnalyzer) {
        f.flushRequests(actor)
        assertFalse(actor.localServices.hasPendingRequests)
    }
    private fun request(actor: RunMethodAnalyzer, initial: InitialAp, kind: RequestKind) {
        when (kind) {
            is RequestKind.Mark -> actor.addMarkRequest(initial, kind.mark)
            is RequestKind.Position -> error("Historical Position is tested only by the pure reference controls")
        }
    }
    private fun climbs(f: MethodAnalysisFixtures): List<ReferenceRequestDelivery> = buildList {
        addAll(f.positionDeliveries)
        for (event in f.events) if (event is RunEvent.MarkRequestsIn) for ((slot, marks) in event.rows)
            marks.forEachMark { add(ReferenceRequestDelivery(event.method, slot.initial(f.manager), RequestKind.Mark(it))) }
    }

    @Test
    fun `a request produced during fact processing joins through the same method-empty batch`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("Box"))
            val field = f.field("f")
            val t = f.mark("T")
            val u = f.mark("U")
            val atField = policy(f, static, listOf(cls, field))
            f.declareStaticDomain(listOf(cls, field))
            // This barrier control needs real fact work; a write/request alone
            // no longer starts an abstract static-field analysis.
            f.statement(0, StatementSummary(setOf(STATIC), listOf(org.opentaint.dataflow.bidi.interp.MicroEdge.of(
                org.opentaint.dataflow.bidi.interp.starEdge(STATIC, listOf(cls, field),
                    AccessPathBase.LocalVar(0), emptyList()))), emptyList(), emptyMap()))
            val actor = core(f)
            request(actor, atField, RequestKind.Mark(u))
            var inserted = false
            f.onProcess = { _, _ ->
                if (!inserted) { inserted = true; request(actor, atField, RequestKind.Mark(t)) }
            }
            val ref = caller(f, "A", flow = true)
            f.admit(actor, ref, Layer.NORMAL,
                f.fact(static, listOf(cls, field), Tail.STAR, MarkSlot.STAR))
            assertFalse(inserted)
            assertTrue(climbs(f).isEmpty())
            barrier(f, actor)
            assertTrue(inserted)
            assertEquals(setOf(ReferenceRequestDelivery(ref.caller, ref.premise as InitialAp, RequestKind.Mark(t)),
                ReferenceRequestDelivery(ref.caller, ref.premise as InitialAp, RequestKind.Mark(u))), climbs(f).toSet())
            assertEquals(setOf(t,u),f.events.filterIsInstance<RunEvent.MarkRequestsIn>().flatMap { event ->
                event.rows.values.flatMap { marks -> buildList { marks.forEachMark { add(it) } } } }.toSet())
            assertTrue(f.initials(actor).none { it.base == static && it.pathData.size == 1 && it.isFlow })
        }
    }

    @Test
    fun `F75 excluded disjoint cleaner request remains standing for a later concrete link`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val t = f.mark("T")
            val a = f.field("a")
            val b = f.field("b")
            val i = policy(f)
            val actor = core(f)
            val excluded = MarkSlot.Star(f.manager.markSetOf(t))
            f.admit(actor, caller(f, "abstract", flow = true), Layer.NORMAL,
                f.fact(tail = Tail.STAR, mark = excluded))
            val input = f.fact(path = listOf(a), tail = Tail.STAR, mark = excluded)
            val cleaner = Cleaner(x, f.manager.path(listOf(b)), CleanReach.EXACT, t)
            val cleaned = ArrayList<Facts>()
            f.ops.clean(input, i, cleaner, actor.mode, actor.collect(i, input) { premise, facts ->
                assertSame(i, premise)
                cleaned += facts
            })
            val residual = assertIs<FlowTree>(cleaned.single())
            val original = assertIs<FlowTree>(input)
            assertSame(original.root, residual.root)
            assertEquals(original.base, residual.base)
            assertEquals(original.layer, residual.layer)
            assertEquals(original.exclusion, residual.exclusion)
            assertEquals(original.markExclusion, residual.markExclusion)
            assertEquals(setOf(i), f.initials(actor))
            assertTrue(climbs(f).isEmpty())
            f.admit(actor, caller(f, "concrete"), Layer.NORMAL, f.fact(mark = MarkSlot.Concrete(t)))
            assertEquals(setOf(i), f.initials(actor), "The late mark answer is deferred")
            barrier(f, actor)
            assertEquals(setOf(i, initial(f, x, emptyList(), Tail.EXACT, t)), f.initials(actor))
            assertEquals(1, climbs(f).size, "F78 forwards even an incoming excluded mark")
        }
    }

    @Test
    fun `both arrival orders give an exact root answer or a demand chain answer with the incoming policy key`(): Unit {
        for (firstRequest in listOf(true, false)) for (below in listOf(false, true)) {
            MethodAnalysisFixtures().use { f ->
                val t = f.mark("T")
                val a = f.field("a")
                val i = policy(f)
                val request = RequestKind.Mark(t)
                val added = f.fact(path = if (below) listOf(a) else emptyList(), mark = MarkSlot.Concrete(t))
                val actor = core(f)
                arrive(f, actor, firstRequest, i, request, caller(f), added)
                val expected = initial(f, x, emptyList(), if (below) Tail.STAR else Tail.EXACT, t)
                assertEquals(setOf(i, expected), f.initials(actor), "$firstRequest/$below")
                assertTrue(climbs(f).isEmpty())
                val start = actor.edges.edgesAt(f.inst(0), expected).single().second
                val taint = assertIs<TaintTree>(start)
                assertEquals(if (below) Layer.DEMAND else Layer.NORMAL, taint.layer)
                assertEquals(ExclusionSet.Empty, taint.exclusion)
                assertEquals(if (below) MarkSet.EMPTY else f.manager.markSetOf(t), taint.root.leaf.exact)
                assertEquals(if (below) f.manager.markSetOf(t) else MarkSet.EMPTY, taint.root.leaf.any)
                f.drain(actor)
                assertTrue(f.processed.any { it.premise === expected })
            }
        }
    }

    @Test
    fun `a new caller reference to the same abstract added fact climbs even after the request already joined`(): Unit {
        for (firstRequest in listOf(true, false)) {
            MethodAnalysisFixtures().use { f ->
                val t = f.mark("T")
                val i = policy(f)
                val request = RequestKind.Mark(t)
                val added = f.fact(tail = Tail.STAR, mark = MarkSlot.STAR)
                val actor = core(f)
                val a = caller(f, "A", flow = true)
                val b = caller(f, "B", flow = true)
                assertSame(a.premise, b.premise)
                arrive(f, actor, firstRequest, i, request, a, added)
                f.admit(actor, b, Layer.NORMAL, added)
                barrier(f, actor)
                assertEquals(listOf(ReferenceRequestDelivery(a.caller, a.premise as InitialAp, request),
                    ReferenceRequestDelivery(b.caller, b.premise as InitialAp, request)), climbs(f))
                val enqueues = f.enqueued.size
                request(actor, i, request)
                f.admit(actor, a, Layer.NORMAL, added)
                f.admit(actor, b, Layer.NORMAL, added)
                barrier(f, actor)
                assertEquals(2, climbs(f).size)
                assertEquals(enqueues, f.enqueued.size)
                assertEquals(setOf(i), f.initials(actor))
                assertEquals(2, f.links(actor).asSequence().count())
            }
        }
    }

    @Test
    fun `a DEMAND FLOW link joins separately for NORMAL and DEMAND caller layers without replacing its premise`(): Unit {
        for (firstRequest in listOf(true, false)) MethodAnalysisFixtures().use { f ->
            val i = policy(f)
            val request = RequestKind.Mark(f.mark("T"))
            val added = f.fact(tail = Tail.ANY, mark = MarkSlot.STAR, layer = Layer.DEMAND)
            val normalCaller = caller(f, flow = true)
            val demandCaller = normalCaller.copy(callerLayer = Layer.DEMAND)
            assertSame(normalCaller.premise, demandCaller.premise)
            val actor = core(f)
            arrive(f, actor, firstRequest, i, request, normalCaller, added)
            f.admit(actor, demandCaller, Layer.DEMAND, added)
            barrier(f, actor)
            val expected = ReferenceRequestDelivery(normalCaller.caller, normalCaller.premise as InitialAp, request)
            assertEquals(listOf(expected, expected), climbs(f), "Each complete caller reference has its own standing join")
            assertTrue(climbs(f).all { it.premise === normalCaller.premise })
            val links = f.links(actor).asSequence().toList()
            assertEquals(setOf(normalCaller, demandCaller), links.map { it.caller }.toSet())
            assertTrue(links.all { it.linkLayer == Layer.DEMAND && it.addedFact.fact.tail == Tail.ANY })
            assertEquals(setOf(Layer.NORMAL, Layer.DEMAND), links.map { it.caller.callerLayer }.toSet())
            assertEquals(2L, actor.demandLayerEdges,
                "One incoming DEMAND presence and one universal DEMAND static default")
            request(actor, i, request)
            f.admit(actor, normalCaller, Layer.DEMAND, added)
            f.admit(actor, demandCaller, Layer.DEMAND, added)
            assertEquals(2, climbs(f).size)
            assertEquals(2L, actor.demandLayerEdges)
        }
    }

    @Test
    fun `wrong concrete marks and zero do not answer while incoming FLOW mx does not block a request`(): Unit {
        for (firstRequest in listOf(true, false)) for (shape in listOf("wrong", "excluded", "zero")) {
            MethodAnalysisFixtures().use { f ->
                val t = f.mark("T")
                val i = policy(f)
                val added = when (shape) {
                    "wrong" -> f.fact(mark = MarkSlot.Concrete(f.mark("U")))
                    "excluded" -> f.fact(tail = Tail.STAR, mark = MarkSlot.Star(f.manager.markSetOf(t)))
                    else -> Reach.NORMAL
                }
                val actor = core(f)
                arrive(f, actor, firstRequest, i, RequestKind.Mark(t), caller(f, flow = shape == "excluded"), added)
                if (shape == "excluded") assertEquals(1, climbs(f).size, "$firstRequest/$shape")
                else assertTrue(climbs(f).isEmpty(), "$firstRequest/$shape")
                assertEquals(setOf(if (shape == "zero") f.manager.zero else i), f.initials(actor))
                assertTrue(actor.initials.all().all { it.mark !is MarkSlot.Concrete || it.isZero })
            }
        }
    }

    private fun orders(values: List<String>): List<List<String>> =
        if (values.isEmpty()) listOf(emptyList()) else values.flatMap { first ->
            orders(values - first).map { listOf(first) + it }
        }

    @Test
    fun `every finite interleaving of two requests and two links gives both exact answers`(): Unit {
        val permutations = orders(listOf("requestT", "requestU", "linkT", "linkU"))
        assertEquals(24, permutations.size)
        for (order in permutations) MethodAnalysisFixtures().use { f ->
            val t = f.mark("T")
            val u = f.mark("U")
            val i = policy(f)
            val ref = caller(f)
            val actor = core(f)
            for (action in order) when (action) {
                "requestT" -> request(actor, i, RequestKind.Mark(t))
                "requestU" -> request(actor, i, RequestKind.Mark(u))
                "linkT" -> f.admit(actor, ref, Layer.NORMAL, f.fact(mark = MarkSlot.Concrete(t)))
                else -> f.admit(actor, ref, Layer.NORMAL, f.fact(mark = MarkSlot.Concrete(u)))
            }
            assertEquals(setOf(i), f.initials(actor), "No answer precedes the crossed-arrival barrier")
            barrier(f, actor)
            val at = initial(f, x, emptyList(), Tail.EXACT, t)
            val au = initial(f, x, emptyList(), Tail.EXACT, u)
            assertEquals(setOf(i, at, au), f.initials(actor), order.toString())
            assertTrue(climbs(f).isEmpty())
            f.drain(actor)
            assertEquals(setOf(at, au), f.processed.map { it.premise }.filter { it !== i }.toSet(), order.toString())
        }
    }

    @Test
    fun `an answered request stays live for later deeper answers and new caller sites`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val t = f.mark("T")
            val a = f.field("a")
            val i = policy(f)
            val request = RequestKind.Mark(t)
            val actor = core(f)
            request(actor, i, request)
            f.admit(actor, caller(f, node = 0), Layer.NORMAL, f.fact(mark = MarkSlot.Concrete(t)))
            f.admit(actor, caller(f, node = 1), Layer.NORMAL, f.fact(path = listOf(a), mark = MarkSlot.Concrete(t)))
            barrier(f, actor)
            val exact = initial(f, x, emptyList(), Tail.EXACT, t)
            val chain = initial(f, x, emptyList(), Tail.STAR, t)
            assertEquals(setOf(i, exact, chain), f.initials(actor))
            val abstract = f.fact(tail = Tail.STAR, mark = MarkSlot.STAR)
            val first = caller(f, "up", node = 0, flow = true)
            val second = caller(f, "up", node = 1, flow = true)
            f.admit(actor, first, Layer.NORMAL, abstract)
            f.admit(actor, second, Layer.NORMAL, abstract)
            barrier(f, actor)
            val target = ReferenceRequestDelivery(first.caller, first.premise as InitialAp, request)
            assertEquals(listOf(target, target), climbs(f),
                "Separate new-input N2 visits each deliver the same full target request")
            assertEquals(setOf(target), climbs(f).toSet(), "The projected request denotation is unchanged")
            assertEquals(setOf(first, second), f.links(actor).asSequence().map { it.caller }.filter { it.caller == first.caller }.toSet())
            f.admit(actor, second, Layer.NORMAL, abstract)
            barrier(f, actor)
            assertEquals(2, climbs(f).size, "Repeated admission of the same full subscription adds no work")
        }
    }

    @Test
    fun `a nonstatic must link with exclusions answers only with a demoted chain premise`(): Unit {
        for (firstRequest in listOf(true, false)) for (pathBelow in listOf(false, true)) {
            MethodAnalysisFixtures().use { f ->
                val t = f.mark("T")
                val a = f.field("a")
                val excluded = f.field("excluded")
                val e = ExclusionSet.of(excluded)
                val added = f.fact(path = if (pathBelow) listOf(a) else emptyList(), tail = Tail.ANY_TAINT,
                    mark = MarkSlot.Concrete(t), exclusion = e)
                val actor = core(f)
                val i = policy(f)
                arrive(f, actor, firstRequest, i, RequestKind.Mark(t), caller(f), added)
                val answer = initial(f, x, emptyList(), Tail.STAR, t)
                assertEquals(setOf(i, answer), f.initials(actor))
                assertTrue(f.initials(actor).none { it.tail == Tail.ANY_TAINT })
                val start = assertIs<TaintTree>(actor.edges.edgesAt(f.inst(0), answer).single().second)
                assertEquals(Layer.DEMAND, start.layer)
                assertEquals(ExclusionSet.Empty, start.exclusion)
                assertEquals(f.manager.markSetOf(t), start.root.leaf.any)
                val link = f.links(actor).asSequence().single()
                assertEquals(e, link.addedFact.exclusion)
                assertEquals(Tail.ANY_TAINT, link.addedFact.fact.tail)
            }
        }
    }

    @Test
    fun `static mark answers preserve the full added path and demote must exclusions`(): Unit {
        for (firstRequest in listOf(true, false)) for (tail in listOf(Tail.EXACT, Tail.ANY_TAINT, Tail.ANY)) {
            MethodAnalysisFixtures().use { f ->
                val t = f.mark("T")
                val cls = f.manager.accessors.index(ClassStaticAccessor("State"))
                val a = f.field("a")
                val b = f.field("b")
                val e = if (tail == Tail.ANY_TAINT) ExclusionSet.of(b) else ExclusionSet.Empty
                val added = f.fact(static, listOf(cls, a), tail, MarkSlot.Concrete(t), e,
                    if (tail == Tail.ANY) Layer.DEMAND else Layer.NORMAL)
                val i = policy(f, static, listOf(cls, a))
                f.declareStaticDomain(listOf(cls, a))
                val actor = core(f)
                arrive(f, actor, firstRequest, i, RequestKind.Mark(t), caller(f), added)
                val expectedTail = if (tail == Tail.EXACT) Tail.EXACT else Tail.STAR
                val answer = initial(f, static, listOf(cls, a), expectedTail, t)
                assertEquals(setOf(i,answer), f.initials(actor))
                assertEquals(ExclusionSet.Empty, answer.exclusion)
                assertEquals(listOf(cls, a), answer.path?.toList())
                assertTrue(climbs(f).isEmpty())
                val start = assertIs<TaintTree>(f.processed.first { it.node == f.inst(0) && it.premise == answer }.facts)
                assertEquals(Layer.DEMAND, start.layer)
                assertEquals(ExclusionSet.Empty, start.exclusion)
            }
        }
    }

    @Test
    fun `a static mark link above the request gives the chain answer at the request path`(): Unit {
        for (firstRequest in listOf(true, false)) MethodAnalysisFixtures().use { f ->
            val t = f.mark("T")
            val cls = f.manager.accessors.index(ClassStaticAccessor("State"))
            val a = f.field("a")
            val excluded = f.field("excluded")
            val added = f.fact(static, listOf(cls), Tail.ANY_TAINT, MarkSlot.Concrete(t), ExclusionSet.of(excluded))
            val i = policy(f, static, listOf(cls, a))
            f.declareStaticDomain(listOf(cls, a))
            val actor = core(f)
            arrive(f, actor, firstRequest, i, RequestKind.Mark(t), caller(f), added)
            val answer = initial(f, static, listOf(cls, a), Tail.STAR, t)
            assertEquals(setOf(i,answer), f.initials(actor))
            assertEquals(ExclusionSet.Empty, answer.exclusion)
            val start = assertIs<TaintTree>(f.processed.first { it.node == f.inst(0) && it.premise == answer }.facts)
            assertEquals(Layer.DEMAND, start.layer)
            assertTrue(climbs(f).isEmpty())
        }
    }

    @Test
    fun `historical scalar Position alone retains its full path and at-or-below reference rule`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("State"))
            val a = f.field("a")
            val b = f.field("b")
            val path = listOf(cls, a)
            val query = RequestKind.Position(checkNotNull(f.manager.path(path)))
            val root = policy(f, static)
            val ref = caller(f)
            for (deeper in listOf(false, true)) {
                val added = f.pattern(static, path + if (deeper) listOf(b) else emptyList())
                val answer = assertIs<ReferenceRequestAction.Answer>(f.ops.referenceRequestAction(root, query, added, ref))
                assertEquals(policy(f, static, path), answer.initial)
            }
        }
    }

    @Test
    fun `historical scalar Position above-query climbs only through a static original premise`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("State"))
            val path = listOf(cls, f.field("a"))
            val query = RequestKind.Position(checkNotNull(f.manager.path(path)))
            for (staticCaller in listOf(false, true)) {
                val ref = caller(f, base = if (staticCaller) static else x, flow = true)
                val action = f.ops.referenceRequestAction(policy(f, static), query,
                    f.pattern(static, listOf(cls), Tail.STAR, MarkSlot.STAR), ref)
                if (staticCaller) assertIs<ReferenceRequestAction.Climb>(action)
                else assertIs<ReferenceRequestAction.None>(action)
            }
        }
    }

    @Test
    fun `historical Position exclusion rule is distinct from the F78 exclusion-insensitive mark matcher`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("State"))
            val field = f.field("a")
            val query = RequestKind.Position(checkNotNull(f.manager.path(listOf(cls, field))))
            val input = f.pattern(static, listOf(cls), Tail.ANY_TAINT,
                MarkSlot.Concrete(f.mark("T")), ExclusionSet.of(field))
            assertIs<ReferenceRequestAction.None>(f.ops.referenceRequestAction(policy(f, static), query, input, caller(f)))
            assertTrue(slotMatches(Run1Slot.StaticField(cls, field), f.fact(static, listOf(cls),
                Tail.ANY_TAINT, MarkSlot.Concrete(f.mark("T")), ExclusionSet.of(field))))
        }
    }

    @Test
    fun `actor contexts have separate standing joins even when they share canonical premises and added objects`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val a = core(f, key = f.store.key("callee-A"))
            val b = core(f, key = f.store.key("callee-B"))
            assertNotEquals(a.key, b.key)
            val i = policy(f)
            val kind = RequestKind.Mark(f.mark("T"))
            val added = f.fact(tail = Tail.STAR, mark = MarkSlot.STAR)
            val ref = caller(f, "shared-caller", flow = true)
            request(a, i, kind)
            f.admit(b, ref, Layer.NORMAL, added)
            assertTrue(climbs(f).isEmpty(), "A's request must not join B's link")
            assertTrue(a.initials.all().isEmpty())
            assertEquals(setOf(i), b.initials.all().toSet())
            f.admit(a, ref, Layer.NORMAL, added)
            barrier(f, a)
            val climb = ReferenceRequestDelivery(ref.caller, ref.premise as InitialAp, kind)
            assertEquals(listOf(climb), climbs(f))
            request(b, i, kind)
            barrier(f, b)
            assertEquals(listOf(climb, climb), climbs(f), "Both distinct actor stores run their own first join")
            request(a, i, kind)
            request(b, i, kind)
            assertEquals(2, climbs(f).size)
            assertEquals(1, f.links(a).asSequence().count())
            assertEquals(1, f.links(b).asSequence().count())
        }
    }

    @Test
    fun `cancelled request insertion is rejected before mutation and activates to its real first answer`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val actor = core(f)
            val t = f.mark("T")
            val i = policy(f)
            val answer = initial(f, x, emptyList(), Tail.EXACT, t)
            f.admit(actor, caller(f), Layer.NORMAL, f.fact(mark = MarkSlot.Concrete(t)))
            f.drain(actor)
            assertEquals(setOf(i), f.initials(actor))
            f.cancellation.cancel()
            assertFailsWith<Cancellation.Cancelled> { request(actor, i, RequestKind.Mark(t)) }
            assertEquals(setOf(i), f.initials(actor))
            f.cancellation.activate()
            request(actor, i, RequestKind.Mark(t))
            assertEquals(setOf(i), f.initials(actor), "Reactivate admits the request before answering")
            barrier(f, actor)
            assertEquals(setOf(i, answer), f.initials(actor), "No cancelled insertion may suppress the legitimate first request")
            f.drain(actor)
            assertTrue(f.processed.any { it.premise === answer })
            assertTrue(climbs(f).isEmpty())
        }
    }

    @Test
    fun `restricted forward and backward services never answer or climb and reject request entry`(): Unit {
        for (index in listOf(2, 3)) MethodAnalysisFixtures().use { f ->
            val actor = core(f, index = index)
            val i = policy(f)
            val t = f.mark("T")
            assertFails { request(actor, i, RequestKind.Mark(t)) }
            f.admit(actor, caller(f, flow = true), Layer.NORMAL, f.fact(tail = Tail.STAR, mark = MarkSlot.STAR))
            assertEquals(setOf(i), f.initials(actor),
                "An actual later FLOW query starts without any old guide, but emits no mark requests")
            assertTrue(climbs(f).isEmpty())
            if (index == 2) assertNull(actor.localServices.conjunctions)
            else assertNotNull(actor.localServices.conjunctions)
        }
    }
}
