package org.opentaint.dataflow.jvm.ap.ifds.alias

import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRPrimitiveType
import org.opentaint.ir.api.jvm.cfg.JIRArgument
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRCallInst
import org.opentaint.ir.api.jvm.cfg.JIRCastExpr
import org.opentaint.ir.api.jvm.cfg.JIRCatchInst
import org.opentaint.ir.api.jvm.cfg.JIRConstant
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRNewArrayExpr
import org.opentaint.ir.api.jvm.cfg.JIRNewExpr
import org.opentaint.ir.api.jvm.cfg.JIRRef
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRThis
import org.opentaint.ir.api.jvm.cfg.JIRThrowInst
import org.opentaint.ir.api.jvm.cfg.JIRValue

sealed interface ExprOrValue

sealed interface Value: ExprOrValue

sealed interface RefValue : Value, Comparable<RefValue> {
    val valueKind: Int

    override fun compareTo(other: RefValue): Int {
        val kindCmp = valueKind.compareTo(other.valueKind)
        if (kindCmp != 0) return kindCmp
        return compareValue(other)
    }

    fun compareValue(other: RefValue): Int

    data class Local(val idx: Int, val ctx: ContextInfo) : RefValue {
        override val valueKind: Int get() = 0

        override fun compareValue(other: RefValue): Int = compare(other as Local)
        fun compare(other: Local): Int = comparator.compare(this, other)

        companion object {
            private val comparator = compareBy<Local> { it.idx }.thenComparing { it.ctx }
        }
    }

    data class Arg(val idx: Int) : RefValue {
        override val valueKind: Int get() = 1

        override fun compareValue(other: RefValue): Int = compare(other as Arg)
        fun compare(other: Arg): Int = idx.compareTo(other.idx)
    }

    data class This(val isOuter: Boolean) : RefValue {
        override val valueKind: Int get() = 2

        override fun compareValue(other: RefValue): Int = compare(other as This)
        fun compare(other: This): Int = isOuter.compareTo(other.isOuter)
    }

    data class Static(val type: String) : RefValue {
        override val valueKind: Int get() = 3

        override fun compareValue(other: RefValue): Int = compare(other as Static)
        fun compare(other: Static): Int = type.compareTo(other.type)
    }
}

sealed interface Expr: ExprOrValue {
    data object Unknown: Expr
    data class Alloc(val stmt: JIRInst) : Expr
    data class FieldLoad(val instance: RefValue, val field: JIRField) : Expr
    data class ArrayLoad(val instance: RefValue, val index: SimpleValue.Primitive) : Expr
}

sealed interface SimpleValue: Expr, Value {
    data object Primitive: SimpleValue
    data class RefConst(val expr: JIRExpr): SimpleValue
}

sealed interface Stmt : Comparable<Stmt> {
    val originalIdx: Int

    sealed interface NoCall: Stmt

    data class Call(val method: JIRMethod, val lValue: RefValue.Local?, val instance: Value?, val args: List<Value>, override val originalIdx: Int) : Stmt {
        private val usedValuesValue = lazy { usedValues() }

        private fun usedValues(): List<Value> {
            if (instance == null) return args
            return args + instance
        }

        fun getUsedValues() = usedValuesValue.value
    }

    data class Copy(val lValue: RefValue.Local, val rValue: RefValue, override val originalIdx: Int): NoCall
    data class Assign(val lValue: RefValue.Local, val expr: Expr, override val originalIdx: Int) : NoCall
    data class FieldStore(val instance: RefValue, val field: JIRField, val value: ExprOrValue, override val originalIdx: Int) : NoCall
    data class ArrayStore(val instance: RefValue, val index: SimpleValue.Primitive, val value: ExprOrValue, override val originalIdx: Int) : NoCall
    data class WriteStatic(val field: JIRField, val value: ExprOrValue, override val originalIdx: Int) : NoCall
    data class Return(val value: RefValue?, override val originalIdx: Int) : NoCall
    data class Throw(val value: RefValue, override val originalIdx: Int) : NoCall

    override fun compareTo(other: Stmt): Int = this.originalIdx.compareTo(other.originalIdx)
}

interface InstEvalContext {
    fun createThis(isOuter: Boolean): Value
    fun createArg(idx: Int): Value
    fun createLocal(idx: Int): RefValue.Local
}

fun InstEvalContext.evalInst(inst: JIRInst): Stmt? {
    return when (inst) {
        is JIRAssignInst -> {
            evalAssign(inst.lhv, inst.rhv, inst)
        }

        is JIRCallInst -> {
            evalCall(inst.callExpr, inst, lValue = null)
        }

        is JIRCatchInst -> {
            val local = inst.throwable as? JIRLocalVar ?: return null
            val expr = Expr.Alloc(inst)

            val lValue = createLocal(local.index)
            Stmt.Assign(lValue, expr, inst.location.index)
        }

        is JIRReturnInst -> {
            val value = inst.returnValue?.let { evalSimpleValue(it as JIRImmediate, inst) } as? RefValue
            Stmt.Return(value, inst.location.index)
        }

        is JIRThrowInst -> {
            val value = getLocalRefValue(inst.throwable, inst) as? RefValue ?: return null
            Stmt.Throw(value, inst.location.index)
        }

        else -> null
    }
}

private fun InstEvalContext.evalAssign(lhv: JIRValue, rhv: JIRExpr, inst: JIRInst): Stmt? {
    val loc = inst.location
    val isPrimitive = lhv.type is JIRPrimitiveType
    if (rhv is JIRCallExpr) {
        val lValue = if (isPrimitive) null else lhv
        return evalCall(rhv, inst, lValue)
    }

    if (isPrimitive) return null

    val expr = evalExpr(rhv, inst)

    when (lhv) {
        is JIRLocalVar -> {
            val lValue = createLocal(lhv.index)
            val stmt = when (expr) {
                is Expr -> Stmt.Assign(lValue, expr, loc.index)
                is RefValue -> Stmt.Copy(lValue, expr, loc.index)
            }
            return stmt
        }

        is JIRRef -> {
            when (lhv) {
                is JIRFieldRef -> {
                    val instance = lhv.instance ?: return Stmt.WriteStatic(lhv.field.field, expr, loc.index)
                    val iv = getLocalRefValue(instance, inst) as? RefValue ?: return null
                    return Stmt.FieldStore(iv, lhv.field.field, expr, loc.index)
                }

                is JIRArrayAccess -> {
                    val iv = getLocalRefValue(lhv.array, inst) as? RefValue ?: return null
                    return Stmt.ArrayStore(iv, SimpleValue.Primitive, expr, loc.index)
                }

                else -> error("Unexpected lhv: $lhv")
            }
        }

        else -> error("Unexpected lhv: $lhv")
    }
}

private fun InstEvalContext.evalCall(
    expr: JIRCallExpr,
    loc: JIRInst,
    lValue: JIRValue?,
): Stmt? {
    val lhs = (lValue as? JIRLocalVar)?.let { createLocal(it.index) }

    if (expr.method.method.isPrimitiveBoxAllocMethod()) {
        if (lhs == null) return null
        return Stmt.Assign(lhs, Expr.Alloc(loc), loc.location.index)
    }

    val args = expr.args.map { evalSimpleValue(it as JIRImmediate, loc) }
    val instance = (expr as? JIRInstanceCallExpr)?.instance?.let { evalSimpleValue(it as JIRImmediate, loc) }
    val stmt = Stmt.Call(expr.method.method, lhs, instance, args, loc.location.index)
    return stmt
}

private fun InstEvalContext.evalExpr(expr: JIRExpr, inst: JIRInst): ExprOrValue = when (expr) {
    is JIRNewExpr,
    is JIRNewArrayExpr -> Expr.Alloc(inst)
    is JIRCastExpr -> evalExpr(expr.operand, inst)
    is JIRValue -> evalValue(expr, inst)
    else -> Expr.Unknown
}

private fun InstEvalContext.evalValue(value: JIRValue, loc: JIRInst): ExprOrValue = when (value) {
    is JIRImmediate -> evalSimpleValue(value, loc)
    is JIRRef -> evalRefValue(value, loc)
    else -> Expr.Unknown
}

private fun InstEvalContext.evalSimpleValue(value: JIRImmediate, loc: JIRInst): Value {
    if (value.type is JIRPrimitiveType) return SimpleValue.Primitive
    return when (value) {
        is JIRConstant -> SimpleValue.RefConst(value)
        is JIRThis,
        is JIRArgument,
        is JIRLocalVar -> getLocalRefValue(value, loc)

        else -> error("Unexpected value: $value")
    }
}

private fun InstEvalContext.evalRefValue(value: JIRRef, loc: JIRInst): ExprOrValue {
    return when (value) {
        is JIRFieldRef -> {
            if (value.field.isStatic) {
                val type = value.field.enclosingType.typeName
                Expr.FieldLoad(RefValue.Static(type), value.field.field)
            }
            else {
                val instance = value.instance ?: return Expr.Alloc(loc)
                val iv = getLocalRefValue(instance, loc) as? RefValue ?: return Expr.Unknown
                Expr.FieldLoad(iv, value.field.field)
            }
        }

        is JIRArrayAccess -> {
            val instance = value.array
            val iv = getLocalRefValue(instance, loc) as? RefValue ?: return Expr.Unknown
            Expr.ArrayLoad(iv, SimpleValue.Primitive)
        }

        else -> Expr.Unknown
    }
}

private fun InstEvalContext.getLocalRefValue(local: JIRValue, loc: JIRInst): Value = when (local) {
    is JIRThis -> createThis(isOuter = !loc.location.method.isConstructor)
    is JIRArgument -> createArg(local.index)
    is JIRLocalVar -> createLocal(local.index)
    else -> error("Unexpected local: $local")
}

private const val stdPrimitiveBoxMethodName = "valueOf"

private val stdPrimitiveBoxes = setOf(
    "java.lang.Boolean",
    "java.lang.Byte",
    "java.lang.Char",
    "java.lang.Short",
    "java.lang.Integer",
    "java.lang.Long",
    "java.lang.Float",
    "java.lang.Double",
)

private fun JIRMethod.isPrimitiveBoxAllocMethod(): Boolean =
    name == stdPrimitiveBoxMethodName && enclosingClass.name in stdPrimitiveBoxes
