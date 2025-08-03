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
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.rebase

fun mapMethodExitToReturnFlowFact(
    callStatement: JIRInst,
    methodExit: JIRInst,
    fact: Fact.FinalFact,
    checker: FactTypeChecker
): Fact.FinalFact? = mapMethodExitToReturnFlowFact(
    callStatement = callStatement,
    methodExit = methodExit,
    fact = fact,
    factApBase = { it.ap.base },
    checkFactType = { type, f -> checker.filterFactByLocalType(type, f) },
    rebaseFact = { f, base -> f.rebase(base) }
)

fun mapMethodCallToStartFlowFact(
    callee: JIRMethod,
    callExpr: JIRCallExpr,
    fact: Fact.FinalFact,
    checker: FactTypeChecker,
    onMappedFact: (Fact.FinalFact, AccessPathBase) -> Unit
) = mapMethodCallToStartFlowFact(
    callee = callee,
    callExpr = callExpr,
    fact = fact,
    factApBase = { it.ap.base },
    checkFactType = { type, f -> checker.filterFactByLocalType(type, f) },
    onMappedFact = onMappedFact
)

private inline fun <F: Fact> mapMethodExitToReturnFlowFact(
    callStatement: JIRInst,
    methodExit: JIRInst,
    fact: F,
    factApBase: (F) -> AccessPathBase,
    checkFactType: (JIRType, F) -> F?,
    rebaseFact: (F, AccessPathBase) -> F,
): F? {
    val callExpr = callStatement.callExpr ?: error("Non call statement")
    val returnValue: JIRImmediate? = (callStatement as? JIRAssignInst)?.lhv?.let {
        it as? JIRImmediate ?: error("Non simple return value: $callStatement")
    }

    when (val base = factApBase(fact)) {
        is AccessPathBase.ClassStatic -> return fact
        is AccessPathBase.Constant -> return fact
        is AccessPathBase.Argument -> {
            val argExpr = callExpr.args.getOrNull(base.idx) ?: error("Call $callExpr has no arg $fact")
            val newBase = MethodFlowFunctionUtils.accessPathBase(argExpr) ?: return null
            if (newBase is AccessPathBase.Constant) return null

           val checkedFact = checkFactType(argExpr.type, fact) ?: return null

            return rebaseFact(checkedFact, newBase)
        }

        AccessPathBase.This -> {
            check(callExpr is JIRInstanceCallExpr) { "Non instance call with <this> argument" }

            val newBase = MethodFlowFunctionUtils.accessPathBase(callExpr.instance) ?: return null
            if (newBase is AccessPathBase.Constant) return null

            val checkedFact = checkFactType(callExpr.instance.type, fact) ?: return null

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


                    val checkedFact = checkFactType(returnValue.type, fact) ?: return null

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

fun isValidMethodExitFact(methodExit: JIRInst, fact: Fact.FinalFact): Boolean =
    isValidMethodExitFact(methodExit, fact.ap.base)

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

private inline fun <F: Fact> mapMethodCallToStartFlowFact(
    callee: JIRMethod,
    callExpr: JIRCallExpr,
    fact: F,
    factApBase: (F) -> AccessPathBase,
    checkFactType: (JIRType, F) -> F?,
    onMappedFact: (F, AccessPathBase) -> Unit,
) {
    val factBase = factApBase(fact)

    if (factBase is AccessPathBase.ClassStatic) {
        onMappedFact(fact, factBase)
    }

    if (callExpr is JIRInstanceCallExpr) {
        val instanceBase = MethodFlowFunctionUtils.accessPathBase(callExpr.instance)
        if (instanceBase == factBase) {
            val checkedFact = checkFactType(callee.enclosingClass.toType(), fact)
            if (checkedFact != null) {
                onMappedFact(checkedFact, AccessPathBase.This)
            }
        }
    }

    for ((i, arg) in callExpr.args.withIndex()) {
        val argBase = MethodFlowFunctionUtils.accessPathBase(arg)
        if (argBase == factBase) {
            val checkedFact = checkFactType(arg.type, fact)
            if (checkedFact != null) {
                onMappedFact(checkedFact, AccessPathBase.Argument(i))
            }
        }
    }
}

fun factCanBeModifiedByMethodCall(returnValue: JIRImmediate?, callExpr: JIRCallExpr, fact: Fact.FinalFact): Boolean =
    factCanBeModifiedByMethodCall(returnValue, callExpr, fact.ap.base)

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
