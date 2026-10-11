package org.opentaint.dataflow.bidi.ap

/** The mark test runs after the position test. A premise cannot exclude marks. */
internal class MarkGate(private val m: ApManager, val premise: MarkSlot, val target: MarkSlot) {
    init {
        check(premise !is MarkSlot.Star || premise.excluded.isEmpty) { "S7: a premise cannot exclude marks" }
        check(premise is MarkSlot.Concrete || target is MarkSlot.Star) { "S7: a concrete target needs a concrete premise" }
    }

    fun flowMarks(x: MarkSet): MarkSet? =
        if (premise is MarkSlot.Concrete) null else m.intern(x + (target as MarkSlot.Star).excluded)

    fun flowRequest(x: MarkSet): TaintMark? = (premise as? MarkSlot.Concrete)?.mark?.takeIf { it !in x }

    fun taintMarks(ms: MarkSet): MarkSet? {
        if (ms.isEmpty) return null
        val passed = when (premise) {
            is MarkSlot.Concrete -> if (premise.mark in ms) m.markSetOf(premise.mark) else return null
            is MarkSlot.Star -> ms
        }
        val result = when (target) {
            is MarkSlot.Concrete -> m.markSetOf(target.mark)
            is MarkSlot.Star -> m.intern(passed - target.excluded)
        }
        return result.takeIf { !it.isEmpty }
    }
}
