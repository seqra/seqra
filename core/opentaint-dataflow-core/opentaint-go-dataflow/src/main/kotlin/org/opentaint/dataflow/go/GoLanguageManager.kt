package org.opentaint.dataflow.go

import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.serialization.MethodContextSerializer
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.inst.GoIRInstRef

open class GoLanguageManager : LanguageManager {
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
        TODO("Not yet implemented")
    }

    override fun producesExceptionalControlFlow(inst: CommonInst): Boolean {
        TODO("Not yet implemented")
    }

    override fun getCalleeMethod(callExpr: CommonCallExpr): CommonMethod {
        TODO("Not yet implemented")
    }

    override val methodContextSerializer: MethodContextSerializer
        get() = TODO("Not yet implemented")
}
