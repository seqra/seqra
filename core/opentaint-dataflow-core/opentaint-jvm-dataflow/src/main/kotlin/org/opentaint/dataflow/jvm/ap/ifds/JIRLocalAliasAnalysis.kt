package org.opentaint.dataflow.jvm.ap.ifds

import it.unimi.dsi.fastutil.ints.IntArrayList
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.analysis.alias.AAHeapAccessor
import org.opentaint.dataflow.ap.ifds.analysis.alias.AAInfo
import org.opentaint.dataflow.ap.ifds.analysis.alias.AnalysisResult
import org.opentaint.dataflow.ap.ifds.analysis.alias.ContextInfo
import org.opentaint.dataflow.ap.ifds.analysis.alias.LocalAliasAnalysis
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.ClassStatic
import org.opentaint.dataflow.configuration.jvm.CopyAllMarks
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.PositionAccessor
import org.opentaint.dataflow.configuration.jvm.PositionWithAccess
import org.opentaint.dataflow.configuration.jvm.Result
import org.opentaint.dataflow.configuration.jvm.This
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAccessor
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasInfo
import org.opentaint.dataflow.jvm.ap.ifds.alias.ArrayAlias
import org.opentaint.dataflow.jvm.ap.ifds.alias.ExternalCallModelProvider
import org.opentaint.dataflow.jvm.ap.ifds.alias.ExternalCallModelProvider.ExternalAssign
import org.opentaint.dataflow.jvm.ap.ifds.alias.ExternalCallModelProvider.ExternalObject
import org.opentaint.dataflow.jvm.ap.ifds.alias.FieldAlias
import org.opentaint.dataflow.jvm.ap.ifds.alias.JIRIntraProcAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.alias.JIRIntraProcAliasAnalysis.Convert.convertToAliasInfo
import org.opentaint.dataflow.jvm.ap.ifds.alias.LocalAlias
import org.opentaint.dataflow.jvm.ap.ifds.alias.RefValue
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.jvm.graph.JApplicationGraph
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class JIRLocalAliasAnalysis(
    private val entryPoint: JIRInst,
    private val graph: JApplicationGraph,
    private val callResolver: JIRCallResolver,
    private val modelProvider: TaintRulesProvider?,
    private val localVariableReachability: JIRLocalVariableReachability,
    private val cancellation: Cancellation,
    private val languageManager: JIRLanguageManager,
    private val params: Params,
) : LocalAliasAnalysis<AliasInfo, AliasAccessor>() {
    data class Params(
        val useAliasAnalysis: Boolean = true,
        val aliasAnalysisInterProcCallDepth: Int = 0,
        val aliasAnalysisTimeLimit: Duration = 10.seconds,
    )

    override fun getInstIndex(statement: CommonInst): Int =
        languageManager.getInstIndex(statement)

    override fun localInfo(localIdx: Int): AAInfo =
        LocalAlias.SimpleLoc(RefValue.Local(localIdx, ContextInfo.rootContext))

    override fun convertAliasAccessor(aa: AliasAccessor): List<AAHeapAccessor> = when (aa) {
        is AliasAccessor.Array -> listOf(ArrayAlias)
        is AliasAccessor.Field -> listOf(FieldAlias(aa, false), FieldAlias(aa, true))
        is AliasAccessor.Static -> emptyList()
    }

    override fun convert(
        info: AAInfo,
        depth: Int,
        convertInstance: (Int) -> List<Pair<IntArrayList, AliasInfo>>,
    ): List<Pair<IntArrayList, AliasInfo>> = info.convertToAliasInfo(depth, null, convertInstance)

    private inner class CallModelProvider : ExternalCallModelProvider {
        override fun provideModel(method: JIRMethod): List<ExternalAssign> {
            val rules = modelProvider?.passTroughRulesForMethod(method, null, null, false)?.toList().orEmpty()
            if (rules.isEmpty()) return emptyList()

            val actions = rules
                .flatMap { it.actionsAfter }
                .filterIsInstance<CopyAllMarks>()
                .distinct()

            val externalAssigns = actions.mapNotNull { action ->
                val from = action.from.toExternalObject() ?: return@mapNotNull null
                val to = action.to.toExternalObject() ?: return@mapNotNull null
                ExternalAssign(from, to)
            }

            return externalAssigns
        }

        private fun Position.toExternalObject(): ExternalObject? {
            val base = when (this) {
                is Argument -> ExternalCallModelProvider.Position.Arg(index)
                is Result -> ExternalCallModelProvider.Position.RetVal
                is This -> ExternalCallModelProvider.Position.This
                is PositionWithAccess -> {
                    val b = base.toExternalObject() ?: return null
                    val a = access.toAaAccessor() ?: return null
                    return ExternalObject(b.pos, b.accessors + a)
                }

                is ClassStatic -> return null
            }

            return ExternalObject(base, emptyList())
        }

        private fun PositionAccessor.toAaAccessor(): AAHeapAccessor? = when (this) {
            is PositionAccessor.AnyFieldAccessor -> null
            is PositionAccessor.ElementAccessor -> ArrayAlias
            is PositionAccessor.FieldAccessor -> FieldAlias(
                AliasAccessor.Field(className, fieldName, fieldType),
                isImmutable = false
            ).takeIf { it.field.fieldName != "<rule-storage>" }
        }
    }

    override fun compute(): AnalysisResult? {
        val analysis = JIRIntraProcAliasAnalysis(
            entryPoint, graph, callResolver,
            CallModelProvider(),
            languageManager, cancellation, params
        )
        return analysis.compute(localVariableReachability)
    }

    sealed interface AliasAccessor {
        data class Field(val className: String, val fieldName: String, val fieldType: String) : AliasAccessor
        data object Array : AliasAccessor
        data class Static(val typeName: String) : AliasAccessor
    }

    sealed interface AliasInfo

    data class AliasApInfo(
        override val base: AccessPathBase,
        override val accessors: List<AliasAccessor>
    ) : AliasInfo,
        CommonAliasApInfo<AliasAccessor>

    data class AliasAllocInfo(val allocInst: Int) : AliasInfo
}
