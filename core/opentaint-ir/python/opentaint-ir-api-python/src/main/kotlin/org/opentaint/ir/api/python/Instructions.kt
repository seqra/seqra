package org.opentaint.ir.api.python

/**
 * Base for all PIR instructions.
 */
sealed interface PIRInstruction {
    val lineNumber: Int
    val colOffset: Int
    fun <T> accept(visitor: PIRInstVisitor<T>): T
}

/**
 * Base for all PIR expressions (right-hand sides of assignments).
 * [PIRValue] subtypes (locals, constants, refs) are also expressions.
 * Compound expressions (binary ops, comparisons, attribute loads, etc.) extend this.
 */
sealed interface PIRExpr

// ─── Assignment (target = expr) ─────────────────────────────

data class PIRAssign(
    val target: PIRValue,
    val expr: PIRExpr,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    /** Backwards-compat: the source value when the expression is a simple value copy. */
    val source: PIRValue get() = expr as PIRValue
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitAssign(this)
}



// ─── Compound Expressions ───────────────────────────────────

data class PIRBinExpr(
    val left: PIRValue,
    val right: PIRValue,
    val op: PIRBinaryOperator,
) : PIRExpr

data class PIRUnaryExpr(
    val operand: PIRValue,
    val op: PIRUnaryOperator,
) : PIRExpr

data class PIRCompareExpr(
    val left: PIRValue,
    val right: PIRValue,
    val op: PIRCompareOperator,
) : PIRExpr

data class PIRAttrExpr(
    val obj: PIRValue,
    val attribute: String,
    val resultType: PIRType = PIRAnyType,
) : PIRExpr

data class PIRSubscriptExpr(
    val obj: PIRValue,
    val index: PIRValue,
    val resultType: PIRType = PIRAnyType,
) : PIRExpr

data class PIRListExpr(val elements: List<PIRValue>) : PIRExpr
data class PIRTupleExpr(val elements: List<PIRValue>) : PIRExpr
data class PIRSetExpr(val elements: List<PIRValue>) : PIRExpr
data class PIRDictExpr(val keys: List<PIRValue>, val values: List<PIRValue>) : PIRExpr
data class PIRSliceExpr(val lower: PIRValue?, val upper: PIRValue?, val step: PIRValue?) : PIRExpr
data class PIRStringExpr(val parts: List<PIRValue>) : PIRExpr
data class PIRIterExpr(val iterable: PIRValue) : PIRExpr
data class PIRTypeCheckExpr(val value: PIRValue, val checkType: PIRType) : PIRExpr

enum class PIRBinaryOperator {
    ADD, SUB, MUL, DIV, FLOOR_DIV, MOD, POW, MAT_MUL,
    BIT_AND, BIT_OR, BIT_XOR, LSHIFT, RSHIFT,
}

enum class PIRUnaryOperator { NEG, POS, NOT, INVERT }

enum class PIRCompareOperator {
    EQ, NE, LT, LE, GT, GE, IS, IS_NOT, IN, NOT_IN,
}

// ─── Memory Store (side-effecting, no result) ───────────────

data class PIRStoreAttr(
    val obj: PIRValue,
    val attribute: String,
    val value: PIRValue,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitStoreAttr(this)
}

data class PIRStoreSubscript(
    val obj: PIRValue,
    val index: PIRValue,
    val value: PIRValue,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitStoreSubscript(this)
}

data class PIRStoreGlobal(
    val name: String,
    val module: String,
    val value: PIRValue,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitStoreGlobal(this)
}

data class PIRStoreClosure(
    val name: String,
    val depth: Int,
    val value: PIRValue,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitStoreClosure(this)
}

// ─── Call ───────────────────────────────────────────────────

data class PIRCall(
    val target: PIRValue?,
    val callee: PIRValue,
    val args: List<PIRCallArg>,
    val resolvedCallee: String? = null,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitCall(this)
}

data class PIRCallArg(
    val value: PIRValue,
    val kind: PIRCallArgKind,
    val keyword: String? = null,
)

enum class PIRCallArgKind { POSITIONAL, KEYWORD, STAR, DOUBLE_STAR }

// ─── Iteration ──────────────────────────────────────────────

data class PIRNextIter(
    val target: PIRValue,
    val iterator: PIRValue,
    val bodyBlock: Int,
    val exitBlock: Int,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitNextIter(this)
}

data class PIRUnpack(
    val targets: List<PIRValue>,
    val source: PIRValue,
    val starIndex: Int = -1,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitUnpack(this)
}

// ─── Control Flow (Terminators) ─────────────────────────────

data class PIRGoto(
    val targetBlock: Int,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitGoto(this)
}

data class PIRBranch(
    val condition: PIRValue,
    val trueBlock: Int,
    val falseBlock: Int,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitBranch(this)
}

data class PIRReturn(
    val value: PIRValue?,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitReturn(this)
}

data class PIRRaise(
    val exception: PIRValue?,
    val cause: PIRValue?,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitRaise(this)
}

data class PIRExceptHandler(
    val target: PIRValue?,
    val exceptionTypes: List<PIRType>,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitExceptHandler(this)
}

// ─── Generators & Async ─────────────────────────────────────

data class PIRYield(
    val target: PIRValue?,
    val value: PIRValue?,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitYield(this)
}

data class PIRYieldFrom(
    val target: PIRValue?,
    val iterable: PIRValue,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitYieldFrom(this)
}

data class PIRAwait(
    val target: PIRValue?,
    val awaitable: PIRValue,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitAwait(this)
}

// ─── Delete ─────────────────────────────────────────────────

data class PIRDeleteLocal(
    val local: PIRValue,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitDeleteLocal(this)
}

data class PIRDeleteAttr(
    val obj: PIRValue,
    val attribute: String,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitDeleteAttr(this)
}

data class PIRDeleteSubscript(
    val obj: PIRValue,
    val index: PIRValue,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitDeleteSubscript(this)
}

data class PIRDeleteGlobal(
    val name: String,
    val module: String,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitDeleteGlobal(this)
}

// ─── Misc ───────────────────────────────────────────────────

data object PIRUnreachable : PIRInstruction {
    override val lineNumber: Int = -1
    override val colOffset: Int = -1
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitUnreachable(this)
}

// ─── Helper extensions for checking expression types ────────

/** Check if this instruction is an assignment with a specific expression type. */
inline fun <reified E : PIRExpr> PIRInstruction.isAssignOf(): Boolean =
    this is PIRAssign && this.expr is E

/** Get the expression from an assignment, cast to the expected type. Returns null if not matching. */
inline fun <reified E : PIRExpr> PIRInstruction.assignExprOrNull(): E? =
    (this as? PIRAssign)?.expr as? E

/** Filter instructions for assignments with a specific expression type. */
inline fun <reified E : PIRExpr> Iterable<PIRInstruction>.filterAssignOf(): List<PIRAssign> =
    filterIsInstance<PIRAssign>().filter { it.expr is E }

// ─── Typed accessor extensions for PIRAssign ────────────────
// These allow `assign.binExpr`, `assign.compareExpr` etc. for convenient access.

val PIRAssign.binExpr: PIRBinExpr get() = expr as PIRBinExpr
val PIRAssign.unaryExpr: PIRUnaryExpr get() = expr as PIRUnaryExpr
val PIRAssign.compareExpr: PIRCompareExpr get() = expr as PIRCompareExpr
val PIRAssign.attrExpr: PIRAttrExpr get() = expr as PIRAttrExpr
val PIRAssign.subscriptExpr: PIRSubscriptExpr get() = expr as PIRSubscriptExpr
val PIRAssign.listExpr: PIRListExpr get() = expr as PIRListExpr
val PIRAssign.tupleExpr: PIRTupleExpr get() = expr as PIRTupleExpr
val PIRAssign.setExpr: PIRSetExpr get() = expr as PIRSetExpr
val PIRAssign.dictExpr: PIRDictExpr get() = expr as PIRDictExpr
val PIRAssign.sliceExpr: PIRSliceExpr get() = expr as PIRSliceExpr
val PIRAssign.stringExpr: PIRStringExpr get() = expr as PIRStringExpr
val PIRAssign.iterExpr: PIRIterExpr get() = expr as PIRIterExpr
val PIRAssign.typeCheckExpr: PIRTypeCheckExpr get() = expr as PIRTypeCheckExpr
