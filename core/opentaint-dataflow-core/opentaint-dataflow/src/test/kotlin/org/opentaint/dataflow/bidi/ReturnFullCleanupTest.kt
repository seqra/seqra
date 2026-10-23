package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

class ReturnFullCleanupTest {
    private val f = StoreFixtures()
    private val m = f.manager
    private val x = AccessPathBase.Argument(0)
    private val t = m.marks.full("T")
    private val u = m.marks.full("U")
    private val v = m.marks.full("V")
    private val mode = ApMode(true, Direction.FORWARD, 1)
    private fun cleaner(mark: TaintMark? = t, path: PathNode? = null,
                        reach: CleanReach = CleanReach.EXACT, base: AccessPathBase = x) = Cleaner(base, path, reach, mark)
    private fun steps(first: TaintMark, second: TaintMark? = null, third: TaintMark? = null) =
        listOfNotNull(first, second, third).map { CleanStep.Clean(cleaner(it)) }

    @Test fun `metadata preserves raw steps and every middle suffix`() {
        val raw = steps(t, u, t)
        val actions = CallStage.Actions.run1FullReturnRewriter(raw, m.marks)
        val schedule = assertNotNull(actions.returnFullCleanupSchedule)
        val block = assertNotNull(schedule.blockContaining(0))
        assertEquals(0, block.first)
        assertEquals(3, block.end)
        assertEquals(MarkSet(intArrayOf(t.id, u.id)), block.marksFrom(0))
        assertEquals(MarkSet(intArrayOf(t.id, u.id)), block.marksFrom(1))
        assertEquals(MarkSet.of(t), block.marksFrom(2))
        raw.indices.forEach { assertSame(raw[it], actions.steps[it]); assertSame(block, schedule.blockContaining(it)) }
        assertNull(actions.reversed().returnFullCleanupSchedule)
        assertNull(CallStage.Actions(CallPoint.BOUND, CallPoint.ADDED, raw).returnFullCleanupSchedule)
    }

    @Test fun `all effects and geometry changes terminate a full block`() {
        val p = m.path(intArrayOf(f.field("f")))
        val temp = AccessPathBase.CleanerTemporary()
        val barriers = listOf<CleanStep>(CleanStep.Kill(StatementSummary.EMPTY),
            CleanStep.Field(temp, StatementSummary.EMPTY, cleaner(base = temp), StatementSummary.EMPTY),
            CleanStep.Clean(cleaner(m.marks.group(m.marks.groupOf(t)))),
            CleanStep.Clean(cleaner(null)), CleanStep.Clean(cleaner(path = p)),
            CleanStep.Clean(cleaner(reach = CleanReach.BELOW)),
            CleanStep.Clean(cleaner(base = STATIC)),
            CleanStep.Clean(cleaner(base = AccessPathBase.CleanerTemporary())))
        for (barrier in barriers) {
            val a = CallStage.Actions.run1FullReturnRewriter(steps(t, u) + barrier + steps(u, t), m.marks)
            val s = assertNotNull(a.returnFullCleanupSchedule)
            assertEquals(2, assertNotNull(s.blockContaining(0)).end)
            assertNull(s.blockContaining(2))
            assertEquals(3, assertNotNull(s.blockContaining(3)).first)
        }
        assertNull(CallStage.Actions.run1FullReturnRewriter(steps(t), m.marks).returnFullCleanupSchedule)
    }

    @Test fun `metadata owns raw steps rebuilds deterministically and retains linear storage`() {
        val raw = (0 until 100).map { if (it % 2 == 0) t else u }.map { CleanStep.Clean(cleaner(it)) }.toMutableList<CleanStep>()
        val first = CallStage.Actions.run1FullReturnRewriter(raw, m.marks)
        val rebuilt = CallStage.Actions.run1FullReturnRewriter(raw.map { step ->
            val c = (step as CleanStep.Clean).cleaner
            CleanStep.Clean(Cleaner(c.base, c.path, c.reach, c.mark))
        }, m.marks)
        raw.clear()
        assertEquals(100, first.steps.size)
        val block = assertNotNull(first.returnFullCleanupSchedule).blockContaining(0)!!
        for (start in first.steps.indices) {
            assertSame(block, first.returnFullCleanupSchedule!!.blockContaining(start))
            assertEquals(block.marksFrom(start), rebuilt.returnFullCleanupSchedule!!.blockContaining(start)!!.marksFrom(start))
        }
        fun counts(actions: CallStage.Actions) = FormStorageCounts().apply {
            measure(CallPlan(emptySet(), listOf(actions, actions), emptyList(), CallPoint.RETURNED, CallPoint.AFTER))
        }
        val plain = counts(CallStage.Actions(first.from, first.to, first.steps))
        val selected = counts(first)
        // One raw directory plus one maximal block's two-atom mask; no retained suffix masks.
        assertEquals(plain.primitiveIndexCells + 102, selected.primitiveIndexCells)
        assertEquals(plain.ownedContainerEntries + 1, selected.ownedContainerEntries)
    }

    private fun decode(values: List<Facts>): Set<Pair<Pattern, Layer>> = values.flatMap { value ->
        f.ops.leaves(value).map { it to value.layer }.toList()
    }.toSet()

    @Test fun `packed spatial kernel equals scalar full cleanup for all tails and reaches`() {
        val field = f.field("f")
        val other = f.field("other")
        val inputPaths = listOf(emptyList(), listOf(field), listOf(other), listOf(field, other))
        val cleanerPaths = listOf(emptyList(), listOf(field), listOf(field, other))
        for (tail in listOf(Tail.EXACT, Tail.ANY, Tail.ANY_TAINT))
            for (reach in CleanReach.entries) for (at in cleanerPaths) {
                val inputs = inputPaths.flatMap { path -> listOf(t, u).map { mark ->
                    f.fact(x, path, tail, MarkSlot.Concrete(mark), layer = Layer.DEMAND)
                } }
                val group = ConclusionGroup(m, StoreInterners(m.softRefs, m.cancellation))
                inputs.forEach(group::add)
                val unselected = f.fact(x, listOf(field), Tail.EXACT, MarkSlot.Concrete(v), layer = Layer.DEMAND) as TaintTree
                val first = inputs.first() as TaintTree
                val mixed = m.taintTree(x, Layer.DEMAND, m.taintAlg.mergeAdd(first.root, unselected.root, m.cancellation),
                    anyTail = if (tail.isAny) tail else Tail.ANY_TAINT)
                for (input in group.all().toList() + listOf(unselected, mixed)) {
                    var old = listOf(input)
                    val geometry = cleaner(t, m.path(at), reach)
                    for (mark in listOf(t, u, t)) old = old.flatMap { value ->
                        CollectingOut().also { f.ops.clean(value, m.zero, cleaner(mark, m.path(at), reach), mode, it) }.results
                    }
                    val out = CollectingOut()
                    f.ops.cleanSelectedFullMask(input, m.zero, geometry, MarkSet(intArrayOf(t.id, u.id)), mode,
                        request = { error("concrete values must not request") }, out = out)
                    assertEquals(decode(old), decode(out.results), "tail=$tail reach=$reach at=$at")
                }
            }
    }

    @Test fun `flow shares paths requests all mask atoms and preserves previous exclusions`() {
        val input = f.fact(x, listOf(f.field("f")), Tail.STAR, MarkSlot.Star(MarkSet.of(t))) as FlowTree
        val out = CollectingOut()
        val requests = mutableListOf<MarkSet>()
        val mask = MarkSet(intArrayOf(t.id, u.id))
        f.ops.cleanSelectedFullMask(input, m.zero, cleaner(reach = CleanReach.EXACT), mask, mode,
            request = requests::add, out = out)
        assertEquals(listOf(mask), requests)
        val result = assertIs<FlowTree>(out.results.single())
        assertSame(input.root, result.root)
        assertEquals(input.tail, result.tail)
        assertEquals(setOf(t, u), result.markExclusion.ids.map(::TaintMark).toSet())
        assertTrue(out.markRequests.isEmpty())
    }
}
