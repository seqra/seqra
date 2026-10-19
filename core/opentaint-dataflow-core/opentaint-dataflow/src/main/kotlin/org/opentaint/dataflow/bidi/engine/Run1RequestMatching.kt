package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isFieldAccessor
import org.opentaint.dataflow.bidi.ap.*

/** Only the actual singleton normal abstract origin is a request slot. */
internal fun normalRun1Slot(premise: PremiseKey): Run1Slot? =
    (premise as? InitialAp)?.let { Run1Slot.fromInitial(it) }

/** Validate the actual origin before request eligibility. Layer changes do not
 * change the origin or invalidate a surviving FLOW dependency. */
internal fun validateRun1Supplier(ap: ApManager, premise: PremiseKey, input: Facts) {
    ap.cancellation.checkpoint()
    check((input is FlowTree) == premise.isFlow) { "K6: input kind follows its original premise" }
    check(premise !is PremiseSet || input is TaintTree) { "A conjunctive supplier is concrete" }
    if (input is FlowTree) {
        val original = premise as? InitialAp ?: error("A run-1 FLOW supplier needs one abstract origin")
        validateRun1Initial(original, 1)
        check(original.mark == MarkSlot.STAR) { "A FLOW supplier needs an abstract original mark" }
    }
}

/** Non-static request geometry has one empty-path base row. */
internal fun slotMatches(slot: Run1Slot, input: Facts): Boolean = input !is Reach &&
    slot.base == input.base && when (input) {
        is FlowTree -> !input.root.isEmpty
        is TaintTree -> !input.root.isEmpty
        is Reach -> false
    }

/** All concrete branches and all actual requested marks, with no supplier memo.
 * Exact and nonexact alternatives remain distinct; every nonexact answer is STAR. */
internal fun forEachConcreteAnswer(
    ap: ApManager, input: Facts, rows: List<RequestedRow>,
    statistics: RequestBatchStatistics.Counts? = null, emit: (InitialAp) -> Unit,
) {
    if (input !is TaintTree || rows.isEmpty()) return
    val stack = ArrayDeque<Pair<TaintNode, IntArray>>()
    stack.addLast(input.root to EMPTY_PATH)
    while (stack.isNotEmpty()) {
        ap.cancellation.checkpoint()
        val (node, path) = stack.removeLast()
        fun branch(marks: MarkSet, exact: Boolean) {
            if (marks.isEmpty) return
            for (row in rows) {
                ap.cancellation.checkpoint()
                if (statistics != null) statistics.groupGeometrySteps++
                val slot = row.slot
                if (slot.base != input.base) continue
                val tail = if (exact && path.isEmpty()) Tail.EXACT else Tail.STAR
                var canonicalPath: PathNode? = null
                var prepared = false
                for (id in marks.data) {
                    ap.cancellation.checkpoint()
                    if (statistics != null) statistics.memberScans++
                    val mark = TaintMark(id)
                    if (mark !in row.marks) continue
                    if (!prepared) { canonicalPath = ap.path(slot.pathData); prepared = true }
                    emit(ap.initial(slot.base, canonicalPath, tail, ExclusionSet.Empty, MarkSlot.Concrete(mark)))
                }
            }
        }
        branch(node.leaf.exact, exact = true)
        branch(node.leaf.any, exact = false)
        val children = node.childData ?: continue
        val accessors = checkNotNull(node.accessorData)
        for (i in children.indices.reversed()) {
            ap.cancellation.checkpoint()
            stack.addLast(children[i] to (path + accessors[i]))
        }
    }
}

/** Admission checks the selected F78 initial domain, not the wider AP factory. */
internal fun validateRun1Initial(initial: InitialAp, fieldLimit: Int) {
    check(fieldLimit == 1) { "F78 run 1 requires field limit 1" }
    check(initial.exclusion == ExclusionSet.Empty) { "A run-1 initial has no field exclusion" }
    check(initial.tail == Tail.EXACT || initial.tail == Tail.STAR) { "A run-1 initial is EXACT or STAR" }
    check(initial.base !is AccessPathBase.CleanerTemporary)
    if (initial.isZero) {
        check(initial.path == null && initial.tail == Tail.EXACT && initial.mark == MarkSlot.Concrete(TaintMark.ZERO))
        return
    }
    val path = initial.pathData
    if (initial.base != STATIC) {
        check(path.isEmpty()) { "A non-static run-1 initial has an empty path" }
    } else {
        check(path.size <= 2 && (path.isEmpty() || path[0].isClass()) &&
            (path.size < 2 || path[1].isFieldAccessor())) { "Invalid run-1 static initial layout" }
    }
    when (val mark = initial.mark) {
        is MarkSlot.Star -> {
            check(mark.excluded.isEmpty && initial.tail == Tail.STAR)
            check(initial.base != STATIC || path.size == 2) { "Run 1 has only abstract static-field initials" }
        }
        is MarkSlot.Concrete -> check(initial.base != STATIC || path.isNotEmpty()) {
            "Run 1 has no concrete empty static-root initial"
        }
    }
}

/** No emitter cut: fail if the preceding selected JVM cuts/bindings did not
 * establish their input invariant. Current facts may still have ANY/must/E/mx. */
internal fun validateRun1Input(ap: ApManager, input: Facts) {
    if (input is Reach) return
    check(input.base !is AccessPathBase.CleanerTemporary)
    val root: TrieNode<*> = when (input) {
        is FlowTree -> input.root
        is TaintTree -> input.root
        is Reach -> return
    }
    check(root.boundedDepth <= 1) { "The run-1 input was not cut before delivery" }
    if (input.base != STATIC) {
        val children = root.childData ?: return
        val accessors = checkNotNull(root.accessorData)
        for (i in children.indices) {
            ap.cancellation.checkpoint()
            check(accessors[i].isCounted() && children[i].childData == null) {
                "A non-static run-1 input has at most one ordinary accessor"
            }
        }
        return
    }
    check(input !is TaintTree || input.root.leaf.exact.isEmpty) { "Exact empty static-root taint is outside F78" }
    val classes = root.accessorData ?: return
    for (i in classes.indices) {
        ap.cancellation.checkpoint()
        check(classes[i].isClass()) { "A static input begins with its class accessor" }
        val cls = root.childData!![i]
        val fields = cls.accessorData ?: continue
        for (j in fields.indices) {
            ap.cancellation.checkpoint()
            check(fields[j].isFieldAccessor() && cls.childData!![j].childData == null) {
                "A static class contains one static field and no deeper path"
            }
        }
    }
}
