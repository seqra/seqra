package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.DriverFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import kotlin.test.*

/** Current F77 sequences. All field transfers below use one field per statement. */
class CurrentSequenceTest {
    private val argument = AccessPathBase.Argument(0)
    private val returned = AccessPathBase.Return
    private val x = AccessPathBase.LocalVar(0)
    private val r = AccessPathBase.LocalVar(1)

    private fun straight(size: Int): List<List<Int>> = List(size) { if (it + 1 < size) listOf(it + 1) else emptyList() }
    private fun sources(f: DriverFixtures, key: MethodKey, vararg edges: PathEdge) {
        f.program.entry(key, RuleStatement(f.summary(ZERO_KEEP, *edges), StatementSummary.EMPTY, emptyList()))
    }
    private fun sink(f: DriverFixtures, id: String, p: Pattern, ends: List<MicroEdge> = emptyList()): SinkRule =
        SinkRule(f.rule(id), 0, listOf(p), ends)
    private fun exit(f: DriverFixtures, key: MethodKey, index: Int, vararg sinks: SinkRule) {
        f.program.exit(key, index, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY,
            sinks.toList()), false, emptySet()))
    }
    private fun resolved(f: DriverFixtures, callee: MethodKey, bindings: List<Pair<AccessPathBase, AccessPathBase>>,
                         target: AccessPathBase): CallPlan = CallPlan(bindings.map { it.first }.toSet() + target, listOf(
        CallStage.Edges(BEFORE, AFTER, PASS_OVER,
            f.summary(*bindings.map { keepEdge(it.first) }.toTypedArray())),
        CallStage.Edges(BEFORE, BOUND, BIND_IN, f.summary(ZERO_KEEP,
            *bindings.map { starEdge(it.first, emptyList(), it.second, emptyList()) }.toTypedArray())),
        CallStage.Actions(BOUND, ADDED, emptyList()),
        CallStage.Callees(ADDED, RETURNED, listOf(callee)),
        CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
        CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, f.summary(ZERO_KEEP,
            starEdge(returned, emptyList(), target, emptyList()))),
    ), emptyList(), BEFORE, AFTER)
    private fun raw(f: DriverFixtures, result: RunResult<RunMethodAnalyzer>, key: MethodKey): Set<Pair<Set<Pattern>, Conclusion>> =
        result.analyzers.single { it.key == key }.summaries.all().flatMap { (premise, facts) ->
            f.ops.leaves(facts).map { premise.members.map { it.toPattern() }.toSet() to
                Conclusion(it.fact, it.exclusion, facts.layer == Layer.DEMAND) }
        }.toSet()
    private fun selected(f: DriverFixtures, result: RunResult<RunMethodAnalyzer>, key: MethodKey): Set<Pair<Set<Pattern>, Conclusion>> =
        result.analyzers.single { it.key == key }.summaries.demandEdges().flatMap { (premise, facts) ->
            f.ops.leaves(facts).map { premise.members.map { it.toPattern() }.toSet() to
                Conclusion(it.fact, it.exclusion, facts.layer == Layer.DEMAND) }
        }.toSet()

    /** Negative control: remove only the backward conjunction demotion from the cached form. */
    private fun normalConjunctionReversal(f: DriverFixtures, method: MethodKey): SharedObjects {
        val original = f.shared
        val contextSource = object : MethodContextSource {
            private val changed = object : MethodForms by f.program.forms(method) {
                private var cached: CallPlan? = null
                override fun <R> withCall(direction: Direction, s: CommonInst, call: CommonCallExpr,
                                          use: (CallPlan) -> R): R = use(call(direction, s, call))
                override fun call(direction: Direction, s: CommonInst, call: CommonCallExpr): CallPlan {
                    val actual = f.program.forms(method).call(direction, s, call)
                    if (direction != Direction.BACKWARD) return actual
                    return cached ?: CallPlan(actual.touched, actual.stages.map { stage ->
                        if (stage !is CallStage.Edges) stage else stage.copy(summary = StatementSummary(
                            stage.summary.touched, stage.summary.edges.map { edge ->
                                if (!edge.conjunctive) edge else MicroEdge(edge.edge, edge.forward, conjunctive = false)
                            }, stage.summary.conjunctions, stage.summary.typeFilters, stage.summary.resultFilters))
                    }, actual.sinks, actual.entry, actual.exit, actual.passReads).also { cached = it }
                }
            }
            override fun forms(key: MethodKey): MethodForms = if (key == method) changed else f.program.forms(key)
            override fun graph(key: MethodKey, direction: Direction) = f.program.graph(key, direction)
        }
        return SharedObjects(original.ap, original.ops, original.interpreter, original.language,
            MethodContextCache(original.interpreter, contextSource), original.records, original.vulnerabilities,
            original.unitResolver, original.refManager, original.cancellation, threads = 1)
    }

    @Test
    fun `WRAP stays zero only after its normal record removes callee demand while root cuts refine through five runs`(): Unit {
        DriverFixtures().use { f ->
            val p = f.program
            val root = p.method("WRAP root", straight(3), 2).key()
            val wrap = p.method("WRAP callee", straight(4), 3).key()
            val a = p.field("a"); val b = p.field("b"); val c = p.field("c"); val field = p.field("f")
            val mark = MarkSlot.Concrete(p.mark("WRAP T"))
            val source = PathEdge(ZERO_FACT, PathFact(x, listOf(a, b, c), Tail.EXACT, mark), ExclusionSet.Empty)
            sources(f, root, source)
            val z = AccessPathBase.LocalVar(2)
            p.statement(wrap, 1, MicroEdgeBuilder().apply { write(z, listOf(field), false, listOf(argument), emptyList()) }.build())
            p.statement(wrap, 2, MicroEdgeBuilder().apply { move(returned, z) }.build())
            p.call(root, 1, resolved(f, wrap, listOf(x to argument), r))
            val finding = sink(f, "WRAP sink", p.pattern(r, listOf(field, a, b, c), mark = mark))
            exit(f, root, 2, finding)
            val driver = f.driver(FixedLimits(listOf(1, 2, 3, 4, 5)))
            val report = driver.analyze(listOf(root))
            assertEquals(listOf(1, 2, 3, 4, 5), f.configs.map { it.index })
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 5, Direction.FORWARD, EndReason.STOP_RULE), report.end)
            assertEquals(ReportState.CONFIRMED, report.entries.single().state)
            assertEquals(5, report.entries.single().run)
            val from = p.pattern(argument, tail = Tail.STAR, mark = MarkSlot.STAR)
            val to = Conclusion(PathFact(returned, listOf(field), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty, false)
            assertContains(raw(f, f.results[0], wrap), setOf(from) to to)
            assertTrue(selected(f, f.results[0], wrap).isEmpty())
            assertContains(driver.frontiers[0].analysed, wrap)
            assertTrue(driver.frontiers[0].demandEdges[wrap] == null)
            for (index in 1..4) {
                assertContains(driver.frontiers[index].zeroOnly, wrap)
                assertTrue(wrap !in driver.frontiers[index].analysed)
                assertTrue(f.configs[index].demand!!.near(wrap, argument, null).isEmpty())
                assertTrue(f.configs[index].demand!!.near(wrap, returned, null).isEmpty())
                assertTrue(raw(f, f.results[index], wrap).all { it.first == setOf(ZERO_PATTERN) })
                val selected = selected(f, f.results[index], wrap)
                assertEquals(if (f.results[index].direction == Direction.BACKWARD)
                    setOf(setOf(ZERO_PATTERN) to Conclusion(ZERO_FACT, ExclusionSet.Empty, false))
                    else emptySet(), selected)
                assertTrue(driver.frontiers[index].recordCrossings >= 1)
            }
            assertTrue(f.configs[2].seeds.allowsSource(root, root.statement, source))
            assertTrue(f.configs[4].seeds.allowsSource(root, root.statement, source))
            assertEquals(listOf(1, 0, 1, 0, 0), driver.frontiers.map { it.demandVulnerabilities })
            assertTrue(f.results[0].witnessesByKey().values.flatten().all { !it.confirmed })
            assertTrue(f.results[2].witnessesByKey().values.flatten().all { !it.confirmed })
            assertTrue(f.results[4].witnessesByKey().values.flatten().any { it.confirmed })
            val records = f.records.byEntry(wrap, from).filter { it.direction == Direction.FORWARD }.toList()
            val record = records.single { f.ops.leaves(it.conclusion).any { it.fact.base == returned } }
            assertEquals(setOf(Pattern(to.fact, to.exclusion)), f.ops.leaves(record.conclusion).toSet())
            assertTrue(records.any { f.ops.leaves(it.conclusion).any { it == from } })
            assertTrue(f.results.filter { it.direction == Direction.BACKWARD }.all { it.witnessesByKey().isEmpty() })
        }
    }

    @Test
    fun `a nonreturning worker loop reports its sink without waiting for a normal summary`(): Unit {
        DriverFixtures().use { f ->
            val p = f.program
            val root = p.method("worker caller", straight(3), 2).key()
            val worker = p.method("nonreturning worker", listOf(listOf(1), listOf(2), listOf(1), emptyList()), 3).key()
            val t = MarkSlot.Concrete(p.mark("worker T"))
            val source = PathEdge(ZERO_FACT, PathFact(x, emptyList(), Tail.EXACT, t), ExclusionSet.Empty)
            sources(f, root, source)
            p.call(root, 1, resolved(f, worker, listOf(x to argument), r))
            val finding = sink(f, "nonreturning worker sink", p.pattern(argument, mark = t))
            val keep = f.summary(keepEdge(argument), ZERO_KEEP)
            p.call(worker, 1, CallPlan(setOf(argument), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, keep),
                CallStage.Actions(BOUND, ADDED, emptyList()),
                CallStage.Edges(ADDED, RETURNED, UNRESOLVED, keep),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, keep),
            ), listOf(finding), BEFORE, AFTER))
            val driver = f.driver(FixedLimits(listOf(1, 2, 3)))
            val report = driver.analyze(listOf(root))
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 1, Direction.FORWARD, EndReason.STOP_RULE), report.end)
            assertEquals(listOf(finding.rule), report.entries.map { it.key.rule })
            assertEquals(ReportState.CONFIRMED, report.entries.single().state)
            val actor = f.results.single().analyzers.single { it.key == worker }
            assertTrue(actor.summaries.all().none())
            assertTrue(actor.links.links().any { it.caller.caller == root && it.linkLayer == Layer.NORMAL })
            assertTrue(f.processed.any { it.method == worker && it.delta.node == p.inst(worker, 2) })
            assertTrue(f.processed.none { it.method == worker && it.delta.node == p.inst(worker, 3) })
            assertContains(driver.frontiers.single().analysed, worker)
        }
    }

    @Test
    fun `an incomplete third run preserves both states of the first complete forward report`(): Unit {
        for (status in listOf(RunStatus.TIMEOUT, RunStatus.OOM, RunStatus.FAILED)) DriverFixtures().use { f ->
            val confirmed = f.confirmed()
            val demand = f.persistentDemand()
            val visited = ArrayList<Int>()
            val driver = f.driver(FixedLimits(listOf(1, 2, 3)), invokeRun = { config, owner, timeout ->
                visited += config.index
                if (config.index < 3) f.run(config, owner, timeout)
                else f.manager(config, owner).let { manager ->
                    manager.fail(status)
                    manager.run(timeout)
                }
            })
            val report = driver.analyze(listOf(confirmed.root, demand.root))
            assertEquals(listOf(1, 2, 3), visited)
            assertEquals(AnalysisEnd(status, 3, Direction.FORWARD, EndReason.ABNORMAL), report.end)
            assertEquals(mapOf(confirmed.rule to ReportState.CONFIRMED, demand.rule to ReportState.DEMAND),
                report.entries.associate { it.key.rule to it.state })
            assertTrue(report.entries.all { it.run == 1 && it.witnesses.all { it.run == 1 } })
            assertEquals(listOf(1, 2), driver.frontiers.map { it.run })
            assertFalse(f.cancellation.isActive())
        }
    }

    @Test
    fun `END recovers the already confirmed trigger seed inside M and retains its later may finding`(): Unit {
        DriverFixtures().use { f ->
            val p = f.program
            val root = p.method("END root", straight(3), 2).key()
            val m = p.method("END M", straight(5), 4).key()
            val t = MarkSlot.Concrete(p.mark("END T")); val u = MarkSlot.Concrete(p.mark("END U"))
            val source = PathEdge(ZERO_FACT, PathFact(x, emptyList(), Tail.EXACT, t), ExclusionSet.Empty)
            sources(f, root, source)
            p.call(root, 1, resolved(f, m, listOf(x to argument), r))
            val y = AccessPathBase.LocalVar(2); val w = AccessPathBase.LocalVar(3)
            val end = MicroEdge.of(PathEdge(ZERO_FACT, PathFact(returned, emptyList(), Tail.EXACT, u), ExclusionSet.Empty))
            val early = sink(f, "END confirmed trigger", p.pattern(argument, mark = t), listOf(end))
            p.call(m, 1, CallPlan(setOf(argument, y), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, f.summary(keepEdge(argument), ZERO_KEEP)),
                CallStage.Edges(BOUND, RETURNED, END_FACTS, f.summary(end.forward), Guard.SinkTriggered(early)),
                CallStage.Actions(BOUND, ADDED, emptyList()),
                CallStage.Edges(ADDED, RETURNED, UNRESOLVED, f.summary(keepEdge(argument), ZERO_KEEP)),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, f.summary(starEdge(returned, emptyList(), y, emptyList()), ZERO_KEEP)),
            ), listOf(early), BEFORE, AFTER))
            val may = PathEdge(PathFact(argument, emptyList(), Tail.STAR, MarkSlot.STAR),
                PathFact(returned, emptyList(), Tail.ANY, MarkSlot.STAR), ExclusionSet.Empty)
            p.call(m, 2, CallPlan(setOf(y, w), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, f.summary(starEdge(y, emptyList(), argument, emptyList()), ZERO_KEEP)),
                CallStage.Actions(BOUND, ADDED, emptyList()),
                CallStage.Edges(ADDED, RETURNED, UNRESOLVED, f.summary(may, ZERO_KEEP)),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, f.summary(starEdge(returned, emptyList(), w, emptyList()), ZERO_KEEP)),
            ), emptyList(), BEFORE, AFTER))
            p.statement(m, 3, MicroEdgeBuilder().apply { move(returned, w) }.build())
            val later = sink(f, "END later demand", p.pattern(r, tail = Tail.ANY, mark = u))
            exit(f, root, 2, later)
            val driver = f.driver(FixedLimits(listOf(1, 2, 3)))
            val report = driver.analyze(listOf(root))
            val earlyKey = VulnerabilityKey(early.rule, m.method, p.inst(m, 1))
            val laterKey = VulnerabilityKey(later.rule, root.method, p.inst(root, 2))
            assertEquals(listOf(1, 2, 3), f.configs.map { it.index })
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 3, Direction.FORWARD, EndReason.POLICY), report.end)
            assertEquals(mapOf(earlyKey to ReportState.CONFIRMED, laterKey to ReportState.DEMAND),
                report.entries.associate { it.key to it.state })
            assertEquals(1, report.entries.single { it.key == earlyKey }.run)
            assertEquals(3, report.entries.single { it.key == laterKey }.run)
            assertTrue(f.configs[1].seeds.at(m, p.inst(m, 1)).isEmpty())
            assertEquals(listOf(Seed.Sink(later.rule, root, p.inst(root, 2), later.patterns.single())),
                f.configs[1].seeds.at(root, p.inst(root, 2)))
            val backward = f.results[1].analyzers.single { it.key == m }
            assertEquals(1L, backward.triggerSeeds)
            assertEquals(listOf(Triple(root, root.statement, source)),
                f.results[1].analyzers.flatMap { it.sourceHits!!.entries().toList() })
            assertTrue(f.configs[2].seeds.allowsSource(root, root.statement, source))
            assertContains(f.configs[2].demand!!.near(m, argument, null), DemandPattern(p.pattern(argument, mark = t), null))
            val endOutput = Conclusion(PathFact(returned, emptyList(), Tail.ANY, u), ExclusionSet.Empty, true)
            assertContains(selected(f, f.results[2], m), setOf(ZERO_PATTERN) to endOutput)
            assertTrue(f.results[2].witnessesByKey().getValue(earlyKey).any { it.confirmed })
            assertTrue(f.results[2].witnessesByKey().getValue(laterKey).all { !it.confirmed })
            assertTrue(f.results[1].witnessesByKey().isEmpty())
            assertEquals(listOf(1, 1, 1), driver.frontiers.map { it.seeds })
            assertEquals(listOf(1, 0, 1), driver.frontiers.map { it.confirmedVulnerabilities })
        }
    }

    @Test
    fun `CONJ backward literal branches stay demand and only the call with both inputs confirms after refinement`(): Unit {
        for (normalReversal in listOf(false, true)) DriverFixtures().use { f ->
            val p = f.program
            val root = p.method("CONJ root", straight(7), 6).key()
            val m = p.method("CONJ M", straight(2), 1).key()
            val a = x; val b = AccessPathBase.LocalVar(2); val clean = AccessPathBase.LocalVar(3)
            val z = AccessPathBase.LocalVar(4); val y = AccessPathBase.LocalVar(5); val bad = AccessPathBase.LocalVar(6)
            val arg1 = AccessPathBase.Argument(1)
            val t1 = MarkSlot.Concrete(p.mark("CONJ T1")); val t2 = MarkSlot.Concrete(p.mark("CONJ T2"))
            val t = MarkSlot.Concrete(p.mark("CONJ T"))
            val field = p.field("f"); val g = p.field("g")
            val sa = PathEdge(ZERO_FACT, PathFact(a, emptyList(), Tail.EXACT, t1), ExclusionSet.Empty)
            val sb = PathEdge(ZERO_FACT, PathFact(b, emptyList(), Tail.EXACT, t2), ExclusionSet.Empty)
            sources(f, root, sa, sb)
            val nd = ConjunctiveEdge(listOf(p.pattern(argument, mark = t1), p.pattern(arg1, mark = t2)),
                PathFact(returned, emptyList(), Tail.EXACT, t))
            // The unresolved lib call is the one statement of M.
            p.call(m, 0, CallPlan(setOf(argument, arg1, returned), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, f.summary(keepEdge(argument), keepEdge(arg1), ZERO_KEEP)),
                CallStage.Actions(BOUND, ADDED, emptyList()),
                CallStage.Edges(ADDED, RETURNED, UNRESOLVED,
                    StatementSummary(setOf(argument, arg1, returned), listOf(MicroEdge.of(ZERO_KEEP)), listOf(nd), emptyMap())),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, f.summary(keepEdge(returned), ZERO_KEEP)),
            ), emptyList(), BEFORE, AFTER))
            p.call(root, 1, resolved(f, m, listOf(a to argument, b to arg1), r))
            // Build y.f.g with two one-field writes on fresh objects.
            p.statement(root, 2, MicroEdgeBuilder().apply { write(z, listOf(g), false, listOf(r), emptyList()) }.build())
            p.statement(root, 3, MicroEdgeBuilder().apply { write(y, listOf(field), false, listOf(z), emptyList()) }.build())
            p.call(root, 5, resolved(f, m, listOf(a to argument, clean to arg1), bad))
            val goodSink = sink(f, "CONJ both inputs", p.pattern(y, listOf(field, g), mark = t))
            val badSink = sink(f, "CONJ missing second input", p.pattern(bad, mark = t))
            exit(f, root, 6, goodSink, badSink)
            val control = if (normalReversal) normalConjunctionReversal(f, m) else null
            val driver = f.driver(FixedLimits(listOf(1, 2, 3)), invokeRun = { config, owner, timeout ->
                f.run(config, control ?: owner, timeout)
            })
            val report = try { driver.analyze(listOf(root)) } finally { control?.close() }
            assertEquals(listOf(1, 2, 3), f.configs.map { it.index })
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 3, Direction.FORWARD, EndReason.STOP_RULE), report.end)
            assertEquals(if (normalReversal) setOf(goodSink.rule, badSink.rule) else setOf(goodSink.rule),
                report.entries.map { it.key.rule }.toSet())
            assertTrue(report.entries.all { it.state == ReportState.CONFIRMED && it.run == 3 })
            assertTrue(f.results.filter { it.direction == Direction.FORWARD }.all { result ->
                normalReversal && result.runIndex == 3 || result.witnessesByKey().keys.none { it.rule === badSink.rule }
            })
            val required = setOf(p.pattern(argument, mark = t1), p.pattern(arg1, mark = t2))
            assertContains(raw(f, f.results[0], m), required to Conclusion(nd.target, ExclusionSet.Empty, false))
            val backward = selected(f, f.results[1], m)
            val literalRows = if (normalReversal) raw(f, f.results[1], m) else backward
            assertTrue(literalRows.any { it.second.fact == required.first().fact && it.second.demand == !normalReversal })
            assertTrue(literalRows.any { it.second.fact == required.last().fact && it.second.demand == !normalReversal })
            assertTrue(backward.filter { it.second.fact.base != AccessPathBase.Zero }.all { it.second.demand })
            assertContains(backward, setOf(ZERO_PATTERN) to Conclusion(ZERO_FACT, ExclusionSet.Empty, false))
            val concreteReturn = p.pattern(returned, mark = t)
            assertEquals(normalReversal, f.records.byEntry(m, concreteReturn).any { it.direction == Direction.BACKWARD },
                "Split literal branches cannot become independent normal records")
            assertEquals(!normalReversal, f.configs[2].demand!!.near(m, argument, null).any { it.entry == required.first() })
            assertEquals(!normalReversal, f.configs[2].demand!!.near(m, arg1, null).any { it.entry == required.last() })
            assertTrue(f.configs[2].seeds.allowsSource(root, root.statement, sa))
            assertTrue(f.configs[2].seeds.allowsSource(root, root.statement, sb))
            assertTrue(f.results[2].witnessesByKey().values.flatten().any { it.confirmed })
            assertTrue(f.results[2].analyzers.single { it.key == m }.links.links().any { link ->
                link.caller.call == p.inst(root, 5) && link.addedFact == required.first() && link.linkLayer == Layer.NORMAL
            }, "The second call really supplies T1 even when the independent record bypasses callee emission")
            assertEquals(listOf(1, 0, 0), driver.frontiers.map { it.demandVulnerabilities })
        }
    }
}
