package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.ConjunctionStore
import org.opentaint.dataflow.bidi.store.RulePredicateStore
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.configuration.jvm.PositionAccessor
import org.opentaint.dataflow.configuration.jvm.PositionWithAccess
import org.opentaint.dataflow.configuration.jvm.Result
import org.opentaint.dataflow.configuration.jvm.TaintMark as RuleMark
import org.opentaint.dataflow.jvm.BasicTestUtils
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRRuleTestFixtures
import org.opentaint.dataflow.jvm.bidi.fixtures.NoTaintRules
import org.opentaint.dataflow.jvm.bidi.interp.EffectiveBidiRulesProvider
import org.opentaint.dataflow.taint.PositionAccess
import org.opentaint.dataflow.taint.RuleConditionRewriter.ExprOrConstant
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr.ContainsMarkLiteral
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr.ContainsMarkOnAnyAccessorLiteral
import kotlin.test.*

/** This uses the selected provider compiler, not a hand-built GROUP oracle. */
class GroupRuleViewsTest : BasicTestUtils() {
    private fun fixture(): JIRRuleTestFixtures = JIRRuleTestFixtures(cp, mapOf(
        "T" to MarkGroupKey.Rule("joined"), "U" to MarkGroupKey.Rule("joined"),
        "V" to MarkGroupKey.Rule("imported"),
    ))

    private fun literal(base: AccessPathBase, mark: String = "T", negated: Boolean = false,
                        path: PositionAccess = PositionAccess.Simple(base)) =
        ContainsMarkLiteral(path, TaintMarkAccessor(mark), negated)
    private fun and(vararg terms: TaintMarkAwareConditionExpr) = TaintMarkAwareConditionExpr.And(arrayOf(*terms))
    private fun or(vararg terms: TaintMarkAwareConditionExpr) = TaintMarkAwareConditionExpr.Or(arrayOf(*terms))
    private fun groups(fixture: JIRRuleTestFixtures) = fixture.forms(
        EffectiveBidiRulesProvider(NoTaintRules, fixture.manager.marks), view = RuleView.RUN1_GROUP)
    private fun occurrence(fixture: JIRRuleTestFixtures): RuleOccurrenceId {
        val method = fixture.method("plain")
        return RuleOccurrenceId(FormKey(FormOwner(), method, null, method.instList.first()), RuleKind.CALL_SOURCE, 0)
    }
    private fun atom(pattern: Pattern) = (pattern.fact.mark as MarkSlot.Concrete).mark

    @Test fun `provider removes alias products but retains each bound position`(): Unit {
        val fixture = fixture()
        val expression = and(*Array(4) { i ->
            or(literal(AccessPathBase.Argument(i)), literal(AccessPathBase.Argument(i), "U"))
        })
        val selected = groups(fixture).predicate("group", ExprOrConstant(expression))
        val full = fixture.forms().predicate("full", ExprOrConstant(expression))
        assertEquals(4, selected.leaves.size)
        assertEquals(8, full.leaves.size)
        assertEquals(RulePredicate.Kind.AND, selected.kind(selected.root))
        assertTrue(selected.leaves.all { fixture.manager.marks.isGroup(atom(it)) })
        assertTrue(full.leaves.all { !fixture.manager.marks.isGroup(atom(it)) })

        fun count(predicate: RulePredicate, both: Boolean): Int {
            val store = RulePredicateStore(fixture.manager)
            val id = occurrence(fixture)
            var observations = 0
            for ((leaf, pattern) in predicate.leaves.withIndex()) {
                if (!both && atom(pattern) == fixture.manager.marks.mark("U")) continue
                val initial = fixture.manager.initial(pattern.fact.base, null, Tail.STAR,
                    ExclusionSet.Empty, pattern.fact.mark)
                val facts = fixture.manager.factsOf(pattern.fact, ExclusionSet.Empty, Layer.DEMAND)
                store.add(id, id.form.statement, predicate, leaf,
                    ConjunctionStore.Input(initial, Layer.DEMAND, facts)) { observations++ }
            }
            return observations
        }
        assertEquals(1, count(selected, true))
        assertEquals(16, count(full, true))
        assertEquals(1, count(full, false))
    }

    @Test fun `full signed contradictions fold before equal group selectors`(): Unit {
        val fixture = fixture()
        val base = AccessPathBase.Argument(0)
        val forms = groups(fixture)
        assertTrue(forms.predicate("contradiction", ExprOrConstant(and(literal(base), literal(base, negated = true)))).isFalse)
        val distinct = forms.predicate("distinct", ExprOrConstant(and(literal(base), literal(base, "U", true))))
        assertFalse(distinct.isFalse)
        assertFalse(distinct.isTrue)
        assertEquals(1, distinct.leaves.size)
    }

    @Test fun `different owners paths and check kinds do not merge`(): Unit {
        val fixture = fixture()
        val base = AccessPathBase.Argument(0)
        val field = PositionAccess.Complex(PositionAccess.Simple(base), FieldAccessor("Owner", "f", "java.lang.Object"))
        val another = PositionAccess.Complex(PositionAccess.Simple(base), FieldAccessor("Owner", "h", "java.lang.Object"))
        val expression = and(literal(base), literal(base, "U"), literal(base, "V"),
            literal(base, path = field), literal(base, "U", path = another),
            ContainsMarkOnAnyAccessorLiteral(field, TaintMarkAccessor("T"), false))
        val predicate = groups(fixture).predicate("positions", ExprOrConstant(expression))
        assertEquals(5, predicate.leaves.size)
        assertEquals(2, predicate.leaves.map { fixture.manager.marks.groupOf(atom(it)) }.distinct().size)
        assertEquals(setOf(Tail.EXACT, Tail.ANY), predicate.leaves.filter { it.fact.path.isNotEmpty() }.map { it.fact.tail }.toSet())
    }

    @Test fun `positive absorption cannot erase equal conjunctions in different orders`(): Unit {
        val fixture = fixture()
        val a = literal(AccessPathBase.Argument(0))
        val b = literal(AccessPathBase.Argument(1))
        val predicate = groups(fixture).predicate("absorption", ExprOrConstant(or(and(a, b), and(b, a))))
        assertFalse(predicate.isFalse)
        assertEquals(2, predicate.leaves.size)
        val reduced = groups(fixture).predicate("absorption", ExprOrConstant(or(a, and(a, b))))
        assertEquals(1, reduced.leaves.size)
        assertEquals(AccessPathBase.Argument(0), reduced.leaves.single().fact.base)
    }

    @Test fun `same position aliases preserve the original fixed target source primitive`(): Unit {
        val fixture = fixture()
        val base = AccessPathBase.Argument(0)
        val at = PositionAccess.Complex(PositionAccess.Simple(base), FieldAccessor("Owner", "f", "java.lang.Object"))
        val expression = and(literal(base, path = at), literal(base, "U", path = at))
        val action = AssignMark(RuleMark("T"), PositionWithAccess(Result, PositionAccessor.AnyFieldAccessor))
        val rule = fixture.source(listOf(action, action))
        val id = occurrence(fixture)
        val summary = MicroEdgeBuilder().also { groups(fixture).source(it, rule, id, ExprOrConstant(expression)) }.buildStage()
        val source = summary.sources.single()
        assertTrue(source.groupTransfers)
        assertEquals(1, source.predicate.leaves.size)
        assertTrue(source.unaryTransfers.isEmpty())
        assertEquals(1, assertNotNull(source.multiPredicate).leaves.size)
        assertEquals(listOf(0, 1), source.actions.map { it.id.ordinal })
        assertEquals(listOf(Tail.ANY_TAINT, Tail.ANY_TAINT), source.actions.map { it.target.tail })
        assertTrue(source.actions.all { fixture.manager.marks.isGroup((it.target.mark as MarkSlot.Concrete).mark) })

        val mark = atom(source.predicate.leaves.single())
        val initial = fixture.manager.initial(base, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.Concrete(mark))
        val current = fixture.manager.factsOf(PathFact(base, emptyList(), Tail.ANY, MarkSlot.Concrete(mark)), ExclusionSet.Empty, Layer.DEMAND)
        val wrong = CollectingOut()
        fixture.ops.applyEdge(current, initial, source.transfer(source.predicate.leaves.single(), source.actions.first()).edge,
            statementEdge = true, mode = ApMode(true, Direction.FORWARD, 1), out = wrong)
        assertTrue(wrong.results.isNotEmpty())
        assertTrue(wrong.results.flatMap { fixture.ops.leaves(it) }.all { it.fact.tail == Tail.ANY })
        val correct = fixture.ops.targetTree(source.actions.first().target, Layer.DEMAND)
        assertEquals(Tail.ANY_TAINT, fixture.ops.leaves(correct).single().fact.tail)

        val full = MicroEdgeBuilder().also { fixture.forms().source(it, rule, id, ExprOrConstant(expression)) }.buildStage().sources.single()
        assertFalse(full.groupTransfers)
        assertEquals(2, full.predicate.leaves.size)
        assertEquals(source.actions.map { it.id }, full.actions.map { it.id })
    }

    @Test fun `mixed source keeps its unary anchor and guarded multi transfer`(): Unit {
        val fixture = fixture()
        val p = AccessPathBase.Argument(0)
        val q = AccessPathBase.Argument(1)
        val expression = or(literal(p), and(literal(p, "U"), literal(q)))
        val rule = fixture.source(listOf(AssignMark(RuleMark("T"), Result)))
        val source = MicroEdgeBuilder().also { groups(fixture).source(it, rule, occurrence(fixture), ExprOrConstant(expression)) }
            .buildStage().sources.single()
        assertEquals(1, source.predicate.leaves.size)
        assertEquals(p, source.unaryTransfers.single().anchor.fact.base)
        assertEquals(setOf(p, q), assertNotNull(source.multiPredicate).leaves.map { it.fact.base }.toSet())
    }
}
