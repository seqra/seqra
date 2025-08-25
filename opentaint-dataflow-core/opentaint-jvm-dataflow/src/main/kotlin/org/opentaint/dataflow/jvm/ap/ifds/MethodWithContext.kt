package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst

sealed interface MethodContext

object EmptyMethodContext : MethodContext {
    override fun toString(): String = "{}"
}

data class InstanceTypeMethodContext(val type: JIRClassOrInterface) : MethodContext {
    override fun toString(): String = "{this is $type}"
}

data class MethodWithContext(val method: JIRMethod, val ctx: MethodContext)

data class MethodEntryPoint(val context: MethodContext, val statement: JIRInst) {
    val method: JIRMethod get() = statement.method

    override fun toString(): String = "$method [$context]"
}
