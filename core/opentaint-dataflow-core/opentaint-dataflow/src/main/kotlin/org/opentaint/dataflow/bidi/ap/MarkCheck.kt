package org.opentaint.dataflow.bidi.ap

sealed interface MarkCheck {
    data object None : MarkCheck
    class Request(val mark: TaintMark) : MarkCheck
    class Holds(val facts: Facts) : MarkCheck
}

/** A literal selects full input leaves. A concrete accepted input keeps its layer. */
internal fun markCheckFacts(m: ApManager, c: Facts, p: Pattern, mode: ApMode): MarkCheck {
    m.cancellation.checkpoint()
    val t = (p.fact.mark as MarkSlot.Concrete).mark
    if (c.base != p.fact.base) return MarkCheck.None
    val gate = MarkGate(m, p.fact.mark, p.fact.mark)
    return when (c) {
        is Reach -> if (t == TaintMark.ZERO) MarkCheck.Holds(c) else MarkCheck.None
        is FlowTree -> {
            if (!flowOverlaps(m, c, p)) return MarkCheck.None
            val request = gate.flowRequest(c.markExclusion) ?: return MarkCheck.None
            if (mode.run1) MarkCheck.Request(request) else MarkCheck.None
        }
        is TaintTree -> {
            val hit = taintOverlap(m, c.root, p, c.exclusion) ?: return MarkCheck.None
            val withT = m.taintAlg.mapLeaves(hit, m.cancellation) { leaf ->
                m.taintLeaves(gate.taintMarks(leaf.exact) ?: MarkSet.EMPTY,
                    gate.taintMarks(leaf.any) ?: MarkSet.EMPTY)
            } ?: return MarkCheck.None
            MarkCheck.Holds(m.taintTree(c.base, c.layer, withT, c.exclusion))
        }
    }
}

private fun flowOverlaps(m: ApManager, c: FlowTree, p: Pattern): Boolean {
    val path = p.fact.path.toIntArray()
    var hit = false
    val u = c.root.walk(path) { d, n ->
        m.cancellation.checkpoint()
        if (!n.leaf.isEmpty && (c.layer == Layer.DEMAND || c.exclusion.admits(path[d]))) hit = true
    }
    return hit || (u != null && (!u.leaf.isEmpty || (p.fact.tail.isAny && u.accessorData != null)))
}

private fun taintOverlap(m: ApManager, c: TaintNode, p: Pattern, e: ExclusionSet): TaintNode? {
    val path = p.fact.path.toIntArray()
    val spine = ArrayList<TaintLeaves>()
    val u = c.walk(path) { d, n ->
        m.cancellation.checkpoint()
        spine += if (e.admits(path[d])) m.taintLeaves(MarkSet.EMPTY, n.leaf.any) else TaintLeaves.EMPTY
    }
    val tip = u?.let { if (p.fact.tail.isAny) it else m.taintAlg.leafNode(it.leaf) }
    return m.taintAlg.chain(m, path, spine, tip)
}

internal fun withoutMarksPart(m: ApManager, c: Facts, marks: MarkSet): Facts? {
    m.cancellation.checkpoint()
    if (marks.isEmpty || c !is TaintTree) return c
    return m.taintAlg.mapLeaves(c.root, m.cancellation) {
        m.taintLeaves(it.exact - marks, it.any - marks)
    }?.let { if (it === c.root) c else c.withRoot(it) }
}
