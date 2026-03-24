package org.opentaint.dataflow.python

import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.serialization.MethodContextSerializer
import org.opentaint.dataflow.python.adapter.PIRCallExprAdapter
import org.opentaint.dataflow.python.serialization.PIRMethodContextSerializer
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.python.*

open class PIRLanguageManager(
    protected val cp: PIRClasspath,
) : LanguageManager {

    // --- Flat instruction list cache (per-function) ---
    private val flatInstsCache = HashMap<PIRFunction, List<PIRInstruction>>()

    protected fun flattenCfg(method: PIRFunction): List<PIRInstruction> =
        flatInstsCache.getOrPut(method) {
            method.cfg.blocks
                .sortedBy { it.label }
                .flatMap { it.instructions }
        }

    // --- Interface implementations ---

    override fun getInstIndex(inst: CommonInst): Int =
        (inst as PIRInstruction).location.index

    override fun getMaxInstIndex(method: CommonMethod): Int =
        flattenCfg(method as PIRFunction).size - 1

    override fun getInstByIndex(method: CommonMethod, index: Int): CommonInst =
        flattenCfg(method as PIRFunction)[index]

    override fun isEmpty(method: CommonMethod): Boolean =
        flattenCfg(method as PIRFunction).isEmpty()

    override fun getCallExpr(inst: CommonInst): CommonCallExpr? {
        val pirInst = inst as PIRInstruction
        return if (pirInst is PIRCall) PIRCallExprAdapter(pirInst) else null
    }

    override fun producesExceptionalControlFlow(inst: CommonInst): Boolean =
        inst is PIRRaise

    override fun getCalleeMethod(callExpr: CommonCallExpr): CommonMethod {
        val adapter = callExpr as PIRCallExprAdapter
        val qualifiedName = adapter.pirCall.resolvedCallee
            ?: error("Unresolved call: ${adapter.pirCall.callee}")
        return cp.findFunctionOrNull(qualifiedName)
            ?: error("Function not found: $qualifiedName")
    }

    override val methodContextSerializer: MethodContextSerializer =
        PIRMethodContextSerializer()
}
