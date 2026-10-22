package org.opentaint.ir.go.expr

import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.api.GoIRGlobal
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
    override fun toString(): String {
        val kind = if (isHeap) "new" else "local"
        val suffix = comment?.let { "  // $it" } ?: ""
        return "$kind ${allocType.typeName}$suffix"
    }
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
    override fun toString(): String = "$x ${op.symbol} $y"
}

data class GoIRUnOpExpr(
    override var type: GoIRType,
    val op: GoIRUnaryOp,
    val x: GoIRValue,
    val commaOk: Boolean,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitUnOp(this)
    override fun toString(): String = "${op.symbol}$x${if (commaOk) ",ok" else ""}"
}

// ─── Type conversions ───────────────────────────────────────────────

data class GoIRChangeTypeExpr(
    override var type: GoIRType,
    val x: GoIRValue,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitChangeType(this)
    override fun toString(): String = "changetype<${type.typeName}>($x)"
}

data class GoIRConvertExpr(
    override var type: GoIRType,
    val x: GoIRValue,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitConvert(this)
    override fun toString(): String = "convert<${type.typeName}>($x)"
}

data class GoIRMultiConvertExpr(
    override var type: GoIRType,
    val x: GoIRValue,
    val fromType: GoIRType,
    val toType: GoIRType,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitMultiConvert(this)
    override fun toString(): String = "multiconvert<${fromType.typeName} -> ${toType.typeName}>($x)"
}

data class GoIRChangeInterfaceExpr(
    override var type: GoIRType,
    val x: GoIRValue,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitChangeInterface(this)
    override fun toString(): String = "changeinterface<${type.typeName}>($x)"
}

data class GoIRSliceToArrayPointerExpr(
    override var type: GoIRType,
    val x: GoIRValue,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitSliceToArrayPointer(this)
    override fun toString(): String = "slicetoarrayptr<${type.typeName}>($x)"
}

// ─── Interface / type assertion ─────────────────────────────────────

data class GoIRMakeInterfaceExpr(
    override var type: GoIRType,
    val x: GoIRValue,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitMakeInterface(this)
    override fun toString(): String = "makeinterface<${type.typeName}>($x)"
}

data class GoIRTypeAssertExpr(
    override var type: GoIRType,
    val x: GoIRValue,
    val assertedType: GoIRType,
    val commaOk: Boolean,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitTypeAssert(this)
    override fun toString(): String = "$x.(${assertedType.typeName})${if (commaOk) ",ok" else ""}"
}

// ─── Closures ───────────────────────────────────────────────────────

data class GoIRMakeClosureExpr(
    override var type: GoIRType,
    val lookup: org.opentaint.ir.go.api.GoIrFunctionReference,
    val bindings: List<GoIRValue>,
) : GoIRExpr {
    val fn get() = lookup.function
    override val operands: List<GoIRValue> get() = bindings
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitMakeClosure(this)
    override fun toString(): String = "makeclosure ${fn.name}[${bindings.joinToString(", ")}]"
}

// ─── Container construction ─────────────────────────────────────────

data class GoIRMakeMapExpr(
    override var type: GoIRType,
    val reserve: GoIRValue?,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOfNotNull(reserve)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitMakeMap(this)
    override fun toString(): String = "make ${type.typeName}${reserve?.let { "($it)" } ?: ""}"
}

data class GoIRMakeChanExpr(
    override var type: GoIRType,
    val size: GoIRValue,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(size)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitMakeChan(this)
    override fun toString(): String = "make ${type.typeName}($size)"
}

data class GoIRMakeSliceExpr(
    override var type: GoIRType,
    val len: GoIRValue,
    val cap: GoIRValue,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(len, cap)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitMakeSlice(this)
    override fun toString(): String = "make ${type.typeName}($len, $cap)"
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
    override fun toString(): String = "&$x.$fieldName"
}

data class GoIRFieldExpr(
    override var type: GoIRType,
    val x: GoIRValue,
    val fieldIndex: Int,
    val fieldName: String,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitField(this)
    override fun toString(): String = "$x.$fieldName"
}

// ─── Indexing ───────────────────────────────────────────────────────

data class GoIRIndexAddrExpr(
    override var type: GoIRType,
    val x: GoIRValue,
    val indexValue: GoIRValue,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x, indexValue)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitIndexAddr(this)
    override fun toString(): String = "&$x[$indexValue]"
}

data class GoIRIndexExpr(
    override var type: GoIRType,
    val x: GoIRValue,
    val indexValue: GoIRValue,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x, indexValue)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitIndex(this)
    override fun toString(): String = "$x[$indexValue]"
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
    override fun toString(): String {
        val l = low?.toString() ?: ""
        val h = high?.toString() ?: ""
        val m = max?.let { ":$it" } ?: ""
        return "$x[$l:$h$m]"
    }
}

data class GoIRLookupExpr(
    override var type: GoIRType,
    val x: GoIRValue,
    val indexValue: GoIRValue,
    val commaOk: Boolean,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x, indexValue)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitLookup(this)
    override fun toString(): String = "$x[$indexValue]${if (commaOk) ",ok" else ""}"
}

// ─── Iteration ──────────────────────────────────────────────────────

data class GoIRRangeExpr(
    override var type: GoIRType,
    val x: GoIRValue,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(x)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitRange(this)
    override fun toString(): String = "range $x"
}

data class GoIRNextExpr(
    override var type: GoIRType,
    val iter: GoIRValue,
    val isString: Boolean,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(iter)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitNext(this)
    override fun toString(): String = "next${if (isString) "<string>" else ""}($iter)"
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
    override fun toString(): String {
        val kind = if (isBlocking) "select" else "select-nonblocking"
        val cases = states.joinToString(", ") { s ->
            when (s.send) {
                null -> "<-${s.chan}"
                else -> "${s.chan}<-${s.send}"
            }
        }
        return "$kind [$cases]"
    }
}

// ─── Tuple extraction ───────────────────────────────────────────────

data class GoIRExtractExpr(
    override var type: GoIRType,
    val tuple: GoIRValue,
    val extractIndex: Int,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = listOf(tuple)
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitExtract(this)
    override fun toString(): String = "extract $tuple #$extractIndex"
}

// ─── Entity value loads ─────────────────────────────────────────────

data class GoIRGlobalValueExpr(
    override var type: GoIRType,
    val lookup: org.opentaint.ir.go.api.GoIRGlobalReference,
) : GoIRExpr {
    val global get() = lookup.global
    override val operands: List<GoIRValue> get() = emptyList()
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitGlobalValue(this)
    override fun toString(): String = "global ${global.fullName}"
}

data class GoIRFunctionValueExpr(
    override var type: GoIRType,
    val lookup: org.opentaint.ir.go.api.GoIrFunctionReference,
) : GoIRExpr {
    val function get() = lookup.function
    override val operands: List<GoIRValue> get() = emptyList()
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitFunctionValue(this)
    override fun toString(): String = "func ${function.fullName}"
}

data class GoIRBuiltinValueExpr(
    override var type: GoIRType,
    val builtinName: String,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = emptyList()
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitBuiltinValue(this)
    override fun toString(): String = "builtin $builtinName"
}

data class GoIRFreeVarValueExpr(
    override var type: GoIRType,
    val freeVarIndex: Int,
    val name: String,
) : GoIRExpr {
    override val operands: List<GoIRValue> get() = emptyList()
    override fun <T> accept(visitor: GoIRExprVisitor<T>): T = visitor.visitFreeVarValue(this)
    override fun toString(): String = "freevar:$freeVarIndex $name"
}

private val GoIRBinaryOp.symbol: String
    get() = when (this) {
        GoIRBinaryOp.ADD -> "+"
        GoIRBinaryOp.SUB -> "-"
        GoIRBinaryOp.MUL -> "*"
        GoIRBinaryOp.DIV -> "/"
        GoIRBinaryOp.REM -> "%"
        GoIRBinaryOp.AND -> "&"
        GoIRBinaryOp.OR -> "|"
        GoIRBinaryOp.XOR -> "^"
        GoIRBinaryOp.SHL -> "<<"
        GoIRBinaryOp.SHR -> ">>"
        GoIRBinaryOp.AND_NOT -> "&^"
        GoIRBinaryOp.EQ -> "=="
        GoIRBinaryOp.NEQ -> "!="
        GoIRBinaryOp.LT -> "<"
        GoIRBinaryOp.LEQ -> "<="
        GoIRBinaryOp.GT -> ">"
        GoIRBinaryOp.GEQ -> ">="
    }

private val GoIRUnaryOp.symbol: String
    get() = when (this) {
        GoIRUnaryOp.NOT -> "!"
        GoIRUnaryOp.NEG -> "-"
        GoIRUnaryOp.XOR -> "^"
        GoIRUnaryOp.DEREF -> "*"
        GoIRUnaryOp.ARROW -> "<-"
    }
