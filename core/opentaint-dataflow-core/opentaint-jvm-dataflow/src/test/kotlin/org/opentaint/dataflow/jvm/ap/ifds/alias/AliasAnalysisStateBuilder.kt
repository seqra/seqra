package org.opentaint.dataflow.jvm.ap.ifds.alias

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAccessor.Field
import org.opentaint.dataflow.jvm.ap.ifds.alias.DSUAliasAnalysis.State
import org.opentaint.dataflow.jvm.ap.ifds.alias.LocalAlias.SimpleLoc
import java.util.IdentityHashMap

internal class StateBuilder(
    private val manager: AAInfoManager,
    private val strategy: DSUAliasAnalysis.DsuMergeStrategy
) {
    private var state = State.empty(manager, strategy)

    internal val created = IdentityHashMap<AAInfo, Unit>()

    fun local(idx: Int): LocalAlias = create(
        SimpleLoc(RefValue.Local(idx, ContextInfo.rootContext))
    )

    fun outerThis(): LocalAlias = create(
        SimpleLoc(RefValue.This(isOuter = true))
    )

    fun arg(idx: Int): LocalAlias = create(
        SimpleLoc(RefValue.Arg(idx))
    )

    fun unknown(originalIdx: Int): Unknown = create(
        Unknown(Stmt.Return(value = null, originalIdx = originalIdx), ContextInfo.rootContext)
    )

    fun arrayAlias(instanceInfo: AAInfo): HeapAlias =
        heapAlias(instanceInfo) { i -> HeapAlias(i, ArrayAlias) }

    fun fieldAlias(instanceInfo: AAInfo, fieldName: String, isImmutable: Boolean = true): HeapAlias =
        heapAlias(instanceInfo) { i ->
            HeapAlias(i, FieldAlias(Field("Cls", fieldName, "I"), isImmutable = isImmutable))
        }

    private fun heapAlias(instance: AAInfo, body: (Int) -> HeapAlias): HeapAlias {
        val instanceId = checkedInfoId(instance)
        val instanceGroupId = state.aliasGroupId(instanceId)

        return create(body(instanceGroupId))
    }

    private fun <T : AAInfo> create(info: T): T {
        created[info] = Unit
        return info
    }

    fun merge(set: Set<AAInfo>) {
        val setIds = checkedInfoIds(set)
        state = state.mergeAliasSets(setIds)
    }

    fun remove(set: Set<AAInfo>) {
        val setIds = checkedInfoIds(set)
        state = state.removeUnsafe(setIds)
    }

    fun mergeStates(vararg builders: StateBuilder) {
        val states = builders.map { it.state }
        this.state = State.merge(manager, strategy, states)

        builders.forEach {
            created.putAll(it.created)
        }
    }

    fun build(): State = state

    internal fun infoId(info: AAInfo): Int = manager.getOrAdd(info)

    internal fun infoIds(set: Set<AAInfo>): IntOpenHashSet {
        val setIds = IntOpenHashSet()
        set.forEach { setIds.add(infoId(it)) }
        return setIds
    }

    private fun checkedInfoId(info: AAInfo): Int {
        check(created.containsKey(info)) { "$info doesn't belongs to the current state" }
        return manager.getOrAdd(info)
    }

    private fun checkedInfoIds(set: Set<AAInfo>): IntOpenHashSet {
        val setIds = IntOpenHashSet()
        set.forEach { setIds.add(checkedInfoId(it)) }
        return setIds
    }
}
