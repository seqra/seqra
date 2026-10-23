package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.bidi.ap.MarkGroupKey
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.configuration.jvm.TaintMark as RuleMark
import org.opentaint.dataflow.configuration.jvm.serialized.UserDefinedRuleInfo
import org.opentaint.dataflow.jvm.bidi.fixtures.CallRules
import org.opentaint.dataflow.jvm.bidi.interp.EffectiveBidiRulesProvider
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import kotlin.test.*

class JIRReturnGroupScheduleTest : BidiJIRTestUtils() {
    override val fixtureMarkOwners = mapOf("T" to MarkGroupKey.Rule("joined"), "U" to MarkGroupKey.Rule("joined"))
    private val owner = "sample.bidi.BidiCallSample"
    private fun info() = object : UserDefinedRuleInfo {
        override val relevantTaintMarks = linkedSetOf("T", "U")
    }

    private fun forms(rules: CallRules): Pair<MethodForms, org.opentaint.ir.api.jvm.cfg.JIRInst> {
        val interpreter = interp(EffectiveBidiRulesProvider(rules, manager.marks))
        val caller = method("staticCall", owner)
        val call = caller.instList.instructions.first { it.callExpr?.method?.method?.name == "target" }
        return interpreter.forms(key(caller)) to call
    }

    private fun actions(plan: CallPlan, from: CallPoint, to: CallPoint) =
        plan.stages.filterIsInstance<CallStage.Actions>().single { it.from == from && it.to == to }

    // Core metadata is intentionally internal to a different module. Inspect only this
    // construction contract instead of adding a public execution API for JVM tests.
    private fun schedule(actions: CallStage.Actions): Any? =
        actions.javaClass.getDeclaredField("returnGroupSchedule").apply { isAccessible = true }.get(actions)

    private fun fullSchedule(actions: CallStage.Actions): Any? =
        actions.javaClass.getDeclaredField("returnFullCleanupSchedule").apply { isAccessible = true }.get(actions)

    private fun selected(actions: CallStage.Actions, start: Int): List<Int> {
        val schedule = schedule(actions) ?: return (start until actions.steps.size).toList()
        val execute = schedule.javaClass.getDeclaredMethod("execute", Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType).apply { isAccessible = true }
        return (start until actions.steps.size).filter { execute.invoke(schedule, it, start) as Boolean }
    }

    private fun sourceIds(plan: CallPlan) = plan.stages.filterIsInstance<CallStage.Edges>()
        .single { it.kind == StageKind.SOURCES }.summary.edges.filter { it.isSource }.map { it.actionId }

    @Test fun `returned rewriters gain mode specific metadata and cross-rule raw occurrences remain`() {
        val target = method("target", owner)
        val rules = CallRules().apply {
            sources = List(2) { TaintMethodSource(target, mkTrue(),
                listOf(AssignMark(RuleMark("T"), Result)), info()) }
            cleaners = listOf(TaintCleaner(target, mkTrue(),
                listOf(RemoveMark(RuleMark("T"), Argument(0)), RemoveMark(RuleMark("U"), Argument(0))), null))
        }
        val (forms, at) = forms(rules)
        val expression = checkNotNull(at.callExpr)
        val group = forms.call(Direction.FORWARD, at, expression, RuleView.RUN1_GROUP)
        val full = forms.call(Direction.FORWARD, at, expression, RuleView.FULL)
        val returned = actions(group, CallPoint.RETURNED, CallPoint.REWRITTEN)
        assertEquals(4, returned.steps.size, "Both full members of both original rules remain raw steps")
        assertEquals(listOf(0), selected(returned, 0))
        assertEquals(listOf(2), selected(returned, 2))
        assertNotNull(schedule(returned))
        assertNull(schedule(actions(group, CallPoint.BOUND, CallPoint.ADDED)), "Configured cleaners keep exact execution")
        assertNull(schedule(actions(full, CallPoint.RETURNED, CallPoint.REWRITTEN)))
        assertNotNull(fullSchedule(actions(full, CallPoint.RETURNED, CallPoint.REWRITTEN)))
        assertNull(fullSchedule(returned))
        assertNull(fullSchedule(actions(full, CallPoint.BOUND, CallPoint.ADDED)))
        assertEquals(listOf(0, 1, 2, 3), selected(actions(full, CallPoint.RETURNED, CallPoint.REWRITTEN), 0))
        val backward = forms.call(Direction.BACKWARD, at, expression, RuleView.RUN1_GROUP)
        assertNull(schedule(actions(backward, CallPoint.REWRITTEN, CallPoint.RETURNED)))
        val fullBackward = forms.call(Direction.BACKWARD, at, expression, RuleView.FULL)
        assertNull(fullSchedule(actions(fullBackward, CallPoint.REWRITTEN, CallPoint.RETURNED)))
        assertEquals(2, sourceIds(group).size)
        assertEquals(2, sourceIds(group).distinct().size, "Source occurrence IDs are not compacted")
        assertEquals(sourceIds(full), sourceIds(group))
        val groupAtom = manager.marks.group(manager.marks.groupOf(manager.marks.full("T")))
        assertTrue(returned.steps.all { (it as CleanStep.Clean).cleaner.mark == groupAtom })
        assertSame(group, forms.call(Direction.FORWARD, at, expression, RuleView.RUN1_GROUP))
        assertSame(full, forms.call(Direction.FORWARD, at, expression, RuleView.FULL))
    }

    @Test fun `field lowering keeps every full ordinal temporary and breaks the eligible block`() {
        val field = PositionWithAccess(Argument(0), PositionAccessor.FieldAccessor(owner, "f", "java.lang.Object"))
        val target = method("target", owner)
        val rules = CallRules().apply {
            sources = listOf(TaintMethodSource(target, mkTrue(), listOf(
                AssignMark(RuleMark("T"), Result), AssignMark(RuleMark("T"), field),
                AssignMark(RuleMark("T"), Result)), info()))
        }
        val (forms, at) = forms(rules)
        val expression = checkNotNull(at.callExpr)
        val groupPlan = forms.call(Direction.FORWARD, at, expression, RuleView.RUN1_GROUP)
        val fullPlan = forms.call(Direction.FORWARD, at, expression, RuleView.FULL)
        val group = actions(groupPlan, CallPoint.RETURNED, CallPoint.REWRITTEN)
        val full = actions(fullPlan, CallPoint.RETURNED, CallPoint.REWRITTEN)
        val fullBlocks = assertNotNull(fullSchedule(full))
        val blockAt = fullBlocks.javaClass.getDeclaredMethod("blockContaining", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
        assertNotNull(blockAt.invoke(fullBlocks, 0))
        assertNull(blockAt.invoke(fullBlocks, 2))
        assertNull(blockAt.invoke(fullBlocks, 3))
        assertNotNull(blockAt.invoke(fullBlocks, 4))
        assertEquals(6, group.steps.size)
        assertEquals(full.steps.map { it.javaClass }, group.steps.map { it.javaClass })
        assertEquals(listOf(0, 2, 3, 4), selected(group, 0))
        assertEquals(listOf(1, 2, 3, 4), selected(group, 1))
        assertEquals(listOf(5), selected(group, 5))
        val groupFields = group.steps.filterIsInstance<CleanStep.Field>()
        val fullFields = full.steps.filterIsInstance<CleanStep.Field>()
        assertEquals(2, groupFields.size)
        assertNotEquals(groupFields[0].temporary, groupFields[1].temporary,
            "Different full-mark ordinals must not share a generated field temporary")
        assertEquals(fullFields.map { it.temporary }, groupFields.map { it.temporary })
        for ((g, f) in groupFields.zip(fullFields)) {
            assertEquals(f.read.edges.map { it.edge }, g.read.edges.map { it.edge })
            assertEquals(f.write.edges.map { it.edge }, g.write.edges.map { it.edge })
        }
        assertEquals(sourceIds(fullPlan), sourceIds(groupPlan))
        assertTrue(groupPlan.stages.filterIsInstance<CallStage.Edges>().flatMap { it.summary.edges }
            .filter { it.isSource }.all { it.forward.to.mark is MarkSlot.Concrete })
    }
}
