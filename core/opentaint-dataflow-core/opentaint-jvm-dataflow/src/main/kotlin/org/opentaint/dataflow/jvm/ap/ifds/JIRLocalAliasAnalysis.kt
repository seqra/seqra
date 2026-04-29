package org.opentaint.dataflow.jvm.ap.ifds

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.alias.JIRIntraProcAliasAnalysis
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.jvm.graph.JApplicationGraph
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class JIRLocalAliasAnalysis(
    private val entryPoint: JIRInst,
    private val graph: JApplicationGraph,
    private val callResolver: JIRCallResolver,
    private val localVariableReachability: JIRLocalVariableReachability,
    private val cancellation: Cancellation,
    private val languageManager: JIRLanguageManager,
    private val params: Params,
) {
    data class Params(
        val useAliasAnalysis: Boolean = true,
        val aliasAnalysisInterProcCallDepth: Int = 0,
        val aliasAnalysisTimeLimit: Duration = 10.seconds,
    )

    private val aliasInfo by lazy { compute() }

    class MethodAliasInfo(
        val aliasBeforeStatement: Array<Int2ObjectOpenHashMap<Array<Any>>?>?,
        val aliasAfterStatement: Array<Int2ObjectOpenHashMap<Array<Any>>?>?,
    )

    private fun getLocalVarAliases(
        alias: Array<Int2ObjectOpenHashMap<Array<Any>>?>,
        instIdx: Int, base: AccessPathBase.LocalVar
    ): List<AliasInfo>? =
        alias[instIdx]?.getOrDefault(base.idx, null)?.filter {
            it !is AliasApInfo || it.accessors.isNotEmpty() || it.base != base
        }?.map { it.wrapAliasInfo() }

    fun findAlias(base: AccessPathBase.LocalVar, statement: CommonInst): List<AliasInfo>? {
        val aliasBefore = aliasInfo.aliasBeforeStatement ?: return null
        val idx = languageManager.getInstIndex(statement)
        return getLocalVarAliases(aliasBefore, idx, base)
    }

    fun getAllAliasAtStatement(statement: CommonInst): Int2ObjectOpenHashMap<List<AliasInfo>> {
        val aliasBefore = aliasInfo.aliasBeforeStatement ?: return Int2ObjectOpenHashMap()
        val idx = languageManager.getInstIndex(statement)
        return aliasBefore[idx]?.let { wrapAllInfo(it) } ?: Int2ObjectOpenHashMap()
    }

    fun findAliasAfterStatement(base: AccessPathBase.LocalVar, statement: CommonInst): List<AliasInfo>? {
        val aliasAfter = aliasInfo.aliasAfterStatement ?: return null
        val idx = languageManager.getInstIndex(statement)
        return getLocalVarAliases(aliasAfter, idx, base)
    }

    private fun compute(): MethodAliasInfo {
        val analysis = JIRIntraProcAliasAnalysis(entryPoint, graph, callResolver, languageManager, cancellation, params)
        return analysis.compute(localVariableReachability)
    }

    sealed interface AliasAccessor {
        data class Field(val className: String, val fieldName: String, val fieldType: String) : AliasAccessor
        data object Array : AliasAccessor
        data class Static(val typeName: String) : AliasAccessor
    }

    sealed interface AliasInfo
    data class AliasApInfo(val base: AccessPathBase, val accessors: List<AliasAccessor>): AliasInfo
    data class AliasAllocInfo(val allocInst: Int): AliasInfo

    companion object {
        fun AliasInfo.unwrap(): Any = when (this) {
            is AliasAllocInfo -> allocInst
            is AliasApInfo -> if (accessors.isEmpty()) base else this
        }

        fun Any.wrapAliasInfo(): AliasInfo = when (this) {
            is AccessPathBase -> AliasApInfo(this, emptyList())
            is AliasInfo -> this
            is Int -> AliasAllocInfo(this)
            else -> error("Impossible")
        }

        fun wrapAllInfo(info: Int2ObjectOpenHashMap<Array<Any>>): Int2ObjectOpenHashMap<List<AliasInfo>> {
            val result = Int2ObjectOpenHashMap<List<AliasInfo>>()
            for ((key, aliases) in info) {
                result.put(key, List(aliases.size) { aliases[it].wrapAliasInfo() })
            }
            return result
        }

        fun unwrapAllInfo(info: Int2ObjectOpenHashMap<List<AliasInfo>>): Int2ObjectOpenHashMap<Array<Any>> {
            val result = Int2ObjectOpenHashMap<Array<Any>>(info.size, 0.99f)
            val iter = info.int2ObjectEntrySet().fastIterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                val value = entry.value
                val unwrapped = Array(value.size) { value[it].unwrap() }
                result.put(entry.intKey, unwrapped)
            }
            return result
        }
    }
}
