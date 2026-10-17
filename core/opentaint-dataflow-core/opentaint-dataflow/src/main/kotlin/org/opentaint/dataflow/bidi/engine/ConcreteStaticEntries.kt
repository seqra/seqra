package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isFieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*

/** A diagonal relation: exact/any leaf payloads encode EXACT/STAR originals.
 * This tree is a descriptor, never a fact with one shared original. */
internal class ConcreteStaticEntries(private val ops: ApOps) {
    private val ap get() = ops.manager
    private val geometry = StaticIdentityEntries(ops)

    fun accepts(initial: InitialAp): Boolean = initial.base == STATIC &&
        initial.pathData.size == 2 && initial.pathData[0].isClass() && initial.pathData[1].isFieldAccessor() &&
        initial.mark is MarkSlot.Concrete && initial.exclusion == ExclusionSet.Empty &&
        (initial.tail == Tail.EXACT || initial.tail == Tail.STAR)

    fun validate(entries: TaintTree) {
        check(entries.base == STATIC && entries.layer == Layer.DEMAND && entries.exclusion == ExclusionSet.Empty)
        check(!entries.root.hasAny || entries.anyTail == Tail.ANY) { "A concrete carrier encodes STAR, never must" }
        check(entries.root.leaf.isEmpty)
        entries.root.requestChildren(ap.cancellation::checkpoint) { cls, children ->
            check(cls.isClass() && children.leaf.isEmpty)
            children.requestChildren(ap.cancellation::checkpoint) { field, leaf ->
                check(field.isFieldAccessor() && !leaf.leaf.isEmpty && leaf.accessors == null)
            }
        }
    }

    private fun tree(root: TaintNode): TaintTree = ap.taintTree(STATIC, Layer.DEMAND, root, anyTail = Tail.ANY)
    fun current(initial: InitialAp): TaintTree = ops.startFact(initial).withLayer(Layer.DEMAND) as TaintTree
    fun canonical(premise: PremiseKey, value: Facts): Boolean =
        premise is InitialAp && accepts(premise) && value == current(premise)
    fun single(initial: InitialAp): TaintTree {
        check(accepts(initial))
        return tree(current(initial).root)
    }
    fun merge(old: TaintTree?, value: TaintTree): Pair<TaintTree, TaintTree?> {
        if (old == null) return value to value
        val (root, delta) = ap.taintAlg.mergeAddDelta(old.root, value.root, ap.cancellation)
        return tree(root) to delta?.let(::tree)
    }
    fun originals(entries: TaintTree): Sequence<InitialAp> = ops.leaves(entries).map {
        ap.cancellation.checkpoint()
        ap.initial(it.copy(fact = it.fact.copy(tail = if (it.fact.tail == Tail.EXACT) Tail.EXACT else Tail.STAR)))
    }

    fun abstractEntries(entries: TaintTree): FlowTree {
        fun convert(node: TaintNode, depth: Int): FlowNode {
            check(depth <= 2)
            val children = node.childData?.map { convert(it, depth + 1) }?.toTypedArray()
            return FlowAlgebra.node(if (node.leaf.isEmpty) FlowLeaf.NONE else FlowLeaf.LEAF,
                node.accessorData, children)
        }
        return ap.flowTree(STATIC, Layer.DEMAND, ExclusionSet.Empty, MarkSet.EMPTY, convert(entries.root, 0), Tail.STAR)
    }
    private fun select(entries: TaintTree, selected: FlowTree?): TaintTree? {
        if (selected == null) return null
        val classes = ArrayList<Int>()
        val values = ArrayList<TaintNode>()
        selected.root.requestChildren(ap.cancellation::checkpoint) { cls, fields ->
            val actual = checkNotNull(entries.root.child(cls))
            val accessors = checkNotNull(fields.accessorData)
            classes += cls
            values += ap.taintAlg.node(TaintLeaves.EMPTY, accessors,
                accessors.map { checkNotNull(actual.child(it)) }.toTypedArray())
        }
        return tree(ap.taintAlg.node(TaintLeaves.EMPTY, classes.toIntArray(), values.toTypedArray()))
    }
    fun remainder(entries: TaintTree, selected: TaintTree?): TaintTree? =
        if (selected == null) entries else ap.taintAlg.minusNode(entries.root, selected.root, ap.cancellation)?.let(::tree)
    fun affected(summary: StatementSummary, entries: TaintTree, stage: Boolean = false): TaintTree? =
        select(entries, geometry.affected(summary, abstractEntries(entries), stage))
    fun affected(rules: RuleStatement, entries: TaintTree): TaintTree? =
        select(entries, geometry.affected(rules, abstractEntries(entries)))
    fun affected(steps: List<CleanStep>, entries: TaintTree): TaintTree? =
        select(entries, geometry.affected(steps, abstractEntries(entries)))
    fun sinkEntries(sinks: List<SinkRule>, entries: TaintTree): TaintTree? = select(entries,
        geometry.select(abstractEntries(entries), sinks.flatMap { it.patterns }.filter { it.fact.base == STATIC }
            .map { it.fact.path.take(2) }))
}
