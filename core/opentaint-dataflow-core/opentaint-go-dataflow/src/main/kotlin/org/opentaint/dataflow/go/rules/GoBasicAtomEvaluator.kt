package org.opentaint.dataflow.go.rules

import org.opentaint.dataflow.go.GoCallExpr
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.value.GoIRConstValue
import org.opentaint.ir.go.value.GoIRConstantValue
import org.opentaint.ir.go.value.GoIRValue

class GoBasicAtomEvaluator(
    private val negated: Boolean,
    private val callExpr: GoCallExpr,
    private val statement: GoIRInst,
    private val returnValue: GoIRValue?,
) : GoConditionVisitor<Boolean> {

    override fun visit(c: GoRuleCondition.ContainsMark): Boolean {
        error("ContainsMark must be handled by the rewriter, not the evaluator")
    }

    override fun visit(c: GoRuleCondition.ContainsMarkOnAnyAccessor): Boolean {
        error("ContainsMarkOnAnyAccessor must be handled by the rewriter, not the evaluator")
    }

    override fun visit(c: GoRuleCondition.IsConstant): Boolean {
        val value = resolveValue(c.position) ?: return false
        return value is GoIRConstValue
    }

    override fun visit(c: GoRuleCondition.IsNull): Boolean {
        val value = resolveValue(c.position) ?: return false
        return value is GoIRConstValue && value.value is GoIRConstantValue.NilConst
    }

    override fun visit(c: GoRuleCondition.ConstantEq): Boolean {
        val value = resolveValue(c.position) ?: return false
        return cmpConstant(c.value, value) { irVal, condVal -> eqConstant(irVal, condVal) }
    }

    override fun visit(c: GoRuleCondition.ConstantLt): Boolean {
        val value = resolveValue(c.position) ?: return false
        return cmpConstant(c.value, value) { irVal, condVal -> ltConstant(irVal, condVal) }
    }

    override fun visit(c: GoRuleCondition.ConstantGt): Boolean {
        val value = resolveValue(c.position) ?: return false
        return cmpConstant(c.value, value) { irVal, condVal -> gtConstant(irVal, condVal) }
    }

    override fun visit(c: GoRuleCondition.ConstantMatches): Boolean {
        val value = resolveValue(c.position) ?: return false
        val constValue = (value as? GoIRConstValue)?.value ?: return false
        if (constValue !is GoIRConstantValue.StringConst) return false
        return c.pattern.containsMatchIn(constValue.value)
    }

    private fun resolveValue(pos: Position.Simple): GoIRValue? = when (pos) {
        is Position.Argument -> callExpr.explicitArgs.getOrNull(pos.index)
        is Position.Result -> returnValue
        is Position.This -> callExpr.effectiveReceiver
        is Position.ClassStatic -> null
    }

    private inline fun cmpConstant(
        condValue: ConstantValue,
        value: GoIRValue,
        cmp: (GoIRConstValue, ConstantValue) -> Boolean,
    ): Boolean {
        if (value !is GoIRConstValue) return false
        return cmp(value, condValue)
    }

    private fun eqConstant(irValue: GoIRConstValue, condValue: ConstantValue): Boolean {
        val v = irValue.value
        return when (condValue) {
            is ConstantIntValue -> v is GoIRConstantValue.IntConst && v.value == condValue.value.toLong()
            is ConstantStringValue -> v is GoIRConstantValue.StringConst && v.value == condValue.value
            is ConstantBooleanValue -> v is GoIRConstantValue.BoolConst && v.value == condValue.value
        }
    }

    private fun ltConstant(irValue: GoIRConstValue, condValue: ConstantValue): Boolean {
        val v = irValue.value
        return when (condValue) {
            is ConstantIntValue -> v is GoIRConstantValue.IntConst && v.value < condValue.value.toLong()
            is ConstantStringValue ->
                TODO("Go atom ConstantLt: not implementable against ConstantStringValue (Go IR string constants don't expose ordered comparison)")
            is ConstantBooleanValue ->
                TODO("Go atom ConstantLt: not implementable against ConstantBooleanValue (booleans aren't ordered)")
        }
    }

    private fun gtConstant(irValue: GoIRConstValue, condValue: ConstantValue): Boolean {
        val v = irValue.value
        return when (condValue) {
            is ConstantIntValue -> v is GoIRConstantValue.IntConst && v.value > condValue.value.toLong()
            is ConstantStringValue ->
                TODO("Go atom ConstantGt: not implementable against ConstantStringValue (Go IR string constants don't expose ordered comparison)")
            is ConstantBooleanValue ->
                TODO("Go atom ConstantGt: not implementable against ConstantBooleanValue (booleans aren't ordered)")
        }
    }
}
