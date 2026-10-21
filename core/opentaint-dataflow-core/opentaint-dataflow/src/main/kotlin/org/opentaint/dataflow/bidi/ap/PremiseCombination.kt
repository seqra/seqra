package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor

/** Combine original inputs only after the existing complete condition has been admitted. */
fun interface PremiseCombination {
    fun combine(inputs: Iterable<PremiseKey>): PremiseKey
}

/** Later runs keep the complete canonical conjunction. The shared AP manager has no run mode. */
class ExactPremises(private val manager: ApManager) : PremiseCombination {
    override fun combine(inputs: Iterable<PremiseKey>): PremiseKey {
        var result: PremiseKey = manager.zero
        var present = false
        for (input in inputs) {
            manager.cancellation.checkpoint()
            result = manager.union(result, input)
            present = true
        }
        check(present) { "A complete condition needs at least one input" }
        return result
    }
}

/** Run 1 weakens a completed dependency to one existing original, without interning a union. */
class Run1Premises(private val manager: ApManager) : PremiseCombination {
    private val order = Run1InitialOrder(manager)

    override fun combine(inputs: Iterable<PremiseKey>): InitialAp {
        var selected = manager.zero
        var present = false
        for (input in inputs) {
            manager.cancellation.checkpoint()
            check(input is InitialAp) { "A run-1 condition input must have one original premise" }
            check(input.manager === manager) { "All premises must use this AP manager" }
            check(input.exclusion == ExclusionSet.Empty) { "A run-1 original has Empty field exclusions" }
            present = true
            if (input.isZero) continue
            if (selected.isZero || order.compare(input, selected) < 0) selected = input
        }
        check(present) { "A complete condition needs at least one input" }
        return selected
    }
}

/** Allocation IDs and abbreviated display strings are not part of the preference order. */
private class Run1InitialOrder(private val manager: ApManager) : Comparator<InitialAp> {
    override fun compare(a: InitialAp, b: InitialAp): Int {
        if (a === b) return 0
        val category = category(a).compareTo(category(b))
        if (category != 0) return category
        val base = compareBase(a.base, b.base)
        if (base != 0) return base
        val path = comparePath(a.pathData, b.pathData)
        if (path != 0) return path
        val tail = tailTag(a.tail).compareTo(tailTag(b.tail))
        if (tail != 0) return tail
        return compareMark(a.mark, b.mark)
    }

    private fun category(i: InitialAp): Int = when (i.base) {
        is AccessPathBase.Argument -> 0
        AccessPathBase.This -> 1
        STATIC -> if (i.pathData.size >= 2) 3 else 4
        AccessPathBase.Zero -> 5 // ZERO is filtered before comparison.
        else -> 2
    }

    private fun baseTag(base: AccessPathBase): Int = when (base) {
        is AccessPathBase.Argument -> 0
        AccessPathBase.This -> 1
        is AccessPathBase.LocalVar -> 2
        AccessPathBase.Return -> 3
        AccessPathBase.Exception -> 4
        is AccessPathBase.Constant -> 5
        AccessPathBase.ClassStatic -> 6
        AccessPathBase.Zero -> 7
        is AccessPathBase.CleanerTemporary -> error("A temporary cannot be an original premise")
    }

    private fun compareBase(a: AccessPathBase, b: AccessPathBase): Int {
        val tag = baseTag(a).compareTo(baseTag(b))
        if (tag != 0) return tag
        return when (a) {
            is AccessPathBase.Argument -> a.idx.compareTo((b as AccessPathBase.Argument).idx)
            is AccessPathBase.LocalVar -> a.idx.compareTo((b as AccessPathBase.LocalVar).idx)
            is AccessPathBase.Constant -> {
                b as AccessPathBase.Constant
                val type = a.typeName.compareTo(b.typeName)
                if (type != 0) type else a.value.compareTo(b.value)
            }
            else -> 0
        }
    }

    private fun comparePath(a: IntArray, b: IntArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            if (i and 255 == 0) manager.cancellation.checkpoint()
            val compared = compareAccessor(manager.accessors.accessor(a[i]), manager.accessors.accessor(b[i]))
            if (compared != 0) return compared
        }
        return a.size.compareTo(b.size)
    }

    private fun accessorTag(a: Accessor): Int = when (a) {
        is ClassStaticAccessor -> 0
        is FieldAccessor -> 1
        ElementAccessor -> 2
        else -> error("An original path contains an unsupported accessor")
    }

    private fun compareAccessor(a: Accessor, b: Accessor): Int {
        val tag = accessorTag(a).compareTo(accessorTag(b))
        if (tag != 0) return tag
        return when (a) {
            is ClassStaticAccessor -> a.typeName.compareTo((b as ClassStaticAccessor).typeName)
            is FieldAccessor -> {
                b as FieldAccessor
                val owner = a.className.compareTo(b.className)
                if (owner != 0) owner else {
                    val name = a.fieldName.compareTo(b.fieldName)
                    if (name != 0) name else a.fieldType.compareTo(b.fieldType)
                }
            }
            else -> 0
        }
    }

    private fun tailTag(tail: Tail): Int = when (tail) {
        Tail.EXACT -> 0
        Tail.STAR -> 1
        Tail.ANY -> 2
        Tail.ANY_TAINT -> 3
    }

    private fun markTag(mark: MarkSlot): Int = when (mark) {
        is MarkSlot.Star -> 0
        is MarkSlot.Concrete -> when (manager.marks.key(mark.mark)) {
            MarkAtomKey.ControlZero -> 1
            is MarkAtomKey.Group -> 2
            is MarkAtomKey.Full -> 3
        }
    }

    private fun compareMark(a: MarkSlot, b: MarkSlot): Int {
        val tag = markTag(a).compareTo(markTag(b))
        if (tag != 0) return tag
        // Full names uniquely identify their declared owner; GROUP names encode owner and mode.
        // name() only reads immutable keys. It does not intern a new full or group atom.
        return if (a is MarkSlot.Concrete && b is MarkSlot.Concrete)
            manager.marks.name(a.mark).compareTo(manager.marks.name(b.mark)) else 0
    }
}
