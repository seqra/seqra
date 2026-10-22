package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.ap.ifds.AccessPathBase

/** Alias admission for a raw scalar callee summary, before caller binding. */
internal fun hasNoMemoryEffect(initial: PathFact, result: PathFact): Boolean {
    if (initial.base == AccessPathBase.Zero || initial.base != result.base || initial.path != result.path) return false
    return when {
        initial.mark is MarkSlot.Star && result.mark is MarkSlot.Star ->
            initial.tail == Tail.STAR && result.tail == Tail.STAR
        initial.mark is MarkSlot.Concrete && result.mark is MarkSlot.Concrete -> initial.mark == result.mark
        else -> false
    }
}

/** Split actual path/mark payloads. Tail, layer and both headers stay on each
 * scalar result. This does not compare against a started or normalized initial. */
internal fun ApManager.run1SummaryParts(initial: InitialAp, result: Facts): List<Pair<Facts, Boolean>> {
    cancellation.checkpoint()
    check(initial.manager === this) { "A summary premise must use this AP manager" }
    if (initial.isZero || initial.base != result.base) return listOf(result to false)
    return when (result) {
        is Reach -> listOf(result to false)
        is FlowTree -> {
            if (initial.mark !is MarkSlot.Star || initial.tail != Tail.STAR || result.tail != Tail.STAR)
                return listOf(result to false)
            val (selected, rest) = splitSummaryLeaf(FlowAlgebra, result.root, initial.pathData) { it }
            summaryParts(result, selected, rest, result::withRoot)
        }
        is TaintTree -> {
            val atom = (initial.mark as? MarkSlot.Concrete)?.mark ?: return listOf(result to false)
            val singleton = markSetOf(atom)
            val (selected, rest) = splitSummaryLeaf(taintAlg, result.root, initial.pathData) { leaf ->
                taintLeaves(if (atom in leaf.exact) singleton else MarkSet.EMPTY,
                    if (atom in leaf.any) singleton else MarkSet.EMPTY)
            }
            summaryParts(result, selected, rest, result::withRoot)
        }
    }
}

private fun <P : TrieLeaf> summaryParts(
    original: Facts, selected: TrieNode<P>?, rest: TrieNode<P>?, make: (TrieNode<P>) -> Facts,
): List<Pair<Facts, Boolean>> = when {
    selected == null -> listOf(original to false)
    rest == null -> listOf(original to true)
    else -> listOf(make(rest) to false, make(selected) to true)
}

/** Only the exact premise spine changes. Ancestors and descendants are separate
 * scalar paths; untouched sibling subtrees remain shared. No mark/path product. */
private fun <P : TrieLeaf> ApManager.splitSummaryLeaf(
    algebra: LeafAlgebra<P>, root: TrieNode<P>, path: IntArray, select: (P) -> P,
): Pair<TrieNode<P>?, TrieNode<P>?> {
    val spine = ArrayList<TrieNode<P>>(path.size)
    var tip = root
    for (accessor in path) {
        cancellation.checkpoint()
        spine += tip
        tip = tip.child(accessor) ?: return null to root
    }
    val selected = select(tip.leaf)
    if (selected.isEmpty) return null to root
    var rest = algebra.withLeaf(tip, algebra.minus(tip.leaf, selected))
    for (depth in path.indices.reversed()) {
        cancellation.checkpoint()
        val parent = spine[depth]
        rest = algebra.replaceChild(parent, parent.leaf, path[depth], rest)
    }
    val chosen = if (rest == null) root else algebra.prepend(this, this.path(path), algebra.leafNode(selected))
    return chosen to rest
}
