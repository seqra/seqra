package org.opentaint.dataflow.go

import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.serialization.MethodContextSerializer
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.api.GoIRProgram
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.inst.GoIRInstRef
import org.opentaint.ir.go.inst.GoIRPanic
import org.opentaint.ir.go.type.GoIRCallMode
import org.opentaint.ir.go.value.GoIRFunctionValue

open class GoLanguageManager(val cp: GoIRProgram) : LanguageManager {
    override fun getInstIndex(inst: CommonInst): Int =
        (inst as GoIRInst).location.index

    override fun getMaxInstIndex(method: CommonMethod): Int {
        val body = (method as GoIRFunction).body ?: return 0
        return body.instructions.lastIndex
    }

    override fun getInstByIndex(
        method: CommonMethod,
        index: Int
    ): CommonInst {
        val body = (method as GoIRFunction).body ?: error("Function has no body")
        return body.inst(GoIRInstRef(index))
    }

    override fun isEmpty(method: CommonMethod): Boolean = (method as GoIRFunction).body == null

    override fun getCallExpr(inst: CommonInst): CommonCallExpr? {
        val goInst = inst as GoIRInst
        val callInfo = GoFlowFunctionUtils.extractCallInfo(goInst) ?: return null

        val callee = when (callInfo.mode) {
            GoIRCallMode.DIRECT -> (callInfo.function as? GoIRFunctionValue)?.function
            else -> null
        }

        return if (callInfo.receiver != null) {
            GoInstanceCallExpr(callInfo, callee, callInfo.receiver as CommonValue)
        } else {
            GoCallExpr(callInfo, callee)
        }
    }

    override fun producesExceptionalControlFlow(inst: CommonInst): Boolean {
        return inst is GoIRPanic
    }

    override fun getCalleeMethod(callExpr: CommonCallExpr): CommonMethod {
        val goExpr = callExpr as GoCallExpr
        return goExpr.resolvedCallee
            ?: error("Cannot get callee for unresolved call: ${goExpr.callInfo}")
    }

    override val methodContextSerializer: MethodContextSerializer
        get() = DummyMethodContextSerializer
}
