package org.opentaint.ir.go.cfg

import org.opentaint.ir.go.api.GoIrFunctionReference
import org.opentaint.ir.go.api.GoIRPosition
import org.opentaint.ir.go.type.GoIRCallMode
import org.opentaint.ir.go.type.GoIRChanDirection
import org.opentaint.ir.go.type.GoIRType
import org.opentaint.ir.go.value.GoIRValue

sealed interface GoIRCallTarget {
    val type: GoIRType
    val displayName: String

    data class Function(val lookup: GoIrFunctionReference) : GoIRCallTarget {
        val function get() = lookup.function
        override val type: GoIRType get() = lookup.signature
        override val displayName: String get() = function.fullName
    }

    data class Builtin(val name: String, override val type: GoIRType) : GoIRCallTarget {
        override val displayName: String get() = name
    }

    data class Dynamic(val value: GoIRValue) : GoIRCallTarget {
        override val type: GoIRType get() = value.type
        override val displayName: String get() = value.name
    }
}

data class GoIRCallInfo(
    val mode: GoIRCallMode,
    val target: GoIRCallTarget?,
    val receiver: GoIRValue?,
    val methodName: String?,
    val args: List<GoIRValue>,
    val resultType: GoIRType,
) {
    fun allOperands(): List<GoIRValue> {
        val dyn = (target as? GoIRCallTarget.Dynamic)?.value
        return listOfNotNull(dyn, receiver) + args
    }
}

data class GoIRSelectState(
    val direction: GoIRChanDirection,
    val chan: GoIRValue,
    val send: GoIRValue?,
    val position: GoIRPosition?,
)
