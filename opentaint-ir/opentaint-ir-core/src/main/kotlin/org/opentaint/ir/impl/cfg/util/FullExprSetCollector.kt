
package org.opentaint.ir.impl.cfg.util

import org.opentaint.ir.api.JIRRawAddExpr
import org.opentaint.ir.api.JIRRawAndExpr
import org.opentaint.ir.api.JIRRawArgument
import org.opentaint.ir.api.JIRRawArrayAccess
import org.opentaint.ir.api.JIRRawBool
import org.opentaint.ir.api.JIRRawByte
import org.opentaint.ir.api.JIRRawCastExpr
import org.opentaint.ir.api.JIRRawChar
import org.opentaint.ir.api.JIRRawClassConstant
import org.opentaint.ir.api.JIRRawCmpExpr
import org.opentaint.ir.api.JIRRawCmpgExpr
import org.opentaint.ir.api.JIRRawCmplExpr
import org.opentaint.ir.api.JIRRawDivExpr
import org.opentaint.ir.api.JIRRawDouble
import org.opentaint.ir.api.JIRRawDynamicCallExpr
import org.opentaint.ir.api.JIRRawEqExpr
import org.opentaint.ir.api.JIRRawExpr
import org.opentaint.ir.api.JIRRawFieldRef
import org.opentaint.ir.api.JIRRawFloat
import org.opentaint.ir.api.JIRRawGeExpr
import org.opentaint.ir.api.JIRRawGtExpr
import org.opentaint.ir.api.JIRRawInstanceOfExpr
import org.opentaint.ir.api.JIRRawInt
import org.opentaint.ir.api.JIRRawInterfaceCallExpr
import org.opentaint.ir.api.JIRRawLeExpr
import org.opentaint.ir.api.JIRRawLengthExpr
import org.opentaint.ir.api.JIRRawLocal
import org.opentaint.ir.api.JIRRawLong
import org.opentaint.ir.api.JIRRawLtExpr
import org.opentaint.ir.api.JIRRawMethodConstant
import org.opentaint.ir.api.JIRRawMulExpr
import org.opentaint.ir.api.JIRRawNegExpr
import org.opentaint.ir.api.JIRRawNeqExpr
import org.opentaint.ir.api.JIRRawNewArrayExpr
import org.opentaint.ir.api.JIRRawNewExpr
import org.opentaint.ir.api.JIRRawNullConstant
import org.opentaint.ir.api.JIRRawOrExpr
import org.opentaint.ir.api.JIRRawRemExpr
import org.opentaint.ir.api.JIRRawShlExpr
import org.opentaint.ir.api.JIRRawShort
import org.opentaint.ir.api.JIRRawShrExpr
import org.opentaint.ir.api.JIRRawSpecialCallExpr
import org.opentaint.ir.api.JIRRawStaticCallExpr
import org.opentaint.ir.api.JIRRawStringConstant
import org.opentaint.ir.api.JIRRawSubExpr
import org.opentaint.ir.api.JIRRawThis
import org.opentaint.ir.api.JIRRawUshrExpr
import org.opentaint.ir.api.JIRRawVirtualCallExpr
import org.opentaint.ir.api.JIRRawXorExpr
import org.opentaint.ir.api.cfg.JIRRawExprVisitor

class FullExprSetCollector : JIRRawExprVisitor<Unit> {
    val exprs = mutableSetOf<JIRRawExpr>()

    override fun visitJcRawAddExpr(expr: JIRRawAddExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawAndExpr(expr: JIRRawAndExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawCmpExpr(expr: JIRRawCmpExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawCmpgExpr(expr: JIRRawCmpgExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawCmplExpr(expr: JIRRawCmplExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawDivExpr(expr: JIRRawDivExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawMulExpr(expr: JIRRawMulExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawEqExpr(expr: JIRRawEqExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawNeqExpr(expr: JIRRawNeqExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawGeExpr(expr: JIRRawGeExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawGtExpr(expr: JIRRawGtExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawLeExpr(expr: JIRRawLeExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawLtExpr(expr: JIRRawLtExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawOrExpr(expr: JIRRawOrExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawRemExpr(expr: JIRRawRemExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawShlExpr(expr: JIRRawShlExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawShrExpr(expr: JIRRawShrExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawSubExpr(expr: JIRRawSubExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawUshrExpr(expr: JIRRawUshrExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawXorExpr(expr: JIRRawXorExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawLengthExpr(expr: JIRRawLengthExpr) {
        exprs.add(expr)
        expr.array.accept(this)
    }

    override fun visitJcRawNegExpr(expr: JIRRawNegExpr) {
        exprs.add(expr)
        expr.operand.accept(this)
    }

    override fun visitJcRawCastExpr(expr: JIRRawCastExpr) {
        exprs.add(expr)
        expr.operand.accept(this)
    }

    override fun visitJcRawNewExpr(expr: JIRRawNewExpr) {
        exprs.add(expr)
    }

    override fun visitJcRawNewArrayExpr(expr: JIRRawNewArrayExpr) {
        exprs.add(expr)
        expr.dimensions.forEach { it.accept(this) }
    }

    override fun visitJcRawInstanceOfExpr(expr: JIRRawInstanceOfExpr) {
        exprs.add(expr)
        expr.operand.accept(this)
    }

    override fun visitJcRawDynamicCallExpr(expr: JIRRawDynamicCallExpr) {
        exprs.add(expr)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJcRawVirtualCallExpr(expr: JIRRawVirtualCallExpr) {
        exprs.add(expr)
        expr.instance.accept(this)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJcRawInterfaceCallExpr(expr: JIRRawInterfaceCallExpr) {
        exprs.add(expr)
        expr.instance.accept(this)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJcRawStaticCallExpr(expr: JIRRawStaticCallExpr) {
        exprs.add(expr)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJcRawSpecialCallExpr(expr: JIRRawSpecialCallExpr) {
        exprs.add(expr)
        expr.instance.accept(this)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJcRawThis(value: JIRRawThis) {
        exprs.add(value)
    }

    override fun visitJcRawArgument(value: JIRRawArgument) {
        exprs.add(value)
    }

    override fun visitJcRawLocal(value: JIRRawLocal) {
        exprs.add(value)
    }

    override fun visitJcRawFieldRef(value: JIRRawFieldRef) {
        exprs.add(value)
        value.instance?.accept(this)
    }

    override fun visitJcRawArrayAccess(value: JIRRawArrayAccess) {
        exprs.add(value)
        value.array.accept(this)
        value.index.accept(this)
    }

    override fun visitJcRawBool(value: JIRRawBool) {
        exprs.add(value)
    }

    override fun visitJcRawByte(value: JIRRawByte) {
        exprs.add(value)
    }

    override fun visitJcRawChar(value: JIRRawChar) {
        exprs.add(value)
    }

    override fun visitJcRawShort(value: JIRRawShort) {
        exprs.add(value)
    }

    override fun visitJcRawInt(value: JIRRawInt) {
        exprs.add(value)
    }

    override fun visitJcRawLong(value: JIRRawLong) {
        exprs.add(value)
    }

    override fun visitJcRawFloat(value: JIRRawFloat) {
        exprs.add(value)
    }

    override fun visitJcRawDouble(value: JIRRawDouble) {
        exprs.add(value)
    }

    override fun visitJcRawNullConstant(value: JIRRawNullConstant) {
        exprs.add(value)
    }

    override fun visitJcRawStringConstant(value: JIRRawStringConstant) {
        exprs.add(value)
    }

    override fun visitJcRawClassConstant(value: JIRRawClassConstant) {
        exprs.add(value)
    }

    override fun visitJcRawMethodConstant(value: JIRRawMethodConstant) {
        exprs.add(value)
    }
}

