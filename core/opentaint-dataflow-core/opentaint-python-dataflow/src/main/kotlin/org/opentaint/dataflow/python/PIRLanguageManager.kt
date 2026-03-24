package org.opentaint.dataflow.python

import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.serialization.MethodContextSerializer
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst

open class PIRLanguageManager : LanguageManager {
    override fun getInstIndex(inst: CommonInst): Int {
        TODO("Not yet implemented")
    }

    override fun getMaxInstIndex(method: CommonMethod): Int {
        TODO("Not yet implemented")
    }

    override fun getInstByIndex(
        method: CommonMethod,
        index: Int
    ): CommonInst {
        TODO("Not yet implemented")
    }

    override fun isEmpty(method: CommonMethod): Boolean {
        TODO("Not yet implemented")
    }

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
