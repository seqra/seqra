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
    /**
     * Maps a PIRValue to an AccessPathBase.
     *
     * PIRLocal whose name matches a method parameter → Argument(parameterIndex)
     *   (PIR represents parameter usage in the body as PIRLocal, not PIRParameterRef,
     *   but the dataflow framework expects parameters as Argument(i) so that
     *   interprocedural summary edges align with caller subscriptions.)
     * PIRLocal (non-parameter) → LocalVar(ctx.localIndex(name))
     * PIRParameterRef → Argument(parameterIndex)
     * PIRConst → null (constants are not tracked)
     * PIRGlobalRef → null (not tracked for now)
     */
    fun accessPathBase(
        value: PIRValue,
        method: PIRFunction,
        ctx: PIRMethodAnalysisContext,
    ): AccessPathBase? = when (value) {
        is PIRLocal -> {
            // Check if this local name is actually a parameter name.
            val param = method.parameters.firstOrNull { it.name == value.name }
            if (param != null) {
                AccessPathBase.Argument(param.index)
            } else {
                AccessPathBase.LocalVar(ctx.localIndex(value.name))
            }
        }
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

    /**
     * Returns the number of implicit parameters (self/cls) that are not present in call args.
     *
     * Instance methods have `self` as first parameter; classmethods have `cls`.
     * Neither is passed explicitly in PIRCall.args — the receiver is only in
     * the preceding PIRLoadAttr / PIRAttrExpr.
     *
     * Static methods and module-level functions have no implicit parameters.
     */
    fun implicitParamOffset(callee: PIRFunction): Int {
        if (callee.enclosingClass == null) return 0  // Module-level function
        if (callee.isStaticMethod) return 0
        // Instance method or classmethod — skip first parameter (self/cls)
        return 1
    }

    /**
     * Finds the receiver object of a method call.
     *
     * In PIR, `data.upper()` is lowered to:
     *   PIRAssign(target=$t0, expr=PIRAttrExpr(obj=data, attribute="upper"))
     *   PIRCall(target=$t1, callee=$t0, args=[], resolvedCallee="builtins.str.upper")
     *
     * This method finds the PIRAssign that defines `call.callee` and extracts
     * the `obj` from its PIRAttrExpr. Returns the receiver PIRValue, or null
     * if the call isn't a method call or the definition can't be found.
     */
    fun findMethodCallReceiver(call: PIRCall, method: PIRFunction): PIRValue? {
        val callee = call.callee
        if (callee !is PIRLocal) return null

        // Scan the flattened instructions in the same method to find the defining assignment
        for (block in method.cfg.blocks) {
            for (inst in block.instructions) {
                if (inst is PIRAssign && inst.target is PIRLocal
                    && (inst.target as PIRLocal).name == callee.name
                    && inst.expr is PIRAttrExpr
                ) {
                    return (inst.expr as PIRAttrExpr).obj
                }
            }
        }
        return null
    }
}
