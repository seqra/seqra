package org.opentaint.dataflow.jvm.ap.ifds.alias

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import mu.KLogging
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.graph.CompactGraph
import org.opentaint.dataflow.graph.MethodInstGraph
import org.opentaint.dataflow.jvm.ap.ifds.JIRCallResolver
import org.opentaint.dataflow.jvm.ap.ifds.JIRLanguageManager
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAccessor
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAllocInfo
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasApInfo
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasInfo
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalVariableReachability
import org.opentaint.dataflow.jvm.ap.ifds.alias.DSUAliasAnalysis.ConnectedAliases
import org.opentaint.dataflow.jvm.ap.ifds.alias.RefValue.Local
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRStringConstant
import org.opentaint.jvm.graph.JApplicationGraph
import org.opentaint.util.analysis.ApplicationGraph

class JIRIntraProcAliasAnalysis(
    private val entryPoint: JIRInst,
    private val graph: JApplicationGraph,
    private val callResolver: JIRCallResolver,
    private val languageManager: JIRLanguageManager,
    private val params: JIRLocalAliasAnalysis.Params,
    private val mergeType: MergeType,
) {
    companion object {
        private val logger = object : KLogging() {}.logger
        private const val HEAP_CHAIN_LIMIT = 5
    }

    data class JIRInstGraph(
        val statements: List<JIRInst>,
        val graph: CompactGraph,
        val initialIdx: Int,
    )

    private fun getJIG(entryPoint: JIRInst): JIRInstGraph {
        @Suppress("UNCHECKED_CAST")
        val instGraph = MethodInstGraph.build(
            languageManager,
            graph as ApplicationGraph<CommonMethod, CommonInst>,
            entryPoint.location.method
        )

        return JIRInstGraph(
            statements = instGraph.instructions.map { it as JIRInst },
            graph = instGraph.graph,
            initialIdx = languageManager.getInstIndex(entryPoint)
        )
    }

    private inner class CallResolver: JirCallResolver(callResolver, graph, params) {
        override fun buildMethodJig(entryPoint: JIRInst): JIRInstGraph = getJIG(entryPoint)
    }

    fun compute(
        localVariableReachability: JIRLocalVariableReachability
    ): JIRLocalAliasAnalysis.MethodAliasInfo =
        withAnalysisCancellation(
            timeLimit = params.aliasAnalysisTimeLimit,
            body = { compute(it, localVariableReachability) },
            onAnalysisCancelled = {
                logger.error {
                    "Alias analysis for ${entryPoint.location.method} exceed ${params.aliasAnalysisTimeLimit}"
                }

                JIRLocalAliasAnalysis.MethodAliasInfo(
                    aliasBeforeStatement = null,
                    aliasAfterStatement = null
                )
            }
        )

    private fun compute(
        cancellation: AnalysisCancellation,
        localVariableReachability: JIRLocalVariableReachability
    ): JIRLocalAliasAnalysis.MethodAliasInfo {
        val jig = getJIG(entryPoint)
        val daa = DSUAliasAnalysis(CallResolver(), localVariableReachability, mergeType, cancellation).analyze(jig)

        val aliasBeforeStatement = Array(jig.statements.size) { i ->
            resolveLocalVar(daa.statesBeforeStmt[i], localVariableReachability, i)
        }

        val aliasAfterStatement = Array(jig.statements.size) { i ->
            resolveLocalVar(daa.statesAfterStmt[i], localVariableReachability, i)
        }

        return compressAliasInfo(aliasBeforeStatement, aliasAfterStatement)
    }

    private fun compressAliasInfo(
        aliasBeforeStatement: Array<Int2ObjectOpenHashMap<List<AliasInfo>>>,
        aliasAfterStatement: Array<Int2ObjectOpenHashMap<List<AliasInfo>>>
    ): JIRLocalAliasAnalysis.MethodAliasInfo {
        val compressedBefore = arrayOfNulls<Int2ObjectOpenHashMap<Array<Any>>>(aliasBeforeStatement.size)
        val compressedAfter = arrayOfNulls<Int2ObjectOpenHashMap<Array<Any>>>(aliasAfterStatement.size)

        compress(aliasBeforeStatement, compressedBefore, reference = null, referenceCompressed = null)
        compress(aliasAfterStatement, compressedAfter, aliasBeforeStatement, compressedBefore)
        return JIRLocalAliasAnalysis.MethodAliasInfo(compressedBefore, compressedAfter)
    }

    private fun compress(
        statementInfo: Array<Int2ObjectOpenHashMap<List<AliasInfo>>>,
        compressed: Array<Int2ObjectOpenHashMap<Array<Any>>?>,
        reference: Array<Int2ObjectOpenHashMap<List<AliasInfo>>>?,
        referenceCompressed: Array<Int2ObjectOpenHashMap<Array<Any>>?>?
    ) {
        for (i in statementInfo.indices) {
            val current = statementInfo[i]
            if (current.isEmpty()) continue

            if (i > 0 && statementInfo[i - 1] == current) {
                compressed[i] = compressed[i - 1]
                continue
            }

            if (reference != null) {
                if (reference[i] == current) {
                    compressed[i] = referenceCompressed!![i]
                }

                if (i > 0 && reference[i - 1] == current) {
                    compressed[i] = referenceCompressed!![i - 1]
                    continue
                }
            }

            val unwrapped = JIRLocalAliasAnalysis.unwrapAllInfo(current)
            compressed[i] = unwrapped
        }
    }

    private fun resolveLocalVar(
        daa: ConnectedAliases,
        reachableLocals: JIRLocalVariableReachability,
        instIdx: Int
    ): Int2ObjectOpenHashMap<List<AliasInfo>> {
        val result = Int2ObjectOpenHashMap<List<AliasInfo>>()
        daa.aliasGroups.forEach { (_, group) ->
            val locals = group.filter {
                it is LocalAlias.SimpleLoc && it.loc is Local && reachableLocals.isReachable(it.loc.idx, instIdx)
            }
            if (locals.isEmpty()) return@forEach

            val converted = group
                .flatMap { it.convertToAliasInfo(daa.aliasGroups, depth = 0) }
                .filter { it !is AliasApInfo || reachableLocals.isReachable(it.base, instIdx) }
                .distinct()

            // size == 1 means only local was converted to AliasInfo; not really meaningful
            if (converted.size <= 1) return@forEach
            locals.forEach { local ->
                val id = ((local as LocalAlias.SimpleLoc).loc as Local).idx
                result[id] = converted
            }
        }
        return result
    }

    private fun AAInfo.convertToAliasInfo(
        aliasGroups: Int2ObjectOpenHashMap<List<AAInfo>>,
        depth: Int
    ): List<AliasInfo> {
        if (this !is HeapAlias) {
            val base = convertBaseAccessor(this)
            return listOfNotNull(base)
        }

        if (depth > HEAP_CHAIN_LIMIT) {
            return emptyList()
        }

        val instanceGroup = aliasGroups[instance] ?: return emptyList()
        val instances = instanceGroup.flatMap { it.convertToAliasInfo(aliasGroups, depth + 1) }
        val accessor = when (val a = this.heapAccessor) {
            is ArrayAlias -> AliasAccessor.Array
            is FieldAlias -> a.field
        }

        return instances.mapNotNull {
            when (it) {
                is AliasAllocInfo -> return@mapNotNull null
                is AliasApInfo -> AliasApInfo(it.base, it.accessors + accessor)
            }
        }
    }

    private fun convertBaseAccessor(cur: AAInfo): AliasInfo? {
        if (cur.ctx != ContextInfo.rootContext) return null

        val base = when (cur) {
            is LocalAlias.SimpleLoc -> when (val loc = cur.loc) {
                is Local -> AccessPathBase.LocalVar(loc.idx)
                is RefValue.Arg -> AccessPathBase.Argument(loc.idx)
                is RefValue.This -> AccessPathBase.This
                is RefValue.Static -> {
                    val staticAccessors = listOf(AliasAccessor.Static(loc.type))
                    return AliasApInfo(AccessPathBase.ClassStatic, staticAccessors)
                }
            }

            is LocalAlias.Alloc -> {
                val assign = cur.stmt as? Stmt.Assign ?: return null

                val const = assign.expr as? SimpleValue.RefConst
                val stringConst = const?.expr as? JIRStringConstant
                    ?: return AliasAllocInfo(assign.originalIdx)

                AccessPathBase.Constant("java.lang.String", stringConst.value)
            }

            is CallReturn,
            is Unknown -> return null

            is HeapAlias -> error("unreachable")
        }

        return AliasApInfo(base, emptyList())
    }
}
