package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.driver.ReportBuilder
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.DriverFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.bidi.store.DemandStore
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** Analyzer inventory 18/interpreter 23: caller-owned state is not callee-owned G2 state. */
class CallerStateAcceptanceTest {
    @Test
    fun `caller state triggers release and remains visible after its G2 exit in run one and a restricted run`(): Unit {
        DriverFixtures().use { f ->
            val root = f.program.method("caller state root", listOf(listOf(1), listOf(2), listOf(3), listOf(4), emptyList()), 4).key()
            val acquire = f.program.method("acquire state", listOf(listOf(1), emptyList()), 1).key()
            val release = f.program.method("release state", listOf(listOf(1), emptyList()), 1).key()
            val after = f.program.method("after state", listOf(listOf(1), emptyList()), 1).key()
            val static = AccessPathBase.ClassStatic
            val cls = f.manager.accessors.index(ClassStaticAccessor("Lock"))
            val mark = MarkSlot.Concrete(f.program.mark("STATE"))
            val state = f.program.pattern(static, listOf(cls), Tail.EXACT, mark)
            val source = PathEdge(ZERO_FACT, state.fact, ExclusionSet.Empty)
            f.program.entry(acquire, RuleStatement(f.summary(ZERO_KEEP, source), StatementSummary.EMPTY, emptyList()))
            val taint = MarkSlot.Concrete(f.program.mark("T"))
            val returned = f.program.pattern(AccessPathBase.Return, mark = taint)
            val releaseResult = PathEdge(ZERO_FACT, returned.fact, ExclusionSet.Empty)
            val afterResult = PathEdge(ZERO_FACT, returned.fact, ExclusionSet.Empty)
            f.program.entry(release, RuleStatement(f.summary(ZERO_KEEP, releaseResult), StatementSummary.EMPTY, emptyList()))
            f.program.entry(after, RuleStatement(f.summary(ZERO_KEEP, afterResult), StatementSummary.EMPTY, emptyList()))
            val releaseSink = SinkRule(f.rule("release state sink"), 0, listOf(state, returned), emptyList())
            val afterSink = SinkRule(f.rule("after state sink"), 0, listOf(state, returned), emptyList())
            fun exit(sink: SinkRule): ExitRules = ExitRules(
                RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, listOf(sink)), true, emptySet())
            f.program.exit(release, 1, exit(releaseSink))
            f.program.exit(after, 1, exit(afterSink))
            fun call(callee: MethodKey): CallPlan {
                // Consume the caller's Result at each binding. Each callee generates its own tainted Result.
                val identity = StatementSummary(setOf(static, AccessPathBase.Zero, AccessPathBase.Return),
                    listOf(MicroEdge.of(keepEdge(static)), MicroEdge.of(ZERO_KEEP)), emptyList(), emptyMap())
                return CallPlan(setOf(static, AccessPathBase.Zero, AccessPathBase.Return), listOf(
                    CallStage.Edges(BEFORE, BOUND, BIND_IN, identity),
                    CallStage.Actions(BOUND, ADDED, emptyList()),
                    CallStage.Callees(ADDED, RETURNED, listOf(callee)),
                    CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                    CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, identity),
                ), emptyList(), BEFORE, AFTER)
            }
            f.program.call(root, 1, call(acquire))
            f.program.call(root, 2, call(release))
            f.program.call(root, 3, call(after))

            val demand = DemandStore.Builder(f.manager).also { builder ->
                builder.add(acquire, DemandPattern(f.manager.zero.toPattern(), state))
                builder.add(release, DemandPattern(state, state))
                builder.add(after, DemandPattern(state, state))
            }.build()
            for (index in listOf(1, 3)) {
                val config = RunConfig(index, if (index == 1) 1 else 3,
                    if (index == 1) null else demand, f.records.view(),
                    if (index == 1) SeedIndex.EMPTY else SeedIndex.of(listOf(
                        Seed.Source(acquire, f.program.inst(acquire, 0), source),
                        Seed.Source(release, f.program.inst(release, 0), releaseResult),
                        Seed.Source(after, f.program.inst(after, 0), afterResult))),
                    listOf(root))
                val result = f.run(config, timeout = 5.seconds)
                assertEquals(RunStatus.COMPLETE, result.status)
                assertTrue(f.records.byEntry(release, state).none(), "No previous-run record may mask state loss at release")
                val actors = result.analyzers.associateBy { it.key }
                val initial = f.manager.initial(state)
                val stateLayer = if (index == 1) Layer.DEMAND else Layer.NORMAL
                val expected = Conclusion(state.fact, ExclusionSet.Empty, index == 1)
                for (callee in listOf(release, after)) {
                    val actor = actors.getValue(callee)
                    assertTrue(actor.summaries.all().any { (premise, facts) ->
                        premise == initial && f.ops.leaves(facts).any {
                            Conclusion(it.fact, it.exclusion, facts.layer == Layer.DEMAND) == expected
                        }
                    }, "Caller-owned state must remain in $callee's nonzero raw summary in run $index")
                    val link = f.links(index, actor.key).asSequence().single {
                        it.addedFact == state && it.caller.caller == root &&
                            it.linkLayer == stateLayer && it.caller.callerLayer == stateLayer
                    }
                    assertEquals(stateLayer, link.linkLayer)
                    assertEquals(stateLayer, link.caller.callerLayer)
                    assertTrue(link.caller.premise.isZero)
                    assertEquals(f.program.inst(root, if (callee == release) 2 else 3), link.caller.call)
                    assertTrue(actor.summaries.all().any { (premise, facts) ->
                        premise.isZero && facts.layer == Layer.NORMAL && f.ops.leaves(facts).any { it.fact == returned.fact }
                    }, "Each callee's zero source supplies its independent Result literal")
                }
                val witnesses = result.witnessesByKey()
                assertEquals(setOf(releaseSink.rule, afterSink.rule), witnesses.keys.map { it.rule }.toSet())
                for ((key, rows) in witnesses) {
                    val expectedCallee = if (key.rule === releaseSink.rule) release else after
                    assertEquals(f.program.inst(expectedCallee, 1), key.statement)
                    assertTrue(rows.any { witness ->
                        witness.methodKey == expectedCallee && witness.edges.size == 2 &&
                            witness.edges.map { it.premise } == listOf(initial, f.manager.zero) &&
                            witness.edges.map { it.layer } == listOf(stateLayer, Layer.NORMAL) &&
                            witness.edges.map { it.facts.layer } == listOf(stateLayer, Layer.NORMAL) &&
                            witness.edges.map { f.ops.leaves(it.facts).map { leaf -> leaf.fact }.toSet() } ==
                                listOf(setOf(state.fact), setOf(returned.fact)) &&
                            witness.supportPremise(f.manager) == initial
                    }, "Both sinks must receive caller state with its exact selected layer in run $index")
                }
                val report = ReportBuilder().also { it.add(config, result) }
                assertTrue(report.hasDemandVulnerability())
                assertTrue(witnesses.values.flatten().all { !it.confirmed },
                    "Caller-owned state is exact, but this run supplied no confirmation certificate")
                if (index == 3) {
                    assertTrue(actors.getValue(release).summaries.demandEdges().none())
                    assertTrue(actors.getValue(after).summaries.demandEdges().none())
                }
            }
        }
    }
}
