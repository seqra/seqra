package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.*
import kotlin.time.Duration

/** Current F72/F75/F76/F77 sequences; labelled earlier reports are a separate inclusion check. */
class AnyTaintIterationTest {
    private val argument = AccessPathBase.Argument(0)
    private val result = AccessPathBase.Return
    private val value = AccessPathBase.LocalVar(0)

    private data class Native(val method: MethodKey, val direction: Direction, val premise: Pattern,
                              val conclusion: Conclusion)
    private data class Barrier(val config: RunConfig, val observation: RunObservation,
                               val demand: Map<MethodKey, Set<DemandPattern>>, val seeds: Set<Seed>,
                               val records: Set<Native>, val counts: Map<MethodKey, Int>)

    /** Required services remain real; these wrappers only snapshot calls and complete joined stores. */
    private class SequenceRun(val f: DriverFixtures, val keys: List<MethodKey>, val bases: List<AccessPathBase>) {
        private val processed = ConcurrentLinkedQueue<Pair<Int, ObservedEdge>>()
        private val requests = ConcurrentLinkedQueue<Pair<Int, ObservedRequest>>()
        private val publications = ConcurrentLinkedQueue<Pair<Int, ObservedEdge>>()
        private val inputs = ConcurrentLinkedQueue<Pair<Int, ObservedLink>>()
        val barriers = ArrayList<Barrier>()
        private val report = ReportBuilder()

        private fun run(config: RunConfig, shared: SharedObjects, timeout: Duration): RunResult<RunMethodAnalyzer> {
            f.configs += config
            f.timeouts += timeout
            val local = MethodLocalServicesFactory { core ->
                val actual = RunMethodLocalServices(core)
                object : MethodLocalServices by actual {
                    override fun addMarkRequest(premise: InitialAp, mark: TaintMark) {
                    val kind = RequestKind.Mark(mark)
                        requests += config.index to ObservedRequest(core.key, premise.toPattern(), kind)
                        actual.addMarkRequest(premise, mark)
                    }
                    override fun addMarkRequests(premise: InitialAp, marks: RequestMarkSet) {
                        marks.forEachMark { requests += config.index to ObservedRequest(core.key, premise.toPattern(), RequestKind.Mark(it)) }
                        actual.addMarkRequests(premise, marks)
                    }
                }
            }
            val calls = MethodCallServicesFactory(::RunMethodCallServices)
            val actual = RunMethodHandlers(local, calls)
            val observed = RunnerHandlerFactory<RunMethodAnalyzer> { port ->
                val observedPort = object : RunnerPort<RunMethodAnalyzer> by port {
                    override fun link(callee: MethodKey, ref: CallerRef, linkLayer: Layer, added: Facts) {
                        f.ops.leaves(added).forEach { inputs += config.index to observedLink(callee, Link(it, linkLayer, ref)) }
                        port.link(callee, ref, linkLayer, added)
                    }
                    override val onProcess: (MethodKey, EdgeDelta) -> Unit = { key, delta ->
                        for (edge in observedEdges(f.ops, key, delta.premise, delta.facts, delta.node))
                            processed += config.index to edge
                        port.onProcess?.invoke(key, delta)
                    }
                }
                val handler = actual.create(observedPort)
                object : RunnerHandler<RunMethodAnalyzer>, SubscriptionHandler {
                    override fun createActor(key: MethodKey): RunMethodAnalyzer = RunMethodAnalyzer(key, observedPort,
                        PublicationSink { method, batch ->
                            for (pub in batch) for (edge in observedEdges(f.ops, method, pub.premise, pub.conclusion))
                                publications += config.index to edge
                            port.summaryStorage(method).publish(batch)
                        }, local, calls)
                    override fun handle(event: RunEvent): Unit = handler.handle(event)
                    override fun subscribe(sub: Subscription): Unit = (handler as SubscriptionHandler).subscribe(sub)
                    override fun freeze(): Unit = (handler as SubscriptionHandler).freeze()
                }
            }
            return RunManager(config, shared, observed, quantum = 1).run(timeout).also(f.results::add)
        }

        fun analyze(root: MethodKey, limits: List<Int>, historicalGolden: Map<String, HistoricalState>? = null,
                    historicalLabel: String = ""): Pair<Report, List<Frontier>> {
            val earlier = historicalGolden?.let {
                HistoricalConcreteIteration(f.program, listOf(root), limits, allowMustSources = true).run()
            }
            val driver = f.driver(FixedLimits(limits), invokeRun = ::run, run1MarkMode = MarkMode.GROUP, guardBarrier = { shared, body ->
                val next = body()
                val config = f.configs.last()
                val joined = f.results.last()
                assertEquals(RunStatus.COMPLETE, joined.status)
                if (config.direction == Direction.FORWARD) report.add(config, joined)
                val handOff = HandOff.of(config, joined, report, shared)
                val storedDemand = keys.associateWith { key ->
                    bases.flatMap { handOff.demand.near(key, demandProbe(it)) }.toSet()
                }
                val seedSnapshot = keys.flatMap { key ->
                    f.program.programMethod(key).successors.indices.flatMap { handOff.seeds.at(key, f.program.inst(key, it)) }
                }.toSet()
                val native = keys.flatMap { key -> bases.flatMap { base ->
                    shared.records.byEntry(key, Pattern(PathFact(base, emptyList(), Tail.ANY, MarkSlot.STAR), ExclusionSet.Empty))
                        .flatMap { record -> f.ops.leaves(record.conclusion).map { leaf ->
                            Native(key, record.direction, record.premise.toPattern(), Conclusion(leaf.fact, leaf.exclusion, false))
                        } }.toList()
                } }.toSet()
                val rows = processed.filter { it.first == config.index }.map { it.second }.toSet()
                val observation = RunObservation(processed = rows,
                    initials = rows.flatMap { edge -> edge.premise.map { ObservedInitial(edge.method, it) } }.toSet(),
                    rawSummaries = joined.analyzers.flatMap { actor -> actor.summaries.all().flatMap { (pr, facts) ->
                        observedEdges(f.ops, actor.key, pr, facts)
                    }.toList() }.toSet(),
                    publications = publications.filter { it.first == config.index }.map { it.second }.toSet(),
                    selectedDemand = joined.analyzers.flatMap { actor -> actor.summaries.demandEdges().flatMap { (pr, facts) ->
                        observedEdges(f.ops, actor.key, pr, facts)
                    }.toList() }.toSet(),
                    links = inputs.filter { it.first == config.index }.map { it.second }.toSet(),
                    requests = requests.filter { it.first == config.index }.map { it.second }.toSet(),
                    sourceHits = joined.analyzers.flatMap { actor -> actor.sourceHits?.entries()?.map {
                        ObservedSourceHit(it.first, it.second, it.third)
                    }?.toList().orEmpty() }.toSet(),
                    witnesses = joined.witnessesByKey().flatMap { (key, witnesses) ->
                        witnesses.map { observedWitness(f.ops, key, it) }
                    }.toSet())
                barriers += Barrier(config, observation, storedDemand, seedSnapshot, native, handOff.demandEdges.toMap())
                if (next is Next.Run) {
                    assertEquals(storedDemand, keys.associateWith { key -> bases.flatMap {
                        next.config.demand!!.near(key, demandProbe(it))
                    }.toSet() })
                    assertEquals(seedSnapshot, keys.flatMap { key -> f.program.programMethod(key).successors.indices.flatMap {
                        next.config.seeds.at(key, f.program.inst(key, it))
                    } }.toSet())
                }
                next
            })
            val final = driver.analyze(listOf(root))
            if (earlier != null) {
                assertEquals(historicalGolden, earlier.findings.entries.associate { it.key.rule.id to it.value },
                    "Earlier labelled report: $historicalLabel")
                val current = final.entries.associate { HistoricalFinding(it.key.rule, it.key.statement) to
                    if (it.state == ReportState.CONFIRMED) HistoricalState.CONFIRMED else HistoricalState.DEMAND }
                for ((key, state) in earlier.findings) {
                    assertNotNull(current[key], "Earlier finding lost: $historicalLabel key=$key")
                    assertEquals(HistoricalState.DEMAND, current[key],
                        "Current witnesses are candidates; the labelled historical support policy is separate")
                }
            }
            assertEquals(f.configs.size, barriers.size)
            val first = barriers.first()
            val eligible = first.observation.rawSummaries.filter {
                !it.conclusion.demand && it.premise.size == 1
            }.map { Native(it.method, Direction.FORWARD, it.premise.single(), it.conclusion) }.toSet()
            assertEquals(eligible, first.records, "Persist each eligible NORMAL singleton after run-1")
            for (barrier in barriers) {
                if (barrier.config.restricted) {
                    assertTrue(barrier.observation.requests.isEmpty())
                    assertTrue(barrier.observation.processed.none { it.conclusion.fact.mark is MarkSlot.Star },
                        "Concrete production queries never execute old abstract guidance as FLOW")
                } else assertTrue(barrier.records.all { record ->
                    record.conclusion.fact.mark is MarkSlot.Star || record.conclusion.fact.base == AccessPathBase.Zero
                }, "Run-1 can persist eligible NORMAL FLOW rows; all group-valued TAINT rows remain DEMAND")
                if (barrier.config.direction == Direction.BACKWARD) {
                    assertTrue(barrier.observation.witnesses.isEmpty())
                    assertTrue(barrier.observation.processed.none { it.conclusion.fact.tail == Tail.ANY_TAINT })
                }
                assertTrue(barrier.records.all { !it.conclusion.demand })
                assertTrue(barrier.observation.rawSummaries.none { it.conclusion.fact.base is AccessPathBase.CleanerTemporary })
                assertTrue(barrier.demand.values.flatten().all { d ->
                    d.entry.fact.tail != Tail.ANY_TAINT && d.exit?.fact?.tail != Tail.ANY_TAINT &&
                        (d.entry.fact.mark as? MarkSlot.Star)?.excluded?.isEmpty != false &&
                        (d.exit?.fact?.mark as? MarkSlot.Star)?.excluded?.isEmpty != false
                })
            }
            val frontiers = driver.frontiers
            for ((frontier, barrier) in frontiers.zip(barriers)) {
                assertEquals(barrier.config.index, frontier.run)
                assertEquals(barrier.config.direction, frontier.direction)
                assertEquals(barrier.counts, frontier.demandEdges)
                assertEquals(barrier.seeds.size, frontier.seeds)
                assertEquals(f.results[frontier.run - 1].recordCrossings, frontier.recordCrossings)
                assertEquals(barrier.observation.initials.filter { it.initial.fact.base != AccessPathBase.Zero }
                    .map { it.method }.toSet(), frontier.analysed)
                assertEquals(f.results[frontier.run - 1].analyzers.map { it.key }.toSet() - frontier.analysed,
                    frontier.zeroOnly)
                assertNull(frontier.demandByCause)
            }
            return final to frontiers
        }
    }

    private fun source(f: DriverFixtures, key: MethodKey, target: PathFact): PathEdge =
        PathEdge(ZERO_FACT, target, ExclusionSet.Empty).also { edge ->
            f.program.entry(key, RuleStatement(f.summary(ZERO_KEEP, edge), StatementSummary.EMPTY, emptyList()))
        }
    private fun sink(f: DriverFixtures, key: MethodKey, index: Int, vararg sinks: SinkRule) {
        f.program.exit(key, index, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, sinks.toList()),
            false, emptySet()))
    }
    private fun call(f: DriverFixtures, callee: MethodKey, from: AccessPathBase, to: AccessPathBase,
                     returnFrom: AccessPathBase = result, returnTo: AccessPathBase = result,
                     returnPath: List<Int> = emptyList()): CallPlan = CallPlan(setOf(from, returnTo), listOf(
        CallStage.Edges(BEFORE, BOUND, BIND_IN, f.summary(starEdge(from, emptyList(), to, emptyList()), ZERO_KEEP)),
        CallStage.Actions(BOUND, ADDED, emptyList()),
        CallStage.Callees(ADDED, RETURNED, listOf(callee)),
        CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
        CallStage.Edges(REWRITTEN, AFTER, BIND_BACK,
            f.summary(starEdge(returnFrom, emptyList(), returnTo, returnPath), ZERO_KEEP)),
    ), emptyList(), BEFORE, AFTER)
    private fun conclusions(rows: Set<ObservedEdge>, key: MethodKey, base: AccessPathBase): Set<Conclusion> =
        rows.filter { it.method == key && it.conclusion.fact.base == base }.map { it.conclusion }.toSet()
    private fun group(f: DriverFixtures, mark: MarkSlot.Concrete): MarkSlot.Concrete =
        MarkSlot.Concrete(f.manager.marks.group(f.manager.marks.groupOf(mark.mark)))
    private fun zero(f: DriverFixtures): Pattern = f.manager.zero.toPattern()
    private fun initialRows(barrier: Barrier, key: MethodKey): Set<Pattern> =
        barrier.observation.initials.filter { it.method == key }.map { it.initial }.toSet()
    private fun finding(f: DriverFixtures, rule: RuleId, key: MethodKey, node: Int): VulnerabilityKey =
        VulnerabilityKey(rule, key.method, f.program.inst(key, node))

    @Test
    fun `G getter refines provisional guidance through concrete backward and forward queries`(): Unit {
        DriverFixtures().use { f ->
            val root = f.program.method("G root", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val get = f.program.method("G get", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val name = f.program.field("name")
            val t = MarkSlot.Concrete(f.program.mark("G T"))
            val src = source(f, root, PathFact(value, emptyList(), Tail.ANY_TAINT, t))
            f.program.statement(get, 1, MicroEdgeBuilder().apply { read(result, argument, listOf(name)) }.build())
            f.program.call(root, 1, call(f, get, value, argument))
            val rule = f.rule("G sinkAny")
            val literal = f.program.pattern(result, tail = Tail.ANY, mark = t)
            sink(f, root, 2, SinkRule(rule, 0, listOf(literal), emptyList()))
            val runs = SequenceRun(f, listOf(root, get), listOf(AccessPathBase.Zero, value, argument, result))
            val (report, frontiers) = runs.analyze(root, listOf(1, 1, 2), mapOf("G sinkAny" to HistoricalState.CONFIRMED),
                "Earlier D6X/DRXs: AnyTaintExCases2.G.run3_confirmed_handoff")
            val j = f.program.pattern(argument, tail = Tail.STAR, mark = MarkSlot.STAR)
            val demandResult = f.program.pattern(result, tail = Tail.ANY, mark = MarkSlot.STAR)
            assertEquals(listOf(1, 1, 2), f.configs.map { it.fieldLimit })
            assertEquals(setOf(Conclusion(demandResult.fact, ExclusionSet.Empty, true)),
                conclusions(runs.barriers[0].observation.rawSummaries, get, result))
            assertEquals(setOf(DemandPattern(demandResult, j),
                DemandPattern(f.program.pattern(argument, tail = Tail.ANY, mark = MarkSlot.STAR), j)), runs.barriers[0].demand.getValue(get)
                .filterNot { it.entry.fact.base == AccessPathBase.Zero }.toSet())
            val backward = f.program.pattern(result, tail = Tail.ANY, mark = t)
            assertEquals(setOf(zero(f), backward), initialRows(runs.barriers[1], get))
            val readBack = f.program.pattern(argument, listOf(name), Tail.ANY, t)
            assertEquals(setOf(Conclusion(readBack.fact, ExclusionSet.Empty, true)),
                conclusions(runs.barriers[1].observation.rawSummaries, get, argument))
            val selectedGetter = runs.barriers[1].observation.selectedDemand.filter { it.method == get }.toSet()
            assertEquals(setOf(ObservedEdge(get, setOf(ZERO_PATTERN),
                Conclusion(ZERO_FACT, ExclusionSet.Empty, false)),
                ObservedEdge(get, setOf(backward), Conclusion(readBack.fact, ExclusionSet.Empty, true))), selectedGetter)
            assertTrue(runs.barriers[1].records.none { it.method == get && it.direction == Direction.BACKWARD },
                "The concrete backward may answer is DEMAND and cannot become a native record")
            assertEquals(setOf(DemandPattern(zero(f), null), DemandPattern(readBack, backward)),
                runs.barriers[1].demand.getValue(get))
            val actualMust = f.program.pattern(argument, tail = Tail.ANY_TAINT, mark = t)
            val narrowMust = f.program.pattern(argument, listOf(name), Tail.ANY_TAINT, t)
            assertEquals(setOf(zero(f), actualMust, narrowMust), initialRows(runs.barriers[2], get))
            assertEquals(setOf(Conclusion(PathFact(result, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.Empty, false)),
                conclusions(runs.barriers[2].observation.rawSummaries, root, result))
            assertEquals(setOf(Seed.Source(root, root.statement, src)), runs.barriers[1].seeds)
            assertTrue(runs.barriers.all { it.observation.requests.isEmpty() })
            assertEquals(listOf(1, 1, 1), frontiers.map { it.seeds })
            assertContains(frontiers[0].analysed, get)
            assertContains(frontiers[1].analysed, get)
            assertContains(frontiers[2].analysed, get)
            assertContains(runs.barriers[0].records, Native(get, Direction.FORWARD, j,
                Conclusion(j.fact, ExclusionSet.Empty, false)))
            assertTrue(frontiers[2].recordCrossings > 0L, "Eligible NORMAL FLOW records remain reusable")
            assertContains(runs.barriers[2].records, Native(get, Direction.FORWARD, narrowMust,
                Conclusion(PathFact(result, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.Empty, false)))
            assertEquals(ReportState.DEMAND, report.entries.single { it.key == finding(f, rule, root, 2) }.state)
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 3, Direction.FORWARD, EndReason.NO_DEMAND_EDGE,
                run1MarkMode = MarkMode.GROUP), report.end)
        }
    }

    @Test
    fun `C sink in callee recovers an admitted must premise through the same caller link`(): Unit {
        DriverFixtures().use { f ->
            val root = f.program.method("C root", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val use = f.program.method("C use", listOf(listOf(1), emptyList()), 1).key()
            val name = f.program.field("name")
            val t = MarkSlot.Concrete(f.program.mark("C T"))
            val src = source(f, root, PathFact(value, emptyList(), Tail.ANY_TAINT, t))
            f.program.call(root, 1, call(f, use, value, argument))
            val rule = f.rule("C sinkAny name")
            sink(f, use, 1, SinkRule(rule, 0, listOf(f.program.pattern(argument, listOf(name), Tail.ANY, t)), emptyList()))
            val runs = SequenceRun(f, listOf(root, use), listOf(AccessPathBase.Zero, value, argument, result))
            val (report, frontiers) = runs.analyze(root, listOf(1, 1, 2), mapOf("C sinkAny name" to HistoricalState.CONFIRMED),
                "Earlier D6X/DRXs: AnyTaintExCases2.C.run3_confirmed_handoff")
            val flow = f.program.pattern(argument, tail = Tail.STAR, mark = MarkSlot.STAR)
            assertEquals(setOf(ObservedRequest(use, flow, RequestKind.Mark(group(f, t).mark))),
                runs.barriers[0].observation.requests)
            assertTrue(runs.barriers[0].observation.witnesses.any { it.methodKey == use && it.edges.single().layer == Layer.DEMAND })
            assertEquals(setOf(Seed.Source(root, root.statement, src)), runs.barriers[1].seeds)
            val admitted = f.program.pattern(argument, listOf(name), Tail.ANY_TAINT, t)
            assertContains(initialRows(runs.barriers[2], use), admitted)
            val witnesses = runs.barriers[2].observation.witnesses.filter { it.methodKey == use }
            val actualMust = f.program.pattern(argument, tail = Tail.ANY_TAINT, mark = t)
            assertEquals(setOf(setOf(admitted), setOf(actualMust)), witnesses.map { it.edges.single().premise }.toSet())
            assertTrue(witnesses.all { !it.confirmed && it.edges.single().layer == Layer.NORMAL })
            assertTrue(runs.barriers[2].observation.links.any { link ->
                link.callee == use && link.caller == root && link.call == f.program.inst(root, 1) &&
                    link.premise == setOf(zero(f)) && link.callerLayer == Layer.NORMAL && link.linkLayer == Layer.NORMAL &&
                    link.added == f.program.pattern(argument, tail = Tail.ANY_TAINT, mark = t)
            })
            assertEquals(ReportState.DEMAND, report.entries.single { it.key == finding(f, rule, use, 1) }.state)
            assertEquals(listOf(1, 2, 3), frontiers.map { it.run })
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 3, Direction.FORWARD, EndReason.NO_DEMAND_EDGE,
                run1MarkMode = MarkMode.GROUP), report.end)
        }
    }

    @Test
    fun `I normal must source still undergoes strong refinement with each later limit policy`(): Unit {
        DriverFixtures().use { f ->
            val limit = 2
            val root = f.program.method("I root L$limit", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val id = f.program.method("I id L$limit", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val t = MarkSlot.Concrete(f.program.mark("I T"))
            val src = source(f, root, PathFact(value, emptyList(), Tail.ANY_TAINT, t))
            f.program.statement(id, 1, MicroEdgeBuilder().apply { move(result, argument) }.build())
            f.program.call(root, 1, call(f, id, value, argument))
            val rule = f.rule("I sinkAny")
            sink(f, root, 2, SinkRule(rule, 0, listOf(f.program.pattern(result, tail = Tail.ANY, mark = t)), emptyList()))
            val runs = SequenceRun(f, listOf(root, id), listOf(AccessPathBase.Zero, value, argument, result))
            val (report, frontiers) = runs.analyze(root, listOf(1, 1, 2), mapOf("I sinkAny" to HistoricalState.CONFIRMED),
                "Earlier D6X/DRXs: AnyTaintExCases2.I.run1_confirmed")
            assertEquals(listOf(1, 2, 3), f.configs.map { it.index })
            val normal = Conclusion(PathFact(result, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.Empty, false)
            assertEquals(setOf(normal.copy(fact = normal.fact.copy(mark = group(f, t)), demand = true)),
                conclusions(runs.barriers[0].observation.rawSummaries, root, result))
            assertEquals(setOf(normal), conclusions(runs.barriers[2].observation.rawSummaries, root, result))
            assertContains(runs.barriers[2].records, Native(id, Direction.FORWARD,
                f.program.pattern(argument, tail = Tail.ANY_TAINT, mark = t), normal))
            assertTrue(runs.barriers[0].counts.getValue(id) > 0, "Group-valued F1 summaries are DEMAND")
            assertTrue(runs.barriers[2].counts.isEmpty())
            assertEquals(setOf(Seed.Sink(rule, root, f.program.inst(root, 2),
                f.program.pattern(result, tail = Tail.ANY, mark = t))), runs.barriers[0].seeds)
            assertEquals(setOf(Seed.Source(root, root.statement, src)), runs.barriers[1].seeds)
            assertTrue(runs.barriers.all { it.observation.requests.isEmpty() })
            assertEquals(1, frontiers.last().demandVulnerabilities)
            assertEquals(0, frontiers.last().confirmedVulnerabilities)
            assertEquals(ReportState.DEMAND, report.entries.single().state)
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 3, Direction.FORWARD, EndReason.NO_DEMAND_EDGE,
                run1MarkMode = MarkMode.GROUP), report.end)
        }
    }

    @Test
    fun `S and SD setters refine an E-free provisional cover to strong sibling preservation`(): Unit {
        for (deeper in listOf(false, true)) DriverFixtures().use { f ->
            val limit = 2
            val root = f.program.method("S root L$limit", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val set = f.program.method("S set L$limit", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val name = f.program.field("name")
            val email = f.program.field("email")
            val t = MarkSlot.Concrete(f.program.mark("S T"))
            source(f, root, PathFact(value, emptyList(), Tail.ANY_TAINT, t))
            f.program.statement(set, 1, MicroEdgeBuilder().apply {
                write(argument, listOf(name), weak = false, values = emptyList(), aliasPaths = emptyList())
            }.build())
            val wrapper = if (deeper) f.program.method("SD setter wrapper L$limit",
                listOf(listOf(1), listOf(2), emptyList()), 2).key().also { key ->
                f.program.call(key, 1, call(f, set, argument, argument, returnFrom = argument, returnTo = argument))
            } else null
            f.program.call(root, 1, call(f, wrapper ?: set, value, argument, returnFrom = argument, returnTo = result))
            val overwritten = f.rule("S name")
            val admitted = f.rule("S email")
            sink(f, root, 2, SinkRule(overwritten, 0, listOf(f.program.pattern(result, listOf(name), Tail.ANY, t)), emptyList()),
                SinkRule(admitted, 0, listOf(f.program.pattern(result, listOf(email), Tail.ANY, t)), emptyList()))
            val runs = SequenceRun(f, listOfNotNull(root, set, wrapper), listOf(AccessPathBase.Zero, value, argument, result))
            val (report, frontiers) = runs.analyze(root, listOf(1, 1, 2), mapOf("S email" to HistoricalState.CONFIRMED),
                "Earlier D6X/DRXs: AnyTaintExCases.S.run1_email_confirmed; SD.run1_email_confirmed")
            val exclusion = ExclusionSet.of(name)
            assertEquals(setOf(Conclusion(PathFact(result, emptyList(), Tail.ANY_TAINT, group(f, t)), ExclusionSet.Empty, true)),
                conclusions(runs.barriers[0].observation.rawSummaries, root, result))
            assertEquals(setOf(Conclusion(PathFact(result, emptyList(), Tail.ANY_TAINT, t), exclusion, false)),
                conclusions(runs.barriers[2].observation.rawSummaries, root, result))
            val must = f.program.pattern(argument, tail = Tail.ANY_TAINT, mark = t)
            assertContains(runs.barriers[2].records, Native(set, Direction.FORWARD, must,
                Conclusion(must.fact, exclusion, false)))
            if (wrapper != null) assertContains(runs.barriers[2].records, Native(wrapper, Direction.FORWARD, must,
                Conclusion(must.fact, exclusion, false)))
            assertTrue(runs.barriers[0].observation.witnesses.any { it.rule === overwritten })
            assertTrue(runs.barriers[2].observation.witnesses.none { it.rule === overwritten })
            assertTrue(runs.barriers[2].counts.isEmpty())
            assertTrue(runs.barriers.all { it.observation.requests.isEmpty() })
            assertEquals(listOf(finding(f, admitted, root, 2)), report.entries.map { it.key })
            assertEquals(ReportState.DEMAND, report.entries.single().state)
            assertEquals(1, frontiers.last().seeds)
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 3, Direction.FORWARD, EndReason.NO_DEMAND_EDGE,
                run1MarkMode = MarkMode.GROUP), report.end)
        }
    }
    @Test
    fun `F76 factory keeps grouped TAINT as demand then recomputes native E and full marks from source seeds`(): Unit {
        DriverFixtures().use { f ->
            val root = f.program.method("F76 factory root",
                listOf(listOf(1), listOf(2), listOf(3), listOf(4), listOf(5), listOf(6), listOf(7), listOf(8), emptyList()), 8).key()
            val factory = f.program.method("F76 factory", listOf(listOf(1), listOf(2), listOf(3), emptyList()), 3).key()
            val name = f.program.field("name")
            val email = f.program.field("email")
            val k = f.program.field("k")
            val path = listOf(f.program.field("a"), f.program.field("b"))
            val t = MarkSlot.Concrete(f.program.mark("F76 T"))
            val u = MarkSlot.Concrete(f.program.mark("F76 U"))
            val sourceT = PathEdge(ZERO_FACT, PathFact(result, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.Empty)
            val sourceU = PathEdge(ZERO_FACT, PathFact(value, emptyList(), Tail.EXACT, u), ExclusionSet.Empty)
            f.program.entry(factory, RuleStatement(f.summary(ZERO_KEEP, sourceT, sourceU), StatementSummary.EMPTY, emptyList()))
            f.program.statement(factory, 1, MicroEdgeBuilder().apply {
                write(result, listOf(name), weak = false, values = emptyList(), aliasPaths = emptyList())
            }.build())
            f.program.statement(factory, 2, MicroEdgeBuilder().apply {
                write(result, listOf(k), weak = false, values = listOf(value), aliasPaths = emptyList())
            }.build())
            val returned = AccessPathBase.LocalVar(10)
            val readT = AccessPathBase.LocalVar(11)
            val readU = AccessPathBase.LocalVar(12)
            val blocked = AccessPathBase.LocalVar(13)
            val outU = AccessPathBase.Argument(1)
            val outBlocked = AccessPathBase.Argument(2)
            f.program.call(root, 1, CallPlan(setOf(returned), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, f.summary(ZERO_KEEP)),
                CallStage.Actions(BOUND, ADDED, emptyList()),
                CallStage.Callees(ADDED, RETURNED, listOf(factory)),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK,
                    f.summary(starEdge(result, emptyList(), returned, emptyList()), ZERO_KEEP)),
            ), emptyList(), BEFORE, AFTER))
            f.program.statement(root, 2, MicroEdgeBuilder().apply { read(readT, returned, listOf(email)) }.build())
            f.program.statement(root, 3, MicroEdgeBuilder().apply { move(result, readT, path) }.build())
            f.program.statement(root, 4, MicroEdgeBuilder().apply { read(readU, returned, listOf(k)) }.build())
            f.program.statement(root, 5, MicroEdgeBuilder().apply { move(outU, readU, path) }.build())
            f.program.statement(root, 6, MicroEdgeBuilder().apply { read(blocked, returned, listOf(name)) }.build())
            f.program.statement(root, 7, MicroEdgeBuilder().apply { move(outBlocked, blocked, path) }.build())
            val admittedT = f.rule("F76 admitted email T")
            val admittedU = f.rule("F76 exact k U")
            val excluded = f.rule("F76 excluded name T")
            sink(f, root, 8,
                SinkRule(admittedT, 0, listOf(f.program.pattern(result, path, mark = t)), emptyList()),
                SinkRule(admittedU, 0, listOf(f.program.pattern(outU, path, mark = u)), emptyList()),
                SinkRule(excluded, 0, listOf(f.program.pattern(outBlocked, path, mark = t)), emptyList()))
            val runs = SequenceRun(f, listOf(root, factory), listOf(AccessPathBase.Zero, value, argument, result,
                returned, readT, readU, blocked, outU, outBlocked))
            val (report, frontiers) = runs.analyze(root, listOf(1, 1, 2), mapOf(
                "F76 admitted email T" to HistoricalState.CONFIRMED,
                "F76 exact k U" to HistoricalState.CONFIRMED),
                "F69/F71 native factory prior to F76 views; argued, no Lean program theorem")
            val exclusion = ExclusionSet.of(intArrayOf(name, k))
            val nativeT = Native(factory, Direction.FORWARD, zero(f),
                Conclusion(PathFact(result, emptyList(), Tail.ANY_TAINT, t), exclusion, false))
            val nativeU = Native(factory, Direction.FORWARD, zero(f),
                Conclusion(PathFact(result, listOf(k), Tail.EXACT, u), ExclusionSet.Empty, false))
            assertEquals(setOf(
                nativeT.conclusion.copy(fact = nativeT.conclusion.fact.copy(mark = group(f, t)),
                    exclusion = ExclusionSet.Empty, demand = true),
                nativeU.conclusion.copy(fact = nativeU.conclusion.fact.copy(mark = group(f, u)), demand = true)),
                conclusions(runs.barriers[0].observation.rawSummaries, factory, result))
            assertTrue(runs.barriers[0].records.none { it.conclusion.fact.mark == group(f, t) ||
                it.conclusion.fact.mark == group(f, u) }, "Group TAINT stays out of native records")
            assertTrue(nativeT !in runs.barriers[1].records && nativeU !in runs.barriers[1].records)
            assertContains(runs.barriers[2].records, nativeT)
            assertContains(runs.barriers[2].records, nativeU)
            assertTrue(runs.barriers[0].counts.getValue(factory) > 0)
            assertNull(runs.barriers[2].counts[factory])
            assertContains(runs.barriers[0].demand.getValue(factory), DemandPattern(
                f.program.pattern(result, tail = Tail.ANY, mark = group(f, t)), zero(f)))
            assertContains(initialRows(runs.barriers[1], factory), f.program.pattern(result, listOf(k), Tail.EXACT, u))
            assertEquals(setOf(zero(f)), initialRows(runs.barriers[2], factory))
            assertEquals(setOf(ObservedSourceHit(factory, factory.statement, sourceT),
                ObservedSourceHit(factory, factory.statement, sourceU)), runs.barriers[1].observation.sourceHits)
            assertEquals(setOf(Seed.Source(factory, factory.statement, sourceT),
                Seed.Source(factory, factory.statement, sourceU)), runs.barriers[1].seeds)
            assertEquals(setOf(nativeT.conclusion, nativeU.conclusion),
                conclusions(runs.barriers[2].observation.rawSummaries, factory, result))
            assertEquals(setOf(Conclusion(PathFact(result, path.take(1), Tail.ANY, group(f, t)), ExclusionSet.Empty, true)),
                conclusions(runs.barriers[0].observation.rawSummaries, root, result))
            assertEquals(setOf(Conclusion(PathFact(outU, path.take(1), Tail.ANY, group(f, u)), ExclusionSet.Empty, true),
                Conclusion(PathFact(outU, path.take(1), Tail.ANY, group(f, t)), ExclusionSet.Empty, true)),
                conclusions(runs.barriers[0].observation.rawSummaries, root, outU))
            assertEquals(setOf(Conclusion(PathFact(result, path, Tail.ANY_TAINT, t), ExclusionSet.Empty, false)),
                conclusions(runs.barriers[2].observation.rawSummaries, root, result))
            assertEquals(setOf(Conclusion(PathFact(outU, path, Tail.EXACT, u), ExclusionSet.Empty, false)),
                conclusions(runs.barriers[2].observation.rawSummaries, root, outU))
            assertEquals(setOf(Conclusion(PathFact(outBlocked, path.take(1), Tail.ANY, group(f, t)), ExclusionSet.Empty, true)),
                conclusions(runs.barriers[0].observation.rawSummaries, root, outBlocked))
            assertTrue(runs.barriers.drop(1).all { conclusions(it.observation.rawSummaries, root, outBlocked).isEmpty() })
            assertTrue(runs.barriers.all { it.observation.requests.isEmpty() })
            assertEquals(0L, frontiers[1].recordCrossings, "B2 cannot execute an F1 source record")
            assertTrue(frontiers[2].recordCrossings > 0L)
            assertEquals(setOf(finding(f, admittedT, root, 8), finding(f, admittedU, root, 8)), report.entries.map { it.key }.toSet())
            assertTrue(report.entries.all { it.state == ReportState.DEMAND && it.run == 3 })
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 3, Direction.FORWARD, EndReason.NO_DEMAND_EDGE,
                run1MarkMode = MarkMode.GROUP), report.end)
        }
    }

    @Test
    fun `source target must refines to NORMAL while the conditional may pass stays DEMAND`(): Unit {
        for (isSource in listOf(true, false)) DriverFixtures().use { f ->
            val root = f.program.method("source versus pass $isSource", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val t = MarkSlot.Concrete(f.program.mark("source versus pass T"))
            val src = source(f, root, PathFact(value, emptyList(), Tail.EXACT, t))
            // A conditional single-literal source has the must target. The pass rule has an ANY target.
            val ruleEdge = PathEdge(src.to, PathFact(result, emptyList(), if (isSource) Tail.ANY_TAINT else Tail.ANY, t),
                ExclusionSet.Empty)
            f.program.statement(root, 1, f.summary(ruleEdge))
            val rule = f.rule("source versus pass sinkAny")
            sink(f, root, 2, SinkRule(rule, 0, listOf(f.program.pattern(result, tail = Tail.ANY, mark = t)), emptyList()))
            val runs = SequenceRun(f, listOf(root), listOf(AccessPathBase.Zero, value, result))
            val (report, frontiers) = runs.analyze(root, listOf(1, 1, 2))
            val expected = Conclusion(ruleEdge.to, ExclusionSet.Empty, !isSource)
            val grouped = expected.copy(fact = expected.fact.copy(mark = group(f, t)), demand = true)
            assertEquals(setOf(grouped), conclusions(runs.barriers[0].observation.rawSummaries, root, result))
            assertTrue(runs.barriers.all { it.observation.requests.isEmpty() })
            if (isSource) {
                assertEquals(3, runs.barriers.size)
                assertEquals(setOf(expected), conclusions(runs.barriers[2].observation.rawSummaries, root, result))
                assertTrue(runs.barriers[0].observation.selectedDemand.any { it.conclusion == grouped })
                assertTrue(runs.barriers[2].observation.selectedDemand.isEmpty())
                assertEquals(setOf(Seed.Source(root, root.statement, src)), runs.barriers[1].seeds)
                assertEquals(ReportState.DEMAND, report.entries.single().state)
                assertEquals(EndReason.NO_DEMAND_EDGE, report.end.reason)
            } else {
                assertEquals(3, runs.barriers.size)
                assertEquals(setOf(expected), conclusions(runs.barriers[2].observation.rawSummaries, root, result))
                assertTrue(runs.barriers[1].observation.processed.any { edge ->
                    edge.conclusion.fact == src.to && edge.conclusion.demand
                }, "The reversed may target makes its precise requirement DEMAND")
                assertTrue(runs.barriers[1].records.none { native ->
                    native.direction == Direction.BACKWARD && native.conclusion.fact.base == value
                })
                assertEquals(setOf(Seed.Source(root, root.statement, src)), runs.barriers[1].seeds)
                assertEquals(ReportState.DEMAND, report.entries.single().state)
                assertEquals(EndReason.POLICY, report.end.reason)
                assertEquals(listOf(1, 2, 3), frontiers.map { it.run })
            }
        }
    }

    @Test
    fun `AP5 root EXACT requests the group then recovers its concrete descendant from the full source seed`(): Unit {
        DriverFixtures().use { f ->
            val root = f.program.method("AP5 root", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val callee = f.program.method("AP5 root EXACT callee",
                listOf(listOf(1), listOf(2), listOf(3), listOf(4), emptyList()), 4).key()
            val p = AccessPathBase.LocalVar(20)
            val n = f.program.field("n")
            val outer = f.program.field("f")
            val taint = f.program.mark("AP5 T")
            val t = MarkSlot.Concrete(taint)
            val src = source(f, root, PathFact(value, emptyList(), Tail.EXACT, t))
            f.program.statement(callee, 1, MicroEdgeBuilder().apply {
                write(p, listOf(n), weak = false, values = listOf(argument), aliasPaths = emptyList())
            }.build())
            // This is a primitive root action, not a named-field cleaner. Each surrounding write is one field.
            val identity = f.summary(keepEdge(p), ZERO_KEEP)
            f.program.call(callee, 2, CallPlan(setOf(p), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, identity),
                CallStage.Actions(BOUND, ADDED, listOf(CleanStep.Clean(Cleaner(p, null, CleanReach.EXACT, taint)))),
                CallStage.Edges(ADDED, RETURNED, UNRESOLVED, identity),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, identity),
            ), emptyList(), BEFORE, AFTER))
            f.program.statement(callee, 3, MicroEdgeBuilder().apply {
                write(result, listOf(outer), weak = false, values = listOf(p), aliasPaths = emptyList())
            }.build())
            f.program.call(root, 1, call(f, callee, value, argument))
            val rule = f.rule("AP5 f.n sink")
            val exact = f.program.pattern(result, listOf(outer, n), mark = t)
            sink(f, root, 2, SinkRule(rule, 0, listOf(exact), emptyList()))
            val runs = SequenceRun(f, listOf(root, callee), listOf(AccessPathBase.Zero, value, argument, result, p))
            val (report, frontiers) = runs.analyze(root, listOf(1, 1, 2))
            val initialFlow = f.program.pattern(argument, tail = Tail.STAR, mark = MarkSlot.STAR)
            val grouped = group(f, t)
            assertEquals(setOf(ObservedRequest(callee, initialFlow, RequestKind.Mark(grouped.mark))),
                runs.barriers[0].observation.requests)
            assertContains(initialRows(runs.barriers[0], callee), f.program.pattern(argument, mark = grouped))
            val excluded = MarkSlot.Star(MarkSet(intArrayOf(grouped.mark.id)))
            assertContains(conclusions(runs.barriers[0].observation.rawSummaries, callee, result),
                Conclusion(PathFact(result, listOf(outer), Tail.ANY, excluded), ExclusionSet.Empty, true))
            assertContains(conclusions(runs.barriers[0].observation.rawSummaries, callee, result),
                Conclusion(PathFact(result, listOf(outer), Tail.ANY, grouped), ExclusionSet.Empty, true))
            assertContains(runs.barriers[0].demand.getValue(callee), DemandPattern(
                f.program.pattern(result, listOf(outer), Tail.ANY, MarkSlot.STAR), initialFlow))
            assertContains(runs.barriers[0].demand.getValue(callee), DemandPattern(
                f.program.pattern(result, listOf(outer), Tail.ANY, grouped), f.program.pattern(argument, mark = grouped)))
            assertTrue(runs.barriers[1].records.none { it.method == callee && it.direction == Direction.BACKWARD },
                "The limit-one backward query for a two-field sink stays DEMAND")
            assertTrue(runs.barriers[1].observation.rawSummaries.any { it.method == callee &&
                it.conclusion.fact.base == argument && it.conclusion.fact.mark == t && it.conclusion.demand },
                "The coarse backward relation still reaches the actual source argument")
            assertEquals(setOf(Seed.Source(root, root.statement, src)), runs.barriers[1].seeds)
            assertEquals(setOf(Conclusion(exact.fact, ExclusionSet.Empty, false)),
                conclusions(runs.barriers[2].observation.rawSummaries, root, result))
            assertTrue(frontiers[2].recordCrossings > 0L)
            assertEquals(ReportState.DEMAND, report.entries.single().state)
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 3, Direction.FORWARD, EndReason.NO_DEMAND_EDGE,
                run1MarkMode = MarkMode.GROUP), report.end)
        }
    }
}
