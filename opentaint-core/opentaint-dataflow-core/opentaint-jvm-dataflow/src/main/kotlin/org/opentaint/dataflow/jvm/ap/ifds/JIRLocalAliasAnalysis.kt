package org.opentaint.dataflow.jvm.ap.ifds

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.alias.JIRIntraProcAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodCallResolver
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.jvm.graph.JApplicationGraph

class JIRLocalAliasAnalysis(
    private val entryPoint: JIRInst,
    private val graph: JApplicationGraph,
    private val callResolver: JIRMethodCallResolver,
    private val languageManager: JIRLanguageManager,
    private val params: Params,
) {
    data class Params(
        val useAliasAnalysis: Boolean = true,
        val aliasAnalysisInterProcCallDepth: Int = 0,
    )

    private val aliasInfo by lazy { compute() }

    class MethodAliasInfo(
        val aliasBeforeStatement: Array<Int2ObjectOpenHashMap<List<AliasInfo>>>,
        val aliasAfterStatement: Array<Int2ObjectOpenHashMap<List<AliasInfo>>>,
    )

    private fun getLocalVarAliases(
        alias: Array<Int2ObjectOpenHashMap<List<AliasInfo>>>,
        instIdx: Int, base: AccessPathBase.LocalVar
    ): List<AliasInfo>? =
        alias[instIdx].getOrDefault(base.idx, null)?.filter {
            it !is AliasApInfo || it.accessors.isNotEmpty() || it.base != base
        }

    fun findAlias(base: AccessPathBase.LocalVar, statement: CommonInst): List<AliasInfo>? {
        val idx = languageManager.getInstIndex(statement)
        return getLocalVarAliases(aliasInfo.aliasBeforeStatement, idx, base)
    }

    fun getAllAliasAtStatement(statement: CommonInst): Int2ObjectOpenHashMap<List<AliasInfo>> {
        val idx = languageManager.getInstIndex(statement)
        return aliasInfo.aliasBeforeStatement[idx]
    }

    fun findAliasAfterStatement(base: AccessPathBase.LocalVar, statement: CommonInst): List<AliasInfo>? {
        val idx = languageManager.getInstIndex(statement)
        return getLocalVarAliases(aliasInfo.aliasAfterStatement, idx, base)
    }

    private fun compute(): MethodAliasInfo {
        return JIRIntraProcAliasAnalysis(entryPoint, graph, callResolver, languageManager, params).compute()
    }

    sealed interface AliasAccessor {
        data class Field(val className: String, val fieldName: String, val fieldType: String) : AliasAccessor
        data object Array : AliasAccessor
    }

    sealed interface AliasInfo
    data class AliasApInfo(val base: AccessPathBase, val accessors: List<AliasAccessor>): AliasInfo
    data class AliasAllocInfo(val allocInst: Int): AliasInfo
}
