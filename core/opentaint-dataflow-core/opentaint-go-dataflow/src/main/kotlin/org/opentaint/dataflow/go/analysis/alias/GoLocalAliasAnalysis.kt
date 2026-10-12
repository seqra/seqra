package org.opentaint.dataflow.go.analysis.alias

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import mu.KLogging
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.analysis.alias.AAHeapAccessor
import org.opentaint.dataflow.ap.ifds.analysis.alias.AAInfo
import org.opentaint.dataflow.ap.ifds.analysis.alias.AAInfoManager
import org.opentaint.dataflow.ap.ifds.analysis.alias.AnalysisCancellation
import org.opentaint.dataflow.ap.ifds.analysis.alias.AnalysisResult
import org.opentaint.dataflow.ap.ifds.analysis.alias.ContextInfo
import org.opentaint.dataflow.ap.ifds.analysis.alias.HeapAlias
import org.opentaint.dataflow.ap.ifds.analysis.alias.ImmutableState
import org.opentaint.dataflow.ap.ifds.analysis.alias.LocalAliasAnalysis
import org.opentaint.dataflow.ap.ifds.analysis.alias.State
import org.opentaint.dataflow.ap.ifds.analysis.alias.allElements
import org.opentaint.dataflow.ap.ifds.analysis.alias.withAnalysisCancellation
import org.opentaint.dataflow.go.GoFlowFunctionUtils
import org.opentaint.dataflow.go.analysis.alias.GoDSUAliasAnalysis.ConnectedAliases
import org.opentaint.dataflow.util.forEachInt
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.inst.GoIRInst
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed interface GoAliasInfo
data class AliasApInfo(val base: AccessPathBase, val accessors: List<GoAliasAccessor>) : GoAliasInfo
data class AliasAllocInfo(val allocInst: Int) : GoAliasInfo

sealed interface GoAliasInfoNoRef

data class AliasApInfoNoRef(
    override val base: AccessPathBase,
    override val accessors: List<GoAliasAccessor.NoRef>
) : GoAliasInfoNoRef,
    LocalAliasAnalysis.CommonAliasApInfo<GoAliasAccessor.NoRef>

data class AliasAllocInfoNoRef(val allocInst: Int) : GoAliasInfoNoRef

class GoLocalAliasAnalysis(
    private val function: GoIRFunction,
    private val params: Params = Params(),
): LocalAliasAnalysis<GoAliasInfoNoRef, GoAliasAccessor.NoRef>() {
    data class Params(
        val interProcCallDepth: Int = 0,
        val aliasAnalysisTimeLimit: Duration = 10.seconds,
    )

    override fun getInstIndex(statement: CommonInst): Int =
        (statement as GoIRInst).location.index

    override fun localInfo(localIdx: Int): AAInfo =
        GoLocalAlias.SimpleLoc(GoRefValue.Local(localIdx, ContextInfo.rootContext))

    override fun compute(): AnalysisResult? = computeAliases()

    override fun convert(
        info: AAInfo,
        depth: Int,
        convertInstance: (Int) -> List<Pair<IntArrayList, GoAliasInfoNoRef>>,
    ): List<Pair<IntArrayList, GoAliasInfoNoRef>> = convertInfo(info, depth, convertInstance)

    override fun convertAliasAccessor(aa: GoAliasAccessor.NoRef): List<AAHeapAccessor> = when (aa) {
        is GoAliasAccessor.Array -> listOf(GoArrayAlias)
        is GoAliasAccessor.Field -> listOf(GoFieldAlias(aa))
        is GoAliasAccessor.Global -> emptyList() // todo: globals
    }

    companion object {
        private const val HEAP_CHAIN_LIMIT = 5
        private val logger = object : KLogging() {}.logger
    }

    private fun computeAliases(): AnalysisResult? = withAnalysisCancellation(
        params.aliasAnalysisTimeLimit, parentCancellation = null,
        body = { cancellation ->
            computeAliases(cancellation)
        },
        onAnalysisCancelled = {
            logger.error {
                "Alias analysis for $function exceed ${params.aliasAnalysisTimeLimit}"
            }
            null
        }
    )

    private fun computeAliases(cancellation: AnalysisCancellation): AnalysisResult {
        val analyzer = GoDSUAliasAnalysis(function, params.interProcCallDepth, cancellation)
        val daa = analyzer.analyze()
        return daa.collapseRefs()
    }

    private fun AnalysisResult.collapseRefs(): AnalysisResult =
        AnalysisResult(
            manager,
            Array(statesBeforeStmt.size) { statesBeforeStmt[it]?.collapseRefs(manager) },
            Array(statesAfterStmt.size) { statesAfterStmt[it]?.collapseRefs(manager) }
        )

    private fun ImmutableState.collapseRefs(manager: AAInfoManager): ImmutableState {
        var state = this.mutableCopy()
        state.allElements().forEachInt { element ->
            val elementRef = HeapAlias(state.aliasGroupId(element), GoRefAlias)
            val elementRefId = manager.getOrAdd(elementRef)
            state = state.mergeWith(element, elementRefId)
        }
        return state
    }

    private fun State.mergeWith(info: Int, other: Int): State =
        mergeAliasSets(IntOpenHashSet(intArrayOf(info, other)))

    fun computeAliasWithRef(base: AccessPathBase, inst: GoIRInst): List<AliasApInfo> {
        val self = listOf(AliasApInfo(base, emptyList()))
        val idx = inst.location.index
        val analysisResult = aliasAnalysisWithRef().oldAnalysisResult()
        val connected = analysisResult.statesBeforeStmt.getOrNull(idx) ?: return self
        for (entry in connected.aliasGroups.values) {
            val converted = entry.flatMap { convert(it, connected.aliasGroups, 0) }.distinct()
            if (converted.any { it is AliasApInfo && it.accessors.isEmpty() && it.base == base }) {
                return converted.filterIsInstance<AliasApInfo>()
            }
        }
        return self
    }

    private fun aliasAnalysisWithRef(): AnalysisResult {
        val noCancellation = AnalysisCancellation(Duration.INFINITE, parentCancellation = null)
        return GoDSUAliasAnalysis(function, params.interProcCallDepth, noCancellation).analyze()
    }

    private fun convert(info: AAInfo, groups: Int2ObjectOpenHashMap<List<AAInfo>>, depth: Int): List<GoAliasInfo> {
        if (info !is HeapAlias) {
            val base = convertBase(info)
            val baseWithRef = when (base) {
                is AliasAllocInfoNoRef -> AliasAllocInfo(base.allocInst)
                is AliasApInfoNoRef -> AliasApInfo(base.base, base.accessors)
                null -> null
            }
            return listOfNotNull(baseWithRef)
        }
        if (depth > HEAP_CHAIN_LIMIT) return emptyList()
        val instanceGroup = groups[info.instance] ?: return emptyList()
        val instances = instanceGroup.flatMap { convert(it, groups, depth + 1) }
        val accessor = when (val a = info.heapAccessor) {
            is GoArrayAlias -> GoAliasAccessor.Array
            is GoRefAlias -> GoAliasAccessor.Ref
            is GoFieldAlias -> a.field
            else -> error("Unexpected heap accessor")
        }
        return instances.mapNotNull {
            when (it) {
                is AliasAllocInfo -> null
                is AliasApInfo -> AliasApInfo(it.base, it.accessors + accessor)
            }
        }
    }

    private fun GoAliasInfoNoRef.willDepthExceedLimit(depth: Int) =
        this !is AliasApInfoNoRef || accessors.size + depth >= HEAP_CHAIN_LIMIT

    private fun IntArrayList.hasTwoOfInstance(instance: Int) =
        count { it == instance } >= 2

    private fun GoAliasInfoNoRef.isHeapAllowedBase() =
        this is AliasApInfoNoRef && base !is AccessPathBase.Constant

    private fun convertInfo(
        info: AAInfo,
        depth: Int,
        convertInstance: (Int) -> List<Pair<IntArrayList, GoAliasInfoNoRef>>
    ): List<Pair<IntArrayList, GoAliasInfoNoRef>> {
        if (info !is HeapAlias) {
            val base = convertBase(info) ?: return emptyList()
            return listOf(IntArrayList(0) to base)
        }

        if (info.heapAccessor is GoRefAlias) {
            return emptyList()
        }

        if (depth > HEAP_CHAIN_LIMIT) {
            return emptyList()
        }

        val instances = convertInstance(info.instance)
            .filterNot { it.second.willDepthExceedLimit(depth) || it.first.hasTwoOfInstance(info.instance) }
            .filter { it.second.isHeapAllowedBase() }

        val accessor = when (val a = info.heapAccessor) {
            is GoArrayAlias -> GoAliasAccessor.Array
            is GoFieldAlias -> a.field
            else -> error("Impossible")
        }

        return instances.map { (usedInstances, aliasInfo) ->
            check(aliasInfo is AliasApInfoNoRef)
            val newUsedInstances = usedInstances.clone()
            newUsedInstances.add(info.instance)
            val newAliasInfo = AliasApInfoNoRef(aliasInfo.base, aliasInfo.accessors + accessor)
            newUsedInstances to newAliasInfo
        }
    }

    private fun convertBase(info: AAInfo): GoAliasInfoNoRef? {
        if (info.ctx != ContextInfo.rootContext) return null
        return when (info) {
            is GoLocalAlias.SimpleLoc -> when (val loc = info.loc) {
                is GoRefValue.Local -> AliasApInfoNoRef(AccessPathBase.LocalVar(loc.idx), emptyList())
                is GoRefValue.Arg -> AliasApInfoNoRef(
                    GoFlowFunctionUtils.parameterAccessPathBase(loc.idx, function),
                    emptyList()
                )
                is GoRefValue.Global -> AliasApInfoNoRef(AccessPathBase.ClassStatic, listOf(GoAliasAccessor.Global(loc.name)))
                is GoRefValue.FreeVarBase -> AliasApInfoNoRef(AccessPathBase.This, emptyList())
            }
            is GoLocalAlias.Alloc -> AliasAllocInfoNoRef(info.inst)
            is GoReturnValue -> null
            is GoUnknown -> null
            is HeapAlias -> error("unreachable")
            else -> null
        }
    }

    private fun AAInfo.convertToAliasInfo(
        aliasGroups: Int2ObjectOpenHashMap<List<AAInfo>>,
        depth: Int,
        cancellation: AnalysisCancellation,
    ): List<GoAliasInfoNoRef> {
        if (this !is HeapAlias) {
            val base = convertBase(this)
            return listOfNotNull(base)
        }

        if (this.heapAccessor is GoRefAlias) {
            return emptyList()
        }

        if (depth > HEAP_CHAIN_LIMIT) {
            return emptyList()
        }

        cancellation.checkpoint()

        val instanceGroup = aliasGroups[instance] ?: return emptyList()
        val instances = instanceGroup.flatMap { it.convertToAliasInfo(aliasGroups, depth + 1, cancellation) }
        val accessor = when (val a = this.heapAccessor) {
            is GoArrayAlias -> GoAliasAccessor.Array
            is GoFieldAlias -> a.field
            else -> error("Impossible")
        }

        return instances.mapNotNull {
            when (it) {
                is AliasAllocInfoNoRef -> return@mapNotNull null
                is AliasApInfoNoRef -> AliasApInfoNoRef(it.base, it.accessors + accessor)
            }
        }
    }

    data class OldAnalysisResult(
        val statesBeforeStmt: List<ConnectedAliases>,
        val statesAfterStmt: List<ConnectedAliases>,
    )

    private fun AnalysisResult.oldAnalysisResult() = OldAnalysisResult(
        getConnectedAliases(statesBeforeStmt),
        getConnectedAliases(statesAfterStmt)
    )

    private fun AnalysisResult.getConnectedAliases(
        states: Array<ImmutableState?>
    ): List<ConnectedAliases> = List(states.size) { idx ->
        val state = states[idx]?.mutableCopy()
            ?: return@List ConnectedAliases(Int2ObjectOpenHashMap())

        val groupsElements = Int2ObjectOpenHashMap<IntOpenHashSet>()
        state.allElements().forEachInt { element ->
            val groupId = state.aliasGroupId(element)
            val group = groupsElements.get(groupId) ?: IntOpenHashSet().also { groupsElements.put(groupId, it) }
            group.add(element)
        }
        val groups = Int2ObjectOpenHashMap<List<AAInfo>>()
        val keys = groupsElements.keys.toIntArray()
        for (key in keys) {
            val els = groupsElements.get(key)
            val list = mutableListOf<AAInfo>()
            els.forEachInt { list += manager.getElementUncheck(it) }
            groups.put(key, list)
        }
        ConnectedAliases(groups)
    }
}
