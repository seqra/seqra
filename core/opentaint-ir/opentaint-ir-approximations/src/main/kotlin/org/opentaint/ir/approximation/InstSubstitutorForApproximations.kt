package org.opentaint.ir.approximation

import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.api.jvm.cfg.BsmDoubleArg
import org.opentaint.ir.api.jvm.cfg.BsmFloatArg
import org.opentaint.ir.api.jvm.cfg.BsmHandle
import org.opentaint.ir.api.jvm.cfg.BsmIntArg
import org.opentaint.ir.api.jvm.cfg.BsmLongArg
import org.opentaint.ir.api.jvm.cfg.BsmMethodTypeArg
import org.opentaint.ir.api.jvm.cfg.BsmStringArg
import org.opentaint.ir.api.jvm.cfg.BsmTypeArg
import org.opentaint.ir.api.jvm.cfg.JIRRawAddExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawAndExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawArgument
import org.opentaint.ir.api.jvm.cfg.JIRRawArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRRawAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRRawBinaryExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawBool
import org.opentaint.ir.api.jvm.cfg.JIRRawByte
import org.opentaint.ir.api.jvm.cfg.JIRRawCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawCallInst
import org.opentaint.ir.api.jvm.cfg.JIRRawCastExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawCatchInst
import org.opentaint.ir.api.jvm.cfg.JIRRawChar
import org.opentaint.ir.api.jvm.cfg.JIRRawClassConstant
import org.opentaint.ir.api.jvm.cfg.JIRRawCmpExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawCmpgExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawCmplExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawConditionExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawDivExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawDouble
import org.opentaint.ir.api.jvm.cfg.JIRRawDynamicCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawEnterMonitorInst
import org.opentaint.ir.api.jvm.cfg.JIRRawEqExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawExitMonitorInst
import org.opentaint.ir.api.jvm.cfg.JIRRawExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawExprVisitor
import org.opentaint.ir.api.jvm.cfg.JIRRawFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRRawFloat
import org.opentaint.ir.api.jvm.cfg.JIRRawGeExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawGotoInst
import org.opentaint.ir.api.jvm.cfg.JIRRawGtExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawIfInst
import org.opentaint.ir.api.jvm.cfg.JIRRawInst
import org.opentaint.ir.api.jvm.cfg.JIRRawInstVisitor
import org.opentaint.ir.api.jvm.cfg.JIRRawInstanceOfExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawInt
import org.opentaint.ir.api.jvm.cfg.JIRRawInterfaceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawLabelInst
import org.opentaint.ir.api.jvm.cfg.JIRRawLeExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawLengthExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawLineNumberInst
import org.opentaint.ir.api.jvm.cfg.JIRRawLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRRawLong
import org.opentaint.ir.api.jvm.cfg.JIRRawLtExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawMethodConstant
import org.opentaint.ir.api.jvm.cfg.JIRRawMethodType
import org.opentaint.ir.api.jvm.cfg.JIRRawMulExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawNegExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawNeqExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawNewArrayExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawNewExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawNullConstant
import org.opentaint.ir.api.jvm.cfg.JIRRawOrExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawRemExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRRawShlExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawShort
import org.opentaint.ir.api.jvm.cfg.JIRRawShrExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawSimpleValue
import org.opentaint.ir.api.jvm.cfg.JIRRawSpecialCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawStaticCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawStringConstant
import org.opentaint.ir.api.jvm.cfg.JIRRawSubExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawSwitchInst
import org.opentaint.ir.api.jvm.cfg.JIRRawThis
import org.opentaint.ir.api.jvm.cfg.JIRRawThrowInst
import org.opentaint.ir.api.jvm.cfg.JIRRawUshrExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawValue
import org.opentaint.ir.api.jvm.cfg.JIRRawVirtualCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRRawXorExpr
import org.opentaint.ir.impl.types.TypeNameImpl

/**
 * Removes all occurrences of approximations with their targets in [JIRRawInst]s and [JIRRawExpr]s.
 */
class InstSubstitutorForApproximations(
    private val approximations: Approximations
) : JIRRawInstVisitor<JIRRawInst>, JIRRawExprVisitor<JIRRawExpr> {
    override fun visitJIRRawAssignInst(inst: JIRRawAssignInst): JIRRawInst {
        val newLhv = inst.lhv.accept(this) as JIRRawValue
        val newRhv = inst.rhv.accept(this)
        return JIRRawAssignInst(inst.owner, newLhv, newRhv)
    }

    override fun visitJIRRawEnterMonitorInst(inst: JIRRawEnterMonitorInst): JIRRawInst {
        val newMonitor = inst.monitor.accept(this) as JIRRawSimpleValue
        return JIRRawEnterMonitorInst(inst.owner, newMonitor)
    }

    override fun visitJIRRawExitMonitorInst(inst: JIRRawExitMonitorInst): JIRRawInst {
        val newMonitor = inst.monitor.accept(this) as JIRRawSimpleValue
        return JIRRawExitMonitorInst(inst.owner, newMonitor)
    }

    override fun visitJIRRawCallInst(inst: JIRRawCallInst): JIRRawInst {
        val newCall = inst.callExpr.accept(this) as JIRRawCallExpr
        return JIRRawCallInst(inst.owner, newCall)
    }

    override fun visitJIRRawLabelInst(inst: JIRRawLabelInst): JIRRawInst {
        return JIRRawLabelInst(inst.owner, inst.name)
    }

    override fun visitJIRRawLineNumberInst(inst: JIRRawLineNumberInst): JIRRawInst {
        return JIRRawLineNumberInst(inst.owner, inst.lineNumber, inst.start)
    }

    override fun visitJIRRawReturnInst(inst: JIRRawReturnInst): JIRRawInst {
        val newReturn = inst.returnValue?.accept(this) as? JIRRawValue
        return JIRRawReturnInst(inst.owner, newReturn)
    }

    override fun visitJIRRawThrowInst(inst: JIRRawThrowInst): JIRRawInst {
        val newThrowable = inst.throwable.accept(this) as JIRRawValue
        return JIRRawThrowInst(inst.owner, newThrowable)
    }

    override fun visitJIRRawCatchInst(inst: JIRRawCatchInst): JIRRawInst {
        val newThrowable = inst.throwable.accept(this) as JIRRawValue
        val entries = inst.entries.map {
            it.copy(acceptedThrowable = it.acceptedThrowable.eliminateApproximation(approximations))
        }

        return JIRRawCatchInst(inst.owner, newThrowable, inst.handler, entries)
    }

    override fun visitJIRRawGotoInst(inst: JIRRawGotoInst): JIRRawInst {
        return JIRRawGotoInst(inst.owner, inst.target)
    }

    override fun visitJIRRawIfInst(inst: JIRRawIfInst): JIRRawInst {
        val newCondition = inst.condition.accept(this) as JIRRawConditionExpr
        return JIRRawIfInst(inst.owner, newCondition, inst.trueBranch, inst.falseBranch)
    }

    override fun visitJIRRawSwitchInst(inst: JIRRawSwitchInst): JIRRawInst {
        val newKey = inst.key.accept(this) as JIRRawValue
        val newBranches = inst.branches.mapKeys { it.key.accept(this) as JIRRawValue }
        return JIRRawSwitchInst(inst.owner, newKey, newBranches, inst.default)
    }

    private fun <T : JIRRawBinaryExpr> binaryHandler(
        expr: T,
        constructor: (TypeName, JIRRawValue, JIRRawValue) -> T,
    ): T {
        val newLhv = expr.lhv.accept(this) as JIRRawValue
        val newRhv = expr.rhv.accept(this) as JIRRawValue

        return constructor(newLhv.typeName.eliminateApproximation(approximations), newLhv, newRhv)
    }

    override fun visitJIRRawAddExpr(expr: JIRRawAddExpr): JIRRawExpr = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawAddExpr(type, lhv, rhv)
    }

    override fun visitJIRRawAndExpr(expr: JIRRawAndExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawAndExpr(type, lhv, rhv)
    }

    override fun visitJIRRawCmpExpr(expr: JIRRawCmpExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawCmpExpr(type, lhv, rhv)
    }

    override fun visitJIRRawCmpgExpr(expr: JIRRawCmpgExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawCmpgExpr(type, lhv, rhv)
    }

    override fun visitJIRRawCmplExpr(expr: JIRRawCmplExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawCmplExpr(type, lhv, rhv)
    }

    override fun visitJIRRawDivExpr(expr: JIRRawDivExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawDivExpr(type, lhv, rhv)
    }

    override fun visitJIRRawMulExpr(expr: JIRRawMulExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawMulExpr(type, lhv, rhv)
    }

    override fun visitJIRRawEqExpr(expr: JIRRawEqExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JIRRawEqExpr(expr.typeName.eliminateApproximation(approximations), lhv, rhv)
    }

    override fun visitJIRRawNeqExpr(expr: JIRRawNeqExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JIRRawNeqExpr(expr.typeName.eliminateApproximation(approximations), lhv, rhv)
    }

    override fun visitJIRRawGeExpr(expr: JIRRawGeExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JIRRawGeExpr(expr.typeName.eliminateApproximation(approximations), lhv, rhv)
    }

    override fun visitJIRRawGtExpr(expr: JIRRawGtExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JIRRawGtExpr(expr.typeName.eliminateApproximation(approximations), lhv, rhv)
    }

    override fun visitJIRRawLeExpr(expr: JIRRawLeExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JIRRawLeExpr(expr.typeName.eliminateApproximation(approximations), lhv, rhv)
    }

    override fun visitJIRRawLtExpr(expr: JIRRawLtExpr) = binaryHandler(expr) { _, lhv, rhv ->
        JIRRawLtExpr(expr.typeName.eliminateApproximation(approximations), lhv, rhv)
    }

    override fun visitJIRRawOrExpr(expr: JIRRawOrExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawOrExpr(type, lhv, rhv)
    }

    override fun visitJIRRawRemExpr(expr: JIRRawRemExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawRemExpr(type, lhv, rhv)
    }

    override fun visitJIRRawShlExpr(expr: JIRRawShlExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawShlExpr(type, lhv, rhv)
    }

    override fun visitJIRRawShrExpr(expr: JIRRawShrExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawShrExpr(type, lhv, rhv)
    }

    override fun visitJIRRawSubExpr(expr: JIRRawSubExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawSubExpr(type, lhv, rhv)
    }

    override fun visitJIRRawUshrExpr(expr: JIRRawUshrExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawUshrExpr(type, lhv, rhv)
    }

    override fun visitJIRRawXorExpr(expr: JIRRawXorExpr) = binaryHandler(expr) { type, lhv, rhv ->
        JIRRawXorExpr(type, lhv, rhv)
    }

    override fun visitJIRRawLengthExpr(expr: JIRRawLengthExpr): JIRRawExpr {
        val newArray = expr.array.accept(this) as JIRRawValue
        return JIRRawLengthExpr(expr.typeName.eliminateApproximation(approximations), newArray)
    }

    override fun visitJIRRawNegExpr(expr: JIRRawNegExpr): JIRRawExpr {
        val newOperand = expr.operand.accept(this) as JIRRawValue
        return JIRRawNegExpr(newOperand.typeName.eliminateApproximation(approximations), newOperand)
    }

    override fun visitJIRRawCastExpr(expr: JIRRawCastExpr): JIRRawExpr {
        val newOperand = expr.operand.accept(this) as JIRRawValue
        return JIRRawCastExpr(expr.typeName.eliminateApproximation(approximations), newOperand)
    }

    override fun visitJIRRawNewExpr(expr: JIRRawNewExpr): JIRRawExpr {
        return expr.eliminateApproximations(expr.typeName) { expr.copy(typeName = it) }
    }

    override fun visitJIRRawNewArrayExpr(expr: JIRRawNewArrayExpr): JIRRawExpr {
        val newDimensions = expr.dimensions.map { it.accept(this) as JIRRawValue }
        return JIRRawNewArrayExpr(expr.typeName.eliminateApproximation(approximations), newDimensions)
    }

    override fun visitJIRRawInstanceOfExpr(expr: JIRRawInstanceOfExpr): JIRRawExpr {
        val newOperand = expr.operand.accept(this) as JIRRawValue
        return JIRRawInstanceOfExpr(
            expr.typeName.eliminateApproximation(approximations),
            newOperand,
            expr.targetType.eliminateApproximation(approximations)
        )
    }

    private fun BsmHandle.eliminateApproximations(): BsmHandle = copy(
        declaringClass = declaringClass.eliminateApproximation(approximations),
        argTypes = argTypes.map { it.eliminateApproximation(approximations) },
        returnType = returnType.eliminateApproximation(approximations)
    )

    override fun visitJIRRawDynamicCallExpr(expr: JIRRawDynamicCallExpr): JIRRawExpr {
        with(expr) {
            val newArgs = args.map { it.accept(this@InstSubstitutorForApproximations) as JIRRawValue }

            return JIRRawDynamicCallExpr(
                bsm.eliminateApproximations(),
                bsmArgs.map { arg ->
                    when (arg) {
                        is BsmDoubleArg -> arg
                        is BsmFloatArg -> arg
                        is BsmHandle -> arg.eliminateApproximations()
                        is BsmIntArg -> arg
                        is BsmLongArg -> arg
                        is BsmMethodTypeArg -> arg.copy(
                            arg.argumentTypes.map { it.eliminateApproximation(approximations) },
                            arg.returnType.eliminateApproximation(approximations)
                        )

                        is BsmStringArg -> arg
                        is BsmTypeArg -> arg.copy(arg.typeName.eliminateApproximation(approximations))
                    }
                },
                callSiteMethodName,
                callSiteArgTypes.map { it.eliminateApproximation(approximations) },
                callSiteReturnType.eliminateApproximation(approximations),
                newArgs
            )
        }
    }

    override fun visitJIRRawVirtualCallExpr(expr: JIRRawVirtualCallExpr): JIRRawExpr {
        val newInstance = expr.instance.accept(this) as JIRRawValue
        val newArgs = expr.args.map { it.accept(this) as JIRRawValue }

        return with(expr) {
            JIRRawVirtualCallExpr(
                declaringClass.eliminateApproximation(approximations),
                methodName,
                argumentTypes.map { it.eliminateApproximation(approximations) },
                returnType.eliminateApproximation(approximations),
                newInstance,
                newArgs
            )
        }
    }

    override fun visitJIRRawInterfaceCallExpr(expr: JIRRawInterfaceCallExpr): JIRRawExpr {
        val newInstance = expr.instance.accept(this) as JIRRawValue
        val newArgs = expr.args.map { it.accept(this) as JIRRawValue }

        return with(expr) {
            JIRRawInterfaceCallExpr(
                declaringClass.eliminateApproximation(approximations),
                methodName,
                argumentTypes.map { it.eliminateApproximation(approximations) },
                returnType.eliminateApproximation(approximations),
                newInstance,
                newArgs
            )
        }
    }

    override fun visitJIRRawStaticCallExpr(expr: JIRRawStaticCallExpr): JIRRawExpr {
        val newArgs = expr.args.map { it.accept(this) as JIRRawValue }

        return with(expr) {
            JIRRawStaticCallExpr(
                declaringClass.eliminateApproximation(approximations),
                methodName,
                argumentTypes.map { it.eliminateApproximation(approximations) },
                returnType.eliminateApproximation(approximations),
                newArgs,
                isInterfaceMethodCall
            )
        }
    }

    override fun visitJIRRawSpecialCallExpr(expr: JIRRawSpecialCallExpr): JIRRawExpr {
        val newInstance = expr.instance.accept(this) as JIRRawValue
        val newArgs = expr.args.map { it.accept(this) as JIRRawValue }

        return with(expr) {
            JIRRawSpecialCallExpr(
                declaringClass.eliminateApproximation(approximations),
                methodName,
                argumentTypes.map { it.eliminateApproximation(approximations) },
                returnType.eliminateApproximation(approximations),
                newInstance,
                newArgs
            )
        }
    }

    override fun visitJIRRawThis(value: JIRRawThis): JIRRawExpr {
        return value.copy(value.typeName.eliminateApproximation(approximations))
    }

    override fun visitJIRRawArgument(value: JIRRawArgument): JIRRawExpr {
        return value.eliminateApproximations(value.typeName) { value.copy(typeName = it) }
    }

    private fun <T : JIRRawExpr> T.eliminateApproximations(typeName: TypeName, constructor: (TypeName) -> T): T {
        val className = typeName.typeName.toApproximationName()
        val originalClassName = approximations.findOriginalByApproximationOrNull(className) ?: return this
        return constructor(TypeNameImpl.fromTypeName(originalClassName))
    }

    override fun visitJIRRawLocalVar(value: JIRRawLocalVar): JIRRawExpr {
        return value.copy(typeName = value.typeName.eliminateApproximation(approximations))
    }

    override fun visitJIRRawFieldRef(value: JIRRawFieldRef): JIRRawExpr {
        val newInstance = value.instance?.accept(this) as? JIRRawValue
        return JIRRawFieldRef(
            newInstance,
            value.declaringClass.eliminateApproximation(approximations),
            value.fieldName,
            value.typeName.eliminateApproximation(approximations)
        )
    }

    override fun visitJIRRawArrayAccess(value: JIRRawArrayAccess): JIRRawExpr {
        val newArray = value.array.accept(this) as JIRRawValue
        val newIndex = value.index.accept(this) as JIRRawValue

        return JIRRawArrayAccess(newArray, newIndex, value.typeName.eliminateApproximation(approximations))
    }

    override fun visitJIRRawBool(value: JIRRawBool): JIRRawExpr {
        return value.copy(typeName = value.typeName.eliminateApproximation(approximations))
    }

    override fun visitJIRRawByte(value: JIRRawByte): JIRRawExpr {
        return value.copy(typeName = value.typeName.eliminateApproximation(approximations))
    }

    override fun visitJIRRawChar(value: JIRRawChar): JIRRawExpr {
        return value.copy(typeName = value.typeName.eliminateApproximation(approximations))
    }

    override fun visitJIRRawShort(value: JIRRawShort): JIRRawExpr {
        return value.copy(typeName = value.typeName.eliminateApproximation(approximations))
    }

    override fun visitJIRRawInt(value: JIRRawInt): JIRRawExpr {
        return value.copy(typeName = value.typeName.eliminateApproximation(approximations))
    }

    override fun visitJIRRawLong(value: JIRRawLong): JIRRawExpr {
        return value.copy(typeName = value.typeName.eliminateApproximation(approximations))
    }

    override fun visitJIRRawFloat(value: JIRRawFloat): JIRRawExpr {
        return value.copy(typeName = value.typeName.eliminateApproximation(approximations))
    }

    override fun visitJIRRawDouble(value: JIRRawDouble): JIRRawExpr {
        return value.copy(typeName = value.typeName.eliminateApproximation(approximations))
    }

    override fun visitJIRRawNullConstant(value: JIRRawNullConstant): JIRRawExpr {
        return value.copy(typeName = value.typeName.eliminateApproximation(approximations))
    }

    override fun visitJIRRawStringConstant(value: JIRRawStringConstant): JIRRawExpr {
        return value.eliminateApproximations(value.typeName) { value.copy(typeName = it) }
    }

    override fun visitJIRRawClassConstant(value: JIRRawClassConstant): JIRRawExpr {
        return JIRRawClassConstant(
            value.className.eliminateApproximation(approximations),
            value.typeName.eliminateApproximation(approximations)
        )
    }

    override fun visitJIRRawMethodConstant(value: JIRRawMethodConstant): JIRRawExpr {
        return with(value) {
            JIRRawMethodConstant(
                declaringClass.eliminateApproximation(approximations),
                name,
                argumentTypes.map { it.eliminateApproximation(approximations) },
                returnType.eliminateApproximation(approximations),
                typeName.eliminateApproximation(approximations)
            )
        }
    }

    override fun visitJIRRawMethodType(value: JIRRawMethodType): JIRRawExpr {
        return with(value) {
            JIRRawMethodType(
                argumentTypes.map { it.eliminateApproximation(approximations) },
                returnType.eliminateApproximation(approximations),
                typeName.eliminateApproximation(approximations)
            )
        }
    }
}
