package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.fixtures.ScalarPositionLocalServices
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
        f.core(index = index, key = key, localFactory = MethodLocalServicesFactory { ScalarPositionLocalServices(it, f.positionDeliveries::add) })
    private fun caller(f: MethodAnalysisFixtures, context: String = "caller", base: AccessPathBase = x,
                       path: List<Int> = emptyList(), node: Int = 1, flow: Boolean = false): CallerRef =
        CallerRef(f.store.key(context), if (flow) policy(f, base, path) else f.manager.zero, Layer.NORMAL, f.inst(node))
    private fun arrive(f: MethodAnalysisFixtures, core: RunMethodAnalyzer, firstRequest: Boolean, premise: InitialAp, kind: RequestKind,
                       ref: CallerRef, added: Facts) {
        if (firstRequest) request(core, premise, kind)
        f.admit(core, ref, added.layer, added)
        if (!firstRequest) request(core, premise, kind)
        if (kind is RequestKind.Mark) {
            assertTrue(core.initials.all().none { !it.isZero && it.mark is MarkSlot.Concrete },
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
            is RequestKind.Position -> assertIs<ScalarPositionLocalServices>(actor.localServices).addPositionRequest(initial, kind)
        }
    }
    private fun climbs(f: MethodAnalysisFixtures): List<ReferenceRequestDelivery> = buildList {
        addAll(f.positionDeliveries)
        for (event in f.events) if (event is RunEvent.MarkRequestsIn) for (i in 0 until event.marks.size)
            add(ReferenceRequestDelivery(event.method, event.premise, RequestKind.Mark(event.marks.markAt(i))))
    }

    @Test
    fun `a callback request for a later bucket joins once through its own new-request path`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("Box"))
            val field = f.field("f")
            val t = f.mark("T")
            val u = f.mark("U")
            val root = policy(f, static)
            val atClass = policy(f, static, listOf(cls))
            val atField = policy(f, static, listOf(cls, field))
            val actor = core(f)
            actor.addInitial(root)
            request(actor, root, RequestKind.Position(checkNotNull(f.manager.path(listOf(cls)))))
            request(actor, atField, RequestKind.Mark(u))
            var inserted = false
            f.onCut = { point, _ ->
                if (point == Cut.ENTRY_RULES && !inserted) {
                    inserted = true
                    request(actor, atField, RequestKind.Mark(t))
                }
            }
            val a = caller(f, "A", static, flow = true)
            val added = f.fact(base = static, path = listOf(cls, field), tail = Tail.STAR, mark = MarkSlot.STAR)
            f.admit(actor, a, Layer.NORMAL, added)
            assertTrue(inserted)
            assertTrue(climbs(f).isEmpty(), "The position callback is prompt; its mark requests are deferred")
            barrier(f, actor)
            assertEquals(listOf(ReferenceRequestDelivery(a.caller, a.premise as InitialAp, RequestKind.Mark(t)),
                ReferenceRequestDelivery(a.caller, a.premise as InitialAp, RequestKind.Mark(u))), climbs(f))
            assertTrue(atClass in actor.initials.all().toSet())
            f.onCut = null
            val b = caller(f, "B", static, flow = true)
            f.admit(actor, b, Layer.NORMAL, added)
            barrier(f, actor)
            assertEquals(setOf(ReferenceRequestDelivery(b.caller, b.premise as InitialAp, RequestKind.Mark(u)),
                ReferenceRequestDelivery(b.caller, b.premise as InitialAp, RequestKind.Mark(t))), climbs(f).takeLast(2).toSet())
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
            assertEquals(setOf(i), actor.initials.all().toSet())
            assertTrue(climbs(f).isEmpty())
            f.admit(actor, caller(f, "concrete"), Layer.NORMAL, f.fact(mark = MarkSlot.Concrete(t)))
            assertEquals(setOf(i), actor.initials.all().toSet(), "The late mark answer is deferred")
            barrier(f, actor)
            assertEquals(setOf(i, initial(f, x, emptyList(), Tail.EXACT, t)), actor.initials.all().toSet())
            assertTrue(climbs(f).isEmpty())
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
                assertEquals(setOf(i, expected), actor.initials.all().toSet(), "$firstRequest/$below")
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
                assertEquals(setOf(i), actor.initials.all().toSet())
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
            assertEquals(1L, actor.demandLayerEdges,
                "Incoming DEMAND is recorded once per actor; both full caller references were checked above")
            request(actor, i, request)
            f.admit(actor, normalCaller, Layer.DEMAND, added)
            f.admit(actor, demandCaller, Layer.DEMAND, added)
            assertEquals(2, climbs(f).size)
            assertEquals(1L, actor.demandLayerEdges)
        }
    }

    @Test
    fun `wrong concrete marks excluded abstract marks and zero links neither answer nor climb`(): Unit {
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
                assertTrue(climbs(f).isEmpty(), "$firstRequest/$shape")
                assertEquals(setOf(if (shape == "zero") f.manager.zero else i), actor.initials.all().toSet())
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
            assertEquals(setOf(i), actor.initials.all().toSet(), "No answer precedes the crossed-arrival barrier")
            barrier(f, actor)
            val at = initial(f, x, emptyList(), Tail.EXACT, t)
            val au = initial(f, x, emptyList(), Tail.EXACT, u)
            assertEquals(setOf(i, at, au), actor.initials.all().toSet(), order.toString())
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
            assertEquals(setOf(i, exact, chain), actor.initials.all().toSet())
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
                assertEquals(setOf(i, answer), actor.initials.all().toSet())
                assertTrue(actor.initials.all().none { it.tail == Tail.ANY_TAINT })
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
                val i = policy(f, static, listOf(cls))
                val actor = core(f)
                arrive(f, actor, firstRequest, i, RequestKind.Mark(t), caller(f), added)
                val expectedTail = if (tail == Tail.ANY_TAINT) Tail.ANY else tail
                val answer = initial(f, static, listOf(cls, a), expectedTail, t)
                assertEquals(setOf(policy(f, static), answer), actor.initials.all().toSet())
                assertEquals(ExclusionSet.Empty, answer.exclusion)
                assertEquals(listOf(cls, a), answer.path?.toList())
                assertTrue(climbs(f).isEmpty())
                val start = assertIs<TaintTree>(actor.edges.edgesAt(f.inst(0), answer).single().second)
                assertEquals(if (tail == Tail.EXACT) Layer.NORMAL else Layer.DEMAND, start.layer)
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
            val actor = core(f)
            arrive(f, actor, firstRequest, i, RequestKind.Mark(t), caller(f), added)
            val answer = initial(f, static, listOf(cls, a), Tail.STAR, t)
            assertEquals(setOf(policy(f, static), answer), actor.initials.all().toSet())
            assertEquals(ExclusionSet.Empty, answer.exclusion)
            val start = assertIs<TaintTree>(actor.edges.edgesAt(f.inst(0), answer).single().second)
            assertEquals(Layer.DEMAND, start.layer)
            assertTrue(climbs(f).isEmpty())
        }
    }

    @Test
    fun `a position request is answered at or below its full static path in either arrival order`(): Unit {
        for (firstRequest in listOf(true, false)) for (deeper in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("State"))
            val a = f.field("a")
            val b = f.field("b")
            val path = listOf(cls, a)
            val kind = RequestKind.Position(checkNotNull(f.manager.path(path)))
            val i = policy(f, static)
            val added = f.fact(static, path + if (deeper) listOf(b) else emptyList(), Tail.EXACT,
                MarkSlot.Concrete(f.mark("T")))
            val actor = f.core(L = 2, localFactory = MethodLocalServicesFactory { ScalarPositionLocalServices(it, f.positionDeliveries::add) })
            arrive(f, actor, firstRequest, i, kind, caller(f), added)
            val answer = policy(f, static, path)
            assertEquals(setOf(i, answer), actor.initials.all().toSet())
            val start = assertIs<FlowTree>(actor.edges.edgesAt(f.inst(0), answer).single().second)
            assertEquals(Layer.NORMAL, start.layer)
            assertEquals(ExclusionSet.Empty, start.exclusion)
            assertEquals(MarkSet.EMPTY, start.markExclusion)
            assertTrue(climbs(f).isEmpty())
        }
    }

    @Test
    fun `position links above the query climb only through a static caller premise`(): Unit {
        for (firstRequest in listOf(true, false)) for (staticCaller in listOf(true, false)) MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("State"))
            val a = f.field("a")
            val path = listOf(cls, a)
            val kind = RequestKind.Position(checkNotNull(f.manager.path(path)))
            val i = policy(f, static)
            val added = f.fact(static, listOf(cls), Tail.STAR, MarkSlot.STAR)
            val ref = caller(f, base = if (staticCaller) static else x, flow = true)
            val actor = core(f)
            arrive(f, actor, firstRequest, i, kind, ref, added)
            assertEquals(if (staticCaller) listOf(ReferenceRequestDelivery(ref.caller, ref.premise as InitialAp, kind))
                else emptyList(), climbs(f), "$firstRequest/$staticCaller")
            assertEquals(setOf(i), actor.initials.all().toSet())
        }
    }

    @Test
    fun `first accessor exclusions and exact ancestors block mark and position joins in both arrival orders`(): Unit {
        for (firstRequest in listOf(true, false)) for (position in listOf(true, false)) {
            for (shape in listOf("must-excluded", "flow-excluded", "exact-ancestor")) MethodAnalysisFixtures().use { f ->
                val t = f.mark("T")
                val cls = f.manager.accessors.index(ClassStaticAccessor("State"))
                val a = f.field("a")
                val query = listOf(cls, a)
                val i = policy(f, static, if (position) emptyList() else query)
                val kind = if (position) RequestKind.Position(checkNotNull(f.manager.path(query))) else RequestKind.Mark(t)
                val added = when (shape) {
                    "must-excluded" -> f.fact(static, listOf(cls), Tail.ANY_TAINT, MarkSlot.Concrete(t), ExclusionSet.of(a))
                    "flow-excluded" -> f.fact(static, listOf(cls), Tail.STAR, MarkSlot.STAR, ExclusionSet.of(a))
                    else -> f.fact(static, listOf(cls), Tail.EXACT, MarkSlot.Concrete(t))
                }
                val actor = core(f)
                val ref = caller(f, base = static, flow = shape == "flow-excluded")
                arrive(f, actor, firstRequest, i, kind, ref, added)
                assertEquals(setOf(policy(f, static)), actor.initials.all().toSet(), "$firstRequest/$position/$shape")
                assertTrue(climbs(f).isEmpty(), "$firstRequest/$position/$shape")
            }
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
            assertEquals(setOf(i), actor.initials.all().toSet())
            f.cancellation.cancel()
            assertFailsWith<Cancellation.Cancelled> { request(actor, i, RequestKind.Mark(t)) }
            assertEquals(setOf(i), actor.initials.all().toSet())
            f.cancellation.activate()
            request(actor, i, RequestKind.Mark(t))
            assertEquals(setOf(i), actor.initials.all().toSet(), "Reactivate admits the request before answering")
            barrier(f, actor)
            assertEquals(setOf(i, answer), actor.initials.all().toSet(), "No cancelled insertion may suppress the legitimate first request")
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
            assertTrue(actor.initials.all().isEmpty())
            assertTrue(climbs(f).isEmpty())
            if (index == 2) assertNull(actor.localServices.conjunctions)
            else assertNotNull(actor.localServices.conjunctions)
        }
    }
}
