package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isFieldAccessor

/** Only for validated depth-two identity descriptors, including shared trie DAGs. */
internal fun staticEntryMemberships(entries: FlowTree): Long =
    entries.root.size - 1 - (entries.root.accessorData?.size ?: 0)

/** A descriptor relation, not a FLOW fact with one shared premise. Every
 * [C,f] leaf denotes only i_f -> C.f.*; the original is implicit in its path. */
internal class StaticIdentityEntries(private val ops: ApOps) {
    private val ap get() = ops.manager

    fun validate(entries: FlowTree) {
        check(entries.base == STATIC && entries.layer == Layer.DEMAND && entries.tail == Tail.STAR &&
            entries.exclusion == ExclusionSet.Empty && entries.markExclusion.isEmpty)
        check(entries.root.leaf.isEmpty)
        entries.root.requestChildren(ap.cancellation::checkpoint) { cls, children ->
            check(cls.isClass() && children.leaf.isEmpty)
            children.requestChildren(ap.cancellation::checkpoint) { field, leaf ->
                check(field.isFieldAccessor() && !leaf.leaf.isEmpty && leaf.accessors == null)
            }
        }
    }

    fun merge(old: FlowTree?, value: FlowTree): Pair<FlowTree, FlowTree?> {
        if (old == null) return value to value
        val (root, delta) = FlowAlgebra.mergeAddDelta(old.root, value.root, ap.cancellation)
        return old.withRoot(root) to delta?.let(old::withRoot)
    }

    fun single(initial: InitialAp): FlowTree =
        (ops.startFact(initial).withLayer(Layer.DEMAND) as FlowTree)

    fun originals(entries: FlowTree): Sequence<InitialAp> = ops.leaves(entries).map {
        ap.cancellation.checkpoint()
        ap.initial(it)
    }

    fun contains(entries: FlowTree, initial: InitialAp): Boolean =
        initial.base == STATIC && initial.tail == Tail.STAR && initial.mark == MarkSlot.STAR &&
            initial.exclusion == ExclusionSet.Empty && initial.pathData.size == 2 &&
            entries.root.child(initial.pathData[0])?.child(initial.pathData[1])?.leaf == FlowLeaf.LEAF

    fun remainder(entries: FlowTree, selected: FlowTree?): FlowTree? =
        if (selected == null) entries else ops.without(entries, selected) as? FlowTree

    /** Prefix selection reads only selected trie branches; [] means every field. */
    fun select(entries: FlowTree, prefixes: List<List<Int>>): FlowTree? {
        if (prefixes.any { it.isEmpty() }) return entries
        var root: FlowNode? = null
        for (prefix in prefixes) {
            ap.cancellation.checkpoint()
            val cls = prefix[0]
            val classNode = entries.root.child(cls) ?: continue
            val selected = if (prefix.size == 1) classNode else {
                val field = prefix[1]
                val leaf = classNode.child(field) ?: continue
                FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(field), arrayOf(leaf))
            }
            val branch = FlowAlgebra.node(FlowLeaf.NONE, intArrayOf(cls), arrayOf(selected))
            root = root?.let { FlowAlgebra.mergeAdd(it, branch, ap.cancellation) } ?: branch
        }
        return root?.let(entries::withRoot)
    }

    /** A missing stage edge kills, whereas an untouched statement passes.
     * Unknown/filtered forms fall back to scalar evaluation of every entry. */
    fun affected(summary: StatementSummary, entries: FlowTree, stage: Boolean = false): FlowTree? {
        if (STATIC in summary.typeFilters || STATIC in summary.resultFilters) return entries
        // Excluded-target creators issue their original-slot requests BEFORE
        // testing spatial overlap. A disjoint field still observes that event.
        if (summary.edgesOf(STATIC).any { (it.edge.to.mark as? MarkSlot.Star)?.excluded?.isEmpty == false })
            return entries
        if (summary.sources.any { source -> source.predicate.leaves.any { it.fact.base == STATIC } &&
                source.actions.any { (it.target.mark as? MarkSlot.Star)?.excluded?.isEmpty == false } })
            return entries
        fun plainRootKeep(edge: MicroEdge): Boolean = edge.isIdentity &&
            edge.edge.from == starSide(STATIC) && !edge.may && !edge.isSource &&
            edge.forward == edge.edge && !edge.conjunctive
        val rootKeep = summary.edgesOf(STATIC).any(::plainRootKeep)
        if ((stage || STATIC in summary.touched) && !rootKeep) return entries
        val prefixes = ArrayList<List<Int>>()
        for (edge in summary.edgesOf(STATIC)) {
            if (plainRootKeep(edge)) continue
            prefixes += edge.edge.from.path.take(2)
        }
        for (edge in summary.conjunctions) for (literal in edge.literals)
            if (literal.fact.base == STATIC) prefixes += literal.fact.path.take(2)
        for (source in summary.sources) for (literal in source.predicate.leaves)
            if (literal.fact.base == STATIC) prefixes += literal.fact.path.take(2)
        return select(entries, prefixes)
    }

    fun affected(rules: RuleStatement, entries: FlowTree): FlowTree? {
        val value = affected(rules.summary, entries)
        val conditions = select(entries, rules.sinks.flatMap { it.patterns }
            .filter { it.fact.base == STATIC }.map { it.fact.path.take(2) })
        return if (conditions == null) value else merge(value, conditions).first
    }

    fun affected(steps: List<CleanStep>, entries: FlowTree): FlowTree? {
        var affected: FlowTree? = null
        fun include(value: FlowTree?) { if (value != null) affected = merge(affected, value).first }
        for (step in steps) when (step) {
            is CleanStep.Clean -> if (step.cleaner.base == STATIC)
                include(select(entries, listOf(step.cleaner.pathArray.asList().take(2))))
            is CleanStep.Kill -> include(affected(step.keepEdges, entries))
            is CleanStep.Field -> {
                include(affected(step.read, entries)); include(affected(step.write, entries))
            }
        }
        return affected
    }
}
