package org.opentaint.dataflow.ap.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.dataflow.ap.ifds.serialization.MethodContextSerializer

interface LanguageManager {
    fun getInstIndex(inst: CommonInst): Int
    fun getMaxInstIndex(method: CommonMethod): Int
    fun getInstByIndex(method: CommonMethod, index: Int): CommonInst
    fun isEmpty(method: CommonMethod): Boolean
    fun getCallExpr(inst: CommonInst): CommonCallExpr?
    fun producesExceptionalControlFlow(inst: CommonInst): Boolean
    fun getCalleeMethod(callExpr: CommonCallExpr): CommonMethod
    fun accessPathBase(value: CommonValue): AccessPathBase?

    val methodContextSerializer: MethodContextSerializer
}
