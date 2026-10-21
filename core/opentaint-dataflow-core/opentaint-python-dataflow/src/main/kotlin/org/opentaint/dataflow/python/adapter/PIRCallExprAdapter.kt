package org.opentaint.dataflow.python.adapter

import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.python.PIRAnyType
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRInstruction

class PIRCallExprAdapter(
    val pirCall: PIRCall,
) : CommonCallExpr {

    override val typeName: String
        get() = (pirCall.target?.type ?: PIRAnyType).typeName

    override val args: List<CommonValue> = pirCall.args.map { it.value }
}

val PIRInstruction.callExpr: PIRCallExprAdapter?
    get() = if (this is PIRCall) PIRCallExprAdapter(this) else null
