package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.dataflow.ap.ifds.MethodContext

data class JIRInstanceTypeMethodContext(val type: JIRClassOrInterface) : MethodContext {
    override fun toString(): String = "{this is $type}"
}