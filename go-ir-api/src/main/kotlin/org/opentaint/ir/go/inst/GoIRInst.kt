package org.opentaint.ir.go.inst

import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.api.GoIRPosition
import org.opentaint.ir.go.cfg.GoIRBasicBlock
import org.opentaint.ir.go.cfg.GoIRCallInfo
import org.opentaint.ir.go.cfg.GoIRSelectState
import org.opentaint.ir.go.type.GoIRBinaryOp
import org.opentaint.ir.go.type.GoIRType
import org.opentaint.ir.go.type.GoIRUnaryOp
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

/** An instruction that also produces a value (SSA register). */
sealed interface GoIRValueInst : GoIRInst, GoIRValue

/** Marker for terminator instructions (last in block). */
sealed interface GoIRTerminator : GoIRInst

/** Marker for branching terminators (Jump, If). */
sealed interface GoIRBranching : GoIRTerminator

// ─── Value-producing instructions ───────────────────────────────────

data class GoIRAlloc(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val allocType: GoIRType,
    val isHeap: Boolean,
    val comment: String?,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = emptyList()
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitAlloc(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRPhi(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val edges: List<GoIRValue>,
    val comment: String?,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = edges
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitPhi(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRBinOp(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val op: GoIRBinaryOp,
    val x: GoIRValue,
    val y: GoIRValue,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = listOf(x, y)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitBinOp(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRUnOp(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val op: GoIRUnaryOp,
    val x: GoIRValue,
    val commaOk: Boolean,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitUnOp(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRCall(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val call: GoIRCallInfo,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = call.allOperands()
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitCall(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRChangeType(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val x: GoIRValue,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitChangeType(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRConvert(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val x: GoIRValue,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitConvert(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRMultiConvert(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val x: GoIRValue,
    val fromType: GoIRType,
    val toType: GoIRType,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitMultiConvert(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRChangeInterface(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val x: GoIRValue,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitChangeInterface(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRSliceToArrayPointer(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val x: GoIRValue,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitSliceToArrayPointer(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRMakeInterface(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val x: GoIRValue,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitMakeInterface(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRTypeAssert(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val x: GoIRValue,
    val assertedType: GoIRType,
    val commaOk: Boolean,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitTypeAssert(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRMakeClosure(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val fn: GoIRFunction,
    val bindings: List<GoIRValue>,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = bindings
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitMakeClosure(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRMakeMap(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val reserve: GoIRValue?,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = listOfNotNull(reserve)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitMakeMap(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRMakeChan(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val size: GoIRValue,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = listOf(size)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitMakeChan(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRMakeSlice(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val len: GoIRValue,
    val cap: GoIRValue,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = listOf(len, cap)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitMakeSlice(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRFieldAddr(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val x: GoIRValue,
    val fieldIndex: Int,
    val fieldName: String,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitFieldAddr(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRField(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val x: GoIRValue,
    val fieldIndex: Int,
    val fieldName: String,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitField(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRIndexAddr(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val x: GoIRValue,
    val indexValue: GoIRValue,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = listOf(x, indexValue)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitIndexAddr(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRIndex(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val x: GoIRValue,
    val indexValue: GoIRValue,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = listOf(x, indexValue)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitIndex(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRSlice(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val x: GoIRValue,
    val low: GoIRValue?,
    val high: GoIRValue?,
    val max: GoIRValue?,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = listOfNotNull(x, low, high, max)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitSlice(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRLookup(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val x: GoIRValue,
    val indexValue: GoIRValue,
    val commaOk: Boolean,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = listOf(x, indexValue)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitLookup(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRRange(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val x: GoIRValue,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitRange(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRNext(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val iter: GoIRValue,
    val isString: Boolean,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = listOf(iter)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitNext(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRSelect(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val states: List<GoIRSelectState>,
    val isBlocking: Boolean,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() =
        states.flatMap { listOfNotNull(it.chan, it.send) }
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitSelect(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

data class GoIRExtract(
    override val index: Int,
    override val block: GoIRBasicBlock,
    override val position: GoIRPosition?,
    val tuple: GoIRValue,
    val extractIndex: Int,
    override val type: GoIRType,
    override val name: String,
) : GoIRValueInst {
    override val operands: List<GoIRValue> get() = listOf(tuple)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitExtract(this)
    override fun <T> acceptValue(visitor: org.opentaint.ir.go.value.GoIRValueVisitor<T>): T = visitor.visitValueInst(this)
}

// ─── Effect-only instructions ───────────────────────────────────────

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
