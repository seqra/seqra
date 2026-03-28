package org.opentaint.dataflow.go

import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInstanceCallExpr
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.cfg.GoIRCallInfo
import org.opentaint.ir.go.type.GoIRCallMode
import org.opentaint.ir.go.value.GoIRBuiltinValue

/**
 * Wraps GoIRCallInfo to implement the framework's CommonCallExpr interface.
 */
open class GoCallExpr(
    val callInfo: GoIRCallInfo,
    val resolvedCallee: GoIRFunction?,
) : CommonCallExpr {
    override val args: List<CommonValue>
        get() = callInfo.args.map { it as CommonValue }

    override val typeName: String
        get() = callInfo.resultType.displayName

    /**
     * Callee function name for rule matching.
     * For DIRECT: the function's full name (e.g., "test.source")
     * For INVOKE: constructed from receiver type + method name
     * For builtins: the builtin name (e.g., "append")
     * For DYNAMIC: null (unresolved)
     */
    val calleeName: String?
        get() {
            resolvedCallee?.fullName?.let { return it }
            val func = callInfo.function
            if (func is GoIRBuiltinValue) return func.name
            if (callInfo.mode == GoIRCallMode.INVOKE) {
                val recvType = callInfo.receiver?.type?.displayName ?: return null
                val methodName = callInfo.methodName ?: return null
                return "($recvType).$methodName"
            }
            return null
        }
}

/**
 * GoCallExpr variant for method calls (receiver-based).
 */
class GoInstanceCallExpr(
    callInfo: GoIRCallInfo,
    resolvedCallee: GoIRFunction?,
    override val instance: CommonValue,
) : GoCallExpr(callInfo, resolvedCallee), CommonInstanceCallExpr
