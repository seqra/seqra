package org.opentaint.ir.go.cfg

import org.opentaint.ir.go.api.GoIRPosition
import org.opentaint.ir.go.type.GoIRCallMode
import org.opentaint.ir.go.type.GoIRChanDirection
import org.opentaint.ir.go.type.GoIRType
import org.opentaint.ir.go.value.GoIRValue

data class GoIRCallInfo(
    val mode: GoIRCallMode,
    val function: GoIRValue?,          // for DIRECT and DYNAMIC
    val receiver: GoIRValue?,          // for INVOKE
    val methodName: String?,           // for INVOKE
    val args: List<GoIRValue>,
    val resultType: GoIRType,
) {
    fun allOperands(): List<GoIRValue> =
        listOfNotNull(function, receiver) + args
}

data class GoIRSelectState(
    val direction: GoIRChanDirection,
    val chan: GoIRValue,
    val send: GoIRValue?,              // only for send cases
    val position: GoIRPosition?,
)
