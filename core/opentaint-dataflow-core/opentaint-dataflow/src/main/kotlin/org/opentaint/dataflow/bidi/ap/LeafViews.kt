package org.opentaint.dataflow.bidi.ap

internal fun leafViews(m: ApManager, f: Facts): Sequence<Pattern> {
    m.cancellation.checkpoint()
    return when (f) {
        is Reach -> sequenceOf(m.zero.toPattern())
        is FlowTree -> buildList {
            FlowAlgebra.forEachLeaf(f.root, EMPTY_PATH, m.cancellation) { path, _ -> add(flowPattern(f, path)) }
        }.asSequence()
        is TaintTree -> buildList {
            m.taintAlg.forEachLeaf(f.root, EMPTY_PATH, m.cancellation) { path, leaf -> taintPatterns(f, path, leaf) { add(it) } }
        }.asSequence()
    }.map { pattern ->
        m.cancellation.checkpoint()
        pattern
    }
}

/** Walk to p once. Only an admitted non-exact leaf can overlap from above p. */
internal fun nearLeafViews(m: ApManager, f: Facts, p: IntArray): Sequence<Pattern> {
    m.cancellation.checkpoint()
    return when (f) {
        is Reach -> if (p.isEmpty()) leafViews(m, f) else emptySequence()
        is FlowTree -> buildList {
            val u = f.root.walk(p) { d, n ->
                m.cancellation.checkpoint()
                if (!n.leaf.isEmpty && f.exclusion.admits(p[d])) add(flowPattern(f, p.copyOf(d)))
            }
            u?.let { FlowAlgebra.forEachLeaf(it, p, m.cancellation) { path, _ -> add(flowPattern(f, path)) } }
        }.asSequence()
        is TaintTree -> buildList {
            val u = f.root.walk(p) { d, n ->
                m.cancellation.checkpoint()
                if (!n.leaf.any.isEmpty && f.exclusion.admits(p[d]))
                    taintPatterns(f, p.copyOf(d), m.taintLeaves(MarkSet.EMPTY, n.leaf.any)) { add(it) }
            }
            u?.let { m.taintAlg.forEachLeaf(it, p, m.cancellation) { path, leaf -> taintPatterns(f, path, leaf) { add(it) } } }
        }.asSequence()
    }.map { pattern ->
        m.cancellation.checkpoint()
        pattern
    }
}

private fun flowPattern(f: FlowTree, path: IntArray): Pattern = Pattern(
    PathFact(f.base, path.asList(), f.tail, MarkSlot.Star(f.markExclusion)),
    f.exclusion)

private inline fun taintPatterns(f: TaintTree, path: IntArray, leaf: TaintLeaves, add: (Pattern) -> Unit) {
    val anyTail = f.anyTail
    for (id in leaf.exact.data) add(Pattern(PathFact(f.base, path.asList(), Tail.EXACT, MarkSlot.Concrete(TaintMark(id))), ExclusionSet.Empty))
    for (id in leaf.any.data) add(Pattern(PathFact(f.base, path.asList(), anyTail, MarkSlot.Concrete(TaintMark(id))), f.exclusion))
}
