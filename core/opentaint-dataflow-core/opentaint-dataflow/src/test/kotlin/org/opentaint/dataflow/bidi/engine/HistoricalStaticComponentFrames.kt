package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*

/** Historical footprint-frame codec controls. The selected engine does not
 * call these helpers; it applies ordinary stored summaries to actual inputs. */
/** Project one field cylinder before the ordinary complete matcher. Proper
 * ancestor E is tested before consuming its prefix, while a tip E stays at the
 * tip. Distinct E headers remain distinct output Facts, not a widened union. */
internal fun staticFieldInputs(ap: ApManager, input: Facts, slot: Run1Slot.StaticField): List<Facts> {
    if (input.base != STATIC) return emptyList()
    val result = ArrayList<Facts>(3)
    val path = ap.path(slot.pathData)
    when (input) {
        is Reach -> Unit
        is FlowTree -> {
            val cls = input.root.child(slot.cls)
            val direct = cls?.child(slot.field)
            if (direct != null) result += ap.flowTree(STATIC, input.layer, input.exclusion,
                input.markExclusion, FlowAlgebra.prepend(ap, path, direct))
            val rootAdmits = !input.root.leaf.isEmpty && input.exclusion.admits(slot.cls)
            val classAdmits = cls != null && !cls.leaf.isEmpty && input.exclusion.admits(slot.field)
            if (rootAdmits || classAdmits) result += ap.flowTree(STATIC, input.layer, ExclusionSet.Empty,
                input.markExclusion, FlowAlgebra.prepend(ap, path, FlowAlgebra.LEAF_NODE))
        }
        is TaintTree -> {
            val cls = input.root.child(slot.cls)
            val direct = cls?.child(slot.field)
            if (direct != null) result += ap.taintTree(STATIC, input.layer,
                ap.taintAlg.prepend(ap, path, direct), input.exclusion)
            var broad = MarkSet.EMPTY
            if (input.exclusion.admits(slot.cls)) broad += input.root.leaf.any
            if (input.exclusion.admits(slot.field) && cls != null) broad += cls.leaf.any
            if (!broad.isEmpty) result += ap.taintTree(STATIC, input.layer,
                ap.taintAlg.prepend(ap, path, ap.taintAlg.leafNode(ap.taintLeaves(MarkSet.EMPTY, broad))))
        }
    }
    return result.distinct()
}

/** Exact class points are separate from field cylinders. Broad suppliers give
 * a conservative DEMAND point, never a manufactured normal exact proof. */
internal fun staticClassPointInputs(ap: ApManager, input: Facts, cls: Int): List<Facts> {
    if (input !is TaintTree || input.base != STATIC) return emptyList()
    val result = ArrayList<Facts>(3)
    val leaf = input.root.child(cls)?.leaf
    val path = ap.path(cls, null)
    if (leaf != null && !leaf.exact.isEmpty) result += ap.taintTree(STATIC, input.layer,
        ap.taintAlg.prepend(ap, path, ap.taintAlg.leafNode(ap.taintLeaves(leaf.exact, MarkSet.EMPTY))))
    var possible = leaf?.any ?: MarkSet.EMPTY
    if (input.exclusion.admits(cls)) possible += input.root.leaf.any
    if (!possible.isEmpty) result += ap.taintTree(STATIC, Layer.DEMAND,
        ap.taintAlg.prepend(ap, path, ap.taintAlg.leafNode(ap.taintLeaves(possible, MarkSet.EMPTY))))
    return result
}

/** The footprint is structural and survives request release. This read-time
 * partition never mutates a publication or native record. */
internal fun prepareStaticSummaryInputs(
    ap: ApManager, input: Facts, premise: InitialAp, footprint: Run1StaticFootprint?,
): List<Facts> = prepareStaticSummaryInputs(ap, input, premise.toPattern(), footprint)

internal fun prepareStaticSummaryInputs(
    ap: ApManager, input: Facts, premise: Pattern, footprint: Run1StaticFootprint?,
): List<Facts> {
    if (footprint == null || input.base != STATIC || premise.fact.base != STATIC) return listOf(input)
    ap.cancellation.checkpoint()
    if (isSpecialStaticRoot(premise)) return staticUntouchedInputs(ap, input, footprint)
    val path = premise.fact.path
    return when {
        path.size == 2 && path[0].isClass() -> {
            val slot = Run1Slot.StaticField(path[0], path[1])
            if (footprint.owns(slot.cls, slot.field)) staticFieldInputs(ap, input, slot) else emptyList()
        }
        path.size == 1 && path[0].isClass() -> staticClassPointInputs(ap, input, path[0])
        else -> listOf(input)
    }
}

/** Identity is reconstructed only for the footprint complement; callers invoke
 * this only after an actual reached normal-exit publication/record is selected. */
internal fun staticUntouchedInputs(ap: ApManager, input: Facts, footprint: Run1StaticFootprint): List<Facts> {
    if (input.base != STATIC || input is Reach || footprint.allStatic) return emptyList()
    if (input.layer == Layer.NORMAL) return when (input) {
        is FlowTree -> normalStaticUntouched(ap, input, input.root, FlowAlgebra, footprint,
            broad = { it }, make = { node, exclusion ->
                ap.flowTree(STATIC, Layer.NORMAL, exclusion, input.markExclusion, node)
            })
        is TaintTree -> normalStaticUntouched(ap, input, input.root, ap.taintAlg, footprint,
            broad = { ap.taintLeaves(MarkSet.EMPTY, it.any) }, make = { node, exclusion ->
                ap.taintTree(STATIC, Layer.NORMAL, node, exclusion)
            })
        is Reach -> emptyList()
    }

    // DEMAND has no field exclusion header. Enumerate only required finite
    // projections; preserve packed marks, and never widen a point into a field.
    val result = ArrayList<Facts>()
    for (slot in footprint.fieldUniverse) {
        ap.cancellation.checkpoint()
        if (!footprint.owns(slot.cls, slot.field)) result += staticFieldInputs(ap, input, slot)
    }
    when (input) {
        is TaintTree -> for (cls in footprint.classUniverse) {
            ap.cancellation.checkpoint()
            if (!footprint.ownsPoint(cls)) result += staticClassPointInputs(ap, input, cls)
        }
        is FlowTree -> {
            // Selected JVM routes establish this before LinkIn. A DEMAND broad
            // FLOW cannot encode its class-point residual in the current grammar.
            check(input.root.leaf.isEmpty) { "DEMAND root FLOW is outside the selected run-1 JVM domain" }
            input.root.requestChildren(ap.cancellation::checkpoint) { _, node ->
                check(node.leaf.isEmpty) { "DEMAND class FLOW is outside the selected run-1 JVM domain" }
            }
        }
        is Reach -> Unit
    }
    return result.distinct()
}

/** A NORMAL residual uses E to keep the complement compact. Only a class whose
 * point is owned but whose fields are not all owned needs finite field rows. */
private fun <P : TrieLeaf> normalStaticUntouched(
    ap: ApManager, input: Facts, root: TrieNode<P>, alg: LeafAlgebra<P>, footprint: Run1StaticFootprint,
    broad: (P) -> P, make: (TrieNode<P>, ExclusionSet) -> Facts,
): List<Facts> {
    val result = ArrayList<Facts>()
    val exclusion = when (input) {
        is FlowTree -> input.exclusion
        is TaintTree -> input.exclusion
        is Reach -> return emptyList()
    }
    val affected = footprint.affectedClasses
    fun emit(node: TrieNode<P>?, path: PathNode?, ownExclusion: ExclusionSet) {
        if (node != null && !node.isEmpty) result += make(alg.prepend(ap, path, node), ownExclusion)
    }
    if (!root.leaf.isEmpty) emit(alg.leafNode(root.leaf), null,
        exclusion.union(ExclusionSet.of(affected.toIntArray())))

    // Explicit unaffected branches retain their original E and immutable nodes.
    root.requestChildren(ap.cancellation::checkpoint) { cls, _ ->
        if (cls !in affected) emit(root.child(cls), ap.path(cls, null), exclusion)
    }
    for (cls in affected) {
        ap.cancellation.checkpoint()
        if (cls in footprint.wideClasses) continue // entire class and point owned
        if (footprint.ownsPoint(cls)) {
            for (slot in footprint.fieldUniverse) {
                ap.cancellation.checkpoint()
                if (slot.cls == cls && !footprint.owns(cls, slot.field)) result += staticFieldInputs(ap, input, slot)
            }
            continue
        }
        val ownedFields = footprint.fieldsByClass[cls].orEmpty()
        val fieldMask = ExclusionSet.of(ownedFields.toIntArray())
        val path = ap.path(cls, null)
        val direct = root.child(cls)
        if (direct != null) {
            if (!direct.leaf.isEmpty) emit(alg.leafNode(direct.leaf), path, exclusion.union(fieldMask))
            val children = alg.retainChildren(direct, ap.cancellation) { it !in ownedFields }
            emit(children, path, exclusion)
        }
        // A proper ancestor consumes E at C. It must not inherit a tip E from
        // an explicit C or C.f branch packed in the same input.
        if (exclusion.admits(cls)) {
            val supplied = broad(root.leaf)
            if (!supplied.isEmpty) emit(alg.leafNode(supplied), path, fieldMask)
        }
    }
    return result.distinct()
}
