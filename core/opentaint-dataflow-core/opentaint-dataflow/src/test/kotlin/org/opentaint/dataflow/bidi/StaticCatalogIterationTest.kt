package org.opentaint.dataflow.bidi

import org.junit.jupiter.api.Test
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** Real joined F1/B2/F3 engines versus the unchanged scalar positive-NNF iteration oracle. */
class StaticCatalogIterationTest {
    private val returned = AccessPathBase.Return
    private val result = AccessPathBase.Argument(1)

    private data class Program(val roots: List<MethodKey>, val callee: MethodKey, val sink: SinkRule,
                               val fields: List<Int>, val marks: List<TaintMark>, val output: PathFact,
                               val sources: Set<PathEdge>)

    private fun program(f: DriverFixtures, crossed: Boolean): Program {
        val p = f.program
        val roots = List(if (crossed) 2 else 1) { index ->
            p.method("catalog root $index", listOf(listOf(1), listOf(2), emptyList()), 2).key("root $index")
        }
        val callee = p.method("catalog NNF callee", listOf(listOf(1), emptyList()), 1).key("callee context")
        val cls = p.ap.accessors.index(ClassStaticAccessor("Box"))
        val sf = p.field("static f"); val sg = p.field("static g")
        val a = p.field("result a"); val b = p.field("result b")
        val t = MarkSlot.Concrete(p.mark("T")); val u = MarkSlot.Concrete(p.mark("U"))
        val v = MarkSlot.Concrete(p.mark("V")); val end = MarkSlot.Concrete(p.mark("End"))
        val first = p.pattern(STATIC, listOf(cls, sf), mark = t)
        val second = p.pattern(STATIC, listOf(cls, sg), mark = u)
        val sources = listOf(first, second).map { PathEdge(ZERO_FACT, it.fact, ExclusionSet.Empty) }
        val call = CallPlan(setOf(STATIC, returned, result), listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, f.summary(keepEdge(STATIC), ZERO_KEEP)),
            CallStage.Actions(BOUND, ADDED, emptyList()), CallStage.Callees(ADDED, RETURNED, listOf(callee)),
            CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK,
                f.summary(keepEdge(STATIC), starEdge(returned, emptyList(), result, emptyList()), ZERO_KEEP)),
        ), emptyList(), BEFORE, AFTER)
        for ((index, root) in roots.withIndex()) {
            val selected = if (crossed) listOf(sources[index]) else sources
            p.entry(root, RuleStatement(f.summary(*(listOf(ZERO_KEEP) + selected).toTypedArray()),
                StatementSummary.EMPTY, emptyList()))
            p.call(root, 1, call)
        }
        val node = p.inst(callee, 1)
        val form = FormKey(FormOwner(), callee.method, callee, node)
        val sourceId = RuleOccurrenceId(form, RuleKind.EXIT_SOURCE, 0)
        val sourceBuilder = RulePredicate.Builder(p.cancellation)
        val one = sourceBuilder.leaf(first); val two = sourceBuilder.leaf(second)
        val sourceCondition = sourceBuilder.build(sourceBuilder.and(listOf(one, sourceBuilder.or(listOf(two, two)))))
        val output = p.pattern(returned, listOf(a, b), mark = v).fact
        val source = SourceRuleOccurrence(sourceId, sourceCondition,
            listOf(SourceAction(ActionId(sourceId, ActionRole.SOURCE, 0), output)))
        val sinkBuilder = RulePredicate.Builder(p.cancellation)
        val value = sinkBuilder.leaf(Pattern(output, ExclusionSet.Empty))
        val state = sinkBuilder.leaf(second)
        val condition = sinkBuilder.build(sinkBuilder.or(listOf(value, sinkBuilder.and(listOf(value, state)))))
        val endFact = p.pattern(returned, mark = end).fact
        val endEdge = MicroEdge.of(PathEdge(ZERO_FACT, endFact, ExclusionSet.Empty))
        val sink = SinkRule(f.rule("catalog NNF sink"), 0, condition.leaves, listOf(endEdge),
            RuleOccurrenceId(form, RuleKind.EXIT_SINK, 0), condition)
        val body = StatementSummary(setOf(returned), listOf(MicroEdge.of(ZERO_KEEP)), emptyList(), emptyMap(),
            sources = listOf(source))
        p.exit(callee, 1, ExitRules(RuleStatement(body,
            StatementSummary(emptySet(), listOf(endEdge), emptyList(), emptyMap()), listOf(sink)), true, emptySet()))
        return Program(roots, callee, sink, listOf(cls, sf, sg, a, b), listOf(t.mark, u.mark, v.mark, end.mark),
            output, sources.toSet())
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

    /** Additional field FLOW records can refine the root policy without changing its complete
     *  relation. Native records are compared as direction/method/kind-scoped pair unions here;
     *  full premise/literal/caller headers remain checked by the actual witnesses and closure tests. */
    private fun nativeRelations(rows: List<RawRecord>, universe: BoundedDenotation): Map<Triple<MethodKey, Direction,
        ObservationComponent>, Set<TestLocationPair>> = rows.groupBy { record ->
        Triple(record.method, record.direction, when {
            record.conclusion.fact.base == AccessPathBase.Zero -> ObservationComponent.REACH
            record.conclusion.fact.mark is MarkSlot.Star -> ObservationComponent.FLOW
            record.conclusion.fact.tail == Tail.EXACT -> ObservationComponent.NORMAL_EXACT
            else -> ObservationComponent.NORMAL_MUST
        })
    }.mapValues { (_, records) -> records.flatMap { universe.pairs(it.premise, it.conclusion) }.toSet() }

    @Test
    fun `catalog NNF END iteration preserves scalar records handoffs and complete support at limits one two three`(): Unit {
        for (crossed in listOf(false, true)) for (sameUnit in listOf(false, true)) DriverFixtures().use { f ->
            val p = program(f, crossed)
            val limits = listOf(1, 2, 3)
            val reference = RawPredicateIteration(f.program, p.roots, limits).run()
            val universe = BoundedDenotation(listOf(AccessPathBase.Zero, STATIC, returned, result, AccessPathBase.Argument(17)),
                p.fields + f.program.field("fresh"), p.marks + f.program.mark("fresh") + TaintMark.ZERO, 2)
            // First check the full forward closure before actual engines freeze their working stores.
            val forward = f.program.config(L = 1, roots = p.roots)
            val scalar = NaiveClosure(f.program, forward, semantics = NaiveClosure.Semantics.SELECTED).run()
            for (policy in listOf(SchedulePolicy.REPLAY_FIRST, SchedulePolicy.NOTIFY_FIRST)) {
                val scheduler = ProductionSchedule(f.program, forward, sameUnit, policy, quantum = 1,
                    duplicateDeliveries = true, useStaticCatalog = true)
                val actual = scheduler.run()
                try { scalar.assertCatalogRefinement(actual, universe, p.roots) }
                catch (error: AssertionError) { throw AssertionError("NNF catalog F1 crossed=$crossed sameUnit=$sameUnit " +
                    "program=${f.program.describe()} trace=${scheduler.trace}", error) }
            }
            f.program.freshShared(sameUnit, freshVulnerabilities = true).use { owner ->
                val configs = ArrayList<RunConfig>()
                val driver = IterationDriver(FixedLimits(limits), owner, 20.seconds,
                    invokeRun = { config, shared, timeout -> configs += config; f.run(config, shared, timeout) },
                    guardBarrier = { _, body -> body() })
                val report = driver.analyze(p.roots)
                assertEquals(RunStatus.COMPLETE, report.end.status)
                assertEquals(listOf(1, 2, 3), f.results.map { it.runIndex })
                assertEquals(3, report.end.run)
                val states = report.entries.associate { HistoricalFinding(it.key.rule, it.key.statement) to
                    if (it.state == ReportState.CONFIRMED) HistoricalState.CONFIRMED else HistoricalState.DEMAND }
                assertEquals(reference.findings, states)
                assertEquals(setOf(HistoricalState.DEMAND), states.values.toSet())
                for ((expected, result) in reference.runs.zip(f.results)) {
                    assertEquals(RunStatus.COMPLETE, result.status)
                    val actual = retained(result, f.ops)
                    // Only F1 replaces static registration. Restricted B2/F3 transfer and every
                    // static summary retain the full scalar method/premise/mark/layer comparison.
                    val expectedView = if (result.runIndex == 1) catalogObservables(expected.observation, p.roots)
                        else expected.observation
                    val actualView = if (result.runIndex == 1) catalogObservables(actual, p.roots) else actual
                    RunObservation(rawSummaries = expectedView.rawSummaries, selectedDemand = expectedView.selectedDemand,
                        sourceHits = expectedView.sourceHits, witnesses = expectedView.witnesses).assertEquivalent(
                        RunObservation(rawSummaries = actualView.rawSummaries, selectedDemand = actualView.selectedDemand,
                            sourceHits = actualView.sourceHits, witnesses = actualView.witnesses), universe)
                    if (result.direction == Direction.BACKWARD) assertEquals(p.sources, actual.sourceHits.map { it.forward }.toSet())
                }
                // Compare every full method/exit demand from the real handoff by its required-location union.
                fun demandCells(rows: List<Pair<MethodKey, DemandPattern>>) = rows.filter { it.second.entry.fact.base != AccessPathBase.Zero }
                    .groupBy { it.first to it.second.exit }.mapValues { (_, values) ->
                        values.flatMap { universe.marked(it.second.entry) }.toSet()
                    }
                for (index in 1 until configs.size) {
                    val expected = reference.runs[index - 1].handOff.demand
                    val actual = (p.roots + p.callee).flatMap { method -> universe.positions.flatMap { position ->
                        configs[index].demand!!.covering(method, position.base, f.manager.path(position.path)).map { method to it }
                    } }.distinct()
                    assertEquals(demandCells(expected), demandCells(actual), "Handoff into run ${index + 1}")
                }
                assertEquals(1, configs[1].seeds.size, "Keep one physical compressed condition seed")
                assertIs<Seed.PredicateSink>(configs[1].seeds.at(p.callee, f.program.inst(p.callee, 1)).single())
                val sourceSeeds = p.roots.flatMap { root -> configs[2].seeds.at(root, f.program.inst(root, 0)) }
                    .filterIsInstance<Seed.Source>()
                assertEquals(p.sources, sourceSeeds.map { it.edge }.toSet())
                assertEquals(reference.runs[1].observation.sourceHits.map {
                    Seed.Source(it.method, it.statement, it.forward)
                }.toSet(), sourceSeeds.toSet(), "Preserve full source method context, statement and original edge")
                val records = (p.roots + p.callee).flatMap { method -> universe.positions.flatMap { position ->
                    f.records.byEntry(method, f.program.pattern(position.base, position.path, Tail.STAR, MarkSlot.STAR)).toList()
                } }.distinct().flatMap { record -> f.ops.leaves(record.conclusion).map {
                    RawRecord(record.method, record.direction, record.premise.toPattern(), Conclusion(it.fact, it.exclusion, false))
                }.toList() }
                assertEquals(nativeRelations(reference.runs.last().handOff.records, universe), nativeRelations(records, universe))
                assertTrue(report.entries.flatMap { it.witnesses }.all { witness -> witness.edges.all { edge ->
                    edge.premise.members.all { member -> member.isZero || member.base == STATIC }
                } })
            }
        }
    }
}
