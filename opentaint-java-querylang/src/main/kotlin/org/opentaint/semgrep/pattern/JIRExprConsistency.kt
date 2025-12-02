package org.opentaint.semgrep.pattern

import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.jvm.graph.JIRApplicationGraph

// TODO: points-to analysis
fun checkJIRExprConsistency(
    graph: JIRApplicationGraph,
    strategy: LocalVarStrategy,
    a: Pair<JIRExpr, ExprPosition?>,
    b: Pair<JIRExpr, ExprPosition?>,
): Boolean {
    val (aExpr, aInst) = a
    val (bExpr, bInst) = b
    return aExpr == bExpr
}
