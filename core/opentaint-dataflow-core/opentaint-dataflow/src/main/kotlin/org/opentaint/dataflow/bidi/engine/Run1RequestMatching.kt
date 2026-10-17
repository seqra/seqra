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
    if (input is FlowTree) check(normalRun1Slot(premise) != null) {
        "A run-1 FLOW supplier needs an admitted original abstract slot"
    }
}

internal fun TrieNode<*>.run1BroadLeaf(): Boolean = when (val value = leaf) {
    is FlowLeaf -> !value.isEmpty
    is TaintLeaves -> !value.any.isEmpty
    else -> error("Unexpected run-1 leaf")
}

/** Geometry only. E and FLOW mx are ordinary annotations, not request filters. */
internal fun slotMatches(slot: Run1Slot, input: Facts): Boolean {
    if (input is Reach || slot.base != input.base) return false
    val root: TrieNode<*> = when (input) {
        is FlowTree -> input.root
        is TaintTree -> input.root
        is Reach -> return false
    }
    return when (slot) {
        is Run1Slot.Base -> !root.isEmpty
        is Run1Slot.StaticField -> root.run1BroadLeaf() || root.child(slot.cls)?.let { cls ->
            cls.run1BroadLeaf() || cls.child(slot.field)?.isEmpty == false
        } == true
    }
}

/** All concrete branches and all actual requested marks, with no supplier memo.
 * Exact and nonexact alternatives remain distinct; every nonexact answer is STAR. */
internal fun forEachConcreteAnswer(
    ap: ApManager, input: Facts, rows: List<RequestedRow>,
    statistics: RequestBatchStatistics.Counts? = null, emit: (InitialAp) -> Unit,
) {
    if (input !is TaintTree || input.base == STATIC || rows.isEmpty()) return
    check(rows.all { it.slot is Run1Slot.Base }) { "Static marks are admitted without ordinary requests" }
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
                val matches = when (slot) {
                    is Run1Slot.Base -> true
                    is Run1Slot.StaticField -> when (path.size) {
                        0 -> !exact
                        1 -> !exact && path[0] == slot.cls
                        else -> path[0] == slot.cls && path[1] == slot.field
                    }
                }
                if (!matches) continue
                val atSlot = when (slot) {
                    is Run1Slot.Base -> path.isEmpty()
                    is Run1Slot.StaticField -> path.size == 2
                }
                val tail = if (exact && atSlot) Tail.EXACT else Tail.STAR
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

/** A direct class point is not a request slot. Broad components need structural
 * ownership, then every actual mark participates (a cleaner for T must preserve
 * an unrelated U). Only an exact supplier starts an EXACT class initial. */
internal fun forEachDirectClassInitial(
    ap: ApManager, input: Facts, profile: Run1StaticProfile?, emit: (InitialAp) -> Unit,
) {
    if (input !is TaintTree || input.base != STATIC) return
    fun emitMarks(cls: Int, actual: MarkSet, exact: Boolean, selected: Boolean) {
        if (selected && (profile == null || !profile.footprint.ownsPoint(cls))) return
        for (id in actual.data) {
            ap.cancellation.checkpoint()
            val mark = TaintMark(id)
            emit(ap.initial(STATIC, ap.path(cls, null), if (exact) Tail.EXACT else Tail.STAR,
                ExclusionSet.Empty, MarkSlot.Concrete(mark)))
        }
    }
    if (!input.root.leaf.any.isEmpty && profile != null) for (cls in profile.classPointClasses) {
        ap.cancellation.checkpoint()
        emitMarks(cls, input.root.leaf.any, exact = false, selected = true)
    }
    val children = input.root.childData ?: return
    val accessors = checkNotNull(input.root.accessorData)
    for (i in children.indices) {
        ap.cancellation.checkpoint()
        emitMarks(accessors[i], children[i].leaf.exact, exact = true, selected = false)
        emitMarks(accessors[i], children[i].leaf.any, exact = false, selected = true)
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
