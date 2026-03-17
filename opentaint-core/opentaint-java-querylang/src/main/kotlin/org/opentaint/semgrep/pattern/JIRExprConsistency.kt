package org.opentaint.semgrep.pattern

import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.jvm.graph.JApplicationGraph

// TODO: points-to analysis
fun checkJIRExprConsistency(
    graph: JApplicationGraph,
    strategy: LocalVarStrategy,
    a: Pair<JIRExpr, ExprPosition?>,
    b: Pair<JIRExpr, ExprPosition?>,
): Boolean {
    val (aExpr, aInst) = a
    val (bExpr, bInst) = b
    return aExpr == bExpr
}
