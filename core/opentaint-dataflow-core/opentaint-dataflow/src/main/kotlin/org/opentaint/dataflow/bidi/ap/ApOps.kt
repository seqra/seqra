package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.RequestKind
import org.opentaint.dataflow.bidi.store.RequestMarkSelection

/** Shared facade. All walk state is local to the call. */
class ApOps(val manager: ApManager) {
    fun applyEdge(c: Facts, premise: PremiseKey, e: PathEdge, statementEdge: Boolean,
                  mode: ApMode, out: ApOut, may: Boolean = false) {
        val ce = compiled(e)
        applyCompiledEdge(c, ce, may, staticIdentityDepth(c, premise, ce, statementEdge, mode), mode, out)
    }

    internal fun compiled(e: PathEdge): CompiledEdge {
        val old = e.compiled
        if (old != null && (old.toPath == null || old.toPath.manager === manager)) return old
        return manager.compile(e).also { e.compiled = it }
    }

    internal fun applyCompiledEdge(c: Facts, ce: CompiledEdge, edgeDemand: Boolean,
                                   staticAt: Int, mode: ApMode, out: ApOut) {
        manager.cancellation.checkpoint()
        if (c.base != ce.fromBase) return
        val app = EdgeApplication(manager, ce, edgeDemand, mode, out)
        when (c) {
            is Reach -> app.reach(c)
            is FlowTree -> app.flow(c, staticAt)
            is TaintTree -> app.taint(c)
        }
        app.results.flush(out)
    }

    fun satisfying(a: Facts, j: InitialAp, mode: ApMode, record: Boolean = false): Facts? =
        satisfying(a, j.toPattern(), mode, record)

    fun satisfying(a: Facts, j: Pattern, mode: ApMode, record: Boolean = false): Facts? =
        satisfyingPart(manager, a, j, mode, record)

    fun applySummary(a: Facts, j: InitialAp, g: Facts, mode: ApMode, out: ApOut) =
        applySummaryPart(this, a, j, g, mode, out)

    fun applyRecordLeaf(a: Facts, view: RecordLeafView, mode: ApMode, out: ApOut) =
        applyRecordLeafPart(this, a, view, mode, out)

    fun applyCombination(parts: List<Pair<Facts, InitialAp>>, g: TaintTree, mode: ApMode, out: ApOut) =
        applyCombinationPart(manager, parts, g, mode, out)

    /** Used only after E6's full matching and TAINT guards. Caller layers remain in the resume context. */
    internal fun applyMatchedCombination(linkLayers: List<Layer>, g: TaintTree, out: ApOut) =
        applyMatchedCombinationPart(manager, linkLayers, g, out)

    fun without(c: Facts, part: Facts): Facts? = withoutPart(manager, c, part)
    fun reverse(e: PathEdge): PathEdge? = revEdge(e)
    fun leaves(f: Facts): Sequence<Pattern> = leafViews(manager, f)
    fun leavesNear(f: Facts, p: IntArray): Sequence<Pattern> = nearLeafViews(manager, f, p)

    fun clean(c: Facts, premise: PremiseKey, cl: Cleaner, mode: ApMode, out: ApOut) =
        cleanFacts(manager, c, premise, cl, mode, out)

    fun filter(c: Facts, f: TypeFilter): Facts? = filterFacts(manager, c, f)
    fun limit(c: Facts, fieldLimit: Int, out: ApOut) = limitFacts(manager, c, fieldLimit, out)
    fun checkMark(c: Facts, p: Pattern, mode: ApMode): MarkCheck = markCheckFacts(manager, c, p, mode)
    fun withoutMarks(c: Facts, marks: MarkSet): Facts? = withoutMarksPart(manager, c, marks)

    fun targetTree(target: PathFact, layer: Layer): Facts {
        check(target.mark is MarkSlot.Concrete && target.tail != Tail.STAR) { "W7: a target must be concrete and exact or any" }
        return manager.factsOf(target, ExclusionSet.Empty, layer)
    }

    fun zero(layer: Layer): Reach = Reach.of(layer)
    fun startFact(i: InitialAp): Facts = initialFacts(manager, i)
    fun policy(added: Facts): InitialAp = policyInitial(manager, added)
    fun emit(d: DemandPattern, added: Facts): List<InitialAp> = emitInitials(this, d, added)
    fun restrictReference(j: InitialAp, g: Facts, d: DemandPattern): List<Conclusion> = restrictRaw(this, j, g, d)
    fun restrict(j: InitialAp, g: Facts, d: DemandPattern): List<Facts> = restrictFacts(this, j, g, d)
    fun demandPart(premise: PremiseKey, g: Facts, direction: Direction): Facts? =
        selectDemand(manager, premise, g, direction)
    fun requestAction(i: InitialAp, kind: RequestKind, a: Pattern, caller: CallerRef): RequestAction =
        actOnRequest(manager, i, kind, a, caller)
    fun requestGroup(i: InitialAp, marks: RequestMarkSelection, facts: Facts, caller: CallerRef): RequestGroupCursor =
        RequestGroupCursor(manager, i, marks, facts, caller)
}

private fun staticIdentityDepth(c: Facts, premise: PremiseKey, ce: CompiledEdge,
                                statementEdge: Boolean, mode: ApMode): Int {
    if (c !is FlowTree || c.layer != Layer.NORMAL || !mode.run1 || !statementEdge) return -1
    if (c.base != STATIC || ce.fromBase != STATIC) return -1
    val i = premise as? InitialAp ?: return -1
    if (i.base != STATIC || i.tail != Tail.STAR || i.mark != MarkSlot.STAR) return -1
    val q = i.pathData
    if (!(q.isEmpty() || (q.size == 1 && q[0].isClass()))) return -1
    if (ce.fromPathData.size <= q.size || !ce.fromPathData.startsWith(q)) return -1
    return q.size
}
