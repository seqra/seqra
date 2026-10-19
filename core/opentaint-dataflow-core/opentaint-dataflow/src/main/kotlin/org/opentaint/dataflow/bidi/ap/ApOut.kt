package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.ap.ifds.AccessPathBase

/** Results of one operation. Mark requests refer to the input edge's premise. */
interface ApOut {
    fun result(f: Facts)
    fun markRequest(mark: TaintMark)
}

internal fun ApOut.run1Policy(m: ApManager, mode: ApMode): ApOut = if (!mode.run1) this else object : ApOut {
    override fun result(f: Facts) = run1CoverFacts(m, m.runtimeFact(f, mode)) { this@run1Policy.result(it) }
    override fun markRequest(mark: TaintMark) = this@run1Policy.markRequest(m.marks.runtimeAtom(mark, mode.markMode))
}

/** One result per kind and canonical key, without store subsumption. */
internal class Results(private val m: ApManager, private val base: AccessPathBase) {
    private val reach = BooleanArray(2)
    private data class FlowKey(val layer: Layer, val exclusion: ExclusionSet, val mx: MarkExclusions, val tail: Tail)
    private data class TaintKey(val layer: Layer, val exclusion: ExclusionSet, val anyTail: Tail)
    private val flow = LinkedHashMap<FlowKey, FlowNode>(2)
    private val taint = LinkedHashMap<TaintKey, TaintNode>(2)

    fun reach(layer: Layer) { reach[layer.ordinal] = true }

    fun flow(layer: Layer, exclusion: ExclusionSet, mx: MarkExclusions, node: FlowNode,
             tail: Tail = if (layer == Layer.NORMAL) Tail.STAR else Tail.ANY) {
        if (node.isEmpty) return
        val key = FlowKey(layer, if (tail == Tail.ANY) ExclusionSet.Empty else m.intern(exclusion), m.intern(mx), tail)
        flow[key] = flow[key]?.let { FlowAlgebra.mergeAdd(it, node, m.cancellation) } ?: node
    }

    fun flow(layer: Layer, exclusion: ExclusionSet, mx: MarkSet, node: FlowNode,
             tail: Tail = if (layer == Layer.NORMAL) Tail.STAR else Tail.ANY) =
        flow(layer, exclusion, MarkExclusions.fromMarks(mx, m.marks), node, tail)

    fun taint(layer: Layer, node: TaintNode, exclusion: ExclusionSet = ExclusionSet.Empty,
              anyTail: Tail = if (layer == Layer.NORMAL) Tail.ANY_TAINT else Tail.ANY) {
        if (node.isEmpty) return
        val key = TaintKey(layer, if (anyTail == Tail.ANY || !node.hasAny) ExclusionSet.Empty else m.intern(exclusion),
            if (node.hasAny) anyTail else Tail.ANY_TAINT)
        taint[key] = taint[key]?.let { m.taintAlg.mergeAdd(it, node, m.cancellation) } ?: node
    }

    fun flush(out: ApOut) {
        for (layer in Layer.entries) if (reach[layer.ordinal]) {
            m.cancellation.checkpoint()
            out.result(Reach.of(layer))
        }
        for ((key, root) in flow) {
            m.cancellation.checkpoint()
            out.result(m.flowTree(base, key.layer, key.exclusion, key.mx, root, key.tail))
        }
        for ((key, root) in taint) {
            m.cancellation.checkpoint()
            out.result(m.taintTree(base, key.layer, root, key.exclusion, key.anyTail))
        }
        reach.fill(false)
        flow.clear()
        taint.clear()
    }
}

/** Keep every result and request in callback order. */
class CollectingOut : ApOut {
    val results = ArrayList<Facts>()
    val markRequests = ArrayList<TaintMark>()
    override fun result(f: Facts) { results += f }
    override fun markRequest(mark: TaintMark) { markRequests += mark }
}
