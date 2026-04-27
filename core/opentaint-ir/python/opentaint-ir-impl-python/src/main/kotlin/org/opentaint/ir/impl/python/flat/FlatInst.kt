package org.opentaint.ir.impl.python.flat

/** A single instruction inside a [FlatBlock]. */
sealed interface FlatInst {
    val line: Int
}

data class FlatAssign(val target: FlatValue, val source: FlatValue, override val line: Int = -1) : FlatInst
data class FlatLoadAttr(val target: FlatValue, val obj: FlatValue, val attribute: String, val type: FlatType = FlatAnyType, override val line: Int = -1) : FlatInst
data class FlatStoreAttr(val obj: FlatValue, val attribute: String, val value: FlatValue, override val line: Int = -1) : FlatInst
data class FlatLoadSubscript(val target: FlatValue, val obj: FlatValue, val index: FlatValue, val type: FlatType = FlatAnyType, override val line: Int = -1) : FlatInst
data class FlatStoreSubscript(val obj: FlatValue, val index: FlatValue, val value: FlatValue, override val line: Int = -1) : FlatInst
data class FlatLoadGlobal(val target: FlatValue, val name: String, val module: String, override val line: Int = -1) : FlatInst
data class FlatStoreGlobal(val name: String, val module: String, val value: FlatValue, override val line: Int = -1) : FlatInst
data class FlatLoadClosure(val target: FlatValue, val name: String, val depth: Int, override val line: Int = -1) : FlatInst
data class FlatStoreClosure(val name: String, val depth: Int, val value: FlatValue, override val line: Int = -1) : FlatInst

data class FlatBinOp(val target: FlatValue, val left: FlatValue, val right: FlatValue, val op: FlatBinaryOperator, override val line: Int = -1) : FlatInst
data class FlatUnaryOp(val target: FlatValue, val operand: FlatValue, val op: FlatUnaryOperator, override val line: Int = -1) : FlatInst
data class FlatCompare(val target: FlatValue, val left: FlatValue, val right: FlatValue, val op: FlatCompareOperator, override val line: Int = -1) : FlatInst

data class FlatCall(val target: FlatValue?, val callee: FlatValue, val args: List<FlatCallArg> = emptyList(), val resolvedCallee: String? = null, override val line: Int = -1) : FlatInst
data class FlatCallArg(val value: FlatValue, val kind: FlatArgKind = FlatArgKind.POSITIONAL, val keyword: String? = null)

data class FlatBuildList(val target: FlatValue, val elements: List<FlatValue> = emptyList(), override val line: Int = -1) : FlatInst
data class FlatBuildTuple(val target: FlatValue, val elements: List<FlatValue> = emptyList(), override val line: Int = -1) : FlatInst
data class FlatBuildSet(val target: FlatValue, val elements: List<FlatValue> = emptyList(), override val line: Int = -1) : FlatInst
data class FlatBuildDict(val target: FlatValue, val keys: List<FlatValue> = emptyList(), val values: List<FlatValue> = emptyList(), override val line: Int = -1) : FlatInst
data class FlatBuildSlice(val target: FlatValue, val lower: FlatValue?, val upper: FlatValue?, val step: FlatValue?, override val line: Int = -1) : FlatInst
data class FlatBuildString(val target: FlatValue, val parts: List<FlatValue>, override val line: Int = -1) : FlatInst

data class FlatGetIter(val target: FlatValue, val iterable: FlatValue, override val line: Int = -1) : FlatInst
data class FlatNextIter(val target: FlatValue, val iterator: FlatValue, val bodyBlock: Int, val exitBlock: Int, override val line: Int = -1) : FlatInst
data class FlatUnpack(val targets: List<FlatValue>, val source: FlatValue, val starIndex: Int, override val line: Int = -1) : FlatInst

data class FlatGoto(val targetBlock: Int, override val line: Int = -1) : FlatInst
data class FlatBranch(val condition: FlatValue, val trueBlock: Int, val falseBlock: Int, override val line: Int = -1) : FlatInst
data class FlatReturn(val value: FlatValue?, override val line: Int = -1) : FlatInst
data class FlatRaise(val exception: FlatValue?, val cause: FlatValue?, override val line: Int = -1) : FlatInst
data class FlatExceptHandler(val target: FlatValue?, val exceptionTypes: List<FlatType>, override val line: Int = -1) : FlatInst

data class FlatYield(val target: FlatValue?, val value: FlatValue?, override val line: Int = -1) : FlatInst
data class FlatYieldFrom(val target: FlatValue?, val iterable: FlatValue, override val line: Int = -1) : FlatInst
data class FlatAwait(val target: FlatValue?, val awaitable: FlatValue, override val line: Int = -1) : FlatInst

data class FlatDeleteLocal(val local: FlatValue, override val line: Int = -1) : FlatInst
data class FlatDeleteAttr(val obj: FlatValue, val attribute: String, override val line: Int = -1) : FlatInst
data class FlatDeleteSubscript(val obj: FlatValue, val index: FlatValue, override val line: Int = -1) : FlatInst
data class FlatDeleteGlobal(val name: String, val module: String, override val line: Int = -1) : FlatInst

data class FlatTypeCheck(val target: FlatValue, val value: FlatValue, val type: FlatType, override val line: Int = -1) : FlatInst
data object FlatUnreachable : FlatInst {
    override val line: Int = -1
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
