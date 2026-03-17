package org.opentaint.dataflow.jvm.ap.ifds.alias

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAccessor
import org.opentaint.dataflow.jvm.ap.ifds.alias.JIRIntraProcAliasAnalysis.JIRInstGraph
import org.opentaint.dataflow.jvm.ap.ifds.alias.RefValue.Local
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import java.util.BitSet

class DSUAliasAnalysis(
    val methodCallResolver: CallResolver,
) {
    companion object {
        private const val HEAP_CHAIN_LIMIT = 5
    }

    private val aliasManager = AAInfoManager()

    data class ConnectedAliases(val aliasGroups: List<Set<AAInfo>>)

    data class AnalysisResult(
        val statesBeforeStmt: List<ConnectedAliases>,
        val statesAfterStmt: List<ConnectedAliases>
    )

    class GraphAnalysisState(size: Int, val call: CallTreeNode) {
        val stateBeforeStmt = arrayOfNulls<ImmutableState>(size)
        val stateAfterStmt = arrayOfNulls<ImmutableState>(size)
    }

    class ResolvedCallMethod(
        val graph: JIRInstGraph,
        val state: GraphAnalysisState
    )

    private object RootInstEvalContext : InstEvalContext {
        override fun createThis(isOuter: Boolean): RefValue = RefValue.This(isOuter)
        override fun createArg(idx: Int): RefValue = RefValue.Arg(idx)
        override fun createLocal(idx: Int): Local = Local(idx, level = 0, ctx = 0)
    }

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

    fun analyze(jig: JIRInstGraph): AnalysisResult {
        val initialState = ImmutableState(IntDisjointSets())
        val rootCall = CallTreeNode(level = 0, instEvalCtx = RootInstEvalContext)
        val analysisState = GraphAnalysisState(jig.statements.size, rootCall)
        val (stateBeforeStmt, stateAfterStmt) = analyze(jig, initialState, analysisState)
        return AnalysisResult(
            getConnectedAliases(stateBeforeStmt),
            getConnectedAliases(stateAfterStmt)
        )
    }

    private fun analyze(
        jig: JIRInstGraph,
        initialState: ImmutableState,
        analysisState: GraphAnalysisState
    ): Pair<Array<ImmutableState?>, Array<ImmutableState?>> {
        val stateBeforeStmt = analysisState.stateBeforeStmt
        val stateAfterStmt = analysisState.stateAfterStmt
        simulateJIG(
            jig, initialState, stateBeforeStmt, stateAfterStmt,
            { i, s -> eval(i, s, analysisState.call) },
            ::merge
        )
        return stateBeforeStmt to stateAfterStmt
    }

    private fun merge(states: Int2ObjectMap<ImmutableState?>): ImmutableState {
        val statesWithPhi = states.mapValuesTo(hashMapOf()) { it.value?.mutableCopy() }
        val result = IntDisjointSets()
        result.unionSets(statesWithPhi.values.mapNotNull { it?.aliasGroups })
        return State(result).asImmutable()
    }

    sealed interface AAInfo
    data class Unknown(val stmt: Stmt, val level: Int) : AAInfo
    data class CallReturn(val stmt: Stmt.Call, val level: Int) : AAInfo

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
        val field: AliasAccessor.Field,
        override val depth: Int,
        override val isImmutable: Boolean,
    ) : HeapAlias

    private fun eval(inst: JIRInst, state: ImmutableState, callFrame: CallTreeNode): ImmutableState =
        eval(inst, state.mutableCopy(), callFrame).asImmutable()

    private fun eval(inst: JIRInst, state: State, callFrame: CallTreeNode): State {
        val stmt = callFrame.instEvalCtx.evalInst(inst) ?: return state
        return when (stmt) {
            is Stmt.Call -> evalCall(stmt, state, callFrame)
            is Stmt.NoCall -> evalSimple(stmt, callFrame, state)
        }
    }

    private fun evalCall(stmt: Stmt.Call, state: State, callFrame: CallTreeNode): State {
        // todo: use instance alloc info
        val resolvedCall = callFrame.resolveCall(stmt, methodCallResolver)
        if (resolvedCall != null) {
            val result = evalCall(stmt, state, callFrame, resolvedCall)
            if (result != null) return result
        }

        val resultState = if (stmt.lValue != null) {
            val info = aliasSetFromInfo(CallReturn(stmt, callFrame.level))
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

    private fun evalCall(
        stmt: Stmt.Call,
        state: State,
        callFrame: CallTreeNode,
        methods: Map<JIRMethod, ResolvedCallMethod>
    ): State? {
        val stateBefore = state.asImmutable()
        val statesAfterCall = mutableListOf<ImmutableState>()

        for ((_, resolvedMethod) in methods) {
            analyze(resolvedMethod.graph, stateBefore, resolvedMethod.state)

            val methodFinalStates = resolvedMethod.state.mapCallFinalStates(
                resolvedMethod.graph, stmt, callFrame.level
            )
            statesAfterCall += methodFinalStates
        }

        if (statesAfterCall.isEmpty()) return null

        if (statesAfterCall.size == 1) {
            return statesAfterCall.first().mutableCopy()
        }

        val statesMap = Int2ObjectOpenHashMap<ImmutableState>()
        statesAfterCall.forEachIndexed { index, state ->
            statesMap[index] = state
        }
        return merge(statesMap).mutableCopy()
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
                is LocalAlias.SimpleLoc -> {
                    if (aInfo.loc.isOuter()) return true
                    if (aInfoIndex in invalid) return true
                }
            }
        }
        return false
    }

    private fun evalSimple(stmt: Stmt.NoCall, callFrame: CallTreeNode, state: State): State = when (stmt) {
        is Stmt.Assign -> evalAssign(stmt, callFrame, state)

        is Stmt.Copy -> evalCopy(stmt, state)

        is Stmt.FieldStore -> evalFieldStore(stmt, state)

        is Stmt.ArrayStore -> evalArrayStore(stmt, state)

        // no effect on alias info
        is Stmt.Return,
        is Stmt.Throw,
        is Stmt.WriteStatic -> state
    }

    private fun evalAssign(stmt: Stmt.Assign, callFrame: CallTreeNode, state: State): State {
        val rValue = evalExpr(stmt.expr, stmt, callFrame, state)
        return state.removeOldAndMergeWith(stmt.lValue.aliasInfo().index(), rValue)
    }

    private fun evalCopy(stmt: Stmt.Copy, state: State): State =
        state.removeOldAndMergeWith(stmt.lValue.aliasInfo(), stmt.rValue.aliasInfo())

    private fun evalExpr(expr: Expr, stmt: Stmt, callFrame: CallTreeNode, state: State): Set<AliasSet> = when (expr) {
        is Expr.Alloc,
        is SimpleValue.RefConst -> setOf(aliasSetFromInfo(LocalAlias.Alloc(stmt)))

        is Expr.FieldLoad -> evalFieldLoad(expr, stmt, callFrame, state)

        is Expr.ArrayLoad -> evalArrayLoad(expr, stmt, callFrame, state)

        is SimpleValue.Primitive,
        is Expr.Unknown -> setOf(aliasSetFromInfo(Unknown(stmt, callFrame.level)))
    }

    private fun evalHeapLoad(
        instance: RefValue,
        stmt: Stmt, callFrame: CallTreeNode,
        state: State, heapAppender: (AAInfo) -> HeapAlias?
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
            result += aliasSetFromInfo(Unknown(stmt, callFrame.level))
        }

        return result
    }

    private fun <T : HeapAlias> createHeapAliasWrtLimit(instance: AAInfo, builder: (Int) -> T): T? {
        val depth = if (instance is HeapAlias) instance.depth + 1 else 0
        if (depth > HEAP_CHAIN_LIMIT) return null
        return builder(depth)
    }

    private fun createFieldAliasWrtLimit(instance: AAInfo, field: JIRField): FieldAlias? {
        val f = AliasAccessor.Field(field.enclosingClass.name, field.name, field.type.typeName)
        return createFieldAliasWrtLimit(instance, f, field.isFinal)
    }

    private fun createFieldAliasWrtLimit(instance: AAInfo, field: AliasAccessor.Field, fieldIsImmutable: Boolean): FieldAlias? {
        val immutability = when (instance) {
            is HeapAlias -> instance.isImmutable
            else -> true
        } && fieldIsImmutable
        return createHeapAliasWrtLimit(instance) { depth -> FieldAlias(instance, field, depth, immutability) }
    }

    private fun createArrayAliasWrtLimit(instance: AAInfo): ArrayAlias? =
        createHeapAliasWrtLimit(instance) { depth -> ArrayAlias(instance, depth) }

    private fun evalArrayLoad(
        load: Expr.ArrayLoad,
        stmt: Stmt, callFrame: CallTreeNode, state: State
    ): Set<AliasSet> =
        evalHeapLoad(load.instance, stmt, callFrame, state, ::createArrayAliasWrtLimit)

    private fun evalFieldLoad(
        load: Expr.FieldLoad,
        stmt: Stmt, callFrame: CallTreeNode, state: State
    ): Set<AliasSet> =
        evalHeapLoad(load.instance, stmt, callFrame, state) { instance -> createFieldAliasWrtLimit(instance, load.field) }

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

                is LocalAlias.SimpleLoc -> {
                    if (info.loc.isOuter()) return true
                    continue
                }

                is Unknown -> return true
            }
        }
        return concrete > 1
    }

    private fun RefValue.isOuter(): Boolean = when (this) {
        is Local -> false
        is RefValue.This -> isOuter
        is RefValue.Arg,
        is RefValue.Static -> true
    }

    private fun GraphAnalysisState.mapCallFinalStates(
        graph: JIRInstGraph, callStmt: Stmt.Call, level: Int
    ): List<ImmutableState> =
        graph.statements.filterIsInstance<JIRReturnInst>().mapNotNull { inst ->
            val stmt = call.instEvalCtx.evalInst(inst) as Stmt.Return
            val finalState = stateAfterStmt[stmt.originalIdx]
                ?: return@mapNotNull null

            finalState.createStateAfterCall(callStmt, stmt.value, level)
        }

    private fun ImmutableState.createStateAfterCall(stmt: Stmt.Call, retVal: RefValue?, level: Int): ImmutableState {
        var state = mutableCopy()
        stmt.lValue?.let { v ->
            val retVal = retVal?.aliasInfo() ?: return@let
            val outerRetVal = v.aliasInfo()
            state = state.removeOldAndMergeWith(outerRetVal, retVal)
        }

        val result = state.removeCallLocals(level)
        return result.asImmutable()
    }

    private fun State.removeCallLocals(level: Int): State {
        val result = aliasGroups.mutableCopy()
        val aaInfoToRemove = BitSet()

        for (aliasSet in aliasGroups.allSets()) {
            for (info in aliasSet) {
                val element = aliasManager.getElementUncheck(info)
                if (!element.isCallLocal(level)) continue

                aaInfoToRemove.set(info)

                val alternative = element.nonLocalAlternative(aliasGroups, level)
                    ?: continue

                val alternativeId = aliasManager.getOrAdd(alternative)
                aliasSet.forEach { result.union(it, alternativeId) }
            }
        }

        result.removeAll { aaInfoToRemove.get(it) }
        return State(result)
    }

    private fun AAInfo.isCallLocal(level: Int): Boolean = when (this) {
        is LocalAlias.Alloc -> false
        is Unknown -> this.level > level
        is CallReturn -> this.level > level
        is LocalAlias.SimpleLoc -> loc is Local && loc.level > level
        is HeapAlias -> instance.isCallLocal(level)
    }

    private fun AAInfo.nonLocalAlternative(aliasGroups: IntDisjointSets, level: Int): AAInfo? {
        if (this !is HeapAlias) return null
        return findNonLocalAlternative(aliasGroups, level)
    }

    private fun AAInfo.findNonLocalAlternative(aliasGroups: IntDisjointSets, level: Int): AAInfo? {
        return when (this) {
            is Unknown, is CallReturn -> null
            is LocalAlias.Alloc -> this

            is LocalAlias.SimpleLoc -> {
                if (loc !is Local) return this
                if (loc.level <= level) return this

                val alternatives = aliasGroups.findAlternatives(this)
                    .filterTo(mutableListOf()) { !it.isCallLocal(level) }

                alternatives.sortBy { it.alternativePriority() }

                alternatives.firstNotNullOfOrNull { it.findNonLocalAlternative(aliasGroups, level) }
            }

            is HeapAlias -> {
                val instanceAlternative = instance.findNonLocalAlternative(aliasGroups, level)
                    ?: return null

                when (this) {
                    is ArrayAlias -> createArrayAliasWrtLimit(instanceAlternative)
                    is FieldAlias -> createFieldAliasWrtLimit(instanceAlternative, field, isImmutable)
                }
            }
        }
    }

    private fun AAInfo.alternativePriority(): Int = when (this) {
        is LocalAlias.Alloc -> 0
        is LocalAlias.SimpleLoc -> 1
        is FieldAlias -> 2 + this.depth
        is ArrayAlias -> 1000 + this.depth
        else -> 10_000
    }

    private fun IntDisjointSets.findAlternatives(element: AAInfo): List<AAInfo> {
        val elementId = aliasManager.getOrAdd(element)
        val alternatives = mutableListOf<AAInfo>()
        forEachElementInSet(elementId) { alternativeId ->
            if (alternativeId == elementId) return@forEachElementInSet

            val alternative = aliasManager.getElementUncheck(alternativeId)
            alternatives.add(alternative)
        }
        return alternatives
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
