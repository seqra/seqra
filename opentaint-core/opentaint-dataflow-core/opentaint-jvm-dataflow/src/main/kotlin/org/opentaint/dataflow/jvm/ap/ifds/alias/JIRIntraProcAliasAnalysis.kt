package org.opentaint.dataflow.jvm.ap.ifds.alias

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.graph.CompactGraph
import org.opentaint.dataflow.graph.MethodInstGraph
import org.opentaint.dataflow.jvm.ap.ifds.JIRLanguageManager
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAccessor
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAllocInfo
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasApInfo
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasInfo
import org.opentaint.dataflow.jvm.ap.ifds.alias.DSUAliasAnalysis.AAInfo
import org.opentaint.dataflow.jvm.ap.ifds.alias.DSUAliasAnalysis.ArrayAlias
import org.opentaint.dataflow.jvm.ap.ifds.alias.DSUAliasAnalysis.CallReturn
import org.opentaint.dataflow.jvm.ap.ifds.alias.DSUAliasAnalysis.ConnectedAliases
import org.opentaint.dataflow.jvm.ap.ifds.alias.DSUAliasAnalysis.FieldAlias
import org.opentaint.dataflow.jvm.ap.ifds.alias.DSUAliasAnalysis.HeapAlias
import org.opentaint.dataflow.jvm.ap.ifds.alias.DSUAliasAnalysis.LocalAlias
import org.opentaint.dataflow.jvm.ap.ifds.alias.DSUAliasAnalysis.Unknown
import org.opentaint.dataflow.jvm.ap.ifds.alias.RefValue.Local
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodCallResolver
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRStringConstant
import org.opentaint.jvm.graph.JApplicationGraph
import org.opentaint.util.analysis.ApplicationGraph

class JIRIntraProcAliasAnalysis(
    private val entryPoint: JIRInst,
    private val graph: JApplicationGraph,
    private val callResolver: JIRMethodCallResolver,
    private val languageManager: JIRLanguageManager,
    private val params: JIRLocalAliasAnalysis.Params,
) {
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

    fun compute(): JIRLocalAliasAnalysis.MethodAliasInfo {
        val jig = getJIG(entryPoint)
        val daa = DSUAliasAnalysis(CallResolver()).analyze(jig)

        val aliasBeforeStatement =
            Array(jig.statements.size) { i -> resolveLocalVar(daa.statesBeforeStmt[i]) }.also { squash(it) }
        val aliasAfterStatement =
            Array(jig.statements.size) { i -> resolveLocalVar(daa.statesAfterStmt[i]) }.also { squash(it) }

        return JIRLocalAliasAnalysis.MethodAliasInfo(aliasBeforeStatement, aliasAfterStatement)
    }

    private fun squash(arr: Array<Int2ObjectOpenHashMap<List<AliasInfo>>>) {
        for (i in 1 until arr.size) {
            if (arr[i - 1] == arr[i]) {
                arr[i] = arr[i - 1]
            }
        }
    }

    private fun resolveLocalVar(daa: ConnectedAliases): Int2ObjectOpenHashMap<List<AliasInfo>> {
        val result = Int2ObjectOpenHashMap<List<AliasInfo>>()
        daa.aliasGroups.forEach { group ->
            val locals = group.filter { it is LocalAlias.SimpleLoc && it.loc is Local }
            if (locals.isEmpty()) return@forEach
            val converted = group.mapNotNull { it.convertToAliasInfo() }
            // size == 1 means only local was converted to AliasInfo; not really meaningful
            if (converted.size <= 1) return@forEach
            locals.forEach { local ->
                val id = ((local as LocalAlias.SimpleLoc).loc as Local).idx
                result[id] = converted
            }
        }
        return result
    }

    private fun AAInfo.convertToAliasInfo(): AliasInfo? {
        val (nonHeapInfo, accessors) = convertHeapAccessors(this)
        return convertBaseAccessor(nonHeapInfo, accessors)
    }

    private fun convertHeapAccessors(initial: AAInfo): Pair<AAInfo, List<AliasAccessor>> {
        var cur = initial
        val accessors = mutableListOf<AliasAccessor>()
        while (cur is HeapAlias) {
            when (cur) {
                is FieldAlias -> accessors.add(cur.field)
                is ArrayAlias -> accessors.add(AliasAccessor.Array)
            }
            cur = cur.instance
        }

        accessors.reverse()
        val optimizedAccessors = accessors.ifEmpty { emptyList() }

        return cur to optimizedAccessors
    }

    private fun convertBaseAccessor(cur: AAInfo, accessors: List<AliasAccessor>): AliasInfo? {
        val base = when (cur) {
            is LocalAlias.SimpleLoc -> when (val loc = cur.loc) {
                is Local -> AccessPathBase.LocalVar(loc.idx)
                is RefValue.Arg -> AccessPathBase.Argument(loc.idx)
                is RefValue.This -> AccessPathBase.This
                is RefValue.Static -> AccessPathBase.ClassStatic(loc.type)
            }

            is LocalAlias.Alloc -> {
                val assign = cur.stmt as? Stmt.Assign ?: return null

                val const = assign.expr as? SimpleValue.RefConst
                val stringConst = const?.expr as? JIRStringConstant
                if (stringConst == null) {
                    if (accessors.isNotEmpty()) return null
                    return AliasAllocInfo(assign.originalIdx)
                }

                AccessPathBase.Constant("java.lang.String", stringConst.value)
            }

            is CallReturn,
            is Unknown -> return null

            is HeapAlias -> error("unreachable")
        }

        return AliasApInfo(base, accessors)
    }
}
