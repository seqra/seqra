package org.opentaint.opentaint-ir.impl.cfg.util

import org.opentaint.opentaint-ir.api.cfg.*

class FullExprSetCollector : JIRRawExprVisitor<Unit> {
    val exprs = mutableSetOf<JIRRawExpr>()

    override fun visitJIRRawAddExpr(expr: JIRRawAddExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJIRRawAndExpr(expr: JIRRawAndExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJIRRawCmpExpr(expr: JIRRawCmpExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJIRRawCmpgExpr(expr: JIRRawCmpgExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJIRRawCmplExpr(expr: JIRRawCmplExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJIRRawDivExpr(expr: JIRRawDivExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJIRRawMulExpr(expr: JIRRawMulExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJIRRawEqExpr(expr: JIRRawEqExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJIRRawNeqExpr(expr: JIRRawNeqExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJIRRawGeExpr(expr: JIRRawGeExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJIRRawGtExpr(expr: JIRRawGtExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJIRRawLeExpr(expr: JIRRawLeExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJIRRawLtExpr(expr: JIRRawLtExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJIRRawOrExpr(expr: JIRRawOrExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJIRRawRemExpr(expr: JIRRawRemExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJIRRawShlExpr(expr: JIRRawShlExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJIRRawShrExpr(expr: JIRRawShrExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJIRRawSubExpr(expr: JIRRawSubExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJIRRawUshrExpr(expr: JIRRawUshrExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJIRRawXorExpr(expr: JIRRawXorExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJIRRawLengthExpr(expr: JIRRawLengthExpr) {
        exprs.add(expr)
        expr.array.accept(this)
    }

    override fun visitJIRRawNegExpr(expr: JIRRawNegExpr) {
        exprs.add(expr)
        expr.operand.accept(this)
    }

    override fun visitJIRRawCastExpr(expr: JIRRawCastExpr) {
        exprs.add(expr)
        expr.operand.accept(this)
    }

    override fun visitJIRRawNewExpr(expr: JIRRawNewExpr) {
        exprs.add(expr)
    }

    override fun visitJIRRawNewArrayExpr(expr: JIRRawNewArrayExpr) {
        exprs.add(expr)
        expr.dimensions.forEach { it.accept(this) }
    }

    override fun visitJIRRawInstanceOfExpr(expr: JIRRawInstanceOfExpr) {
        exprs.add(expr)
        expr.operand.accept(this)
    }

    override fun visitJIRRawDynamicCallExpr(expr: JIRRawDynamicCallExpr) {
        exprs.add(expr)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJIRRawVirtualCallExpr(expr: JIRRawVirtualCallExpr) {
        exprs.add(expr)
        expr.instance.accept(this)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJIRRawInterfaceCallExpr(expr: JIRRawInterfaceCallExpr) {
        exprs.add(expr)
        expr.instance.accept(this)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJIRRawStaticCallExpr(expr: JIRRawStaticCallExpr) {
        exprs.add(expr)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJIRRawSpecialCallExpr(expr: JIRRawSpecialCallExpr) {
        exprs.add(expr)
        expr.instance.accept(this)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJIRRawThis(value: JIRRawThis) {
        exprs.add(value)
    }

    override fun visitJIRRawArgument(value: JIRRawArgument) {
        exprs.add(value)
    }

    override fun visitJIRRawLocalVar(value: JIRRawLocalVar) {
        exprs.add(value)
    }

    override fun visitJIRRawFieldRef(value: JIRRawFieldRef) {
        exprs.add(value)
        value.instance?.accept(this)
    }

    override fun visitJIRRawArrayAccess(value: JIRRawArrayAccess) {
        exprs.add(value)
        value.array.accept(this)
        value.index.accept(this)
    }

    override fun visitJIRRawBool(value: JIRRawBool) {
        exprs.add(value)
    }

    override fun visitJIRRawByte(value: JIRRawByte) {
        exprs.add(value)
    }

    override fun visitJIRRawChar(value: JIRRawChar) {
        exprs.add(value)
    }

    override fun visitJIRRawShort(value: JIRRawShort) {
        exprs.add(value)
    }

    override fun visitJIRRawInt(value: JIRRawInt) {
        exprs.add(value)
    }

    override fun visitJIRRawLong(value: JIRRawLong) {
        exprs.add(value)
    }

    override fun visitJIRRawFloat(value: JIRRawFloat) {
        exprs.add(value)
    }

    override fun visitJIRRawDouble(value: JIRRawDouble) {
        exprs.add(value)
    }

    override fun visitJIRRawNullConstant(value: JIRRawNullConstant) {
        exprs.add(value)
    }

    override fun visitJIRRawStringConstant(value: JIRRawStringConstant) {
        exprs.add(value)
    }

    override fun visitJIRRawClassConstant(value: JIRRawClassConstant) {
        exprs.add(value)
    }

    override fun visitJIRRawMethodConstant(value: JIRRawMethodConstant) {
        exprs.add(value)
    }
}

