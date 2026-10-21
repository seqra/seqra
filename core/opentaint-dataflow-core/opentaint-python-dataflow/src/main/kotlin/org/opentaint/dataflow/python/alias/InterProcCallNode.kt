package org.opentaint.dataflow.python.alias

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.analysis.alias.ContextInfo
import org.opentaint.dataflow.ap.ifds.analysis.alias.ImmutableState
import org.opentaint.dataflow.python.PIRCallResolver
import org.opentaint.dataflow.python.PIRFlowFunctionUtils
import org.opentaint.dataflow.python.graph.PIRUnknownFunction
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRCallArgKind
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.util.analysis.ApplicationGraph
import java.util.BitSet
import org.opentaint.dataflow.python.util.indexOfKeywordParam

interface CallResolver {
    fun resolveMethodCall(call: PIRCall, level: Int): List<PIRFunction>?
    fun buildMethodGraph(method: PIRFunction): PIRInstGraph?
}

class AliasCallResolver(
    private val callResolver: PIRCallResolver,
    private val graph: ApplicationGraph<CommonMethod, CommonInst>,
    private val languageManager: LanguageManager,
    private val params: PIRLocalAliasAnalysis.Params,
) : CallResolver {
    override fun resolveMethodCall(call: PIRCall, level: Int): List<PIRFunction>? {
        if (level >= params.aliasAnalysisInterProcCallDepth) return null
        val methods = callResolver.resolveCall(call).filter { it !is PIRUnknownFunction }
        return methods.takeIf { it.isNotEmpty() }
    }

    override fun buildMethodGraph(method: PIRFunction): PIRInstGraph? {
        val entry = graph.methodGraph(method).entryPoints().singleOrNull() ?: return null
        return buildPirInstGraph(languageManager, graph, method, entry)
    }
}

class CallTreeNode(val ctx: ContextInfo, val instEvalCtx: InstEvalContext) {
    private val emptyCalls = BitSet()
    private val calls = Int2ObjectOpenHashMap<Map<PIRFunction, ResolvedCallMethod>>()

    fun resolveCall(call: PIRCall, callResolver: CallResolver): Map<PIRFunction, ResolvedCallMethod>? {
        val callIdx = call.location.index
        if (emptyCalls.get(callIdx)) return null

        return calls.getOrPut(callIdx) {
            val resolved = resolveCallNoCache(call, ctx, instEvalCtx, callResolver)
            if (resolved == null) {
                emptyCalls.set(callIdx)
                return null
            }

            resolved
        }
    }
}

class NestedCallInstEvalCtx(
    private val paramActuals: Array<RefValue?>,
    private val ctx: ContextInfo,
) : InstEvalContext {
    override fun createArg(idx: Int): RefValue =
        paramActuals.getOrNull(idx) ?: RefValue.Local(-(idx + 1), ctx)

    override fun createLocal(idx: Int): RefValue.Local = RefValue.Local(idx, ctx)
}

private fun resolveCallNoCache(
    call: PIRCall,
    callerCtx: ContextInfo,
    callerCtxEval: InstEvalContext,
    callResolver: CallResolver,
): Map<PIRFunction, ResolvedCallMethod>? {
    val methods = callResolver.resolveMethodCall(call, callerCtx.level) ?: return null

    val resolved = methods.mapIndexedNotNull { idx, method ->
        val graph = callResolver.buildMethodGraph(method) ?: return@mapIndexedNotNull null
        val nestedCtx = ContextInfo(callerCtx.context + mkContextId(call, idx))
        val instEvalCtx = NestedCallInstEvalCtx(bindParamActuals(call, method, callerCtxEval), nestedCtx)
        val analysisState = GraphAnalysisState(graph.statements.size, CallTreeNode(nestedCtx, instEvalCtx))
        method to ResolvedCallMethod(graph, analysisState)
    }.toMap()

    return resolved.takeIf { it.isNotEmpty() }
}

private fun bindParamActuals(call: PIRCall, method: PIRFunction, callerCtxEval: InstEvalContext): Array<RefValue?> {
    val offset = PIRFlowFunctionUtils.implicitParamOffset(method)
    val actuals = arrayOfNulls<RefValue>(method.parameters.size)
    for ((slot, arg) in call.args.withIndex()) {
        val paramIdx = when (arg.kind) {
            PIRCallArgKind.POSITIONAL -> slot + offset
            PIRCallArgKind.KEYWORD -> arg.keyword?.let { method.indexOfKeywordParam(it) } ?: continue
            PIRCallArgKind.STAR, PIRCallArgKind.DOUBLE_STAR -> continue
        }

        if (paramIdx in actuals.indices) actuals[paramIdx] = callerCtxEval.refValue(arg.value)
    }
    return actuals
}

private fun mkContextId(call: PIRCall, methodIdx: Int): Int = call.location.index * 1000 + methodIdx

object RootInstEvalContext : InstEvalContext {
    override fun createArg(idx: Int): RefValue = RefValue.Arg(idx)
    override fun createLocal(idx: Int): RefValue.Local = RefValue.Local(idx, ContextInfo.rootContext)
}

class GraphAnalysisState(size: Int, val call: CallTreeNode) {
    val stateBeforeStmt = arrayOfNulls<ImmutableState>(size)
    val stateAfterStmt = arrayOfNulls<ImmutableState>(size)
}

class ResolvedCallMethod(val graph: PIRInstGraph, val state: GraphAnalysisState)
