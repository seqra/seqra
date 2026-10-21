package org.opentaint.dataflow.python.alias

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.analysis.alias.AAHeapAccessor
import org.opentaint.dataflow.ap.ifds.analysis.alias.AAInfo
import org.opentaint.dataflow.ap.ifds.analysis.alias.AAInfoManager
import org.opentaint.dataflow.ap.ifds.analysis.alias.AnalysisCancellation
import org.opentaint.dataflow.ap.ifds.analysis.alias.AnalysisResult
import org.opentaint.dataflow.ap.ifds.analysis.alias.ContextInfo
import org.opentaint.dataflow.ap.ifds.analysis.alias.HeapAlias
import org.opentaint.dataflow.ap.ifds.analysis.alias.LocalAliasAnalysis
import org.opentaint.dataflow.ap.ifds.analysis.alias.withAnalysisCancellation
import org.opentaint.dataflow.python.PIRCallResolver
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.python.PIRInstruction
import org.opentaint.util.analysis.ApplicationGraph
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class PIRLocalAliasAnalysis(
    private val entryPoint: PIRInstruction,
    private val graph: ApplicationGraph<CommonMethod, CommonInst>,
    private val callResolver: PIRCallResolver,
    private val languageManager: LanguageManager,
    private val cancellation: Cancellation?,
    private val params: Params,
) : LocalAliasAnalysis<AliasApInfo, AliasAccessor>() {
    data class Params(
        val useAliasAnalysis: Boolean = true,
        val aliasAnalysisInterProcCallDepth: Int = 1,
        val aliasAnalysisTimeLimit: Duration = 10.seconds,
    )

    companion object {
        private const val HEAP_CHAIN_LIMIT = 5
    }

    override fun compute(): AnalysisResult = computeAliases()

    override fun getInstIndex(statement: CommonInst): Int =
        languageManager.getInstIndex(statement)

    override fun localInfo(localIdx: Int): AAInfo =
        LocalAlias.SimpleLoc(RefValue.Local(localIdx, ContextInfo.rootContext))

    override fun convertAliasAccessor(aa: AliasAccessor): List<AAHeapAccessor> = when (aa) {
        is AliasAccessor.Array -> listOf(ArrayAlias)
        is AliasAccessor.Field -> listOf(FieldAlias(aa))
    }

    override fun convert(
        info: AAInfo,
        depth: Int,
        convertInstance: (Int) -> List<AliasApInfo>,
    ): List<AliasApInfo> {
        if (info !is HeapAlias) return listOfNotNull(convertBase(info))
        if (depth > HEAP_CHAIN_LIMIT) return emptyList()

        val instances = convertInstance(info.instance)
        val accessor = when (val a = info.heapAccessor) {
            is ArrayAlias -> AliasAccessor.Array
            is FieldAlias -> a.field
            else -> error("Impossible heap accessor")
        }
        return instances.map { AliasApInfo(it.base, it.accessors + accessor) }
    }

    private fun computeAliases(): AnalysisResult =
        withAnalysisCancellation(
            timeLimit = params.aliasAnalysisTimeLimit,
            parentCancellation = cancellation,
            body = { computeAliases(it) },
            onAnalysisCancelled = { AnalysisResult(AAInfoManager(), emptyArray(), emptyArray()) },
        )

    private fun computeAliases(cancellation: AnalysisCancellation): AnalysisResult {
        val pig = buildPirInstGraph(languageManager, graph, entryPoint.location.method, entryPoint)
        val aliasCallResolver = AliasCallResolver(callResolver, graph, languageManager, params)
        return PIRDSUAliasAnalysis(aliasCallResolver, cancellation).analyze(pig)
    }

    private fun convertBase(info: AAInfo): AliasApInfo? {
        if (info.ctx != ContextInfo.rootContext) return null
        return when (info) {
            is LocalAlias.SimpleLoc -> when (val loc = info.loc) {
                is RefValue.Local -> AliasApInfo(AccessPathBase.LocalVar(loc.idx), emptyList())
                is RefValue.Arg -> AliasApInfo(AccessPathBase.Argument(loc.idx), emptyList())
            }

            is LocalAlias.Alloc,
            is CallReturn,
            is Unknown -> null

            is HeapAlias -> error("unreachable")
            else -> error("Impossible aa-info")
        }
    }
}

sealed interface AliasAccessor {
    data class Field(val name: String) : AliasAccessor
    data object Array : AliasAccessor
}

data class AliasApInfo(
    override val base: AccessPathBase,
    override val accessors: List<AliasAccessor>,
) : LocalAliasAnalysis.CommonAliasApInfo<AliasAccessor>
