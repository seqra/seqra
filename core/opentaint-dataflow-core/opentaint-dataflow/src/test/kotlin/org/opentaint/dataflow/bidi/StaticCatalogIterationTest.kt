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

/** Direct Group F1 operations plus independent ordinary Full B2/F3 closure checks. */
class StaticCatalogIterationTest {
    private val returned = AccessPathBase.Return
    private val result = AccessPathBase.Argument(1)

    private data class Program(val roots: List<MethodKey>, val callee: MethodKey, val sink: SinkRule,
                               val fields: List<Int>, val marks: List<TaintMark>, val output: PathFact,
                               val sources: Set<PathEdge>)

    private fun program(f: DriverFixtures, crossed: Boolean): Program {
        val p = f.program
        p.enableGroupRuleViews()
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
            val summary = f.summary(*(listOf(ZERO_KEEP) + selected).toTypedArray())
            val writes = selected.mapIndexed { ordinal, edge -> StaticOperation.Write(
                OperationId(FormKey(FormOwner(), root.method, root, p.inst(root, 0)), ordinal),
                Pattern(edge.to, ExclusionSet.Empty), edge) }
            p.entry(root, RuleStatement(summary.withStaticOperations(writes), StatementSummary.EMPTY, emptyList()))
            p.call(root, 1, call)
        }
        val node = p.inst(callee, 1)
        val form = FormKey(FormOwner(), callee.method, callee, node)
        val sourceId = RuleOccurrenceId(form, RuleKind.EXIT_SOURCE, 0)
        val sourceBuilder = RulePredicate.Builder(p.cancellation, p.ap.marks)
        val one = sourceBuilder.leaf(first); val two = sourceBuilder.leaf(second)
        val sourceCondition = sourceBuilder.build(sourceBuilder.and(listOf(one, sourceBuilder.or(listOf(two, two)))))
        val output = p.pattern(returned, listOf(a, b), mark = v).fact
        val source = SourceRuleOccurrence(sourceId, sourceCondition,
            listOf(SourceAction(ActionId(sourceId, ActionRole.SOURCE, 0), output)))
        val sinkBuilder = RulePredicate.Builder(p.cancellation, p.ap.marks)
        val value = sinkBuilder.leaf(Pattern(output, ExclusionSet.Empty))
        val state = sinkBuilder.leaf(second)
        val condition = sinkBuilder.build(sinkBuilder.or(listOf(value, sinkBuilder.and(listOf(value, state)))))
        val endFact = p.pattern(returned, mark = end).fact
        val endEdge = MicroEdge.of(PathEdge(ZERO_FACT, endFact, ExclusionSet.Empty))
        val sink = SinkRule(f.rule("catalog NNF sink"), 0, condition.leaves, listOf(endEdge),
            RuleOccurrenceId(form, RuleKind.EXIT_SINK, 0), condition)
        val body = StatementSummary(setOf(returned), listOf(MicroEdge.of(ZERO_KEEP)), emptyList(), emptyMap(),
            sources = listOf(source), staticOperations = listOf(
                StaticOperation.MarkInput(OperationId(form, 0), first),
                StaticOperation.MarkInput(OperationId(form, 1), second)))
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

    /** Plain copies of phase inputs, without a production driver, demand index or record selector in the oracle. */
    private fun phaseInputs(f: DriverFixtures, p: Program, config: RunConfig,
                            universe: BoundedDenotation): ProgramRunInputs {
        val owners = p.roots + p.callee
        val demands = owners.flatMap { owner -> universe.positions.flatMap { position ->
            // Query-specific static guidance is observed through the known FULL test universe.
            val marks: List<MarkSlot> = if (position.base == STATIC) listOf(MarkSlot.STAR) +
                universe.marks.filter { f.manager.marks.fullId(it) != null }.map { MarkSlot.Concrete(it) }
                else listOf(MarkSlot.STAR)
            marks.flatMap { mark ->
                val initial = if (position.base == AccessPathBase.Zero) f.manager.zero
                            else f.manager.initial(demandProbe(position.base, position.path, mark))
                config.demand?.covering(owner, initial).orEmpty().map { owner to it }
            }
        } }.distinct()
        val records = owners.flatMap { owner -> universe.positions.flatMap { position ->
            config.records.byEntry(owner, f.program.pattern(position.base, position.path, Tail.STAR, MarkSlot.STAR)).toList()
        } }.distinct().flatMap { record -> f.ops.leaves(record.conclusion).map {
            RawRecord(record.method, record.direction, record.premise.toPattern(), Conclusion(it.fact, it.exclusion, false))
        }.toList() }
        val seeds = owners.flatMap { owner -> f.program.programMethod(owner).successors.indices.flatMap { index ->
            config.seeds.at(owner, f.program.inst(owner, index))
        } }
        return ProgramRunInputs(p.roots, demands, records, seeds)
    }

    @Test
    fun `direct static NNF candidates retain full refinement source seeds records and complete guard bindings`(): Unit {
        for (crossed in listOf(false, true)) for (sameUnit in listOf(false, true)) DriverFixtures().use { f ->
            val p = program(f, crossed)
            val limits = listOf(1, 1, 2)
            val forward = f.program.config(roots = p.roots)
            var first: RunObservation? = null
            for (policy in listOf(SchedulePolicy.REPLAY_FIRST, SchedulePolicy.NOTIFY_FIRST)) {
                val actual = ProductionSchedule(f.program, forward, sameUnit, policy, quantum = 1,
                    duplicateDeliveries = true).run()
                assertDirectStaticProtocol(actual, f.manager.marks)
                assertEquals(setOf(p.sink.rule), actual.witnesses.map { it.rule }.toSet())
                assertEquals(setOf(1, 2), actual.witnesses.map { it.edges.size }.toSet())
                assertEquals(2, actual.witnesses.map { it.proof }.toSet().size)
                assertTrue(actual.witnesses.all { it.methodKey == p.callee && it.statement == f.program.inst(p.callee, 1) &&
                    it.edges.all { edge -> edge.layer == Layer.DEMAND } })
                assertTrue(actual.rawSummaries.none { it.conclusion.fact.mark == MarkSlot.Concrete(p.marks.last()) ||
                    it.conclusion.fact.mark == GroupedScalarMarks(f.manager.marks).slot(MarkSlot.Concrete(p.marks.last())) },
                    "Ignored END actions never create either Group or Full data")
                val out = GroupedScalarMarks(f.manager.marks).conclusion(Conclusion(p.output, ExclusionSet.Empty, true))
                assertTrue(actual.rawSummaries.any { it.method == p.callee && it.premise.size == 2 &&
                    it.premise.all { member -> member.fact.base == STATIC } &&
                    it.conclusion == limit(out, 1) }, "The complete static guard remains an ND summary")
                first = first ?: actual
            }
            val fresh = f.program.mark("fresh")
            val universe = BoundedDenotation(listOf(AccessPathBase.Zero, STATIC, returned, result, AccessPathBase.Argument(17)),
                p.fields + f.program.field("fresh"), (p.marks + fresh + (p.marks + fresh).map { f.manager.marks.group(f.manager.marks.groupOf(it)) } + TaintMark.ZERO).distinct(), 3)
            f.program.freshShared(sameUnit, freshVulnerabilities = true).use { owner ->
                val configs = ArrayList<RunConfig>()
                val report = IterationDriver(FixedLimits(limits), owner, 20.seconds,
                    invokeRun = { config, shared, timeout -> configs += config; f.run(config, shared, timeout) },
                    guardBarrier = { _, body -> body() }).analyze(p.roots)
                assertEquals(RunStatus.COMPLETE, report.end.status)
                assertEquals(listOf(1, 2, 3), f.results.map { it.runIndex })
                assertEquals(setOf(p.sink.rule), report.entries.map { it.key.rule }.toSet())
                assertEquals(setOf(ReportState.DEMAND), report.entries.map { it.state }.toSet(),
                    "A complete Full binding is checked below; the minimal driver supplies no confirmation certificate")
                assertTrue(f.results.last().witnessesByKey().values.flatten().none { it.confirmed })
                assertEquals(1, configs[1].seeds.size)
                val physical = assertIs<Seed.PredicateSink>(configs[1].seeds.at(p.callee, f.program.inst(p.callee, 1)).single())
                assertEquals(p.sink.occurrenceId, physical.occurrence)
                val expectedRecords = checkNotNull(first).rawSummaries.filter {
                    !it.conclusion.demand && it.premise.size == 1
                }.mapTo(ArrayList()) { RawRecord(it.method, Direction.FORWARD, it.premise.single(), it.conclusion) }
                for (index in 1 until configs.size) {
                    val config = configs[index]
                    val input = phaseInputs(f, p, config, universe)
                    val scalar = RawCurrentClosure(f.program, config, input).run()
                    val actual = retained(f.results[index], f.ops)
                    RunObservation(rawSummaries = scalar.rawSummaries, selectedDemand = scalar.selectedDemand,
                        sourceHits = scalar.sourceHits, witnesses = scalar.witnesses).assertEquivalent(actual, universe)
                    for (row in scalar.rawSummaries) if (!row.conclusion.demand && row.premise.size == 1 &&
                        !(config.direction == Direction.BACKWARD && row.premise.single().fact.base == AccessPathBase.Zero))
                        expectedRecords += RawRecord(row.method, config.direction, row.premise.single(), row.conclusion)
                }
                val backward = f.results[1].analyzers.flatMap { it.sourceHits!!.occurrences().toList() }
                for (source in p.sources) assertTrue(backward.any { it.forward == source && it.method in p.roots })
                val occurrence = f.program.forms(p.callee).exitRules(Direction.FORWARD, f.program.inst(p.callee, 1))
                    .rules.summary.sources.single()
                assertTrue(backward.filter { it.method == p.callee }.all { hit ->
                    hit.action == occurrence.actions.single().id && hit.forward.to == p.output
                }, "The later source seeds retain the full action and occurrence identity")
                val forwarded = (p.roots + p.callee).flatMap { key -> f.program.programMethod(key).successors.indices.flatMap { node ->
                    configs[2].seeds.at(key, f.program.inst(key, node)).filterIsInstance<Seed.Source>()
                } }
                assertEquals(backward.map { Seed.Source(it.method, it.statement, it.forward, it.action) }.toSet(), forwarded.toSet())
                val records = phaseInputs(f, p, RunConfig(3, 2, configs[2].demand, f.records, configs[2].seeds, p.roots), universe).records
                assertEquals(nativeRelations(expectedRecords, universe), nativeRelations(records, universe))
            }
        }
    }
}
