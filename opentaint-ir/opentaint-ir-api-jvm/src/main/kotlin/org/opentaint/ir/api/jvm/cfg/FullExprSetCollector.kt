package org.opentaint.ir.api.jvm.cfg

import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.common.cfg.CommonInst

abstract class AbstractFullRawExprSetCollector :
    JIRRawExprVisitor<Unit>,
    JIRRawInstVisitor.Default<Unit> {

    override fun defaultVisitJIRRawInst(inst: JIRRawInst) {
        inst.operands.forEach {
            ifMatches(it)
            it.accept(this)
        }
    }

    private fun visitBinaryExpr(expr: JIRRawBinaryExpr) {
        ifMatches(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    private fun visitCallExpr(expr: JIRRawCallExpr) {
        ifMatches(expr)
        if (expr is JIRRawInstanceExpr) {
            expr.instance.accept(this)
        }
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJIRRawAddExpr(expr: JIRRawAddExpr) = visitBinaryExpr(expr)
    override fun visitJIRRawAndExpr(expr: JIRRawAndExpr) = visitBinaryExpr(expr)
    override fun visitJIRRawCmpExpr(expr: JIRRawCmpExpr) = visitBinaryExpr(expr)
    override fun visitJIRRawCmpgExpr(expr: JIRRawCmpgExpr) = visitBinaryExpr(expr)
    override fun visitJIRRawCmplExpr(expr: JIRRawCmplExpr) = visitBinaryExpr(expr)
    override fun visitJIRRawDivExpr(expr: JIRRawDivExpr) = visitBinaryExpr(expr)
    override fun visitJIRRawMulExpr(expr: JIRRawMulExpr) = visitBinaryExpr(expr)
    override fun visitJIRRawEqExpr(expr: JIRRawEqExpr) = visitBinaryExpr(expr)
    override fun visitJIRRawNeqExpr(expr: JIRRawNeqExpr) = visitBinaryExpr(expr)
    override fun visitJIRRawGeExpr(expr: JIRRawGeExpr) = visitBinaryExpr(expr)
    override fun visitJIRRawGtExpr(expr: JIRRawGtExpr) = visitBinaryExpr(expr)
    override fun visitJIRRawLeExpr(expr: JIRRawLeExpr) = visitBinaryExpr(expr)
    override fun visitJIRRawLtExpr(expr: JIRRawLtExpr) = visitBinaryExpr(expr)
    override fun visitJIRRawOrExpr(expr: JIRRawOrExpr) = visitBinaryExpr(expr)
    override fun visitJIRRawRemExpr(expr: JIRRawRemExpr) = visitBinaryExpr(expr)
    override fun visitJIRRawShlExpr(expr: JIRRawShlExpr) = visitBinaryExpr(expr)
    override fun visitJIRRawShrExpr(expr: JIRRawShrExpr) = visitBinaryExpr(expr)
    override fun visitJIRRawSubExpr(expr: JIRRawSubExpr) = visitBinaryExpr(expr)
    override fun visitJIRRawUshrExpr(expr: JIRRawUshrExpr) = visitBinaryExpr(expr)
    override fun visitJIRRawXorExpr(expr: JIRRawXorExpr) = visitBinaryExpr(expr)

    override fun visitJIRRawLengthExpr(expr: JIRRawLengthExpr) {
        ifMatches(expr)
        expr.array.accept(this)
    }

    override fun visitJIRRawNegExpr(expr: JIRRawNegExpr) {
        ifMatches(expr)
        expr.operand.accept(this)
    }

    override fun visitJIRRawCastExpr(expr: JIRRawCastExpr) {
        ifMatches(expr)
        expr.operand.accept(this)
    }

    override fun visitJIRRawNewExpr(expr: JIRRawNewExpr) {
        ifMatches(expr)
    }

    override fun visitJIRRawNewArrayExpr(expr: JIRRawNewArrayExpr) {
        ifMatches(expr)
        expr.dimensions.forEach { it.accept(this) }
    }

    override fun visitJIRRawInstanceOfExpr(expr: JIRRawInstanceOfExpr) {
        ifMatches(expr)
        expr.operand.accept(this)
    }

    override fun visitJIRRawDynamicCallExpr(expr: JIRRawDynamicCallExpr) {
        ifMatches(expr)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJIRRawVirtualCallExpr(expr: JIRRawVirtualCallExpr) = visitCallExpr(expr)
    override fun visitJIRRawInterfaceCallExpr(expr: JIRRawInterfaceCallExpr) = visitCallExpr(expr)
    override fun visitJIRRawStaticCallExpr(expr: JIRRawStaticCallExpr) = visitCallExpr(expr)
    override fun visitJIRRawSpecialCallExpr(expr: JIRRawSpecialCallExpr) = visitCallExpr(expr)
    override fun visitJIRRawThis(value: JIRRawThis) = ifMatches(value)
    override fun visitJIRRawArgument(value: JIRRawArgument) = ifMatches(value)
    override fun visitJIRRawLocalVar(value: JIRRawLocalVar) = ifMatches(value)

    override fun visitJIRRawFieldRef(value: JIRRawFieldRef) {
        ifMatches(value)
        value.instance?.accept(this)
    }

    override fun visitJIRRawArrayAccess(value: JIRRawArrayAccess) {
        ifMatches(value)
        value.array.accept(this)
        value.index.accept(this)
    }

    override fun visitJIRRawBool(value: JIRRawBool) = ifMatches(value)
    override fun visitJIRRawByte(value: JIRRawByte) = ifMatches(value)
    override fun visitJIRRawChar(value: JIRRawChar) = ifMatches(value)
    override fun visitJIRRawShort(value: JIRRawShort) = ifMatches(value)
    override fun visitJIRRawInt(value: JIRRawInt) = ifMatches(value)
    override fun visitJIRRawLong(value: JIRRawLong) = ifMatches(value)
    override fun visitJIRRawFloat(value: JIRRawFloat) = ifMatches(value)
    override fun visitJIRRawDouble(value: JIRRawDouble) = ifMatches(value)
    override fun visitJIRRawNullConstant(value: JIRRawNullConstant) = ifMatches(value)
    override fun visitJIRRawStringConstant(value: JIRRawStringConstant) = ifMatches(value)
    override fun visitJIRRawClassConstant(value: JIRRawClassConstant) = ifMatches(value)
    override fun visitJIRRawMethodConstant(value: JIRRawMethodConstant) = ifMatches(value)
    override fun visitJIRRawMethodType(value: JIRRawMethodType) = ifMatches(value)

    abstract fun ifMatches(expr: JIRRawExpr)
}

abstract class AbstractFullExprSetCollector :
    JIRExprVisitor.Default<Any>,
    JIRInstVisitor.Default<Any> {

    override fun defaultVisitCommonExpr(expr: CommonExpr): Any {
        TODO("Not yet implemented")
    }

    override fun defaultVisitCommonInst(inst: CommonInst<*, *>) {
        TODO("Not yet implemented")
    }

    override fun defaultVisitJIRExpr(expr: JIRExpr) {
        ifMatches(expr)
    }

    override fun defaultVisitJIRInst(inst: JIRInst) {
        inst.operands.forEach {
            ifMatches(it)
            it.accept(this)
        }
    }

    private fun visitBinaryExpr(expr: JIRBinaryExpr) {
        ifMatches(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    private fun visitCallExpr(expr: JIRCallExpr) {
        ifMatches(expr)
        if (expr is JIRInstanceCallExpr) {
            expr.instance.accept(this)
        }
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJIRAddExpr(expr: JIRAddExpr): Any = visitBinaryExpr(expr)
    override fun visitJIRAndExpr(expr: JIRAndExpr): Any = visitBinaryExpr(expr)
    override fun visitJIRCmpExpr(expr: JIRCmpExpr): Any = visitBinaryExpr(expr)
    override fun visitJIRCmpgExpr(expr: JIRCmpgExpr): Any = visitBinaryExpr(expr)
    override fun visitJIRCmplExpr(expr: JIRCmplExpr): Any = visitBinaryExpr(expr)
    override fun visitJIRDivExpr(expr: JIRDivExpr): Any = visitBinaryExpr(expr)
    override fun visitJIRMulExpr(expr: JIRMulExpr): Any = visitBinaryExpr(expr)
    override fun visitJIREqExpr(expr: JIREqExpr): Any = visitBinaryExpr(expr)
    override fun visitJIRNeqExpr(expr: JIRNeqExpr): Any = visitBinaryExpr(expr)
    override fun visitJIRGeExpr(expr: JIRGeExpr): Any = visitBinaryExpr(expr)
    override fun visitJIRGtExpr(expr: JIRGtExpr): Any = visitBinaryExpr(expr)
    override fun visitJIRLeExpr(expr: JIRLeExpr): Any = visitBinaryExpr(expr)
    override fun visitJIRLtExpr(expr: JIRLtExpr): Any = visitBinaryExpr(expr)
    override fun visitJIROrExpr(expr: JIROrExpr): Any = visitBinaryExpr(expr)
    override fun visitJIRRemExpr(expr: JIRRemExpr): Any = visitBinaryExpr(expr)
    override fun visitJIRShlExpr(expr: JIRShlExpr): Any = visitBinaryExpr(expr)
    override fun visitJIRShrExpr(expr: JIRShrExpr): Any = visitBinaryExpr(expr)
    override fun visitJIRSubExpr(expr: JIRSubExpr): Any = visitBinaryExpr(expr)
    override fun visitJIRUshrExpr(expr: JIRUshrExpr): Any = visitBinaryExpr(expr)
    override fun visitJIRXorExpr(expr: JIRXorExpr): Any = visitBinaryExpr(expr)

    override fun visitJIRLambdaExpr(expr: JIRLambdaExpr): Any {
        ifMatches(expr)
        expr.args.forEach { it.accept(this) }
        return Unit
    }

    override fun visitJIRLengthExpr(expr: JIRLengthExpr): Any {
        ifMatches(expr)
        expr.array.accept(this)
        return Unit
    }

    override fun visitJIRNegExpr(expr: JIRNegExpr): Any {
        ifMatches(expr)
        expr.operand.accept(this)
        return Unit
    }

    override fun visitJIRCastExpr(expr: JIRCastExpr): Any {
        ifMatches(expr)
        expr.operand.accept(this)
        return Unit
    }

    override fun visitJIRNewExpr(expr: JIRNewExpr): Any {
        ifMatches(expr)
        return Unit
    }

    override fun visitJIRNewArrayExpr(expr: JIRNewArrayExpr): Any {
        ifMatches(expr)
        expr.dimensions.forEach { it.accept(this) }
        return Unit
    }

    override fun visitJIRInstanceOfExpr(expr: JIRInstanceOfExpr): Any {
        ifMatches(expr)
        expr.operand.accept(this)
        return Unit
    }

    override fun visitJIRDynamicCallExpr(expr: JIRDynamicCallExpr): Any {
        ifMatches(expr)
        expr.args.forEach { it.accept(this) }
        return Unit
    }

    override fun visitJIRVirtualCallExpr(expr: JIRVirtualCallExpr): Any = visitCallExpr(expr)
    override fun visitJIRStaticCallExpr(expr: JIRStaticCallExpr): Any = visitCallExpr(expr)
    override fun visitJIRSpecialCallExpr(expr: JIRSpecialCallExpr): Any = visitCallExpr(expr)
    override fun visitJIRThis(value: JIRThis): Any = ifMatches(value)
    override fun visitJIRArgument(value: JIRArgument): Any = ifMatches(value)
    override fun visitJIRLocalVar(value: JIRLocalVar): Any = ifMatches(value)

    override fun visitJIRFieldRef(value: JIRFieldRef): Any {
        ifMatches(value)
        value.instance?.accept(this)
        return Unit
    }

    override fun visitJIRArrayAccess(value: JIRArrayAccess): Any {
        ifMatches(value)
        value.array.accept(this)
        value.index.accept(this)
        return Unit
    }

    override fun visitJIRPhiExpr(expr: JIRPhiExpr): Any {
        ifMatches(expr)
        expr.args.forEach { it.accept(this) }
        expr.values.forEach { it.accept(this) }
        return Unit
    }

    abstract fun ifMatches(expr: JIRExpr)
}
