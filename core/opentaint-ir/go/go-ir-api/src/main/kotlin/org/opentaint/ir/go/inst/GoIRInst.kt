package org.opentaint.ir.go.inst

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonInstLocation
import org.opentaint.ir.go.api.GoIRBody
import org.opentaint.ir.go.api.GoIRGlobal
import org.opentaint.ir.go.api.GoIRPosition
import org.opentaint.ir.go.cfg.GoIRBasicBlock
import org.opentaint.ir.go.cfg.GoIRCallInfo
import org.opentaint.ir.go.expr.GoIRExpr
import org.opentaint.ir.go.value.GoIRRegister
import org.opentaint.ir.go.value.GoIRValue

data class GoInstLocation(
    val functionBody: GoIRBody,
    override val index: Int,
    val blockIndex: Int,
    val position: GoIRPosition?
) : CommonInstLocation {
    override val method: CommonMethod get() = functionBody.function
}

/**
 * Base interface for all IR instructions.
 * Every instruction belongs to a basic block and has a unique index within its function.
 *
 * Equality and hashCode are anchored on [location] — within a function body each instruction has a
 * unique (block,index) coordinate, so two GoIRInst at the same location are necessarily the same
 * instruction. Fields beyond location (registers, exprs, …) carry mutable type state populated
 * during lazy body resolution, and including them in hashCode produces unstable hashes that break
 * map-based storage (e.g. `MethodAnalyzerStorage.entryPoints`).
 */
sealed interface GoIRInst: CommonInst {
    override val location: GoInstLocation
    val operands: List<GoIRValue>

    fun <T> accept(visitor: GoIRInstVisitor<T>): T
}

private fun GoIRInst.equalsByLocation(other: Any?): Boolean =
    this === other || (other is GoIRInst && location == other.location)

val GoIRInst.index: Int get() = location.index

val GoIRInst.block: GoIRBasicBlock get() = location.functionBody.blocks[location.blockIndex]

val GoIRInst.position: GoIRPosition? get() = location.position

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
sealed interface GoIRBranching : GoIRTerminator {
    val successors: List<GoIRInstRef>
}

// ─── Value-defining instructions ────────────────────────────────────

data class GoIRAssignInst(
    override val location: GoInstLocation,
    override val register: GoIRRegister,
    val expr: GoIRExpr,
) : GoIRDefInst {
    override val operands: List<GoIRValue> get() = expr.operands
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitAssign(this)
    override fun toString(): String = "$register = $expr"
    override fun equals(other: Any?): Boolean = equalsByLocation(other)
    override fun hashCode(): Int = location.hashCode()
}

data class GoIRPhi(
    override val location: GoInstLocation,
    override val register: GoIRRegister,
    val edges: Map<GoIRInstRef, GoIRValue>,
    val comment: String?,
) : GoIRDefInst {
    override val operands: List<GoIRValue> get() = edges.entries.sortedBy { it.key.index }.map { it.value }
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitPhi(this)
    override fun toString(): String {
        val args = edges.entries.sortedBy { it.key.index }
            .joinToString(", ") { (ref, value) -> "${ref.index}: $value" }
        val suffix = comment?.let { "  // $it" } ?: ""
        return "$register = phi($args)$suffix"
    }
    override fun equals(other: Any?): Boolean = equalsByLocation(other)
    override fun hashCode(): Int = location.hashCode()
}

data class GoIRCall(
    override val location: GoInstLocation,
    override val register: GoIRRegister,
    val call: GoIRCallInfo,
) : GoIRDefInst {
    override val operands: List<GoIRValue> get() = call.allOperands()
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitCall(this)
    override fun toString(): String = "$register = ${call.render()}"
    override fun equals(other: Any?): Boolean = equalsByLocation(other)
    override fun hashCode(): Int = location.hashCode()
}

// ─── Terminators ────────────────────────────────────────────────────

data class GoIRJump(
    override val location: GoInstLocation,
    val target: GoIRInstRef,
) : GoIRBranching {
    override val operands: List<GoIRValue> get() = emptyList()
    override val successors: List<GoIRInstRef> get() = listOf(target)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitJump(this)
    override fun toString(): String = "jump $target"
    override fun equals(other: Any?): Boolean = equalsByLocation(other)
    override fun hashCode(): Int = location.hashCode()
}

data class GoIRIf(
    override val location: GoInstLocation,
    val cond: GoIRValue,
    val trueBranch: GoIRInstRef,
    val falseBranch: GoIRInstRef,
) : GoIRBranching {
    override val operands: List<GoIRValue> get() = listOf(cond)
    override val successors: List<GoIRInstRef> get() = listOf(trueBranch, falseBranch)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitIf(this)
    override fun toString(): String = "if ($cond) then $trueBranch else $falseBranch"
    override fun equals(other: Any?): Boolean = equalsByLocation(other)
    override fun hashCode(): Int = location.hashCode()
}

data class GoIRReturn(
    override val location: GoInstLocation,
    val results: List<GoIRValue>,
) : GoIRTerminator {
    override val operands: List<GoIRValue> get() = results
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitReturn(this)
    override fun toString(): String =
        if (results.isEmpty()) "return" else "return ${results.joinToString(", ")}"
    override fun equals(other: Any?): Boolean = equalsByLocation(other)
    override fun hashCode(): Int = location.hashCode()
}

data class GoIRPanic(
    override val location: GoInstLocation,
    val x: GoIRValue,
) : GoIRTerminator {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitPanic(this)
    override fun toString(): String = "panic $x"
    override fun equals(other: Any?): Boolean = equalsByLocation(other)
    override fun hashCode(): Int = location.hashCode()
}

// ─── Effect-only instructions ───────────────────────────────────────

sealed interface GoIRStoreInst : GoIRInst {
    val addr: GoIRValue
    val value: GoIRValue
    fun toGenericStore(): GoIRStore
}

data class GoIRStore(
    override val location: GoInstLocation,
    override val addr: GoIRValue,
    override val value: GoIRValue,
) : GoIRStoreInst {
    override val operands: List<GoIRValue> get() = listOf(addr, value)
    override fun toGenericStore(): GoIRStore = this
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitStore(this)
    override fun toString(): String = "*$addr = $value"
    override fun equals(other: Any?): Boolean = equalsByLocation(other)
    override fun hashCode(): Int = location.hashCode()
}

data class GoIRFieldStore(
    override val location: GoInstLocation,
    override val addr: GoIRValue,
    val base: GoIRValue,
    val fieldIndex: Int,
    val fieldName: String,
    override val value: GoIRValue,
) : GoIRStoreInst {
    override val operands: List<GoIRValue> get() = listOf(addr, base, value)
    override fun toGenericStore(): GoIRStore = GoIRStore(location, addr, value)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitFieldStore(this)
    override fun toString(): String = "$base.$fieldName = $value"
    override fun equals(other: Any?): Boolean = equalsByLocation(other)
    override fun hashCode(): Int = location.hashCode()
}

data class GoIRIndexStore(
    override val location: GoInstLocation,
    override val addr: GoIRValue,
    val base: GoIRValue,
    val index: GoIRValue,
    override val value: GoIRValue,
) : GoIRStoreInst {
    override val operands: List<GoIRValue> get() = listOf(addr, base, index, value)
    override fun toGenericStore(): GoIRStore = GoIRStore(location, addr, value)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitIndexStore(this)
    override fun toString(): String = "$base[$index] = $value"
    override fun equals(other: Any?): Boolean = equalsByLocation(other)
    override fun hashCode(): Int = location.hashCode()
}

data class GoIRGlobalStore(
    override val location: GoInstLocation,
    val lookup: org.opentaint.ir.go.api.GoIRGlobalReference,
    val value: GoIRValue,
) : GoIRInst {
    val global get() = lookup.global
    override val operands: List<GoIRValue> get() = listOf(value)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitGlobalStore(this)
    override fun toString(): String = "global ${global.fullName} = $value"
    override fun equals(other: Any?): Boolean = equalsByLocation(other)
    override fun hashCode(): Int = location.hashCode()
}

data class GoIRMapUpdate(
    override val location: GoInstLocation,
    val map: GoIRValue,
    val key: GoIRValue,
    val value: GoIRValue,
) : GoIRInst {
    override val operands: List<GoIRValue> get() = listOf(map, key, value)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitMapUpdate(this)
    override fun toString(): String = "$map[$key] = $value"
    override fun equals(other: Any?): Boolean = equalsByLocation(other)
    override fun hashCode(): Int = location.hashCode()
}

data class GoIRSend(
    override val location: GoInstLocation,
    val chan: GoIRValue,
    val x: GoIRValue,
) : GoIRInst {
    override val operands: List<GoIRValue> get() = listOf(chan, x)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitSend(this)
    override fun toString(): String = "send $chan <- $x"
    override fun equals(other: Any?): Boolean = equalsByLocation(other)
    override fun hashCode(): Int = location.hashCode()
}

data class GoIRGo(
    override val location: GoInstLocation,
    val call: GoIRCallInfo,
) : GoIRInst {
    override val operands: List<GoIRValue> get() = call.allOperands()
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitGo(this)
    override fun toString(): String = "go ${call.render()}"
    override fun equals(other: Any?): Boolean = equalsByLocation(other)
    override fun hashCode(): Int = location.hashCode()
}

data class GoIRDefer(
    override val location: GoInstLocation,
    val call: GoIRCallInfo,
) : GoIRInst {
    override val operands: List<GoIRValue> get() = call.allOperands()
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitDefer(this)
    override fun toString(): String = "defer ${call.render()}"
    override fun equals(other: Any?): Boolean = equalsByLocation(other)
    override fun hashCode(): Int = location.hashCode()
}

data class GoIRRunDefers(
    override val location: GoInstLocation,
) : GoIRInst {
    override val operands: List<GoIRValue> get() = emptyList()
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitRunDefers(this)
    override fun toString(): String = "rundefers"
    override fun equals(other: Any?): Boolean = equalsByLocation(other)
    override fun hashCode(): Int = location.hashCode()
}

data class GoIRDebugRef(
    override val location: GoInstLocation,
    val x: GoIRValue,
    val isAddr: Boolean,
) : GoIRInst {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRInstVisitor<T>): T = visitor.visitDebugRef(this)
    override fun toString(): String = "debugref ${if (isAddr) "&" else ""}$x"
    override fun equals(other: Any?): Boolean = equalsByLocation(other)
    override fun hashCode(): Int = location.hashCode()
}

private fun GoIRCallInfo.render(): String {
    val argsStr = args.joinToString(", ")
    return when {
        receiver != null && methodName != null -> "$receiver.$methodName($argsStr)"
        target != null -> "${target.displayName}($argsStr)"
        else -> "call($argsStr)"
    }
}
