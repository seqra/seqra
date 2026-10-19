package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.ap.ifds.AccessPathBase

/** The two annotations of a TAINT leaf and the single annotation of a FLOW flag. */
private class SummaryTails<P : TrieLeaf>(
    val exclusion: ExclusionSet,
    val anyExclusion: ExclusionSet = ExclusionSet.Empty,
    val select: (P, star: Boolean, any: Boolean, exact: Boolean) -> P,
)

private fun flowTails(a: FlowTree) = SummaryTails<FlowLeaf>(a.exclusion) { p, star, any, _ ->
    if (if (a.tail == Tail.STAR) star else any) p else FlowLeaf.NONE
}

private fun taintTails(m: ApManager, a: TaintTree) =
    SummaryTails<TaintLeaves>(ExclusionSet.Empty, a.exclusion) { p, _, any, exact ->
        m.taintLeaves(if (exact) p.exact else MarkSet.EMPTY, if (any) p.any else MarkSet.EMPTY)
    }

/** Preserve full selected leaves and their input annotations. Record demotion occurs after return actions. */
internal fun satisfyingPart(m: ApManager, a: Facts, j: Pattern, mode: ApMode, record: Boolean): Facts? {
    m.cancellation.checkpoint()
    if (a.base != j.fact.base) return null
    check(j.fact.tail != Tail.ANY_TAINT || (mode.restricted && mode.direction == Direction.FORWARD)) {
        "W8 (c): a must-premise is forward restricted"
    }
    val below = record || mode.run1 || (mode.restricted && j.fact.mark is MarkSlot.Star)
    val inside = record || mode.restricted || mode.run1
    return when (a) {
        is Reach -> a.takeIf { j.fact.base == AccessPathBase.Zero }
        is FlowTree -> if (j.fact.mark !is MarkSlot.Star) null
            else summaryPart(m, a.root, FlowAlgebra, flowTails(a), j, below, inside)
                ?.let { m.flowTree(a.base, a.layer, a.exclusion, a.markExclusion, it, a.tail) }
        is TaintTree -> summaryPart(m, a.root, m.taintAlg, taintTails(m, a), j, below, inside)
            ?.let { markSubPart(m, it, j, mode) }
            ?.let { m.taintTree(a.base, if (mode.markMode == MarkMode.GROUP) Layer.DEMAND else a.layer, it, a.exclusion, a.anyTail) }
    }
}

private fun markSubPart(m: ApManager, n: TaintNode, j: Pattern, mode: ApMode): TaintNode? {
    val gate = MarkGate(m, j.fact.mark, MarkSlot.STAR, mode)
    return m.taintAlg.mapLeaves(n, m.cancellation) { p ->
        m.taintLeaves(gate.taintMarks(p.exact) ?: MarkSet.EMPTY, gate.taintMarks(p.any) ?: MarkSet.EMPTY)
    }
}

private fun <P : TrieLeaf> summaryPart(
    m: ApManager, root: TrieNode<P>, alg: LeafAlgebra<P>, t: SummaryTails<P>, j: Pattern,
    below: Boolean, inside: Boolean,
): TrieNode<P>? {
    val b = if (below) applicablePart(m, root, alg, t, j) else null
    val i = if (inside) insidePart(m, root, alg, t, j) else null
    return if (b == null) i else if (i == null) b else alg.mergeAdd(b, i, m.cancellation)
}

private fun <P : TrieLeaf> applicablePart(
    m: ApManager, root: TrieNode<P>, alg: LeafAlgebra<P>, t: SummaryTails<P>, j: Pattern,
): TrieNode<P>? {
    val path = j.fact.path.toIntArray()
    val u = root.walk(path) { _, _ -> m.cancellation.checkpoint() } ?: return null
    m.cancellation.checkpoint()
    val ej = j.exclusion
    val anyAt = ej.isSubsetOf(t.anyExclusion)
    val at = when (j.fact.tail) {
        Tail.STAR -> t.select(u.leaf, ej.isSubsetOf(t.exclusion), anyAt, true)
        Tail.EXACT -> t.select(u.leaf, false, false, true)
        Tail.ANY_TAINT -> t.select(u.leaf, false, anyAt, false)
        Tail.ANY -> t.select(u.leaf, false, true, false)
    }
    val kids = when (j.fact.tail) {
        Tail.STAR -> alg.retainChildren(u, m.cancellation) { ej.admits(it) }
        Tail.EXACT -> null
        Tail.ANY_TAINT -> alg.retainChildren(u, m.cancellation) { ej.admits(it) }
            ?.let { k -> alg.mapLeaves(k, m.cancellation) { t.select(it, false, true, false) } }
        Tail.ANY -> alg.retainChildren(u, m.cancellation) { true }
            ?.let { k -> alg.mapLeaves(k, m.cancellation) { t.select(it, false, true, false) } }
    }
    return alg.withLeaf(kids, at)?.let { alg.prepend(m, m.path(path), it) }
}

private fun <P : TrieLeaf> insidePart(
    m: ApManager, root: TrieNode<P>, alg: LeafAlgebra<P>, t: SummaryTails<P>, j: Pattern,
): TrieNode<P>? {
    val path = j.fact.path.toIntArray()
    val spine = ArrayList<P>()
    val u = root.walk(path) { d, n ->
        m.cancellation.checkpoint()
        spine += t.select(n.leaf, t.exclusion.admits(path[d]), t.anyExclusion.admits(path[d]), false)
    }
    m.cancellation.checkpoint()
    if (u != null) spine += t.select(u.leaf,
        when (j.fact.tail) {
            Tail.EXACT -> true
            Tail.STAR -> t.exclusion.isSubsetOf(j.exclusion)
            Tail.ANY, Tail.ANY_TAINT -> t.exclusion == ExclusionSet.Empty
        },
        j.fact.tail == Tail.EXACT || t.anyExclusion.isSubsetOf(j.exclusion),
        j.fact.tail == Tail.EXACT)
    return alg.chain(m, path, spine)
}

/** One edge for each tail/mark kind, followed by a graft into every path having that kind. */
internal fun applySummaryPart(ops: ApOps, a: Facts, j: InitialAp, g: Facts, mode: ApMode, out: ApOut) {
    val m = ops.manager
    m.cancellation.checkpoint()
    check(a !is FlowTree || g is FlowTree) { "a FLOW fact satisfies only a `*` premise, whose summaries are FLOW" }
    for (kind in leafKinds(m, g, j)) {
        m.cancellation.checkpoint()
        val one = CollectingOut()
        ops.applyCompiledEdge(a, kind.edge, g.layer == Layer.DEMAND, -1, mode, one)
        // F78: applying an excluded abstract target preserves the interest on
        // the caller's original slot before publishing its excluded result.
        for (mark in one.markRequests) {
            m.cancellation.checkpoint()
            out.markRequest(mark)
        }
        for (r in one.results) {
            m.cancellation.checkpoint()
            out.result(kind.graft(r))
        }
    }
}

/** A transient native record uses its Pattern directly, retaining its own exclusions. */
internal fun applyRecordLeafPart(ops: ApOps, a: Facts, view: RecordLeafView, mode: ApMode, out: ApOut) {
    val m = ops.manager
    m.cancellation.checkpoint()
    val edge = ops.compiled(summaryEdge(view.premise, view.result))
    val one = CollectingOut()
    ops.applyCompiledEdge(a, edge, view.result.demand, -1, mode, one)
    for (mark in one.markRequests) {
        m.cancellation.checkpoint()
        out.markRequest(mark)
    }
    for (r in one.results) {
        m.cancellation.checkpoint()
        out.result(r)
    }
}

private class SummaryLeafKind(val edge: CompiledEdge, val graft: (Facts) -> Facts)

private fun leafKinds(m: ApManager, g: Facts, j: InitialAp): List<SummaryLeafKind> {
    val jOwn = j.tail == Tail.ANY_TAINT
    fun edge(tail: Tail, mark: MarkSlot, exclusion: ExclusionSet, toExclusion: ExclusionSet = ExclusionSet.Empty) =
        CompiledEdge(j.base, j.pathData, j.tail, j.mark, g.base, null, tail, mark,
            m.intern(if (jOwn) ExclusionSet.Empty else exclusion),
            m.intern(if (jOwn) j.exclusion else ExclusionSet.Empty), m.intern(toExclusion))
    return when (g) {
        is Reach -> listOf(SummaryLeafKind(edge(Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO), ExclusionSet.Empty)) { it })
        is FlowTree -> listOf(
            if (g.tail == Tail.STAR)
                SummaryLeafKind(edge(Tail.STAR, MarkSlot.Star(g.markExclusion), j.exclusion.union(g.exclusion))) {
                    graftSummary(m, g.root, { p -> p == FlowLeaf.LEAF }, it)
                }
            else SummaryLeafKind(edge(Tail.ANY, MarkSlot.Star(g.markExclusion), j.exclusion)) {
                graftSummary(m, g.root, { p -> p == FlowLeaf.LEAF }, it)
            })
        is TaintTree -> {
            val all = m.taintAlg.foldAll(g.root, m.cancellation)
            val anyTail = g.anyTail
            val kinds = ArrayList<SummaryLeafKind>()
            for (id in all.exact.ids) {
                m.cancellation.checkpoint()
                val mark = TaintMark(id)
                kinds += SummaryLeafKind(edge(Tail.EXACT, MarkSlot.Concrete(mark), j.exclusion)) {
                    graftSummary(m, g.root, { p -> mark in p.exact }, it)
                }
            }
            for (id in all.any.ids) {
                m.cancellation.checkpoint()
                val mark = TaintMark(id)
                kinds += SummaryLeafKind(edge(anyTail, MarkSlot.Concrete(mark), j.exclusion, g.exclusion)) {
                    graftSummary(m, g.root, { p -> mark in p.any }, it)
                }
            }
            kinds
        }
    }
}

private fun <G : TrieLeaf> graftSummary(m: ApManager, g: TrieNode<G>, occurs: (G) -> Boolean, r: Facts): Facts =
    when (r) {
        is Reach -> r
        is FlowTree -> r.withRoot(checkNotNull(FlowAlgebra.graft(g, occurs, r.root, m.cancellation)))
        is TaintTree -> r.withRoot(checkNotNull(m.taintAlg.graft(g, occurs, r.root, m.cancellation)))
    }

/** Matching is supplied by the caller. W7 makes each complete concrete member return the same target. */
internal fun applyCombinationPart(
    manager: ApManager, parts: List<Pair<Facts, InitialAp>>, g: TaintTree, mode: ApMode, out: ApOut,
) {
    manager.cancellation.checkpoint()
    check(parts.size >= 2)
    val layers = ArrayList<Layer>(parts.size)
    for ((part, _) in parts) {
        manager.cancellation.checkpoint()
        check(part is TaintTree)
        layers += part.layer
    }
    applyMatchedCombinationPart(manager, layers, g, out)
}

/** E6's full member matcher has checked TAINT inputs. Their roots do not affect this transfer. */
internal fun applyMatchedCombinationPart(
    manager: ApManager, linkLayers: List<Layer>, g: TaintTree, out: ApOut,
) {
    manager.cancellation.checkpoint()
    check(linkLayers.size >= 2)
    var layer = g.layer
    for (linkLayer in linkLayers) {
        manager.cancellation.checkpoint()
        if (linkLayer == Layer.DEMAND) layer = Layer.DEMAND
    }
    out.result(manager.taintTree(g.base, layer, g.root, g.exclusion, g.anyTail))
}

/** Remove identical annotated leaves; preserve the other leaves without record demotion. */
internal fun withoutPart(manager: ApManager, c: Facts, part: Facts): Facts? {
    manager.cancellation.checkpoint()
    return when {
        c.base != part.base -> c
        c is TaintTree && part is TaintTree ->
            if (c.layer != part.layer || (part.root.hasAny &&
                    (part.exclusion != c.exclusion || part.anyTail != c.anyTail))) c
            else manager.taintAlg.minusNode(c.root, part.root, manager.cancellation)
                ?.let { if (it === c.root) c else c.withRoot(it) }
        c.groupKey != part.groupKey -> c
        c is Reach -> if (part is Reach && part.layer == c.layer) null else c
        c is FlowTree && part is FlowTree -> FlowAlgebra.minusNode(c.root, part.root, manager.cancellation)
            ?.let { if (it === c.root) c else c.withRoot(it) }
        else -> c
    }
}
