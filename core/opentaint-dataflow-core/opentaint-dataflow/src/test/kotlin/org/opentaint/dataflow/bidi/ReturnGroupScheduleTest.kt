package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.fixtures.TestMark
import org.opentaint.dataflow.bidi.fixtures.testDeclarations
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

class ReturnGroupScheduleTest {
    private val f = StoreFixtures(markDeclarations = testDeclarations(
        TestMark("T", MarkGroupKey.Rule("one")),
        TestMark("U", MarkGroupKey.Rule("one")),
        TestMark("V", MarkGroupKey.Rule("two")),
    ))
    private val marks = f.manager.marks
    private val t = marks.full("T")
    private val g = marks.group(marks.groupOf(t))
    private val h = marks.group(marks.groupOf(marks.full("V")))
    private val arg = AccessPathBase.Argument(0)

    private fun clean(base: AccessPathBase = arg, mark: TaintMark? = g,
                      path: PathNode? = null, reach: CleanReach = CleanReach.EXACT) =
        CleanStep.Clean(Cleaner(base, path, reach, mark))

    private fun stage(steps: List<CleanStep>) = CallStage.Actions.run1GroupReturnRewriter(steps, marks)
    private fun selected(actions: CallStage.Actions, start: Int): List<Int> =
        (start until actions.steps.size).filter { actions.returnGroupSchedule?.execute(it, start) != false }

    // Keys are supplied independently of production classification or prior-index compilation.
    private data class ReferenceKey(val base: String, val group: String)
    private data class Symbol(val step: CleanStep, val key: ReferenceKey?)
    private fun reference(raw: List<Symbol>, start: Int): List<Int> {
        val seen = HashSet<ReferenceKey>()
        return buildList {
            for (index in start until raw.size) {
                val key = raw[index].key
                if (key == null) { seen.clear(); add(index) }
                else if (seen.add(key)) add(index)
            }
        }
    }

    @Test fun `every finite suffix matches an independent first-key scan`() {
        val alphabet = listOf(
            Symbol(clean(), ReferenceKey("arg0", "one")),
            Symbol(clean(mark = h), ReferenceKey("arg0", "two")),
            Symbol(clean(AccessPathBase.This), ReferenceKey("this", "one")),
            Symbol(CleanStep.Kill(StatementSummary.EMPTY), null),
        )
        fun checkWord(word: List<Symbol>) {
            val actions = stage(word.map { it.step })
            for (start in 0..word.size)
                assertEquals(reference(word, start), selected(actions, start), "word=$word start=$start")
        }
        fun words(prefix: List<Symbol>, remaining: Int) {
            checkWord(prefix)
            if (remaining > 0) for (symbol in alphabet) words(prefix + symbol, remaining - 1)
        }
        words(emptyList(), 5)
    }

    @Test fun `suffix starts keep the raw first step and reset after a field`() {
        val temporary = AccessPathBase.CleanerTemporary()
        val field = CleanStep.Field(temporary, StatementSummary.EMPTY,
            Cleaner(temporary, null, CleanReach.EXACT, g), StatementSummary.EMPTY)
        val raw = listOf(clean(), clean(mark = h), clean(), field, clean(), clean())
        val actions = stage(raw)
        assertEquals(listOf(0, 1, 3, 4), selected(actions, 0))
        assertEquals(listOf(2, 3, 4), selected(actions, 2))
        assertEquals(listOf(5), selected(actions, 5))
        assertNotNull(actions.returnGroupSchedule)
        for (index in raw.indices) {
            assertSame(raw[index], actions.steps[index])
            assertTrue(actions.returnGroupSchedule!!.execute(index, index))
        }
    }

    @Test fun `base and genuine group define a key while plain path and reach stay raw`() {
        val element = f.manager.path(listOf(f.manager.accessors.index(ElementAccessor)))
        val raw = listOf(clean(), clean(path = element, reach = CleanReach.BELOW),
            clean(mark = h), clean(AccessPathBase.This), clean(reach = CleanReach.AT_AND_BELOW),
            clean(AccessPathBase.This, reach = CleanReach.BELOW))
        val actions = stage(raw)
        assertEquals(listOf(0, 2, 3), selected(actions, 0))
        assertSame(raw[1], actions.steps[1])
        assertSame(element, (actions.steps[1] as CleanStep.Clean).cleaner.path)
        assertEquals(CleanReach.BELOW, (actions.steps[1] as CleanStep.Clean).cleaner.reach)
        assertEquals(listOf(1, 2, 3), selected(actions, 1))
    }

    @Test fun `full zero static temporary all-mark field and kill operators are barriers`() {
        val temporary = AccessPathBase.CleanerTemporary()
        val barriers = listOf<CleanStep>(
            clean(mark = t), clean(mark = TaintMark.ZERO), clean(base = STATIC), clean(base = temporary),
            clean(mark = null), CleanStep.Kill(StatementSummary.EMPTY),
            CleanStep.Field(temporary, StatementSummary.EMPTY,
                Cleaner(temporary, null, CleanReach.EXACT, g), StatementSummary.EMPTY),
        )
        for (barrier in barriers) {
            val actions = stage(listOf(clean(), clean(), barrier, clean(), clean()))
            assertEquals(listOf(0, 2, 3), selected(actions, 0), barrier.javaClass.name)
            assertEquals(listOf(2, 3), selected(actions, 2), barrier.javaClass.name)
            assertEquals(listOf(4), selected(actions, 4), barrier.javaClass.name)
        }
        assertFails { Cleaner(AccessPathBase.Zero, null, CleanReach.EXACT, g) }
    }

    @Test fun `ordinary and reversed constructors keep exact execution and equality stays raw`() {
        val raw = arrayListOf<CleanStep>(clean(), clean())
        val exact = CallStage.Actions(CallPoint.RETURNED, CallPoint.REWRITTEN, raw)
        val grouped = stage(raw)
        raw.clear()
        assertEquals(2, grouped.steps.size)
        assertEquals(2, exact.steps.size)
        assertEquals(exact, grouped)
        assertEquals(exact.hashCode(), grouped.hashCode())
        assertNull(exact.returnGroupSchedule)
        assertEquals(listOf(0, 1), selected(exact, 0))
        val reversed = grouped.reversed()
        assertNull(reversed.returnGroupSchedule)
        assertEquals(CallPoint.REWRITTEN, reversed.from)
        assertEquals(CallPoint.RETURNED, reversed.to)
        assertEquals(listOf(0, 1), selected(reversed, 0))
        assertSame(grouped.steps[1], reversed.steps[0])
        assertNull(stage(emptyList()).returnGroupSchedule)
        assertNull(stage(listOf(clean(mark = t), clean(mark = t))).returnGroupSchedule)
        assertNull(stage(listOf(clean(), clean(mark = h))).returnGroupSchedule)
        val bound = CallStage.Actions(CallPoint.BOUND, CallPoint.ADDED, grouped.steps)
        assertNull(bound.returnGroupSchedule)
    }

    @Test fun `schedule owns stable metadata and storage counts its raw array once`() {
        val raw = listOf(clean(), clean(mark = h), clean(), clean())
        val first = stage(raw)
        val rebuilt = stage(raw.map { step ->
            val c = (step as CleanStep.Clean).cleaner
            CleanStep.Clean(Cleaner(c.base, c.path, c.reach, c.mark))
        })
        for (start in 0..raw.size) assertEquals(selected(first, start), selected(rebuilt, start))
        fun counts(actions: CallStage.Actions) = FormStorageCounts().apply {
            measure(CallPlan(emptySet(), listOf(actions, actions), emptyList(), CallPoint.BEFORE, CallPoint.AFTER))
        }
        val plain = counts(CallStage.Actions(first.from, first.to, first.steps))
        val packed = counts(first)
        assertEquals(plain.primitiveIndexCells + raw.size, packed.primitiveIndexCells)
        assertEquals(plain.ownedContainerEntries, packed.ownedContainerEntries)
        assertEquals(raw.size, first.returnGroupSchedule!!.size)
    }
}
