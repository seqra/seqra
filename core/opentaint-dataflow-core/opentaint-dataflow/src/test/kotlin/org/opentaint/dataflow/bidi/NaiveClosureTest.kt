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

class NaiveClosureTest {
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Argument(1)
    private val returned = AccessPathBase.Return
    private val zero = setOf(ZERO_PATTERN)

    private fun summary(vararg edges: PathEdge): StatementSummary =
        StatementSummary(edges.map { it.from.base }.toSet(), edges.map(MicroEdge::of), emptyList(), emptyMap())

    private fun call(callee: MethodKey, bindBack: StatementSummary = summary(starEdge(returned, emptyList(), y, emptyList()), ZERO_KEEP),
                     returnedActions: List<CleanStep> = emptyList()): CallPlan =
        CallPlan(setOf(x, y, returned), listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(x), ZERO_KEEP)),
            CallStage.Actions(BOUND, ADDED, emptyList()),
            CallStage.Callees(ADDED, RETURNED, listOf(callee)),
            CallStage.Actions(RETURNED, REWRITTEN, returnedActions),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, bindBack),
        ), emptyList(), BEFORE, AFTER)

    private fun check(f: FiniteProgramFixtures, config: RunConfig, fields: List<Int>, marks: List<TaintMark>,
                      sameUnit: Boolean = false, policy: SchedulePolicy = SchedulePolicy.FIFO,
                      catalogRefinement: Boolean = false): RunObservation {
        val expected = NaiveClosure(f, config, semantics = NaiveClosure.Semantics.SELECTED).run()
        val schedule = ProductionSchedule(f, config, sameUnit = sameUnit, policy = policy, quantum = 1)
        val actual = schedule.run()
        fun patterns(observation: RunObservation): List<Pattern> = buildList {
            for (edge in observation.processed + observation.rawSummaries + observation.publications + observation.selectedDemand) {
                addAll(edge.premise)
                add(Pattern(edge.conclusion.fact, edge.conclusion.exclusion))
            }
            observation.initials.forEach { add(it.initial) }
            observation.links.forEach { addAll(it.premise); add(it.added) }
            observation.requests.forEach { add(it.premise) }
            observation.sourceHits.forEach { add(Pattern(it.forward.from, it.forward.fromExclusion)); add(Pattern(it.forward.to, it.forward.toExclusion)) }
            for (witness in observation.witnesses) {
                witness.edges.forEach { edge -> addAll(edge.premise); edge.facts.forEach { add(Pattern(it.fact, it.exclusion)) } }
                witness.endFacts.forEach { add(Pattern(it, ExclusionSet.Empty)) }
            }
        }
        val observed = patterns(expected) + patterns(actual)
        val positionPaths = (expected.requests + actual.requests).mapNotNull { (it.kind as? RequestKind.Position)?.path?.toIntArray()?.toList() }
        val universe = BoundedDenotation(
            (observed.map { it.fact.base } + listOf(AccessPathBase.Zero, x, y, returned, AccessPathBase.ClassStatic, AccessPathBase.Argument(7))).distinct(),
            (fields + observed.flatMap { it.fact.path + (it.exclusion as? ExclusionSet.Concrete)?.ids?.toList().orEmpty() } + positionPaths.flatten() + f.field("fresh accessor")).distinct(),
            (marks + observed.mapNotNull { (it.fact.mark as? MarkSlot.Concrete)?.mark } + f.mark("fresh mark") + TaintMark.ZERO).distinct(), 2)
        try {
            if (catalogRefinement) expected.assertCatalogRefinement(actual, universe, config.roots)
            else actual.assertEquivalent(expected, universe)
        }
        catch (failure: AssertionError) { throw AssertionError("index=${config.index} sameUnit=$sameUnit policy=$policy program=${f.describe()} trace=${schedule.trace}", failure) }
        return actual
    }

    private fun rule(id: String): CommonTaintConfigurationSink = object : CommonTaintConfigurationSink {
        override val id = id
        override val meta = object : CommonTaintConfigurationSinkMeta {
            override val message = id
            override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
        }
    }

    @Test
    fun `FLOW observation compares the correlated union when equal roots intersect field exclusions`(): Unit {
        FiniteProgramFixtures().use { f ->
            val key = f.method("field union comparison", listOf(listOf(1), emptyList()), 1).key()
            val a = f.field("a")
            val b = f.field("b")
            val t = f.mark("T")
            val u = f.mark("U")
            val universe = BoundedDenotation(listOf(x, returned), listOf(a, b, f.field("fresh")),
                listOf(t, u, f.mark("fresh")), 2)
            val premise = f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR)
            fun output(e: ExclusionSet) = Conclusion(PathFact(returned, emptyList(), Tail.STAR, MarkSlot.STAR), e, false)
            val left = output(ExclusionSet.of(a))
            val right = output(ExclusionSet.of(b))
            val merged = output(ExclusionSet.Empty)
            assertEquals(universe.pairs(premise, merged), universe.pairs(premise, left) + universe.pairs(premise, right))
            val split = RunObservation(rawSummaries = setOf(ObservedEdge(key, setOf(premise), left),
                ObservedEdge(key, setOf(premise), right)))
            val combined = RunObservation(rawSummaries = setOf(ObservedEdge(key, setOf(premise), merged)))
            assertEquals(split.semantic(universe), combined.semantic(universe), "T2 preserves the full pair union")
        }
    }

    @Test
    fun `FLOW observation compares the correlated union when equal roots intersect mark exclusions`(): Unit {
        FiniteProgramFixtures().use { f ->
            val key = f.method("mark union comparison", listOf(listOf(1), emptyList()), 1).key()
            val a = f.field("a")
            val t = f.mark("T")
            val u = f.mark("U")
            val universe = BoundedDenotation(listOf(x, returned), listOf(a, f.field("fresh")),
                listOf(t, u, f.mark("fresh")), 2)
            val premise = f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR)
            fun output(mx: MarkSet) = Conclusion(PathFact(returned, emptyList(), Tail.STAR, MarkSlot.Star(mx)),
                ExclusionSet.of(a), false)
            val left = output(f.ap.markSetOf(t))
            val right = output(f.ap.markSetOf(u))
            val merged = output(MarkSet.EMPTY)
            assertEquals(universe.pairs(premise, merged), universe.pairs(premise, left) + universe.pairs(premise, right))
            val split = RunObservation(rawSummaries = setOf(ObservedEdge(key, setOf(premise), left),
                ObservedEdge(key, setOf(premise), right)))
            val combined = RunObservation(rawSummaries = setOf(ObservedEdge(key, setOf(premise), merged)))
            assertEquals(split.semantic(universe), combined.semantic(universe), "T2' preserves the full pair union")
        }
    }

    @Test
    fun `normal must observation compares the union when equal roots intersect field exclusions`(): Unit {
        FiniteProgramFixtures().use { f ->
            val key = f.method("must union comparison", listOf(listOf(1), emptyList()), 1).key()
            val a = f.field("a")
            val b = f.field("b")
            val t = MarkSlot.Concrete(f.mark("T"))
            val universe = BoundedDenotation(listOf(x, returned), listOf(a, b, f.field("fresh")),
                listOf(t.mark, f.mark("fresh")), 2)
            fun output(e: ExclusionSet) = Conclusion(PathFact(returned, emptyList(), Tail.ANY_TAINT, t), e, false)
            val left = output(ExclusionSet.of(a))
            val right = output(ExclusionSet.of(b))
            val merged = output(ExclusionSet.Empty)
            assertEquals(universe.marked(Pattern(merged.fact, merged.exclusion)),
                universe.marked(Pattern(left.fact, left.exclusion)) + universe.marked(Pattern(right.fact, right.exclusion)))
            val split = RunObservation(rawSummaries = setOf(ObservedEdge(key, zero, left), ObservedEdge(key, zero, right)))
            val combined = RunObservation(rawSummaries = setOf(ObservedEdge(key, zero, merged)))
            assertEquals(split.semantic(universe), combined.semantic(universe), "TAINT E intersection preserves the must union")
        }
    }

    @Test
    fun `observation comparison retains annotations correlations normal components and full protocol headers`(): Unit {
        FiniteProgramFixtures().use { f ->
            val method = f.method("strict comparison", listOf(listOf(1), emptyList()), 1)
            val key = method.key("left context")
            val other = method.key("right context")
            val a = f.field("a")
            val b = f.field("b")
            val t = f.mark("T")
            val u = f.mark("U")
            val universe = BoundedDenotation(listOf(x, y, returned), listOf(a, b, f.field("fresh")),
                listOf(t, u, f.mark("fresh")), 2)
            val p = f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR)
            fun flow(e: ExclusionSet = ExclusionSet.Empty, mx: MarkSet = MarkSet.EMPTY) =
                Conclusion(PathFact(returned, emptyList(), Tail.STAR, MarkSlot.Star(mx)), e, false)
            fun observation(c: Conclusion, k: MethodKey = key, premise: Set<Pattern> = setOf(p)) =
                RunObservation(rawSummaries = setOf(ObservedEdge(k, premise, c)))
            val ea = flow(ExclusionSet.of(a))
            val eb = flow(ExclusionSet.of(b))
            val mt = flow(mx = f.ap.markSetOf(t))
            val mu = flow(mx = f.ap.markSetOf(u))
            assertNotEquals(observation(ea).semantic(universe), observation(eb).semantic(universe))
            assertNotEquals(observation(mt).semantic(universe), observation(mu).semantic(universe))
            assertNotEquals(observation(flow()).semantic(universe), observation(flow(), k = other).semantic(universe))
            assertNotEquals(observation(flow()).semantic(universe), observation(flow(),
                premise = setOf(f.pattern(x, listOf(a), Tail.STAR, MarkSlot.STAR))).semantic(universe))
            val demand = Conclusion(PathFact(returned, emptyList(), Tail.ANY, MarkSlot.STAR), ExclusionSet.Empty, true)
            assertNotEquals(observation(flow()).semantic(universe), observation(demand).semantic(universe))
            val bothChanged = RunObservation(rawSummaries = setOf(
                ObservedEdge(key, setOf(p), flow(ExclusionSet.of(a), f.ap.markSetOf(t))),
                ObservedEdge(key, setOf(p), flow(ExclusionSet.of(b), f.ap.markSetOf(u)))))
            val union = universe.pairs(p, flow(ExclusionSet.of(a), f.ap.markSetOf(t))) +
                universe.pairs(p, flow(ExclusionSet.of(b), f.ap.markSetOf(u)))
            assertNotEquals(universe.pairs(p, flow()), union, "Changing both annotations introduces new pairs")
            assertNotEquals(observation(flow()).semantic(universe), bothChanged.semantic(universe))
            val root = observation(demand)
            val child = demand.copy(fact = demand.fact.copy(path = listOf(a)))
            assertEquals(root.semantic(universe), root.copy(rawSummaries = root.rawSummaries +
                ObservedEdge(key, setOf(p), child)).semantic(universe), "Compare unions instead of leaf counts")
            val concrete = MarkSlot.Concrete(t)
            val exact = Conclusion(PathFact(returned, emptyList(), Tail.EXACT, concrete), ExclusionSet.Empty, false)
            val must = exact.copy(fact = exact.fact.copy(tail = Tail.ANY_TAINT))
            val mustOnly = observation(must, premise = zero)
            assertNotEquals(mustOnly.semantic(universe), mustOnly.copy(rawSummaries = mustOnly.rawSummaries +
                ObservedEdge(key, zero, exact)).semantic(universe), "Normal exact remains distinct from normal must")
            val link = ObservedLink(other, key, setOf(p), Layer.NORMAL, method.inst(0), Layer.NORMAL, Pattern(ea.fact, ea.exclusion))
            val links = RunObservation(links = setOf(link))
            assertNotEquals(links.semantic(universe), links.copy(links = setOf(link.copy(callerLayer = Layer.DEMAND))).semantic(universe))
            assertNotEquals(links.semantic(universe), links.copy(links = setOf(link.copy(added = Pattern(eb.fact, eb.exclusion)))).semantic(universe))
            val witness = ObservedWitness(rule("alternatives"), key, method.inst(1), 0, 1,
                listOf(ObservedSinkEdge(zero, Layer.NORMAL, setOf(exact))), emptySet())
            val witnesses = RunObservation(witnesses = setOf(witness))
            assertNotEquals(witnesses.semantic(universe), witnesses.copy(witnesses = setOf(witness.copy(alternative = 1))).semantic(universe))
        }
    }

    @Test
    fun `an exact source crosses a real getter call and retains its full caller premise and nested path`(): Unit {
        for (sameUnit in listOf(false, true)) FiniteProgramFixtures().use { f ->
            val main = f.method("sourceCaller", listOf(listOf(1), listOf(2), listOf(3), emptyList()), 3)
            val getter = f.method("getter", listOf(listOf(1), emptyList()), 1)
            val caller = main.key()
            val callee = getter.key()
            val a = f.field("a")
            val t = MarkSlot.Concrete(f.mark("T"))
            f.statement(caller, 1, summary(ZERO_KEEP,
                PathEdge(ZERO_FACT, PathFact(x, listOf(a), Tail.EXACT, t), ExclusionSet.Empty)))
            f.call(caller, 2, call(callee))
            f.statement(callee, 0, summary(starEdge(x, emptyList(), returned, emptyList())))
            val config = f.config(roots = listOf(caller))
            val actual = check(f, config, listOf(a), listOf(t.mark), sameUnit)
            assertContains(actual.rawSummaries, ObservedEdge(caller, zero,
                Conclusion(PathFact(y, listOf(a), Tail.EXACT, t), ExclusionSet.Empty, false)))
            assertContains(actual.rawSummaries, ObservedEdge(callee,
                setOf(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR)),
                Conclusion(PathFact(returned, emptyList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty, false)))
            assertContains(actual.processed, ObservedEdge(caller, zero,
                Conclusion(ZERO_FACT, ExclusionSet.Empty, false), main.inst(3)))
            assertTrue(actual.requests.isEmpty() && actual.sourceHits.isEmpty())
            assertEquals(actual.publications, actual.selectedDemand, "Every F1 publication is guidance")
        }
    }

    @Test
    fun `restricted actual query preserves raw E without adding old exit-guide exclusions`(): Unit {
        for (sameUnit in listOf(false, true)) FiniteProgramFixtures().use { f ->
            val main = f.method("mustCaller", listOf(listOf(1), listOf(2), listOf(3), emptyList()), 3)
            val getter = f.method("excludedGetter", listOf(listOf(1), emptyList()), 1)
            val caller = main.key()
            val callee = getter.key()
            val a = f.field("demand excluded")
            val b = f.field("write excluded")
            val t = MarkSlot.Concrete(f.mark("T"))
            val source = PathEdge(ZERO_FACT, PathFact(x, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.Empty)
            f.statement(caller, 1, summary(ZERO_KEEP, source))
            f.call(caller, 2, call(callee))
            f.statement(callee, 0, summary(PathEdge(starSide(x), starSide(returned), ExclusionSet.of(b))))
            val demand = listOf(
                callee to DemandPattern(f.pattern(x, tail = Tail.ANY, mark = t),
                    f.pattern(returned, tail = Tail.STAR, mark = t, exclusion = ExclusionSet.of(a))),
                caller to DemandPattern(ZERO_PATTERN,
                    f.pattern(y, tail = Tail.STAR, mark = t, exclusion = ExclusionSet.of(a))),
            )
            val config = f.config(index = 3, roots = listOf(caller), demand = demand,
                seeds = listOf(Seed.Source(caller, main.inst(1), source)))
            val actual = check(f, config, listOf(a, b), listOf(t.mark), sameUnit)
            val initial = f.pattern(x, tail = Tail.ANY_TAINT, mark = t)
            assertContains(actual.initials, ObservedInitial(callee, initial))
            assertContains(actual.rawSummaries, ObservedEdge(callee, setOf(initial),
                Conclusion(PathFact(returned, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.of(b), false)))
            // Actual-current authorization publishes the complete computed result;
            // prior exit guidance cannot add the unrelated exclusion a.
            val outputExclusion = ExclusionSet.of(b)
            assertContains(actual.publications, ObservedEdge(callee, setOf(initial),
                Conclusion(PathFact(returned, emptyList(), Tail.ANY_TAINT, t), outputExclusion, false)))
            assertContains(actual.rawSummaries, ObservedEdge(caller, zero,
                Conclusion(PathFact(y, emptyList(), Tail.ANY_TAINT, t), outputExclusion, false)))
            assertTrue(actual.requests.isEmpty() && actual.sourceHits.isEmpty() && actual.selectedDemand.isEmpty())
        }
    }

    @Test
    fun `sink literals and exit source joins stay local to complete method contexts`(): Unit {
        for (includeBoth in listOf(false, true)) for (sameUnit in listOf(false, true)) FiniteProgramFixtures().use { f ->
            val method = f.method("contextSink", listOf(listOf(1), emptyList()), 1)
            val left = method.key("only A")
            val right = method.key("only B")
            val both = method.key("both")
            val a = MarkSlot.Concrete(f.mark("A"))
            val b = MarkSlot.Concrete(f.mark("B"))
            val c = MarkSlot.Concrete(f.mark("C"))
            val sourceA = PathEdge(ZERO_FACT, PathFact(x, emptyList(), Tail.EXACT, a), ExclusionSet.Empty)
            val sourceB = PathEdge(ZERO_FACT, PathFact(y, emptyList(), Tail.EXACT, b), ExclusionSet.Empty)
            f.entry(left, RuleStatement(summary(ZERO_KEEP, sourceA), StatementSummary.EMPTY, emptyList()))
            f.entry(right, RuleStatement(summary(ZERO_KEEP, sourceB), StatementSummary.EMPTY, emptyList()))
            f.entry(both, RuleStatement(summary(ZERO_KEEP, sourceA, sourceB), StatementSummary.EMPTY, emptyList()))
            val literals = listOf(f.pattern(x, mark = a), f.pattern(y, mark = b))
            val id = rule("context-local")
            val sink = SinkRule(id, 0, literals, emptyList())
            val conjunction = ConjunctiveEdge(literals, PathFact(returned, emptyList(), Tail.EXACT, c))
            val exit = ExitRules(RuleStatement(StatementSummary(emptySet(), emptyList(), listOf(conjunction), emptyMap()),
                StatementSummary.EMPTY, listOf(sink)), false, emptySet())
            for (key in listOf(left, right, both)) f.exit(key, 1, exit)
            val config = f.config(roots = if (includeBoth) listOf(left, right, both) else listOf(left, right))
            val actual = check(f, config, emptyList(), listOf(a.mark, b.mark, c.mark), sameUnit)
            assertEquals(if (includeBoth) setOf(both) else emptySet(), actual.witnesses.map { it.methodKey }.toSet())
            for (witness in actual.witnesses) {
                assertSame(id, witness.rule)
                assertEquals(0, witness.alternative)
                assertEquals(1, witness.run)
                assertFalse(witness.confirmed)
                assertEquals(listOf(zero, zero), witness.edges.map { it.premise })
                assertEquals(listOf(Layer.NORMAL, Layer.NORMAL), witness.edges.map { it.layer })
            }
            val generated = actual.rawSummaries.filter { it.conclusion.fact.mark == c }
            assertEquals(if (includeBoth) setOf(ObservedEdge(both, zero,
                Conclusion(conjunction.target, ExclusionSet.Empty, false))) else emptySet(), generated.toSet())
        }
    }

    @Test
    fun `scalar static climb remains the oracle while catalog closure preserves supplied caller effects`(): Unit {
        for (policy in listOf(SchedulePolicy.REPLAY_FIRST, SchedulePolicy.NOTIFY_FIRST))
            for (sameUnit in listOf(false, true)) FiniteProgramFixtures().use { f ->
                val main = f.method("staticCaller", listOf(listOf(1), listOf(2), emptyList()), 2)
                val wrapper = f.method("staticWrapper", listOf(listOf(1), listOf(2), emptyList()), 2)
                val getter = f.method("staticConditionalGetter", listOf(listOf(1), emptyList()), 1)
                val caller = main.key()
                val middle = wrapper.key()
                val callee = getter.key()
                val cls = f.ap.accessors.index(ClassStaticAccessor("Box"))
                val field = f.field("static field")
                val path = listOf(cls, field)
                val t = MarkSlot.Concrete(f.mark("T"))
                val u = MarkSlot.Concrete(f.mark("U"))
                val static = AccessPathBase.ClassStatic
                val source = PathEdge(ZERO_FACT, PathFact(static, path, Tail.EXACT, t), ExclusionSet.Empty)
                assertTrue(MicroEdge.of(source).isSource)
                f.entry(caller, RuleStatement(summary(ZERO_KEEP, source), StatementSummary.EMPTY, emptyList()))
                fun staticCall(target: MethodKey, result: AccessPathBase): CallPlan = CallPlan(setOf(static, returned, result), listOf(
                    CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(static), ZERO_KEEP)),
                    CallStage.Actions(BOUND, ADDED, emptyList()),
                    CallStage.Callees(ADDED, RETURNED, listOf(target)),
                    CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                    CallStage.Edges(REWRITTEN, AFTER, BIND_BACK,
                        summary(starEdge(returned, emptyList(), result, emptyList()), ZERO_KEEP)),
                ), emptyList(), BEFORE, AFTER)
                f.call(caller, 1, staticCall(middle, y))
                f.call(middle, 1, staticCall(callee, returned))
                f.statement(callee, 0, summary(PathEdge(PathFact(static, path, Tail.EXACT, t),
                    PathFact(returned, emptyList(), Tail.EXACT, u), ExclusionSet.Empty)))
                val config = f.config(roots = listOf(caller))
                val scalar = NaiveClosure(f, config, semantics = NaiveClosure.Semantics.HISTORICAL).run()
                val actual = check(f, config, path, listOf(t.mark, u.mark), sameUnit, policy, catalogRefinement = true)
                val policyInitial = f.pattern(static, tail = Tail.STAR, mark = MarkSlot.STAR)
                val positionInitial = f.pattern(static, path, Tail.STAR, MarkSlot.STAR)
                val position = RequestKind.Position(f.ap.path(path)!!)
                for (key in listOf(middle, callee)) {
                    // Preserve the old protocol assertions on the independent scalar algorithm.
                    assertContains(scalar.requests, ObservedRequest(key, policyInitial, position))
                    assertContains(scalar.requests, ObservedRequest(key, positionInitial, RequestKind.Mark(t.mark)))
                    assertContains(actual.initials, ObservedInitial(key, f.pattern(static, path, Tail.EXACT, t)))
                }
                assertTrue(actual.requests.none { it.kind is RequestKind.Position })
                assertContains(actual.rawSummaries, ObservedEdge(caller, zero,
                    Conclusion(PathFact(y, emptyList(), Tail.EXACT, u), ExclusionSet.Empty, false)))
                assertTrue(actual.sourceHits.isEmpty())
            }
    }

    @Test
    fun `whole program E6 needs every member at the same consumer call and joins the added layers`(): Unit {
        for (mixed in listOf(false, true)) for (policy in listOf(SchedulePolicy.REPLAY_FIRST, SchedulePolicy.NOTIFY_FIRST))
            for (sameUnit in listOf(false, true)) FiniteProgramFixtures().use { f ->
                val producerMethod = f.method("twoLiteralProducer", listOf(listOf(1), listOf(2), emptyList()), 2)
                val consumerMethod = f.method("oneLiteralConsumer", listOf(listOf(1), listOf(2), emptyList()), 2)
                val joinMethod = f.method("joinedExit", listOf(listOf(1), emptyList()), 1)
                val producer = producerMethod.key()
                val consumer = consumerMethod.key()
                val callee = joinMethod.key()
                val a = f.field("a")
                val b = f.field("b")
                val t = MarkSlot.Concrete(f.mark("A"))
                val u = MarkSlot.Concrete(f.mark("B"))
                val v = MarkSlot.Concrete(f.mark("C"))
                val sourceA = PathEdge(ZERO_FACT, PathFact(x, emptyList(), Tail.EXACT, t), ExclusionSet.Empty)
                val sourceB = PathEdge(ZERO_FACT, PathFact(y, if (mixed) listOf(a, b) else listOf(a), Tail.EXACT, u), ExclusionSet.Empty)
                f.entry(producer, RuleStatement(summary(ZERO_KEEP, sourceA, sourceB), StatementSummary.EMPTY, emptyList()))
                f.entry(consumer, RuleStatement(summary(ZERO_KEEP, sourceA), StatementSummary.EMPTY, emptyList()))
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
                f.exit(callee, 1, ExitRules(RuleStatement(
                    StatementSummary(emptySet(), emptyList(), listOf(ConjunctiveEdge(literals, target)), emptyMap()),
                    StatementSummary.EMPTY, emptyList()), false, emptySet()))
                val demand = listOf(
                    callee to DemandPattern(literals[0], f.pattern(returned, tail = Tail.ANY, mark = v)),
                    callee to DemandPattern(literals[1], f.pattern(returned, tail = Tail.ANY, mark = v)),
                    producer to DemandPattern(ZERO_PATTERN, f.pattern(returned, tail = Tail.ANY, mark = v)),
                    consumer to DemandPattern(ZERO_PATTERN, f.pattern(returned, tail = Tail.ANY, mark = v)),
                )
                val seeds = listOf(Seed.Source(producer, producerMethod.inst(0), sourceA),
                    Seed.Source(producer, producerMethod.inst(0), sourceB),
                    Seed.Source(consumer, consumerMethod.inst(0), sourceA))
                val actual = check(f, f.config(index = 3, roots = listOf(producer, consumer), demand = demand, seeds = seeds),
                    listOf(a, b), listOf(t.mark, u.mark, v.mark), sameUnit, policy)
                val members = setOf(literals[0], f.pattern(y, listOf(a), if (mixed) Tail.ANY else Tail.EXACT, u))
                val joined = ObservedEdge(callee, members, Conclusion(target, ExclusionSet.Empty, mixed))
                assertContains(actual.rawSummaries, joined)
                assertContains(actual.publications, joined)
                assertContains(actual.selectedDemand, joined)
                assertContains(actual.rawSummaries, ObservedEdge(producer, zero, Conclusion(target, ExclusionSet.Empty, mixed)))
                assertTrue(actual.rawSummaries.none { it.method == consumer && it.conclusion.fact.mark == v },
                    "A literal supplied by another caller cannot complete this call")
                assertTrue(actual.requests.isEmpty())
            }
    }

    @Test
    fun `whole program E6 joins an early delivered literal with a later cached replay at the same consumer call`(): Unit {
        for (mixed in listOf(false, true)) for (sameUnit in listOf(false, true)) FiniteProgramFixtures().use { f ->
            val producerMethod = f.method("splitProducer", listOf(listOf(1), listOf(2), emptyList()), 2)
            val sourceNode = 18
            val consumerMethod = f.method("splitConsumer", (0..sourceNode + 1).map { index ->
                when (index) {
                    sourceNode -> listOf(1, sourceNode + 1)
                    sourceNode + 1 -> emptyList()
                    else -> listOf(index + 1)
                }
            }, sourceNode + 1)
            val joinMethod = f.method("splitJoinedExit", listOf(listOf(1), emptyList()), 1)
            val producer = producerMethod.key()
            val consumer = consumerMethod.key()
            val callee = joinMethod.key()
            val a = f.field("a")
            val b = f.field("cut suffix")
            val signal = AccessPathBase.Argument(8)
            val t = MarkSlot.Concrete(f.mark("A"))
            val u = MarkSlot.Concrete(f.mark("B"))
            val v = MarkSlot.Concrete(f.mark("C"))
            val d = MarkSlot.Concrete(f.mark("delay signal"))
            val sourceA = PathEdge(ZERO_FACT, PathFact(x, emptyList(), Tail.EXACT, t), ExclusionSet.Empty)
            val sourceB = PathEdge(ZERO_FACT, PathFact(y, if (mixed) listOf(a, b) else listOf(a), Tail.EXACT, u), ExclusionSet.Empty)
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
            val expected = NaiveClosure(f, config, semantics = NaiveClosure.Semantics.SELECTED).run()
            val schedule = ProductionSchedule(f, config, sameUnit = sameUnit, policy = SchedulePolicy.NOTIFY_FIRST, quantum = 1)
            val actual = schedule.run()
            val universe = BoundedDenotation(listOf(AccessPathBase.Zero, x, y, returned, signal),
                listOf(a, b, f.field("fresh accessor")), listOf(t.mark, u.mark, v.mark, d.mark, f.mark("fresh mark"), TaintMark.ZERO), 2)
            try {
                actual.assertEquivalent(expected, universe)
                val members = setOf(literals[0], f.pattern(y, listOf(a), if (mixed) Tail.ANY else Tail.EXACT, u))
                val pub = ObservedEdge(callee, members, Conclusion(target, ExclusionSet.Empty, mixed))
                assertContains(actual.publications, pub)
                assertContains(actual.rawSummaries, ObservedEdge(consumer, zero, Conclusion(target, ExclusionSet.Empty, mixed)))
                val parts = schedule.subscriptions.filter { it.callee == callee && it.caller == consumer && it.added.base in setOf(x, y) }
                assertEquals(2, parts.size)
                assertEquals(setOf(literals[0], f.pattern(y, listOf(a), if (mixed) Tail.ANY else Tail.EXACT, u)),
                    parts.flatMap { f.ops.leaves(it.added).toList() }.toSet())
                assertTrue(parts.all { it.ref.premise.isZero && it.ref.call == consumerMethod.inst(1) })
                assertEquals(Layer.NORMAL, parts.single { it.added.base == x }.linkLayer)
                assertEquals(Layer.NORMAL, parts.single { it.added.base == x }.ref.callerLayer)
                assertEquals(if (mixed) Layer.DEMAND else Layer.NORMAL, parts.single { it.added.base == y }.linkLayer)
                assertEquals(if (mixed) Layer.DEMAND else Layer.NORMAL, parts.single { it.added.base == y }.ref.callerLayer)
                val aRegistration = schedule.trace.indexOfFirst { line ->
                    line.startsWith("subscribe callee=$callee caller=$consumer ") && line.contains("mark=$t")
                }
                val bRegistration = schedule.trace.indexOfFirst { line ->
                    line.startsWith("subscribe callee=$callee caller=$consumer ") && line.contains("mark=$u")
                }
                val delivery = schedule.trace.indexOfFirst { it.startsWith("Delivery($callee,") && it.contains("mark=$v") }
                assertTrue(aRegistration >= 0 && delivery > aRegistration && bRegistration > delivery,
                    "Consumer A receives the ND publication before consumer B is subscribed")
                assertTrue(schedule.trace.drop(bRegistration + 1).any { it == "replay" },
                    "Consumer B replays the already published ND result")
                assertTrue(actual.requests.isEmpty() && actual.sourceHits.isEmpty())
            } catch (failure: AssertionError) {
                throw AssertionError("mixed=$mixed sameUnit=$sameUnit program=${f.describe()} trace=${schedule.trace}", failure)
            }
        }
    }

    @Test
    fun `strong write exclusions selected cleaning and a positive field cut preserve independent marks`(): Unit {
        for (index in listOf(1, 3)) FiniteProgramFixtures().use { f ->
            val method = f.method("cleanAndWrite", listOf(listOf(1), listOf(2), emptyList()), 2)
            val key = method.key()
            val a = f.field("cleaned")
            val b = f.field("written")
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val mustSource = PathEdge(ZERO_FACT, PathFact(x, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.Empty)
            val deepSource = PathEdge(ZERO_FACT, PathFact(x, listOf(a, b), Tail.EXACT, u), ExclusionSet.Empty)
            f.entry(key, RuleStatement(summary(ZERO_KEEP, mustSource, deepSource), StatementSummary.EMPTY, emptyList()))
            f.call(key, 1, CallPlan(setOf(x), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(x), ZERO_KEEP)),
                CallStage.Actions(BOUND, ADDED, listOf(CleanStep.Clean(Cleaner(x, f.ap.path(listOf(a)), CleanReach.AT_AND_BELOW, t.mark)))),
                CallStage.Edges(ADDED, RETURNED, UNRESOLVED, summary(keepEdge(x, e = ExclusionSet.of(b)), ZERO_KEEP)),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(keepEdge(x), ZERO_KEEP)),
            ), emptyList(), BEFORE, AFTER))
            val demand = listOf(key to DemandPattern(ZERO_PATTERN, f.pattern(x, tail = Tail.ANY, mark = u)))
            val seeds = listOf(Seed.Source(key, method.inst(0), mustSource), Seed.Source(key, method.inst(0), deepSource))
            val actual = check(f, f.config(index = index, roots = listOf(key), demand = if (index == 1) emptyList() else demand,
                seeds = seeds), listOf(a, b), listOf(t.mark, u.mark))
            val must = ObservedEdge(key, zero, Conclusion(PathFact(x, emptyList(), Tail.ANY_TAINT, t),
                if (index == 1) ExclusionSet.Empty else ExclusionSet.of(intArrayOf(a, b)), index == 1))
            val may = ObservedEdge(key, zero, Conclusion(PathFact(x, listOf(a), Tail.ANY, u), ExclusionSet.Empty, true))
            assertContains(actual.rawSummaries, must)
            assertContains(actual.rawSummaries, may)
            assertContains(actual.selectedDemand, may)
            assertEquals(index == 1, actual.selectedDemand.any { it.conclusion.fact.mark == t })
            assertTrue(actual.requests.isEmpty())
        }
    }

    @Test
    fun `native must records retain E through returned reads and layer-only demotion while admitting sibling aliases`(): Unit {
        for (blocked in listOf(false, true)) FiniteProgramFixtures().use { f ->
            val main = f.method("pendingCaller", listOf(listOf(1), listOf(2), emptyList()), 2)
            val library = f.method("recordedLibrary", listOf(listOf(1), emptyList()), 1)
            val caller = main.key()
            val callee = library.key()
            val a = f.field("allowed")
            val b = f.field("blocked")
            val alias = AccessPathBase.Argument(7)
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val source = PathEdge(ZERO_FACT, PathFact(x, listOf(a), Tail.ANY_TAINT, t), ExclusionSet.Empty)
            f.entry(caller, RuleStatement(summary(ZERO_KEEP, source), StatementSummary.EMPTY, emptyList()))
            val original = call(callee, summary(starEdge(returned, listOf(if (blocked) b else a), y, emptyList()), ZERO_KEEP),
                listOf(CleanStep.Clean(Cleaner(returned, null, CleanReach.EXACT, f.mark("unrelated")))))
            f.call(caller, 1, CallPlan(original.touched, original.stages + CallStage.Edges(REWRITTEN, AFTER, ALIASES,
                summary(starEdge(returned, emptyList(), alias, emptyList())), Guard.MemoryEffect), emptyList(), BEFORE, AFTER))
            val initial = f.ap.initial(f.pattern(x, tail = Tail.ANY_TAINT, mark = t))
            val record = Record(callee, Direction.FORWARD, initial,
                f.fact(returned, tail = Tail.ANY_TAINT, mark = u, exclusion = ExclusionSet.of(b)))
            val demand = listOf(callee to DemandPattern(f.pattern(x, tail = Tail.ANY, mark = t),
                f.pattern(returned, tail = Tail.ANY, mark = u)),
                caller to DemandPattern(ZERO_PATTERN, f.pattern(y, tail = Tail.ANY, mark = u)))
            val actual = check(f, f.config(index = 3, roots = listOf(caller), demand = demand,
                records = listOf(callee to record), seeds = listOf(Seed.Source(caller, main.inst(0), source))),
                listOf(a, b), listOf(t.mark, u.mark))
            val outputs = actual.rawSummaries.filter { it.method == caller && it.conclusion.fact.mark == u }.toSet()
            val wanted = buildSet {
                add(ObservedEdge(caller, zero, Conclusion(PathFact(alias, emptyList(), Tail.ANY_TAINT, u), ExclusionSet.of(b), true)))
                if (!blocked) add(ObservedEdge(caller, zero,
                    Conclusion(PathFact(y, emptyList(), Tail.ANY_TAINT, u), ExclusionSet.Empty, true)))
            }
            assertEquals(wanted, outputs)
            assertTrue(actual.sourceHits.isEmpty() && actual.requests.isEmpty())
        }
    }

    @Test
    fun `native exact record identities and effects retain their paths and control memory aliases`(): Unit {
        for (identity in listOf(false, true)) for (sameUnit in listOf(false, true)) FiniteProgramFixtures().use { f ->
            val main = f.method("exactRecordCaller", listOf(listOf(1), listOf(2), emptyList()), 2)
            val library = f.method("exactRecordedLibrary", listOf(listOf(1), emptyList()), 1)
            val caller = main.key()
            val callee = library.key()
            val a = f.field("input field")
            val b = f.field("output field")
            val alias = AccessPathBase.Argument(7)
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val source = PathEdge(ZERO_FACT, PathFact(x, listOf(a), Tail.EXACT, t), ExclusionSet.Empty)
            assertTrue(MicroEdge.of(source).isSource)
            f.entry(caller, RuleStatement(summary(ZERO_KEEP, source), StatementSummary.EMPTY, emptyList()))
            val output = if (identity) source.to else PathFact(returned, listOf(b), Tail.EXACT, u)
            val original = call(callee, summary(starEdge(output.base, emptyList(), y, emptyList()), ZERO_KEEP))
            f.call(caller, 1, CallPlan(original.touched, original.stages +
                CallStage.Edges(REWRITTEN, AFTER, ALIASES,
                    summary(starEdge(output.base, emptyList(), alias, emptyList())), Guard.MemoryEffect),
                emptyList(), BEFORE, AFTER))
            val initial = f.ap.initial(f.pattern(x, listOf(a), Tail.EXACT, t))
            val record = Record(callee, Direction.FORWARD, initial, f.fact(output.base, output.path, output.tail, output.mark))
            val demand = listOf(callee to DemandPattern(f.pattern(x, tail = Tail.ANY, mark = MarkSlot.STAR),
                f.pattern(output.base, tail = Tail.STAR, mark = MarkSlot.STAR)),
                caller to DemandPattern(ZERO_PATTERN, f.pattern(y, tail = Tail.ANY, mark = output.mark)))
            val actual = check(f, f.config(index = 3, roots = listOf(caller), demand = demand,
                records = listOf(callee to record), seeds = listOf(Seed.Source(caller, main.inst(0), source))),
                listOf(a, b), listOf(t.mark, u.mark), sameUnit)
            val expected = buildSet {
                add(ObservedEdge(caller, zero, Conclusion(output.copy(base = y), ExclusionSet.Empty, false)))
                if (!identity) add(ObservedEdge(caller, zero,
                    Conclusion(output.copy(base = alias), ExclusionSet.Empty, false)))
            }
            assertEquals(expected, actual.rawSummaries.filter {
                it.method == caller && it.conclusion.fact.mark == output.mark
            }.toSet(), "A normal identity return suppresses memory aliases; an effect retains them")
            assertContains(actual.links, ObservedLink(callee, caller, zero, Layer.NORMAL, main.inst(1), Layer.NORMAL,
                f.pattern(x, listOf(a), Tail.EXACT, t)))
            assertTrue(actual.requests.isEmpty() && actual.sourceHits.isEmpty() && actual.selectedDemand.isEmpty())
        }
    }

    @Test
    fun `backward record views cover all native must shapes while zero callees publish balanced reach`(): Unit {
        for ((inputTail, outputTail) in listOf(Tail.EXACT to Tail.ANY_TAINT,
            Tail.ANY_TAINT to Tail.ANY_TAINT, Tail.ANY_TAINT to Tail.EXACT))
            for (sameUnit in listOf(false, true)) FiniteProgramFixtures().use { f ->
                val main = f.method("viewCaller", listOf(listOf(1), listOf(2), emptyList()), 2)
                val library = f.method("viewLibrary", listOf(listOf(1), emptyList()), 1)
                val caller = main.key()
                val callee = library.key()
                val a = f.field("input field")
                val b = f.field("excluded output field")
                val t = MarkSlot.Concrete(f.mark("T"))
                val u = MarkSlot.Concrete(f.mark("U"))
                f.call(caller, 1, call(callee))
                val initial = f.ap.initial(f.pattern(x, listOf(a), inputTail, t,
                    if (inputTail == Tail.ANY_TAINT) ExclusionSet.of(b) else ExclusionSet.Empty))
                val record = Record(callee, Direction.FORWARD, initial,
                    f.fact(returned, tail = outputTail, mark = u,
                        exclusion = if (outputTail == Tail.ANY_TAINT) ExclusionSet.of(b) else ExclusionSet.Empty))
                val config = f.config(index = 2, roots = listOf(caller), records = listOf(callee to record),
                    seeds = listOf(Seed.Sink(rule("view input"), caller, main.inst(2), f.pattern(y, mark = u))))
                val actual = check(f, config, listOf(a, b), listOf(t.mark, u.mark), sameUnit)
                val demand = inputTail == Tail.ANY_TAINT
                val result = ObservedEdge(caller, zero, Conclusion(PathFact(x, listOf(a),
                    if (demand) Tail.ANY else Tail.EXACT, t), ExclusionSet.Empty, demand))
                assertEquals(setOf(result), actual.rawSummaries.filter {
                    it.method == caller && it.conclusion.fact.mark == t
                }.toSet(), "The transient view retains an exact input or converts a must input to demand ANY")
                assertContains(actual.selectedDemand, result)
                assertContains(actual.initials, ObservedInitial(callee, ZERO_PATTERN))
                val balanced = ObservedEdge(callee, zero, Conclusion(ZERO_FACT, ExclusionSet.Empty, false))
                assertContains(actual.rawSummaries, balanced)
                assertContains(actual.publications, balanced)
                assertContains(actual.selectedDemand, balanced)
                assertTrue(actual.initials.none { it.method == callee && it.initial == initial.toPattern() },
                    "A native read view is not started as an initial fact")
                assertTrue(actual.requests.isEmpty() && actual.sourceHits.isEmpty() && actual.witnesses.isEmpty())
            }
    }

    @Test
    fun `backward zero publishes balanced reach and reversed end facts seed their sink without synthetic source hits`(): Unit {
        FiniteProgramFixtures().use { f ->
            val method = f.method("endTrigger", listOf(listOf(1), listOf(2), emptyList()), 2)
            val key = method.key()
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val source = PathEdge(ZERO_FACT, PathFact(x, emptyList(), Tail.EXACT, t), ExclusionSet.Empty)
            val end = PathEdge(ZERO_FACT, PathFact(returned, emptyList(), Tail.EXACT, u), ExclusionSet.Empty)
            assertTrue(MicroEdge.of(source).isSource && MicroEdge.of(end).isSource)
            f.statement(key, 1, summary(ZERO_KEEP, source))
            val id = rule("end trigger")
            val sink = SinkRule(id, 0, listOf(f.pattern(x, mark = t)), listOf(MicroEdge.of(end)))
            f.exit(key, 2, ExitRules(RuleStatement(StatementSummary.EMPTY, summary(end), listOf(sink)), false, emptySet()))
            val config = f.config(index = 2, roots = listOf(key),
                seeds = listOf(Seed.Sink(id, key, method.inst(2), f.pattern(returned, mark = u))))
            val actual = check(f, config, emptyList(), listOf(t.mark, u.mark))
            val zeroSummary = ObservedEdge(key, zero, Conclusion(ZERO_FACT, ExclusionSet.Empty, false))
            assertContains(actual.rawSummaries, zeroSummary)
            assertContains(actual.publications, zeroSummary)
            assertContains(actual.selectedDemand, zeroSummary)
            assertContains(actual.processed, ObservedEdge(key, zero,
                Conclusion(PathFact(x, emptyList(), Tail.EXACT, t), ExclusionSet.Empty, false), method.inst(2)))
            assertEquals(setOf(ObservedSourceHit(key, method.inst(1), source)), actual.sourceHits)
            assertTrue(actual.requests.isEmpty() && actual.witnesses.isEmpty())
        }
    }
}
