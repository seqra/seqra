package org.opentaint.dataflow.ap.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst

interface MethodContext

data object EmptyMethodContext : MethodContext {
    override fun toString(): String = "{}"
}

data class MethodWithContext(val method: CommonMethod, val ctx: MethodContext)

data class MethodEntryPoint(val context: MethodContext, val statement: CommonInst) {
    val method: CommonMethod get() = statement.location.method

    override fun toString(): String = "$method [$context]"
}
