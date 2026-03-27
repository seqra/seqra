package org.opentaint.ir.go.expr

import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.cfg.GoIRSelectState
import org.opentaint.ir.go.type.GoIRBinaryOp
import org.opentaint.ir.go.type.GoIRType
import org.opentaint.ir.go.type.GoIRUnaryOp
import org.opentaint.ir.go.value.GoIRValue

/**
 * Base interface for all IR expressions.
 * An expression represents a computation that produces a value.
 * Expressions appear inside [org.opentaint.ir.go.inst.GoIRAssignInst] as the right-hand side.
 */
sealed interface GoIRExpr: CommonExpr {
    val type: GoIRType
    val operands: List<GoIRValue>

    override val typeName: String get() = type.typeName

    fun <T> accept(visitor: GoIRExprVisitor<T>): T
}

// ─── Allocation ─────────────────────────────────────────────────────

data class GoIRAllocExpr(
    override var type: GoIRType,
    val allocType: GoIRType,
    val isHeap: Boolean,
    val comment: String?,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = emptyList()
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitAlloc(this)
}

// ─── Arithmetic / logic ─────────────────────────────────────────────

data class GoIRBinOpExpr(
    override var type: GoIRType,
    val op: GoIRBinaryOp,
    val x: GoIRValue,
    val y: GoIRValue,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x, y)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitBinOp(this)
}

data class GoIRUnOpExpr(
    override var type: GoIRType,
    val op: GoIRUnaryOp,
    val x: GoIRValue,
    val commaOk: Boolean,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitUnOp(this)
}

// ─── Type conversions ───────────────────────────────────────────────

data class GoIRChangeTypeExpr(
    override var type: GoIRType,
    val x: GoIRValue,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitChangeType(this)
}

data class GoIRConvertExpr(
    override var type: GoIRType,
    val x: GoIRValue,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitConvert(this)
}

data class GoIRMultiConvertExpr(
    override var type: GoIRType,
    val x: GoIRValue,
    val fromType: GoIRType,
    val toType: GoIRType,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitMultiConvert(this)
}

data class GoIRChangeInterfaceExpr(
    override var type: GoIRType,
    val x: GoIRValue,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitChangeInterface(this)
}

data class GoIRSliceToArrayPointerExpr(
    override var type: GoIRType,
    val x: GoIRValue,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitSliceToArrayPointer(this)
}

// ─── Interface / type assertion ─────────────────────────────────────

data class GoIRMakeInterfaceExpr(
    override var type: GoIRType,
    val x: GoIRValue,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitMakeInterface(this)
}

data class GoIRTypeAssertExpr(
    override var type: GoIRType,
    val x: GoIRValue,
    val assertedType: GoIRType,
    val commaOk: Boolean,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitTypeAssert(this)
}

// ─── Closures ───────────────────────────────────────────────────────

data class GoIRMakeClosureExpr(
    override var type: GoIRType,
    val fn: GoIRFunction,
    val bindings: List<GoIRValue>,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = bindings
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitMakeClosure(this)
}

// ─── Container construction ─────────────────────────────────────────

data class GoIRMakeMapExpr(
    override var type: GoIRType,
    val reserve: GoIRValue?,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOfNotNull(reserve)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitMakeMap(this)
}

data class GoIRMakeChanExpr(
    override var type: GoIRType,
    val size: GoIRValue,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(size)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitMakeChan(this)
}

data class GoIRMakeSliceExpr(
    override var type: GoIRType,
    val len: GoIRValue,
    val cap: GoIRValue,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(len, cap)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitMakeSlice(this)
}

// ─── Field access ───────────────────────────────────────────────────

data class GoIRFieldAddrExpr(
    override var type: GoIRType,
    val x: GoIRValue,
    val fieldIndex: Int,
    val fieldName: String,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitFieldAddr(this)
}

data class GoIRFieldExpr(
    override var type: GoIRType,
    val x: GoIRValue,
    val fieldIndex: Int,
    val fieldName: String,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitField(this)
}

// ─── Indexing ───────────────────────────────────────────────────────

data class GoIRIndexAddrExpr(
    override var type: GoIRType,
    val x: GoIRValue,
    val indexValue: GoIRValue,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x, indexValue)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitIndexAddr(this)
}

data class GoIRIndexExpr(
    override var type: GoIRType,
    val x: GoIRValue,
    val indexValue: GoIRValue,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x, indexValue)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitIndex(this)
}

data class GoIRSliceExpr(
    override var type: GoIRType,
    val x: GoIRValue,
    val low: GoIRValue?,
    val high: GoIRValue?,
    val max: GoIRValue?,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOfNotNull(x, low, high, max)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitSlice(this)
}

data class GoIRLookupExpr(
    override var type: GoIRType,
    val x: GoIRValue,
    val indexValue: GoIRValue,
    val commaOk: Boolean,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x, indexValue)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitLookup(this)
}

// ─── Iteration ──────────────────────────────────────────────────────

data class GoIRRangeExpr(
    override var type: GoIRType,
    val x: GoIRValue,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitRange(this)
}

data class GoIRNextExpr(
    override var type: GoIRType,
    val iter: GoIRValue,
    val isString: Boolean,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(iter)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitNext(this)
}

// ─── Channels ───────────────────────────────────────────────────────

data class GoIRSelectExpr(
    override var type: GoIRType,
    val states: List<GoIRSelectState>,
    val isBlocking: Boolean,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() =
        states.flatMap { listOfNotNull(it.chan, it.send) }
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitSelect(this)
}

// ─── Tuple extraction ───────────────────────────────────────────────

data class GoIRExtractExpr(
    override var type: GoIRType,
    val tuple: GoIRValue,
    val extractIndex: Int,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(tuple)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitExtract(this)
}
