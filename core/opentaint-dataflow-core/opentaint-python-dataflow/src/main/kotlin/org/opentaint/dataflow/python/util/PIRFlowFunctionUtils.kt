package org.opentaint.dataflow.python.util

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor.*
import org.opentaint.ir.api.python.*

/**
 * Maps PIR values and expressions to AccessPathBase representations.
 * Python equivalent of JVM's MethodFlowFunctionUtils.
 */
object PIRFlowFunctionUtils {

    /**
     * Maps a PIRValue to an AccessPathBase.
     *
     * - PIRLocalVar → LocalVar(value.index). Body locals are first-appearance
     *   indexed during Flat → PIR conversion, starting at `parameters.size`,
     *   so their slots are disjoint from parameter slots. Parameter
     *   reads/writes after the entry-block prologue (`PIRAssign(PIRLocalVar(p),
     *   PIRParameterRef(p))`) flow through their PIRLocalVar slot. Inbound
     *   taint already flows correctly (the assign rule propagates
     *   Argument(i) → LocalVar(idx) at entry), but the return direction —
     *   mutations to LocalVar(idx) being visible on the caller's argument —
     *   requires alias analysis over the prologue assign and is future work.
     * - PIRParameterRef → Argument(value.index). Indices are signature-order
     *   on the post-rewrite [PIRFunction.parameters], so `<self>`-shifted
     *   indices on closure children are already correct.
     * - PIRConst, PIRGlobalRef, PIRModuleRef → null (not taint-trackable).
     */
    fun accessPathBase(value: PIRValue): AccessPathBase? = when (value) {
        is PIRLocalVar -> AccessPathBase.LocalVar(value.index)
        is PIRParameterRef -> AccessPathBase.Argument(value.index)
        is PIRConst -> null // Constants are not taint-trackable
        is PIRGlobalRef -> null  // Not tracked as a local
        is PIRModuleRef -> null  // Module references are not taint-trackable
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
        if (callee !is PIRLocalVar) return null

        for (inst in method.instList) {
            if (inst is PIRLoadAttr && inst.target is PIRLocalVar
                && (inst.target as PIRLocalVar).index == callee.index
            ) {
                return inst.obj
            }
        }
        return null
    }
}
