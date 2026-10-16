package org.opentaint.dataflow.bidi.ap

sealed interface MarkCheck {
    data object None : MarkCheck
    class Request(val mark: TaintMark) : MarkCheck
    class Holds(val facts: Facts) : MarkCheck
}

/** A literal selects full input leaves. A concrete accepted input keeps its layer. */
internal fun markCheckFacts(m: ApManager, c: Facts, p: Pattern, mode: ApMode,
                            unchangedStaticInitial: InitialAp? = null): MarkCheck {
    m.cancellation.checkpoint()
    check(unchangedStaticInitial == null || unchangedStaticInitial.manager === m) {
        "The ordinary static gate must use this AP manager"
    }
    val t = (p.fact.mark as MarkSlot.Concrete).mark
    if (c.base != p.fact.base) return MarkCheck.None
    val gate = MarkGate(m, p.fact.mark, p.fact.mark)
    return when (c) {
        is Reach -> if (t == TaintMark.ZERO) MarkCheck.Holds(c) else MarkCheck.None
        is FlowTree -> {
            val unchanged = if (mode.run1) unchangedStaticFlowPath(c, unchangedStaticInitial) else null
            if (!flowRequests(m, c, p, unchanged)) return MarkCheck.None
            val request = gate.flowRequest(c.markExclusion) ?: return MarkCheck.None
            if (mode.run1) MarkCheck.Request(request) else MarkCheck.None
        }
        is TaintTree -> {
            val hit = taintOverlap(m, c.root, p, c.exclusion) ?: return MarkCheck.None
            val withT = m.taintAlg.mapLeaves(hit, m.cancellation) { leaf ->
                m.taintLeaves(gate.taintMarks(leaf.exact) ?: MarkSet.EMPTY,
                    gate.taintMarks(leaf.any) ?: MarkSet.EMPTY)
            } ?: return MarkCheck.None
            MarkCheck.Holds(m.taintTree(c.base, c.layer, withT, c.exclusion, c.anyTail))
        }
    }
}

/** Null preserves scalar behavior. A supplied initial certifies installed catalog coverage. */
internal fun unchangedStaticFlowPath(c: FlowTree, initial: InitialAp?): IntArray? {
    if (initial == null || initial.base != STATIC || c.base != STATIC || initial.tail != Tail.STAR ||
        initial.mark != MarkSlot.STAR || c.tail != Tail.STAR || c.exclusion != initial.exclusion ||
        !c.markExclusion.isEmpty) return null
    return initial.pathData
}

/** The path arrays are borrowed; this check creates no Pattern, Facts or AP node. */
internal fun sameFlowLeaf(m: ApManager, initial: IntArray?, prefix: IntArray, length: Int): Boolean {
    if (initial == null || initial.size != length) return false
    for (index in 0 until length) {
        if (index and 255 == 0) m.cancellation.checkpoint()
        if (initial[index] != prefix[index]) return false
    }
    return true
}

/** Is there a flag other than the started initial in this eligible subtree?
 *  Nonempty sibling branches prove a changed flag without enumerating the subtree. */
internal fun changedFlowSubtree(m: ApManager, root: FlowNode, prefix: IntArray,
                                initial: IntArray?): Boolean {
    if (root.isEmpty) return false
    if (initial == null || initial.size < prefix.size) return true
    for (index in prefix.indices) {
        if (index and 255 == 0) m.cancellation.checkpoint()
        if (initial[index] != prefix[index]) return true
    }
    var node = root
    for (index in prefix.size until initial.size) {
        m.cancellation.checkpoint()
        if (!node.leaf.isEmpty) return true
        val accessors = node.accessorData ?: return false
        if (accessors.size != 1 || accessors[0] != initial[index]) return true
        node = node.childData!![0]
    }
    return node.accessorData != null
}

private fun flowRequests(m: ApManager, c: FlowTree, p: Pattern, unchanged: IntArray?): Boolean {
    val path = p.fact.path.toIntArray()
    var hit = false
    val u = c.root.walk(path) { d, n ->
        m.cancellation.checkpoint()
        if (!n.leaf.isEmpty && c.exclusion.admits(path[d]) &&
            !sameFlowLeaf(m, unchanged, path, d)) hit = true
    }
    if (hit || u == null) return hit
    return if (p.fact.tail.isAny) changedFlowSubtree(m, u, path, unchanged)
        else !u.leaf.isEmpty && !sameFlowLeaf(m, unchanged, path, path.size)
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
