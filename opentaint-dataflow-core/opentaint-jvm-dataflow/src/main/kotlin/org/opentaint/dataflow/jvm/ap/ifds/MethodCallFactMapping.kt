package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import org.opentaint.ir.api.jvm.cfg.JIRThrowInst
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp

fun mapMethodExitToReturnFlowFact(
    callStatement: JIRInst,
    methodExit: JIRInst,
    factAp: FinalFactAp,
    checker: FactTypeChecker
): FinalFactAp? = mapMethodExitToReturnFlowFact(
    callStatement = callStatement,
    methodExit = methodExit,
    factAp = factAp,
    factApBase = { it.base },
    checkFactType = { type, f -> checker.filterFactByLocalType(type, f) },
    rebaseFact = { f, base -> f.rebase(base) }
)

fun mapMethodCallToStartFlowFact(
    callee: JIRMethod,
    callExpr: JIRCallExpr,
    factAp: FinalFactAp,
    checker: FactTypeChecker,
    onMappedFact: (FinalFactAp, AccessPathBase) -> Unit
) = mapMethodCallToStartFlowFact(
    callee = callee,
    callExpr = callExpr,
    factAp = factAp,
    factApBase = { it.base },
    checkFactType = { type, f -> checker.filterFactByLocalType(type, f) },
    onMappedFact = onMappedFact
)

private inline fun mapMethodExitToReturnFlowFact(
    callStatement: JIRInst,
    methodExit: JIRInst,
    factAp: FinalFactAp,
    factApBase: (FinalFactAp) -> AccessPathBase,
    checkFactType: (JIRType, FinalFactAp) -> FinalFactAp?,
    rebaseFact: (FinalFactAp, AccessPathBase) -> FinalFactAp,
): FinalFactAp? {
    val callExpr = callStatement.callExpr ?: error("Non call statement")
    val returnValue: JIRImmediate? = (callStatement as? JIRAssignInst)?.lhv?.let {
        it as? JIRImmediate ?: error("Non simple return value: $callStatement")
    }

    when (val base = factApBase(factAp)) {
        is AccessPathBase.ClassStatic -> return factAp
        is AccessPathBase.Constant -> return factAp
        is AccessPathBase.Argument -> {
            val argExpr = callExpr.args.getOrNull(base.idx) ?: error("Call $callExpr has no arg $factAp")
            val newBase = MethodFlowFunctionUtils.accessPathBase(argExpr) ?: return null
            if (newBase is AccessPathBase.Constant) return null

           val checkedFact = checkFactType(argExpr.type, factAp) ?: return null

            return rebaseFact(checkedFact, newBase)
        }

        AccessPathBase.This -> {
            check(callExpr is JIRInstanceCallExpr) { "Non instance call with <this> argument" }

            val newBase = MethodFlowFunctionUtils.accessPathBase(callExpr.instance) ?: return null
            if (newBase is AccessPathBase.Constant) return null

            val checkedFact = checkFactType(callExpr.instance.type, factAp) ?: return null

            return rebaseFact(checkedFact, newBase)
        }

        is AccessPathBase.LocalVar -> {
            if (returnValue == null) return null

            when (methodExit) {
                is JIRReturnInst -> {
                    val exitValue = methodExit.returnValue
                        ?.let { MethodFlowFunctionUtils.accessPathBase(it) }
                        ?: return null

                    if (base != exitValue) return null

                    val newBase = MethodFlowFunctionUtils.accessPathBase(returnValue) ?: return null
                    if (newBase is AccessPathBase.Constant) return null


                    val checkedFact = checkFactType(returnValue.type, factAp) ?: return null

                    return rebaseFact(checkedFact, newBase)
                }

                is JIRThrowInst -> {
                    // Trow can't be propagated to method return site
                    return null
                }

                else -> return null
            }
        }
    }
}

fun isValidMethodExitFact(methodExit: JIRInst, factAp: FinalFactAp): Boolean =
    isValidMethodExitFact(methodExit, factAp.base)

private fun isValidMethodExitFact(methodExit: JIRInst, factBase: AccessPathBase): Boolean {
    if (factBase !is AccessPathBase.LocalVar) return true

    when (methodExit) {
        is JIRReturnInst -> {
            val exitValue = methodExit.returnValue
                ?.let { MethodFlowFunctionUtils.accessPathBase(it) }
                ?: return false

            return factBase == exitValue
        }

        is JIRThrowInst -> {
            val throwValue = methodExit.throwable
                .let { MethodFlowFunctionUtils.accessPathBase(it) }

            return factBase == throwValue
        }

        else -> return false
    }
}

private inline fun mapMethodCallToStartFlowFact(
    callee: JIRMethod,
    callExpr: JIRCallExpr,
    factAp: FinalFactAp,
    factApBase: (FinalFactAp) -> AccessPathBase,
    checkFactType: (JIRType, FinalFactAp) -> FinalFactAp?,
    onMappedFact: (FinalFactAp, AccessPathBase) -> Unit,
) {
    val factBase = factApBase(factAp)

    if (factBase is AccessPathBase.ClassStatic) {
        onMappedFact(factAp, factBase)
    }

    if (callExpr is JIRInstanceCallExpr) {
        val instanceBase = MethodFlowFunctionUtils.accessPathBase(callExpr.instance)
        if (instanceBase == factBase) {
            val checkedFact = checkFactType(callee.enclosingClass.toType(), factAp)
            if (checkedFact != null) {
                onMappedFact(checkedFact, AccessPathBase.This)
            }
        }
    }

    for ((i, arg) in callExpr.args.withIndex()) {
        val argBase = MethodFlowFunctionUtils.accessPathBase(arg)
        if (argBase == factBase) {
            val checkedFact = checkFactType(arg.type, factAp)
            if (checkedFact != null) {
                onMappedFact(checkedFact, AccessPathBase.Argument(i))
            }
        }
    }
}

fun factCanBeModifiedByMethodCall(returnValue: JIRImmediate?, callExpr: JIRCallExpr, factAp: FinalFactAp): Boolean =
    factCanBeModifiedByMethodCall(returnValue, callExpr, factAp.base)

private fun factCanBeModifiedByMethodCall(
    returnValue: JIRImmediate?,
    callExpr: JIRCallExpr,
    factBase: AccessPathBase
): Boolean {
    if (factBase is AccessPathBase.ClassStatic) {
        return true
    }

    if (callExpr is JIRInstanceCallExpr) {
        val instanceBase = MethodFlowFunctionUtils.accessPathBase(callExpr.instance)
        // todo: fields only?
        if (instanceBase == factBase) {
            return true
        }
    }

    for (arg in callExpr.args) {
        val argBase = MethodFlowFunctionUtils.accessPathBase(arg)
        // todo: fields only?
        if (argBase == factBase) {
            return true
        }
    }

    if (returnValue != null) {
        val retValBase = MethodFlowFunctionUtils.accessPathBase(returnValue)
        if (retValBase == factBase) {
            return true
        }
    }

    return false
}
