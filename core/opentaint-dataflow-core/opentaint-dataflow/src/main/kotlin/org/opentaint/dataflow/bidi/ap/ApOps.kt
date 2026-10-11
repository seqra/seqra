package org.opentaint.dataflow.bidi.ap

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

    fun without(c: Facts, part: Facts): Facts? = withoutPart(manager, c, part)
    fun reverse(e: PathEdge): PathEdge? = revEdge(e)
    fun leaves(f: Facts): Sequence<Pattern> = leafViews(manager, f)
    fun leavesNear(f: Facts, p: IntArray): Sequence<Pattern> = nearLeafViews(manager, f, p)
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
