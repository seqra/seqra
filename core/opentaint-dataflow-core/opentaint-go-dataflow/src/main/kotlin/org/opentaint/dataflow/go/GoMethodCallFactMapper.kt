package org.opentaint.dataflow.go

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFactMapper
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.cfg.GoIRCallInfo
import org.opentaint.ir.go.cfg.GoIRCallTarget
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.type.GoIRCallMode
import org.opentaint.ir.go.value.GoIRValue

object GoMethodCallFactMapper : MethodCallFactMapper {
    override fun mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        factAp: FinalFactAp,
        checker: FactTypeChecker,
    ): List<FinalFactAp> {
        return mapMethodExitToReturnFlowFact(callStatement, factAp, FinalFactAp::rebase)
    }

    override fun mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        factAp: InitialFactAp,
    ): List<InitialFactAp> {
        return mapMethodExitToReturnFlowFact(callStatement, factAp, InitialFactAp::rebase)
    }

    fun <F: FactAp> mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        factAp: F,
        rebase: F.(AccessPathBase) -> F
    ): List<F> {
        checkProgramBase(factAp.base)
        val goInst = callStatement as GoIRInst
        val callInfo = GoFlowFunctionUtils.extractCallInfo(goInst) ?: return emptyList()
        val method = goInst.location.functionBody.function
        val callExpr = buildGoCallExpr(goInst, callInfo)
        val explicitArgs = callExpr.explicitArgs

        return when (val base = factAp.base) {
            is AccessPathBase.Return -> {
                val resultRegister = GoFlowFunctionUtils.extractResultRegister(goInst)
                    ?: return emptyList()
                listOf(factAp.rebase(AccessPathBase.LocalVar(resultRegister.index)))
            }
            is AccessPathBase.Argument -> {
                val argBase = explicitArgs.getOrNull(base.idx)
                    ?.let { GoFlowFunctionUtils.accessPathBase(it, method) }
                    ?: return emptyList()

                listOf(factAp.rebase(argBase))
            }
            is AccessPathBase.This -> {
                val mappedFacts = mutableListOf<F>()
                callExpr.effectiveReceiver?.let { receiver ->
                    GoFlowFunctionUtils.accessPathBase(receiver, method).let { recvBase ->
                        mappedFacts += factAp.rebase(recvBase)
                    }
                }

                callExpr.handleDynamicCall(method) { dynamicTarget ->
                    mappedFacts += factAp.rebase(dynamicTarget)
                }

                mappedFacts
            }
            is AccessPathBase.ClassStatic -> listOf(factAp)
            is AccessPathBase.Constant -> listOf(factAp)
            else -> emptyList()
        }
    }

    /**
     * Re-build a [GoCallExpr] from a [GoIRInst] for the in-mapper paths that
     * receive only a [CommonInst]. Mirrors what [org.opentaint.dataflow.go.GoLanguageManager.getCallExpr]
     * does, including the DIRECT-method shape normalisation.
     */
    private fun buildGoCallExpr(goInst: GoIRInst, callInfo: GoIRCallInfo): GoCallExpr {
        val enclosingMethod = goInst.location.functionBody.function
        val callee = (callInfo.target as? GoIRCallTarget.Function)?.function
        return GoCallExpr(callInfo, callee, enclosingMethod)
    }

    override fun mapMethodCallToStartFlowFact(
        callStatement: CommonInst,
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        returnValue: CommonValue?,
        factAp: FinalFactAp,
        checker: FactTypeChecker,
        onMappedFact: (FinalFactAp, AccessPathBase) -> Unit,
    ) {
        mapMethodCallToStartFlowAnyFact(callStatement, callExpr, factAp, onMappedFact)
    }

    override fun mapMethodCallToStartFlowFact(
        callStatement: CommonInst,
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        returnValue: CommonValue?,
        fact: InitialFactAp,
        onMappedFact: (InitialFactAp, AccessPathBase) -> Unit,
    ) {
        mapMethodCallToStartFlowAnyFact(callStatement, callExpr, fact, onMappedFact)
    }

    fun <F: FactAp> mapMethodCallToStartFlowAnyFact(
        callStatement: CommonInst,
        callExpr: CommonCallExpr,
        factAp: F,
        onMappedFact: (F, AccessPathBase) -> Unit,
    ) {
        checkProgramBase(factAp.base)
        val goCallExpr = callExpr as GoCallExpr
        val method = (callStatement as GoIRInst).location.functionBody.function

        val receiver = goCallExpr.effectiveReceiver
        if (receiver != null) {
            val receiverBase = GoFlowFunctionUtils.accessPathBase(receiver, method)
            if (factAp.base == receiverBase) {
                onMappedFact(factAp, AccessPathBase.This)
            }
        }

        for ((i, arg) in goCallExpr.explicitArgs.withIndex()) {
            val argBase = GoFlowFunctionUtils.accessPathBase(arg, method)
            if (factAp.base == argBase) {
                onMappedFact(factAp, AccessPathBase.Argument(i))
            }
        }

        if (factAp.base is AccessPathBase.ClassStatic) {
            onMappedFact(factAp, AccessPathBase.ClassStatic)
        }

        goCallExpr.handleDynamicCall(method) { dynamicTarget ->
            if (factAp.base == dynamicTarget) {
                onMappedFact(factAp, AccessPathBase.This)
            }
        }
    }

    override fun factIsRelevantToMethodCall(
        callStatement: CommonInst,
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        factAp: FactAp,
    ): Boolean {
        checkProgramBase(factAp.base)
        val goCallExpr = callExpr as GoCallExpr
        val method = (callStatement as GoIRInst).location.functionBody.function

        for (arg in goCallExpr.explicitArgs) {
            val argBase = GoFlowFunctionUtils.accessPathBase(arg, method)
            if (argBase == factAp.base) return true
        }

        val recv = goCallExpr.effectiveReceiver
        if (recv != null) {
            val recvBase = GoFlowFunctionUtils.accessPathBase(recv, method)
            if (recvBase == factAp.base) return true
        }

        if (returnValue != null) {
            val retBase = GoFlowFunctionUtils.accessPathBase(returnValue as GoIRValue, method)
            if (retBase == factAp.base) return true
        }

        if (factAp.base is AccessPathBase.ClassStatic) return true

        goCallExpr.handleDynamicCall(method) { dynamicTarget ->
            if (dynamicTarget == factAp.base) return true
        }

        return false
    }

    override fun isValidMethodExitFact(factAp: FactAp): Boolean {
        checkProgramBase(factAp.base)
        return factAp.base !is AccessPathBase.LocalVar
    }

    private fun checkProgramBase(base: AccessPathBase) {
        check(base != AccessPathBase.Zero && base !is AccessPathBase.CleanerTemporary) {
            "Internal base is not a Go call fact: $base"
        }
    }

    inline fun GoCallExpr.handleDynamicCall(enclosingFunction: GoIRFunction?, body: (AccessPathBase) -> Unit) {
        if (callInfo.mode != GoIRCallMode.DYNAMIC) return
        val dynamicCallTarget = callInfo.target as? GoIRCallTarget.Dynamic ?: return
        val targetValue = GoFlowFunctionUtils.accessPathBase(dynamicCallTarget.value, enclosingFunction)
        body(targetValue)
    }
}
