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

sealed interface RefValue: Value {
    data class Local(val idx: Int) : RefValue
    data class Arg(val idx: Int) : RefValue
    data class Static(val type: String) : RefValue
    data object This : RefValue
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

sealed interface Stmt {
    val originalIdx: Int

    sealed interface NoCall: Stmt

    data class Call(val method: JIRMethod, val lValue: RefValue.Local?, val args: List<Value>, override val originalIdx: Int) : Stmt

    data class Copy(val lValue: RefValue.Local, val rValue: RefValue, override val originalIdx: Int): NoCall
    data class Assign(val lValue: RefValue.Local, val expr: Expr, override val originalIdx: Int) : NoCall
    data class FieldStore(val instance: RefValue, val field: JIRField, val value: ExprOrValue, override val originalIdx: Int) : NoCall
    data class ArrayStore(val instance: RefValue, val index: SimpleValue.Primitive, val value: ExprOrValue, override val originalIdx: Int) : NoCall
    data class WriteStatic(val field: JIRField, val value: ExprOrValue, override val originalIdx: Int) : NoCall
    data class Return(val value: RefValue?, override val originalIdx: Int) : NoCall
    data class Throw(val value: RefValue, override val originalIdx: Int) : NoCall
}

fun evalInst(inst: JIRInst): Stmt? {
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

            val lValue = RefValue.Local(local.index)
            val stmt = Stmt.Assign(lValue, expr, inst.location.index)
            return stmt
        }

        is JIRReturnInst -> {
            val value = inst.returnValue?.let { evalSimpleValue(it as JIRImmediate) } as? RefValue
            val stmt = Stmt.Return(value, inst.location.index)
            return stmt
        }

        is JIRThrowInst -> {
            val value = getLocalRefValue(inst.throwable)
            val stmt = Stmt.Throw(value, inst.location.index)
            return stmt
        }

        else -> null
    }
}

private fun evalAssign(lhv: JIRValue, rhv: JIRExpr, inst: JIRInst): Stmt? {
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
            val lValue = RefValue.Local(lhv.index)
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
                    val iv = getLocalRefValue(instance)
                    return Stmt.FieldStore(iv, lhv.field.field, expr, loc.index)
                }

                is JIRArrayAccess -> {
                    val iv = getLocalRefValue(lhv.array)
                    return Stmt.ArrayStore(iv, SimpleValue.Primitive, expr, loc.index)
                }

                else -> error("Unexpected lhv: $lhv")
            }
        }

        else -> error("Unexpected lhv: $lhv")
    }
}

private fun evalCall(
    expr: JIRCallExpr,
    loc: JIRInst,
    lValue: JIRValue?,
): Stmt {
    val args = expr.args.map { evalSimpleValue(it as JIRImmediate) }
    val lhs = (lValue as? JIRLocalVar)?.let { RefValue.Local(it.index) }
    val stmt = Stmt.Call(expr.method.method, lhs, args, loc.location.index)
    return stmt
}

private fun evalExpr(expr: JIRExpr, inst: JIRInst): ExprOrValue = when (expr) {
    is JIRNewExpr,
    is JIRNewArrayExpr -> Expr.Alloc(inst)
    is JIRCastExpr -> evalExpr(expr.operand, inst)
    is JIRValue -> evalValue(expr, inst)
    else -> Expr.Unknown
}

private fun evalValue(value: JIRValue, loc: JIRInst): ExprOrValue = when (value) {
    is JIRImmediate -> evalSimpleValue(value)
    is JIRRef -> evalRefValue(value, loc)
    else -> Expr.Unknown
}

private fun evalSimpleValue(value: JIRImmediate): Value {
    if (value.type is JIRPrimitiveType) return SimpleValue.Primitive
    return when (value) {
        is JIRConstant -> SimpleValue.RefConst(value)
        is JIRThis,
        is JIRArgument,
        is JIRLocalVar -> getLocalRefValue(value)

        else -> error("Unexpected value: $value")
    }
}

private fun evalRefValue(value: JIRRef, loc: JIRInst): ExprOrValue {
    return when (value) {
        is JIRFieldRef -> {
            if (value.field.isStatic) {
                val type = value.field.enclosingType.typeName
                Expr.FieldLoad(RefValue.Static(type), value.field.field)
            }
            else {
                val instance = value.instance ?: return Expr.Alloc(loc)
                val iv = getLocalRefValue(instance)
                Expr.FieldLoad(iv, value.field.field)
            }
        }

        is JIRArrayAccess -> {
            val instance = value.array
            val iv = getLocalRefValue(instance)
            Expr.ArrayLoad(iv, SimpleValue.Primitive)
        }

        else -> Expr.Unknown
    }
}

private fun getLocalRefValue(local: JIRValue): RefValue = when (local) {
    is JIRThis -> RefValue.This
    is JIRArgument -> RefValue.Arg(local.index)
    is JIRLocalVar -> RefValue.Local(local.index)
    else -> error("Unexpected local: $local")
}
