package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.FixedLimits
import org.opentaint.dataflow.bidi.driver.HandOff
import org.opentaint.dataflow.bidi.driver.ReportBuilder
import org.opentaint.dataflow.bidi.driver.ReportState
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.engine.RunStatus
import org.opentaint.dataflow.bidi.fixtures.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import java.util.Random
import kotlin.test.*

/** Historical F71 is a labelled regression comparator, not the semantic oracle for current behavior. */
class ConcreteDesignDifferentialTest {
    private val x = AccessPathBase.Argument(0)
    private val ret = AccessPathBase.Return
    private data class DemandCell(val method: MethodKey, val entry: TestLocation, val exit: TestLocation?)

    /** Decode actual public stores only. The expected side is the independently scanned raw barrier. */
    private fun actualHandOff(f: DriverFixtures, keys: List<MethodKey>, handOff: HandOff,
                              bases: List<AccessPathBase> = listOf(AccessPathBase.Zero, x, ret)): ProgramRunInputs {
        val demand = keys.flatMap { key -> bases.flatMap { base ->
            handOff.demand.near(key, base, null).map { key to it }
        } }.toSet().toList()
        val seeds = keys.flatMap { key -> f.program.programMethod(key).successors.indices.flatMap { index ->
            handOff.seeds.at(key, f.program.inst(key, index))
        } }.toSet().toList()
        val records = keys.flatMap { key -> bases.flatMap { base ->
            f.records.byEntry(key, f.program.pattern(base, tail = Tail.ANY, mark = MarkSlot.STAR)).flatMap { record ->
                f.ops.leaves(record.conclusion).map { leaf ->
                    RawRecord(key, record.direction, record.premise.toPattern(), Conclusion(leaf.fact, leaf.exclusion, false))
                }
            }.toList()
        } }.toSet().toList()
        return ProgramRunInputs(emptyList(), demand, records, seeds)
    }

    private fun compareHandOff(expected: ProgramRunInputs, actual: ProgramRunInputs, universe: BoundedDenotation,
                               diagnostic: String) {
        fun demands(rows: List<Pair<MethodKey, DemandPattern>>): Set<DemandCell> = buildSet {
            for ((key, d) in rows) {
                // Zero without an exit is implicit at every method in the production store.
                if (d.entry == ZERO_PATTERN && d.exit == null) continue
                for (entry in universe.marked(d.entry)) {
                    val exit = d.exit
                    if (exit == null) add(DemandCell(key, entry, null))
                    else for (value in universe.marked(exit)) add(DemandCell(key, entry, value))
                }
            }
        }
        assertEquals(demands(expected.demand), demands(actual.demand), "Outgoing demand union: $diagnostic")
        assertEquals(expected.seeds.toSet(), actual.seeds.toSet(), "Exact outgoing seeds: $diagnostic")
        for (direction in Direction.entries) {
            fun edges(rows: List<RawRecord>) = rows.filter { it.direction == direction }.mapTo(LinkedHashSet()) {
                ObservedEdge(it.method, setOf(it.premise), it.conclusion)
            }
            try {
                RunObservation(rawSummaries = edges(actual.records)).assertEquivalent(
                    RunObservation(rawSummaries = edges(expected.records)), universe)
            } catch (error: AssertionError) {
                throw AssertionError("Raw native records direction=$direction: $diagnostic", error)
            }
        }
    }

    private fun compareVersions(f: DriverFixtures, root: MethodKey, keys: List<MethodKey>, bases: List<AccessPathBase>,
                                fields: List<Int>, marks: List<TaintMark>,
                                limits: List<Int> = listOf(1, 2, 3)): HistoricalIterationResult {
        val old = HistoricalConcreteIteration(f.program, listOf(root), limits).run()
        val expected = DirectCurrentIteration(f.program, listOf(root), limits).run()
        val barriers = ArrayList<ProgramRunInputs>()
        val reportBuilder = ReportBuilder()
        val report = f.driver(FixedLimits(limits), guardBarrier = { shared, body ->
            val next = body()
            val config = f.configs.last()
            val result = f.results.last()
            reportBuilder.add(config, result)
            barriers += actualHandOff(f, keys, HandOff.of(config, result, reportBuilder, shared), bases)
            next
        }).analyze(listOf(root))
        val actual = report.entries.associate { HistoricalFinding(it.key.rule, it.key.statement) to
            if (it.state == ReportState.CONFIRMED) HistoricalState.CONFIRMED else HistoricalState.DEMAND }
        val diagnostic = "version=${HistoricalF71.VERSION} program=${f.program.describe()} old=${old.runs} current=${expected.runs} actual=$actual"
        assertEquals(RunStatus.COMPLETE, report.end.status, diagnostic)
        assertEquals(expected.findings, actual, "Current direct report: $diagnostic")
        assertEquals(expected.runs.size, barriers.size, "Complete barriers: $diagnostic")
        val universe = BoundedDenotation(bases, fields + f.program.field("fresh field"),
            marks + f.program.mark("fresh mark") + TaintMark.ZERO, 2)
        for ((run, barrier) in expected.runs.zip(barriers)) compareHandOff(run.handOff, barrier, universe, diagnostic)
        for ((key, state) in old.findings) {
            assertNotNull(actual[key], "Historical finding lost: $diagnostic")
            if (state == HistoricalState.CONFIRMED) assertEquals(state, actual[key], "Historical confirmation weakened: $diagnostic")
        }
        return old
    }

    private fun resolved(f: DriverFixtures, callee: MethodKey, from: AccessPathBase, to: AccessPathBase): CallPlan =
        CallPlan(setOf(from, to), listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, f.summary(starEdge(from, emptyList(), x, emptyList()), ZERO_KEEP)),
            CallStage.Actions(BOUND, ADDED, emptyList()), CallStage.Callees(ADDED, RETURNED, listOf(callee)),
            CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, f.summary(starEdge(ret, emptyList(), to, emptyList()), ZERO_KEEP)),
        ), emptyList(), BEFORE, AFTER)

    private fun resolvedMany(f: DriverFixtures, callee: MethodKey,
                             bindings: List<Pair<AccessPathBase, AccessPathBase>>, target: AccessPathBase): CallPlan =
        CallPlan(bindings.map { it.first }.toSet() + target, listOf(
            CallStage.Edges(BEFORE, AFTER, PASS_OVER, f.summary(*bindings.map { keepEdge(it.first) }.toTypedArray())),
            CallStage.Edges(BEFORE, BOUND, BIND_IN, f.summary(ZERO_KEEP,
                *bindings.map { starEdge(it.first, emptyList(), it.second, emptyList()) }.toTypedArray())),
            CallStage.Actions(BOUND, ADDED, emptyList()), CallStage.Callees(ADDED, RETURNED, listOf(callee)),
            CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, f.summary(ZERO_KEEP,
                starEdge(ret, emptyList(), target, emptyList()))),
        ), emptyList(), BEFORE, AFTER)

    private fun straight(size: Int): List<List<Int>> =
        List(size) { if (it + 1 < size) listOf(it + 1) else emptyList() }

    @Test
    fun `exact source WRAP counterpart preserves concrete finding through five labelled barriers`(): Unit = DriverFixtures().use { f ->
        val p = f.program
        val root = p.method("F71 WRAP root", straight(3), 2).key()
        val wrap = p.method("F71 WRAP callee", straight(4), 3).key()
        val input = AccessPathBase.LocalVar(0); val output = AccessPathBase.LocalVar(1); val z = AccessPathBase.LocalVar(2)
        val a = p.field("a"); val b = p.field("b"); val c = p.field("c"); val field = p.field("f")
        val t = MarkSlot.Concrete(p.mark("WRAP T"))
        val source = PathEdge(ZERO_FACT, PathFact(input, listOf(a, b, c), Tail.EXACT, t), ExclusionSet.Empty)
        p.entry(root, RuleStatement(f.summary(ZERO_KEEP, source), StatementSummary.EMPTY, emptyList()))
        p.statement(wrap, 1, MicroEdgeBuilder().apply { write(z, listOf(field), false, listOf(x), emptyList()) }.build())
        p.statement(wrap, 2, MicroEdgeBuilder().apply { move(ret, z) }.build())
        p.call(root, 1, resolvedMany(f, wrap, listOf(input to x), output))
        val sink = SinkRule(f.rule("F71 WRAP sink"), 0, listOf(p.pattern(output, listOf(field, a, b, c), mark = t)), emptyList())
        p.exit(root, 2, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, listOf(sink)), false, emptySet()))
        val old = compareVersions(f, root, listOf(root, wrap), listOf(AccessPathBase.Zero, x, ret, input, output, z),
            listOf(a, b, c, field), listOf(t.mark), listOf(1, 2, 3, 4, 5))
        assertEquals(mapOf(HistoricalFinding(sink.rule, p.inst(root, 2)) to HistoricalState.CONFIRMED), old.findings)
        assertEquals(listOf(1, 2, 3, 4, 5), old.runs.map { it.index })
    }

    @Test
    fun `exact source CONJ counterpart confirms only the call supplying both independent marks`(): Unit = DriverFixtures().use { f ->
        val p = f.program
        val root = p.method("F71 CONJ root", straight(7), 6).key()
        val callee = p.method("F71 CONJ callee", straight(2), 1).key()
        val a = AccessPathBase.LocalVar(0); val b = AccessPathBase.LocalVar(1); val output = AccessPathBase.LocalVar(2)
        val clean = AccessPathBase.LocalVar(3); val z = AccessPathBase.LocalVar(4); val y = AccessPathBase.LocalVar(5)
        val bad = AccessPathBase.LocalVar(6); val arg1 = AccessPathBase.Argument(1)
        val t1 = MarkSlot.Concrete(p.mark("CONJ T1")); val t2 = MarkSlot.Concrete(p.mark("CONJ T2"))
        val t = MarkSlot.Concrete(p.mark("CONJ T")); val field = p.field("f"); val g = p.field("g")
        val sources = listOf(a to t1, b to t2).map { (base, mark) ->
            PathEdge(ZERO_FACT, PathFact(base, emptyList(), Tail.EXACT, mark), ExclusionSet.Empty)
        }
        p.entry(root, RuleStatement(f.summary(ZERO_KEEP, *sources.toTypedArray()), StatementSummary.EMPTY, emptyList()))
        val joint = ConjunctiveEdge(listOf(p.pattern(x, mark = t1), p.pattern(arg1, mark = t2)),
            PathFact(ret, emptyList(), Tail.EXACT, t))
        p.call(callee, 0, CallPlan(setOf(x, arg1, ret), listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, f.summary(keepEdge(x), keepEdge(arg1), ZERO_KEEP)),
            CallStage.Actions(BOUND, ADDED, emptyList()),
            CallStage.Edges(ADDED, RETURNED, UNRESOLVED,
                StatementSummary(setOf(x, arg1, ret), listOf(MicroEdge.of(ZERO_KEEP)), listOf(joint), emptyMap())),
            CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, f.summary(keepEdge(ret), ZERO_KEEP)),
        ), emptyList(), BEFORE, AFTER))
        p.call(root, 1, resolvedMany(f, callee, listOf(a to x, b to arg1), output))
        p.statement(root, 2, MicroEdgeBuilder().apply { write(z, listOf(g), false, listOf(output), emptyList()) }.build())
        p.statement(root, 3, MicroEdgeBuilder().apply { write(y, listOf(field), false, listOf(z), emptyList()) }.build())
        p.call(root, 5, resolvedMany(f, callee, listOf(a to x, clean to arg1), bad))
        val goodSink = SinkRule(f.rule("F71 CONJ supplied both"), 0, listOf(p.pattern(y, listOf(field, g), mark = t)), emptyList())
        val badSink = SinkRule(f.rule("F71 CONJ missing mark"), 0, listOf(p.pattern(bad, mark = t)), emptyList())
        p.exit(root, 6, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, listOf(goodSink, badSink)), false, emptySet()))
        val old = compareVersions(f, root, listOf(root, callee), listOf(AccessPathBase.Zero, x, arg1, ret, a, b, output, clean, z, y, bad),
            listOf(field, g), listOf(t1.mark, t2.mark, t.mark))
        assertEquals(mapOf(HistoricalFinding(goodSink.rule, p.inst(root, 6)) to HistoricalState.CONFIRMED), old.findings)
        assertFalse(HistoricalFinding(badSink.rule, p.inst(root, 6)) in old.findings)
        assertEquals(listOf(1, 2, 3), old.runs.map { it.index })
        assertTrue(old.runs[1].observation.rawSummaries.filter { it.method == callee && it.premise.any { member ->
            member.fact.base == ret && member.fact.mark == t } }.all { it.conclusion.demand },
            "Reversed conjunction branches remain DEMAND; they cannot create independent normal records")
    }

    @Test
    fun `exact source cleaner normalization counterpart refutes selected mark and retains the other mark`(): Unit = DriverFixtures().use { f ->
        val p = f.program
        val root = p.method("F71 cleaner normalization root", straight(4), 3).key()
        val callee = p.method("F71 cleaner normalization callee", straight(4), 3).key()
        val a = AccessPathBase.LocalVar(0); val b = AccessPathBase.LocalVar(1); val y = AccessPathBase.LocalVar(2)
        val r = AccessPathBase.LocalVar(3); val s = AccessPathBase.LocalVar(4); val field = p.field("f")
        val t = MarkSlot.Concrete(p.mark("normalization T")); val u = MarkSlot.Concrete(p.mark("normalization U"))
        val sources = listOf(a to t, b to u).map { (base, mark) ->
            PathEdge(ZERO_FACT, PathFact(base, listOf(field), Tail.EXACT, mark), ExclusionSet.Empty)
        }
        p.entry(root, RuleStatement(f.summary(ZERO_KEEP, *sources.toTypedArray()), StatementSummary.EMPTY, emptyList()))
        p.statement(callee, 1, f.summary(keepEdge(x), starEdge(x, listOf(field), y, emptyList())))
        p.call(callee, 2, CallPlan(setOf(y, ret), listOf(
            CallStage.Edges(BEFORE, BOUND, UNRESOLVED, f.summary(keepEdge(y), ZERO_KEEP)),
            CallStage.Actions(BOUND, RETURNED, listOf(CleanStep.Clean(Cleaner(y, null, CleanReach.AT_AND_BELOW, t.mark)))),
            CallStage.Edges(RETURNED, AFTER, BIND_BACK, f.summary(starEdge(y, emptyList(), ret, emptyList()), ZERO_KEEP)),
        ), emptyList(), BEFORE, AFTER))
        p.call(root, 1, resolvedMany(f, callee, listOf(a to x), r))
        p.call(root, 2, resolvedMany(f, callee, listOf(b to x), s))
        val sinkT = SinkRule(f.rule("F71 normalized clean T"), 0, listOf(p.pattern(r, mark = t)), emptyList())
        val sinkU = SinkRule(f.rule("F71 normalized keep U"), 0, listOf(p.pattern(s, mark = u)), emptyList())
        p.exit(root, 3, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, listOf(sinkT, sinkU)), false, emptySet()))
        val old = compareVersions(f, root, listOf(root, callee), listOf(AccessPathBase.Zero, x, ret, a, b, y, r, s),
            listOf(field), listOf(t.mark, u.mark))
        assertEquals(mapOf(HistoricalFinding(sinkU.rule, p.inst(root, 3)) to HistoricalState.CONFIRMED), old.findings)
        assertFalse(old.runs.flatMap { it.observation.witnesses }.any { it.rule == sinkT.rule })
    }

    @Test
    fun `exact source two mark shared getter variant retains both labelled concrete findings`(): Unit = DriverFixtures().use { f ->
        val root = f.program.method("exact-source sharing root", listOf(listOf(1), listOf(2), listOf(3), emptyList()), 3).key()
        val callee = f.program.method("exact-source shared getter", listOf(listOf(1), listOf(2), emptyList()), 2).key()
        val a = AccessPathBase.LocalVar(0); val b = AccessPathBase.LocalVar(1)
        val outT = AccessPathBase.LocalVar(2); val outU = AccessPathBase.LocalVar(3)
        val field = f.program.field("name"); val suffix = f.program.field("value")
        val t = MarkSlot.Concrete(f.program.mark("shared T")); val u = MarkSlot.Concrete(f.program.mark("shared U"))
        val sources = listOf(a to t, b to u).map { (base, mark) ->
            PathEdge(ZERO_FACT, PathFact(base, listOf(field, suffix), Tail.EXACT, mark), ExclusionSet.Empty)
        }
        f.program.entry(root, RuleStatement(f.summary(ZERO_KEEP, *sources.toTypedArray()), StatementSummary.EMPTY, emptyList()))
        f.program.call(root, 1, resolved(f, callee, a, outT)); f.program.call(root, 2, resolved(f, callee, b, outU))
        f.program.statement(callee, 1, f.summary(keepEdge(x), starEdge(x, listOf(field), ret, emptyList())))
        val sinks = listOf(outT to t, outU to u).map { (base, mark) ->
            SinkRule(f.rule("exact-source getter ${mark.mark}"), 0, listOf(f.program.pattern(base, listOf(suffix), mark = mark)), emptyList())
        }
        f.program.exit(root, 3, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, sinks), false, emptySet()))
        val old = compareVersions(f, root, listOf(root, callee), listOf(AccessPathBase.Zero, x, ret, a, b, outT, outU),
            listOf(field, suffix), listOf(t.mark, u.mark))
        assertEquals(sinks.map { HistoricalFinding(it.rule, f.program.inst(root, 3)) }.toSet(), old.findings.keys)
        assertEquals(setOf(HistoricalState.CONFIRMED), old.findings.values.toSet())
        assertEquals(listOf(1, 2, 3), old.runs.map { it.index })
    }

    @Test
    fun `exact source named mark changing program retains the labelled concrete pass finding`(): Unit = CurrentModeFixtures().use { mode ->
        val c = mode.markChangingPass()
        val bases = (c.inputs + c.outputs + listOf(AccessPathBase.Zero, x, ret, AccessPathBase.LocalVar(1))).distinct()
        val old = compareVersions(mode.actual, c.root, listOf(c.root, c.callee), bases, c.fields, listOf(c.t, c.u))
        assertContains(old.findings.keys, HistoricalFinding(c.rules.single(), c.sinks))
        assertEquals(HistoricalState.CONFIRMED, old.findings[HistoricalFinding(c.rules.single(), c.sinks)])
    }

    @Test
    fun `primitive partial field cleaner variant refutes cleaned field and retains other marks in both versions`(): Unit = DriverFixtures().use { f ->
        val root = f.program.method("primitive partial root", listOf(listOf(1), listOf(2), emptyList()), 2).key()
        val callee = f.program.method("primitive partial callee", listOf(listOf(1), listOf(2), listOf(3), emptyList()), 3).key()
        val input = AccessPathBase.LocalVar(0); val output = AccessPathBase.LocalVar(1); val local = AccessPathBase.LocalVar(2)
        val h = f.program.field("h"); val cleaned = f.program.field("f"); val kept = f.program.field("g"); val other = f.program.field("k")
        val t = MarkSlot.Concrete(f.program.mark("partial T")); val u = MarkSlot.Concrete(f.program.mark("partial U"))
        val sourceShapes = listOf(listOf(h, cleaned) to t, listOf(h, kept) to t, listOf(h, other) to u)
        val sources = sourceShapes.map { (path, mark) -> PathEdge(ZERO_FACT, PathFact(input, path, Tail.EXACT, mark), ExclusionSet.Empty) }
        f.program.entry(root, RuleStatement(f.summary(ZERO_KEEP, *sources.toTypedArray()), StatementSummary.EMPTY, emptyList()))
        f.program.call(root, 1, resolved(f, callee, input, output))
        f.program.statement(callee, 1, f.summary(keepEdge(x), starEdge(x, listOf(h), local, emptyList())))
        f.program.call(callee, 2, CallPlan(setOf(local, ret), listOf(
            CallStage.Edges(BEFORE, BOUND, UNRESOLVED, f.summary(keepEdge(local), ZERO_KEEP)),
            CallStage.Actions(BOUND, RETURNED, listOf(CleanStep.Clean(Cleaner(local,
                f.manager.path(listOf(cleaned)), CleanReach.AT_AND_BELOW, t.mark)))),
            CallStage.Edges(RETURNED, AFTER, BIND_BACK, f.summary(starEdge(local, emptyList(), ret, emptyList()), ZERO_KEEP)),
        ), emptyList(), BEFORE, AFTER))
        val sinks = listOf(cleaned to t, kept to t, other to u).map { (field, mark) ->
            SinkRule(f.rule("primitive partial $field"), 0, listOf(f.program.pattern(output, listOf(field), mark = mark)), emptyList())
        }
        f.program.exit(root, 2, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, sinks), false, emptySet()))
        val old = compareVersions(f, root, listOf(root, callee), listOf(AccessPathBase.Zero, x, ret, input, output, local),
            listOf(h, cleaned, kept, other), listOf(t.mark, u.mark))
        assertFalse(HistoricalFinding(sinks[0].rule, f.program.inst(root, 2)) in old.findings)
        assertContains(old.findings.keys, HistoricalFinding(sinks[1].rule, f.program.inst(root, 2)))
        assertContains(old.findings.keys, HistoricalFinding(sinks[2].rule, f.program.inst(root, 2)))
        assertEquals(listOf(1, 2, 3), old.runs.map { it.index })
    }

    @Test
    fun `F71 emission copies the added mark and respects excluded raw demand marks`(): Unit = FiniteProgramFixtures().use { f ->
        val a = f.field("a")
        val b = f.field("b")
        val t = MarkSlot.Concrete(f.mark("T"))
        val u = MarkSlot.Concrete(f.mark("U"))
        val added = f.pattern(x, listOf(a, b), mark = t)
        val abstract = f.pattern(x, tail = Tail.ANY, mark = MarkSlot.STAR)
        assertEquals(added, HistoricalF71.emit(abstract, added))
        assertEquals(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR), emit(abstract, added))
        val excluded = abstract.copy(fact = abstract.fact.copy(mark = MarkSlot.Star(MarkSet.EMPTY + t.mark)))
        assertNull(HistoricalF71.emit(excluded, added))
        assertEquals(f.pattern(x, listOf(a, b), mark = u), HistoricalF71.emit(excluded, added.copy(fact = added.fact.copy(mark = u))))
        assertEquals(HistoricalF71.VERSION, "F71 concrete design / proof snapshot fb789d0ab")
    }

    @Test
    fun `F71 selected cleaner is spatial first with requests only in partial geometry`(): Unit = FiniteProgramFixtures().use { f ->
        val a = f.field("a")
        val b = f.field("b")
        val t = f.mark("T")
        val root = Conclusion(PathFact(x, emptyList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty, false)
        val exact = Conclusion(PathFact(x, listOf(a), Tail.EXACT, MarkSlot.STAR), ExclusionSet.Empty, false)
        val excluded = MarkSlot.Star(MarkSet.EMPTY + t)
        val at = HistoricalF71.clean(Cleaner(x, null, CleanReach.AT_AND_BELOW, t), root)
        assertEquals(listOf(root.copy(fact = root.fact.copy(mark = excluded))), at.facts)
        assertNull(at.request)
        val partial = HistoricalF71.clean(Cleaner(x, f.ap.path(listOf(a)), CleanReach.EXACT, t), root)
        assertEquals(listOf(root.copy(fact = root.fact.copy(mark = excluded))), partial.facts)
        assertEquals(t, partial.request)
        val apart = HistoricalF71.clean(Cleaner(x, f.ap.path(listOf(b)), CleanReach.EXACT, t), exact)
        assertEquals(listOf(exact), apart.facts)
        assertNull(apart.request)
        // Current F75 requests even in INSIDE or DISJOINT geometry at the same base.
        assertEquals(t, cleanRes(Cleaner(x, null, CleanReach.AT_AND_BELOW, t), root).request)
        assertEquals(t, cleanRes(Cleaner(x, f.ap.path(listOf(b)), CleanReach.EXACT, t), exact).request)
    }

    @Test
    fun `F71 cross selection is independent of current normal singleton selection`(): Unit = FiniteProgramFixtures().use { f ->
        val a = f.field("a")
        val t = MarkSlot.Concrete(f.mark("T"))
        val u = MarkSlot.Concrete(f.mark("U"))
        val exact = f.pattern(x, mark = t)
        assertTrue(HistoricalF71.cross(exact, Conclusion(PathFact(ret, emptyList(), Tail.EXACT, u), ExclusionSet.Empty, false)))
        assertFalse(HistoricalF71.cross(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR),
            Conclusion(PathFact(ret, emptyList(), Tail.EXACT, u), ExclusionSet.Empty, false)))
        val flow = f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR)
        val output = Conclusion(PathFact(ret, emptyList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.of(a), false)
        assertTrue(HistoricalF71.cross(flow, output), "Ordinary reversal places field E on its result")
        assertFalse(HistoricalF71.cross(flow.copy(exclusion = ExclusionSet.of(a)), output),
            "Cross still rejects a nonempty field E on the original premise")
        assertFalse(output.demand, "This is a normal singleton in both versions")
        assertFalse(HistoricalF71.crossBackward(exact, Conclusion(PathFact(ret, emptyList(), Tail.EXACT, t), ExclusionSet.Empty, true)))
    }

    @Test
    fun `F71 handoff retains excluded abstract marks while current raw barrier normalizes them`(): Unit = DriverFixtures().use { f ->
        val a = f.program.field("selected field")
        val b = f.program.field("other field")
        val t = f.program.mark("cleaned T")
        val u = MarkSlot.Concrete(f.program.mark("source U"))
        val root = f.program.method("excluded-mark root", listOf(listOf(1), listOf(2), emptyList()), 2).key()
        val callee = f.program.method("excluded-mark callee", listOf(listOf(1), listOf(2), emptyList()), 2).key()
        val source = PathEdge(ZERO_FACT, PathFact(x, listOf(a), Tail.EXACT, u), ExclusionSet.Empty)
        f.program.entry(root, RuleStatement(f.summary(ZERO_KEEP, source), StatementSummary.EMPTY, emptyList()))
        f.program.call(root, 1, CallPlan(setOf(x), listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, f.summary(keepEdge(x), ZERO_KEEP)),
            CallStage.Actions(BOUND, ADDED, emptyList()),
            CallStage.Callees(ADDED, RETURNED, listOf(callee)),
            CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, f.summary(keepEdge(x), ZERO_KEEP)),
        ), emptyList(), BEFORE, AFTER))
        f.program.call(callee, 1, CallPlan(setOf(x), listOf(
            CallStage.Edges(BEFORE, BOUND, UNRESOLVED, f.summary(keepEdge(x), ZERO_KEEP)),
            CallStage.Actions(BOUND, AFTER, listOf(
                CleanStep.Clean(Cleaner(x, null, CleanReach.AT_AND_BELOW, t)),
                CleanStep.Clean(Cleaner(x, f.manager.path(listOf(b)), CleanReach.EXACT, null)))),
        ), emptyList(), BEFORE, AFTER))
        val sink = SinkRule(f.rule("excluded-mark sink"), 0, listOf(f.program.pattern(x, listOf(a), mark = u)), emptyList())
        f.program.exit(root, 2, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, listOf(sink)), false, emptySet()))
        val old = HistoricalConcreteIteration(f.program, listOf(root), listOf(1, 2, 3)).run()
        val current = DirectCurrentIteration(f.program, listOf(root), listOf(1, 2, 3)).run()
        val excluded = MarkSlot.Star(MarkSet.EMPTY + t)
        assertTrue(old.runs.size >= 2 && current.runs.size >= 2)
        assertContains(old.runs[1].inputs.demand, callee to DemandPattern(
            f.program.pattern(x, tail = Tail.ANY, mark = excluded), f.program.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR)))
        assertContains(current.runs[1].inputs.demand, callee to DemandPattern(
            f.program.pattern(x, tail = Tail.ANY, mark = MarkSlot.STAR), f.program.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR)))
        assertFalse(current.runs[1].inputs.demand.any { (_, d) ->
            (d.entry.fact.mark as? MarkSlot.Star)?.excluded?.isEmpty == false })
        assertTrue(old.runs[0].observation.requests.isEmpty(), "Earlier INSIDE selected cleaner does not request")
        assertEquals(setOf(ObservedRequest(callee, f.program.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR),
            RequestKind.Mark(t))), current.runs[0].observation.requests,
            "F75 requests before spatial geometry, including INSIDE")
        assertContains(old.runs[0].observation.selectedDemand, ObservedEdge(callee,
            setOf(f.program.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR)),
            Conclusion(PathFact(x, emptyList(), Tail.ANY, excluded), ExclusionSet.Empty, true)))
    }

    @Test
    fun `generated current iterations retain every finding of the labelled concrete design`(): Unit {
        var historicalFindings = 0
        var historicalConfirmed = 0
        var alternatingCases = 0
        for (seed in 0L until 24L) DriverFixtures().use { f ->
            val random = Random(seed)
            val fields = (0..2).map { f.program.field("generated $it") }
            val depth = 2 + random.nextInt(3)
            val path = List(depth) { fields[random.nextInt(fields.size)] }
            val readSize = random.nextInt(minOf(2, depth) + 1)
            val read = path.take(readSize)
            val suffix = path.drop(readSize)
            val t = MarkSlot.Concrete(f.program.mark("T"))
            val u = MarkSlot.Concrete(f.program.mark("U"))
            val root = f.program.method("concrete root $seed", listOf(listOf(1), listOf(2), emptyList()), 2).key("root-$seed")
            val callee = f.program.method("concrete callee $seed", listOf(listOf(1), emptyList()), 1).key("callee-$seed")
            val source = PathEdge(ZERO_FACT, PathFact(x, path, Tail.EXACT, t), ExclusionSet.Empty)
            f.program.entry(root, RuleStatement(f.summary(ZERO_KEEP, source), StatementSummary.EMPTY, emptyList()))
            val changing = seed % 3 == 0L
            val edge = if (changing) PathEdge(PathFact(x, read, Tail.ANY, t),
                PathFact(ret, suffix, Tail.ANY, u), ExclusionSet.Empty)
                else starEdge(x, read, ret, emptyList())
            f.program.statement(callee, 0, f.summary(edge))
            val cleaner = Cleaner(ret, f.manager.path(if (seed % 2 == 0L) emptyList() else listOf(fields[0])),
                CleanReach.values()[(seed % 3).toInt()], if (seed % 4 == 0L) t.mark else u.mark)
            f.program.call(root, 1, CallPlan(setOf(x, ret), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, f.summary(keepEdge(x), ZERO_KEEP)),
                CallStage.Actions(BOUND, ADDED, emptyList()),
                CallStage.Callees(ADDED, RETURNED, listOf(callee)),
                CallStage.Actions(RETURNED, REWRITTEN, listOf(CleanStep.Clean(cleaner))),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, f.summary(keepEdge(ret), ZERO_KEEP)),
            ), emptyList(), BEFORE, AFTER))
            val sinkMark = if (changing) u else t
            val sink = SinkRule(f.rule("generated sink $seed"), 0,
                listOf(f.program.pattern(ret, suffix, mark = sinkMark)), emptyList())
            f.program.exit(root, 2, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, listOf(sink)), false, emptySet()))
            val limits = listOf(1, 2, 3)
            val old = HistoricalConcreteIteration(f.program, listOf(root), limits).run()
            val reference = DirectCurrentIteration(f.program, listOf(root), limits).run()
            val actualBarriers = ArrayList<ProgramRunInputs>()
            val actualReport = ReportBuilder()
            val driver = f.driver(FixedLimits(limits), guardBarrier = { shared, body ->
                val next = body()
                val config = f.configs.last()
                val result = f.results.last()
                actualReport.add(config, result)
                actualBarriers += actualHandOff(f, listOf(root, callee), HandOff.of(config, result, actualReport, shared))
                next
            })
            val actual = driver.analyze(listOf(root))
            val actualStates = actual.entries.associate { HistoricalFinding(it.key.rule, it.key.statement) to
                if (it.state == ReportState.CONFIRMED) HistoricalState.CONFIRMED else HistoricalState.DEMAND }
            val diagnostic = "seed=$seed version=${HistoricalF71.VERSION} limits=$limits path=$path read=$read " +
                "changing=$changing program=${f.program.describe()} old=${old.runs} direct=${reference.runs} " +
                "configs=${f.configs.map { it.index to it.fieldLimit }} actual=$actualStates"
            assertEquals(RunStatus.COMPLETE, actual.end.status, diagnostic)
            assertEquals(reference.findings, actualStates, "Current direct barriers and real driver differ: $diagnostic")
            assertEquals(reference.runs.size, actualBarriers.size, "Complete barrier count: $diagnostic")
            val universe = BoundedDenotation(listOf(AccessPathBase.Zero, x, ret), fields + f.program.field("fresh field"),
                listOf(t.mark, u.mark, f.program.mark("fresh mark"), TaintMark.ZERO), 2)
            for ((run, barrier) in reference.runs.zip(actualBarriers))
                compareHandOff(run.handOff, barrier, universe, "run=${run.index} $diagnostic")
            for ((key, state) in old.findings) {
                val current = actualStates[key]
                assertNotNull(current, "A labelled concrete-design finding was lost: $diagnostic")
                if (state == HistoricalState.CONFIRMED)
                    assertEquals(HistoricalState.CONFIRMED, current, "A labelled concrete confirmation was weakened: $diagnostic")
            }
            historicalFindings += old.findings.size
            historicalConfirmed += old.findings.values.count { it == HistoricalState.CONFIRMED }
            if (reference.runs.size == 3) alternatingCases++
        }
        assertTrue(historicalFindings > 0, "The differential must compare actual findings")
        assertTrue(historicalConfirmed > 0, "The differential must compare actual concrete confirmations")
        assertTrue(alternatingCases > 0, "The differential must cross actual F→B→F barriers")
    }
}
