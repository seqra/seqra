package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFactMapper
import org.opentaint.dataflow.python.adapter.PIRCallExprAdapter
import org.opentaint.dataflow.python.util.PIRFlowFunctionUtils
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.python.*

/**
 * Maps facts between caller and callee frames at call boundaries.
 * Constructed per-method with the caller's analysis context for name→index resolution.
 */
class PIRMethodCallFactMapper(
    private val callerCtx: PIRMethodAnalysisContext,
    private val callResolver: PIRCallResolver,
) : MethodCallFactMapper {

    private val callerMethod: PIRFunction get() = callerCtx.method

    private fun valueToBase(value: PIRValue): AccessPathBase? =
        PIRFlowFunctionUtils.accessPathBase(value, callerMethod, callerCtx)

    /**
     * Computes the implicit parameter offset (self/cls) for a callee resolved from a call.
     * Returns 0 if the callee cannot be resolved or has no implicit parameters.
     */
    private fun callOffset(call: PIRCall): Int {
        val callee = callResolver.resolve(call, callerMethod) ?: return 0
        return PIRFlowFunctionUtils.implicitParamOffset(callee)
    }

    override fun mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        factAp: FinalFactAp,
        checker: FactTypeChecker,
    ): List<FinalFactAp> {
        val call = callStatement as PIRCall
        val offset = callOffset(call)
        return when (val base = factAp.base) {
            is AccessPathBase.Argument -> {
                // Callee's Argument(i) maps to caller's call.args[i - offset]
                val callerArgIdx = base.idx - offset
                val argValue = call.args.getOrNull(callerArgIdx)?.value ?: return emptyList()
                val callerBase = valueToBase(argValue) ?: return emptyList()
                listOf(factAp.rebase(callerBase))
            }
            is AccessPathBase.Return -> {
                val target = call.target ?: return emptyList()
                val targetBase = valueToBase(target) ?: return emptyList()
                listOf(factAp.rebase(targetBase))
            }
            is AccessPathBase.LocalVar -> emptyList()  // Cannot escape
            is AccessPathBase.ClassStatic -> listOf(factAp)
            is AccessPathBase.Constant -> listOf(factAp)
            else -> emptyList()
        }
    }

    override fun mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        factAp: InitialFactAp,
    ): List<InitialFactAp> {
        val call = callStatement as PIRCall
        val offset = callOffset(call)
        return when (val base = factAp.base) {
            is AccessPathBase.Argument -> {
                val callerArgIdx = base.idx - offset
                val argValue = call.args.getOrNull(callerArgIdx)?.value ?: return emptyList()
                val callerBase = valueToBase(argValue) ?: return emptyList()
                listOf(factAp.rebase(callerBase))
            }
            is AccessPathBase.Return -> {
                val target = call.target ?: return emptyList()
                val targetBase = valueToBase(target) ?: return emptyList()
                listOf(factAp.rebase(targetBase))
            }
            is AccessPathBase.LocalVar -> emptyList()
            is AccessPathBase.ClassStatic -> listOf(factAp)
            is AccessPathBase.Constant -> listOf(factAp)
            else -> emptyList()
        }
    }

    override fun mapMethodCallToStartFlowFact(
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        factAp: FinalFactAp,
        checker: FactTypeChecker,
        onMappedFact: (FinalFactAp, AccessPathBase) -> Unit,
    ) {
        val call = (callExpr as PIRCallExprAdapter).pirCall
        val base = factAp.base
        val calleeFunc = callee as PIRFunction
        val calleeParamCount = calleeFunc.parameters.size
        val offset = PIRFlowFunctionUtils.implicitParamOffset(calleeFunc)

        for ((i, arg) in call.args.withIndex()) {
            val calleeArgIdx = i + offset
            // Don't exceed callee's formal parameter count (avoids AccessPathBaseStorage crash)
            if (calleeArgIdx >= calleeParamCount) break
            val argBase = valueToBase(arg.value) ?: continue
            if (base == argBase) {
                val startBase = AccessPathBase.Argument(calleeArgIdx)
                onMappedFact(factAp.rebase(startBase), startBase)
            }
        }

        if (base is AccessPathBase.ClassStatic) {
            onMappedFact(factAp, base)
        }
    }

    override fun mapMethodCallToStartFlowFact(
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        fact: InitialFactAp,
        onMappedFact: (InitialFactAp, AccessPathBase) -> Unit,
    ) {
        val call = (callExpr as PIRCallExprAdapter).pirCall
        val base = fact.base
        val calleeFunc = callee as PIRFunction
        val calleeParamCount = calleeFunc.parameters.size
        val offset = PIRFlowFunctionUtils.implicitParamOffset(calleeFunc)

        for ((i, arg) in call.args.withIndex()) {
            val calleeArgIdx = i + offset
            // Don't exceed callee's formal parameter count (avoids AccessPathBaseStorage crash)
            if (calleeArgIdx >= calleeParamCount) break
            val argBase = valueToBase(arg.value) ?: continue
            if (base == argBase) {
                val startBase = AccessPathBase.Argument(calleeArgIdx)
                onMappedFact(fact.rebase(startBase), startBase)
            }
        }

        if (base is AccessPathBase.ClassStatic) {
            onMappedFact(fact, base)
        }
    }

    override fun factIsRelevantToMethodCall(
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        factAp: FactAp,
    ): Boolean {
        val base = factAp.base
        if (base is AccessPathBase.ClassStatic || base is AccessPathBase.Constant) return true

        val call = (callExpr as PIRCallExprAdapter).pirCall
        for (arg in call.args) {
            if (base == valueToBase(arg.value)) return true
        }

        if (returnValue != null && returnValue is PIRValue) {
            if (base == valueToBase(returnValue)) return true
        }

        return false
    }

    override fun isValidMethodExitFact(factAp: FactAp): Boolean =
        factAp.base !is AccessPathBase.LocalVar
}
