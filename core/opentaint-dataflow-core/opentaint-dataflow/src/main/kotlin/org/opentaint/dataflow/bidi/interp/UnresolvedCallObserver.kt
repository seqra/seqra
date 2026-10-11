package org.opentaint.dataflow.bidi.interp

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.ir.api.common.cfg.CommonCallExpr

/** Run 1 reports each new TAINT delta at an unresolved stage. This hook does not select facts. */
fun interface UnresolvedCallObserver {
    fun reached(call: CommonCallExpr, position: AccessPathBase, plan: CallPlan)
}
