package org.opentaint.dataflow.jvm.bidi.interp

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps
import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.interp.ExitNode
import org.opentaint.dataflow.bidi.interp.ExitRules
import org.opentaint.dataflow.bidi.interp.FormsCache
import org.opentaint.dataflow.bidi.interp.StatementSummary
import org.opentaint.dataflow.graph.MethodInstGraph
import org.opentaint.dataflow.graph.reversed
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalVariableReachability
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.jvm.graph.JMethodEnterInst
import org.opentaint.jvm.graph.JMethodExitExceptionalInst
import org.opentaint.jvm.graph.JMethodExitNormalInst
import org.opentaint.util.analysis.ApplicationGraph

/** Graphs, alias inputs, statements and exit forms shared by every context of one method. */
class JIRMethodEntry internal constructor(val method: JIRMethod, internal val shared: JIRMethodEntries) {
    init {
        val instructions = method.instList.instructions
        check(instructions.isNotEmpty()) { "An empty method has no analysable entry: $method" }
        val n = instructions.size
        check(instructions.getOrNull(n - 3) is JMethodEnterInst &&
            instructions.getOrNull(n - 2) is JMethodExitNormalInst &&
            instructions.getOrNull(n - 1) is JMethodExitExceptionalInst) {
            "Method $method must end with the entry, normal exit and exceptional exit boundary triple"
        }
    }

    internal val size = shared.languageManager.getMaxInstIndex(method) + 1
    fun index(statement: CommonInst): Int = shared.languageManager.getInstIndex(statement)

    @Suppress("UNCHECKED_CAST")
    private val common get() = shared.graph as ApplicationGraph<CommonMethod, CommonInst>

    /** Reachability belongs to the existing alias analysis; forms have no liveness step. */
    val aliasAnalysis: JIRLocalAliasAnalysis? by lazy {
        if (!shared.aliasParams.useAliasAnalysis) null
        else JIRLocalAliasAnalysis(shared.graph.methodGraph(method).entryPoints().first(), shared.graph,
            shared.callResolver, shared.rules, JIRLocalVariableReachability(method, shared.graph, shared.languageManager),
            shared.cancellation, shared.languageManager, shared.aliasParams)
    }

    val lambdas: Int2ObjectMap<Set<JIRMethod>> = shared.prescanLambdas[method] ?: Int2ObjectMaps.emptyMap()

    private val forwardGraph by lazy { MethodInstGraph.build(shared.languageManager, common, method) }
    private val backwardGraph by lazy {
        MethodInstGraph.build(shared.languageManager, JIRExitWiredGraph(common).reversed, method)
    }

    fun graph(direction: Direction): MethodInstGraph =
        if (direction == Direction.FORWARD) forwardGraph else backwardGraph

    internal val statements = FormsCache<StatementSummary>(size) { it.reversed() }
    internal val exits = FormsCache<ExitRules>(size) {
        ExitRules(it.reversed(), globalStateDrop = false, entryMarks = emptySet())
    }

    @Suppress("UNUSED_PARAMETER")
    fun exitNodes(key: MethodKey): List<ExitNode> = shared.graph.methodGraph(method).exitPoints()
        .map { ExitNode(it, it is JMethodExitExceptionalInst) }.toList()
}
