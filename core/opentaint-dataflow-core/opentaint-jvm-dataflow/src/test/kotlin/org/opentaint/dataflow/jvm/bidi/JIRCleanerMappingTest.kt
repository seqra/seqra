package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isFieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.ap.TaintMark
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.configuration.TaintCleanReach
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.configuration.jvm.TaintMark as RuleMark
import org.opentaint.dataflow.jvm.BasicTestUtils
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodPositionBaseTypeResolver
import org.opentaint.dataflow.jvm.ap.ifds.taint.resolveAp
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRRuleTestFixtures
import org.opentaint.dataflow.taint.RuleConditionRewriter
import org.opentaint.dataflow.taint.RuleConditionRewriter.ExprOrConstant
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr.ContainsMarkLiteral
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr.ContainsMarkOnAnyAccessorLiteral
import kotlin.test.*

class JIRCleanerMappingTest : BasicTestUtils() {
    private val fixture by lazy { JIRRuleTestFixtures(cp) }
    private val m get() = fixture.manager
    private val forms by lazy { fixture.forms() }
    private val method get() = fixture.method("plain")
    private val types get() = JIRMethodPositionBaseTypeResolver(method)
    private val x = AccessPathBase.Argument(0)
    private val mark = RuleMark("T")
    private val t get() = m.marks.mark(mark.name)
    private fun p(vararg ids: Int) = ids.toList()
    private fun idx(name: String, type: String = "java.lang.Object", owner: String = CLS) =
        m.accessors.index(FieldAccessor(owner, name, type))
    private fun at(base: Position, name: String, type: String = "java.lang.Object", owner: String = CLS) =
        PositionWithAccess(base, PositionAccessor.FieldAccessor(owner, name, type))
    private fun any(position: Position) = PositionWithAccess(position, PositionAccessor.AnyFieldAccessor)
    private fun cleaner(actions: List<Action>, condition: Condition = JIRRuleTestFixtures.trueCondition) =
        fixture.cleaner(actions, condition)
    private fun lower(rule: TaintCleaner, condition: ExprOrConstant = RuleConditionRewriter.trueExpr,
                      resolver: JIRMethodPositionBaseTypeResolver = types): List<CleanStep> {
        assertTrue(forms.valid(rule))
        return forms.cleanSteps(rule, condition, resolver)
    }

    @Test fun `selected mark reach mapping covers root element field nested field and static rows`(): Unit {
        val cls = m.accessors.index(org.opentaint.dataflow.ap.ifds.ClassStaticAccessor(CLS))
        val f = idx("f")
        val next = idx("next", CLS)
        val rows = listOf(
            Triple<Position, AccessPathBase, List<Int>>(Argument(0), x, emptyList()),
            Triple(PositionWithAccess(Argument(0), PositionAccessor.ElementAccessor), x, p(m.accessors.index(ElementAccessor))),
            Triple(at(Argument(0), "f"), x, p(f)),
            Triple(at(at(Argument(0), "next", CLS), "f"), x, p(next, f)),
            Triple(ClassStatic(CLS), AccessPathBase.ClassStatic, p(cls)),
            Triple(at(ClassStatic(CLS), "sField"), AccessPathBase.ClassStatic, p(cls, idx("sField"))),
        )
        for ((position, base, path) in rows) for (ruleReach in TaintCleanReach.entries) for (trailingAny in listOf(false, true)) {
            val rule = cleaner(listOf(RemoveMark(mark, if (trailingAny) any(position) else position, ruleReach)))
            val step = lower(rule).single()
            val reach = if (trailingAny) CleanReach.BELOW else when (ruleReach) {
                TaintCleanReach.Exact -> CleanReach.EXACT
                TaintCleanReach.ExactAndAnyField -> CleanReach.AT_AND_BELOW
            }
            assertMapped(step, base, path, reach, t)
        }
    }

    @Test fun `all mark actions map root element and named fields to at and below or below`(): Unit {
        val f = idx("f")
        val rows = listOf(
            Argument(0) to emptyList(),
            PositionWithAccess(Argument(0), PositionAccessor.ElementAccessor) to p(m.accessors.index(ElementAccessor)),
            at(Argument(0), "f") to p(f),
        )
        for ((position, path) in rows) for (trailingAny in listOf(false, true)) {
            val step = lower(cleaner(listOf(RemoveAllMarks(if (trailingAny) any(position) else position)))).single()
            assertMapped(step, x, path, if (trailingAny) CleanReach.BELOW else CleanReach.AT_AND_BELOW, null)
        }
    }

    @Test fun `static all marks actions are ordered kills with one exclusion at every proper prefix`(): Unit {
        val cls = m.accessors.index(org.opentaint.dataflow.ap.ifds.ClassStaticAccessor(CLS))
        val field = idx("sField")
        val g = idx("g")
        val classPosition = ClassStatic(CLS)
        val fieldPosition = at(classPosition, "sField")
        val deepPosition = at(fieldPosition, "g")
        val steps = lower(cleaner(listOf(RemoveAllMarks(classPosition), RemoveAllMarks(fieldPosition), RemoveAllMarks(deepPosition))))
        assertEquals(3, steps.size)
        for ((step, path) in steps.zip(listOf(p(cls), p(cls, field), p(cls, field, g)))) {
            val kill = assertIs<CleanStep.Kill>(step)
            assertEquals(setOf(AccessPathBase.ClassStatic), kill.keepEdges.touched)
            val expected = path.indices.map { k ->
                val side = PathFact(AccessPathBase.ClassStatic, path.take(k), Tail.STAR, MarkSlot.STAR)
                PathEdge(side, side, ExclusionSet.of(path[k]))
            }.toSet()
            assertEquals(expected, kill.keepEdges.edges.map { it.edge }.toSet())
            assertTrue(kill.keepEdges.typeFilters.isEmpty() && kill.keepEdges.resultFilters.isEmpty())
        }
        val invalid = cleaner(listOf(RemoveAllMarks(any(classPosition))))
        assertFalse(forms.valid(invalid))
    }

    @Test fun `positive negative exact and any mark conditions and false conditions produce no cleaner steps`(): Unit {
        val position = at(Argument(0), "f")
        for (negated in listOf(false, true)) for (onAny in listOf(false, true)) {
            val literal = if (onAny) ContainsMarkOnAnyAccessorLiteral(Argument(0).resolveAp(), TaintMarkAccessor("T"), negated)
                          else ContainsMarkLiteral(Argument(0).resolveAp(), TaintMarkAccessor("T"), negated)
            assertTrue(lower(cleaner(listOf(RemoveMark(mark, position))), ExprOrConstant(literal)).isEmpty())
        }
        assertTrue(lower(cleaner(listOf(RemoveAllMarks(position))), RuleConditionRewriter.falseExpr).isEmpty())
        assertTrue(lower(cleaner(emptyList())).isEmpty())
        assertTrue(lower(cleaner(listOf(AssignMark(mark, Argument(0)), CopyAllMarks(Argument(0), Result)))).isEmpty())
    }

    @Test fun `String selected cleaner expands in order to string bytes while AnyField has no companion`(): Unit {
        val bytes = idx("<string-bytes>", "byte[]", "java.lang.String")
        val text = idx("text", "java.lang.String")
        val stringTypes = JIRMethodPositionBaseTypeResolver(findMethod(CLS, "stringArg"))
        val root = lower(cleaner(listOf(RemoveMark(mark, Argument(0)))), resolver = stringTypes)
        assertEquals(2, root.size)
        assertMapped(root[0], x, emptyList(), CleanReach.EXACT, t)
        assertMapped(root[1], x, p(bytes), CleanReach.EXACT, t)
        val field = lower(cleaner(listOf(RemoveMark(mark, at(Argument(0), "text", "java.lang.String"),
            TaintCleanReach.ExactAndAnyField))))
        assertEquals(2, field.size)
        assertMapped(field[0], x, p(text), CleanReach.AT_AND_BELOW, t)
        assertMapped(field[1], x, p(text, bytes), CleanReach.AT_AND_BELOW, t)
        assertNotSame(assertIs<CleanStep.Field>(field[0]).temporary, assertIs<CleanStep.Field>(field[1]).temporary)
        val trailing = lower(cleaner(listOf(RemoveMark(mark, any(at(Argument(0), "text", "java.lang.String"))))))
        assertEquals(1, trailing.size)
        assertMapped(trailing.single(), x, p(text), CleanReach.BELOW, t)
    }

    @Test fun `nested owner keeps root filters and resolves only final value type for the temporary`(): Unit {
        val holder = idx("holder", HOLDER)
        val value = idx("value", owner = HOLDER)
        val primitive = idx("primitive", "int", HOLDER)
        data class ValueCase(val name: String, val type: String, val last: Int)
        for ((fieldName, fieldType, last) in listOf(
            ValueCase("value", "java.lang.Object", value), ValueCase("primitive", "int", primitive))) {
            val position = at(at(Argument(0), "holder", HOLDER), fieldName, fieldType, HOLDER)
            val step = assertIs<CleanStep.Field>(lower(cleaner(listOf(RemoveMark(mark, position)))).single())
            assertEquals(p(holder, last), step.read.edges.single { it.edge.to.base === step.temporary }.edge.from.path)
            val input = fixture.manager.accessors.accessor(holder)
            assertEquals(org.opentaint.dataflow.ap.ifds.FactTypeChecker.FilterResult.Accept,
                step.read.typeFilters.getValue(x).may.check(input))
            assertSame(step.read.typeFilters[x], step.write.typeFilters[x])
            val expected = fixture.typeFilters.of(assertNotNull(cp.findTypeOrNull(fieldType)))
            assertSame(expected, step.read.resultFilters[step.temporary])
            assertSame(expected, step.write.typeFilters[step.temporary])
            assertTrue(step.write.resultFilters.isEmpty())
        }
    }

    @Test fun `lowered actions keep rule order own cached paths use fresh temporaries and share them in reversal`(): Unit {
        val actions = arrayListOf<Action>(RemoveMark(mark, at(Argument(0), "f")),
            RemoveAllMarks(at(Argument(0), "g")), RemoveMark(RuleMark("U"), at(Argument(0), "f")))
        val steps = lower(cleaner(actions))
        val fields = steps.map { assertIs<CleanStep.Field>(it) }
        assertEquals(listOf(p(idx("f")), p(idx("g")), p(idx("f"))), fields.map { field ->
            field.read.edges.single { it.edge.to.base === field.temporary }.edge.from.path })
        assertEquals(listOf(t, null, m.marks.mark("U")), fields.map { it.cleaner.mark })
        assertEquals(3, fields.map { it.temporary }.toSet().size)
        val stage = CallStage.Actions(CallPoint.BEFORE, CallPoint.AFTER, steps)
        actions.clear()
        assertEquals(3, stage.steps.size)
        assertFailsWith<UnsupportedOperationException> { (stage.steps as MutableList).clear() }
        val plan = CallPlan(setOf(x), listOf(stage), emptyList(), CallPoint.BEFORE, CallPoint.AFTER)
        val reversed = assertIs<CallStage.Actions>(plan.reversed().stages.single())
        for ((forward, backwardStep) in fields.asReversed().zip(reversed.steps)) {
            val backward = assertIs<CleanStep.Field>(backwardStep)
            assertSame(forward.temporary, backward.temporary)
            assertSame(forward.cleaner, backward.cleaner)
            assertTrue(backward.read.typeFilters.isEmpty() && backward.read.resultFilters.isEmpty())
            assertTrue(backward.write.typeFilters.isEmpty() && backward.write.resultFilters.isEmpty())
            assertEquals(forward.write.edges.map { it.forward }.toSet(), backward.read.edges.map { it.forward }.toSet())
            val path = forward.read.edges.single { it.edge.to.base === forward.temporary }.edge.from.path
            assertFailsWith<UnsupportedOperationException> { (path as MutableList).clear() }
        }
        val again = assertIs<CleanStep.Field>(lower(cleaner(listOf(RemoveMark(mark, at(Argument(0), "f"))))).single())
        assertTrue(fields.none { it.temporary === again.temporary })
    }

    private fun assertMapped(step: CleanStep, base: AccessPathBase, path: List<Int>, reach: CleanReach, mark: TaintMark?) {
        if (base != AccessPathBase.ClassStatic && path.isNotEmpty() && path.last().isFieldAccessor()) {
            val field = assertIs<CleanStep.Field>(step)
            assertIs<AccessPathBase.CleanerTemporary>(field.temporary)
            assertSame(field.temporary, field.cleaner.base)
            assertNull(field.cleaner.path)
            assertEquals(reach, field.cleaner.reach)
            assertEquals(mark, field.cleaner.mark)
            assertEquals(path, field.read.edges.single { it.edge.to.base === field.temporary }.edge.from.path)
            assertEquals(path, field.write.edges.single { it.edge.from.base === field.temporary && it.edge.to.base == base }.edge.to.path)
            assertEquals(setOf(base, field.temporary), field.read.touched)
            assertEquals(setOf(base, field.temporary), field.write.touched)
        } else {
            val clean = assertIs<CleanStep.Clean>(step).cleaner
            assertEquals(base, clean.base)
            assertEquals(path, clean.path?.toList().orEmpty())
            assertEquals(reach, clean.reach)
            assertEquals(mark, clean.mark)
        }
    }

    private companion object {
        const val CLS = "sample.bidi.BidiRuleSample"
        const val HOLDER = "sample.bidi.BidiRuleSample\$Holder"
    }
}
