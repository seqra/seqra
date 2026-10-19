package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.configuration.jvm.TaintMark as RuleMark
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.bidi.fixtures.NoTaintRules
import org.opentaint.dataflow.jvm.bidi.interp.EffectiveBidiRulesProvider
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import kotlin.test.*

class JIRRuleViewCacheTest : BidiJIRTestUtils() {
    override val fixtureMarkOwners = mapOf("T" to MarkGroupKey.Rule("joined"), "U" to MarkGroupKey.Rule("joined"))
    @Test fun `entry exit call and static source caches isolate group from full forms`(): Unit {
        val fullT = manager.marks.mark("T")
        val fullU = manager.marks.mark("U")
        val group = manager.marks.group(manager.marks.groupOf(fullT))
        val predicate: Condition = CommonCondition.Or(listOf(
            CommonCondition.Atom<JirCondition>(ContainsMark(Argument(0), RuleMark("T"))),
            CommonCondition.Atom<JirCondition>(ContainsMark(Argument(0), RuleMark("U")))))
        val delegate = object : TaintRulesProvider by NoTaintRules {
            override fun entryPointRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                listOf(TaintEntryPointSource(method, mkTrue(), listOf(AssignMark(RuleMark("T"), Argument(0)),
                    AssignMark(RuleMark("U"), Argument(0))), null))
            override fun exitSourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                listOf(TaintMethodExitSource(method, predicate, listOf(AssignMark(RuleMark("T"), Result)), null))
            override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                listOf(TaintMethodSource(method, predicate, listOf(AssignMark(RuleMark("T"), Result)), null))
            override fun sourceRulesForStaticField(field: JIRField, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                listOf(TaintStaticFieldSource(field, mkTrue(), listOf(AssignMark(RuleMark("T"), Result)), null))
        }
        val interpreter = interp(EffectiveBidiRulesProvider(delegate, manager.marks))
        val method = method("copy")
        val forms = interpreter.forms(key(method))
        fun sourceMarks(summary: StatementSummary) = summary.edges.filter { it.isSource }.map {
            (it.forward.to.mark as MarkSlot.Concrete).mark
        }
        val groupEntry = forms.entryRules(Direction.FORWARD, RuleView.RUN1_GROUP)
        val fullEntry = forms.entryRules(Direction.FORWARD, RuleView.FULL)
        assertEquals(listOf(group, group), sourceMarks(groupEntry.summary))
        assertEquals(listOf(fullT, fullU), sourceMarks(fullEntry.summary))
        assertSame(fullEntry, forms.entryRules(Direction.FORWARD))
        assertSame(groupEntry, forms.entryRules(Direction.FORWARD, RuleView.RUN1_GROUP))
        assertEquals(groupEntry.summary.edges.filter { it.isSource }.map { it.actionId },
            fullEntry.summary.edges.filter { it.isSource }.map { it.actionId })

        val normal = normalExit(method)
        val groupExitRules = forms.exitRules(Direction.FORWARD, normal, RuleView.RUN1_GROUP)
        val fullExitRules = forms.exitRules(Direction.FORWARD, normal, RuleView.FULL)
        // Entry cleanup metadata follows its cache view. The actor must apply
        // the selected-mark policy, rather than deleting a raw GROUP atom.
        assertEquals(setOf(group), groupExitRules.entryMarks)
        assertEquals(setOf(fullT, fullU), fullExitRules.entryMarks)
        val groupExit = groupExitRules.rules.summary.sources.single()
        val fullExit = fullExitRules.rules.summary.sources.single()
        val backwardExit = forms.exitRules(Direction.BACKWARD, normal, RuleView.FULL).rules.summary.sources.single()
        assertEquals(1, groupExit.predicate.leaves.size)
        assertEquals(2, fullExit.predicate.leaves.size)
        assertEquals(fullExit.id, groupExit.id)
        assertEquals(fullExit.id, backwardExit.id)
        assertEquals(listOf(fullT), backwardExit.actions.map { (it.target.mark as MarkSlot.Concrete).mark })
        assertTrue(backwardExit.backward)

        val readMethod = method("staticRead")
        val read = readMethod.instList.instructions.filterIsInstance<JIRAssignInst>().first { it.rhv is JIRFieldRef }
        val readForms = interpreter.forms(key(readMethod))
        assertEquals(listOf(group), sourceMarks(readForms.statement(Direction.FORWARD, read, RuleView.RUN1_GROUP)))
        assertEquals(listOf(fullT), sourceMarks(readForms.statement(Direction.FORWARD, read, RuleView.FULL)))

        val caller = method("staticCall", "sample.bidi.BidiCallSample")
        val call = caller.instList.instructions.first { it.callExpr?.method?.method?.name == "target" }
        val expression = checkNotNull(call.callExpr)
        val callForms = interpreter.forms(key(caller))
        fun source(plan: CallPlan) = plan.stages.filterIsInstance<CallStage.Edges>()
            .single { it.kind == StageKind.SOURCES }.summary.sources.single()
        val groupCall = source(callForms.call(Direction.FORWARD, call, expression, RuleView.RUN1_GROUP))
        val fullCall = source(callForms.call(Direction.FORWARD, call, expression, RuleView.FULL))
        assertEquals(1, groupCall.predicate.leaves.size)
        assertEquals(2, fullCall.predicate.leaves.size)
        assertEquals(fullCall.id, groupCall.id)
    }
}
