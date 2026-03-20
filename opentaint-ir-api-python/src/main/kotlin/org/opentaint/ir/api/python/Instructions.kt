package org.opentaint.ir.api.python

/**
 * Base for all PIR instructions.
 * Analogous to JIRInst, but PIR folds expressions into instructions.
 * All instruction types are sealed (exhaustive when in Kotlin API module).
 */
sealed interface PIRInstruction {
    val lineNumber: Int
    val colOffset: Int
    fun <T> accept(visitor: PIRInstVisitor<T>): T
}

// ─── Assignment & Memory ────────────────────────────────────

data class PIRAssign(
    val target: PIRValue,
    val source: PIRValue,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitAssign(this)
}

data class PIRLoadAttr(
    val target: PIRValue,
    val obj: PIRValue,
    val attribute: String,
    val resultType: PIRType,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitLoadAttr(this)
}

data class PIRStoreAttr(
    val obj: PIRValue,
    val attribute: String,
    val value: PIRValue,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitStoreAttr(this)
}

data class PIRLoadSubscript(
    val target: PIRValue,
    val obj: PIRValue,
    val index: PIRValue,
    val resultType: PIRType,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitLoadSubscript(this)
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

data class PIRLoadGlobal(
    val target: PIRValue,
    val name: String,
    val module: String,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitLoadGlobal(this)
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

data class PIRLoadClosure(
    val target: PIRValue,
    val name: String,
    val depth: Int,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitLoadClosure(this)
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

// ─── Arithmetic & Comparison ────────────────────────────────

data class PIRBinOp(
    val target: PIRValue,
    val left: PIRValue,
    val right: PIRValue,
    val op: PIRBinaryOperator,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitBinOp(this)
}

enum class PIRBinaryOperator {
    ADD, SUB, MUL, DIV, FLOOR_DIV, MOD, POW, MAT_MUL,
    BIT_AND, BIT_OR, BIT_XOR, LSHIFT, RSHIFT,
}

data class PIRUnaryOp(
    val target: PIRValue,
    val operand: PIRValue,
    val op: PIRUnaryOperator,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitUnaryOp(this)
}

enum class PIRUnaryOperator { NEG, POS, NOT, INVERT }

data class PIRCompare(
    val target: PIRValue,
    val left: PIRValue,
    val right: PIRValue,
    val op: PIRCompareOperator,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitCompare(this)
}

enum class PIRCompareOperator {
    EQ, NE, LT, LE, GT, GE, IS, IS_NOT, IN, NOT_IN,
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

// ─── Collection Builders ────────────────────────────────────

data class PIRBuildList(
    val target: PIRValue,
    val elements: List<PIRValue>,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitBuildList(this)
}

data class PIRBuildTuple(
    val target: PIRValue,
    val elements: List<PIRValue>,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitBuildTuple(this)
}

data class PIRBuildSet(
    val target: PIRValue,
    val elements: List<PIRValue>,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitBuildSet(this)
}

data class PIRBuildDict(
    val target: PIRValue,
    val keys: List<PIRValue>,
    val values: List<PIRValue>,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitBuildDict(this)
}

data class PIRBuildSlice(
    val target: PIRValue,
    val lower: PIRValue?,
    val upper: PIRValue?,
    val step: PIRValue?,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitBuildSlice(this)
}

data class PIRBuildString(
    val target: PIRValue,
    val parts: List<PIRValue>,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitBuildString(this)
}

// ─── Iteration ──────────────────────────────────────────────

data class PIRGetIter(
    val target: PIRValue,
    val iterable: PIRValue,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitGetIter(this)
}

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

data class PIRTypeCheck(
    val target: PIRValue,
    val value: PIRValue,
    val checkType: PIRType,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitTypeCheck(this)
}

data object PIRUnreachable : PIRInstruction {
    override val lineNumber: Int = -1
    override val colOffset: Int = -1
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitUnreachable(this)
}
