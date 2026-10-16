package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.util.Cancellation
import java.util.ArrayDeque

/** A leaf on the cleaner spine is above or at its position. */
internal fun cleanPosAt(cl: Cleaner, tail: Tail, d: Int, admitsRest: Boolean): Pos =
    if (d < cl.pathArray.size) when (tail) {
        Tail.EXACT -> Pos.DISJOINT
        Tail.STAR, Tail.ANY, Tail.ANY_TAINT -> if (admitsRest) Pos.PART else Pos.DISJOINT
    } else when (tail) {
        Tail.EXACT -> if (cl.reach == CleanReach.BELOW) Pos.DISJOINT else Pos.INSIDE
        else -> if (cl.reach == CleanReach.AT_AND_BELOW) Pos.INSIDE else Pos.PART
    }

private class CleanFrame<P : TrieLeaf>(val node: TrieNode<P>, val kept: P, val accessor: Int)

/** Keep the original positional callback interface. */
internal fun <P : TrieLeaf> LeafAlgebra<P>.cleanSpine(
    n: TrieNode<P>, p: IntArray, reach: CleanReach, d: Int,
    atNode: (Int, P) -> P, inside: (TrieNode<P>) -> TrieNode<P>?,
): TrieNode<P>? = cleanSpine(n, p, reach, d, null, atNode, inside)

/** Visit and rebuild one spine. Only children below its end go through inside. */
internal fun <P : TrieLeaf> LeafAlgebra<P>.cleanSpine(
    n: TrieNode<P>, p: IntArray, reach: CleanReach, d: Int, cancellation: Cancellation?,
    atNode: (Int, P) -> P, inside: (TrieNode<P>) -> TrieNode<P>?,
): TrieNode<P>? {
    val parents = ArrayDeque<CleanFrame<P>>()
    var current = n
    var depth = d
    var result: TrieNode<P>?
    while (true) {
        cancellation?.checkpoint()
        val kept = atNode(depth, current.leaf)
        if (depth >= p.size) {
            result = if (reach == CleanReach.EXACT) withLeaf(current, kept) else {
                val kids = retainChildren(current, cancellation) { true }
                withLeaf(kids?.let(inside), kept)
            }
            break
        }
        val accessor = p[depth]
        val child = current.child(accessor)
        if (child == null) {
            result = replaceChild(current, kept, accessor, null)
            break
        }
        parents.addLast(CleanFrame(current, kept, accessor))
        current = child
        depth++
    }
    while (!parents.isEmpty()) {
        cancellation?.checkpoint()
        val parent = parents.removeLast()
        result = replaceChild(parent.node, parent.kept, parent.accessor, result)
    }
    cancellation?.checkpoint()
    return result
}

/** Requests are attached to the incoming premise by the caller's output. */
@Suppress("UNUSED_PARAMETER")
internal fun cleanFacts(m: ApManager, c: Facts, premise: PremiseKey, cl: Cleaner, mode: ApMode, out: ApOut) {
    m.cancellation.checkpoint()
    if (c.base != cl.base) { out.result(c); return }
    when (c) {
        is Reach -> out.result(c)
        is FlowTree -> FlowClean(m, c, cl, mode, out).run()
        is TaintTree -> TaintClean(m, c, cl, mode, out).run()
    }
}

/** The selected-mark branch changes the header and shares every path. */
private class FlowClean(private val m: ApManager, private val c: FlowTree, private val cl: Cleaner,
                        private val mode: ApMode, private val out: ApOut) {
    private val results = Results(m, c.base)
    private val tail = c.tail

    fun run() {
        val selected = cl.mark
        if (selected != null) {
            if (mode.run1 && c.base == STATIC) { selectedStatic(selected); return }
            results.flow(c.layer, c.exclusion, m.intern(c.markExclusion + selected), c.root, c.tail)
            if (mode.run1) out.markRequest(selected)
            results.flush(out)
            return
        }
        FlowAlgebra.cleanSpine(c.root, cl.pathArray, cl.reach, 0, m.cancellation, ::row) { null }
            ?.let { results.flow(c.layer, c.exclusion, c.markExclusion, it, c.tail) }
        results.flush(out)
    }

    /** A disjoint static field is not a mark-interest producer. Split selected
     * leaves before changing mx so an unrelated sibling keeps its own header. */
    private fun selectedStatic(selected: TaintMark) {
        val mx = m.intern(c.markExclusion + selected)
        var matched = false
        fun add(node: FlowNode) {
            matched = true
            results.flow(c.layer, c.exclusion, mx, node, c.tail)
        }
        val kept = FlowAlgebra.cleanSpine(c.root, cl.pathArray, cl.reach, 0, m.cancellation,
            { depth, leaf ->
                if (leaf.isEmpty || cleanPosAt(cl, c.tail, depth,
                        depth < cl.pathArray.size && c.exclusion.admits(cl.pathArray[depth])) == Pos.DISJOINT) leaf
                else {
                    add(FlowAlgebra.prepend(m, m.path(cl.pathArray.copyOf(depth)), FlowAlgebra.LEAF_NODE))
                    FlowLeaf.NONE
                }
            },
            { children ->
                add(FlowAlgebra.prepend(m, cl.path, children))
                null
            })
        kept?.let { results.flow(c.layer, c.exclusion, c.markExclusion, it, c.tail) }
        if (matched) out.markRequest(selected)
        results.flush(out)
    }

    private fun row(d: Int, leaf: FlowLeaf): FlowLeaf {
        if (leaf.isEmpty) return leaf
        val path = cl.pathArray
        val pos = cleanPosAt(cl, tail, d, d < path.size && c.exclusion.admits(path[d]))
        if (pos == Pos.DISJOINT) return leaf
        if (pos == Pos.PART) {
            val at = FlowAlgebra.prepend(m, m.path(path.copyOf(d)), FlowAlgebra.LEAF_NODE)
            results.flow(Layer.DEMAND, c.exclusion, c.markExclusion, at, c.tail)
        }
        return FlowLeaf.NONE
    }
}

/** Concrete and all-mark cleaners retain the spatial and partial-cleaning rows. */
private class TaintClean(private val m: ApManager, private val c: TaintTree, private val cl: Cleaner,
                         private val mode: ApMode, private val out: ApOut) {
    private val results = Results(m, c.base)
    private val anyTail = c.anyTail

    private fun gone(marks: MarkSet): MarkSet =
        if (cl.mark == null || marks.isEmpty) marks else marks.intersect(m.markSetOf(cl.mark))

    fun run() {
        m.taintAlg.cleanSpine(c.root, cl.pathArray, cl.reach, 0, m.cancellation, ::row) { kids ->
            m.taintAlg.mapLeaves(kids, m.cancellation) {
                m.taintLeaves(it.exact - gone(it.exact), it.any - gone(it.any))
            }
        }?.let { results.taint(c.layer, it, c.exclusion, c.anyTail) }
        results.flush(out)
    }

    private fun row(d: Int, leaf: TaintLeaves): TaintLeaves =
        m.taintLeaves(cut(d, Tail.EXACT, leaf.exact), cut(d, anyTail, leaf.any))

    private fun cut(d: Int, tail: Tail, marks: MarkSet): MarkSet {
        val removed = gone(marks)
        if (removed.isEmpty) return marks
        val path = cl.pathArray
        when (cleanPosAt(cl, tail, d, d >= path.size || c.exclusion.admits(path[d]))) {
            Pos.DISJOINT -> return marks
            Pos.INSIDE -> Unit
            Pos.PART -> {
                val at = m.path(path.copyOf(d))
                when {
                    tail == Tail.ANY_TAINT && d == path.size - 1 && cl.reach != CleanReach.EXACT -> {
                        if (mode.run1) put(Layer.DEMAND, at, Tail.ANY_TAINT, removed)
                        else put(c.layer, at, Tail.ANY_TAINT, removed, c.exclusion.union(ExclusionSet.of(path[d])))
                        if (cl.reach == CleanReach.BELOW) put(c.layer, cl.path, Tail.EXACT, removed)
                    }
                    tail.isAny && cl.reach == CleanReach.BELOW && d == path.size -> put(c.layer, at, Tail.EXACT, removed)
                    else -> put(Layer.DEMAND, at, tail, removed, c.exclusion)
                }
            }
        }
        return marks - removed
    }

    private fun put(layer: Layer, at: PathNode?, tail: Tail, marks: MarkSet,
                    exclusion: ExclusionSet = ExclusionSet.Empty) {
        val leaf = if (tail == Tail.EXACT) m.taintLeaves(marks, MarkSet.EMPTY) else m.taintLeaves(MarkSet.EMPTY, marks)
        val root = m.taintAlg.prepend(m, at, m.taintAlg.leafNode(leaf))
        results.taint(layer, root, exclusion, if (tail == Tail.EXACT) Tail.ANY_TAINT else tail)
    }
}
