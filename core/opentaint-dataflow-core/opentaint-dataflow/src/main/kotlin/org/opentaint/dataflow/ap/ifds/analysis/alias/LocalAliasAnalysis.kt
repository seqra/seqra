package org.opentaint.dataflow.ap.ifds.analysis.alias

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.util.getOrCreate
import org.opentaint.ir.api.common.cfg.CommonInst

abstract class LocalAliasAnalysis<AliasInfo, AliasAccessor> {
    interface CommonAliasApInfo<AliasAccessor> {
        val base: AccessPathBase
        val accessors: List<AliasAccessor>
    }

    abstract fun compute(): AnalysisResult?

    val aliasInfo: AnalysisResult? by lazy { compute() }

    val convertedAliases = Long2ObjectOpenHashMap<Pair<Int, List<Pair<IntArrayList, AliasInfo>>>>()

    fun findAlias(base: AccessPathBase.LocalVar, statement: CommonInst): List<AliasInfo>? =
        withStateBeforeStatement(statement) { state, stateId -> state.findLocalAlias(stateId, base.idx) }

    fun findAliasAfterStatement(base: AccessPathBase.LocalVar, statement: CommonInst): List<AliasInfo>? =
        withStateAfterStatement(statement) { state, stateId -> state.findLocalAlias(stateId, base.idx) }

    fun getAllAliasAtStatement(statement: CommonInst): List<List<AliasInfo>> =
        withStateBeforeStatement(statement) { state, stateId ->
            state.convertAllAliasSets(stateId)
        }.orEmpty()

    fun <F> forEachHeapAlias(
        base: AccessPathBase.LocalVar,
        statement: CommonInst,
        fact: F,
        next: (F) -> List<Pair<AliasAccessor, F>>,
        handle: (AliasInfo, F) -> Unit,
    ) {
        withStateBeforeStatement(statement) { state, stateId ->
            state.findHeapAlias(stateId, base.idx, fact, next, handle)
        }
    }

    private inline fun <T> withStateBeforeStatement(statement: CommonInst, body: (State, Int) -> T): T? =
        withStatementState(statement, stateIdOffset = 0, { statesBeforeStmt }, body)

    private inline fun <T> withStateAfterStatement(statement: CommonInst, body: (State, Int) -> T): T? =
        withStatementState(statement, stateIdOffset = 1, { statesAfterStmt }, body)

    abstract fun getInstIndex(statement: CommonInst): Int

    private inline fun <T> withStatementState(
        statement: CommonInst,
        stateIdOffset: Int,
        states: AnalysisResult.() -> Array<ImmutableState?>,
        body: (State, Int) -> T,
    ): T? {
        val res = aliasInfo ?: return null

        val idx = getInstIndex(statement)
        val state = res.states().getOrNull(idx)
            ?: return null

        val stateId = idx * 2 + stateIdOffset
        return body(state.unsafeState(), stateId)
    }

    abstract fun localInfo(localIdx: Int): AAInfo

    private fun <F> State.findHeapAlias(
        stateId: Int,
        localIdx: Int,
        fact: F,
        next: (F) -> List<Pair<AliasAccessor, F>>,
        handle: (AliasInfo, F) -> Unit,
    ) {
        val localInfo = localInfo(localIdx)
        val localInfoIdx = manager.find(localInfo) ?: return

        findHeapAlias(stateId, hashSetOf(), localInfoIdx, fact, next, handle)
    }

    abstract fun convertAliasAccessor(aa: AliasAccessor): List<AAHeapAccessor>

    private fun <F> State.findHeapAlias(
        stateId: Int,
        visited: MutableSet<F>,
        factInfo: Int,
        fact: F,
        next: (F) -> List<Pair<AliasAccessor, F>>,
        handle: (AliasInfo, F) -> Unit,
    ) {
        if (!visited.add(fact)) return

        convertAllAliases(stateId, factInfo).forEach { handle(it, fact) }

        val instance = aliasGroupId(factInfo)
        for ((accessor, nextFact) in next(fact)) {
            val heapAccessors = convertAliasAccessor(accessor)

            for (ha in heapAccessors) {
                val info = HeapAlias(instance, ha)
                val heapIdx = manager.find(info) ?: continue
                findHeapAlias(stateId, visited, heapIdx, nextFact, next, handle)
            }
        }

        visited.remove(fact)
    }

    private fun State.findLocalAlias(stateId: Int, localIdx: Int): List<AliasInfo>? {
        val localInfo = localInfo(localIdx)
        val localInfoIdx = manager.find(localInfo) ?: return null
        return convertAllAliases(stateId, localInfoIdx)
    }

    private fun State.convertAllAliases(stateId: Int, infoIdx: Int): List<AliasInfo> {
        val result = mutableListOf<Pair<IntArrayList, AliasInfo>>()
        forEachAliasInSet(infoIdx) { aliasIdx ->
            if (aliasIdx != infoIdx) {
                result += convert(stateId, aliasIdx, depth = 0)
            }
        }
        return result.map { it.second }
    }

    private fun State.convertAllAliasSets(stateId: Int): List<List<AliasInfo>> =
        allAliasSets().map { aliasSet ->
            val result = mutableListOf<Pair<IntArrayList, AliasInfo>>()
            aliasSet.forEach {
                result += convert(stateId, it, depth = 0)
            }
            result.map { it.second }
        }

    abstract fun convert(
        info: AAInfo, depth: Int,
        convertInstance: (Int) -> List<Pair<IntArrayList, AliasInfo>>,
    ): List<Pair<IntArrayList, AliasInfo>>

    private fun State.convert(stateId: Int, infoIdx: Int, depth: Int): List<Pair<IntArrayList, AliasInfo>> =
        synchronized(convertedAliases) {
            val cacheId = pair(infoIdx, stateId)
            var cacheResult = convertedAliases.getOrCreate(cacheId) {
                depth to convert(stateId, manager.getElementUncheck(infoIdx), depth)
            }
            if (depth < cacheResult.first) {
                cacheResult = depth to convert(stateId, manager.getElementUncheck(infoIdx), depth)
                convertedAliases.put(cacheId, cacheResult)
            }
            cacheResult
        }.second

    private fun State.convert(stateId: Int, info: AAInfo, depth: Int): List<Pair<IntArrayList, AliasInfo>> =
        convert(info, depth) { instance ->
            val instances = mutableListOf<Pair<IntArrayList, AliasInfo>>()
            forEachAliasInSet(instance) {
                instances += convert(stateId, it, depth + 1)
            }
            instances
        }

    private fun pair(a: Int, b: Int): Long =
        (a.toLong() shl 32) or (b.toLong() and 0xFFFF_FFFFL)
}
