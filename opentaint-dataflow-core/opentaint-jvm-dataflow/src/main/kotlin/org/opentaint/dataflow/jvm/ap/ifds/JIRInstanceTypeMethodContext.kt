package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.dataflow.ap.ifds.MethodContext

data class TypeConstraintInfo(val type: JIRClassOrInterface, val exactType: Boolean)

data class JIRInstanceTypeMethodContext(
    val typeConstraint: TypeConstraintInfo
) : MethodContext {
    override fun toString(): String = if (typeConstraint.exactType) {
        "{this == ${typeConstraint.type}}"
    } else {
        "{this is ${typeConstraint.type}}"
    }
}

data class JIRArgumentTypeMethodContext(
    val argIdx: Int,
    val typeConstraint: TypeConstraintInfo
) : MethodContext {
    override fun toString(): String = if (typeConstraint.exactType) {
        "{arg($argIdx) == ${typeConstraint.type}}"
    } else {
        "{arg($argIdx) is ${typeConstraint.type}}"
    }
}
