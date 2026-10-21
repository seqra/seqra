package org.opentaint.dataflow.python

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.configuration.python.AnyArgument
import org.opentaint.dataflow.configuration.python.ContainsMark
import org.opentaint.dataflow.configuration.python.KwArgument
import org.opentaint.dataflow.configuration.python.NumberOfArgs
import org.opentaint.dataflow.configuration.python.TaintMark
import org.opentaint.dataflow.taint.PositionAccess
import org.opentaint.dataflow.taint.RuleConditionRewriter.ExprOrConstant
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr.ContainsMarkLiteral
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr.Or
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRCallArg
import org.opentaint.ir.api.python.PIRCallArgKind
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.ir.api.python.PIRLocation
import org.opentaint.ir.api.python.PIRStrConst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PIRCallSiteConditionTest {

    @Test
    fun `arity matches the exact positional count`() {
        val e = PIRCallAtomEvaluator(call(pos(), pos()))
        assertTrue(e.visit(NumberOfArgs(2)))
        assertFalse(e.visit(NumberOfArgs(1)))
        assertFalse(e.visit(NumberOfArgs(3)))
    }

    @Test
    fun `keyword args count toward arity`() {
        val e = PIRCallAtomEvaluator(call(pos(), kw("b")))
        assertTrue(e.visit(NumberOfArgs(2)))
        assertFalse(e.visit(NumberOfArgs(1)))
    }

    @Test
    fun `a star spread removes the arity upper bound`() {
        val e = PIRCallAtomEvaluator(call(pos(), star()))
        assertTrue(e.visit(NumberOfArgs(1)), "the concrete positional arg is the lower bound")
        assertTrue(e.visit(NumberOfArgs(50)), "the spread can supply any number of further args")
        assertFalse(e.visit(NumberOfArgs(0)), "below the concrete positional count is infeasible")
    }

    @Test
    fun `a double-star spread removes the arity upper bound`() {
        val e = PIRCallAtomEvaluator(call(kw("a"), doubleStar()))
        assertTrue(e.visit(NumberOfArgs(1)), "the concrete keyword arg is the lower bound")
        assertTrue(e.visit(NumberOfArgs(50)), "the ** spread can supply any number of further args")
    }

    @Test
    fun `arg star ContainsMark expands over positional and keyword args`() {
        val idxs = argumentIndicesOf(rewriteAnyArgMark(call(pos(), pos(), kw("b"))).expr as Or)
        assertEquals(listOf(0, 1, 2), idxs, "keyword args are included in arg(*) expansion")
    }

    @Test
    fun `arg star ContainsMark with a single positional arg yields one literal`() {
        val r = rewriteAnyArgMark(call(pos()))
        assertFalse(r.isFalse)
        val lit = r.expr as ContainsMarkLiteral
        assertEquals(0, argumentIndexOf(lit))
    }

    @Test
    fun `arg star ContainsMark over a lone keyword arg yields one literal`() {
        val r = rewriteAnyArgMark(call(kw("b")))
        assertFalse(r.isFalse)
        val lit = r.expr as ContainsMarkLiteral
        assertEquals(0, argumentIndexOf(lit))
    }

    @Test
    fun `arg star ContainsMark with no explicit args is false`() {
        assertTrue(rewriteAnyArgMark(call(star())).isFalse)
    }

    @Test
    fun `kwarg ContainsMark resolves to the matching keyword slot`() {
        val lit = rewriteKwMark(call(pos(), kw("b")), "b").expr as ContainsMarkLiteral
        assertEquals(1, argumentIndexOf(lit), "kwarg(b) is the second raw call arg")
    }

    @Test
    fun `kwarg ContainsMark for an absent keyword is false`() {
        assertTrue(rewriteKwMark(call(pos(), kw("b")), "missing").isFalse)
    }

    private fun rewriteAnyArgMark(call: PIRCall): ExprOrConstant =
        PIRConditionRewriter(
            PIRCallAnyArgumentResolver(call),
            PIRCallAtomEvaluator(call)
        ).rewriteAtom(ContainsMark(TaintMark("t"), AnyArgument), negated = false)

    private fun rewriteKwMark(call: PIRCall, name: String): ExprOrConstant =
        PIRConditionRewriter(
            PIRCallAnyArgumentResolver(call),
            PIRCallAtomEvaluator(call),
            call
        ).rewriteAtom(ContainsMark(TaintMark("t"), KwArgument(name)), negated = false)

    private fun argumentIndicesOf(or: Or): List<Int> = or.args.map { argumentIndexOf(it as ContainsMarkLiteral) }

    private fun argumentIndexOf(lit: ContainsMarkLiteral): Int =
        ((lit.position as PositionAccess.Simple).base as AccessPathBase.Argument).idx

    private fun call(vararg args: PIRCallArg): PIRCall =
        PIRCall(target = null, callee = PIRStrConst("callee"), args = args.toList(), location = unusedLocation)

    private fun pos(): PIRCallArg = PIRCallArg(PIRStrConst("x"), PIRCallArgKind.POSITIONAL)
    private fun kw(name: String): PIRCallArg = PIRCallArg(PIRStrConst("x"), PIRCallArgKind.KEYWORD, name)
    private fun star(): PIRCallArg = PIRCallArg(PIRStrConst("x"), PIRCallArgKind.STAR)
    private fun doubleStar(): PIRCallArg = PIRCallArg(PIRStrConst("x"), PIRCallArgKind.DOUBLE_STAR)

    private val unusedLocation = object : PIRLocation {
        override val method: PIRFunction get() = error("location is never read by the call-site evaluators")
        override val index: Int = 0
    }
}
