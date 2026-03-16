package org.opentaint.dataflow.jvm.ap.ifds.alias

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.alias.DSUAliasAnalysis.GraphAnalysisState
import org.opentaint.dataflow.jvm.ap.ifds.alias.DSUAliasAnalysis.ResolvedCallMethod
import org.opentaint.dataflow.jvm.ap.ifds.alias.JIRIntraProcAliasAnalysis.JIRInstGraph
import org.opentaint.dataflow.jvm.ap.ifds.alias.RefValue.Local
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.jvm.graph.JApplicationGraph
import java.util.BitSet

interface CallResolver {
    fun resolveMethodCall(callStmt: Stmt.Call, level: Int): List<JIRMethod>?
    fun buildMethodGraph(method: JIRMethod): JIRInstGraph?
}

abstract class JirCallResolver(
    val callResolver: JIRCallResolver,
    val graph: JApplicationGraph,
    val params: JIRLocalAliasAnalysis.Params
): CallResolver {
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

class CallTreeNode(val ctx: ContextInfo, val instEvalCtx: InstEvalContext) {
    private val emptyCalls = BitSet()
    private val calls = Int2ObjectOpenHashMap<ResolvedCall>()

    fun resolveCall(stmt: Stmt.Call, callResolver: CallResolver): Map<JIRMethod, ResolvedCallMethod>? {
        if (emptyCalls.get(stmt.originalIdx)) return ResolvedCall.empty.methods

        return calls.getOrPut(stmt.originalIdx) {
            val resolved = resolveCallNoCache(stmt, ctx, callResolver)
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

private class NestedCallInstEvalCtx(val call: Stmt.Call, val ctx: ContextInfo) : InstEvalContext {
    override fun createArg(idx: Int): Value = call.args.getOrNull(idx)
        ?: error("Incorrect argument idx: $idx")

    override fun createThis(isOuter: Boolean): Value = call.instance
        ?: error("Non instance call")

    override fun createLocal(idx: Int): Local = Local(idx, ctx)
}

private fun resolveCallNoCache(stmt: Stmt.Call, ctx: ContextInfo, callResolver: CallResolver): ResolvedCall {
    val methods = callResolver.resolveMethodCall(stmt, ctx.level)
        ?: return ResolvedCall.empty

    val resolvedCall = methods.mapIndexedNotNull { idx, method ->
        val graph = callResolver.buildMethodGraph(method)
            ?: return@mapIndexedNotNull null

        val nestedCtx = ContextInfo(ctx.context + mkContextId(stmt, idx))
        val instEvalCtx = NestedCallInstEvalCtx(stmt, nestedCtx)
        val state = GraphAnalysisState(graph.statements.size, CallTreeNode(nestedCtx, instEvalCtx))
        method to ResolvedCallMethod(graph, state)
    }.toMap()

    if (resolvedCall.isEmpty()) return ResolvedCall.empty

    return ResolvedCall(resolvedCall)
}

private fun mkContextId(stmt: Stmt.Call, methodIdx: Int): Int =
    (stmt.originalIdx * 1000) + methodIdx
