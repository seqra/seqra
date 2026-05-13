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
import org.opentaint.dataflow.util.Cancellation
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
    private val rootCancellation: Cancellation,
    private val params: JIRLocalAliasAnalysis.Params,
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
            parentCancellation = rootCancellation,
            body = { compute(it, localVariableReachability) },
            onAnalysisCancelled = {
                logger.error {
                    "Alias analysis for ${entryPoint.location.method} exceed ${params.aliasAnalysisTimeLimit}"
                }

                JIRLocalAliasAnalysis.MethodAliasInfo(
                    aliasBeforeStatement = null,
                    aliasAfterStatement = null,
                    unboundBeforeStatement = null,
                )
            }
        )

    private fun compute(
        cancellation: AnalysisCancellation,
        localVariableReachability: JIRLocalVariableReachability
    ): JIRLocalAliasAnalysis.MethodAliasInfo {
        val jig = getJIG(entryPoint)
        val daa = DSUAliasAnalysis(CallResolver(), localVariableReachability, cancellation).analyze(jig)

        val aliasBeforeStatement = Array(jig.statements.size) { Int2ObjectOpenHashMap<List<AliasInfo>>() }
        val aliasAfterStatement = Array(jig.statements.size) { Int2ObjectOpenHashMap<List<AliasInfo>>() }

        val unboundAliasBeforeStatement = Array(jig.statements.size) { mutableListOf<List<AliasInfo>>() }
        val unboundAliasAfterStatement = Array(jig.statements.size) { mutableListOf<List<AliasInfo>>() }

        for (i in jig.statements.indices) {
            resolveLocalVar(
                daa.statesBeforeStmt[i], localVariableReachability,
                aliasBeforeStatement[i], unboundAliasBeforeStatement[i],
                i, cancellation
            )

            resolveLocalVar(
                daa.statesAfterStmt[i], localVariableReachability,
                aliasAfterStatement[i], unboundAliasAfterStatement[i],
                i, cancellation
            )
        }

        return compressAliasInfo(aliasBeforeStatement, aliasAfterStatement, unboundAliasBeforeStatement)
    }

    private fun compressAliasInfo(
        aliasBeforeStatement: Array<Int2ObjectOpenHashMap<List<AliasInfo>>>,
        aliasAfterStatement: Array<Int2ObjectOpenHashMap<List<AliasInfo>>>,
        unboundBeforeStatement: Array<MutableList<List<AliasInfo>>>,
    ): JIRLocalAliasAnalysis.MethodAliasInfo {
        val compressedBefore = arrayOfNulls<Int2ObjectOpenHashMap<Array<Any>>>(aliasBeforeStatement.size)
        val compressedAfter = arrayOfNulls<Int2ObjectOpenHashMap<Array<Any>>>(aliasAfterStatement.size)

        compress(aliasBeforeStatement, compressedBefore, reference = null, referenceCompressed = null)
        compress(aliasAfterStatement, compressedAfter, aliasBeforeStatement, compressedBefore)

        val compressedUnbound = compressUnboundAliases(unboundBeforeStatement)
        return JIRLocalAliasAnalysis.MethodAliasInfo(compressedBefore, compressedAfter, compressedUnbound)
    }

    private fun compressUnboundAliases(
        statementInfo: Array<MutableList<List<AliasInfo>>>
    ): Array<Array<Array<Any>>?>? {
        if (statementInfo.all { it.isEmpty() }) return null

        val compressed = arrayOfNulls<Array<Array<Any>>>(statementInfo.size)
        for (i in statementInfo.indices) {
            val current = statementInfo[i]
            if (current.isEmpty()) continue

            if (i > 0 && statementInfo[i - 1] == current) {
                compressed[i] = compressed[i - 1]
                continue
            }

            val unwrapped = Array(current.size) { i ->
                JIRLocalAliasAnalysis.unwrapAliasSet(current[i])
            }
            compressed[i] = unwrapped
        }
        return compressed
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
        result: Int2ObjectOpenHashMap<List<AliasInfo>>,
        unboundAliases: MutableList<List<AliasInfo>>,
        instIdx: Int,
        cancellation: AnalysisCancellation,
    ) {
        daa.aliasGroups.forEach { (_, group) ->
            val converted = group
                .flatMap { it.convertToAliasInfo(daa.aliasGroups, depth = 0, cancellation) }
                .filter { it !is AliasApInfo || reachableLocals.isReachable(it.base, instIdx) }
                .distinct()

            // size == 1 means only local was converted to AliasInfo; not really meaningful
            if (converted.size <= 1) return@forEach

            val locals = converted.filterIsInstance<AliasApInfo>()
                .filter { it.accessors.isEmpty() }
                .mapNotNull { it.base as? AccessPathBase.LocalVar }

            if (locals.isEmpty()) {
                unboundAliases += converted
                return@forEach
            }

            locals.forEach { local ->
                result[local.idx] = converted
            }
        }
    }

    private fun AAInfo.convertToAliasInfo(
        aliasGroups: Int2ObjectOpenHashMap<List<AAInfo>>,
        depth: Int,
        cancellation: AnalysisCancellation,
    ): List<AliasInfo> {
        if (this !is HeapAlias) {
            val base = convertBaseAccessor(this)
            return listOfNotNull(base)
        }

        if (depth > HEAP_CHAIN_LIMIT) {
            return emptyList()
        }

        cancellation.checkpoint()

        val instanceGroup = aliasGroups[instance] ?: return emptyList()
        val instances = instanceGroup.flatMap { it.convertToAliasInfo(aliasGroups, depth + 1, cancellation) }
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
                val assignedExpr = cur.stmt.assignedExpr()
                    ?: return null

                val const = assignedExpr as? SimpleValue.RefConst

                val stringConst = const?.expr as? JIRStringConstant
                    ?: return AliasAllocInfo(cur.stmt.originalIdx)

                AccessPathBase.Constant("java.lang.String", stringConst.value)
            }

            is CallReturn,
            is Unknown -> return null

            is HeapAlias -> error("unreachable")
        }

        return AliasApInfo(base, emptyList())
    }

    private fun Stmt.assignedExpr(): Expr? = when (this) {
        is Stmt.Assign -> expr
        is Stmt.FieldStore -> value as? Expr
        is Stmt.ArrayStore -> value as? Expr
        is Stmt.WriteStatic -> value as? Expr
        else -> null
    }
}
