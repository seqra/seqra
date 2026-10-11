package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.*
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

/** Executable version-domain controls: these intentionally reject blending newer form semantics into F71. */
class HistoricalVersionDomainTest {
    private val x = AccessPathBase.Argument(0)
    private val ret = AccessPathBase.Return
    @Test
    fun `ordinary reversal places STAR field exclusion on its result not its historical Cross premise`(): Unit = FiniteProgramFixtures().use { f ->
        val a = f.field("excluded a")
        val premise = f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR)
        val conclusion = Conclusion(PathFact(ret, emptyList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.of(a), false)
        val view = assertNotNull(HistoricalF71.reverseRecord(premise, conclusion))
        assertEquals(ExclusionSet.Empty, view.premise.exclusion)
        assertEquals(ExclusionSet.of(a), view.result.exclusion)
        assertTrue(HistoricalF71.cross(premise, conclusion), "HandoffDefs.Cross tests the ordinary reversed premise")
    }

    @Test
    fun `historical above emission keeps the demanded STAR field exclusion while copying added concrete mark`(): Unit = FiniteProgramFixtures().use { f ->
        val a = f.field("demanded a")
        val b = f.field("excluded b")
        val t = MarkSlot.Concrete(f.mark("T"))
        val demand = f.pattern(x, listOf(a), Tail.STAR, MarkSlot.STAR, ExclusionSet.of(b))
        val added = f.pattern(x, tail = Tail.ANY, mark = t)
        assertEquals(demand.copy(fact = demand.fact.copy(mark = t)), HistoricalF71.emit(demand, added))
    }

    private fun forbiddenStage(kind: StageKind, guard: Guard? = null): Unit = FiniteProgramFixtures().use { f ->
        val key = f.method("forbidden $kind", listOf(emptyList()), 0).key()
        f.call(key, 0, CallPlan(setOf(x), listOf(CallStage.Edges(CallPoint.BEFORE, CallPoint.AFTER,
            kind, StatementSummary(setOf(x), listOf(MicroEdge.of(keepEdge(x))), emptyList(), emptyMap()), guard)),
            emptyList(), CallPoint.BEFORE, CallPoint.AFTER))
        assertFailsWith<IllegalStateException> { HistoricalF71.validate(f, ProgramRunInputs(listOf(key), emptyList(), emptyList(), emptyList())) }
    }

    @Test
    fun `historical declared domain rejects alias stages`(): Unit = forbiddenStage(StageKind.ALIASES)

    @Test
    fun `historical declared domain rejects end fact stages`(): Unit = forbiddenStage(StageKind.END_FACTS)

    @Test
    fun `historical declared domain rejects guarded stages`(): Unit = forbiddenStage(StageKind.UNRESOLVED, Guard.MemoryEffect)

    @Test
    fun `historical declared domain rejects exit GEN conjunctions`(): Unit = FiniteProgramFixtures().use { f ->
        val key = f.method("forbidden exit conjunction", listOf(emptyList()), 0).key()
        val t = MarkSlot.Concrete(f.mark("T"))
        val u = MarkSlot.Concrete(f.mark("U"))
        val edge = ConjunctiveEdge(listOf(f.pattern(x, mark = t), f.pattern(AccessPathBase.Argument(1), mark = u)),
            PathFact(ret, emptyList(), Tail.EXACT, t))
        val end = StatementSummary(emptySet(), emptyList(), listOf(edge), emptyMap())
        f.exit(key, 0, ExitRules(RuleStatement(StatementSummary.EMPTY, end, emptyList()), false, emptySet()))
        assertFailsWith<IllegalStateException> { HistoricalF71.validate(f, ProgramRunInputs(listOf(key), emptyList(), emptyList(), emptyList())) }
    }
}
