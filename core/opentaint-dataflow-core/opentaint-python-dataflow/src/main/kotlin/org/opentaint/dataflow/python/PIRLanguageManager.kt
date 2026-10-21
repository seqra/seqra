package org.opentaint.dataflow.python

import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.serialization.MethodContextSerializer
import org.opentaint.dataflow.python.adapter.PIRCallExprAdapter
import org.opentaint.dataflow.python.adapter.callExpr
import org.opentaint.dataflow.python.serialization.PIRMethodContextSerializer
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.python.PIRClasspath
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.ir.api.python.PIRInstruction
import org.opentaint.ir.api.python.PIRRaise
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

open class PIRLanguageManager(
    protected val cp: PIRClasspath,
) : LanguageManager {

    override fun getInstIndex(inst: CommonInst): Int =
        (inst as PIRInstruction).location.index

    override fun getMaxInstIndex(method: CommonMethod): Int =
        (method as PIRFunction).instList.size - 1

    override fun getInstByIndex(method: CommonMethod, index: Int): CommonInst =
        (method as PIRFunction).instList[index]

    override fun isEmpty(method: CommonMethod): Boolean =
        (method as PIRFunction).instList.isEmpty()

    override fun getCallExpr(inst: CommonInst): CommonCallExpr? =
        (inst as PIRInstruction).callExpr

    override fun producesExceptionalControlFlow(inst: CommonInst): Boolean =
        inst is PIRRaise

    override fun getCalleeMethod(callExpr: CommonCallExpr): CommonMethod {
        val adapter = callExpr as PIRCallExprAdapter
        val call = adapter.pirCall
        val qualifiedName = call.resolvedCallee
            ?: error("Unresolved call: ${call.callee}")

        cp.findFunctionOrNull(qualifiedName)?.let { return it }

        if ("." !in qualifiedName) {
            // hack: for nested function calls, mypy may set resolvedCallee to just simple name
            val enclosingMethod = call.location.method
            val candidate = "${enclosingMethod.qualifiedName}.$qualifiedName"
            cp.findFunctionOrNull(candidate)?.let { return it }
        }

        error("Function not found: $qualifiedName")
    }

    override val methodContextSerializer: MethodContextSerializer =
        PIRMethodContextSerializer()
}

@OptIn(ExperimentalContracts::class)
internal inline fun <reified T> pIRDowncast(value: Any?) {
    contract {
        returns() implies(value is T)
    }
    check(value is T) { "Downcast error: expected ${T::class}, got $value" }
}
