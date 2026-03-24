package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFactMapper
import org.opentaint.dataflow.ap.ifds.taint.TaintAnalysisContext
import org.opentaint.ir.api.python.*

class PIRMethodAnalysisContext(
    override val methodEntryPoint: MethodEntryPoint,
    val method: PIRFunction,
    val taint: TaintAnalysisContext,
) : MethodAnalysisContext {

    override val methodCallFactMapper: MethodCallFactMapper
        get() = PIRMethodCallFactMapper(this)

    /** Map from PIRLocal names to integer indices (for AccessPathBase.LocalVar) */
    val localNameToIndex: Map<String, Int>

    /** Reverse map from index to local name */
    val indexToLocalName: Map<Int, String>

    init {
        val names = linkedSetOf<String>()
        val collector = LocalNameCollector(names)
        for (block in method.cfg.blocks) {
            for (inst in block.instructions) {
                inst.accept(collector)
            }
        }
        localNameToIndex = names.withIndex().associate { (idx, name) -> name to idx }
        indexToLocalName = localNameToIndex.entries.associate { (name, idx) -> idx to name }
    }

    fun localIndex(name: String): Int =
        localNameToIndex[name] ?: error("Unknown local: $name in ${method.qualifiedName}")

    fun localName(index: Int): String =
        indexToLocalName[index] ?: error("Unknown index: $index in ${method.qualifiedName}")
}

/**
 * Collects PIRLocal names from all instructions using the visitor pattern.
 * Uses PIRInstVisitor.Default so new instruction types are handled safely
 * (they fall through to defaultVisit → no-op).
 */
private class LocalNameCollector(
    private val names: MutableSet<String>,
) : PIRInstVisitor.Default<Unit> {

    private val valueVisitor = object : PIRValueVisitor.Default<Unit> {
        override fun defaultVisitValue(value: PIRValue) = Unit
        override fun visitLocal(value: PIRLocal) { names.add(value.name) }
    }

    private fun visit(value: PIRValue) = value.accept(valueVisitor)
    private fun visitNullable(value: PIRValue?) { value?.accept(valueVisitor) }

    private fun visitExpr(expr: PIRExpr) {
        when (expr) {
            is PIRValue -> visit(expr)
            is PIRBinExpr -> { visit(expr.left); visit(expr.right) }
            is PIRUnaryExpr -> visit(expr.operand)
            is PIRCompareExpr -> { visit(expr.left); visit(expr.right) }
            is PIRAttrExpr -> visit(expr.obj)
            is PIRSubscriptExpr -> { visit(expr.obj); visit(expr.index) }
            is PIRListExpr -> expr.elements.forEach(::visit)
            is PIRTupleExpr -> expr.elements.forEach(::visit)
            is PIRSetExpr -> expr.elements.forEach(::visit)
            is PIRDictExpr -> {
                expr.keys.forEach(::visit)
                expr.values.forEach(::visit)
            }
            is PIRStringExpr -> expr.parts.forEach(::visit)
            is PIRIterExpr -> visit(expr.iterable)
            is PIRSliceExpr -> { expr.lower?.let(::visit); expr.upper?.let(::visit); expr.step?.let(::visit) }
            is PIRTypeCheckExpr -> visit(expr.value)
        }
    }

    override fun defaultVisit(inst: PIRInstruction) = Unit

    override fun visitAssign(inst: PIRAssign) { visit(inst.target); visitExpr(inst.expr) }
    override fun visitCall(inst: PIRCall) {
        visitNullable(inst.target); visit(inst.callee)
        inst.args.forEach { visit(it.value) }
    }
    override fun visitReturn(inst: PIRReturn) { visitNullable(inst.value) }
    override fun visitBranch(inst: PIRBranch) { visit(inst.condition) }
    override fun visitStoreAttr(inst: PIRStoreAttr) { visit(inst.obj); visit(inst.value) }
    override fun visitStoreSubscript(inst: PIRStoreSubscript) {
        visit(inst.obj); visit(inst.index); visit(inst.value)
    }
    override fun visitUnpack(inst: PIRUnpack) {
        inst.targets.forEach(::visit); visit(inst.source)
    }
    override fun visitYield(inst: PIRYield) { visitNullable(inst.target); visitNullable(inst.value) }
    override fun visitYieldFrom(inst: PIRYieldFrom) { visitNullable(inst.target); visit(inst.iterable) }
    override fun visitAwait(inst: PIRAwait) { visitNullable(inst.target); visit(inst.awaitable) }
    override fun visitDeleteLocal(inst: PIRDeleteLocal) { visit(inst.local) }
    override fun visitNextIter(inst: PIRNextIter) { visit(inst.target); visit(inst.iterator) }
    override fun visitRaise(inst: PIRRaise) { visitNullable(inst.exception); visitNullable(inst.cause) }
    override fun visitStoreGlobal(inst: PIRStoreGlobal) { visit(inst.value) }
    override fun visitStoreClosure(inst: PIRStoreClosure) { visit(inst.value) }
    override fun visitDeleteAttr(inst: PIRDeleteAttr) { visit(inst.obj) }
    override fun visitDeleteSubscript(inst: PIRDeleteSubscript) { visit(inst.obj); visit(inst.index) }
}
