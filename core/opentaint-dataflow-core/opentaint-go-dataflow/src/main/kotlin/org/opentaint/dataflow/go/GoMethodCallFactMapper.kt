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
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.value.GoIRValue

/**
 * Maps taint facts between caller and callee namespaces.
 * Singleton object analogous to JIRMethodCallFactMapper.
 */
object GoMethodCallFactMapper : MethodCallFactMapper {

    // ── Exit-to-Return (callee → caller) ─────────────────────────────

    override fun mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        factAp: FinalFactAp,
        checker: FactTypeChecker,
    ): List<FinalFactAp> {
        val goInst = callStatement as GoIRInst
        val callInfo = GoFlowFunctionUtils.extractCallInfo(goInst) ?: return emptyList()
        val method = goInst.location.functionBody.function
        val isInvoke = callInfo.receiver != null
        val argOffset = if (isInvoke) 1 else 0

        return when (factAp.base) {
            is AccessPathBase.Return -> {
                val resultRegister = GoFlowFunctionUtils.extractResultRegister(goInst)
                    ?: return emptyList()
                listOf(factAp.rebase(AccessPathBase.LocalVar(resultRegister.index)))
            }
            is AccessPathBase.Argument -> {
                val idx = (factAp.base as AccessPathBase.Argument).idx
                if (isInvoke && idx == 0) {
                    // Argument(0) in callee = receiver for INVOKE calls
                    val receiver = callInfo.receiver!!
                    val recvBase = GoFlowFunctionUtils.accessPathBase(receiver, method)
                        ?: return emptyList()
                    listOf(factAp.rebase(recvBase))
                } else {
                    val argIdx = idx - argOffset
                    if (argIdx >= 0 && argIdx < callInfo.args.size) {
                        val argBase = GoFlowFunctionUtils.accessPathBase(callInfo.args[argIdx], method)
                            ?: return emptyList()
                        listOf(factAp.rebase(argBase))
                    } else emptyList()
                }
            }
            is AccessPathBase.This -> {
                // Not used in Go convention; kept for safety
                if (isInvoke) {
                    val receiver = callInfo.receiver!!
                    val recvBase = GoFlowFunctionUtils.accessPathBase(receiver, method)
                        ?: return emptyList()
                    listOf(factAp.rebase(recvBase))
                } else emptyList()
            }
            is AccessPathBase.ClassStatic -> listOf(factAp)
            is AccessPathBase.Constant -> listOf(factAp)
            is AccessPathBase.LocalVar -> emptyList()
            is AccessPathBase.Exception -> emptyList()
        }
    }

    override fun mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        factAp: InitialFactAp,
    ): List<InitialFactAp> {
        val goInst = callStatement as GoIRInst
        val callInfo = GoFlowFunctionUtils.extractCallInfo(goInst) ?: return emptyList()
        val method = goInst.location.functionBody.function
        val isInvoke = callInfo.receiver != null
        val argOffset = if (isInvoke) 1 else 0

        return when (factAp.base) {
            is AccessPathBase.Return -> {
                val resultRegister = GoFlowFunctionUtils.extractResultRegister(goInst)
                    ?: return emptyList()
                listOf(factAp.rebase(AccessPathBase.LocalVar(resultRegister.index)))
            }
            is AccessPathBase.Argument -> {
                val idx = (factAp.base as AccessPathBase.Argument).idx
                if (isInvoke && idx == 0) {
                    val receiver = callInfo.receiver!!
                    val recvBase = GoFlowFunctionUtils.accessPathBase(receiver, method)
                        ?: return emptyList()
                    listOf(factAp.rebase(recvBase))
                } else {
                    val argIdx = idx - argOffset
                    if (argIdx >= 0 && argIdx < callInfo.args.size) {
                        val argBase = GoFlowFunctionUtils.accessPathBase(callInfo.args[argIdx], method)
                            ?: return emptyList()
                        listOf(factAp.rebase(argBase))
                    } else emptyList()
                }
            }
            is AccessPathBase.This -> {
                if (isInvoke) {
                    val receiver = callInfo.receiver!!
                    val recvBase = GoFlowFunctionUtils.accessPathBase(receiver, method)
                        ?: return emptyList()
                    listOf(factAp.rebase(recvBase))
                } else emptyList()
            }
            is AccessPathBase.ClassStatic -> listOf(factAp)
            is AccessPathBase.Constant -> listOf(factAp)
            else -> emptyList()
        }
    }

    // ── Call-to-Start (caller → callee) ──────────────────────────────

    override fun mapMethodCallToStartFlowFact(
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        factAp: FinalFactAp,
        checker: FactTypeChecker,
        onMappedFact: (FinalFactAp, AccessPathBase) -> Unit,
    ) {
        val goCallExpr = callExpr as GoCallExpr
        val callInfo = goCallExpr.callInfo
        val isInvoke = callInfo.receiver != null
        val argOffset = if (isInvoke) 1 else 0

        // Map receiver → Argument(0) for INVOKE calls
        if (isInvoke) {
            val receiverBase = GoFlowFunctionUtils.accessPathBaseFromValue(callInfo.receiver!!)
            if (receiverBase != null && factAp.base == receiverBase) {
                onMappedFact(factAp.rebase(AccessPathBase.Argument(0)), AccessPathBase.Argument(0))
            }
        }

        // Map arguments → Argument(i + argOffset)
        for ((i, arg) in callInfo.args.withIndex()) {
            val argBase = GoFlowFunctionUtils.accessPathBaseFromValue(arg)
            if (argBase != null && factAp.base == argBase) {
                val calleeBase = AccessPathBase.Argument(i + argOffset)
                onMappedFact(factAp.rebase(calleeBase), calleeBase)
            }
        }

        // ClassStatic passes through
        if (factAp.base is AccessPathBase.ClassStatic) {
            onMappedFact(factAp, AccessPathBase.ClassStatic)
        }
    }

    override fun mapMethodCallToStartFlowFact(
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        fact: InitialFactAp,
        onMappedFact: (InitialFactAp, AccessPathBase) -> Unit,
    ) {
        val goCallExpr = callExpr as GoCallExpr
        val callInfo = goCallExpr.callInfo
        val isInvoke = callInfo.receiver != null
        val argOffset = if (isInvoke) 1 else 0

        if (isInvoke) {
            val receiverBase = GoFlowFunctionUtils.accessPathBaseFromValue(callInfo.receiver!!)
            if (receiverBase != null && fact.base == receiverBase) {
                onMappedFact(fact.rebase(AccessPathBase.Argument(0)), AccessPathBase.Argument(0))
            }
        }

        for ((i, arg) in callInfo.args.withIndex()) {
            val argBase = GoFlowFunctionUtils.accessPathBaseFromValue(arg)
            if (argBase != null && fact.base == argBase) {
                val calleeBase = AccessPathBase.Argument(i + argOffset)
                onMappedFact(fact.rebase(calleeBase), calleeBase)
            }
        }

        if (fact.base is AccessPathBase.ClassStatic) {
            onMappedFact(fact, AccessPathBase.ClassStatic)
        }
    }

    // ── Relevance and validity checks ────────────────────────────────

    override fun factIsRelevantToMethodCall(
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        factAp: FactAp,
    ): Boolean {
        val goCallExpr = callExpr as GoCallExpr
        val callInfo = goCallExpr.callInfo

        for (arg in callInfo.args) {
            val argBase = GoFlowFunctionUtils.accessPathBaseFromValue(arg)
            if (argBase != null && argBase == factAp.base) return true
        }

        if (callInfo.receiver != null) {
            val recvBase = GoFlowFunctionUtils.accessPathBaseFromValue(callInfo.receiver!!)
            if (recvBase != null && recvBase == factAp.base) return true
        }

        if (returnValue != null) {
            val retBase = GoFlowFunctionUtils.accessPathBaseFromValue(returnValue as GoIRValue)
            if (retBase != null && retBase == factAp.base) return true
        }

        if (factAp.base is AccessPathBase.ClassStatic) return true

        return false
    }

    override fun isValidMethodExitFact(factAp: FactAp): Boolean {
        return factAp.base !is AccessPathBase.LocalVar
    }
}
