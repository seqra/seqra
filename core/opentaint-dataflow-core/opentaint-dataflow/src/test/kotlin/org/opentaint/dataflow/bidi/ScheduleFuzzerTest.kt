package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.engine.RunConfig
import org.opentaint.dataflow.bidi.fixtures.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.bidi.store.Record
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import kotlin.test.*

class ScheduleFuzzerTest {
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Argument(1)
    private val returned = AccessPathBase.Return
    private val zero = setOf(ZERO_PATTERN)
    private fun summary(vararg edges: PathEdge): StatementSummary =
        StatementSummary(edges.map { it.from.base }.toSet(), edges.map(MicroEdge::of), emptyList(), emptyMap())

    private fun call(callee: MethodKey, bindBack: StatementSummary = summary(keepEdge(returned), ZERO_KEEP),
                     actions: List<CleanStep> = emptyList()): CallPlan = CallPlan(setOf(x, y, returned), listOf(
        CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(x), ZERO_KEEP)),
        CallStage.Actions(BOUND, ADDED, emptyList()),
        CallStage.Callees(ADDED, RETURNED, listOf(callee)),
        CallStage.Actions(RETURNED, REWRITTEN, actions),
        CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, bindBack),
    ), emptyList(), BEFORE, AFTER)

    private fun rule(id: String): CommonTaintConfigurationSink = object : CommonTaintConfigurationSink {
        override val id = id
        override val meta = object : CommonTaintConfigurationSinkMeta {
            override val message = id
            override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
        }
    }

    private fun compareSeed(f: FiniteProgramFixtures, config: RunConfig, fields: List<Int>, marks: List<TaintMark>,
                            seed: Long, catalogRefinement: Boolean = false, verify: (RunObservation) -> Unit) {
        val expected = try { NaiveClosure(f, config, semantics = NaiveClosure.Semantics.SELECTED).run() }
        catch (failure: Throwable) { throw AssertionError("oracle seed=$seed index=${config.index} program=${f.describe()} trace=[]", failure) }
        fun patterns(o: RunObservation): List<Pattern> = buildList {
            for (edge in o.processed + o.rawSummaries + o.publications + o.selectedDemand) {
                addAll(edge.premise)
                add(Pattern(edge.conclusion.fact, edge.conclusion.exclusion))
            }
            o.initials.forEach { add(it.initial) }
            o.links.forEach { addAll(it.premise); add(it.added) }
            o.requests.forEach { add(it.premise) }
            o.sourceHits.forEach { add(Pattern(it.forward.from, it.forward.fromExclusion)); add(Pattern(it.forward.to, it.forward.toExclusion)) }
            o.witnesses.forEach { witness ->
                witness.edges.forEach { edge -> addAll(edge.premise); edge.facts.forEach { add(Pattern(it.fact, it.exclusion)) } }
                witness.endFacts.forEach { add(Pattern(it, ExclusionSet.Empty)) }
            }
        }
        for (sameUnit in listOf(false, true)) {
            val quantum = 1 + (seed % 2).toInt()
            val duplicate = seed and 2L != 0L
            val schedule = ProductionSchedule(f, config, sameUnit = sameUnit, policy = SchedulePolicy.SEEDED,
                seed = seed, quantum = quantum, duplicateDeliveries = duplicate)
            val actual = schedule.run()
            try {
                val values = patterns(expected) + patterns(actual)
                val positionPaths = (expected.requests + actual.requests).mapNotNull {
                    (it.kind as? RequestKind.Position)?.path?.toIntArray()?.toList()
                }
                val universe = BoundedDenotation(
                    (values.map { it.fact.base } + listOf(AccessPathBase.Zero, x, y, returned, AccessPathBase.ClassStatic)).distinct(),
                    (fields + values.flatMap { it.fact.path + (it.exclusion as? ExclusionSet.Concrete)?.ids?.toList().orEmpty() } +
                        positionPaths.flatten() + f.field("fresh accessor")).distinct(),
                    (marks + values.mapNotNull { (it.fact.mark as? MarkSlot.Concrete)?.mark } + f.mark("fresh mark") + TaintMark.ZERO).distinct(), 2)
                if (catalogRefinement) expected.assertCatalogRefinement(actual, universe, config.roots)
                else actual.assertEquivalent(expected, universe)
                verify(actual)
                if (seed < 2) {
                    val repeat = ProductionSchedule(f, config, sameUnit = sameUnit, policy = SchedulePolicy.SEEDED,
                        seed = seed, quantum = quantum, duplicateDeliveries = duplicate)
                    val repeated = repeat.run()
                    assertEquals(actual, repeated, "A fresh scheduler reproduces the same raw observations")
                    assertEquals(schedule.trace, repeat.trace, "The seed reproduces the exact schedule trace")
                    verify(repeated)
                }
            } catch (failure: AssertionError) {
                throw AssertionError("seed=$seed index=${config.index} sameUnit=$sameUnit quantum=$quantum duplicate=$duplicate " +
                    "program=${f.describe()} trace=${schedule.trace}", failure)
            }
        }
    }

    @Test
    fun `fixed replay and notification orders reach the same finite call closure with duplicate delivery`(): Unit {
        for (policy in listOf(SchedulePolicy.FIFO, SchedulePolicy.REPLAY_FIRST, SchedulePolicy.NOTIFY_FIRST))
            for (sameUnit in listOf(false, true)) for (duplicate in listOf(false, true)) FiniteProgramFixtures().use { f ->
                val main = f.method("fixedCaller", listOf(listOf(1), listOf(2), emptyList()), 2)
                val getter = f.method("fixedGetter", listOf(listOf(1), emptyList()), 1)
                val caller = main.key()
                val callee = getter.key()
                val t = MarkSlot.Concrete(f.mark("T"))
                f.entry(caller, RuleStatement(summary(ZERO_KEEP,
                    PathEdge(ZERO_FACT, PathFact(x, emptyList(), Tail.EXACT, t), ExclusionSet.Empty)),
                    StatementSummary.EMPTY, emptyList()))
                f.call(caller, 1, CallPlan(setOf(x, returned), listOf(
                    CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(x), ZERO_KEEP)),
                    CallStage.Actions(BOUND, ADDED, emptyList()),
                    CallStage.Callees(ADDED, RETURNED, listOf(callee)),
                    CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                    CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(keepEdge(returned), ZERO_KEEP)),
                ), emptyList(), BEFORE, AFTER))
                f.statement(callee, 0, summary(starEdge(x, emptyList(), returned, emptyList())))
                val config = f.config(roots = listOf(caller))
                val expected = NaiveClosure(f, config, semantics = NaiveClosure.Semantics.SELECTED).run()
                val schedule = ProductionSchedule(f, config, sameUnit = sameUnit, policy = policy,
                    quantum = 1, duplicateDeliveries = duplicate)
                val actual = schedule.run()
                val observed = expected.processed + actual.processed + expected.rawSummaries + actual.rawSummaries +
                    expected.publications + actual.publications + expected.selectedDemand + actual.selectedDemand
                val bases = observed.flatMap { it.premise.map { p -> p.fact.base } + it.conclusion.fact.base } +
                    (expected.initials + actual.initials).map { it.initial.fact.base } +
                    (expected.links + actual.links).flatMap { it.premise.map { p -> p.fact.base } + it.added.fact.base } +
                    (expected.witnesses + actual.witnesses).flatMap { it.edges.flatMap { e -> e.premise.map { p -> p.fact.base } + e.facts.map { c -> c.fact.base } } }
                val universe = BoundedDenotation((bases + listOf(AccessPathBase.Zero, x, returned)).distinct(),
                    listOf(f.field("fresh accessor")), (listOf(t.mark, f.mark("fresh mark"), TaintMark.ZERO) + (listOf(t.mark, f.mark("fresh mark"))).map { f.ap.marks.group(f.ap.marks.groupOf(it)) }).distinct(), 2)
                try { actual.assertEquivalent(expected, universe) }
                catch (failure: AssertionError) { throw AssertionError("policy=$policy sameUnit=$sameUnit duplicate=$duplicate program=${f.describe()} trace=${schedule.trace}", failure) }
                assertContains(actual.rawSummaries, ObservedEdge(caller, setOf(ZERO_PATTERN),
                    GroupedScalarMarks(f.ap.marks).conclusion(Conclusion(PathFact(returned, emptyList(), Tail.EXACT, t), ExclusionSet.Empty, false))))
                assertTrue(actual.requests.isEmpty() && actual.sourceHits.isEmpty())
                if (config.run1) assertEquals(actual.publications, actual.selectedDemand)
                else assertTrue(actual.selectedDemand.isEmpty())
            }
    }

    @Test
    fun `seeded getter schedules preserve full source paths in run1 and restricted forward modes`(): Unit {
        for (seed in 0L until 32L) FiniteProgramFixtures().use { f ->
            val main = f.method("seededGetterCaller", listOf(listOf(1), listOf(2), emptyList()), 2)
            val library = f.method("seededGetter", listOf(listOf(1), emptyList()), 1)
            val caller = main.key("caller context")
            val callee = library.key("callee context")
            val a = f.field("a")
            val t = MarkSlot.Concrete(f.mark("T"))
            val source = PathEdge(ZERO_FACT, PathFact(x, listOf(a), Tail.EXACT, t), ExclusionSet.Empty)
            assertTrue(MicroEdge.of(source).isSource)
            f.entry(caller, RuleStatement(summary(ZERO_KEEP, source), StatementSummary.EMPTY, emptyList()))
            f.call(caller, 1, call(callee))
            f.statement(callee, 0, summary(starEdge(x, emptyList(), returned, emptyList())))
            val index = if (seed % 2 == 0L) 1 else 3
            val demand = listOf(callee to DemandPattern(f.pattern(x, tail = Tail.ANY, mark = t),
                f.pattern(returned, tail = Tail.ANY, mark = t)),
                caller to DemandPattern(ZERO_PATTERN, f.pattern(returned, tail = Tail.ANY, mark = t)))
            val config = f.config(index = index, roots = listOf(caller), demand = if (index == 1) emptyList() else demand,
                seeds = listOf(Seed.Source(caller, main.inst(0), source)))
            compareSeed(f, config, listOf(a), listOf(t.mark), seed) { actual ->
                assertContains(actual.rawSummaries, selectedExpectedEdge(f.ap.marks, index, ObservedEdge(caller, zero,
                    Conclusion(PathFact(returned, listOf(a), Tail.EXACT, t), ExclusionSet.Empty, false))))
                val layer = if (index == 1) Layer.DEMAND else Layer.NORMAL
                val expectedInput = f.pattern(x, listOf(a), Tail.EXACT, t)
                assertContains(actual.links, ObservedLink(callee, caller, zero, layer, main.inst(1), layer,
                    if (index == 1) GroupedScalarMarks(f.ap.marks).pattern(expectedInput) else expectedInput))
                assertTrue(actual.requests.isEmpty() && actual.sourceHits.isEmpty())
                if (config.run1) assertEquals(actual.publications, actual.selectedDemand)
                else assertTrue(actual.selectedDemand.isEmpty())
            }
        }
    }

    @Test
    fun `seeded direct static schedules preserve field origins beside explicit historical discovery controls`(): Unit {
        for (seed in 0L until 32L) FiniteProgramFixtures().use { f ->
            val main = f.method("seededStaticCaller", listOf(listOf(1), listOf(2), emptyList()), 2)
            val middleMethod = f.method("seededStaticWrapper", listOf(listOf(1), listOf(2), emptyList()), 2)
            val library = f.method("seededConditionalGetter", listOf(listOf(1), emptyList()), 1)
            val caller = main.key("caller context")
            val middle = middleMethod.key("middle context")
            val callee = library.key("callee context")
            val cls = f.ap.accessors.index(ClassStaticAccessor("Box"))
            val field = f.field("static field")
            val path = listOf(cls, field)
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val static = AccessPathBase.ClassStatic
            val source = PathEdge(ZERO_FACT, PathFact(static, path, Tail.EXACT, t), ExclusionSet.Empty)
            assertTrue(MicroEdge.of(source).isSource)
            f.entry(caller, RuleStatement(directStaticWrite(f, caller, 0, source, summary(ZERO_KEEP, source)), StatementSummary.EMPTY, emptyList()))
            fun staticCall(target: MethodKey, result: AccessPathBase) = CallPlan(setOf(static, returned, result), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(static), ZERO_KEEP)),
                CallStage.Actions(BOUND, ADDED, emptyList()),
                CallStage.Callees(ADDED, RETURNED, listOf(target)),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK,
                    summary(starEdge(returned, emptyList(), result, emptyList()), ZERO_KEEP)),
            ), emptyList(), BEFORE, AFTER)
            f.call(caller, 1, staticCall(middle, y))
            f.call(middle, 1, staticCall(callee, returned))
            val read = PathEdge(PathFact(static, path, Tail.EXACT, t),
                PathFact(returned, emptyList(), Tail.EXACT, u), ExclusionSet.Empty)
            f.statement(callee, 0, directStaticRead(f, callee, 0, read, summary(read)))
            val config = f.config(roots = listOf(caller))
            val scalar = NaiveClosure(f, config, semantics = NaiveClosure.Semantics.HISTORICAL).run()
            for (sameUnit in listOf(false, true)) {
                val actual = ProductionSchedule(f, config, sameUnit, SchedulePolicy.SEEDED, seed,
                    quantum = 1 + (seed % 2).toInt(), duplicateDeliveries = true).run()
                assertDirectStaticProtocol(actual, f.ap.marks)
                val policy = f.pattern(static, tail = Tail.STAR, mark = MarkSlot.STAR)
                val positioned = f.pattern(static, path, Tail.STAR, MarkSlot.STAR)
                for (key in listOf(middle, callee)) {
                    assertContains(scalar.requests, ObservedRequest(key, policy, RequestKind.Position(f.ap.path(path)!!)))
                    assertContains(scalar.requests, ObservedRequest(key, positioned, RequestKind.Mark(t.mark)))
                    assertTrue(actual.initials.none { it.method == key && it.initial.fact.base == STATIC })
                }
                assertTrue(actual.requests.none { it.kind is RequestKind.Position })
                assertContains(actual.rawSummaries, ObservedEdge(caller, setOf(GroupedScalarMarks(f.ap.marks).pattern(f.pattern(STATIC, path, mark = t))),
                    GroupedScalarMarks(f.ap.marks).conclusion(Conclusion(PathFact(y, emptyList(), Tail.EXACT, u), ExclusionSet.Empty, false))))
                assertTrue(actual.sourceHits.isEmpty())
            }
        }
    }

    @Test
    fun `seeded cleaner and strong write schedules retain must exclusions and independent cut marks`(): Unit {
        for (seed in 0L until 32L) FiniteProgramFixtures().use { f ->
            val method = f.method("seededCleanAndWrite", listOf(listOf(1), listOf(2), emptyList()), 2)
            val key = method.key("cleaner context")
            val a = f.field("cleaned")
            val b = f.field("written")
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val must = PathEdge(ZERO_FACT, PathFact(x, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.Empty)
            val deep = PathEdge(ZERO_FACT, PathFact(x, listOf(a, b, b), Tail.EXACT, u), ExclusionSet.Empty)
            f.entry(key, RuleStatement(summary(ZERO_KEEP, must, deep), StatementSummary.EMPTY, emptyList()))
            f.call(key, 1, CallPlan(setOf(x), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(x), ZERO_KEEP)),
                CallStage.Actions(BOUND, ADDED, listOf(CleanStep.Clean(Cleaner(x, f.ap.path(listOf(a)), CleanReach.AT_AND_BELOW, t.mark)))),
                CallStage.Edges(ADDED, RETURNED, UNRESOLVED, summary(keepEdge(x, e = ExclusionSet.of(b)), ZERO_KEEP)),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(keepEdge(x), ZERO_KEEP)),
            ), emptyList(), BEFORE, AFTER))
            val index = if (seed % 2 == 0L) 1 else 3
            val config = f.config(index = index, roots = listOf(key), demand = if (index == 1) emptyList() else
                listOf(key to DemandPattern(ZERO_PATTERN, f.pattern(x, tail = Tail.ANY, mark = u))),
                seeds = listOf(Seed.Source(key, method.inst(0), must), Seed.Source(key, method.inst(0), deep)))
            compareSeed(f, config, listOf(a, b), listOf(t.mark, u.mark), seed) { actual ->
                assertContains(actual.rawSummaries, selectedExpectedEdge(f.ap.marks, index, ObservedEdge(key, zero,
                    Conclusion(PathFact(x, emptyList(), Tail.ANY_TAINT, t), if (index == 1) ExclusionSet.Empty else ExclusionSet.of(intArrayOf(a, b)), index == 1))))
                val cut = selectedExpectedEdge(f.ap.marks, index, ObservedEdge(key, zero, Conclusion(PathFact(x, if (index == 1) listOf(a) else listOf(a, b), Tail.ANY, u), ExclusionSet.Empty, true)))
                assertContains(actual.rawSummaries, cut)
                assertContains(actual.selectedDemand, cut)
                assertEquals(index == 1, actual.selectedDemand.any { it.conclusion.fact.mark == (if (index == 1) GroupedScalarMarks(f.ap.marks).slot(t) else t) })
                assertTrue(actual.requests.isEmpty())
            }
        }
    }

    @Test
    fun `seeded ND schedules join complete members across delivery replay and mixed layers`(): Unit {
        for (seed in 0L until 32L) FiniteProgramFixtures().use { f ->
            val mixed = seed % 2 != 0L
            val producerMethod = f.method("seededSplitProducer", listOf(listOf(1), listOf(2), emptyList()), 2)
            val sourceNode = 18
            val consumerMethod = f.method("seededSplitConsumer", (0..(sourceNode + 1)).map { index ->
                when (index) {
                    sourceNode -> listOf(1, sourceNode + 1)
                    sourceNode + 1 -> emptyList()
                    else -> listOf(index + 1)
                }
            }, sourceNode + 1)
            val joinMethod = f.method("seededSplitJoinedExit", listOf(listOf(1), emptyList()), 1)
            val producer = producerMethod.key("producer context")
            val consumer = consumerMethod.key("consumer context")
            val callee = joinMethod.key("callee context")
            val signal = AccessPathBase.Argument(8)
            val a = f.field("a")
            val b = f.field("cut suffix")
            val t = MarkSlot.Concrete(f.mark("A"))
            val u = MarkSlot.Concrete(f.mark("B"))
            val v = MarkSlot.Concrete(f.mark("C"))
            val d = MarkSlot.Concrete(f.mark("delay signal"))
            val sourceA = PathEdge(ZERO_FACT, PathFact(x, emptyList(), Tail.EXACT, t), ExclusionSet.Empty)
            val sourceB = PathEdge(ZERO_FACT, PathFact(y, if (mixed) listOf(a, b, b) else listOf(a), Tail.EXACT, u), ExclusionSet.Empty)
            val sourceSignal = PathEdge(ZERO_FACT, PathFact(signal, emptyList(), Tail.EXACT, d), ExclusionSet.Empty)
            val delayedB = PathEdge(sourceSignal.to, sourceB.to, ExclusionSet.Empty)
            assertTrue(MicroEdge.of(sourceA).isSource && MicroEdge.of(sourceB).isSource && MicroEdge.of(sourceSignal).isSource)
            assertFalse(MicroEdge.of(delayedB).isSource)
            f.entry(producer, RuleStatement(summary(ZERO_KEEP, sourceA, sourceB), StatementSummary.EMPTY, emptyList()))
            f.entry(consumer, RuleStatement(summary(ZERO_KEEP, sourceSignal, sourceA), StatementSummary.EMPTY, emptyList()))
            f.statement(consumer, 2, StatementSummary(setOf(AccessPathBase.Zero), emptyList(), emptyList(), emptyMap()))
            f.statement(consumer, sourceNode, summary(delayedB))
            val callPlan = CallPlan(setOf(x, y, returned), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(x), keepEdge(y), ZERO_KEEP)),
                CallStage.Actions(BOUND, ADDED, emptyList()),
                CallStage.Callees(ADDED, RETURNED, listOf(callee)),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(keepEdge(returned), ZERO_KEEP)),
            ), emptyList(), BEFORE, AFTER)
            f.call(producer, 1, callPlan)
            f.call(consumer, 1, callPlan)
            val literals = listOf(f.pattern(x, mark = t), f.pattern(y, listOf(a), Tail.ANY, u))
            val target = PathFact(returned, emptyList(), Tail.EXACT, v)
            f.exit(callee, 1, ExitRules(RuleStatement(StatementSummary(emptySet(), emptyList(),
                listOf(ConjunctiveEdge(literals, target)), emptyMap()), StatementSummary.EMPTY, emptyList()), false, emptySet()))
            val demand = literals.map { callee to DemandPattern(it, f.pattern(returned, tail = Tail.ANY, mark = v)) } +
                listOf(producer, consumer).map { it to DemandPattern(ZERO_PATTERN, f.pattern(returned, tail = Tail.ANY, mark = v)) }
            val seeds = listOf(Seed.Source(producer, producerMethod.inst(0), sourceA),
                Seed.Source(producer, producerMethod.inst(0), sourceB), Seed.Source(consumer, consumerMethod.inst(0), sourceA),
                Seed.Source(consumer, consumerMethod.inst(0), sourceSignal))
            val config = f.config(index = 3, roots = listOf(producer, consumer), demand = demand, seeds = seeds)
            compareSeed(f, config, listOf(a, b), listOf(t.mark, u.mark, v.mark, d.mark), seed) { actual ->
                val members = setOf(literals[0], f.pattern(y, if (mixed) listOf(a, b) else listOf(a), if (mixed) Tail.ANY else Tail.EXACT, u))
                val joined = ObservedEdge(callee, members, Conclusion(target, ExclusionSet.Empty, mixed))
                assertContains(actual.rawSummaries, joined)
                assertContains(actual.publications, joined)
                assertContains(actual.selectedDemand, joined)
                for (caller in listOf(producer, consumer)) {
                    val output = ObservedEdge(caller, zero, Conclusion(target, ExclusionSet.Empty, mixed))
                    assertContains(actual.rawSummaries, output)
                    if (mixed) assertContains(actual.selectedDemand, output)
                    else assertTrue(actual.selectedDemand.none { it.method == caller && it.conclusion.fact.mark == v })
                }
                assertContains(actual.links, ObservedLink(callee, consumer, zero, Layer.NORMAL, consumerMethod.inst(1),
                    Layer.NORMAL, literals[0]))
                assertContains(actual.links, ObservedLink(callee, consumer, zero, if (mixed) Layer.DEMAND else Layer.NORMAL,
                    consumerMethod.inst(1), if (mixed) Layer.DEMAND else Layer.NORMAL,
                    f.pattern(y, if (mixed) listOf(a, b) else listOf(a), if (mixed) Tail.ANY else Tail.EXACT, u)))
                assertTrue(actual.requests.isEmpty() && actual.sourceHits.isEmpty())
            }
        }
    }

    @Test
    fun `seeded native exact and pending record schedules preserve identity flags and late demotion`(): Unit {
        for (seed in 0L until 32L) FiniteProgramFixtures().use { f ->
            val main = f.method("seededRecordCaller", listOf(listOf(1), listOf(2), emptyList()), 2)
            val library = f.method("seededRecordedLibrary", listOf(listOf(1), emptyList()), 1)
            val caller = main.key("caller context")
            val callee = library.key("callee context")
            val a = f.field("allowed")
            val b = f.field("blocked")
            val alias = AccessPathBase.Argument(7)
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val shape = (seed % 4).toInt()
            val pending = shape < 2
            val blocked = shape == 1
            val identity = shape == 2
            val source = PathEdge(ZERO_FACT, PathFact(x, listOf(a), if (pending) Tail.ANY_TAINT else Tail.EXACT, t), ExclusionSet.Empty)
            assertTrue(MicroEdge.of(source).isSource)
            f.entry(caller, RuleStatement(summary(ZERO_KEEP, source), StatementSummary.EMPTY, emptyList()))
            val output = when {
                pending -> PathFact(returned, emptyList(), Tail.ANY_TAINT, u)
                identity -> source.to
                else -> PathFact(returned, listOf(b), Tail.EXACT, u)
            }
            val bindBack = if (pending) summary(starEdge(returned, listOf(if (blocked) b else a), y, emptyList()), ZERO_KEEP)
                else summary(starEdge(output.base, emptyList(), y, emptyList()), ZERO_KEEP)
            val original = call(callee, bindBack,
                listOf(CleanStep.Clean(Cleaner(returned, null, CleanReach.EXACT, f.mark("unrelated")))))
            f.call(caller, 1, CallPlan(original.touched, original.stages +
                CallStage.Edges(REWRITTEN, AFTER, ALIASES,
                    summary(starEdge(output.base, emptyList(), alias, emptyList())), Guard.MemoryEffect),
                emptyList(), BEFORE, AFTER))
            val initial = if (pending) f.ap.initial(f.pattern(x, tail = Tail.ANY_TAINT, mark = t))
                else f.ap.initial(f.pattern(x, listOf(a), Tail.EXACT, t))
            val record = Record(callee, Direction.FORWARD, initial,
                f.fact(output.base, output.path, output.tail, output.mark, if (pending) ExclusionSet.of(b) else ExclusionSet.Empty))
            val demand = listOf(callee to DemandPattern(f.pattern(x, tail = Tail.ANY, mark = MarkSlot.STAR),
                f.pattern(output.base, tail = Tail.STAR, mark = MarkSlot.STAR)),
                caller to DemandPattern(ZERO_PATTERN, f.pattern(y, tail = Tail.ANY, mark = output.mark)))
            val config = f.config(index = 3, roots = listOf(caller), demand = demand, records = listOf(callee to record),
                seeds = listOf(Seed.Source(caller, main.inst(0), source)))
            compareSeed(f, config, listOf(a, b), listOf(t.mark, u.mark), seed) { actual ->
                val expected = buildSet {
                    if (pending) {
                        add(ObservedEdge(caller, zero, Conclusion(PathFact(alias, emptyList(), Tail.ANY_TAINT, u), ExclusionSet.of(b), true)))
                        if (!blocked) add(ObservedEdge(caller, zero,
                            Conclusion(PathFact(y, emptyList(), Tail.ANY_TAINT, u), ExclusionSet.Empty, true)))
                    } else {
                        add(ObservedEdge(caller, zero, Conclusion(output.copy(base = y), ExclusionSet.Empty, false)))
                        if (!identity) add(ObservedEdge(caller, zero, Conclusion(output.copy(base = alias), ExclusionSet.Empty, false)))
                    }
                }
                assertEquals(expected, actual.rawSummaries.filter {
                    it.method == caller && it.conclusion.fact.mark == output.mark
                }.toSet())
                assertTrue(actual.requests.isEmpty() && actual.sourceHits.isEmpty())
                assertEquals(pending && !blocked, actual.selectedDemand.any { it.method == caller && it.conclusion.fact.mark == output.mark })
            }
        }
    }

    @Test
    fun `seeded backward native views preserve exact and may results alongside balanced zero calls`(): Unit {
        for (seed in 0L until 32L) FiniteProgramFixtures().use { f ->
            val main = f.method("seededViewCaller", listOf(listOf(1), listOf(2), emptyList()), 2)
            val library = f.method("seededViewLibrary", listOf(listOf(1), emptyList()), 1)
            val caller = main.key("caller context")
            val callee = library.key("callee context")
            val a = f.field("input")
            val b = f.field("excluded")
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val (inputTail, outputTail) = listOf(Tail.EXACT to Tail.ANY_TAINT,
                Tail.ANY_TAINT to Tail.ANY_TAINT, Tail.ANY_TAINT to Tail.EXACT)[(seed % 3).toInt()]
            f.call(caller, 1, call(callee, summary(starEdge(returned, emptyList(), y, emptyList()), ZERO_KEEP)))
            val initial = f.ap.initial(f.pattern(x, listOf(a), inputTail, t,
                if (inputTail == Tail.ANY_TAINT) ExclusionSet.of(b) else ExclusionSet.Empty))
            val record = Record(callee, Direction.FORWARD, initial,
                f.fact(returned, tail = outputTail, mark = u,
                    exclusion = if (outputTail == Tail.ANY_TAINT) ExclusionSet.of(b) else ExclusionSet.Empty))
            val config = f.config(index = 2, roots = listOf(caller), records = listOf(callee to record),
                seeds = listOf(Seed.Sink(rule("view seed"), caller, main.inst(2), f.pattern(y, mark = u))))
            compareSeed(f, config, listOf(a, b), listOf(t.mark, u.mark), seed) { actual ->
                val demand = inputTail == Tail.ANY_TAINT
                val expected = ObservedEdge(caller, zero, Conclusion(PathFact(x, listOf(a),
                    if (demand) Tail.ANY else Tail.EXACT, t), ExclusionSet.Empty, demand))
                assertEquals(setOf(expected), actual.rawSummaries.filter {
                    it.method == caller && it.conclusion.fact.mark == t
                }.toSet())
                assertContains(actual.selectedDemand, expected)
                assertContains(actual.initials, ObservedInitial(callee, ZERO_PATTERN))
                val zeroSummary = ObservedEdge(callee, zero, Conclusion(ZERO_FACT, ExclusionSet.Empty, false))
                assertContains(actual.rawSummaries, zeroSummary)
                assertContains(actual.publications, zeroSummary)
                assertContains(actual.selectedDemand, zeroSummary)
                assertTrue(actual.requests.isEmpty() && actual.sourceHits.isEmpty() && actual.witnesses.isEmpty())
            }
        }
    }
}
