package org.opentaint.dataflow.jvm

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAccessor.Field
import org.opentaint.dataflow.jvm.ap.ifds.alias.AAInfo
import org.opentaint.dataflow.jvm.ap.ifds.alias.AAInfoManager
import org.opentaint.dataflow.jvm.ap.ifds.alias.ArrayAlias
import org.opentaint.dataflow.jvm.ap.ifds.alias.ContextInfo
import org.opentaint.dataflow.jvm.ap.ifds.alias.DSUAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.alias.FieldAlias
import org.opentaint.dataflow.jvm.ap.ifds.alias.HeapAlias
import org.opentaint.dataflow.jvm.ap.ifds.alias.LocalAlias
import org.opentaint.dataflow.jvm.ap.ifds.alias.LocalAlias.SimpleLoc
import org.opentaint.dataflow.jvm.ap.ifds.alias.MergeType
import org.opentaint.dataflow.jvm.ap.ifds.alias.RefValue
import org.opentaint.dataflow.jvm.ap.ifds.alias.State
import org.opentaint.dataflow.jvm.ap.ifds.alias.Stmt
import org.opentaint.dataflow.jvm.ap.ifds.alias.Unknown
import java.util.IdentityHashMap
import kotlin.collections.forEach

abstract class DSUAliasAnalysisTestUtils(protected val mergeType: MergeType) {

    protected val manager = AAInfoManager()
    protected val strategy = DSUAliasAnalysis.DsuMergeStrategy(manager)

    protected class StateBuilder(
        private val manager: AAInfoManager,
        private val strategy: DSUAliasAnalysis.DsuMergeStrategy,
        private val mergeType: MergeType,
    ) {
        private var state = State.empty(manager, strategy)

        private val created = IdentityHashMap<AAInfo, Unit>()

        fun local(idx: Int): LocalAlias = create(
            SimpleLoc(RefValue.Local(idx, ContextInfo.rootContext))
        )

        fun unknown(originalIdx: Int): Unknown = create(
            Unknown(Stmt.Return(value = null, originalIdx = originalIdx), ContextInfo.rootContext)
        )

        fun arrayAlias(instanceInfo: AAInfo) = heapAlias(instanceInfo) { i -> HeapAlias(i, ArrayAlias) }

        fun fieldAlias(instanceInfo: AAInfo, fieldName: String) = heapAlias(instanceInfo) { i ->
            HeapAlias(i, FieldAlias(Field("Cls", fieldName, "I"), isImmutable = true))
        }

        private fun heapAlias(instance: AAInfo, body: (Int) -> HeapAlias): HeapAlias {
            val instanceId = infoId(instance)
            val instanceGroupId = state.aliasGroupId(instanceId)

            return create(body(instanceGroupId))
        }

        private fun <T : AAInfo> create(info: T): T {
            created[info] = Unit
            return info
        }

        fun merge(set: Set<AAInfo>) {
            val setIds = infoIds(set)
            state = state.mergeAliasSets(setIds)
        }

        fun remove(set: Set<AAInfo>) {
            val setIds = infoIds(set)
            state = state.removeUnsafe(setIds)
        }

        private fun infoId(info: AAInfo): Int {
            check(created.containsKey(info)) { "$info doesn't belongs to the current state" }
            return manager.getOrAdd(info)
        }

        private fun infoIds(set: Set<AAInfo>): IntOpenHashSet {
            val setIds = IntOpenHashSet()
            set.forEach { setIds.add(infoId(it)) }
            return setIds
        }

        fun build(): State = state

        fun mergeStates(vararg builders: StateBuilder) {
            val states = builders.map { it.state }
            this.state = State.merge(manager, strategy, states, mergeType)

            builders.forEach {
                created.putAll(it.created)
            }
        }
    }

    protected inline fun buildState(body: StateBuilder.() -> Unit): State =
        fillState(body).build()

    protected inline fun fillState(body: StateBuilder.() -> Unit): StateBuilder {
        val builder = StateBuilder(manager, strategy, mergeType)
        builder.body()
        return builder
    }
}
