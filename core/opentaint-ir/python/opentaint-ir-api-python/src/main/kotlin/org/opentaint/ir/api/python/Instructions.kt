package org.opentaint.ir.api.python

import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonInstLocation

interface PIRLocation : CommonInstLocation {
    override val method: PIRFunction
    val index: Int
    val lineNumber: Int
    val colOffset: Int
}

/**
 * Base for all PIR instructions.
 */
sealed interface PIRInstruction: CommonInst {
    fun <T> accept(visitor: PIRInstVisitor<T>): T

    override var location: PIRLocation

    val lineNumber: Int get() = location.lineNumber
    val colOffset: Int get() = location.colOffset

    /**
     * PIR instruction data classes must use identity-based equality, not structural equality.
     * Reason: data class equals/hashCode uses only constructor params, but `location` (which
     * carries the owning method + index) is a lateinit body property excluded from those.
     * Without this, structurally identical instructions from different methods (e.g. two
     * `PIRReturn(value=null)`) are considered equal, causing the dataflow engine to confuse
     * their MethodEntryPoints and store summaries in the wrong AccessPathBaseStorage.
     */
    // Implementors: override equals/hashCode to use identity (=== / System.identityHashCode)
}

sealed interface PIRBranchingInst : PIRInstruction {
    val successors: List<Int>
    val blockSuccessors: List<Int>
}

sealed interface PIRTerminatingInst : PIRInstruction

/**
 * Base for all PIR expressions (right-hand sides of assignments).
 * [PIRValue] subtypes (locals, constants, refs) are also expressions.
 * Compound expressions (binary ops, comparisons, attribute loads, etc.) extend this.
 */
sealed interface PIRExpr : org.opentaint.ir.api.common.cfg.CommonExpr {
    override val typeName: String get() = "expr"
}

// ─── Assignment (target = expr) ─────────────────────────────

data class PIRAssign(
    val target: PIRValue,
    val expr: PIRExpr,
) : PIRInstruction {
    override lateinit var location: PIRLocation
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    /** Backwards-compat: the source value when the expression is a simple value copy. */
    val source: PIRValue get() = expr as PIRValue
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitAssign(this)
}



// ─── Binary Expressions ────────────────────────────────────

sealed interface PIRBinaryExpr : PIRExpr {
    val left: PIRValue
    val right: PIRValue
}

data class PIRAddExpr(override val left: PIRValue, override val right: PIRValue) : PIRBinaryExpr
data class PIRSubExpr(override val left: PIRValue, override val right: PIRValue) : PIRBinaryExpr
data class PIRMulExpr(override val left: PIRValue, override val right: PIRValue) : PIRBinaryExpr
data class PIRDivExpr(override val left: PIRValue, override val right: PIRValue) : PIRBinaryExpr
data class PIRFloorDivExpr(override val left: PIRValue, override val right: PIRValue) : PIRBinaryExpr
data class PIRModExpr(override val left: PIRValue, override val right: PIRValue) : PIRBinaryExpr
data class PIRPowExpr(override val left: PIRValue, override val right: PIRValue) : PIRBinaryExpr
data class PIRMatMulExpr(override val left: PIRValue, override val right: PIRValue) : PIRBinaryExpr
data class PIRBitAndExpr(override val left: PIRValue, override val right: PIRValue) : PIRBinaryExpr
data class PIRBitOrExpr(override val left: PIRValue, override val right: PIRValue) : PIRBinaryExpr
data class PIRBitXorExpr(override val left: PIRValue, override val right: PIRValue) : PIRBinaryExpr
data class PIRLShiftExpr(override val left: PIRValue, override val right: PIRValue) : PIRBinaryExpr
data class PIRRShiftExpr(override val left: PIRValue, override val right: PIRValue) : PIRBinaryExpr

// ─── Unary Expressions ─────────────────────────────────────

sealed interface PIRUnaryExpr : PIRExpr {
    val operand: PIRValue
}

data class PIRNegExpr(override val operand: PIRValue) : PIRUnaryExpr
data class PIRPosExpr(override val operand: PIRValue) : PIRUnaryExpr
data class PIRNotExpr(override val operand: PIRValue) : PIRUnaryExpr
data class PIRInvertExpr(override val operand: PIRValue) : PIRUnaryExpr

// ─── Compare Expressions ───────────────────────────────────

sealed interface PIRCompareExpr : PIRExpr {
    val left: PIRValue
    val right: PIRValue
}

data class PIREqExpr(override val left: PIRValue, override val right: PIRValue) : PIRCompareExpr
data class PIRNeExpr(override val left: PIRValue, override val right: PIRValue) : PIRCompareExpr
data class PIRLtExpr(override val left: PIRValue, override val right: PIRValue) : PIRCompareExpr
data class PIRLeExpr(override val left: PIRValue, override val right: PIRValue) : PIRCompareExpr
data class PIRGtExpr(override val left: PIRValue, override val right: PIRValue) : PIRCompareExpr
data class PIRGeExpr(override val left: PIRValue, override val right: PIRValue) : PIRCompareExpr
data class PIRIsExpr(override val left: PIRValue, override val right: PIRValue) : PIRCompareExpr
data class PIRIsNotExpr(override val left: PIRValue, override val right: PIRValue) : PIRCompareExpr
data class PIRInExpr(override val left: PIRValue, override val right: PIRValue) : PIRCompareExpr
data class PIRNotInExpr(override val left: PIRValue, override val right: PIRValue) : PIRCompareExpr

// ─── Other Expressions ─────────────────────────────────────

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

// ─── Memory Store (side-effecting, no result) ───────────────

data class PIRStoreAttr(
    val obj: PIRValue,
    val attribute: String,
    val value: PIRValue,
) : PIRInstruction {
    override lateinit var location: PIRLocation
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitStoreAttr(this)
}

data class PIRStoreSubscript(
    val obj: PIRValue,
    val index: PIRValue,
    val value: PIRValue,
) : PIRInstruction {
    override lateinit var location: PIRLocation
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitStoreSubscript(this)
}

data class PIRStoreGlobal(
    val name: String,
    val module: String,
    val value: PIRValue,
) : PIRInstruction {
    override lateinit var location: PIRLocation
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitStoreGlobal(this)
}

data class PIRStoreClosure(
    val name: String,
    val depth: Int,
    val value: PIRValue,
) : PIRInstruction {
    override lateinit var location: PIRLocation
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitStoreClosure(this)
}

// ─── Call ───────────────────────────────────────────────────

data class PIRCall(
    val target: PIRValue?,
    val callee: PIRValue,
    val args: List<PIRCallArg>,
    val resolvedCallee: String? = null,
) : PIRInstruction {
    override lateinit var location: PIRLocation
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
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
    val bodyInstIndex: Int,
    val exitInstIndex: Int,
) : PIRInstruction, PIRBranchingInst {
    override lateinit var location: PIRLocation

    override val successors: List<Int>
        get() = listOf(bodyInstIndex, exitInstIndex)

    override val blockSuccessors: List<Int>
        get() = listOf(bodyBlock, exitBlock)

    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitNextIter(this)
}

data class PIRUnpack(
    val targets: List<PIRValue>,
    val source: PIRValue,
    val starIndex: Int,
) : PIRInstruction {
    override lateinit var location: PIRLocation
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitUnpack(this)
}

// ─── Control Flow (Terminators) ─────────────────────────────

data class PIRGoto(
    val targetBlock: Int,
    val targetInstIndex: Int,
) : PIRInstruction, PIRBranchingInst {
    override lateinit var location: PIRLocation

    override val successors: List<Int>
        get() = listOf(targetInstIndex)

    override val blockSuccessors: List<Int>
        get() = listOf(targetBlock)

    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitGoto(this)
}

data class PIRBranch(
    val condition: PIRValue,
    val trueBlock: Int,
    val falseBlock: Int,
    val trueInstIndex: Int,
    val falseInstIndex: Int,
) : PIRInstruction, PIRBranchingInst {
    override lateinit var location: PIRLocation

    override val successors: List<Int>
        get() = listOf(trueInstIndex, falseInstIndex)

    override val blockSuccessors: List<Int>
        get() = listOf(trueBlock, falseBlock)

    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitBranch(this)
}

data class PIRReturn(
    val value: PIRValue?,
) : PIRInstruction, PIRTerminatingInst {
    override lateinit var location: PIRLocation
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitReturn(this)
}

data class PIRRaise(
    val exception: PIRValue?,
    val cause: PIRValue?,
) : PIRInstruction, PIRTerminatingInst {
    override lateinit var location: PIRLocation
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitRaise(this)
}

data class PIRExceptHandler(
    val target: PIRValue?,
    val exceptionTypes: List<PIRType>,
) : PIRInstruction {
    override lateinit var location: PIRLocation
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitExceptHandler(this)
}

// ─── Generators & Async ─────────────────────────────────────

data class PIRYield(
    val target: PIRValue?,
    val value: PIRValue?,
) : PIRInstruction {
    override lateinit var location: PIRLocation
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitYield(this)
}

data class PIRYieldFrom(
    val target: PIRValue?,
    val iterable: PIRValue,
) : PIRInstruction {
    override lateinit var location: PIRLocation
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitYieldFrom(this)
}

data class PIRAwait(
    val target: PIRValue?,
    val awaitable: PIRValue,
) : PIRInstruction {
    override lateinit var location: PIRLocation
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitAwait(this)
}

// ─── Delete ─────────────────────────────────────────────────

data class PIRDeleteLocal(
    val local: PIRValue,
) : PIRInstruction {
    override lateinit var location: PIRLocation
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitDeleteLocal(this)
}

data class PIRDeleteAttr(
    val obj: PIRValue,
    val attribute: String,
) : PIRInstruction {
    override lateinit var location: PIRLocation
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitDeleteAttr(this)
}

data class PIRDeleteSubscript(
    val obj: PIRValue,
    val index: PIRValue,
) : PIRInstruction {
    override lateinit var location: PIRLocation
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitDeleteSubscript(this)
}

data class PIRDeleteGlobal(
    val name: String,
    val module: String,
) : PIRInstruction {
    override lateinit var location: PIRLocation
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitDeleteGlobal(this)
}

// ─── Misc ───────────────────────────────────────────────────

data object PIRUnreachable : PIRInstruction, PIRTerminatingInst {
    override var location: PIRLocation = object : PIRLocation {
        override val method: PIRFunction get() = error("Unreachable instruction has no method")
        override val index: Int get() = -1
        override val lineNumber: Int get() = -1
        override val colOffset: Int get() = -1
    }
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

val PIRAssign.binaryExpr: PIRBinaryExpr get() = expr as PIRBinaryExpr
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
