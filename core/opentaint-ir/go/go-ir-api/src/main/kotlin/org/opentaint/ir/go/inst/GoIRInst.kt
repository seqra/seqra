package org.opentaint.ir.go.inst

import org.opentaint.ir.go.api.GoIRPosition
import org.opentaint.ir.go.cfg.GoIRBasicBlock
import org.opentaint.ir.go.cfg.GoIRCallInfo
import org.opentaint.ir.go.expr.GoIRExpr
import org.opentaint.ir.go.type.GoIRType
import org.opentaint.ir.go.value.GoIRRegister
import org.opentaint.ir.go.value.GoIRValue

/**
 * Base interface for all IR instructions.
 * Every instruction belongs to a basic block and has a unique index within its function.
 */
sealed interface GoIRInst {
    val index: Int
    val block: GoIRBasicBlock
    val position: GoIRPosition?
    val operands: List<GoIRValue>

    fun <T> accept(visitor: GoIRInstVisitor<T>): T
}

/**
 * A value-defining instruction: an instruction that writes its result into a [GoIRRegister].
 * Subtyped by [GoIRAssignInst], [GoIRPhi], and [GoIRCall].
 *
 * Other instructions reference the [register], never the instruction itself.
 */
sealed interface GoIRDefInst : GoIRInst {
    val register: GoIRRegister
}

/** Marker for terminator instructions (last in block). */
sealed interface GoIRTerminator : GoIRInst

/** Marker for branching terminators (Jump, If). */
sealed interface GoIRBranching : GoIRTerminator

// ─── Value-defining instructions ────────────────────────────────────

/**
 * Assignment instruction: `register = expr`.
 * Wraps a [GoIRExpr] that computes the value stored into [register].
 * This covers 24 expression kinds (alloc, binop, unop, conversions, field access, etc.).
 */
data class GoIRAssignInst(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    override val register: GoIRRegister,
    val expr: GoIRExpr,
) : GoIRDefInst {
    override val operands: List<GoIRValue> get() = expr.operands
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitAssign(this)
}

/**
 * Phi instruction: merges values from predecessor blocks at a join point.
 * Each edge corresponds to a predecessor block; edge[i] is the value from predecessor[i].
 */
data class GoIRPhi(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    override val register: GoIRRegister,
    val edges: List<GoIRValue>,
    val comment: String?,
) : GoIRDefInst {
    override val operands: List<GoIRValue> get() = edges
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitPhi(this)
}

/**
 * Call instruction: invokes a function and stores the result into [register].
 */
data class GoIRCall(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    override val register: GoIRRegister,
    val call: GoIRCallInfo,
) : GoIRDefInst {
    override val operands: List<GoIRValue> get() = call.allOperands()
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitCall(this)
}

// ─── Terminators ────────────────────────────────────────────────────

data class GoIRJump(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
) : GoIRBranching {
    override val operands: List<GoIRValue> get() = emptyList()
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitJump(this)
}

data class GoIRIf(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val cond: GoIRValue,
) : GoIRBranching {
    override val operands: List<GoIRValue> get() = listOf(cond)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitIf(this)
}

data class GoIRReturn(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val results: List<GoIRValue>,
) : GoIRTerminator {
    override val operands: List<GoIRValue> get() = results
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitReturn(this)
}

data class GoIRPanic(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val x: GoIRValue,
) : GoIRTerminator {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitPanic(this)
}

// ─── Effect-only instructions ───────────────────────────────────────

data class GoIRStore(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val addr: GoIRValue,
    val value: GoIRValue,
) : GoIRInst {
    override val operands: List<GoIRValue> get() = listOf(addr, value)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitStore(this)
}

data class GoIRMapUpdate(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val map: GoIRValue,
    val key: GoIRValue,
    val value: GoIRValue,
) : GoIRInst {
    override val operands: List<GoIRValue> get() = listOf(map, key, value)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitMapUpdate(this)
}

data class GoIRSend(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val chan: GoIRValue,
    val x: GoIRValue,
) : GoIRInst {
    override val operands: List<GoIRValue> get() = listOf(chan, x)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitSend(this)
}

data class GoIRGo(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val call: GoIRCallInfo,
) : GoIRInst {
    override val operands: List<GoIRValue> get() = call.allOperands()
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitGo(this)
}

data class GoIRDefer(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val call: GoIRCallInfo,
) : GoIRInst {
    override val operands: List<GoIRValue> get() = call.allOperands()
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitDefer(this)
}

data class GoIRRunDefers(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
) : GoIRInst {
    override val operands: List<GoIRValue> get() = emptyList()
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitRunDefers(this)
}

data class GoIRDebugRef(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val x: GoIRValue,
    val isAddr: Boolean,
) : GoIRInst {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitDebugRef(this)
}
