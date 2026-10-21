package org.opentaint.dataflow.python.alias

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntCollection
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.opentaint.dataflow.ap.ifds.analysis.alias.AAInfo
import org.opentaint.dataflow.ap.ifds.analysis.alias.AAInfoManager
import org.opentaint.dataflow.ap.ifds.analysis.alias.AnalysisCancellation
import org.opentaint.dataflow.ap.ifds.analysis.alias.AnalysisResult
import org.opentaint.dataflow.ap.ifds.analysis.alias.ContextInfo
import org.opentaint.dataflow.ap.ifds.analysis.alias.HeapAlias
import org.opentaint.dataflow.ap.ifds.analysis.alias.ImmutableState
import org.opentaint.dataflow.ap.ifds.analysis.alias.IntDisjointSets
import org.opentaint.dataflow.ap.ifds.analysis.alias.State
import org.opentaint.dataflow.ap.ifds.analysis.alias.allElements
import org.opentaint.dataflow.python.PIRFlowFunctionUtils
import org.opentaint.dataflow.util.forEachInt
import org.opentaint.ir.api.python.PIRAssign
import org.opentaint.ir.api.python.PIRBinaryExpr
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRDictExpr
import org.opentaint.ir.api.python.PIRExpr
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.ir.api.python.PIRInstruction
import org.opentaint.ir.api.python.PIRListExpr
import org.opentaint.ir.api.python.PIRLoadAttr
import org.opentaint.ir.api.python.PIRReturn
import org.opentaint.ir.api.python.PIRSetExpr
import org.opentaint.ir.api.python.PIRStoreAttr
import org.opentaint.ir.api.python.PIRStoreSubscript
import org.opentaint.ir.api.python.PIRStringExpr
import org.opentaint.ir.api.python.PIRSubscriptExpr
import org.opentaint.ir.api.python.PIRTupleExpr
import org.opentaint.ir.api.python.PIRValue
import org.opentaint.ir.api.python.PythonNames

class PIRDSUAliasAnalysis(
    private val methodCallResolver: CallResolver,
    private val cancellation: AnalysisCancellation,
) {
    private val aliasManager = AAInfoManager()
    private val dsuMergeStrategy = DsuMergeStrategy(aliasManager)

    private val selfField = AliasAccessor.Field(PythonNames.BOUND_SELF_ATTR)

    private class DsuMergeStrategy(private val manager: AAInfoManager) : IntDisjointSets.RankStrategy {
        override fun compare(a: Int, b: Int): Int =
            manager.getElementUncheck(a).compareTo(manager.getElementUncheck(b))
    }

    private fun AAInfo.index(): Int = aliasManager.getOrAdd(this)

    fun analyze(pig: PIRInstGraph): AnalysisResult {
        val initialState = State.empty(aliasManager, dsuMergeStrategy)
        val rootCall = CallTreeNode(ContextInfo.rootContext, RootInstEvalContext)
        val analysisState = GraphAnalysisState(pig.statements.size, rootCall)

        analyze(pig, initialState, analysisState)

        return AnalysisResult(aliasManager, analysisState.stateBeforeStmt, analysisState.stateAfterStmt)
    }

    private fun analyze(pig: PIRInstGraph, initialState: ImmutableState, analysisState: GraphAnalysisState) {
        simulatePIG(
            pig, initialState, analysisState.stateBeforeStmt, analysisState.stateAfterStmt,
            { i, s -> eval(i, s, analysisState.call) },
            { s -> merge(s) },
        )
    }

    private fun merge(states: Int2ObjectMap<ImmutableState?>): ImmutableState =
        State.merge(aliasManager, dsuMergeStrategy, states.values.filterNotNull())

    private fun eval(inst: PIRInstruction, state: ImmutableState, callFrame: CallTreeNode): ImmutableState {
        cancellation.checkpoint()
        return eval(inst, state.mutableCopy(), callFrame).asImmutable()
    }

    private fun eval(inst: PIRInstruction, state: State, callFrame: CallTreeNode): State = when (inst) {
        is PIRAssign -> handleAssign(inst, callFrame, state)
        is PIRLoadAttr -> handleLoadAttr(inst, callFrame, state)
        is PIRStoreAttr -> handleStoreAttr(inst, callFrame, state)
        is PIRStoreSubscript -> handleStoreSubscript(inst, callFrame, state)
        is PIRCall -> evalCall(inst, state, callFrame)
        else -> state
    }

    private fun evalCall(inst: PIRCall, state: State, callFrame: CallTreeNode): State {
        val resolved = callFrame.resolveCall(inst, methodCallResolver)
        if (resolved != null) {
            evalResolvedCall(inst, state, callFrame, resolved)?.let { return it }
        }

        val target = inst.target ?: return state
        val lValue = callFrame.instEvalCtx.createLocal(target.index)
        val info = aliasSetFromInfo(CallReturn(inst.location.index, callFrame.ctx))
        return state.removeOldAndMergeWith(lValue.aliasInfo().index(), info)
    }

    private fun evalResolvedCall(
        inst: PIRCall,
        state: State,
        callFrame: CallTreeNode,
        methods: Map<PIRFunction, ResolvedCallMethod>,
    ): State? {
        val stateBefore = state.asImmutable()
        val callerLValue = inst.target?.let { callFrame.instEvalCtx.createLocal(it.index) }
        val calleeRef = callFrame.instEvalCtx.refValue(inst.callee)
        val statesAfterCall = mutableListOf<ImmutableState>()

        for ((method, resolvedMethod) in methods) {
            val calleeInitial = bindReceiver(stateBefore, calleeRef, method, resolvedMethod)
            analyze(resolvedMethod.graph, calleeInitial, resolvedMethod.state)
            statesAfterCall += resolvedMethod.state.mapCallFinalStates(
                resolvedMethod.graph, callerLValue, callFrame.ctx.level
            )
        }

        if (statesAfterCall.isEmpty()) return null
        if (statesAfterCall.size == 1) return statesAfterCall.first().mutableCopy()
        return State.merge(aliasManager, dsuMergeStrategy, statesAfterCall)
    }

    private fun isConstructor(method: PIRFunction): Boolean =
        method.name == PythonNames.INIT_METHOD && method.enclosingClass != null

    private fun bindReceiver(
        stateBefore: ImmutableState,
        calleeRef: RefValue?,
        method: PIRFunction,
        resolvedMethod: ResolvedCallMethod,
    ): ImmutableState {
        if (PIRFlowFunctionUtils.implicitParamOffset(method) != 1 || calleeRef == null) return stateBefore
        if (isConstructor(method)) return stateBefore
        val selfSlot = RefValue.Local(-1, resolvedMethod.state.call.ctx)
        val state = stateBefore.mutableCopy()
        val receiverHeap = createFieldAlias(state.heapObj(calleeRef.aliasInfo()), selfField)
        return state.mergeWith(selfSlot.aliasInfo().index(), receiverHeap.index()).asImmutable()
    }

    private fun handleAssign(inst: PIRAssign, callFrame: CallTreeNode, state: State): State {
        val lValue = callFrame.instEvalCtx.createLocal(inst.target.index)
        return state.removeOldAndMergeWith(lValue.aliasInfo().index(), aliasOfExpr(inst.expr, inst, callFrame, state))
    }

    private fun aliasOfExpr(expr: PIRExpr, inst: PIRInstruction, callFrame: CallTreeNode, state: State): AliasSet {
        val freshAlloc = { aliasSetFromInfo(LocalAlias.Alloc(inst.location.index, callFrame.ctx)) }
        return when (expr) {
            is PIRValue -> callFrame.instEvalCtx.refValue(expr)
                ?.let { aliasSetFromInfo(it.aliasInfo()) } ?: freshAlloc()

            is PIRSubscriptExpr -> callFrame.instEvalCtx.refValue(expr.obj)
                ?.let { evalHeapLoad(it, state, ::createArrayAlias) }
                ?: aliasSetFromInfo(Unknown(inst.location.index, callFrame.ctx))

            is PIRListExpr, is PIRTupleExpr, is PIRDictExpr, is PIRSetExpr,
            is PIRBinaryExpr, is PIRStringExpr -> freshAlloc()

            else -> aliasSetFromInfo(Unknown(inst.location.index, callFrame.ctx))
        }
    }

    private fun evalHeapLoad(instance: RefValue, state: State, heapAppender: (Int) -> HeapAlias): AliasSet {
        val obj = state.heapObj(instance.aliasInfo())
        return aliasSetFromInfo(heapAppender(obj))
    }

    private fun State.heapObj(instance: AAInfo): Int = aliasGroupId(instance.index())

    private fun createFieldAlias(obj: Int, field: AliasAccessor.Field): HeapAlias = HeapAlias(obj, FieldAlias(field))
    private fun createArrayAlias(obj: Int): HeapAlias = HeapAlias(obj, ArrayAlias)

    private fun handleLoadAttr(inst: PIRLoadAttr, callFrame: CallTreeNode, state: State): State {
        val lValue = callFrame.instEvalCtx.createLocal(inst.target.index)
        val obj = callFrame.instEvalCtx.refValue(inst.obj)
        val rhs = if (obj != null) {
            evalHeapLoad(obj, state) { createFieldAlias(it, AliasAccessor.Field(inst.attribute)) }
        } else {
            aliasSetFromInfo(Unknown(inst.location.index, callFrame.ctx))
        }
        var result = state.removeOldAndMergeWith(lValue.aliasInfo().index(), rhs)

        if (obj != null) {
            result = evalHeapStore(isFieldStore = true, lValue, aliasSetFromInfo(obj.aliasInfo()), result) {
                createFieldAlias(it, selfField)
            }
        }
        return result
    }

    private fun evalHeapStore(
        isFieldStore: Boolean,
        instance: RefValue,
        value: AliasSet,
        state: State,
        heapAppender: (Int) -> HeapAlias,
    ): State {
        val instanceInfo = instance.aliasInfo()
        val obj = state.heapObj(instanceInfo)
        val heapAlias = heapAppender(obj).index()

        var resultState = state
        if (isFieldStore && !state.containsMultipleConcreteOrOuterLocations(instanceInfo)) {
            resultState = resultState.remove(heapAlias)
        }

        return resultState.mergeWith(value.repr, heapAlias)
    }

    private fun handleStoreSubscript(inst: PIRStoreSubscript, callFrame: CallTreeNode, state: State): State {
        val obj = callFrame.instEvalCtx.refValue(inst.obj) ?: return state
        val value = storeValueAlias(inst.value, inst, callFrame)
        return evalHeapStore(isFieldStore = false, obj, value, state, ::createArrayAlias)
    }

    private fun handleStoreAttr(inst: PIRStoreAttr, callFrame: CallTreeNode, state: State): State {
        val obj = callFrame.instEvalCtx.refValue(inst.obj) ?: return state
        val value = storeValueAlias(inst.value, inst, callFrame)
        return evalHeapStore(isFieldStore = true, obj, value, state) {
            createFieldAlias(it, AliasAccessor.Field(inst.attribute))
        }
    }

    private fun storeValueAlias(value: PIRValue, inst: PIRInstruction, callFrame: CallTreeNode): AliasSet =
        callFrame.instEvalCtx.refValue(value)
            ?.let { aliasSetFromInfo(it.aliasInfo()) }
            ?: aliasSetFromInfo(LocalAlias.Alloc(inst.location.index, callFrame.ctx))

    private fun State.containsMultipleConcreteOrOuterLocations(instance: AAInfo): Boolean =
        containsMultipleConcreteOrOuterLocations(instance.index(), IntOpenHashSet())

    private fun State.containsMultipleConcreteOrOuterLocations(infoIdx: Int, visited: IntOpenHashSet): Boolean {
        val instances = IntArrayList()
        forEachAliasInSet(infoIdx) { instances.add(it) }
        return instances.containsMultipleConcreteOrOuterLocations(this, visited)
    }

    private fun IntCollection.containsMultipleConcreteOrOuterLocations(state: State, visited: IntOpenHashSet): Boolean {
        var concrete = 0
        forEachInt { infoIndex ->
            if (infoIndex in visited) return true

            when (val info = aliasManager.getElementUncheck(infoIndex)) {
                is CallReturn -> return true

                is HeapAlias -> {
                    val toRollback = this.filterTo(hashSetOf()) { visited.add(it) }
                    val instanceRepr = state.aliasGroupRepr(info.instance)
                    try {
                        if (state.containsMultipleConcreteOrOuterLocations(instanceRepr, visited)) return true
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

                else -> error("Impossible aa-info")
            }
        }
        return concrete > 1
    }

    private fun RefValue.isOuter(): Boolean = when (this) {
        is RefValue.Local -> false
        is RefValue.Arg -> true
    }

    private fun GraphAnalysisState.mapCallFinalStates(
        graph: PIRInstGraph,
        callerLValue: RefValue?,
        level: Int,
    ): List<ImmutableState> =
        graph.statements.filterIsInstance<PIRReturn>().mapNotNull { retInst ->
            val finalState = stateAfterStmt[retInst.location.index] ?: return@mapNotNull null
            val retVal = retInst.value?.let { call.instEvalCtx.refValue(it) }
            finalState.createStateAfterCall(callerLValue, retVal, level)
        }

    private fun ImmutableState.createStateAfterCall(lValue: RefValue?, retVal: RefValue?, level: Int): ImmutableState {
        var state = mutableCopy()
        if (lValue != null && retVal != null) {
            state = state.removeOldAndMergeWith(lValue.aliasInfo(), retVal.aliasInfo())
        }
        return state.removeCallLocals(level).asImmutable()
    }

    private fun State.removeCallLocals(level: Int): State {
        val toRemove = IntOpenHashSet()
        allElements().forEachInt { info ->
            if (aliasManager.getElementUncheck(info).isCallLocal(level)) toRemove.add(info)
        }
        return removeUnsafe(toRemove)
    }

    private fun AAInfo.isCallLocal(level: Int): Boolean = when (this) {
        is LocalAlias.Alloc -> false
        is Unknown -> ctx.level > level
        is CallReturn -> ctx.level > level
        is LocalAlias.SimpleLoc -> { val l = loc; l is RefValue.Local && l.ctx.level > level }
        is HeapAlias -> false
        else -> error("Impossible aa-info")
    }

    private fun RefValue.aliasInfo(): AAInfo = LocalAlias.SimpleLoc(this)

    private data class AliasSet(val repr: Int)

    private fun aliasSetFromInfo(info: AAInfo): AliasSet = AliasSet(info.index())

    private fun State.remove(element: Int): State {
        val info = aliasManager.getElementUncheck(element)
        if (info is Unknown) return this
        return removeUnsafe(IntOpenHashSet.of(element))
    }

    private fun State.removeOldAndMergeWith(info: AAInfo, other: AAInfo): State =
        removeOldAndMergeWith(info.index(), aliasSetFromInfo(other))

    private fun State.removeOldAndMergeWith(info: Int, alias: AliasSet): State =
        this.remove(info).mergeAliasSets(info, alias)

    private fun State.mergeWith(info: Int, other: Int): State = mergeAliasSets(info, AliasSet(other))

    private fun State.mergeAliasSets(info: Int, other: AliasSet): State =
        mergeAliasSets(IntOpenHashSet(intArrayOf(info, other.repr)))
}
