package org.opentaint.dataflow.jvm.ap.ifds.alias

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import mu.KLogging
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.analysis.alias.AAInfo
import org.opentaint.dataflow.ap.ifds.analysis.alias.AAInfoManager
import org.opentaint.dataflow.ap.ifds.analysis.alias.AnalysisCancellation
import org.opentaint.dataflow.ap.ifds.analysis.alias.AnalysisResult
import org.opentaint.dataflow.ap.ifds.analysis.alias.ContextInfo
import org.opentaint.dataflow.ap.ifds.analysis.alias.DsuMergeStrategy
import org.opentaint.dataflow.ap.ifds.analysis.alias.HeapAlias
import org.opentaint.dataflow.ap.ifds.analysis.alias.ImmutableState
import org.opentaint.dataflow.ap.ifds.analysis.alias.allElements
import org.opentaint.dataflow.ap.ifds.analysis.alias.withAnalysisCancellation
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
import org.opentaint.dataflow.jvm.ap.ifds.alias.JIRIntraProcAliasAnalysis.Convert.compress
import org.opentaint.dataflow.jvm.ap.ifds.alias.RefValue.Local
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.forEachInt
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRNullConstant
import org.opentaint.ir.api.jvm.cfg.JIRStringConstant
import org.opentaint.jvm.graph.JApplicationGraph
import org.opentaint.util.analysis.ApplicationGraph

class JIRIntraProcAliasAnalysis(
    private val entryPoint: JIRInst,
    private val graph: JApplicationGraph,
    private val callResolver: JIRCallResolver,
    private val externalCallModelProvider: ExternalCallModelProvider,
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

    private inner class CallResolver: JirCallResolver(callResolver, graph, externalCallModelProvider, params) {
        override fun buildMethodJig(entryPoint: JIRInst): JIRInstGraph = getJIG(entryPoint)
    }

    fun compute(
        localVariableReachability: JIRLocalVariableReachability
    ): AnalysisResult? =
        withAnalysisCancellation(
            timeLimit = params.aliasAnalysisTimeLimit,
            parentCancellation = rootCancellation,
            body = { compute(it, localVariableReachability) },
            onAnalysisCancelled = {
                logger.error {
                    "Alias analysis for ${entryPoint.location.method} exceed ${params.aliasAnalysisTimeLimit}"
                }

                null
            }
        )

    private fun compute(
        cancellation: AnalysisCancellation,
        localVariableReachability: JIRLocalVariableReachability
    ): AnalysisResult {
        val jig = getJIG(entryPoint)
        val analyzer = DSUAliasAnalysis(CallResolver(), localVariableReachability, cancellation)
        val result = analyzer.analyze(jig)
        return result.compress(localVariableReachability)
    }

    object Convert {
        private fun AliasInfo.willDepthExceedLimit(depth: Int) =
            this !is AliasApInfo || accessors.size + depth >= HEAP_CHAIN_LIMIT

        private fun IntArrayList.hasTwoOfInstance(instance: Int) =
            count { it == instance } >= 2

        private fun AliasInfo.isHeapAllowedBase() =
            this is AliasApInfo && base !is AccessPathBase.Constant

        fun AAInfo.convertToAliasInfo(
            depth: Int,
            cancellation: AnalysisCancellation?,
            resolveHeapInstance: (Int) -> List<Pair<IntArrayList, AliasInfo>>,
        ): List<Pair<IntArrayList, AliasInfo>> {
            if (this !is HeapAlias) {
                val base = convertBaseAccessor(this) ?: return emptyList()
                return listOf(IntArrayList(0) to base)
            }

            if (depth > HEAP_CHAIN_LIMIT) {
                return emptyList()
            }

            cancellation?.checkpoint()

            val instances = resolveHeapInstance(instance)
                .filterNot { it.second.willDepthExceedLimit(depth) || it.first.hasTwoOfInstance(instance) }
                .filter { it.second.isHeapAllowedBase() }

            val accessor = when (val a = this.heapAccessor) {
                is ArrayAlias -> AliasAccessor.Array
                is FieldAlias -> a.field
                else -> error("Impossible")
            }

            return instances.map { (usedInstances, aliasInfo) ->
                check(aliasInfo is AliasApInfo)
                val newUsedInstances = usedInstances.clone()
                newUsedInstances.add(instance)
                val newAliasInfo = AliasApInfo(aliasInfo.base, aliasInfo.accessors + accessor)
                newUsedInstances to newAliasInfo
            }
        }

        fun convertBaseAccessor(cur: AAInfo): AliasInfo? {
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
                    val expr = const?.expr

                    if (expr is JIRNullConstant) return null

                    val stringConst = expr as? JIRStringConstant
                        ?: return AliasAllocInfo(cur.stmt.originalIdx)

                    AccessPathBase.Constant("java.lang.String", stringConst.value)
                }

                is CallReturn,
                is Unknown -> return null

                is HeapAlias -> error("unreachable")
                else -> error("Impossible aa-info")
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

        fun AnalysisResult.compress(reachableLocals: JIRLocalVariableReachability): AnalysisResult {
            val compressed = AnalysisResult(
                AAInfoManager(),
                arrayOfNulls(statesBeforeStmt.size),
                arrayOfNulls(statesBeforeStmt.size)
            )
            val compressedStrategy = DsuMergeStrategy(compressed.manager)

            for (i in statesBeforeStmt.indices) {
                compressed.statesBeforeStmt[i] = statesBeforeStmt[i]?.let {
                    compress(
                        it, reachableLocals, i,
                        manager, compressed.manager, compressedStrategy,
                        compressed.statesBeforeStmt, compressed.statesAfterStmt
                    )
                }

                compressed.statesAfterStmt[i] = statesAfterStmt[i]?.let {
                    compress(
                        it, reachableLocals, i,
                        manager, compressed.manager, compressedStrategy,
                        compressed.statesBeforeStmt, compressed.statesAfterStmt
                    )
                }
            }

            return compressed
        }

        private fun compress(
            originalState: ImmutableState,
            reachability: JIRLocalVariableReachability,
            idx: Int,
            currentManager: AAInfoManager,
            newManager: AAInfoManager,
            newStrategy: DsuMergeStrategy,
            ref0: Array<ImmutableState?>,
            ref1: Array<ImmutableState?>,
        ): ImmutableState? {
            val state = originalState.mutableCopy()

            val unreachableElements = IntOpenHashSet()
            state.allElements().forEachInt { element ->
                val elementInfo = currentManager.getElementUncheck(element)
                if (elementInfo is HeapAlias) return@forEachInt

                val baseInfo = convertBaseAccessor(elementInfo)
                if (baseInfo == null) {
                    unreachableElements.add(element)
                    return@forEachInt
                }

                if (baseInfo is AliasApInfo) {
                    if (!reachability.isReachable(baseInfo.base, idx)) {
                        unreachableElements.add(element)
                        return@forEachInt
                    }
                }
            }

            val cleanState = state.removeUnsafe(unreachableElements)
            if (cleanState.isEmpty()) {
                return null
            }

            val compressedState = cleanState.translate(newManager, newStrategy)

            if (idx == 0) return compressedState

            if (compressedState == ref0[idx - 1]) return ref0[idx - 1]
            if (compressedState == ref1[idx - 1]) return ref1[idx - 1]
            return compressedState
        }
    }
}
