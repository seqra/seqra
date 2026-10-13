package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.jvm.BasicTestUtils
import org.opentaint.dataflow.jvm.ap.ifds.CallPositionValue
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionRewriter
import org.opentaint.dataflow.jvm.ap.ifds.taint.ContainsMarkOnAnyField
import org.opentaint.dataflow.jvm.ap.ifds.taint.resolveAp
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRRuleTestFixtures
import org.opentaint.dataflow.taint.PositionAccess
import org.opentaint.dataflow.taint.RuleConditionRewriter
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.ext.toType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JIRSignedConditionTest : BasicTestUtils() {
    private val fixture by lazy { JIRRuleTestFixtures(cp) }
    private val forms by lazy { fixture.forms() }
    private val mark = TaintMark("T")
    private fun atom(p: Position): Condition = CommonCondition.Atom(ContainsMark(p, mark))
    private val rewriter = object : RuleConditionRewriter<JirCondition> {
        override fun rewriteAtom(atom: JirCondition, negated: Boolean): RuleConditionRewriter.ExprOrConstant {
            return RuleConditionRewriter.ExprOrConstant(when (atom) {
                is ContainsMark -> TaintMarkAwareConditionExpr.ContainsMarkLiteral(atom.position.resolveAp(),
                    TaintMarkAccessor(atom.mark.name), negated)
                is ContainsMarkOnAnyField -> TaintMarkAwareConditionExpr.ContainsMarkOnAnyAccessorLiteral(atom.position.resolveAp(),
                    TaintMarkAccessor(atom.mark.name), negated)
                else -> error("Unexpected test atom")
            })
        }
    }

    @Test fun `source and sink fold contradictions before residual negative projection`(): Unit {
        val p = atom(Argument(0))
        for (rule in listOf(fixture.source(listOf(AssignMark(mark, Result)), CommonCondition.And(listOf(p, CommonCondition.Not(p)))),
            fixture.sink(condition = CommonCondition.And(listOf(p, CommonCondition.Not(p)))))) {
            assertTrue(forms.rewriteSourceOrSink(rule, rewriter).isFalse)
        }
        val remaining = fixture.sink(condition = CommonCondition.Not(p))
        assertTrue(forms.rewriteSourceOrSink(remaining, rewriter).isTrue)
        assertFalse(forms.rewriteSourceOrSink(remaining, rewriter, projectNegatives = false).isTrue,
            "Entry eligibility must still see the remaining mark dependency")
    }

    @Test fun `raw validation precedes false folding and prevents static evaluator invocation`(): Unit {
        val bad = PositionWithAccess(PositionWithAccess(Argument(0), PositionAccessor.AnyFieldAccessor), PositionAccessor.AnyFieldAccessor)
        val p = atom(bad)
        val rule = fixture.source(listOf(AssignMark(mark, Result)), CommonCondition.And(listOf(p, CommonCondition.Not(p))))
        var calls = 0
        val evaluator = object : RuleConditionRewriter<JirCondition> {
            override fun rewriteAtom(atom: JirCondition, negated: Boolean): RuleConditionRewriter.ExprOrConstant {
                calls++
                return RuleConditionRewriter.trueExpr
            }
        }
        assertTrue(forms.rewriteSourceOrSink(rule, evaluator).isFalse)
        assertEquals(0, calls)
        assertFalse(forms.valid(rule))
    }

    @Test fun `distinct positions marks paths and root versus any field are not signed complements`(): Unit {
        val positive = atom(Argument(0))
        val others: List<Condition> = listOf(atom(Argument(1)),
            CommonCondition.Atom(ContainsMark(Argument(0), TaintMark("U"))),
            CommonCondition.Atom(ContainsMarkOnAnyField(Argument(0), mark)),
            atom(PositionWithAccess(Argument(0), PositionAccessor.FieldAccessor(JIRRuleTestFixtures.SAMPLE_CLASS,
                "f", "java.lang.Object"))))
        for (other in others) for (source in listOf(false, true)) {
            val and = CommonCondition.And(listOf(positive, CommonCondition.Not(other)))
            val or = CommonCondition.Or(listOf(positive, CommonCondition.Not(other)))
            fun rule(condition: Condition): TaintConfigurationItem = if (source)
                fixture.source(listOf(AssignMark(mark, Result)), condition) else fixture.sink(condition = condition)
            val signedAnd = forms.rewriteSourceOrSink(rule(and), rewriter, projectNegatives = false)
            val signedOr = forms.rewriteSourceOrSink(rule(or), rewriter, projectNegatives = false)
            assertFalse(signedAnd.isFalse || signedAnd.isTrue, "Distinct signed atoms must remain in $and")
            assertFalse(signedOr.isFalse || signedOr.isTrue, "Distinct signed atoms must remain in $or")
            val projected = forms.rewriteSourceOrSink(rule(and), rewriter)
            assertEquals(TaintMarkAwareConditionExpr.ContainsMarkLiteral(PositionAccess.Simple(AccessPathBase.Argument(0)),
                TaintMarkAccessor("T"), false), projected.expr)
            assertTrue(forms.rewriteSourceOrSink(rule(or), rewriter).isTrue)
        }
    }

    @Test fun `raw nonmark complement is false before polarity-sensitive static approximation`(): Unit {
        // Top-level runtime types use the same canonical and binary name. Nested fixture types
        // render a dot in value.typeName and a dollar in class.name, which is a separate IR issue.
        val parent = findClass("java.lang.Number")
        val child = findClass("java.lang.Integer")
        val value = JIRLocalVar(901, "parent", parent.toType())
        val resolver = object : PositionResolver<CallPositionValue> {
            override fun resolve(position: Position) = CallPositionValue.Value(value)
        }
        val method = fixture.method("plain")
        val evaluator = JIRMarkAwareConditionRewriter(resolver, fixture.checker, null, method.instList.first())
        val type = TypeMatchesPattern(Argument(0), ConditionNameMatcher.Concrete(child.name))
        val p: Condition = CommonCondition.Atom(type)
        assertEquals(parent.name, value.type.typeName)
        val diagnostic = "valueType=${value.type.typeName} parent=${parent.name} child=${child.name} " +
            "childSuper=${child.superClass?.name} maySubtype=${fixture.checker.typeMayHaveSubtypeOf(parent.name, child.name)}"
        assertTrue(evaluator.rewriteAtom(type, negated = false).isTrue, diagnostic)
        assertTrue(evaluator.rewriteAtom(type, negated = true).isTrue, diagnostic)
        val rule = fixture.sink(condition = CommonCondition.And(listOf(p, CommonCondition.Not(p))))
        assertTrue(forms.rewriteSourceOrSink(rule, evaluator).isFalse)
    }

    @Test fun `a complement exposed by rewriting is folded before negative projection`(): Unit {
        val condition = CommonCondition.And(listOf(atom(Argument(0)), CommonCondition.Not(atom(Argument(1)))))
        val evaluator = object : RuleConditionRewriter<JirCondition> {
            override fun rewriteAtom(atom: JirCondition, negated: Boolean) =
                RuleConditionRewriter.ExprOrConstant(TaintMarkAwareConditionExpr.ContainsMarkLiteral(
                    PositionAccess.Simple(AccessPathBase.Argument(0)), TaintMarkAccessor("T"), negated))
        }
        assertTrue(forms.rewriteSourceOrSink(fixture.sink(condition = condition), evaluator).isFalse)
    }

    @Test fun `deep raw rules validate and rewrite without recursive condition hashing`(): Unit {
        var condition = atom(Argument(0))
        repeat(32768) { condition = CommonCondition.Not(condition) }
        val rule = fixture.sink(condition = condition)
        assertTrue(forms.valid(rule))
        val folded = forms.rewriteSourceOrSink(rule, rewriter)
        assertFalse(folded.isFalse)
        assertFalse(folded.isTrue)
    }

    @Test fun `deep invalid raw rule diagnostics keep the reason without recursive rendering`(): Unit {
        var condition = atom(PositionWithAccess(PositionWithAccess(Argument(0),
            PositionAccessor.AnyFieldAccessor), PositionAccessor.AnyFieldAccessor))
        repeat(32768) { condition = CommonCondition.Not(condition) }
        val rule = fixture.sink(condition = condition).copy(serializedId = "deep-invalid")
        assertFalse(forms.valid(rule))
        assertFalse(forms.valid(rule))
        assertTrue(forms.rewriteSourceOrSink(rule, rewriter).isFalse)
    }

    @Test fun `transient rule copies validate independently and diagnostics retain detached identifiers`(): Unit {
        val local = fixture.forms()
        val rule = fixture.sink(condition = atom(Argument(0))).copy(serializedId = "transient-rule")
        val bad = rule.copy(condition = atom(PositionWithAccess(PositionWithAccess(Argument(0),
            PositionAccessor.AnyFieldAccessor), PositionAccessor.AnyFieldAccessor)))
        repeat(3) {
            assertTrue(local.valid(rule.copy()))
            assertFalse(local.valid(bad.copy()))
            assertTrue(local.rewriteSourceOrSink(bad.copy(), rewriter).isFalse)
        }

        val errors = org.opentaint.dataflow.jvm.bidi.interp.RuleErrors()
        assertFalse(errors.reject(rule, "probe reason"))
        assertFalse(errors.reject(rule, "probe reason"))
        assertFalse(errors.reject(rule.copy(), "probe reason"))
        val diagnostics = errors.javaClass.getDeclaredField("seen").apply { isAccessible = true }.get(errors) as Set<*>
        assertEquals(1, diagnostics.size)
        errors.applied(rule, "probe reason")
        assertFalse(errors.reject(rule, "another reason"))
        assertFalse(errors.reject(rule.copy(serializedId = "another-rule"), "probe reason"))
        assertEquals(4, diagnostics.size, "Distinct IDs, reasons and applied/rejected diagnostics must remain separate")
        for (diagnostic in diagnostics) {
            val fields = checkNotNull(diagnostic).javaClass.declaredFields.filterNot {
                java.lang.reflect.Modifier.isStatic(it.modifiers)
            }
            assertTrue(fields.isNotEmpty())
            for (field in fields) {
                field.isAccessible = true
                assertTrue(field.get(diagnostic) is String, "Diagnostics must not retain bound rules, conditions or methods")
            }
        }

        val unidentified = object {
            override fun hashCode(): Int = error("Diagnostics must not hash arbitrary bound owners")
            override fun toString(): String = error("Diagnostics must not render arbitrary bound owners")
        }
        repeat(2) { assertFalse(errors.reject(unidentified, "unidentified reason")) }
        assertEquals(4, diagnostics.size, "Anonymous rules must not enter the detached-ID deduplication set")
    }
}
