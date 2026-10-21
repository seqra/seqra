package org.opentaint.dataflow.python

import org.opentaint.dataflow.configuration.python.AnyArgument
import org.opentaint.dataflow.configuration.python.Argument
import org.opentaint.dataflow.configuration.python.BoolConstantValue
import org.opentaint.dataflow.configuration.python.ClassRef
import org.opentaint.dataflow.configuration.python.ConstantCmp
import org.opentaint.dataflow.configuration.python.ConstantCmpType
import org.opentaint.dataflow.configuration.python.ConstantMatches
import org.opentaint.dataflow.configuration.python.ContainsMark
import org.opentaint.dataflow.configuration.python.ContainsMarkOnAnyAccessor
import org.opentaint.dataflow.configuration.python.IntConstantValue
import org.opentaint.dataflow.configuration.python.KwArgument
import org.opentaint.dataflow.configuration.python.NumberOfArgs
import org.opentaint.dataflow.configuration.python.PIRConditionVisitor
import org.opentaint.dataflow.configuration.python.Position
import org.opentaint.dataflow.configuration.python.PositionWithAccess
import org.opentaint.dataflow.configuration.python.Result
import org.opentaint.dataflow.configuration.python.StrConstantValue
import org.opentaint.dataflow.configuration.python.This
import org.opentaint.ir.api.python.PIRBoolConst
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRCallArgKind
import org.opentaint.ir.api.python.PIRIntConst
import org.opentaint.ir.api.python.PIRStrConst
import org.opentaint.ir.api.python.PIRValue

interface PIRBasicAtomEvaluator : PIRConditionVisitor<Boolean> {
    fun numberOrArgs(): IntRange

    fun valueAt(pos: Position): PIRValue?

    override fun visit(c: ContainsMark): Boolean =
        error("ContainsMark is a taint-fact atom; handle it in condition rewriter, not the evaluator")

    override fun visit(c: ContainsMarkOnAnyAccessor): Boolean =
        error("ContainsMarkOnAnyAccessor is a taint-fact atom; handle it in condition rewriter, not the evaluator")

    override fun visit(c: ConstantCmp): Boolean {
        val v = valueAt(c.pos) ?: return false
        return when (val expected = c.value) {
            is IntConstantValue -> v is PIRIntConst && when (c.cmp) {
                ConstantCmpType.Eq -> v.value == expected.value
                ConstantCmpType.Lt -> v.value < expected.value
                ConstantCmpType.Gt -> v.value > expected.value
            }
            is StrConstantValue -> v is PIRStrConst && when (c.cmp) {
                ConstantCmpType.Eq -> v.value == expected.value
                ConstantCmpType.Lt -> TODO()
                ConstantCmpType.Gt -> TODO()
            }
            is BoolConstantValue -> v is PIRBoolConst && when (c.cmp) {
                ConstantCmpType.Eq -> v.value == expected.value
                ConstantCmpType.Lt -> TODO()
                ConstantCmpType.Gt -> TODO()
            }
        }
    }

    override fun visit(c: ConstantMatches): Boolean {
        val v = valueAt(c.pos) ?: return false
        return v is PIRStrConst && c.pattern.matches(v.value)
    }

    override fun visit(c: NumberOfArgs): Boolean {
        return c.n in numberOrArgs()
    }
}

class PIRCallAtomEvaluator(private val call: PIRCall) : PIRBasicAtomEvaluator {
    override fun numberOrArgs(): IntRange {
        val explicit = call.args.count {
            it.kind == PIRCallArgKind.POSITIONAL || it.kind == PIRCallArgKind.KEYWORD
        }
        val hasSplat = call.args.any {
            it.kind == PIRCallArgKind.STAR || it.kind == PIRCallArgKind.DOUBLE_STAR
        }
        val max = if (hasSplat) Int.MAX_VALUE else explicit

        return explicit..max
    }

    override fun valueAt(pos: Position): PIRValue? = when (pos) {
        is Argument -> call.args.getOrNull(pos.index)?.value
        is KwArgument -> call.args.firstOrNull { it.kind == PIRCallArgKind.KEYWORD && it.keyword == pos.name }?.value
        is Result -> call.target

        AnyArgument -> error("AnyArgument is only supported for ContainsMark")

        is ClassRef,
        is This,
        is PositionWithAccess -> null
    }
}

class PIRSequentAtomEvaluator(
    val returnValue: PIRValue? = null,
) : PIRBasicAtomEvaluator {

    override fun numberOrArgs(): IntRange = 0..0

    override fun valueAt(pos: Position): PIRValue? = when (pos) {
        is Result -> {
            check(returnValue != null) {
                "Return value is required for Result position"
            }

            returnValue
        }

        else -> error("Unexpected position: $pos")
    }
}
