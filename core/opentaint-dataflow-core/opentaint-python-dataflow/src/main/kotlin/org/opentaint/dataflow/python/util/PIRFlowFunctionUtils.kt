package org.opentaint.dataflow.python.util

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor.*
import org.opentaint.dataflow.python.analysis.PIRMethodAnalysisContext
import org.opentaint.ir.api.python.*

/**
 * Maps PIR values and expressions to AccessPathBase representations.
 * Python equivalent of JVM's MethodFlowFunctionUtils.
 */
object PIRFlowFunctionUtils {

    /**
     * Maps a PIRValue to an AccessPathBase.
     *
     * PIRLocal("x") → LocalVar(ctx.localIndex("x"))
     * PIRParameterRef("arg") → Argument(parameterIndex)
     * PIRConst → null (constants are not tracked)
     * PIRGlobalRef → null (not tracked for now)
     */
    fun accessPathBase(
        value: PIRValue,
        method: PIRFunction,
        ctx: PIRMethodAnalysisContext,
    ): AccessPathBase? = when (value) {
        is PIRLocal -> AccessPathBase.LocalVar(ctx.localIndex(value.name))
        is PIRParameterRef -> {
            val param = method.parameters.firstOrNull { it.name == value.name }
                ?: error("Parameter not found: ${value.name} in ${method.qualifiedName}")
            AccessPathBase.Argument(param.index)
        }
        is PIRConst -> null // Constants are not taint-trackable
        is PIRGlobalRef -> null  // Not tracked as a local
    }

    /**
     * Converts a PIRExpr to an AccessPathBase.
     * Simple values → base
     * Compound expressions → null (not directly resolvable)
     */
    fun exprToBase(
        expr: PIRExpr,
        method: PIRFunction,
        ctx: PIRMethodAnalysisContext,
    ): AccessPathBase? = when (expr) {
        is PIRValue -> accessPathBase(expr, method, ctx)
        else -> null
    }
}
