package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.fixtures.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import kotlin.test.*

/** F69 refined must flags plus the earlier concrete hand-off, checked against named Lean findings. */
class HistoricalMustProgramTest {
    private val arg = AccessPathBase.Argument(0)
    private val ret = AccessPathBase.Return
    private val local = AccessPathBase.LocalVar(0)

    @Test
    fun `older must emission copies concrete mark and retains must flag and admitted exclusion`(): Unit = FiniteProgramFixtures().use { f ->
        val a = f.field("admitted a"); val b = f.field("excluded b"); val t = MarkSlot.Concrete(f.mark("T"))
        val added = f.pattern(arg, tail = Tail.ANY_TAINT, mark = t, exclusion = ExclusionSet.of(b))
        val broad = f.pattern(arg, tail = Tail.ANY, mark = MarkSlot.STAR)
        assertEquals(added, HistoricalF71.emit(broad, added), "AnyTaintExDefs.emitTX carries the normal must flag")
        assertEquals(f.pattern(arg, listOf(a), Tail.ANY_TAINT, t),
            HistoricalF71.emit(f.pattern(arg, listOf(a), Tail.ANY, MarkSlot.STAR), added))
        assertNull(HistoricalF71.emit(f.pattern(arg, listOf(b), Tail.ANY, MarkSlot.STAR), added))
        assertEquals(f.pattern(arg, listOf(a), Tail.EXACT, t),
            HistoricalF71.emit(f.pattern(arg, listOf(a), Tail.EXACT, MarkSlot.STAR), added))
    }

    @Test
    fun `older must cleaner one step partial rows retain normal E and optional exact position`(): Unit = FiniteProgramFixtures().use { f ->
        val a = f.field("cleaned a"); val b = f.field("excluded b"); val t = f.mark("T")
        val original = Conclusion(PathFact(arg, emptyList(), Tail.ANY_TAINT, MarkSlot.Concrete(t)), ExclusionSet.of(b), false)
        val excluded = original.copy(exclusion = ExclusionSet.of(intArrayOf(a, b)))
        fun check(expected: List<Conclusion>, actual: CleanOut) {
            assertEquals(expected, actual.facts)
            assertNull(actual.request)
        }
        check(listOf(excluded),
            HistoricalF71.clean(Cleaner(arg, f.ap.path(listOf(a)), CleanReach.AT_AND_BELOW, t), original))
        check(listOf(excluded, Conclusion(PathFact(arg, listOf(a), Tail.EXACT, MarkSlot.Concrete(t)), ExclusionSet.Empty, false)),
            HistoricalF71.clean(Cleaner(arg, f.ap.path(listOf(a)), CleanReach.BELOW, t), original))
        check(listOf(original),
            HistoricalF71.clean(Cleaner(arg, f.ap.path(listOf(b)), CleanReach.AT_AND_BELOW, t), original))
    }

    @Test
    fun `older refined must restriction meets exact and STAR exclusions at and above the demanded path`(): Unit = FiniteProgramFixtures().use { f ->
        val a = f.field("old excluded a"); val b = f.field("new excluded b"); val c = f.field("admitted c")
        val t = MarkSlot.Concrete(f.mark("restriction T"))
        val premise = f.pattern(arg, mark = t)
        val entry = f.pattern(arg, tail = Tail.ANY, mark = t)
        val input = Conclusion(PathFact(ret, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.of(a), false)
        val exact = f.pattern(ret, mark = t)
        val sameStar = f.pattern(ret, tail = Tail.STAR, mark = MarkSlot.STAR, exclusion = ExclusionSet.of(b))
        val aboveStar = f.pattern(ret, listOf(c), Tail.STAR, MarkSlot.STAR, ExclusionSet.of(b))
        val blockedStar = f.pattern(ret, listOf(a), Tail.STAR, MarkSlot.STAR, ExclusionSet.of(b))
        val expected = listOf(
            Conclusion(PathFact(ret, emptyList(), Tail.EXACT, t), ExclusionSet.Empty, false),
            Conclusion(PathFact(ret, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.of(intArrayOf(a, b)), false),
            Conclusion(PathFact(ret, listOf(c), Tail.ANY_TAINT, t), ExclusionSet.of(b), false),
            null,
        )
        val actual = listOf(exact, sameStar, aboveStar, blockedStar).map { exit ->
            HistoricalF71.restrict(premise, input, DemandPattern(entry, exit))
        }
        assertEquals(expected, actual, "HandoffXRestrict.restrictConcIX meetExX + chainExX; the old layer stays normal")
    }

    @Test
    fun `earlier G and C concrete iterations confirm their same mark supported must premises`(): Unit {
        for (sinkInCallee in listOf(false, true)) DriverFixtures().use { f ->
            val p = f.program
            val root = p.method("historical ${if (sinkInCallee) "C" else "G"} root", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val callee = p.method("historical ${if (sinkInCallee) "C" else "G"} callee", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val field = p.field("f"); val t = MarkSlot.Concrete(p.mark("T"))
            val source = PathEdge(ZERO_FACT, PathFact(local, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.Empty)
            p.entry(root, RuleStatement(f.summary(ZERO_KEEP, source), StatementSummary.EMPTY, emptyList()))
            p.call(root, 1, CallPlan(setOf(local, ret), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, f.summary(ZERO_KEEP, starEdge(local, emptyList(), arg, emptyList()))),
                CallStage.Actions(BOUND, ADDED, emptyList()), CallStage.Callees(ADDED, RETURNED, listOf(callee)),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, f.summary(ZERO_KEEP, keepEdge(ret))),
            ), emptyList(), BEFORE, AFTER))
            if (!sinkInCallee) p.statement(callee, 1, MicroEdgeBuilder().apply { read(ret, arg, listOf(field)) }.build())
            val rule = f.rule("historical ${if (sinkInCallee) "C" else "G"} sinkAny")
            val sinkKey = if (sinkInCallee) callee else root
            val literal = if (sinkInCallee) p.pattern(arg, listOf(field), Tail.ANY, t) else p.pattern(ret, tail = Tail.ANY, mark = t)
            p.exit(sinkKey, 2, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY,
                listOf(SinkRule(rule, 0, listOf(literal), emptyList()))), false, emptySet()))
            val old = HistoricalConcreteIteration(p, listOf(root), listOf(1, 1, 2), allowMustSources = true).run()
            val reference = if (sinkInCallee) "AnyTaintExCases2.C.run3_confirmed_handoff:1729" else
                "AnyTaintExCases2.G.run3_confirmed_handoff:1618"
            assertEquals(mapOf(HistoricalFinding(rule, p.inst(sinkKey, 2)) to HistoricalState.CONFIRMED), old.findings, reference)
            assertEquals(listOf(1, 2, 3), old.runs.map { it.index }, reference)
            assertTrue(old.runs[2].observation.initials.any { it.method == callee && it.initial.fact.tail == Tail.ANY_TAINT &&
                it.initial.fact.mark == t }, "SupLinkX is supplied by same-site normal must/E added fact")
            for (run in old.runs) assertTrue(run.handOff.demand.all { (_, demand) ->
                demand.entry.fact.tail != Tail.ANY_TAINT && demand.exit?.fact?.tail != Tail.ANY_TAINT },
                "Older hand-off forgets forward must flags on both entry and exit")
            val report = f.driver(FixedLimits(listOf(1, 1, 2))).analyze(listOf(root))
            assertEquals(mapOf(rule to ReportState.DEMAND), report.entries.associate { it.key.rule to it.state }, reference)
        }
    }
}
