package org.opentaint.dataflow.jvm.ap.ifds.alias

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.alias.DSUAliasAnalysis.GraphAnalysisState
import org.opentaint.dataflow.jvm.ap.ifds.alias.DSUAliasAnalysis.ResolvedCallMethod
import org.opentaint.dataflow.jvm.ap.ifds.alias.JIRIntraProcAliasAnalysis.JIRInstGraph
import org.opentaint.dataflow.jvm.ap.ifds.alias.RefValue.Local
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodCallResolver
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.jvm.graph.JApplicationGraph
import java.util.BitSet

interface CallResolver {
    fun resolveMethodCall(callStmt: Stmt.Call, level: Int): List<JIRMethod>?
    fun buildMethodGraph(method: JIRMethod): JIRInstGraph?
}

abstract class JirCallResolver(
    val methodCallResolver: JIRMethodCallResolver,
    val graph: JApplicationGraph,
    val params: JIRLocalAliasAnalysis.Params
): CallResolver {
    val callResolver = methodCallResolver.callResolver

    abstract fun buildMethodJig(entryPoint: JIRInst): JIRInstGraph

    override fun resolveMethodCall(callStmt: Stmt.Call, level: Int): List<JIRMethod>? {
        if (level >= params.aliasAnalysisInterProcCallDepth) return null

        val methods = callResolver.allKnownOverridesOrNull(callStmt.method)
            ?: return null

        return methods.takeIf { it.isNotEmpty() }
    }

    override fun buildMethodGraph(method: JIRMethod): JIRInstGraph? {
        val entryPoint = graph.methodGraph(method).entryPoints().singleOrNull()
            ?: return null

        return buildMethodJig(entryPoint)
    }
}

class CallTreeNode(val level: Int, val instEvalCtx: InstEvalContext) {
    private val emptyCalls = BitSet()
    private val calls = Int2ObjectOpenHashMap<ResolvedCall>()

    fun resolveCall(stmt: Stmt.Call, callResolver: CallResolver): Map<JIRMethod, ResolvedCallMethod>? {
        if (emptyCalls.get(stmt.originalIdx)) return ResolvedCall.empty.methods

        return calls.getOrPut(stmt.originalIdx) {
            val resolved = resolveCallNoCache(stmt, level, callResolver)
            if (resolved === ResolvedCall.empty) {
                emptyCalls.set(stmt.originalIdx)
                return ResolvedCall.empty.methods
            }

            resolved
        }.methods
    }
}

private class ResolvedCall(val methods: Map<JIRMethod, ResolvedCallMethod>?) {
    companion object {
        val empty = ResolvedCall(methods = null)
    }
}

private class NestedCallInstEvalCtx(val call: Stmt.Call, val level: Int) : InstEvalContext {
    override fun createArg(idx: Int): Value = call.args.getOrNull(idx)
        ?: error("Incorrect argument idx: $idx")

    override fun createThis(): Value = call.instance
        ?: error("Non instance call")

    override fun createLocal(idx: Int): Local = Local(idx, level = level, ctx = call.originalIdx)
}

private fun resolveCallNoCache(stmt: Stmt.Call, level: Int, callResolver: CallResolver): ResolvedCall {
    val methods = callResolver.resolveMethodCall(stmt, level)
        ?: return ResolvedCall.empty

    val resolvedCall = methods.mapNotNull { method ->
        val graph = callResolver.buildMethodGraph(method)
            ?: return@mapNotNull null

        val nextLevel = level + 1
        val instEvalCtx = NestedCallInstEvalCtx(stmt, nextLevel)
        val state = GraphAnalysisState(graph.statements.size, CallTreeNode(nextLevel, instEvalCtx))
        method to ResolvedCallMethod(graph, state)
    }.toMap()

    if (resolvedCall.isEmpty()) return ResolvedCall.empty

    return ResolvedCall(resolvedCall)
}
