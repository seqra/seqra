package org.opentaint.ir.impl.python.flat

/**
 * Visitor over [FlatInst]. Every concrete instruction has a `visit*` method;
 * dispatch goes through [FlatInst.accept], which each instruction implements
 * by invoking the matching method. Adding a new [FlatInst] kind requires
 * adding one method here and one `accept` override on the new type — the
 * compiler will then flag every implementation that has not handled it.
 *
 * Most utility passes don't need a visitor directly — see [FlatInst.target],
 * [FlatInst.mapOperand], and [FlatInst.withTarget] for the common shape
 * queries, all implemented on top of this visitor.
 *
 * Visitors must NOT walk operands recursively here; operand walks are
 * driven externally by composing visitors with these helpers.
 */
interface FlatInstVisitor<R> {
    fun visitAssign(inst: FlatAssign): R
    fun visitLoadAttr(inst: FlatLoadAttr): R
    fun visitStoreAttr(inst: FlatStoreAttr): R
    fun visitLoadSubscript(inst: FlatLoadSubscript): R
    fun visitStoreSubscript(inst: FlatStoreSubscript): R
    fun visitLoadGlobal(inst: FlatLoadGlobal): R
    fun visitStoreGlobal(inst: FlatStoreGlobal): R
    fun visitBindFunction(inst: FlatBindFunction): R
    fun visitBinOp(inst: FlatBinOp): R
    fun visitUnaryOp(inst: FlatUnaryOp): R
    fun visitCompare(inst: FlatCompare): R
    fun visitCall(inst: FlatCall): R
    fun visitBuildList(inst: FlatBuildList): R
    fun visitBuildTuple(inst: FlatBuildTuple): R
    fun visitBuildSet(inst: FlatBuildSet): R
    fun visitBuildDict(inst: FlatBuildDict): R
    fun visitBuildSlice(inst: FlatBuildSlice): R
    fun visitBuildString(inst: FlatBuildString): R
    fun visitGetIter(inst: FlatGetIter): R
    fun visitNextIter(inst: FlatNextIter): R
    fun visitUnpack(inst: FlatUnpack): R
    fun visitGoto(inst: FlatGoto): R
    fun visitBranch(inst: FlatBranch): R
    fun visitReturn(inst: FlatReturn): R
    fun visitRaise(inst: FlatRaise): R
    fun visitExceptHandler(inst: FlatExceptHandler): R
    fun visitYield(inst: FlatYield): R
    fun visitYieldFrom(inst: FlatYieldFrom): R
    fun visitAwait(inst: FlatAwait): R
    fun visitDeleteLocal(inst: FlatDeleteLocal): R
    fun visitDeleteAttr(inst: FlatDeleteAttr): R
    fun visitDeleteSubscript(inst: FlatDeleteSubscript): R
    fun visitDeleteGlobal(inst: FlatDeleteGlobal): R
    fun visitTypeCheck(inst: FlatTypeCheck): R
    fun visitUnreachable(inst: FlatUnreachable): R
}
