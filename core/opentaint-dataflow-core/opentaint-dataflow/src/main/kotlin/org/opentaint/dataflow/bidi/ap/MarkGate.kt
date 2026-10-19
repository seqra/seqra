package org.opentaint.dataflow.bidi.ap

/** Cached primitive forms remain full. Only the execution view projects them. */
internal class MarkGate(private val m: ApManager, from: MarkSlot, to: MarkSlot,
                        private val mode: ApMode = ApMode(false, Direction.FORWARD, 1)) {
    val premise: MarkSlot = when (from) {
        is MarkSlot.Star -> from
        is MarkSlot.Concrete -> MarkSlot.Concrete(if (m.marks.isGroup(from.mark)) from.mark
            else m.marks.runtimeAtom(from.mark, mode.markMode))
    }
    val target: MarkSlot = when (to) {
        is MarkSlot.Star -> MarkSlot.Star(to.excluded.projected(m.marks, mode.markMode))
        is MarkSlot.Concrete -> MarkSlot.Concrete(m.marks.runtimeAtom(to.mark, mode.markMode))
    }
    init {
        check(premise !is MarkSlot.Star || premise.excluded.isEmpty) { "S7: a premise cannot exclude marks" }
        check(premise is MarkSlot.Concrete || target is MarkSlot.Star) { "S7: a selected target needs a selected premise" }
    }
    fun flowMarks(x: MarkExclusions): MarkExclusions? =
        if (premise is MarkSlot.Concrete) null else m.intern(x + (target as MarkSlot.Star).excluded)
    fun flowMarks(x: MarkSet): MarkExclusions? = flowMarks(MarkExclusions.fromMarks(x, m.marks))
    fun flowRequest(x: MarkSet): TaintMark? = flowRequest(MarkExclusions.fromMarks(x, m.marks))
    fun flowRequest(x: MarkExclusions): TaintMark? = (premise as? MarkSlot.Concrete)?.mark?.takeIf {
        mode.markMode == MarkMode.GROUP || it !in x
    }
    fun taintMarks(ms: MarkSet): MarkSet? {
        if (ms.isEmpty) return null
        val input = if (mode.markMode == MarkMode.GROUP) m.runtimeMarks(ms, mode) else ms
        val passed = when (premise) {
            is MarkSlot.Concrete -> m.intern(MarkSet(input.data.filter {
                m.marks.coversAtom(premise.mark, TaintMark(it))
            }.toIntArray())).takeIf { !it.isEmpty } ?: return null
            is MarkSlot.Star -> input
        }
        val result = when (target) {
            is MarkSlot.Concrete -> m.markSetOf(target.mark)
            is MarkSlot.Star -> m.intern(passed - target.excluded)
        }
        return result.takeIf { !it.isEmpty }
    }
}
