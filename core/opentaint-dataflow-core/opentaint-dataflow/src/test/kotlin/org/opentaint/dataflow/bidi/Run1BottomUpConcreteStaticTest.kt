package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import kotlin.test.*

/** The reference expands the ordinary static-interest walk. Production keeps
 * source needs and contracts the actual input graph. All nine semantic channels
 * are compared after decoding; no expected output is copied from runtime plans. */
class Run1BottomUpConcreteStaticTest {
    private val argument = AccessPathBase.Argument(0)
    private val returned = AccessPathBase.Return

    private fun summary(vararg edges: PathEdge): StatementSummary = StatementSummary(
        edges.mapTo(linkedSetOf()) { it.from.base }, edges.map(MicroEdge::of), emptyList(), emptyMap())

    private fun call(target: MethodKey, before: List<CleanStep> = emptyList()): CallPlan {
        val bases = linkedSetOf(STATIC, argument, returned)
        return CallPlan(bases, listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN,
                summary(ZERO_KEEP, *bases.map(::keepEdge).toTypedArray())),
            CallStage.Actions(BOUND, ADDED, before),
            CallStage.Callees(ADDED, RETURNED, listOf(target)),
            CallStage.Edges(RETURNED, AFTER, BIND_BACK,
                summary(ZERO_KEEP, *bases.map(::keepEdge).toTypedArray())),
        ), emptyList(), BEFORE, AFTER)
    }

    private fun source(f: FiniteProgramFixtures, owner: MethodKey, facts: List<PathFact>) =
        f.entry(owner, RuleStatement(summary(ZERO_KEEP, *facts.map {
            PathEdge(ZERO_FACT, it, ExclusionSet.Empty)
        }.toTypedArray()), StatementSummary.EMPTY, emptyList()))

    private fun concrete(base: AccessPathBase, path: List<Int>, mark: TaintMark,
                         tail: Tail = Tail.EXACT): PathFact = PathFact(base, path, tail, MarkSlot.Concrete(mark))

    private fun field(f: FiniteProgramFixtures, cls: Int, name: String): List<Int> = listOf(cls, f.field(name))

    private fun check(f: FiniteProgramFixtures, root: MethodKey, fields: List<Int>, marks: List<TaintMark>,
                      policy: SchedulePolicy, sameUnit: Boolean = false): Pair<RunObservation, ProductionSchedule> {
        val config = f.config(roots = listOf(root))
        val expected = NaiveClosure(f, config, semantics = NaiveClosure.Semantics.SELECTED).run()
        val schedule = ProductionSchedule(f, config, policy = policy, sameUnit = sameUnit,
            seed = 41, quantum = 2, duplicateDeliveries = true)
        val actual = schedule.run()
        val facts = (expected.processed + expected.rawSummaries + expected.publications + actual.processed +
            actual.rawSummaries + actual.publications).flatMap { edge -> edge.premise.map { it.fact } + edge.conclusion.fact }
        val universe = BoundedDenotation((facts.map { it.base } + listOf(AccessPathBase.Zero, STATIC, argument,
            returned, AccessPathBase.LocalVar(0))).distinct(),
            (fields + facts.flatMap { it.path } + f.field("outside support")).distinct(),
            (marks + TaintMark.ZERO + f.mark("outside marks")).distinct(), 2)
        try {
            expected.assertEquivalent(actual, universe)
        } catch (failure: AssertionError) {
            throw AssertionError("Concrete static concept mismatch: policy=$policy sameUnit=$sameUnit " +
                "program=${f.describe()} trace=${schedule.trace.takeLast(60)}", failure)
        }
        assertTrue(actual.initials.none { it.initial.fact.tail == Tail.ANY || it.initial.fact.tail == Tail.ANY_TAINT })
        assertTrue(actual.initials.none { it.initial.fact.base == STATIC && it.initial.fact.path.isEmpty() })
        for (edge in actual.processed + actual.rawSummaries + actual.publications) {
            if (edge.conclusion.fact.base == STATIC || edge.premise.any { it.fact.base == STATIC })
                assertTrue(edge.conclusion.demand, "Static original/current data is D before any operation: $edge")
        }
        return actual to schedule
    }

    @Test fun `write bootstrap crosses read-free relay and needs actual nonstatic T`() {
        for (supplied in listOf(false, true)) for (policy in listOf(SchedulePolicy.REPLAY_FIRST, SchedulePolicy.NOTIFY_FIRST))
            FiniteProgramFixtures().use { f ->
                val cls = f.ap.accessors.index(ClassStaticAccessor("Bootstrap"))
                val fp = field(f, cls, "f"); val t = f.mark("T"); val u = f.mark("U")
                val root = f.method("supplier", listOf(listOf(1), listOf(2), emptyList()), 2).key()
                val writer = f.method("A(p)", listOf(listOf(1), listOf(2), emptyList()), 2).key()
                val relay = f.method("H", listOf(listOf(1), emptyList()), 1).key()
                val consumer = f.method("B reads then checks", listOf(listOf(1), listOf(2), emptyList()), 2).key()
                source(f, root, listOf(concrete(argument, emptyList(), if (supplied) t else u)))
                f.call(root, 1, call(writer))
                f.statement(writer, 0, MicroEdgeBuilder().apply {
                    write(STATIC, fp, weak = false, values = listOf(argument), aliasPaths = emptyList())
                }.build())
                f.call(writer, 1, call(relay)); f.call(relay, 0, call(consumer))
                val local = AccessPathBase.LocalVar(0)
                f.statement(consumer, 0, MicroEdgeBuilder().apply { read(local, STATIC, fp) }.build())
                val result = concrete(returned, emptyList(), t)
                f.statement(consumer, 1, summary(ZERO_KEEP,
                    PathEdge(concrete(local, emptyList(), t), result, ExclusionSet.Empty)))
                val (actual, _) = check(f, root, fp, listOf(t, u), policy, sameUnit = policy == SchedulePolicy.NOTIFY_FIRST)
                val base = f.pattern(argument, tail = Tail.STAR, mark = MarkSlot.STAR)
                assertContains(actual.requests, ObservedRequest(writer, base, RequestKind.Mark(t)),
                    "Static interest must reach the original nonstatic write boundary")
                val observed = actual.rawSummaries.any { it.method == consumer && it.conclusion.fact == result }
                assertEquals(supplied, observed, "Interest is not an actual T supplier")
                assertEquals(supplied, actual.initials.any { it.method == consumer &&
                    it.initial.fact == concrete(STATIC, fp, t) })
                if (!supplied) assertTrue(actual.processed.none { it.conclusion.fact.mark == MarkSlot.Concrete(t) },
                    "A check/request without any actual T cannot manufacture a T fact")
            }
    }

    @Test fun `changed field routes need to original write input across late graph orders`() {
        for (policy in listOf(SchedulePolicy.FIFO, SchedulePolicy.REPLAY_FIRST, SchedulePolicy.NOTIFY_FIRST, SchedulePolicy.SEEDED))
            FiniteProgramFixtures().use { f ->
                val cls = f.ap.accessors.index(ClassStaticAccessor("Changed"))
                val fp = field(f, cls, "f"); val gp = field(f, cls, "g"); val t = f.mark("T")
                val root = f.method("supply", listOf(listOf(1), listOf(2), emptyList()), 2).key()
                val writer = f.method("write g", listOf(listOf(1), listOf(2), emptyList()), 2).key()
                val move = f.method("g to f", listOf(listOf(1), listOf(2), emptyList()), 2).key()
                val leaf = f.method("check f", listOf(listOf(1), emptyList()), 1).key()
                source(f, root, listOf(concrete(argument, emptyList(), t)))
                f.call(root, 1, call(writer))
                f.statement(writer, 0, MicroEdgeBuilder().apply {
                    write(STATIC, gp, weak = false, values = listOf(argument), aliasPaths = emptyList())
                }.build())
                f.call(writer, 1, call(move))
                f.statement(move, 0, summary(ZERO_KEEP, starEdge(STATIC, gp, STATIC, fp)))
                f.call(move, 1, call(leaf))
                val target = concrete(returned, emptyList(), t)
                f.statement(leaf, 0, summary(ZERO_KEEP, PathEdge(concrete(STATIC, fp, t), target, ExclusionSet.Empty)))
                val (actual, _) = check(f, root, fp + gp, listOf(t), policy)
                assertContains(actual.requests, ObservedRequest(writer,
                    f.pattern(argument, tail = Tail.STAR, mark = MarkSlot.STAR), RequestKind.Mark(t)))
                assertTrue(actual.links.any { it.caller == move && it.callee == leaf &&
                    it.premise.singleOrNull()?.fact?.path == gp && it.added.fact.path == fp })
                assertTrue(actual.rawSummaries.any { it.method == leaf && it.conclusion.fact == target })
            }
    }

    @Test fun `all actual static marks enter without local need and remain diagonal`() {
        FiniteProgramFixtures().use { f ->
            val cls = f.ap.accessors.index(ClassStaticAccessor("AllActual"))
            val fp = field(f, cls, "f"); val gp = field(f, cls, "g")
            val t = f.mark("T"); val u = f.mark("U")
            val root = f.method("concrete sources", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val relay = f.method("read-free concrete relay", listOf(listOf(1), emptyList()), 1).key()
            source(f, root, listOf(concrete(STATIC, fp, t), concrete(STATIC, gp, u)))
            f.call(root, 1, call(relay))
            val (actual, _) = check(f, root, fp + gp, listOf(t, u), SchedulePolicy.REPLAY_FIRST)
            val admitted = actual.initials.filter { it.method == relay && it.initial.fact.mark is MarkSlot.Concrete &&
                it.initial.fact.base == STATIC }.map { it.initial.fact }.toSet()
            assertEquals(setOf(concrete(STATIC, fp, t), concrete(STATIC, gp, u)), admitted)
            assertTrue(actual.requests.none { it.method == relay }, "Actual T admission does not require a local check")
        }
    }

    @Test fun `chain carrier publishes ordinary ANY effect at real return`() {
        FiniteProgramFixtures().use { f ->
            val cls = f.ap.accessors.index(ClassStaticAccessor("Chain"))
            val fp = field(f, cls, "f"); val t = f.mark("T")
            val root = f.method("broad supplier", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val relay = f.method("returning chain relay", listOf(listOf(1), emptyList()), 1).key()
            source(f, root, listOf(concrete(STATIC, fp, t, Tail.ANY)))
            f.call(root, 1, call(relay))
            val (actual, _) = check(f, root, fp, listOf(t), SchedulePolicy.NOTIFY_FIRST)
            val initial = f.pattern(STATIC, fp, Tail.STAR, MarkSlot.Concrete(t))
            assertContains(actual.initials, ObservedInitial(relay, initial))
            assertTrue(actual.publications.any { it.method == relay && it.premise == setOf(initial) &&
                it.conclusion.fact == concrete(STATIC, fp, t, Tail.ANY) && it.conclusion.demand },
                "STAR-to-ANY is an effect; the root default is only correlated identity")
        }
    }

    @Test fun `strong kill before call blocks actual T despite an existing interest`() {
        FiniteProgramFixtures().use { f ->
            val cls = f.ap.accessors.index(ClassStaticAccessor("Kill"))
            val fp = field(f, cls, "f"); val t = f.mark("T")
            val root = f.method("kill supplier", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val writer = f.method("kill before call", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val leaf = f.method("killed target", listOf(listOf(1), emptyList()), 1).key()
            source(f, root, listOf(concrete(STATIC, fp, t)))
            f.call(root, 1, call(writer))
            f.statement(writer, 0, MicroEdgeBuilder().apply {
                write(STATIC, fp, weak = false, values = emptyList(), aliasPaths = emptyList())
            }.build())
            f.call(writer, 1, call(leaf))
            f.statement(leaf, 0, summary(ZERO_KEEP, PathEdge(concrete(STATIC, fp, t),
                concrete(returned, emptyList(), t), ExclusionSet.Empty)))
            val (actual, _) = check(f, root, fp, listOf(t), SchedulePolicy.SEEDED)
            assertTrue(actual.initials.none { it.method == leaf && it.initial.fact == concrete(STATIC, fp, t) })
            assertTrue(actual.rawSummaries.none { it.method == leaf && it.conclusion.fact.base == returned &&
                it.conclusion.fact.mark == MarkSlot.Concrete(t) })
        }
    }

    @Test fun `direct class exact stays separate from field supply and starts D`() {
        FiniteProgramFixtures().use { f ->
            val cls = f.ap.accessors.index(ClassStaticAccessor("DirectClass"))
            val fp = field(f, cls, "f"); val t = f.mark("T")
            f.declareStaticDomain(fp)
            val root = f.method("class source", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val callee = f.method("class consumer", listOf(listOf(1), emptyList()), 1).key()
            source(f, root, listOf(concrete(STATIC, listOf(cls), t)))
            f.call(root, 1, call(callee))
            f.statement(callee, 0, summary(ZERO_KEEP, PathEdge(concrete(STATIC, listOf(cls), t),
                concrete(returned, emptyList(), t), ExclusionSet.Empty)))
            val (actual, _) = check(f, root, fp, listOf(t), SchedulePolicy.REPLAY_FIRST)
            assertContains(actual.initials, ObservedInitial(callee, f.pattern(STATIC, listOf(cls), Tail.EXACT, MarkSlot.Concrete(t))))
            assertTrue(actual.initials.none { it.method == callee && it.initial.fact.base == STATIC &&
                it.initial.fact.path == fp }, "An exact class point cannot supply a deeper field")
            assertTrue(actual.rawSummaries.any { it.method == callee && it.conclusion.fact.base == returned && it.conclusion.demand })
        }
    }

    @Test fun `mark conversion keeps the actual old mark in its full caller origin`() {
        FiniteProgramFixtures().use { f ->
            val cls = f.ap.accessors.index(ClassStaticAccessor("Conversion"))
            val fp = field(f, cls, "f"); val t = f.mark("T"); val u = f.mark("U")
            val root = f.method("T source", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val convert = f.method("T to U", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val leaf = f.method("U consumer", listOf(listOf(1), emptyList()), 1).key()
            source(f, root, listOf(concrete(STATIC, fp, t)))
            f.call(root, 1, call(convert))
            f.statement(convert, 0, summary(ZERO_KEEP,
                PathEdge(concrete(STATIC, fp, t), concrete(STATIC, fp, u), ExclusionSet.Empty)))
            f.call(convert, 1, call(leaf))
            val target = concrete(returned, emptyList(), u)
            f.statement(leaf, 0, summary(ZERO_KEEP, PathEdge(concrete(STATIC, fp, u), target, ExclusionSet.Empty)))
            val (actual, _) = check(f, root, fp, listOf(t, u), SchedulePolicy.SEEDED)
            assertTrue(actual.links.any { it.caller == convert && it.callee == leaf &&
                it.premise == setOf(f.pattern(STATIC, fp, Tail.EXACT, MarkSlot.Concrete(t))) &&
                it.added.fact == concrete(STATIC, fp, u) }, "T-to-U is a supported effect, not diagonal identity")
            assertTrue(actual.rawSummaries.any { it.method == leaf && it.conclusion.fact == target })
        }
    }

    @Test fun `conditional ND summary cannot join concrete carriers from different call ports`() {
        for (bothAtOnePort in listOf(false, true)) FiniteProgramFixtures().use { f ->
            val cls = f.ap.accessors.index(ClassStaticAccessor("PortJoin"))
            val fp = field(f, cls, "f"); val gp = field(f, cls, "g")
            val t = f.mark("T"); val u = f.mark("U"); val v = f.mark("V")
            val root = f.method("two port supplier", listOf(listOf(1), listOf(2), listOf(3), emptyList()), 3).key()
            val left = f.method("left port", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val right = f.method("right port", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val join = f.method("same callee", listOf(listOf(1), emptyList()), 1).key()
            source(f, root, listOf(concrete(STATIC, fp, t), concrete(STATIC, gp, u)))
            f.call(root, 1, call(left)); f.call(root, 2, call(right))
            if (!bothAtOnePort) f.statement(left, 0, MicroEdgeBuilder().apply {
                write(STATIC, gp, weak = false, values = emptyList(), aliasPaths = emptyList())
            }.build())
            f.statement(right, 0, MicroEdgeBuilder().apply {
                write(STATIC, fp, weak = false, values = emptyList(), aliasPaths = emptyList())
            }.build())
            f.call(left, 1, call(join)); f.call(right, 1, call(join))
            val literals = listOf(f.pattern(STATIC, fp, mark = MarkSlot.Concrete(t)),
                f.pattern(STATIC, gp, mark = MarkSlot.Concrete(u)))
            val target = concrete(returned, emptyList(), v)
            f.exit(join, 1, ExitRules(RuleStatement(StatementSummary(emptySet(), emptyList(),
                listOf(ConjunctiveEdge(literals, target)), emptyMap()), StatementSummary.EMPTY, emptyList()),
                false, emptySet()))
            val (actual, _) = check(f, root, fp + gp, listOf(t, u, v), SchedulePolicy.SEEDED)
            assertTrue(actual.publications.filter { it.method == join && it.conclusion.fact == target }
                .all { it.premise == literals.toSet() }, "The full conditional premise must remain attached")
            assertEquals(bothAtOnePort,
                actual.rawSummaries.any { it.method == left && it.conclusion.fact == target })
            assertTrue(actual.rawSummaries.none { it.method == right && it.conclusion.fact == target },
                "The right call cannot borrow f/T from the other port")
        }
    }

    @Test fun `plain source stage preserves its false identity header in a concrete call port`() {
        FiniteProgramFixtures().use { f ->
            val cls = f.ap.accessors.index(ClassStaticAccessor("StageHeader"))
            val fp = field(f, cls, "f"); val t = f.mark("T")
            val root = f.method("header source", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val relay = f.method("source stage relay", listOf(listOf(1), emptyList()), 1).key()
            val leaf = f.method("header target", listOf(listOf(1), emptyList()), 1).key()
            source(f, root, listOf(concrete(STATIC, fp, t)))
            f.call(root, 1, call(relay))
            f.call(relay, 0, CallPlan(setOf(STATIC), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(STATIC), ZERO_KEEP)),
                CallStage.Edges(BOUND, ADDED, SOURCES, summary(keepEdge(STATIC), ZERO_KEEP)),
                CallStage.Callees(ADDED, RETURNED, listOf(leaf)),
                CallStage.Edges(RETURNED, AFTER, BIND_BACK, summary(keepEdge(STATIC), ZERO_KEEP)),
            ), emptyList(), BEFORE, AFTER))
            val (_, schedule) = check(f, root, fp, listOf(t), SchedulePolicy.NOTIFY_FIRST)
            val ports = schedule.concreteSubscriptions.filter { it.caller == relay && it.callee == leaf }
            assertTrue(ports.isNotEmpty())
            assertTrue(ports.all { it.identityCandidate == false },
                "SOURCES clears identity classification even for a plain keep; it is part of the shared full port")
        }
    }

    @Test fun `ordinary base request reenters static route and reaches original writer without inventing T`() {
        for (supplied in listOf(false, true))
            for (policy in listOf(SchedulePolicy.REPLAY_FIRST, SchedulePolicy.NOTIFY_FIRST))
                FiniteProgramFixtures().use { f ->
                    val cls = f.ap.accessors.index(ClassStaticAccessor("AlternatingBootstrap"))
                    val fp = field(f, cls, "f"); val t = f.mark("T"); val u = f.mark("U")
                    val root = f.method("actual parameter supplier", listOf(listOf(1), listOf(2), emptyList()), 2).key()
                    val writer = f.method("A writes parameter to f", listOf(listOf(1), listOf(2), emptyList()), 2).key()
                    val reader = f.method("B reads f to argument", listOf(listOf(1), listOf(2), emptyList()), 2).key()
                    val consumer = f.method("C checks argument", listOf(listOf(1), emptyList()), 1).key()
                    source(f, root, listOf(concrete(argument, emptyList(), if (supplied) t else u)))
                    f.call(root, 1, call(writer))
                    f.statement(writer, 0, MicroEdgeBuilder().apply {
                        write(STATIC, fp, weak = false, values = listOf(argument), aliasPaths = emptyList())
                    }.build())
                    // B has no independent argument input: its only path to C's
                    // argument is the read of the actual supplied static field.
                    f.call(writer, 1, CallPlan(setOf(STATIC, returned), listOf(
                        CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(STATIC), ZERO_KEEP)),
                        CallStage.Actions(BOUND, ADDED, emptyList()),
                        CallStage.Callees(ADDED, RETURNED, listOf(reader)),
                        CallStage.Edges(RETURNED, AFTER, BIND_BACK, summary(keepEdge(STATIC), keepEdge(returned), ZERO_KEEP)),
                    ), emptyList(), BEFORE, AFTER))
                    f.statement(reader, 0, MicroEdgeBuilder().apply { read(argument, STATIC, fp) }.build())
                    f.call(reader, 1, call(consumer))
                    val target = concrete(returned, emptyList(), t)
                    f.statement(consumer, 0, summary(ZERO_KEEP,
                        PathEdge(concrete(argument, emptyList(), t), target, ExclusionSet.Empty)))
                    val (actual, _) = check(f, root, fp, listOf(t, u), policy,
                        sameUnit = policy == SchedulePolicy.NOTIFY_FIRST)
                    val base = f.pattern(argument, tail = Tail.STAR, mark = MarkSlot.STAR)
                    val staticOrigin = f.pattern(STATIC, fp, Tail.STAR, MarkSlot.STAR)
                    assertContains(actual.requests, ObservedRequest(consumer, base, RequestKind.Mark(t)))
                    assertContains(actual.requests, ObservedRequest(reader, staticOrigin, RequestKind.Mark(t)),
                        "C's Base request must reenter the source router at B's actual static original")
                    assertContains(actual.requests, ObservedRequest(writer, base, RequestKind.Mark(t)),
                        "The fast static route must reach A's original parameter write boundary")
                    assertTrue(actual.links.filter { it.callee == reader && it.added.fact.base != AccessPathBase.Zero }
                        .all { it.added.fact.base == STATIC }, "No parameter-only shortcut may feed B")
                    assertEquals(supplied, actual.rawSummaries.any { it.method == consumer && it.conclusion.fact == target })
                    assertEquals(supplied, actual.rawSummaries.any { it.method == reader &&
                        it.conclusion.fact == target && it.conclusion.demand })
                    if (!supplied) assertTrue(actual.processed.none { it.conclusion.fact.mark == MarkSlot.Concrete(t) },
                        "The entire alternating need route still requires an actual T supplier")
                }
    }
}
