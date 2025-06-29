package org.opentaint.ir.api.jvm.cfg

abstract class AbstractFullRawExprSetCollector :
    JIRRawExprVisitor.Default<Any>,
    JIRRawInstVisitor.Default<Any> {

    abstract fun ifMatches(expr: JIRRawExpr)

    override fun defaultVisitJIRRawExpr(expr: JIRRawExpr) {
        ifMatches(expr)
        expr.operands.forEach {
            it.accept(this)
        }
    }

    override fun defaultVisitJIRRawInst(inst: JIRRawInst) {
        inst.operands.forEach {
            it.accept(this)
        }
    }
}

abstract class AbstractFullExprSetCollector :
    JIRExprVisitor.Default<Any>,
    JIRInstVisitor.Default<Any> {

    abstract fun ifMatches(expr: JIRExpr)

    override fun defaultVisitJIRExpr(expr: JIRExpr) {
        ifMatches(expr)
        expr.operands.forEach {
            it.accept(this)
        }
    }

    override fun defaultVisitJIRInst(inst: JIRInst) {
        inst.operands.forEach {
            it.accept(this)
        }
    }
}
