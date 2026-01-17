package org.opentaint.dataflow.jvm.ap.ifds

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentHashSetOf
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.util.PersistentBitSet
import org.opentaint.dataflow.util.PersistentBitSet.Companion.emptyPersistentBitSet
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.toBitSet
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRTypedField
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRCallInst
import org.opentaint.ir.api.jvm.cfg.JIRCastExpr
import org.opentaint.ir.api.jvm.cfg.JIRCatchInst
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.JIRRef
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.jvm.graph.JApplicationGraph
import java.util.BitSet

class JIRLocalAliasAnalysis(
    private val entryPoint: JIRInst,
    private val graph: JApplicationGraph,
    private val languageManager: JIRLanguageManager
) {
    private val aliasInfo by lazy { compute() }

    class MethodAliasInfo(
        val aliasBeforeStatement: Array<Int2ObjectOpenHashMap<AliasInfo>?>,
        val aliasAfterStatement: Array<Int2ObjectOpenHashMap<AliasInfo>?>,
    )

    fun findAlias(base: AccessPathBase.LocalVar, statement: CommonInst): AliasInfo? {
        val idx = languageManager.getInstIndex(statement)
        return aliasInfo.aliasBeforeStatement[idx]?.get(base.idx)
    }

    fun findAliasAfterStatement(base: AccessPathBase.LocalVar, statement: CommonInst): AliasInfo? {
        val idx = languageManager.getInstIndex(statement)
        return aliasInfo.aliasAfterStatement[idx]?.get(base.idx)
    }

    private fun compute(): MethodAliasInfo {
        val beforeInst = arrayOfNulls<State>(languageManager.getMaxInstIndex(entryPoint.location.method) + 1)
        val afterInst = arrayOfNulls<State>(languageManager.getMaxInstIndex(entryPoint.location.method) + 1)
        val unprocessed = mutableListOf<Pair<JIRInst, State>>()

        unprocessed.add(entryPoint to State.empty())
        while (unprocessed.isNotEmpty()) {
            val (inst, newState) = unprocessed.removeLast()

            val instIdx = languageManager.getInstIndex(inst)
            val currentState = beforeInst[instIdx]
            val state = if (currentState == null) {
                newState
            } else {
                currentState.merge(newState) ?: continue
            }
            beforeInst[instIdx] = state

            val nextState = evalInst(inst, state)

            afterInst[instIdx] = nextState

            graph.successors(inst).forEach { successor ->
                unprocessed.add(successor to nextState)
            }
        }

        val aliasBeforeStatement = Array(beforeInst.size) { beforeInst[it]?.result() }
        val aliasAfterStatement = Array(afterInst.size) { afterInst[it]?.result() }
        return MethodAliasInfo(aliasBeforeStatement, aliasAfterStatement)
    }

    sealed interface AliasAccessor {
        data class Field(
            val className: String,
            val fieldName: String,
            val fieldType: String
        ) : AliasAccessor

        data class Array(
            val index: AccessPathBase
        ) : AliasAccessor
    }

    data class AliasInfo(val base: AccessPathBase, val accessors: List<AliasAccessor>)

    private fun State.result(): Int2ObjectOpenHashMap<AliasInfo> {
        val result = Int2ObjectOpenHashMap<AliasInfo>()
        localVarValue.entries.forEach { (local, alias) ->
            result[local] = alias.aliasInfo()
        }
        return result
    }

    private fun AssignedValue.aliasInfo(): AliasInfo {
        val accessors = mutableListOf<AliasAccessor>()
        var current = this
        while (true) {
            when (current) {
                is AssignedValue.Local.Value -> return AliasInfo(current.value, if (accessors.isEmpty()) emptyList() else accessors)
                is AssignedValue.Local.Var -> return AliasInfo(current.local, if (accessors.isEmpty()) emptyList() else accessors)
                is AssignedValue.HeapLocation.Array -> {
                    accessors.add(AliasAccessor.Array(current.arrayIndex))
                    current = current.base
                }

                is AssignedValue.HeapLocation.Field -> {
                    val field = current.field.field
                    accessors.add(AliasAccessor.Field(field.enclosingClass.name, field.name, field.type.typeName))
                    current = current.base
                }
            }
        }
    }

    sealed interface AssignedValue {
        sealed interface Local : AssignedValue {
            data class Value(val value: AccessPathBase) : Local {
                init {
                    check(value !is AccessPathBase.LocalVar)
                }
            }

            data class Var(val local: AccessPathBase.LocalVar) : Local
        }

        sealed interface HeapLocation : AssignedValue {
            val base: AssignedValue
            val isImmutable: Boolean

            data class Field(
                override val base: AssignedValue,
                val field: JIRTypedField,
            ) : HeapLocation {
                override val isImmutable: Boolean
                    get() = this.field.isFinal && (base !is HeapLocation || base.isImmutable)
            }

            data class Array(
                override val base: AssignedValue,
                val arrayIndex: AccessPathBase
            ) : HeapLocation {
                override val isImmutable: Boolean get() = false
            }
        }
    }

    data class LocalVarVarUsageIndex(
        val dependsOn: PersistentMap<Int, PersistentBitSet>,
        val usages: PersistentMap<Int, PersistentBitSet>,
    ) {
        fun remove(localVar: Int): LocalVarVarUsageIndex {
            val (updatedDependsOn, updatedUsages) = removeVar(localVar, dependsOn, usages) { body ->
                forEach(body)
            }
            return LocalVarVarUsageIndex(updatedDependsOn, updatedUsages)
        }

        fun add(v: Int, d: Int): LocalVarVarUsageIndex {
            val (updatedDependsOn, updatedUsages) = addVarDependency(
                v, d, usages, dependsOn, ::emptyPersistentBitSet, PersistentBitSet::persistentAdd
            )
            return LocalVarVarUsageIndex(updatedDependsOn, updatedUsages)
        }
    }

    data class LocalVarFieldIndex(
        val dependsOn: PersistentMap<Int, PersistentSet<JIRTypedField>>,
        val usages: PersistentMap<JIRTypedField, PersistentBitSet>,
    ) {
        fun remove(localVar: Int): LocalVarFieldIndex {
            val (updatedDependsOn, updatedUsages) = removeVar(localVar, dependsOn, usages) { body ->
                forEach(body)
            }
            return LocalVarFieldIndex(updatedDependsOn, updatedUsages)
        }

        fun add(v: Int, f: JIRTypedField): LocalVarFieldIndex {
            val (updatedDependsOn, updatedUsages) = addVarDependency(
                v, f, usages, dependsOn, ::persistentHashSetOf, PersistentSet<JIRTypedField>::add
            )
            return LocalVarFieldIndex(updatedDependsOn, updatedUsages)
        }
    }

    data class StateIndex(
        val heapLocations: PersistentBitSet,
        val immutableHeapLocations: PersistentBitSet,
        val arrayRefOnHeapLocations: PersistentBitSet,
        val fields: LocalVarFieldIndex,
        val locals: LocalVarVarUsageIndex,
    ) {
        fun removeLocal(local: Int): StateIndex {
            val updatedHeap = heapLocations.persistentRemove(local)
            val updatedImmutableHeap = immutableHeapLocations.persistentRemove(local)
            val updatedArrayRefOnHeap = arrayRefOnHeapLocations.persistentRemove(local)
            val updatedFields = fields.remove(local)
            val updatedLocals = locals.remove(local)

            return StateIndex(
                heapLocations = updatedHeap,
                immutableHeapLocations = updatedImmutableHeap,
                arrayRefOnHeapLocations = updatedArrayRefOnHeap,
                fields = updatedFields,
                locals = updatedLocals
            )
        }

        fun updateIndex(local: Int, value: AssignedValue): StateIndex {
            var heapLocations = this.heapLocations
            var immutableHeapLocations = this.immutableHeapLocations
            var arrayRefOnHeapLocations = this.arrayRefOnHeapLocations
            var fields = this.fields
            var locals = this.locals

            if (value is AssignedValue.HeapLocation) {
                heapLocations = heapLocations.persistentAdd(local)
                immutableHeapLocations = if (value.isImmutable) {
                    immutableHeapLocations.persistentAdd(local)
                } else {
                    immutableHeapLocations.persistentRemove(local)
                }
            }

            fun traverse(v: AssignedValue) {
                when (v) {
                    is AssignedValue.HeapLocation.Array -> {
                        traverse(v.base)
                        arrayRefOnHeapLocations = arrayRefOnHeapLocations.persistentAdd(local)

                        val indexVar = v.arrayIndex as? AccessPathBase.LocalVar
                        if (indexVar != null) {
                            locals = locals.add(local, indexVar.idx)
                        }
                    }

                    is AssignedValue.HeapLocation.Field -> {
                        traverse(v.base)
                        fields = fields.add(local, v.field)
                    }

                    is AssignedValue.Local.Value -> {
                        // do nothing
                    }

                    is AssignedValue.Local.Var -> {
                        locals = locals.add(local, v.local.idx)
                    }
                }
            }

            traverse(value)

            return StateIndex(heapLocations, immutableHeapLocations, arrayRefOnHeapLocations, fields, locals)
        }

        fun mutableHeap(): BitSet = heapLocations.persistentRemoveAll(immutableHeapLocations)

        fun removeLocals(locals: BitSet): StateIndex {
            var result = this
            locals.forEach { local -> result = result.removeLocal(local) }
            return result
        }
    }

    class State(
        val localVarValue: PersistentMap<Int, AssignedValue>,
        private val stateIndex: StateIndex
    ) {
        fun updateLocal(local: AccessPathBase.LocalVar, value: AssignedValue): State {
            if (value is AssignedValue.Local.Var && value.local == local) {
                return State(
                    localVarValue.remove(local.idx),
                    stateIndex.removeLocal(local.idx),
                )
            }

            return State(
                localVarValue.put(local.idx, value),
                stateIndex.updateIndex(local.idx, value)
            )
        }

        fun findLocalAlias(local: AccessPathBase.LocalVar): AssignedValue? =
            localVarValue[local.idx]

        fun merge(new: State): State? {
            val locals = localVarValue.removeAll { local, value ->
                val newValue = new.localVarValue[local]
                value != newValue
            }

            if (locals === localVarValue) return null

            val removedLocals = this.localVarValue.keys - locals.keys
            return State(locals, stateIndex.removeLocals(removedLocals.toBitSet { it }))
        }

        fun resetHeapAlias(mutatedRef: JIRRef?): State {
            if (mutatedRef == null) {
                return resetAllMutableHeap()
            }

            return when (mutatedRef) {
                is JIRFieldRef -> removeFieldRefFromHeap(mutatedRef.field)
                is JIRArrayAccess -> removeArrayRefFromHeap()
                else -> error("Unknown ref: $mutatedRef")
            }
        }

        private fun resetAllMutableHeap(): State {
            val mutableHeap = stateIndex.mutableHeap()
            return State(
                localVarValue.removeAll(mutableHeap),
                stateIndex.removeLocals(mutableHeap)
            )
        }

        private fun removeArrayRefFromHeap(): State = State(
            localVarValue.removeAll(stateIndex.arrayRefOnHeapLocations),
            stateIndex.removeLocals(stateIndex.arrayRefOnHeapLocations)
        )

        private fun removeFieldRefFromHeap(field: JIRTypedField): State {
            val localsWithField = stateIndex.fields.usages[field] ?: return this
            return State(
                localVarValue.removeAll(localsWithField),
                stateIndex.removeLocals(localsWithField)
            )
        }

        fun resetLocalAlias(mutatedLocal: JIRImmediate): State {
            if (mutatedLocal !is JIRLocalVar) {
                TODO("wtf?")
            }

            val localVarUsages =
                (stateIndex.locals.usages[mutatedLocal.index] ?: PersistentBitSet())
                    .persistentAdd(mutatedLocal.index)

            return State(
                localVarValue.removeAll(localVarUsages),
                stateIndex.removeLocals(localVarUsages)
            )
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is State) return false

            return localVarValue == other.localVarValue
        }

        override fun hashCode(): Int = localVarValue.hashCode()

        override fun toString(): String = localVarValue.toString()

        companion object {
            fun empty() = State(
                persistentHashMapOf(),
                StateIndex(
                    emptyPersistentBitSet(),
                    emptyPersistentBitSet(),
                    emptyPersistentBitSet(),
                    LocalVarFieldIndex(persistentHashMapOf(), persistentHashMapOf()),
                    LocalVarVarUsageIndex(persistentHashMapOf(), persistentHashMapOf())
                )
            )
        }
    }

    private fun evalInst(inst: JIRInst, state: State): State = when (inst) {
        is JIRAssignInst -> {
            handleAssign(state, inst)
        }

        is JIRCallInst -> {
            handleCall(state, inst.callExpr)
        }

        is JIRCatchInst -> {
            val local = inst.throwable as? JIRLocalVar
            if (local != null) state.resetLocalAlias(local) else state
        }

        else -> state
    }

    private fun handleCall(state: State, call: JIRCallExpr): State {
        if (call.cantMutateAliasedHeap()) return state
        return state.resetHeapAlias(mutatedRef = null)
    }

    private fun JIRCallExpr.cantMutateAliasedHeap(): Boolean {
        if (args.isNotEmpty()) return false
        return method.isStatic || method.method.isConstructor
    }

    private fun handleAssign(initialState: State, assign: JIRAssignInst): State {
        val lhv = assign.lhv
        val rhv = assign.rhv
        var state = initialState

        when (lhv) {
            is JIRImmediate -> {
                state = state.resetLocalAlias(lhv)

                if (lhv !is JIRLocalVar) {
                    return state
                }

                val lhvAp = AccessPathBase.LocalVar(lhv.index)

                return when (rhv) {
                    is JIRImmediate -> {
                        localVarSimpleAlias(lhvAp, rhv.accessPathBase(), state)
                    }

                    is JIRCallExpr -> {
                        handleCall(state, rhv)
                    }

                    is JIRCastExpr -> {
                        val operand = rhv.operand as JIRImmediate
                        localVarSimpleAlias(lhvAp, operand.accessPathBase(), state)
                    }

                    is JIRRef -> {
                        when (rhv) {
                            is JIRFieldRef -> {
                                val instance = rhv.instance?.accessPathBase()
                                    ?: AccessPathBase.ClassStatic(rhv.field.enclosingType.typeName)

                                val base = state.currentValue(instance)
                                val alias = AssignedValue.HeapLocation.Field(base, rhv.field)
                                return state.updateLocal(lhvAp, alias)
                            }

                            is JIRArrayAccess -> {
                                val base = state.currentValue(rhv.array.accessPathBase())
                                val index = rhv.index.accessPathBase()
                                val alias = AssignedValue.HeapLocation.Array(base, index)
                                return state.updateLocal(lhvAp, alias)
                            }

                            else -> error("Unsupported ref: $rhv")
                        }
                    }

                    else -> state
                }
            }

            is JIRRef -> return state.resetHeapAlias(mutatedRef = lhv)

            else -> error("Unsupported assign lhv: $lhv")
        }
    }

    private fun localVarSimpleAlias(local: AccessPathBase.LocalVar, value: AccessPathBase, state: State): State {
        val alias = state.currentValue(value)
        return state.updateLocal(local, alias)
    }

    private fun State.currentValue(value: AccessPathBase): AssignedValue = when (value) {
        is AccessPathBase.LocalVar -> findLocalAlias(value) ?: AssignedValue.Local.Var(value)
        else -> AssignedValue.Local.Value(value)
    }
}

private fun JIRValue.accessPathBase(): AccessPathBase =
    MethodFlowFunctionUtils.accessPathBase(this)
        ?: error("Unexpected value: $this")

private fun <V> PersistentMap<Int, V>.removeAll(keys: BitSet): PersistentMap<Int, V> {
    return mutate { map -> keys.forEach { map.remove(it) } }
}

private inline fun <K, V> PersistentMap<K, V>.removeAll(removeIf: (K, V) -> Boolean): PersistentMap<K, V> {
    val mutable = builder()
    val iter = mutable.iterator()
    while (iter.hasNext()) {
        val entry = iter.next()
        if (removeIf(entry.key, entry.value)) {
            iter.remove()
        }
    }
    return mutable.build()
}

private inline fun <V, VSet> removeVar(
    localVar: Int,
    dependsOn: PersistentMap<Int, VSet>,
    usages: PersistentMap<V, PersistentBitSet>,
    setForEach: VSet.(body: (V) -> Unit) -> Unit
): Pair<PersistentMap<Int, VSet>, PersistentMap<V, PersistentBitSet>> {
    var dependentLocals: VSet? = null
    val updatedLocalVarDependency = dependsOn.mutate {
        dependentLocals = it.remove(localVar)
    }

    val updatedLocalVarUsages = usages.mutate { map ->
        dependentLocals?.setForEach { usage ->
            val current = map[usage] ?: return@setForEach
            val result = current.persistentRemove(localVar)
            if (result.isEmpty) map.remove(usage) else map[usage] = result
        }
    }

    return updatedLocalVarDependency to updatedLocalVarUsages
}

private inline fun <D, DSet> addVarDependency(
    v: Int,
    d: D,
    thisUsages: PersistentMap<D, PersistentBitSet>,
    thisDependsOn: PersistentMap<Int, DSet>,
    emptySet: () -> DSet,
    setAdd: DSet.(D) -> DSet
): Pair<PersistentMap<Int, DSet>, PersistentMap<D, PersistentBitSet>> {
    var usages = thisUsages[d] ?: emptyPersistentBitSet()
    usages = usages.persistentAdd(v)
    val updatedUsages = thisUsages.put(d, usages)

    var dependency = thisDependsOn[v] ?: emptySet()
    dependency = dependency.setAdd(d)
    val updatedDependency = thisDependsOn.put(v, dependency)

    return updatedDependency to updatedUsages
}
