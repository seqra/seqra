package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.RunStatus
import org.opentaint.dataflow.bidi.fixtures.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import kotlin.test.*

/** These argued interpreter extensions are deliberately separate from the fb789d0ab Lean domain. */
class HistoricalFixedResidualTest {
    private val arg = AccessPathBase.Argument(0)
    private val ret = AccessPathBase.Return
    private val x = AccessPathBase.LocalVar(0)
    private val r = AccessPathBase.LocalVar(1)
    private fun straight(size: Int) = List(size) { if (it + 1 < size) listOf(it + 1) else emptyList() }
    private fun resolved(f: DriverFixtures, callee: MethodKey): CallPlan = CallPlan(setOf(x, r), listOf(
        CallStage.Edges(BEFORE, AFTER, PASS_OVER, f.summary(keepEdge(x))),
        CallStage.Edges(BEFORE, BOUND, BIND_IN, f.summary(ZERO_KEEP, starEdge(x, emptyList(), arg, emptyList()))),
        CallStage.Actions(BOUND, ADDED, emptyList()), CallStage.Callees(ADDED, RETURNED, listOf(callee)),
        CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
        CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, f.summary(ZERO_KEEP, starEdge(ret, emptyList(), r, emptyList()))),
    ), emptyList(), BEFORE, AFTER)

    private fun states(report: Report): Map<HistoricalFinding, HistoricalState> = report.entries.associate {
        HistoricalFinding(it.key.rule, it.key.statement) to if (it.state == ReportState.CONFIRMED)
            HistoricalState.CONFIRMED else HistoricalState.DEMAND
    }

    @Test
    fun `pre F72 AP equations plus specified END trigger rules retain early confirmation and later demand`(): Unit = DriverFixtures().use { f ->
        val p = f.program
        val root = p.method("historical END root", straight(3), 2).key()
        val m = p.method("historical END M", straight(5), 4).key()
        val t = MarkSlot.Concrete(p.mark("END T")); val u = MarkSlot.Concrete(p.mark("END U"))
        val source = PathEdge(ZERO_FACT, PathFact(x, emptyList(), Tail.EXACT, t), ExclusionSet.Empty)
        p.entry(root, RuleStatement(f.summary(ZERO_KEEP, source), StatementSummary.EMPTY, emptyList()))
        p.call(root, 1, resolved(f, m))
        val y = AccessPathBase.LocalVar(2); val w = AccessPathBase.LocalVar(3)
        val end = MicroEdge.of(PathEdge(ZERO_FACT, PathFact(ret, emptyList(), Tail.EXACT, u), ExclusionSet.Empty))
        val early = SinkRule(f.rule("historical END early"), 0, listOf(p.pattern(arg, mark = t)), listOf(end))
        p.call(m, 1, CallPlan(setOf(arg, y), listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, f.summary(keepEdge(arg), ZERO_KEEP)),
            CallStage.Edges(BOUND, RETURNED, END_FACTS, f.summary(end.forward), Guard.SinkTriggered(early)),
            CallStage.Actions(BOUND, ADDED, emptyList()),
            CallStage.Edges(ADDED, RETURNED, UNRESOLVED, f.summary(keepEdge(arg), ZERO_KEEP)),
            CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, f.summary(starEdge(ret, emptyList(), y, emptyList()), ZERO_KEEP)),
        ), listOf(early), BEFORE, AFTER))
        val may = PathEdge(PathFact(arg, emptyList(), Tail.STAR, MarkSlot.STAR),
            PathFact(ret, emptyList(), Tail.ANY, MarkSlot.STAR), ExclusionSet.Empty)
        p.call(m, 2, CallPlan(setOf(y, w), listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, f.summary(starEdge(y, emptyList(), arg, emptyList()), ZERO_KEEP)),
            CallStage.Actions(BOUND, ADDED, emptyList()),
            CallStage.Edges(ADDED, RETURNED, UNRESOLVED, f.summary(may, ZERO_KEEP)),
            CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, f.summary(starEdge(ret, emptyList(), w, emptyList()), ZERO_KEEP)),
        ), emptyList(), BEFORE, AFTER))
        p.statement(m, 3, MicroEdgeBuilder().apply { move(ret, w) }.build())
        val later = SinkRule(f.rule("historical END later"), 0, listOf(p.pattern(r, tail = Tail.ANY, mark = u)), emptyList())
        p.exit(root, 2, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, listOf(later)), false, emptySet()))
        val expected = mapOf(HistoricalFinding(early.rule, p.inst(m, 1)) to HistoricalState.CONFIRMED,
            HistoricalFinding(later.rule, p.inst(root, 2)) to HistoricalState.DEMAND)
        val limits = listOf(1, 2, 3)
        val old = HistoricalConcreteIteration(p, listOf(root), limits, allowEndFacts = true).run()
        val direct = DirectCurrentIteration(p, listOf(root), limits, allowEndFacts = true).run()
        val report = f.driver(FixedLimits(limits)).analyze(listOf(root))
        val label = "F71 AP equations plus F67/F68 END interpreter rules; proof-history.md:1599 excludes END from Lean"
        assertEquals(expected, old.findings, label)
        assertEquals(expected, direct.findings, label)
        assertEquals(expected, states(report), label)
        assertEquals(AnalysisEnd(RunStatus.COMPLETE, 3, Direction.FORWARD, EndReason.POLICY), report.end)
        assertEquals(listOf(1, 2, 3), old.runs.map { it.index }, label)
        assertEquals(setOf(Seed.Sink(later.rule, root, p.inst(root, 2), later.patterns.single())), old.runs[0].handOff.seeds.toSet(),
            "An already confirmed END trigger is absent from the ordinary demand seeds")
        assertContains(old.runs[1].observation.sourceHits, ObservedSourceHit(root, root.statement, source),
            "The reversed end fact fires its trigger, restoring the original T source path")
        assertContains(old.runs[1].handOff.seeds, Seed.Source(root, root.statement, source))
        assertTrue(old.runs[2].observation.witnesses.any { it.rule == later.rule && it.edges.all { edge -> edge.layer == Layer.DEMAND } })
        for (offset in 1..2) {
            val config = f.configs[offset]
            val actualSeeds = listOf(root, m).flatMap { key -> p.programMethod(key).successors.indices.flatMap { index ->
                config.seeds.at(key, p.inst(key, index))
            } }.toSet()
            assertEquals(direct.runs[offset].inputs.seeds.toSet(), actualSeeds, "Current direct incoming seeds at run ${offset + 1}")
        }
    }

    @Test
    fun `pre F76 R3 never fabricates a normal STAR read view from a must result with field exclusion`(): Unit = FiniteProgramFixtures().use { f ->
        val t = MarkSlot.Concrete(f.mark("old factory T")); val excluded = f.field("name")
        val premise = ZERO_PATTERN
        val must = Conclusion(PathFact(ret, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.of(excluded), false)
        assertFalse(HistoricalF71.cross(premise, must), "HandoffDefs.CrossK excludes the old any/must result")
        assertNull(HistoricalF71.reverseRecord(premise, must), "ap-history.md F69 amendment R3 rejects nonempty must E")
        val empty = must.copy(exclusion = ExclusionSet.Empty)
        val old = assertNotNull(HistoricalF71.reverseRecord(premise, empty))
        assertEquals(Pattern(empty.fact.copy(tail = Tail.ANY), ExclusionSet.Empty), old.premise)
        assertEquals(Conclusion(ZERO_FACT, ExclusionSet.Empty, false), old.result)
        assertNull(HistoricalF71.reverseRecord(Pattern(empty.fact, ExclusionSet.Empty),
            Conclusion(PathFact(ret, emptyList(), Tail.EXACT, t), ExclusionSet.Empty, false)),
            "The old R3 rule also rejects a must premise")
    }

    @Test
    fun `pre F76 factory fallback performs real backward source analysis while current native views retain both findings`(): Unit = DriverFixtures().use { f ->
        val p = f.program
        val root = p.method("historical factory root", straight(9), 8).key()
        val factory = p.method("historical factory", straight(4), 3).key()
        val name = p.field("name"); val email = p.field("email"); val k = p.field("k")
        val path = listOf(p.field("a"), p.field("b"), p.field("c"))
        val t = MarkSlot.Concrete(p.mark("factory T")); val u = MarkSlot.Concrete(p.mark("factory U"))
        val sourceT = PathEdge(ZERO_FACT, PathFact(ret, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.Empty)
        val sourceU = PathEdge(ZERO_FACT, PathFact(x, emptyList(), Tail.EXACT, u), ExclusionSet.Empty)
        p.entry(factory, RuleStatement(f.summary(ZERO_KEEP, sourceT, sourceU), StatementSummary.EMPTY, emptyList()))
        p.statement(factory, 1, MicroEdgeBuilder().apply { write(ret, listOf(name), false, emptyList(), emptyList()) }.build())
        p.statement(factory, 2, MicroEdgeBuilder().apply { write(ret, listOf(k), false, listOf(x), emptyList()) }.build())
        val returned = AccessPathBase.LocalVar(10); val readT = AccessPathBase.LocalVar(11)
        val readU = AccessPathBase.LocalVar(12); val blocked = AccessPathBase.LocalVar(13)
        val outU = AccessPathBase.Argument(1); val outBlocked = AccessPathBase.Argument(2)
        p.call(root, 1, CallPlan(setOf(returned), listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, f.summary(ZERO_KEEP)),
            CallStage.Actions(BOUND, ADDED, emptyList()), CallStage.Callees(ADDED, RETURNED, listOf(factory)),
            CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, f.summary(starEdge(ret, emptyList(), returned, emptyList()), ZERO_KEEP)),
        ), emptyList(), BEFORE, AFTER))
        p.statement(root, 2, MicroEdgeBuilder().apply { read(readT, returned, listOf(email)) }.build())
        p.statement(root, 3, MicroEdgeBuilder().apply { move(ret, readT, path) }.build())
        p.statement(root, 4, MicroEdgeBuilder().apply { read(readU, returned, listOf(k)) }.build())
        p.statement(root, 5, MicroEdgeBuilder().apply { move(outU, readU, path) }.build())
        p.statement(root, 6, MicroEdgeBuilder().apply { read(blocked, returned, listOf(name)) }.build())
        p.statement(root, 7, MicroEdgeBuilder().apply { move(outBlocked, blocked, path) }.build())
        val admittedT = SinkRule(f.rule("historical factory email T"), 0, listOf(p.pattern(ret, path, mark = t)), emptyList())
        val admittedU = SinkRule(f.rule("historical factory exact k U"), 0, listOf(p.pattern(outU, path, mark = u)), emptyList())
        val excluded = SinkRule(f.rule("historical factory excluded name"), 0, listOf(p.pattern(outBlocked, path, mark = t)), emptyList())
        p.exit(root, 8, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY,
            listOf(admittedT, admittedU, excluded)), false, emptySet()))
        val limits = listOf(1, 2, 3)
        val old = HistoricalConcreteIteration(p, listOf(root), limits, allowMustSources = true).run()
        val direct = DirectCurrentIteration(p, listOf(root), limits, allowMustSources = true).run()
        val report = f.driver(FixedLimits(limits)).analyze(listOf(root))
        val expected = mapOf(HistoricalFinding(admittedT.rule, p.inst(root, 8)) to HistoricalState.CONFIRMED,
            HistoricalFinding(admittedU.rule, p.inst(root, 8)) to HistoricalState.CONFIRMED)
        val label = "F69 refined must/E plus F71 concrete hand-off, prior to F76 record views; ap-history.md F69 amendment R3"
        assertEquals(expected, old.findings, label)
        assertEquals(expected, direct.findings, label)
        assertEquals(expected, states(report), label)
        assertEquals(listOf(1, 2, 3), old.runs.map { it.index }, label)
        assertTrue(old.runs[1].observation.initials.any { it.method == factory && it.initial.fact.base != AccessPathBase.Zero },
            "A non-reversible must/E record forces old backward callee analysis")
        assertContains(old.runs[1].observation.sourceHits, ObservedSourceHit(factory, factory.statement, sourceT))
        assertContains(old.runs[1].handOff.seeds, Seed.Source(factory, factory.statement, sourceT))
        assertTrue(direct.runs[1].observation.initials.filter { it.method == factory }.all { it.initial == ZERO_PATTERN })
        assertTrue(direct.runs[1].observation.sourceHits.isEmpty(), "F76 record application is not source execution")
        assertTrue(f.configs[2].seeds.at(factory, factory.statement).isEmpty())
        assertTrue(old.runs[0].observation.rawSummaries.any { it.method == factory && it.conclusion ==
            Conclusion(PathFact(ret, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.of(intArrayOf(name, k)), false) })
        assertTrue(old.runs[2].observation.rawSummaries.any { it.method == factory && it.conclusion.fact.base == ret &&
            it.conclusion.fact.tail == Tail.ANY_TAINT }, "The genuine source seed permits the old forward source again")
    }
}
