package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.ApMode
import org.opentaint.dataflow.bidi.ap.ApOps
import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.interp.RuleView
import org.opentaint.dataflow.bidi.interp.DirectedForms
import org.opentaint.dataflow.bidi.interp.Interpreter
import org.opentaint.dataflow.bidi.interp.MethodForms
import org.opentaint.dataflow.bidi.store.VulnerabilityStore
import org.opentaint.dataflow.graph.MethodInstGraph
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst

/** The language cache owns both form directions and method graphs. */
interface MethodContextSource {
    fun forms(key: MethodKey): MethodForms
    fun graph(key: MethodKey, direction: Direction): MethodInstGraph
}

class MethodContextCache(val interpreter: Interpreter, private val source: MethodContextSource) {
    fun forms(key: MethodKey): MethodForms = source.forms(key)
    fun graph(key: MethodKey, direction: Direction): MethodInstGraph = source.graph(key, direction)
    fun directed(direction: Direction, view: RuleView = RuleView.FULL): DirectedForms =
        DirectedForms(interpreter, direction, ::forms, view)
}

/** A run selects its graph and form direction while retaining the full method key. */
class RunMethodContext(
    val key: MethodKey,
    val forms: DirectedForms,
    val config: RunConfig,
    val shared: SharedObjects,
) {
    private val graph: MethodInstGraph = shared.contexts.graph(key, config.direction)
    private val ends: Set<CommonInst> = forms.endNodes(key).toHashSet()
    private val exceptionalExits: Set<CommonInst> =
        if (config.direction != Direction.FORWARD) emptySet()
        else shared.interpreter.exitNodes(key).filter { it.exceptional }.mapTo(HashSet()) { it.node }

    val mode: ApMode get() = config.mode
    val ops: ApOps get() = shared.ops
    val ap: ApManager get() = shared.ap
    val vulnerabilities: VulnerabilityStore get() = shared.vulnerabilities
    fun isEnd(n: CommonInst): Boolean = n in ends
    fun isExceptionalExit(n: CommonInst): Boolean = n in exceptionalExits
    fun forEachSuccessor(n: CommonInst, body: (CommonInst) -> Unit): Unit =
        graph.forEachSuccessor(shared.language, n, body)
    fun callAt(n: CommonInst): CommonCallExpr? = shared.language.getCallExpr(n)
}
