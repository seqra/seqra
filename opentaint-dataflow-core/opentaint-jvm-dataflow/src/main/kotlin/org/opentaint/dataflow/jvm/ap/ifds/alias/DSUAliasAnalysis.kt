package org.opentaint.dataflow.jvm.ap.ifds.alias

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import org.opentaint.dataflow.jvm.ap.ifds.alias.RefValue.Local
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.cfg.JIRInst
import java.util.BitSet

class DSUAliasAnalysis {
    companion object {
        private const val HEAP_CHAIN_LIMIT = 5
    }

    private val aliasManager = AAInfoManager()

    data class ConnectedAliases(val aliasGroups: List<Set<AAInfo>>)

    data class AnalysisResult(
        val statesBeforeStmt: List<ConnectedAliases>,
        val statesAfterStmt: List<ConnectedAliases>
    )

    data class ImmutableState(val aliasGroups: ImmutableIntDSU) {
        fun mutableCopy(): State = State(aliasGroups.mutableCopy())
    }

    data class State(val aliasGroups: IntDisjointSets) {
        fun asImmutable(): ImmutableState = ImmutableState(aliasGroups)
    }

    private fun AAInfo.index(): Int {
        return aliasManager.getOrAdd(this)
    }

    private fun getConnectedAliases(states: Array<ImmutableState?>): List<ConnectedAliases> =
        List(states.size) { stmt ->
            states[stmt]?.let { s ->
                val groups = s.aliasGroups.mutableCopy().allSets().map { set ->
                    set.map { aliasManager.getElementUncheck(it) }.toSet()
                }
                ConnectedAliases(groups)
            } ?: ConnectedAliases(emptyList())
        }

    fun analyze(jig: JIRIntraProcAliasAnalysis.JIRInstGraph): AnalysisResult {
        val initialState = ImmutableState(IntDisjointSets())
        val stateBeforeStmt = arrayOfNulls<ImmutableState>(jig.statements.size)
        val stateAfterStmt = simulateJIG(jig, initialState, stateBeforeStmt, ::eval, ::merge)
        return AnalysisResult(
            getConnectedAliases(stateBeforeStmt),
            getConnectedAliases(stateAfterStmt)
        )
    }

    private fun merge(states: Int2ObjectMap<ImmutableState?>): ImmutableState {
        val statesWithPhi = states.mapValuesTo(hashMapOf()) { it.value?.mutableCopy() }
        val result = IntDisjointSets()
        result.unionSets(statesWithPhi.values.mapNotNull { it?.aliasGroups })
        return State(result).asImmutable()
    }

    sealed interface AAInfo
    data class Unknown(val stmt: Stmt) : AAInfo
    data class CallReturn(val stmt: Stmt.Call) : AAInfo

    sealed interface LocalAlias : AAInfo {
        data class SimpleLoc(val loc: RefValue) : LocalAlias
        data class Alloc(val stmt: Stmt) : LocalAlias
    }

    sealed interface HeapAlias : AAInfo {
        val instance: AAInfo
        val depth: Int
        val isImmutable: Boolean
    }

    data class ArrayAlias(
        override val instance: AAInfo,
        override val depth: Int,
    ) : HeapAlias {
        override val isImmutable: Boolean get() = false
    }

    data class FieldAlias(
        override val instance: AAInfo,
        val field: JIRField,
        override val depth: Int,
        override val isImmutable: Boolean,
    ) : HeapAlias

    private fun eval(inst: JIRInst, state: ImmutableState): ImmutableState =
        eval(inst, state.mutableCopy()).asImmutable()

    private fun eval(inst: JIRInst, state: State): State {
        val stmt = evalInst(inst) ?: return state
        return when (stmt) {
            is Stmt.Call -> evalCall(stmt, state)
            is Stmt.NoCall -> evalSimple(stmt, state)
        }
    }

    private fun evalCall(stmt: Stmt.Call, state: State): State {
        val resultState = if (stmt.lValue != null) {
            val info = aliasSetFromInfo(CallReturn(stmt))
            state.removeOldAndMergeWith(stmt.lValue.aliasInfo().index(), setOf(info))
        } else state
        if (stmt.cantMutateAliasedHeap()) return resultState

        val argAliases = hashSetOf<Int>()
        stmt.args.forEach { arg ->
            val info = arg.aliasInfo() ?: return@forEach
            val infoIndex = aliasManager.getOrAdd(info)
            resultState.forEachAliasInSet(infoIndex) { argAliases.add(it) }
        }
        return resultState.invalidateOuterHeapAliases(argAliases)
    }

    private fun State.invalidateOuterHeapAliases(startInvalidAliases: Set<Int>): State {
        val invalidAliases = collectTransitiveInvalidAliases(startInvalidAliases)
        return resetHeapWithInvalidInstance(invalidAliases)
    }

    private fun State.resetHeapWithInvalidInstance(invalidAliases: Set<Int>): State {
        val result = aliasGroups.mutableCopy()
        result.removeAll {
            val element = aliasManager.getElementUncheck(it)
            if (element !is HeapAlias || element.isImmutable) return@removeAll false
            it in invalidAliases
        }
        return State(result)
    }

    private fun State.collectTransitiveInvalidAliases(startInvalidAliases: Set<Int>): Set<Int> {
        val currentAliasGroups = aliasGroups.allSets().toList()

        val invalidAliases = hashSetOf<Int>()
        invalidAliases.addAll(startInvalidAliases)

        val invalidGroups = BitSet()

        do {
            val before = invalidAliases.size

            for ((i, aliasSet) in currentAliasGroups.withIndex()) {
                if (invalidGroups.get(i)) continue

                if (aliasGroupContainsInvalidOrOuter(aliasSet, invalidAliases)) {
                    invalidGroups.set(i)
                    invalidAliases.addAll(aliasSet)
                }
            }

        } while (before < invalidAliases.size)

        return invalidAliases
    }

    private fun aliasGroupContainsInvalidOrOuter(group: Iterable<Int>, invalid: Set<Int>): Boolean {
        for (aInfoIndex in group) {
            when (val aInfo = aliasManager.getElementUncheck(aInfoIndex)) {
                is Unknown -> return true
                is CallReturn -> return true
                is HeapAlias -> if (aInfo.instance.index() in invalid) return true
                is LocalAlias.Alloc -> continue
                is LocalAlias.SimpleLoc -> when (aInfo.loc) {
                    is Local -> if (aInfoIndex in invalid) return true

                    // outer
                    is RefValue.Arg,
                    is RefValue.Static,
                    is RefValue.This -> return true
                }
            }
        }
        return false
    }

    private fun evalSimple(stmt: Stmt.NoCall, state: State): State = when (stmt) {
        is Stmt.Assign -> evalAssign(stmt, state)

        is Stmt.Copy -> evalCopy(stmt, state)

        is Stmt.FieldStore -> evalFieldStore(stmt, state)

        is Stmt.ArrayStore -> evalArrayStore(stmt, state)

        // no effect on alias info
        is Stmt.Return,
        is Stmt.Throw,
        is Stmt.WriteStatic -> state
    }

    private fun evalAssign(stmt: Stmt.Assign, state: State): State {
        val rValue = evalExpr(stmt.expr, stmt, state)
        return state.removeOldAndMergeWith(stmt.lValue.aliasInfo().index(), rValue)
    }

    private fun evalCopy(stmt: Stmt.Copy, state: State): State =
        state.removeOldAndMergeWith(stmt.lValue.aliasInfo(), stmt.rValue.aliasInfo())

    private fun evalExpr(expr: Expr, stmt: Stmt, state: State): Set<AliasSet> = when (expr) {
        is Expr.Alloc,
        is SimpleValue.RefConst -> setOf(aliasSetFromInfo(LocalAlias.Alloc(stmt)))

        is Expr.FieldLoad -> evalFieldLoad(expr, stmt, state)

        is Expr.ArrayLoad -> evalArrayLoad(expr, stmt, state)

        is SimpleValue.Primitive,
        is Expr.Unknown -> setOf(aliasSetFromInfo(Unknown(stmt)))
    }

    private fun evalHeapLoad(
        instance: RefValue,
        stmt: Stmt, state: State, heapAppender: (AAInfo) -> HeapAlias?
    ): Set<AliasSet> {
        var valueMayBeUnknown = false

        val result = hashSetOf<AliasSet>()
        state.forEachAliasInSet(instance.aliasInfo().index()) { instanceIndex ->
            val instanceAlias = aliasManager.getElementUncheck(instanceIndex)
            val field = heapAppender(instanceAlias)
            if (field != null) {
                result += aliasSetFromInfo(field)
            } else {
                valueMayBeUnknown = true
            }
        }

        if (valueMayBeUnknown) {
            result += aliasSetFromInfo(Unknown(stmt))
        }

        return result
    }

    private fun <T: HeapAlias> createHeapAliasWrtLimit(instance: AAInfo, builder: (Int) -> T): T? {
        val depth = if (instance is HeapAlias) instance.depth + 1 else 0
        if (depth > HEAP_CHAIN_LIMIT) return null
        return builder(depth)
    }

    private fun createFieldAliasWrtLimit(instance: AAInfo, field: JIRField): FieldAlias? {
        val immutability = when (instance) {
            is HeapAlias -> instance.isImmutable
            else -> true
        } && field.isFinal
        return createHeapAliasWrtLimit(instance) { depth -> FieldAlias(instance, field, depth, immutability) }
    }

    private fun createArrayAliasWrtLimit(instance: AAInfo): ArrayAlias? =
        createHeapAliasWrtLimit(instance) { depth -> ArrayAlias(instance, depth) }

    private fun evalArrayLoad(
        load: Expr.ArrayLoad,
        stmt: Stmt, state: State
    ): Set<AliasSet> =
        evalHeapLoad(load.instance, stmt, state, ::createArrayAliasWrtLimit)

    private fun evalFieldLoad(
        load: Expr.FieldLoad,
        stmt: Stmt, state: State
    ): Set<AliasSet> =
        evalHeapLoad(load.instance, stmt, state) { instance -> createFieldAliasWrtLimit(instance, load.field) }

    private fun evalHeapStore(
        instance: RefValue,
        value: ExprOrValue,
        state: State,
        heapAppender: (AAInfo) -> HeapAlias?
    ): State {
        val currentInstanceAliasIndexes = hashSetOf<Int>()
        state.forEachAliasInSet(instance.aliasInfo().index()) { currentInstanceAliasIndexes += it }

        val fields = currentInstanceAliasIndexes.mapNotNullTo(hashSetOf()) {
            heapAppender(aliasManager.getElementUncheck(it))?.index()
        }

        var resultState = state
        if (!currentInstanceAliasIndexes.containsMultipleConcreteOrOuterLocations(state, hashSetOf())) {
            resultState = resultState.remove(fields)
        }

        if (value is RefValue)
            return resultState.mergeWith(value.aliasInfo().index(), fields)
        return resultState
    }

    private fun evalArrayStore(stmt: Stmt.ArrayStore, state: State): State =
        evalHeapStore(stmt.instance, stmt.value, state, ::createArrayAliasWrtLimit)

    private fun evalFieldStore(stmt: Stmt.FieldStore, state: State): State =
        evalHeapStore(stmt.instance, stmt.value, state) { createFieldAliasWrtLimit(it, stmt.field) }

    private fun Iterable<Int>.containsMultipleConcreteOrOuterLocations(
        state: State,
        visited: MutableSet<Int>
    ): Boolean {
        var concrete = 0
        for (infoIndex in this) {
            // value depends on itself
            if (infoIndex in visited) {
                return true
            }

            when (val info = aliasManager.getElementUncheck(infoIndex)) {
                // outer
                is CallReturn -> return true

                is HeapAlias -> {
                    val toRollback = this.filterTo(hashSetOf()) { visited.add(it) }

                    try {
                        val instances = mutableListOf<Int>()
                        state.forEachAliasInSet(info.instance.index()) { instances.add(it) }
                        if (instances.containsMultipleConcreteOrOuterLocations(state, visited)) {
                            return true
                        }
                    } finally {
                        visited.removeAll(toRollback)
                    }

                    concrete++
                }

                is LocalAlias.Alloc -> concrete++

                is LocalAlias.SimpleLoc -> when (info.loc) {
                    is Local -> continue

                    // outer
                    is RefValue.Arg,
                    is RefValue.Static,
                    is RefValue.This -> return true
                }

                is Unknown -> return true
            }
        }
        return concrete > 1
    }

    private fun RefValue.aliasInfo(): AAInfo = LocalAlias.SimpleLoc(this)

    private fun Value.aliasInfo(): AAInfo? = when (this) {
        is RefValue -> aliasInfo()
        is SimpleValue.Primitive,
        is SimpleValue.RefConst -> null
    }

    private data class AliasSet(val repr: Int)

    private fun aliasSetFromInfo(info: AAInfo): AliasSet = AliasSet(info.index())

    private fun State.forEachAliasInSet(info: Int, body: (Int) -> Unit) {
        aliasGroups.forEachElementInSet(info, body)
    }

    private fun State.remove(infos: Set<Int>): State {
        val result = aliasGroups.mutableCopy()
        result.removeAll {
            val info = aliasManager.getElementUncheck(it)
            info.dependsOn(infos)
        }
        return State(result)
    }

    private fun AAInfo.dependsOn(values: Set<Int>): Boolean {
        if (this is Unknown) return false
        if (this.index() in values) return true

        if (this !is HeapAlias) return false
        return instance.dependsOn(values)
    }

    private fun State.removeOldAndMergeWith(info: AAInfo, other: AAInfo): State =
        removeOldAndMergeWith(info.index(), setOf(aliasSetFromInfo(other)))

    private fun State.removeOldAndMergeWith(info: Int, alias: Set<AliasSet>): State {
        val result = this.remove(setOf(info))
        return result.mergeAliasSets(info, alias)
    }

    private fun State.mergeWith(info: Int, other: Set<Int>): State =
        mergeAliasSets(info, other.mapTo(hashSetOf()) { AliasSet(it) })

    private fun State.mergeAliasSets(info: Int, other: Set<AliasSet>): State {
        val result = aliasGroups.mutableCopy()
        val infoRepr = result.find(info)
        other.forEach { result.union(infoRepr, it.repr) }
        return State(result)
    }

    private fun Stmt.Call.cantMutateAliasedHeap(): Boolean {
        if (args.any { it !is SimpleValue.Primitive }) return false
        return method.isStatic || method.isConstructor
    }
}
