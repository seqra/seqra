
package org.opentaint.ir.impl.cfg.util

import org.opentaint.ir.api.TypeName
import org.opentaint.ir.api.cfg.JIRRawAddExpr
import org.opentaint.ir.api.cfg.JIRRawAndExpr
import org.opentaint.ir.api.cfg.JIRRawArgument
import org.opentaint.ir.api.cfg.JIRRawArrayAccess
import org.opentaint.ir.api.cfg.JIRRawAssignInst
import org.opentaint.ir.api.cfg.JIRRawBinaryExpr
import org.opentaint.ir.api.cfg.JIRRawBool
import org.opentaint.ir.api.cfg.JIRRawByte
import org.opentaint.ir.api.cfg.JIRRawCallExpr
import org.opentaint.ir.api.cfg.JIRRawCallInst
import org.opentaint.ir.api.cfg.JIRRawCastExpr
import org.opentaint.ir.api.cfg.JIRRawCatchInst
import org.opentaint.ir.api.cfg.JIRRawChar
import org.opentaint.ir.api.cfg.JIRRawClassConstant
import org.opentaint.ir.api.cfg.JIRRawCmpExpr
import org.opentaint.ir.api.cfg.JIRRawCmpgExpr
import org.opentaint.ir.api.cfg.JIRRawCmplExpr
import org.opentaint.ir.api.cfg.JIRRawConditionExpr
import org.opentaint.ir.api.cfg.JIRRawDivExpr
import org.opentaint.ir.api.cfg.JIRRawDouble
import org.opentaint.ir.api.cfg.JIRRawDynamicCallExpr
import org.opentaint.ir.api.cfg.JIRRawEnterMonitorInst
import org.opentaint.ir.api.cfg.JIRRawEqExpr
import org.opentaint.ir.api.cfg.JIRRawExitMonitorInst
import org.opentaint.ir.api.cfg.JIRRawExpr
import org.opentaint.ir.api.cfg.JIRRawExprVisitor
import org.opentaint.ir.api.cfg.JIRRawFieldRef
import org.opentaint.ir.api.cfg.JIRRawFloat
import org.opentaint.ir.api.cfg.JIRRawGeExpr
import org.opentaint.ir.api.cfg.JIRRawGotoInst
import org.opentaint.ir.api.cfg.JIRRawGtExpr
import org.opentaint.ir.api.cfg.JIRRawIfInst
import org.opentaint.ir.api.cfg.JIRRawInst
import org.opentaint.ir.api.cfg.JIRRawInstVisitor
import org.opentaint.ir.api.cfg.JIRRawInstanceOfExpr
import org.opentaint.ir.api.cfg.JIRRawInt
import org.opentaint.ir.api.cfg.JIRRawInterfaceCallExpr
import org.opentaint.ir.api.cfg.JIRRawLabelInst
import org.opentaint.ir.api.cfg.JIRRawLeExpr
import org.opentaint.ir.api.cfg.JIRRawLengthExpr
import org.opentaint.ir.api.cfg.JIRRawLocal
import org.opentaint.ir.api.cfg.JIRRawLong
import org.opentaint.ir.api.cfg.JIRRawLtExpr
import org.opentaint.ir.api.cfg.JIRRawMethodConstant
import org.opentaint.ir.api.cfg.JIRRawMulExpr
import org.opentaint.ir.api.cfg.JIRRawNegExpr
import org.opentaint.ir.api.cfg.JIRRawNeqExpr
import org.opentaint.ir.api.cfg.JIRRawNewArrayExpr
import org.opentaint.ir.api.cfg.JIRRawNewExpr
import org.opentaint.ir.api.cfg.JIRRawNullConstant
import org.opentaint.ir.api.cfg.JIRRawOrExpr
import org.opentaint.ir.api.cfg.JIRRawRemExpr
import org.opentaint.ir.api.cfg.JIRRawReturnInst
import org.opentaint.ir.api.cfg.JIRRawShlExpr
import org.opentaint.ir.api.cfg.JIRRawShort
import org.opentaint.ir.api.cfg.JIRRawShrExpr
import org.opentaint.ir.api.cfg.JIRRawSimpleValue
import org.opentaint.ir.api.cfg.JIRRawSpecialCallExpr
import org.opentaint.ir.api.cfg.JIRRawStaticCallExpr
import org.opentaint.ir.api.cfg.JIRRawStringConstant
import org.opentaint.ir.api.cfg.JIRRawSubExpr
import org.opentaint.ir.api.cfg.JIRRawSwitchInst
import org.opentaint.ir.api.cfg.JIRRawThis
import org.opentaint.ir.api.cfg.JIRRawThrowInst
import org.opentaint.ir.api.cfg.JIRRawUshrExpr
import org.opentaint.ir.api.cfg.JIRRawValue
import org.opentaint.ir.api.cfg.JIRRawVirtualCallExpr
import org.opentaint.ir.api.cfg.JIRRawXorExpr

class ExprMapper(val mapping: Map<JIRRawExpr, JIRRawExpr>) : JIRRawInstVisitor<JIRRawInst>, JIRRawExprVisitor<JIRRawExpr> {
    override fun visitJcRawAssignInst(inst: JIRRawAssignInst): JIRRawInst {
        val newLhv = inst.lhv.accept(this) as JIRRawValue
        val newRhv = inst.rhv.accept(this)
        return when {
            inst.lhv == newLhv && inst.rhv == newRhv -> inst
            else -> JIRRawAssignInst(newLhv, newRhv)
        }
    }

    override fun visitJcRawEnterMonitorInst(inst: JIRRawEnterMonitorInst): JIRRawInst {
        val newMonitor = inst.monitor.accept(this) as JIRRawSimpleValue
        return when (inst.monitor) {
            newMonitor -> inst
            else -> JIRRawEnterMonitorInst(newMonitor)
        }
    }

    override fun visitJcRawExitMonitorInst(inst: JIRRawExitMonitorInst): JIRRawInst {
        val newMonitor = inst.monitor.accept(this) as JIRRawSimpleValue
        return when (inst.monitor) {
            newMonitor -> inst
            else -> JIRRawExitMonitorInst(newMonitor)
        }
    }

    override fun visitJcRawCallInst(inst: JIRRawCallInst): JIRRawInst {
        val newCall = inst.callExpr.accept(this) as JIRRawCallExpr
        return when (inst.callExpr) {
            newCall -> inst
            else -> JIRRawCallInst(newCall)
        }
    }

    override fun visitJcRawLabelInst(inst: JIRRawLabelInst): JIRRawInst {
        return inst
    }

    override fun visitJcRawReturnInst(inst: JIRRawReturnInst): JIRRawInst {
        val newReturn = inst.returnValue?.accept(this) as? JIRRawValue
        return when (inst.returnValue) {
            newReturn -> inst
            else -> JIRRawReturnInst(newReturn)
        }
    }

    override fun visitJcRawThrowInst(inst: JIRRawThrowInst): JIRRawInst {
        val newThrowable = inst.throwable.accept(this) as JIRRawValue
        return when (inst.throwable) {
            newThrowable -> inst
            else -> JIRRawThrowInst(newThrowable)
        }
    }

    override fun visitJcRawCatchInst(inst: JIRRawCatchInst): JIRRawInst {
        val newThrowable = inst.throwable.accept(this) as JIRRawValue
        return when (inst.throwable) {
            newThrowable -> inst
            else -> JIRRawCatchInst(newThrowable, inst.handler, inst.startInclusive, inst.endExclusive)
        }
    }

    override fun visitJcRawGotoInst(inst: JIRRawGotoInst): JIRRawInst {
        return inst
    }

    override fun visitJcRawIfInst(inst: JIRRawIfInst): JIRRawInst {
        val newCondition = inst.condition.accept(this) as JIRRawConditionExpr
        return when (inst.condition) {
            newCondition -> inst
            else -> JIRRawIfInst(newCondition, inst.trueBranch, inst.falseBranch)
        }
    }

    override fun visitJcRawSwitchInst(inst: JIRRawSwitchInst): JIRRawInst {
        val newKey = inst.key.accept(this) as JIRRawValue
        val newBranches = inst.branches.mapKeys { it.key.accept(this) as JIRRawValue }
        return when {
            inst.key == newKey && inst.branches == newBranches -> inst
            else -> JIRRawSwitchInst(newKey, newBranches, inst.default)
        }
    }

    private fun <T : JIRRawExpr> exprHandler(expr: T, handler: () -> JIRRawExpr): JIRRawExpr {
        if (expr in mapping) return mapping.getValue(expr)
        return handler()
    }

    private fun <T : JIRRawBinaryExpr> binaryHandler(expr: T, handler: (TypeName, JIRRawValue, JIRRawValue) -> T) =
        exprHandler(expr) {
            val newLhv = expr.lhv.accept(this) as JIRRawValue
            val newRhv = expr.rhv.accept(this) as JIRRawValue
            when {
                expr.lhv == newLhv && expr.rhv == newRhv -> expr
                else -> handler(newLhv.typeName, newLhv, newRhv)
            }
        }

    override fun visitJcRawAddExpr(expr: JIRRawAddExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawAddExpr(type, lhv, rhv)
    }

    override fun visitJcRawAndExpr(expr: JIRRawAndExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawAndExpr(type, lhv, rhv)
    }

    override fun visitJcRawCmpExpr(expr: JIRRawCmpExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawCmpExpr(type, lhv, rhv)
    }

    override fun visitJcRawCmpgExpr(expr: JIRRawCmpgExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawCmpgExpr(type, lhv, rhv)
    }

    override fun visitJcRawCmplExpr(expr: JIRRawCmplExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawCmplExpr(type, lhv, rhv)
    }

    override fun visitJcRawDivExpr(expr: JIRRawDivExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawDivExpr(type, lhv, rhv)
    }

    override fun visitJcRawMulExpr(expr: JIRRawMulExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawMulExpr(type, lhv, rhv)
    }

    override fun visitJcRawEqExpr(expr: JIRRawEqExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JIRRawEqExpr(expr.typeName, lhv, rhv)
    }

    override fun visitJcRawNeqExpr(expr: JIRRawNeqExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JIRRawNeqExpr(expr.typeName, lhv, rhv)
    }

    override fun visitJcRawGeExpr(expr: JIRRawGeExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JIRRawGeExpr(expr.typeName, lhv, rhv)
    }

    override fun visitJcRawGtExpr(expr: JIRRawGtExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JIRRawGtExpr(expr.typeName, lhv, rhv)
    }

    override fun visitJcRawLeExpr(expr: JIRRawLeExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JIRRawLeExpr(expr.typeName, lhv, rhv)
    }

    override fun visitJcRawLtExpr(expr: JIRRawLtExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JIRRawLtExpr(expr.typeName, lhv, rhv)
    }

    override fun visitJcRawOrExpr(expr: JIRRawOrExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawOrExpr(type, lhv, rhv)
    }

    override fun visitJcRawRemExpr(expr: JIRRawRemExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawRemExpr(type, lhv, rhv)
    }

    override fun visitJcRawShlExpr(expr: JIRRawShlExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawShlExpr(type, lhv, rhv)
    }

    override fun visitJcRawShrExpr(expr: JIRRawShrExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawShrExpr(type, lhv, rhv)
    }

    override fun visitJcRawSubExpr(expr: JIRRawSubExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawSubExpr(type, lhv, rhv)
    }

    override fun visitJcRawUshrExpr(expr: JIRRawUshrExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawUshrExpr(type, lhv, rhv)
    }

    override fun visitJcRawXorExpr(expr: JIRRawXorExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawXorExpr(type, lhv, rhv)
    }

    override fun visitJcRawLengthExpr(expr: JIRRawLengthExpr) = exprHandler(expr) {
        val newArray = expr.array.accept(this) as JIRRawValue
        when (expr.array) {
            newArray -> expr
            else -> JIRRawLengthExpr(expr.typeName, newArray)
        }
    }

    override fun visitJcRawNegExpr(expr: JIRRawNegExpr) = exprHandler(expr) {
        val newOperand = expr.operand.accept(this) as JIRRawValue
        when (expr.operand) {
            newOperand -> expr
            else -> JIRRawNegExpr(newOperand.typeName, newOperand)
        }
    }

    override fun visitJcRawCastExpr(expr: JIRRawCastExpr) = exprHandler(expr) {
        val newOperand = expr.operand.accept(this) as JIRRawValue
        when (expr.operand) {
            newOperand -> expr
            else -> JIRRawCastExpr(expr.typeName, newOperand)
        }
    }

    override fun visitJcRawNewExpr(expr: JIRRawNewExpr) = exprHandler(expr) { expr }

    override fun visitJcRawNewArrayExpr(expr: JIRRawNewArrayExpr) = exprHandler(expr) {
        val newDimensions = expr.dimensions.map { it.accept(this) as JIRRawValue }
        when (expr.dimensions) {
            newDimensions -> expr
            else -> JIRRawNewArrayExpr(expr.typeName, newDimensions)
        }
    }

    override fun visitJcRawInstanceOfExpr(expr: JIRRawInstanceOfExpr) = exprHandler(expr) {
        val newOperand = expr.operand.accept(this) as JIRRawValue
        when (expr.operand) {
            newOperand -> expr
            else -> JIRRawInstanceOfExpr(expr.typeName, newOperand, expr.targetType)
        }
    }

    override fun visitJcRawDynamicCallExpr(expr: JIRRawDynamicCallExpr) = exprHandler(expr) {
        val newArgs = expr.args.map { it.accept(this) as JIRRawValue }
        when (expr.args) {
            newArgs -> expr
            else -> JIRRawDynamicCallExpr(
                expr.bsm,
                expr.bsmArgs,
                expr.callCiteMethodName,
                expr.callCiteArgTypes,
                expr.callCiteReturnType,
                newArgs
            )
        }
    }

    override fun visitJcRawVirtualCallExpr(expr: JIRRawVirtualCallExpr) = exprHandler(expr) {
        val newInstance = expr.instance.accept(this) as JIRRawValue
        val newArgs = expr.args.map { it.accept(this) as JIRRawValue }
        when {
            expr.instance == newInstance && expr.args == newArgs -> expr
            else -> JIRRawVirtualCallExpr(
                expr.declaringClass,
                expr.methodName,
                expr.argumentTypes,
                expr.returnType,
                newInstance,
                newArgs
            )
        }
    }

    override fun visitJcRawInterfaceCallExpr(expr: JIRRawInterfaceCallExpr) = exprHandler(expr) {
        val newInstance = expr.instance.accept(this) as JIRRawValue
        val newArgs = expr.args.map { it.accept(this) as JIRRawValue }
        when {
            expr.instance == newInstance && expr.args == newArgs -> expr
            else -> JIRRawInterfaceCallExpr(
                expr.declaringClass,
                expr.methodName,
                expr.argumentTypes,
                expr.returnType,
                newInstance,
                newArgs
            )
        }
    }

    override fun visitJcRawStaticCallExpr(expr: JIRRawStaticCallExpr) = exprHandler(expr) {
        val newArgs = expr.args.map { it.accept(this) as JIRRawValue }
        when (expr.args) {
            newArgs -> expr
            else -> JIRRawStaticCallExpr(
                expr.declaringClass, expr.methodName, expr.argumentTypes, expr.returnType, newArgs
            )
        }
    }

    override fun visitJcRawSpecialCallExpr(expr: JIRRawSpecialCallExpr) = exprHandler(expr) {
        val newInstance = expr.instance.accept(this) as JIRRawValue
        val newArgs = expr.args.map { it.accept(this) as JIRRawValue }
        when {
            expr.instance == newInstance && expr.args == newArgs -> expr
            else -> JIRRawSpecialCallExpr(
                expr.declaringClass,
                expr.methodName,
                expr.argumentTypes,
                expr.returnType,
                newInstance,
                newArgs
            )
        }
    }


    override fun visitJcRawThis(value: JIRRawThis) = exprHandler(value) { value }
    override fun visitJcRawArgument(value: JIRRawArgument) = exprHandler(value) { value }
    override fun visitJcRawLocal(value: JIRRawLocal) = exprHandler(value) { value }

    override fun visitJcRawFieldRef(value: JIRRawFieldRef) = exprHandler(value) {
        val newInstance = value.instance?.accept(this) as? JIRRawValue
        when (value.instance) {
            newInstance -> value
            else -> JIRRawFieldRef(newInstance, value.declaringClass, value.fieldName, value.typeName)
        }
    }

    override fun visitJcRawArrayAccess(value: JIRRawArrayAccess) = exprHandler(value) {
        val newArray = value.array.accept(this) as JIRRawValue
        val newIndex = value.index.accept(this) as JIRRawValue
        when {
            value.array == newArray && value.index == newIndex -> value
            else -> JIRRawArrayAccess(newArray, newIndex, value.typeName)
        }
    }

    override fun visitJcRawBool(value: JIRRawBool) = exprHandler(value) { value }
    override fun visitJcRawByte(value: JIRRawByte) = exprHandler(value) { value }
    override fun visitJcRawChar(value: JIRRawChar) = exprHandler(value) { value }
    override fun visitJcRawShort(value: JIRRawShort) = exprHandler(value) { value }
    override fun visitJcRawInt(value: JIRRawInt) = exprHandler(value) { value }
    override fun visitJcRawLong(value: JIRRawLong) = exprHandler(value) { value }
    override fun visitJcRawFloat(value: JIRRawFloat) = exprHandler(value) { value }
    override fun visitJcRawDouble(value: JIRRawDouble) = exprHandler(value) { value }
    override fun visitJcRawNullConstant(value: JIRRawNullConstant) = exprHandler(value) { value }
    override fun visitJcRawStringConstant(value: JIRRawStringConstant) = exprHandler(value) { value }
    override fun visitJcRawClassConstant(value: JIRRawClassConstant) = exprHandler(value) { value }
    override fun visitJcRawMethodConstant(value: JIRRawMethodConstant) = exprHandler(value) { value }
}
