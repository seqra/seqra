package org.opentaint.ir.impl.python.flat

/**
 * A single instruction inside a [FlatBlock].
 *
 * Each concrete subtype dispatches via [accept] to the matching `visit*`
 * method on [FlatInstVisitor]. Common shape queries — operand list, target
 * substitution — are implemented on top of the visitor: see
 * [FlatInst.targets], [FlatInst.mapOperand], and [FlatInst.mapTarget].
 */
sealed interface FlatInst {
    val line: Int
    fun <R> accept(visitor: FlatInstVisitor<R>): R
}

data class FlatAssign(val target: FlatValue, val source: FlatValue, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitAssign(this)
}
data class FlatLoadAttr(val target: FlatValue, val obj: FlatValue, val attribute: String, val type: FlatType = FlatAnyType, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitLoadAttr(this)
}
data class FlatStoreAttr(val obj: FlatValue, val attribute: String, val value: FlatValue, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitStoreAttr(this)
}
data class FlatLoadSubscript(val target: FlatValue, val obj: FlatValue, val index: FlatValue, val type: FlatType = FlatAnyType, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitLoadSubscript(this)
}
data class FlatStoreSubscript(val obj: FlatValue, val index: FlatValue, val value: FlatValue, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitStoreSubscript(this)
}
data class FlatLoadGlobal(val target: FlatValue, val name: String, val module: String, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitLoadGlobal(this)
}
data class FlatStoreGlobal(val name: String, val module: String, val value: FlatValue, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitStoreGlobal(this)
}
data class FlatBindFunction(
    val target: FlatValue,        // local that receives the bound function
    val function: FlatGlobalRef,  // synthetic global ref to the lifted function
    override val line: Int = -1,
) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitBindFunction(this)
}

data class FlatBinOp(val target: FlatValue, val left: FlatValue, val right: FlatValue, val op: FlatBinaryOperator, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitBinOp(this)
}
data class FlatUnaryOp(val target: FlatValue, val operand: FlatValue, val op: FlatUnaryOperator, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitUnaryOp(this)
}
data class FlatCompare(val target: FlatValue, val left: FlatValue, val right: FlatValue, val op: FlatCompareOperator, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitCompare(this)
}

data class FlatCall(val target: FlatValue?, val callee: FlatValue, val args: List<FlatCallArg> = emptyList(), val resolvedCallee: String? = null, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitCall(this)
}
data class FlatCallArg(val value: FlatValue, val kind: FlatArgKind = FlatArgKind.POSITIONAL, val keyword: String? = null)

data class FlatBuildList(val target: FlatValue, val elements: List<FlatValue> = emptyList(), override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitBuildList(this)
}
data class FlatBuildTuple(val target: FlatValue, val elements: List<FlatValue> = emptyList(), override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitBuildTuple(this)
}
data class FlatBuildSet(val target: FlatValue, val elements: List<FlatValue> = emptyList(), override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitBuildSet(this)
}
data class FlatBuildDict(val target: FlatValue, val keys: List<FlatValue> = emptyList(), val values: List<FlatValue> = emptyList(), override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitBuildDict(this)
}
data class FlatBuildSlice(val target: FlatValue, val lower: FlatValue?, val upper: FlatValue?, val step: FlatValue?, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitBuildSlice(this)
}
data class FlatBuildString(val target: FlatValue, val parts: List<FlatValue>, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitBuildString(this)
}

data class FlatGetIter(val target: FlatValue, val iterable: FlatValue, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitGetIter(this)
}
data class FlatNextIter(val target: FlatValue, val iterator: FlatValue, val bodyBlock: Int, val exitBlock: Int, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitNextIter(this)
}
data class FlatUnpack(val targets: List<FlatValue>, val source: FlatValue, val starIndex: Int, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitUnpack(this)
}

data class FlatGoto(val targetBlock: Int, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitGoto(this)
}
data class FlatBranch(val condition: FlatValue, val trueBlock: Int, val falseBlock: Int, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitBranch(this)
}
data class FlatReturn(val value: FlatValue?, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitReturn(this)
}
data class FlatRaise(val exception: FlatValue?, val cause: FlatValue?, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitRaise(this)
}
data class FlatExceptHandler(val target: FlatValue?, val exceptionTypes: List<FlatType>, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitExceptHandler(this)
}

data class FlatYield(val target: FlatValue?, val value: FlatValue?, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitYield(this)
}
data class FlatYieldFrom(val target: FlatValue?, val iterable: FlatValue, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitYieldFrom(this)
}
data class FlatAwait(val target: FlatValue?, val awaitable: FlatValue, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitAwait(this)
}

data class FlatDeleteLocal(val local: FlatValue, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitDeleteLocal(this)
}
data class FlatDeleteAttr(val obj: FlatValue, val attribute: String, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitDeleteAttr(this)
}
data class FlatDeleteSubscript(val obj: FlatValue, val index: FlatValue, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitDeleteSubscript(this)
}
data class FlatDeleteGlobal(val name: String, val module: String, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitDeleteGlobal(this)
}

data class FlatTypeCheck(val target: FlatValue, val value: FlatValue, val type: FlatType, override val line: Int = -1) : FlatInst {
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitTypeCheck(this)
}
data object FlatUnreachable : FlatInst {
    override val line: Int = -1
    override fun <R> accept(visitor: FlatInstVisitor<R>): R = visitor.visitUnreachable(this)
}

enum class FlatBinaryOperator { ADD, SUB, MUL, DIV, FLOOR_DIV, MOD, POW, MAT_MUL, BIT_AND, BIT_OR, BIT_XOR, LSHIFT, RSHIFT }
enum class FlatUnaryOperator { NEG, POS, NOT, INVERT }
enum class FlatCompareOperator { EQ, NE, LT, LE, GT, GE, IS, IS_NOT, IN, NOT_IN }
enum class FlatArgKind { POSITIONAL, KEYWORD, STAR, DOUBLE_STAR }

/** A single basic block: a label, a straight-line list of instructions, and the exception handlers in scope. */
data class FlatBlock(val label: Int, val instructions: List<FlatInst>, val exceptionHandlers: List<Int>)

/** The CFG of one function-like scope. */
data class FlatCFG(val blocks: List<FlatBlock>, val entryBlock: Int, val exitBlocks: List<Int>) {
    companion object {
        val EMPTY = FlatCFG(
            blocks = listOf(FlatBlock(0, listOf(FlatReturn(null)), emptyList())),
            entryBlock = 0,
            exitBlocks = listOf(0),
        )
    }
}
