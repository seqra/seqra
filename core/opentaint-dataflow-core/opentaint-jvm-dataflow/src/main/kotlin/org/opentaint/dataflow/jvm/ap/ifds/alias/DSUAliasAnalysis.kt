package org.opentaint.dataflow.jvm.ap.ifds.alias

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntCollection
import it.unimi.dsi.fastutil.ints.IntIntImmutablePair
import it.unimi.dsi.fastutil.ints.IntIntMutablePair
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis.AliasAccessor
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalVariableReachability
import org.opentaint.dataflow.jvm.ap.ifds.alias.JIRIntraProcAliasAnalysis.JIRInstGraph
import org.opentaint.dataflow.jvm.ap.ifds.alias.RefValue.Local
import org.opentaint.dataflow.util.forEachInt
import org.opentaint.dataflow.util.forEachIntEntry
import org.opentaint.dataflow.util.mapIntTo
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRReturnInst
import java.util.BitSet

class DSUAliasAnalysis(
    val methodCallResolver: CallResolver,
    val rootMethodReachabilityInfo: JIRLocalVariableReachability,
    val cancellation: AnalysisCancellation
) {
    private val aliasManager = AAInfoManager()
    private val dsuMergeStrategy = DsuMergeStrategy(aliasManager)

    private val nestedReachabilityInfo = hashMapOf<JIRMethod, JIRLocalVariableReachability>()

    private fun methodReachabilityInfo(method: JIRMethod): JIRLocalVariableReachability {
        if (method == rootMethodReachabilityInfo.method) return rootMethodReachabilityInfo
        return nestedReachabilityInfo.getOrPut(method) {
            JIRLocalVariableReachability(
                method,
                rootMethodReachabilityInfo.graph,
                rootMethodReachabilityInfo.languageManager
            )
        }
    }

    class DsuMergeStrategy(
        private val manager: AAInfoManager
    ) : IntDisjointSets.RankStrategy {
        override fun compare(a: Int, b: Int): Int {
            val aImpl = manager.getElementUncheck(a)
            val bImpl = manager.getElementUncheck(b)
            return aImpl.compareTo(bImpl)
        }
    }

    data class ConnectedAliases(val aliasGroups: Int2ObjectOpenHashMap<List<AAInfo>>)

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
        override fun createLocal(idx: Int): Local = Local(idx, ContextInfo.rootContext)
    }

    interface ImmutableState {
        fun mutableCopy(): State
    }

    class State private constructor(
        val manager: AAInfoManager,
        val aliasGroups: IntDisjointSets,
    ) : ImmutableState {

        override fun hashCode(): Int = error("Unsupported operation")

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is State) return false

            /**
             * We don't need to align heap instances here.
             * Since set repr selection is deterministic due to strategy,
             * if sets are equal their repr are also equal.
             * So, heap instances must be equal.
             * */
            return aliasGroups == other.aliasGroups
        }

        fun asImmutable(): ImmutableState = this

        override fun mutableCopy(): State = State(manager, aliasGroups.mutableCopy())

        fun removeUnsafe(infos: IntOpenHashSet): State {
            if (infos.isEmpty()) return this

            val normalizedInfos = fixHeapElementInstance(infos)
            val result = aliasGroups.mutableCopy()

            val removedInstances = IntOpenHashSet()
            result.prepareRemoveAll(normalizedInfos, removedInstances)

            val removeAfterHeapFix = IntOpenHashSet()
            restoreHeapInvariant(manager, result, removeAfterHeapFix)

            // since we use prepare-remove, old replaced roots are still in the DSU
            removeAfterHeapFix.addAll(normalizedInfos)
            result.removeAll(removeAfterHeapFix)

            if (removedInstances.isEmpty()) {
                return State(manager, result)
            }

            val removedHeap = IntOpenHashSet()
            result.allElements().forEachInt {
                if (!manager.isHeapAlias(it)) return@forEachInt

                val heapElement = manager.getHeapRefUnchecked(it)
                if (removedInstances.contains(heapElement.instance)) {
                    removedHeap.add(it)
                }
            }

            return State(manager, result).removeUnsafe(removedHeap)
        }

        fun aliasGroupId(info: Int): Int = aliasGroups.find(info)
        fun aliasGroupRepr(groupId: Int): Int = aliasGroups.find(groupId)

        fun mergeAliasSets(aliasSets: IntOpenHashSet): State {
            if (aliasSets.size < 2) return this

            val firstRepr = aliasSets.intIterator().nextInt()
            val relations = mutableListOf<IntIntMutablePair>()
            aliasSets.forEachInt {
                if (it == firstRepr) return@forEachInt
                relations += IntIntMutablePair(firstRepr, it)
            }

            val result = aliasGroups.mutableCopy()
            mergeUnionRelations(relations, result, manager)

            return State(manager, result)
        }

        fun forEachAliasInSet(info: Int, body: (Int) -> Unit) = forEachAliasInSetWithBreak(info, body)

        fun forEachAliasInSetWithBreak(info: Int, body: (Int) -> Unit?) {
            aliasGroups.forEachElementInSet(info, body)
        }

        fun allAliasSets(): Collection<IntOpenHashSet> = aliasGroups.allSets()

        fun allSetElements(): IntOpenHashSet = aliasGroups.allElements()

        override fun toString(): String = buildString {
            for (aliasSet in allAliasSets()) {
                appendLine("{")
                aliasSet.forEachInt {
                    appendLine("\t($it) -> ${manager.getElementUncheck(it)}")
                }
                appendLine("}")
            }
        }

        private fun fixHeapElementInstance(elements: IntOpenHashSet) =
            elements.mapIntTo(IntOpenHashSet(elements.size)) {
                ensureHeapElementCorrect(it, aliasGroups, manager)
            }

        companion object {
            fun empty(manager: AAInfoManager, strategy: DsuMergeStrategy): State =
                State(manager, IntDisjointSets(strategy))

            private fun restoreHeapInvariant(
                manager: AAInfoManager,
                state: IntDisjointSets,
                elementsToRemove: IntOpenHashSet,
            ) {
                while (true) {
                    val replacements = mutableListOf<IntIntImmutablePair>()

                    state.allElements().forEachInt { elementIdx ->
                        if (elementsToRemove.contains(elementIdx)) return@forEachInt

                        val fixedHeap = ensureHeapElementCorrect(elementIdx, state, manager)
                        if (fixedHeap == elementIdx) return@forEachInt

                        replacements += IntIntImmutablePair(elementIdx, fixedHeap)
                    }

                    if (replacements.isEmpty()) return

                    for (replacement in replacements) {
                        elementsToRemove.add(replacement.leftInt())
                        state.union(replacement.leftInt(), replacement.rightInt())
                    }
                }
            }

            private fun ensureHeapElementCorrect(element: Int, state: IntDisjointSets, manager: AAInfoManager): Int {
                if (!manager.isHeapAlias(element)) return element

                val heapElement = manager.getHeapRefUnchecked(element)
                val heapInstanceRepr = state.find(heapElement.instance)
                if (heapInstanceRepr == heapElement.instance) return element

                return manager.replaceHeapInstance(element, heapInstanceRepr)
            }

            fun merge(manager: AAInfoManager, strategy: DsuMergeStrategy, states: List<ImmutableState>): State {
                val allElementParentRelations = mutableListOf<IntIntMutablePair>()
                states.forEach { s ->
                    val stateDsu = (s as State).aliasGroups
                    stateDsu.collectElementParentPairs(allElementParentRelations)
                }

                val result = IntDisjointSets(strategy)
                mergeUnionRelations(allElementParentRelations, result, manager)

                return State(manager, result)
            }

            private fun mergeUnionRelations(
                relations: List<IntIntMutablePair>,
                result: IntDisjointSets,
                manager: AAInfoManager
            ) {
                val removedElements = IntOpenHashSet()
                while (true) {
                    var modified = false
                    relations.forEach {
                        val status = result.union(it.leftInt(), it.rightInt())
                        modified = modified or status
                    }

                    if (!modified) break

                    restoreHeapInvariant(manager, result, removedElements)

                    relations.forEach { relation ->
                        val fixedLeft = ensureHeapElementCorrect(relation.leftInt(), result, manager)
                        val fixedRight = ensureHeapElementCorrect(relation.rightInt(), result, manager)

                        relation.left(fixedLeft)
                        relation.right(fixedRight)
                    }
                }
                result.removeAll(removedElements)
            }
        }
    }

    private fun AAInfo.index(): Int {
        return aliasManager.getOrAdd(this)
    }

    private fun State.allElements(): IntOpenHashSet {
        val result = IntOpenHashSet()
        result.addAll(allSetElements())

        val unprocessedHeapElement = IntArrayList()
        result.forEachInt {
            if (manager.isHeapAlias(it)) unprocessedHeapElement.add(it)
        }

        while (unprocessedHeapElement.isNotEmpty()) {
            val element = unprocessedHeapElement.removeInt(unprocessedHeapElement.lastIndex)
            val instance = manager.getHeapRefUnchecked(element).instance
            if (result.add(instance)) {
                if (manager.isHeapAlias(instance)) {
                    unprocessedHeapElement.add(instance)
                }
            }
        }

        return result
    }

    private fun getConnectedAliases(states: Array<ImmutableState?>): List<ConnectedAliases> =
        List(states.size) { stmt ->
            val state = states[stmt]?.mutableCopy()
                ?: return@List ConnectedAliases(Int2ObjectOpenHashMap())

            val groupsElements = Int2ObjectOpenHashMap<IntOpenHashSet>()

            state.allElements().forEach { element ->
                val groupId = state.aliasGroupId(element)
                val group = groupsElements.get(groupId)
                    ?: IntOpenHashSet().also { groupsElements.put(groupId, it) }
                group.add(element)
            }

            val groups = Int2ObjectOpenHashMap<List<AAInfo>>()
            groupsElements.forEachIntEntry { key, groupElements ->
                val elements = mutableListOf<AAInfo>()
                groupElements.forEachInt {
                    elements += aliasManager.getElementUncheck(it)
                }
                groups.put(key, elements)
            }

            ConnectedAliases(groups)
        }

    fun analyze(jig: JIRInstGraph): AnalysisResult {
        val initialState = State.empty(aliasManager, dsuMergeStrategy)
        val rootCall = CallTreeNode(ContextInfo.rootContext, instEvalCtx = RootInstEvalContext)
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
            { i, s -> merge(i, s, analysisState.call) }
        )
        return stateBeforeStmt to stateAfterStmt
    }

    private fun merge(inst: JIRInst, states: Int2ObjectMap<ImmutableState?>, call: CallTreeNode): ImmutableState {
        val statesToMerge = states.values.filterNotNull()
        val merged = State.merge(aliasManager, dsuMergeStrategy, statesToMerge)

        val reachabilityInfo = methodReachabilityInfo(inst.location.method)
        val instIdx = inst.location.index
        val reachableMerged = merged.removeUnreachableLocals(reachabilityInfo, instIdx, call)

        return reachableMerged.asImmutable()
    }

    private fun State.removeUnreachableLocals(
        reachabilityInfo: JIRLocalVariableReachability,
        instIdx: Int,
        call: CallTreeNode
    ): State {
        val unreachableLocals = IntOpenHashSet()
        allElements().forEachInt {
            val element = manager.getElementUncheck(it)
            if (element !is LocalAlias.SimpleLoc) return@forEachInt

            val loc = element.loc
            if (loc !is Local) return@forEachInt
            if (loc.ctx != call.ctx) return@forEachInt

            if (reachabilityInfo.isReachable(loc.idx, instIdx)) return@forEachInt

            unreachableLocals.add(it)
        }

        return removeUnsafe(unreachableLocals)
    }

    private fun eval(inst: JIRInst, state: ImmutableState, callFrame: CallTreeNode): ImmutableState {
        cancellation.checkpoint()

        val reachabilityInfo = methodReachabilityInfo(inst.location.method)
        val instIdx = inst.location.index

        val s0 = state.mutableCopy()
        val s1 = s0.removeUnreachableLocals(reachabilityInfo, instIdx, callFrame)

        return eval(inst, s1, callFrame).asImmutable()
    }

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
            val info = aliasSetFromInfo(CallReturn(stmt, callFrame.ctx))
            state.removeOldAndMergeWith(stmt.lValue.aliasInfo().index(), info)
        } else state
        if (stmt.cantMutateAliasedHeap()) return resultState

        val argAliases = IntOpenHashSet()
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
                resolvedMethod.graph, stmt, callFrame.ctx.level
            )
            statesAfterCall += methodFinalStates
        }

        if (statesAfterCall.isEmpty()) return null

        if (statesAfterCall.size == 1) {
            return statesAfterCall.first().mutableCopy()
        }

        return State.merge(aliasManager, dsuMergeStrategy, statesAfterCall)
    }

    private fun State.invalidateOuterHeapAliases(startInvalidAliases: IntOpenHashSet): State {
        val invalidAliases = collectTransitiveInvalidAliases(startInvalidAliases)

        val invalidHeapAliases = IntOpenHashSet()
        invalidAliases.forEach {
            val element = aliasManager.getElementUncheck(it)
            if (element !is HeapAlias || isHeapImmutable(element, IntOpenHashSet())) return@forEach

            invalidHeapAliases.add(it)
        }

        return removeUnsafe(invalidHeapAliases)
    }

    private fun State.collectTransitiveInvalidAliases(startInvalidAliases: IntOpenHashSet): IntOpenHashSet {
        val currentAliasGroups = allAliasSets().toList()

        val invalidAliases = IntOpenHashSet()
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

    private fun State.aliasGroupContainsInvalidOrOuter(group: IntCollection, invalid: IntOpenHashSet): Boolean {
        group.forEachInt { aInfoIndex ->
            when (val aInfo = aliasManager.getElementUncheck(aInfoIndex)) {
                is Unknown -> return true
                is CallReturn -> return true
                is HeapAlias -> if (aliasGroupRepr(aInfo.instance) in invalid) return true
                is LocalAlias.Alloc -> return@forEachInt
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

    private fun evalExpr(expr: Expr, stmt: Stmt, callFrame: CallTreeNode, state: State): AliasSet = when (expr) {
        is Expr.Alloc,
        is SimpleValue.RefConst -> aliasSetFromInfo(LocalAlias.Alloc(stmt, callFrame.ctx))

        is Expr.FieldLoad -> evalFieldLoad(expr, state)

        is Expr.ArrayLoad -> evalArrayLoad(expr, state)

        is SimpleValue.Primitive,
        is Expr.Unknown -> aliasSetFromInfo(Unknown(stmt, callFrame.ctx))
    }

    private fun evalHeapLoad(
        instance: RefValue,
        state: State,
        heapAppender: (Int) -> HeapAlias
    ): AliasSet {
        val obj = state.heapObj(instance.aliasInfo())
        val heapLocation = heapAppender(obj)
        return aliasSetFromInfo(heapLocation)
    }

    private fun State.heapObj(instance: AAInfo): Int {
        val instanceIdx = instance.index()
        return aliasGroupId(instanceIdx)
    }

    private fun State.isHeapImmutable(obj: AAInfo, onStack: IntOpenHashSet): Boolean = when (obj) {
        is HeapAlias -> obj.heapAccessor.isImmutable && isHeapImmutable(obj.instance, onStack)
        is LocalAlias.SimpleLoc -> !obj.loc.isOuter()
        is LocalAlias.Alloc -> true
        is CallReturn -> false
        is Unknown -> false
    }

    private fun State.isHeapImmutable(obj: Int, onStack: IntOpenHashSet): Boolean {
        var result = true
        forEachAliasInSetWithBreak(obj) {
            if (!onStack.add(it)) {
                return@forEachAliasInSetWithBreak null
            }

            val element = manager.getElementUncheck(it)
            if (!isHeapImmutable(element, onStack)) {
                result = false
                return@forEachAliasInSetWithBreak null
            }

            onStack.remove(it)
            Unit
        }
        return result
    }

    private fun createFieldAlias(obj: Int, field: JIRField): HeapAlias {
        val f = AliasAccessor.Field(field.enclosingClass.name, field.name, field.type.typeName)
        return createFieldAlias(obj, f, field.isFinal)
    }

    private fun createFieldAlias(obj: Int, field: AliasAccessor.Field, fieldIsImmutable: Boolean): HeapAlias {
        return HeapAlias(obj, FieldAlias(field, fieldIsImmutable))
    }

    private fun createArrayAlias(obj: Int): HeapAlias = HeapAlias(obj, ArrayAlias)

    private fun evalArrayLoad(
        load: Expr.ArrayLoad, state: State
    ) = evalHeapLoad(load.instance, state, ::createArrayAlias)

    private fun evalFieldLoad(
        load: Expr.FieldLoad, state: State
    ) = evalHeapLoad(load.instance, state) { instance -> createFieldAlias(instance, load.field) }

    private fun evalHeapStore(
        instance: RefValue,
        value: ExprOrValue,
        state: State,
        heapAppender: (Int) -> HeapAlias
    ): State {
        val instanceInfo = instance.aliasInfo()
        val obj = state.heapObj(instanceInfo)

        val heapAlias = heapAppender(obj).index()

        var resultState = state
        if (!state.containsMultipleConcreteOrOuterLocations(instanceInfo)) {
            resultState = resultState.remove(heapAlias)
        }

        if (value is RefValue) {
            resultState = resultState.mergeWith(value.aliasInfo().index(), heapAlias)
        }

        return resultState
    }

    private fun evalArrayStore(stmt: Stmt.ArrayStore, state: State): State =
        evalHeapStore(stmt.instance, stmt.value, state, ::createArrayAlias)

    private fun evalFieldStore(stmt: Stmt.FieldStore, state: State): State =
        evalHeapStore(stmt.instance, stmt.value, state) { createFieldAlias(it, stmt.field) }

    private fun State.containsMultipleConcreteOrOuterLocations(instance: AAInfo): Boolean =
        containsMultipleConcreteOrOuterLocations(instance.index(), IntOpenHashSet())

    private fun State.containsMultipleConcreteOrOuterLocations(infoIdx: Int, visited: IntOpenHashSet): Boolean {
        val instances = IntArrayList()
        forEachAliasInSet(infoIdx) { instances.add(it) }
        return instances.containsMultipleConcreteOrOuterLocations(this, visited)
    }

    private fun IntCollection.containsMultipleConcreteOrOuterLocations(
        state: State,
        visited: IntOpenHashSet
    ): Boolean {
        var concrete = 0
        forEachInt { infoIndex ->
            // value depends on itself
            if (infoIndex in visited) {
                return true
            }

            when (val info = aliasManager.getElementUncheck(infoIndex)) {
                // outer
                is CallReturn -> return true

                is HeapAlias -> {
                    val toRollback = this.filterTo(hashSetOf()) { visited.add(it) }

                    val instanceRepr = state.aliasGroupRepr(info.instance)
                    try {
                        if (state.containsMultipleConcreteOrOuterLocations(instanceRepr, visited)) {
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
                    return@forEachInt
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
        val aaInfoToRemove = IntOpenHashSet()

        allElements().forEachInt { info ->
            val element = aliasManager.getElementUncheck(info)
            if (element.isCallLocal(level)) {
                aaInfoToRemove.add(info)
            }
        }

        return this.removeUnsafe(aaInfoToRemove)
    }

    private fun AAInfo.isCallLocal(level: Int): Boolean = when (this) {
        is LocalAlias.Alloc -> false
        is Unknown -> this.ctx.level > level
        is CallReturn -> this.ctx.level > level
        is LocalAlias.SimpleLoc -> loc is Local && loc.ctx.level > level
        is HeapAlias -> false
    }

    private fun RefValue.aliasInfo(): AAInfo = LocalAlias.SimpleLoc(this)

    private fun Value.aliasInfo(): AAInfo? = when (this) {
        is RefValue -> aliasInfo()
        is SimpleValue.Primitive,
        is SimpleValue.RefConst -> null
    }

    private data class AliasSet(val repr: Int)

    private fun aliasSetFromInfo(info: AAInfo): AliasSet = AliasSet(info.index())

    private fun State.remove(element: Int): State {
        val info = aliasManager.getElementUncheck(element)
        if (info is Unknown) return this

        return removeUnsafe(IntOpenHashSet.of(element))
    }

    private fun State.removeOldAndMergeWith(info: AAInfo, other: AAInfo): State =
        removeOldAndMergeWith(info.index(), aliasSetFromInfo(other))

    private fun State.removeOldAndMergeWith(info: Int, alias: AliasSet): State {
        val result = this.remove(info)
        return result.mergeAliasSets(info, alias)
    }

    private fun State.mergeWith(info: Int, other: Int): State =
        mergeAliasSets(info, AliasSet(other))

    private fun State.mergeAliasSets(info: Int, other: AliasSet): State =
        mergeAliasSets(IntOpenHashSet(intArrayOf(info, other.repr)))

    private fun Stmt.Call.cantMutateAliasedHeap(): Boolean {
        if (args.any { it !is SimpleValue.Primitive }) return false
        return method.isStatic || method.isConstructor
    }
}
