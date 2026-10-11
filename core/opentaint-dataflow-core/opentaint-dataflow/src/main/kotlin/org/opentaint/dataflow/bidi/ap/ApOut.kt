package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.ap.ifds.AccessPathBase

/** Results of one operation. Requests refer to the input edge's premise. */
interface ApOut {
    fun result(f: Facts)
    fun markRequest(mark: TaintMark)
    fun positionRequest(position: PathNode)
}

/** One result per kind and canonical key, without store subsumption. */
internal class Results(private val m: ApManager, private val base: AccessPathBase) {
    private val reach = BooleanArray(2)
    private data class FlowKey(val layer: Layer, val exclusion: ExclusionSet, val mx: MarkSet)
    private data class TaintKey(val layer: Layer, val exclusion: ExclusionSet)
    private val flow = LinkedHashMap<FlowKey, FlowNode>(2)
    private val taint = LinkedHashMap<TaintKey, TaintNode>(2)

    fun reach(layer: Layer) { reach[layer.ordinal] = true }

    fun flow(layer: Layer, exclusion: ExclusionSet, mx: MarkSet, node: FlowNode) {
        if (node.isEmpty) return
        val key = FlowKey(layer, if (layer == Layer.DEMAND) ExclusionSet.Empty else m.intern(exclusion), m.intern(mx))
        flow[key] = flow[key]?.let { FlowAlgebra.mergeAdd(it, node, m.cancellation) } ?: node
    }

    fun taint(layer: Layer, node: TaintNode, exclusion: ExclusionSet = ExclusionSet.Empty) {
        if (node.isEmpty) return
        val key = TaintKey(layer, if (layer == Layer.DEMAND || !node.hasAny) ExclusionSet.Empty else m.intern(exclusion))
        taint[key] = taint[key]?.let { m.taintAlg.mergeAdd(it, node, m.cancellation) } ?: node
    }

    fun flush(out: ApOut) {
        for (layer in Layer.entries) if (reach[layer.ordinal]) {
            m.cancellation.checkpoint()
            out.result(Reach.of(layer))
        }
        for ((key, root) in flow) {
            m.cancellation.checkpoint()
            out.result(m.flowTree(base, key.layer, key.exclusion, key.mx, root))
        }
        for ((key, root) in taint) {
            m.cancellation.checkpoint()
            out.result(m.taintTree(base, key.layer, root, key.exclusion))
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
    val positionRequests = ArrayList<PathNode>()
    override fun result(f: Facts) { results += f }
    override fun markRequest(mark: TaintMark) { markRequests += mark }
    override fun positionRequest(position: PathNode) { positionRequests += position }
}
