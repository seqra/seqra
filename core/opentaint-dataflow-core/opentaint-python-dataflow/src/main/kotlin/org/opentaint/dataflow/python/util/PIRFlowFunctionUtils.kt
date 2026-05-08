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
     * - PIRLocal → LocalVar(ctx.localIndex(name)). Parameter slots, after the
     *   function's parameter-binding prologue, also show up as PIRLocal —
     *   the prologue emits one `PIRAssign(PIRLocal(name), PIRParameterRef(name))`
     *   per parameter at function entry, after which the body's reads/writes
     *   resolve uniformly to a LocalVar index. Inbound taint already flows
     *   correctly (the assign rule propagates Argument(i) → LocalVar(idx) at
     *   entry), but the return direction — mutations to LocalVar(idx) being
     *   visible on the caller's argument — requires alias analysis over the
     *   prologue assign and is future work.
     * - PIRParameterRef → Argument(parameter index in [method.parameters]).
     *   Only ever appears as the RHS of an entry-block prologue assign or as
     *   the synthetic `<self>` env parameter prepended by the closure
     *   rewriter. The index is recovered by name lookup against the
     *   post-rewrite signature on every call so we don't have to track
     *   `<self>`-shifted indices on the value itself.
     * - PIRConst, PIRGlobalRef, PIRModuleRef → null (not taint-trackable).
     */
    fun accessPathBase(
        value: PIRValue,
        method: PIRFunction,
        ctx: PIRMethodAnalysisContext,
    ): AccessPathBase? = when (value) {
        is PIRLocal -> AccessPathBase.LocalVar(ctx.localIndex(value.name))
        is PIRParameterRef -> {
            val idx = method.parameters.indexOfFirst { it.name == value.name }
            if (idx < 0) error("Parameter not found: ${value.name} in ${method.qualifiedName}")
            AccessPathBase.Argument(idx)
        }
        is PIRConst -> null // Constants are not taint-trackable
        is PIRGlobalRef -> null  // Not tracked as a local
        is PIRModuleRef -> null  // Module references are not taint-trackable
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
     * the preceding PIRLoadAttr.
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
     *   PIRLoadAttr(target=$t0, obj=data, attribute="upper")
     *   PIRCall(target=$t1, callee=$t0, args=[], resolvedCallee="builtins.str.upper")
     *
     * This method finds the PIRLoadAttr that defines `call.callee` and extracts
     * the `obj`. Returns the receiver PIRValue, or null if the call isn't a
     * method call or the definition can't be found.
     */
    fun findMethodCallReceiver(call: PIRCall, method: PIRFunction): PIRValue? {
        val callee = call.callee
        if (callee !is PIRLocal) return null

        for (inst in method.instList) {
            if (inst is PIRLoadAttr && inst.target is PIRLocal
                && (inst.target as PIRLocal).name == callee.name
            ) {
                return inst.obj
            }
        }
        return null
    }
}
