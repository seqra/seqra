package org.opentaint.dataflow.python.adapter

import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRCallArgKind

/**
 * Adapts PIRCall (an instruction) to CommonCallExpr (an expression).
 *
 * The dataflow engine expects CommonCallExpr with args: List<CommonValue>.
 * PIRCall has args: List<PIRCallArg> where each PIRCallArg wraps a PIRValue.
 *
 * Since PIRValue now extends CommonValue, we can directly extract the values.
 */
class PIRCallExprAdapter(
    val pirCall: PIRCall,
) : CommonCallExpr {

    override val typeName: String get() = "call"

    override val args: List<CommonValue>
        get() = pirCall.args
            .filter { it.kind == PIRCallArgKind.POSITIONAL || it.kind == PIRCallArgKind.KEYWORD }
            .map { it.value }
}
