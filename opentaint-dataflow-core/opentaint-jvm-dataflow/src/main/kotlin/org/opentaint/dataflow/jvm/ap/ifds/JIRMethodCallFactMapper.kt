package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFactMapper
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

object JIRMethodCallFactMapper : org.opentaint.dataflow.ap.ifds.analysis.MethodCallFactMapper {
    // TODO: maybe receive FactTypeChecker at instantiation and remove it from method's parameters?
    override fun mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        factAp: FinalFactAp,
        checker: FactTypeChecker
    ): List<FinalFactAp> {
        jirDowncast<JIRInst>(callStatement)
        return mapMethodExitToReturnFlowFact(callStatement, factAp, checker)
    }

    override fun mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        factAp: InitialFactAp
    ): List<InitialFactAp> {
        jirDowncast<JIRInst>(callStatement)
        return mapMethodExitToReturnFlowFact(callStatement, factAp)
    }

    override fun mapMethodCallToStartFlowFact(
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        factAp: FinalFactAp,
        checker: FactTypeChecker,
        onMappedFact: (FinalFactAp, AccessPathBase) -> Unit
    ) {
        jirDowncast<JIRMethod>(callee)
        jirDowncast<JIRCallExpr>(callExpr)
        return mapMethodCallToStartFlowFact(callee, callExpr, factAp, checker, onMappedFact)
    }

    override fun mapMethodCallToStartFlowFact(
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        fact: InitialFactAp,
        onMappedFact: (InitialFactAp, AccessPathBase) -> Unit
    ) {
        jirDowncast<JIRMethod>(callee)
        jirDowncast<JIRCallExpr>(callExpr)
        return mapMethodCallToStartFlowFact(callee, callExpr, fact, onMappedFact)
    }

    override fun factIsRelevantToMethodCall(
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        factAp: FactAp
    ): Boolean {
        jirDowncast<JIRImmediate?>(returnValue)
        jirDowncast<JIRCallExpr>(callExpr)
        return factIsRelevantToMethodCall(returnValue, callExpr, factAp)
    }

    override fun isValidMethodExitFact(factAp: FactAp): Boolean {
        return isValidMethodExitFact(factAp.base)
    }

    private fun mapMethodExitToReturnFlowFact(
        callStatement: JIRInst,
        factAp: FinalFactAp,
        checker: FactTypeChecker
    ): List<FinalFactAp> = mapMethodExitToReturnFlowFact(
        callStatement = callStatement,
        factAp = factAp,
        checkFactType = { type, f -> checker.filterFactByLocalType(type, f) },
        rebaseFact = { f, base -> f.rebase(base) }
    )

    private fun mapMethodExitToReturnFlowFact(
        callStatement: JIRInst,
        factAp: InitialFactAp
    ): List<InitialFactAp> = mapMethodExitToReturnFlowFact(
        callStatement = callStatement,
        factAp = factAp,
        checkFactType = { _, f -> f },
        rebaseFact = { f, base -> f.rebase(base) }
    )

    private fun mapMethodCallToStartFlowFact(
        callee: JIRMethod,
        callExpr: JIRCallExpr,
        factAp: FinalFactAp,
        checker: FactTypeChecker,
        onMappedFact: (FinalFactAp, AccessPathBase) -> Unit
    ) = mapMethodCallToStartFlowFact(
        callee = callee,
        callExpr = callExpr,
        factAp = factAp,
        checkFactType = { type, f -> checker.filterFactByLocalType(type, f) },
        onMappedFact = onMappedFact
    )

    private fun mapMethodCallToStartFlowFact(
        callee: JIRMethod,
        callExpr: JIRCallExpr,
        fact: InitialFactAp,
        onMappedFact: (InitialFactAp, AccessPathBase) -> Unit
    ) = mapMethodCallToStartFlowFact(
        callee = callee,
        callExpr = callExpr,
        factAp = fact,
        checkFactType = { _, f -> f },
        onMappedFact = onMappedFact
    )

    private inline fun <F : FactAp> mapMethodExitToReturnFlowFact(
        callStatement: JIRInst,
        factAp: F,
        checkFactType: (JIRType, F) -> F?,
        rebaseFact: (F, AccessPathBase) -> F,
    ): List<F> = listOfNotNull(
        mapMethodExitToReturnSingleFlowFact(callStatement, factAp, checkFactType, rebaseFact)
    )

    private inline fun <F: FactAp> mapMethodExitToReturnSingleFlowFact(
        callStatement: JIRInst,
        factAp: F,
        checkFactType: (JIRType, F) -> F?,
        rebaseFact: (F, AccessPathBase) -> F,
    ): F? {
        val base = factAp.base

        val callExpr = callStatement.callExpr
            ?: error("Non call statement")
        val returnValue: JIRImmediate? = (callStatement as? JIRAssignInst)?.lhv?.let {
            it as? JIRImmediate ?: error("Non simple return value: $callStatement")
        }

        return when (base) {
            is AccessPathBase.ClassStatic,
            is AccessPathBase.Constant -> {
               factAp
            }

            is AccessPathBase.Argument -> {
                val argExpr = callExpr.args.getOrNull(base.idx) ?: error("Call $callExpr has no arg $factAp")
                val newBase = MethodFlowFunctionUtils.accessPathBase(argExpr) ?: return null
                if (newBase is AccessPathBase.Constant) return null

                val checkedFact = checkFactType(argExpr.type, factAp) ?: return null

                rebaseFact(checkedFact, newBase)
            }

            AccessPathBase.This -> {
                check(callExpr is JIRInstanceCallExpr) { "Non instance call with <this> argument" }

                val newBase = MethodFlowFunctionUtils.accessPathBase(callExpr.instance) ?: return null
                if (newBase is AccessPathBase.Constant) return null

                val checkedFact = checkFactType(callExpr.instance.type, factAp) ?: return null

                rebaseFact(checkedFact, newBase)
            }

            AccessPathBase.Return -> {
                if (returnValue == null) return null

                val newBase = MethodFlowFunctionUtils.accessPathBase(returnValue) ?: return null
                if (newBase is AccessPathBase.Constant) return null

                val checkedFact = checkFactType(returnValue.type, factAp) ?: return null

                rebaseFact(checkedFact, newBase)
            }

            AccessPathBase.Exception -> {
                // Trow can't be propagated to method return site
                // todo: exceptional flow ignored
                null
            }

            is AccessPathBase.LocalVar -> {
                // local variable can't be propagated to exit
                null
            }
        }
    }

    private fun isValidMethodExitFact(factBase: AccessPathBase): Boolean =
        factBase !is AccessPathBase.LocalVar

    private inline fun <F : FactAp> mapMethodCallToStartFlowFact(
        callee: JIRMethod,
        callExpr: JIRCallExpr,
        factAp: F,
        checkFactType: (JIRType, F) -> F?,
        onMappedFact: (F, AccessPathBase) -> Unit,
    ) {
        val factBase = factAp.base

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

    private fun factIsRelevantToMethodCall(returnValue: JIRImmediate?, callExpr: JIRCallExpr, factAp: FactAp): Boolean =
        factIsRelevantToMethodCall(returnValue, callExpr, factAp.base)

    private fun factIsRelevantToMethodCall(
        returnValue: JIRImmediate?,
        callExpr: JIRCallExpr,
        factBase: AccessPathBase
    ): Boolean {
        if (factBase is AccessPathBase.ClassStatic) {
            return true
        }

        if (callExpr is JIRInstanceCallExpr) {
            val instanceBase = MethodFlowFunctionUtils.accessPathBase(callExpr.instance)
            if (instanceBase == factBase) {
                return true
            }
        }

        for (arg in callExpr.args) {
            val argBase = MethodFlowFunctionUtils.accessPathBase(arg)
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
}