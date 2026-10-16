package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.*
import kotlin.test.*

/** Complete finite closure controls, with literal expected results and an independent positive support oracle. */
class PredicateClosureTest {
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Argument(1)
    private val z = AccessPathBase.Argument(2)
    private val endBase = AccessPathBase.Argument(3)
    private val returned = AccessPathBase.Return
    private val resultBase = AccessPathBase.Argument(4)
    private val finalEnd = AccessPathBase.Argument(5)
    private val zero = setOf(ZERO_PATTERN)

    private data class Program(val root: MethodKey, val callee: MethodKey, val sink: SinkRule,
                               val sources: List<Pair<org.opentaint.ir.api.common.cfg.CommonInst, PathEdge>>,
                               val fields: List<Int>, val marks: List<TaintMark>, val output: PathFact, val end: PathFact)

    private fun program(f: DriverFixtures, width: Int = 1): Program {
        val p = f.program
        val rootMethod = p.method("predicate caller", listOf(listOf(1, 2), listOf(3), listOf(4), listOf(5), listOf(3), emptyList()), 5)
        val calleeMethod = p.method("predicate callee", listOf(listOf(1), emptyList()), 1)
        val root = rootMethod.key("caller context")
        val callee = calleeMethod.key("callee context")
        val xMarks = List(width) { p.mark(if (it == 0) "T" else "T$it") }
        val t = MarkSlot.Concrete(xMarks.first())
        val u = MarkSlot.Concrete(p.mark("U"))
        val v = MarkSlot.Concrete(p.mark("V"))
        val out = MarkSlot.Concrete(p.mark("Out"))
        val end = MarkSlot.Concrete(p.mark("End"))
        val fields = listOf(p.field("a"), p.field("b"))
        val sourceX = PathEdge(ZERO_FACT, p.pattern(x, mark = t).fact, ExclusionSet.Empty)
        val xSources = xMarks.map { PathEdge(ZERO_FACT, p.pattern(x, mark = MarkSlot.Concrete(it)).fact, ExclusionSet.Empty) }
        val sourceY = PathEdge(ZERO_FACT, p.pattern(y, fields, mark = u).fact, ExclusionSet.Empty)
        val sourceZ = PathEdge(ZERO_FACT, p.pattern(z, mark = v).fact, ExclusionSet.Empty)
        p.entry(root, RuleStatement(f.summary(*(listOf(ZERO_KEEP) + xSources).toTypedArray()), StatementSummary.EMPTY, emptyList()))
        p.statement(root, 1, f.summary(ZERO_KEEP, sourceZ))
        p.statement(root, 4, f.summary(ZERO_KEEP, sourceY))
        val call = CallPlan(setOf(x, y, z, resultBase, finalEnd), listOf(
            CallStage.Edges(CallPoint.BEFORE, CallPoint.BOUND, StageKind.BIND_IN,
                f.summary(keepEdge(x), keepEdge(y), keepEdge(z), ZERO_KEEP)),
            CallStage.Actions(CallPoint.BOUND, CallPoint.ADDED, emptyList()),
            CallStage.Callees(CallPoint.ADDED, CallPoint.RETURNED, listOf(callee)),
            CallStage.Actions(CallPoint.RETURNED, CallPoint.REWRITTEN, emptyList()),
            CallStage.Edges(CallPoint.REWRITTEN, CallPoint.AFTER, StageKind.BIND_BACK,
                f.summary(keepEdge(x), keepEdge(y), keepEdge(z), ZERO_KEEP,
                    starEdge(returned, emptyList(), resultBase, emptyList()),
                    starEdge(endBase, emptyList(), finalEnd, emptyList()))),
        ), emptyList(), CallPoint.BEFORE, CallPoint.AFTER)
        p.call(root, 3, call)
        p.entry(callee, RuleStatement(f.summary(ZERO_KEEP, keepEdge(x), keepEdge(y), keepEdge(z)),
            StatementSummary.EMPTY, emptyList()))
        val form = FormKey(FormOwner(), callee.method, callee, calleeMethod.inst(1))
        val sourceId = RuleOccurrenceId(form, RuleKind.EXIT_SOURCE, 0)
        val builder = RulePredicate.Builder(p.cancellation)
        val px = builder.or(xMarks.map { builder.leaf(p.pattern(x, mark = MarkSlot.Concrete(it))) })
        val py = builder.leaf(p.pattern(y, tail = Tail.ANY, mark = u))
        val pz = builder.leaf(p.pattern(z, mark = v))
        // The y branch arrives later; either x or z can provide the other independent input.
        val condition = builder.build(builder.and(listOf(builder.or(listOf(px, pz)), py)))
        val output = p.pattern(returned, mark = out).fact
        val descriptor = SourceRuleOccurrence(sourceId, condition,
            listOf(SourceAction(ActionId(sourceId, ActionRole.SOURCE, 0), output)))
        val sinkBuilder = RulePredicate.Builder(p.cancellation)
        val a = sinkBuilder.leaf(Pattern(output, ExclusionSet.Empty))
        val b = sinkBuilder.leaf(p.pattern(z, mark = v))
        val predicate = sinkBuilder.build(sinkBuilder.or(listOf(a, sinkBuilder.and(listOf(a, b)))))
        val endFact = p.pattern(endBase, mark = end).fact
        val endEdge = MicroEdge.of(PathEdge(ZERO_FACT, endFact, ExclusionSet.Empty))
        val sink = SinkRule(f.rule("predicate sink"), 0, predicate.leaves, listOf(endEdge),
            RuleOccurrenceId(form, RuleKind.EXIT_SINK, 0), predicate)
        val summary = StatementSummary(setOf(AccessPathBase.Zero, x, y, z),
            listOf(ZERO_KEEP, keepEdge(x), keepEdge(y), keepEdge(z)).map(MicroEdge::of),
            emptyList(), emptyMap(), sources = listOf(descriptor))
        p.exit(callee, 1, ExitRules(RuleStatement(summary,
            StatementSummary(emptySet(), listOf(endEdge), emptyList(), emptyMap()), listOf(sink)), true, emptySet()))
        return Program(root, callee, sink, listOf(rootMethod.inst(0) to sourceX,
            rootMethod.inst(4) to sourceY, rootMethod.inst(1) to sourceZ), fields,
            xMarks + listOf(u.mark, v.mark, out.mark, end.mark), output, endFact)
    }

    private fun universe(f: DriverFixtures, p: Program) = BoundedDenotation(
        listOf(AccessPathBase.Zero, x, y, z, endBase, returned, resultBase, finalEnd, AccessPathBase.Argument(17)),
        p.fields + f.program.field("fresh"), p.marks + f.program.mark("fresh") + TaintMark.ZERO, 2)

    private fun compare(f: DriverFixtures, p: Program, config: RunConfig): RunObservation {
        val expected = NaiveClosure(f.program, config, semantics = NaiveClosure.Semantics.SELECTED).run()
        val raw = RawCurrentClosure(f.program, config, f.program.inputs(config)).run()
        expected.assertEquivalent(raw, universe(f, p))
        for (sameUnit in listOf(false, true)) for (policy in listOf(SchedulePolicy.FIFO,
            SchedulePolicy.REPLAY_FIRST, SchedulePolicy.NOTIFY_FIRST)) {
            val scheduler = ProductionSchedule(f.program, config, sameUnit, policy, seed = 73L,
                quantum = 1, duplicateDeliveries = true)
            val actual = scheduler.run()
            try { expected.assertEquivalent(actual, universe(f, p)) }
            catch (e: AssertionError) { throw AssertionError("run=${config.index} L=${config.fieldLimit} " +
                "seed=73 sameUnit=$sameUnit policy=$policy source/sink predicates " +
                "${p.sink.predicate?.leaves} program=${f.program.describe()} trace=${scheduler.trace}", e) }
        }
        return expected
    }

    private fun retained(result: RunResult<RunMethodAnalyzer>, ops: ApOps): RunObservation = RunObservation(
        rawSummaries = result.analyzers.flatMap { actor -> actor.summaries.all().flatMap { (pr, facts) ->
            observedEdges(ops, actor.key, pr, facts).asSequence()
        }.toList() }.toSet(),
        selectedDemand = result.analyzers.flatMap { actor -> actor.summaries.demandEdges().flatMap { (pr, facts) ->
            observedEdges(ops, actor.key, pr, facts).asSequence()
        }.toList() }.toSet(),
        sourceHits = result.analyzers.flatMap { actor -> actor.sourceHits?.entries()?.map { (method, node, edge) ->
            ObservedSourceHit(method, node, edge)
        }?.toList().orEmpty() }.toSet(),
        witnesses = result.witnessesByKey().flatMap { (key, values) -> values.map { observedWitness(ops, key, it) } }.toSet(),
    )

    private fun retained(observation: RunObservation) = RunObservation(rawSummaries = observation.rawSummaries,
        selectedDemand = observation.selectedDemand, sourceHits = observation.sourceHits, witnesses = observation.witnesses)

    @Test
    fun `wide source and sink NNF requests preserve full scalar closure across batch schedules`(): Unit {
        for (limit in listOf(1)) DriverFixtures().use { f ->
            val p = program(f, width = 8)
            val config = f.program.config(L = limit, roots = listOf(p.root))
            val expected = NaiveClosure(f.program, config, semantics = NaiveClosure.Semantics.SELECTED).run()
            val raw = RawCurrentClosure(f.program, config, f.program.inputs(config)).run()
            expected.assertEquivalent(raw, universe(f, p))
            assertTrue(expected.requests.mapNotNull { (it.kind as? RequestKind.Mark)?.mark }
                .toSet().containsAll(p.marks.take(8)))
            for (sameUnit in listOf(false, true)) for (quantum in listOf(1, 7)) for (seed in listOf(19L, 73L)) {
                val scheduler = ProductionSchedule(f.program, config, sameUnit, SchedulePolicy.SEEDED,
                    seed = seed, quantum = quantum, duplicateDeliveries = true)
                try { expected.assertEquivalent(scheduler.run(), universe(f, p)) }
                catch (e: AssertionError) { throw AssertionError("wide requests L=$limit seed=$seed " +
                    "sameUnit=$sameUnit quantum=$quantum trace=${scheduler.trace}", e) }
            }
            val result = f.run(config)
            assertEquals(RunStatus.COMPLETE, result.status)
            retained(expected).assertEquivalent(retained(result, f.ops), universe(f, p))
        }
    }

    @Test
    fun `delayed AND OR sources and NNF sink END agree through complete real forward run at the selected first limit`(): Unit {
        for (limit in listOf(1)) DriverFixtures().use { f ->
            val p = program(f)
            val config = f.program.config(L = limit, roots = listOf(p.root))
            val expected = compare(f, p, config)
            // AP 6.2 starts each nonstatic policy at the base root. Under 4.5, Y's deeper
            // added fact answers that request with root STAR/U, not its exact added path.
            // AP 6.5 starts concrete STAR as ANY/DEMAND; every source proof requires Y.
            // Thus the source and END stay demanded even when the original path fits L.
            val demand = true
            assertContains(expected.rawSummaries, ObservedEdge(p.root, zero,
                Conclusion(p.output.copy(base = resultBase), ExclusionSet.Empty, demand)))
            assertContains(expected.rawSummaries, ObservedEdge(p.root, zero,
                Conclusion(p.end.copy(base = finalEnd), ExclusionSet.Empty, demand)))
            // AP 4.4 cuts only after more than L counted fields: length two fits L=2/3.
            val ySource = PathFact(y, if (limit == 1) p.fields.take(1) else p.fields,
                if (limit == 1) Tail.ANY else Tail.EXACT, MarkSlot.Concrete(p.marks[1]))
            assertContains(expected.rawSummaries, ObservedEdge(p.root, zero,
                Conclusion(ySource, ExclusionSet.Empty, demand = limit == 1)))
            assertEquals(p.marks.take(3).toSet(), expected.requests.map { (it.kind as RequestKind.Mark).mark }.toSet())
            assertTrue(expected.requests.all { it.method == p.callee })
            assertTrue(expected.witnesses.isNotEmpty())
            assertEquals(setOf(1, 2), expected.witnesses.map { it.edges.size }.toSet())
            assertTrue(expected.witnesses.all { it.proof != null && it.methodKey == p.callee &&
                it.edges.first().layer == if (demand) Layer.DEMAND else Layer.NORMAL })
            val result = f.run(config)
            assertEquals(RunStatus.COMPLETE, result.status)
            retained(expected).assertEquivalent(retained(result, f.ops), universe(f, p))
        }
    }

    @Test
    fun `actual compressed backward handoff decodes all positive branches and agrees on full closure`(): Unit {
        DriverFixtures().use { f ->
            val p = program(f)
            val forward = f.program.config(L = 1, roots = listOf(p.root))
            val expected = compare(f, p, forward)
            val result = f.run(forward)
            assertEquals(RunStatus.COMPLETE, result.status)
            val report = ReportBuilder().also { it.add(forward, result) }
            assertEquals(1, report.demandEntries().size)
            val handoff = HandOff.of(forward, result, report, f.shared)
            assertEquals(1, handoff.seeds.size, "One physical full-condition seed, rather than one seed per leaf")
            val node = f.program.inst(p.callee, 1)
            val physical = assertIs<Seed.PredicateSink>(handoff.seeds.at(p.callee, node).single())
            assertEquals(p.sink.occurrenceId, physical.occurrence)
            assertEquals(p.callee, physical.method)
            assertSame(node, physical.statement)
            // The scalar reference gives a minimal logical inventory. T5 is disabled: a late
            // root ANY does not remove an earlier stored child. HandOff expands retained leaves
            // and exact-deduplicates them; it does not require this minimal representation.
            val canonical = expected.selectedDemand.filter { edge -> expected.selectedDemand.none { other ->
                other != edge && other.method == edge.method && other.premise == edge.premise &&
                    other.conclusion.demand && edge.conclusion.demand &&
                    covers(Pattern(other.conclusion.fact, other.conclusion.exclusion),
                        Pattern(edge.conclusion.fact, edge.conclusion.exclusion))
            } }
            val demand = canonical.flatMap { edge -> edge.premise.map { premise ->
                val value = edge.conclusion
                val entry = Pattern(value.fact.copy(
                    tail = if (value.fact.tail == Tail.STAR || value.fact.tail == Tail.ANY_TAINT) Tail.ANY else value.fact.tail,
                    mark = if (value.fact.mark is MarkSlot.Star) MarkSlot.STAR else value.fact.mark),
                    if (value.fact.tail == Tail.EXACT) value.exclusion else ExclusionSet.Empty)
                edge.method to DemandPattern(entry, premise)
            } }.distinct()
            // All F1 normal publications and the stored static defaults join demand
            // guidance. Repeated rows still exact-deduplicate after expansion.
            assertEquals(mapOf(p.root to 7, p.callee to 12), demand.groupingBy { it.first }.eachCount())
            for ((method, pattern) in demand) assertContains(handoff.demand.near(method, pattern.entry.fact.base,
                f.manager.path(pattern.entry.fact.path)), pattern)
            val bounded = universe(f, p)
            fun demandCells(values: List<Pair<MethodKey, DemandPattern>>) = values.groupBy { it.first to it.second.exit }
                .mapValues { (_, rows) -> rows.flatMap { bounded.marked(it.second.entry) }.toSet() }
            val rawDemand = expected.selectedDemand.flatMap { edge -> edge.premise.map { premise ->
                val value = edge.conclusion
                val entry = Pattern(value.fact.copy(
                    tail = if (value.fact.tail == Tail.STAR || value.fact.tail == Tail.ANY_TAINT) Tail.ANY else value.fact.tail,
                    mark = if (value.fact.mark is MarkSlot.Star) MarkSlot.STAR else value.fact.mark),
                    if (value.fact.tail == Tail.EXACT) value.exclusion else ExclusionSet.Empty)
                edge.method to DemandPattern(entry, premise)
            } }.distinct()
            assertEquals(demandCells(rawDemand), demandCells(demand), "Canonicalization preserves the complete required-location union")
            // Enumerate actual candidates only at this observation boundary. All L1 entries
            // lie in this independent universe, and covering excludes the implicit zero demand.
            val observedDemand = listOf(p.root, p.callee).flatMap { method ->
                bounded.positions.flatMap { position ->
                    handoff.demand.covering(method, position.base, f.manager.path(position.path)).map { method to it }
                } + handoff.demand.covering(method, STATIC, null).map { method to it }
            }.distinct()
            val earlierChild = p.root to DemandPattern(f.program.pattern(y, p.fields.take(1), Tail.ANY,
                MarkSlot.Concrete(p.marks[1])), ZERO_PATTERN)
            assertContains(rawDemand, earlierChild, "The independent scalar history contains the cut Y child")
            val rawHandoff = (demand + earlierChild).toSet()
            assertEquals(rawHandoff, observedDemand.toSet(), "Pin every full method/exit/path/tail/mark/E of this schedule")
            assertEquals(mapOf(p.root to 8, p.callee to 12), handoff.demandEdges)
            assertEquals(demandCells(rawDemand), demandCells(observedDemand))
            val seed = Seed.PredicateSink(checkNotNull(p.sink.occurrenceId), p.callee, node)
            // Use the actual physical handoff. A covered child can still emit a narrower initial,
            // so F1 denotation alone does not prove this next-run closure.
            val backward = f.program.config(index = 2, L = 1, roots = listOf(p.root), demand = observedDemand, seeds = listOf(seed))
            val rawRequirements = rawSinkSeedRequirements(f.program, f.program.inputs(backward), p.callee, node)
            assertEquals(p.sink.predicate!!.leaves, rawRequirements)
            assertEquals(1, backward.seeds.size)
            val expectedBackward = compare(f, p, backward)
            assertTrue(expectedBackward.requests.isEmpty())
            assertEquals(p.sources.map { it.second }.toSet(), expectedBackward.sourceHits.map { it.forward }.toSet())
            assertTrue(expectedBackward.processed.any { it.method == p.callee && it.conclusion.fact.base == z &&
                it.conclusion.fact.mark == MarkSlot.Concrete(p.marks[2]) })
            val joined = f.run(backward)
            assertEquals(RunStatus.COMPLETE, joined.status)
            retained(expectedBackward).assertEquivalent(retained(joined, f.ops), universe(f, p))
        }
    }

    @Test
    fun `batched NNF iteration preserves scalar records support and final report through run three`(): Unit {
        DriverFixtures().use { f ->
            val p = program(f)
            val limits = listOf(1, 1, 2)
            val reference = RawPredicateIteration(f.program, listOf(p.root), limits).run()
            val report = f.driver(FixedLimits(limits)).analyze(listOf(p.root))
            assertEquals(RunStatus.COMPLETE, report.end.status)
            assertEquals(3, report.end.run)
            assertEquals(Direction.FORWARD, report.end.direction)
            assertEquals(EndReason.NO_DEMAND_EDGE, report.end.reason)
            val states = report.entries.associate { HistoricalFinding(it.key.rule, it.key.statement) to
                if (it.state == ReportState.CONFIRMED) HistoricalState.CONFIRMED else HistoricalState.DEMAND }
            assertEquals(reference.findings, states)
            assertEquals(reference.runs.map { it.index }, f.results.map { it.runIndex })
            assertEquals(listOf(1, 2, 3), f.results.map { it.runIndex })
            for ((expected, actual) in reference.runs.zip(f.results)) {
                assertEquals(RunStatus.COMPLETE, actual.status)
                val want = expected.observation
                val observed = retained(actual, f.ops)
                RunObservation(rawSummaries = want.rawSummaries, selectedDemand = want.selectedDemand,
                    sourceHits = want.sourceHits, witnesses = want.witnesses).assertEquivalent(
                    RunObservation(rawSummaries = observed.rawSummaries, selectedDemand = observed.selectedDemand,
                        sourceHits = observed.sourceHits, witnesses = observed.witnesses), universe(f, p))
            }
            val expectedRecords = reference.runs.last().handOff.records
            val observedRecords = listOf(p.root, p.callee).flatMap { method -> universe(f, p).positions.flatMap { position ->
                f.records.byEntry(method, f.program.pattern(position.base, position.path, Tail.STAR, MarkSlot.STAR)).toList()
            } }.distinct().flatMap { record -> f.ops.leaves(record.conclusion).map {
                RawRecord(record.method, record.direction, record.premise.toPattern(), Conclusion(it.fact, it.exclusion, false))
            }.toList() }
            for (direction in Direction.entries) {
                fun rows(records: List<RawRecord>) = RunObservation(rawSummaries = records.filter { it.direction == direction }
                    .map { ObservedEdge(it.method, setOf(it.premise), it.conclusion) }.toSet())
                rows(expectedRecords).assertEquivalent(rows(observedRecords), universe(f, p))
            }
        }
    }

    @Test
    fun `full witness observation distinguishes OR proofs whose matched must facts and premises are equal`(): Unit {
        DriverFixtures().use { f ->
            val p = f.program
            val root = p.method("two equal-looking proofs", listOf(listOf(1), emptyList()), 1).key()
            val field = p.field("f")
            val mark = MarkSlot.Concrete(p.mark("T"))
            val source = PathEdge(ZERO_FACT, p.pattern(x, tail = Tail.ANY_TAINT, mark = mark).fact, ExclusionSet.Empty)
            p.entry(root, RuleStatement(f.summary(ZERO_KEEP, source), StatementSummary.EMPTY, emptyList()))
            val builder = RulePredicate.Builder(p.cancellation)
            val rootRead = builder.leaf(p.pattern(x, mark = mark))
            val childRead = builder.leaf(p.pattern(x, listOf(field), mark = mark))
            val predicate = builder.build(builder.or(listOf(rootRead, childRead)))
            val id = RuleOccurrenceId(FormKey(FormOwner(), root.method, root, p.inst(root, 1)), RuleKind.EXIT_SINK, 0)
            val sink = SinkRule(f.rule("equal-looking proof sink"), 0, predicate.leaves, emptyList(), id, predicate)
            p.exit(root, 1, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, listOf(sink)), false, emptySet()))
            val config = p.config(roots = listOf(root))
            val expected = NaiveClosure(p, config, semantics = NaiveClosure.Semantics.SELECTED).run()
            val actual = ProductionSchedule(p, config, quantum = 1).run()
            val universe = BoundedDenotation(listOf(AccessPathBase.Zero, x), listOf(field, p.field("fresh")),
                listOf(mark.mark, p.mark("fresh")), 2)
            expected.assertEquivalent(actual, universe)
            assertEquals(2, actual.witnesses.size)
            assertEquals(2, actual.witnesses.map { it.proof }.toSet().size)
            assertEquals(1, actual.witnesses.map { it.edges }.toSet().size)
            val lostProof = actual.copy(witnesses = setOf(actual.witnesses.first()))
            assertNotEquals(expected.semantic(universe), lostProof.semantic(universe))
        }
    }

    @Test
    fun `independent repeated leaf support keeps one binding and publishes full matched fact growth`(): Unit {
        DriverFixtures().use { f ->
            val p = f.program
            val method = p.method("raw support", listOf(emptyList()), 0).key()
            val a = p.field("a")
            val b = p.field("b")
            val builder = RulePredicate.Builder(p.cancellation)
            val leaf = builder.leaf(p.pattern(x, tail = Tail.ANY))
            val other = builder.leaf(p.pattern(y))
            val predicate = builder.build(builder.and(listOf(builder.or(listOf(leaf, other)), leaf)))
            val id = RuleOccurrenceId(FormKey(FormOwner(), method.method, method, method.statement), RuleKind.EXIT_SINK, 0)
            val supports = RawPredicateSupports(p.cancellation)
            val firstPremise = setOf(p.pattern(x))
            val secondPremise = setOf(p.pattern(z))
            val first = Conclusion(p.pattern(x, listOf(a)).fact, ExclusionSet.Empty, false)
            val growth = Conclusion(p.pattern(x, listOf(b)).fact, ExclusionSet.Empty, false)
            val ordinal = predicate.leaves.indexOf(p.pattern(x, tail = Tail.ANY))
            val initial = supports.add(method, method.statement, id, predicate, ordinal, firstPremise, first)
            assertEquals(1, initial.size)
            assertEquals(listOf(firstPremise), initial.single().inputs.map { it.premise })
            val grown = supports.add(method, method.statement, id, predicate, ordinal, firstPremise, growth)
            assertEquals(initial.single().proof, grown.single().proof)
            assertEquals(setOf(first, growth), grown.single().inputs.single().facts)
            assertTrue(supports.add(method, method.statement, id, predicate, ordinal, firstPremise, growth).isEmpty())
            val independent = supports.add(method, method.statement, id, predicate, ordinal, secondPremise, first)
            assertEquals(1, independent.size)
            assertEquals(listOf(secondPremise), independent.single().inputs.map { it.premise })
            val yOrdinal = predicate.leaves.indexOf(p.pattern(y))
            val joined = supports.add(method, method.statement, id, predicate, yOrdinal, zero,
                Conclusion(p.pattern(y).fact, ExclusionSet.Empty, true))
            assertEquals(2, joined.size)
            assertTrue(joined.all { it.inputs.size == 2 && it.layer == Layer.DEMAND })
            assertEquals(setOf(firstPremise, secondPremise), joined.map { it.inputs.first().premise }.toSet())
        }
    }
}
