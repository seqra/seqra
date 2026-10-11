package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.CallStage
import org.opentaint.dataflow.bidi.interp.CleanStep
import org.opentaint.dataflow.bidi.interp.StatementSummary
import org.opentaint.dataflow.bidi.interp.StageKind

/** F71 concrete-design operations, rather than the current F72/F75/F77 selector. */
internal object HistoricalF71 {
    const val VERSION = "F71 concrete design / proof snapshot fb789d0ab"

    fun validate(program: FiniteProgramFixtures, inputs: ProgramRunInputs) {
        fun summary(s: StatementSummary) {
            for (e in s.edges) if (e.isSource) check(e.forward.to.tail == Tail.EXACT) {
                "$VERSION differential excludes must sources and F76 must-record views"
            }
            check(s.edges.none { it.forward.from.tail == Tail.ANY_TAINT || it.forward.to.tail == Tail.ANY_TAINT })
            check(s.conjunctions.none { it.target.tail == Tail.ANY_TAINT })
        }
        program.statementInputs.forEach { summary(it.summary) }
        for (e in program.entryInputs) {
            summary(e.rules.summary)
            check(e.rules.endFacts.edges.isEmpty() && e.rules.endFacts.conjunctions.isEmpty())
            check(e.rules.sinks.all { it.endFacts.isEmpty() })
        }
        for (e in program.exitInputs) {
            summary(e.rules.rules.summary)
            check(e.rules.rules.endFacts.edges.isEmpty() && e.rules.rules.endFacts.conjunctions.isEmpty() &&
                e.rules.rules.sinks.all { it.endFacts.isEmpty() })
        }
        for (row in program.callInputs) {
            check(row.plan.sinks.all { it.endFacts.isEmpty() })
            for (stage in row.plan.stages) when (stage) {
                is CallStage.Edges -> {
                    check(stage.kind != StageKind.ALIASES && stage.kind != StageKind.END_FACTS &&
                        stage.guard == null && stage.trigger == null) {
                        "$VERSION differential excludes aliases, guarded stages, and end-fact triggers"
                    }
                    summary(stage.summary)
                }
                is CallStage.Actions -> check(stage.steps.all { it is CleanStep.Clean }) {
                    "Ordered field/alias actions are covered by current-mode tests, outside this F71 domain"
                }
                is CallStage.Callees -> Unit
            }
        }
        for (record in inputs.records) {
            check(record.premise.fact.tail != Tail.ANY_TAINT && record.conclusion.fact.tail != Tail.ANY_TAINT)
            check(record.premise.exclusion == ExclusionSet.Empty && record.conclusion.exclusion == ExclusionSet.Empty)
        }
    }

    /** Restricted.lean emitM: match the demand mark, intersect locations, copy the ADDED mark. */
    fun emit(d: Pattern, a: Pattern): Pattern? {
        val admitsMark = when (val dm = d.fact.mark) {
            is MarkSlot.Concrete -> a.fact.mark == dm
            is MarkSlot.Star -> (a.fact.mark as? MarkSlot.Concrete)?.mark?.let { it !in dm.excluded } ?: true
        }
        if (d.fact.base != a.fact.base || !admitsMark) return null
        val p = d.fact.path
        val q = a.fact.path
        return when {
            q == p -> {
                val tail = when {
                    a.fact.tail.isAny -> d.fact.tail
                    d.fact.tail.isAny -> a.fact.tail
                    a.fact.tail == Tail.EXACT || d.fact.tail == Tail.EXACT -> Tail.EXACT
                    else -> Tail.STAR
                }
                Pattern(a.fact.copy(tail = tail), if (tail == Tail.STAR) a.exclusion.union(d.exclusion) else ExclusionSet.Empty)
            }
            q.startsWith(p) -> if (d.tailAdmits(q.drop(p.size))) a else null
            p.startsWith(q) -> if (a.tailAdmits(p.drop(q.size)))
                Pattern(d.fact.copy(mark = a.fact.mark), d.exclusion) else null
            else -> null
        }
    }

    /** Earlier spatial-first cleaner, AnyTaintExDefs.lean cleanResX; F75 has not run yet. */
    fun clean(cl: Cleaner, c: Conclusion): CleanOut {
        val pos = cleanPos(cl, c.fact, c.exclusion)
        val selected = cl.mark
        if (pos == Pos.DISJOINT) return CleanOut(listOf(c), null)
        return when (val m = c.fact.mark) {
            is MarkSlot.Concrete -> when {
                selected != null && selected != m.mark -> CleanOut(listOf(c), null)
                pos == Pos.INSIDE -> CleanOut(emptyList(), null)
                else -> CleanOut(concPart(cl, c), null)
            }
            is MarkSlot.Star -> when {
                selected != null -> CleanOut(listOf(c.copy(fact = c.fact.copy(mark = MarkSlot.Star(m.excluded + selected)))),
                    if (pos == Pos.PART) selected else null)
                pos == Pos.INSIDE -> CleanOut(emptyList(), null)
                else -> CleanOut(listOf(normalize(c.fact, ExclusionSet.Empty, true)), null)
            }
        }
    }

    /** Restricted.lean satI (run 1 still uses applicable). No F72 canonical FLOW overlap shortcut. */
    fun satisfies(j: Pattern, a: Pattern, restricted: Boolean): Boolean =
        if (restricted) inside(j, a) else applicable(j, a)

    /** HandoffDefs.lean Cross/CrossB. Generic revEdge is the unchanged ordinary-record table. */
    private fun crossTail(p: Pattern): Boolean = p.fact.tail == Tail.EXACT ||
        (p.fact.tail == Tail.STAR && p.exclusion == ExclusionSet.Empty)

    fun cross(p: Pattern, c: Conclusion): Boolean {
        if (c.demand || !crossTail(p) || (c.fact.mark !is MarkSlot.Star && p.fact.mark !is MarkSlot.Concrete)) return false
        val reversed = reverseRecord(p, c) ?: return false
        return crossTail(reversed.premise)
    }

    fun crossBackward(p: Pattern, c: Conclusion): Boolean {
        if (c.demand) return false
        val r = reverseRecord(p, c) ?: return false
        return cross(r.premise, r.result)
    }

    fun reverseRecord(p: Pattern, c: Conclusion): RecordLeafView? {
        if (c.demand) return null
        check(p.fact.tail != Tail.ANY_TAINT && c.fact.tail != Tail.ANY_TAINT)
        val reversed = revEdge(summaryEdge(p, c)) ?: return null
        return RecordLeafView(Pattern(reversed.from, ExclusionSet.Empty),
            Conclusion(reversed.to, reversed.exclusion, reversed.to.tail == Tail.ANY))
    }

    /** HandoffDefs.lean restrictI/restrictConcI, coded directly rather than invoking current restrict. */
    fun restrict(p: Pattern, c: Conclusion, d: DemandPattern): Conclusion? {
        val out = d.exit ?: return null
        if (!insideLoc(p, d.entry) || !markSub(d.entry.fact.mark, p.fact.mark)) return null
        val meetMarks = when (val a = out.fact.mark) {
            is MarkSlot.Concrete -> when (val b = c.fact.mark) {
                is MarkSlot.Concrete -> a.mark == b.mark
                is MarkSlot.Star -> a.mark !in b.excluded
            }
            is MarkSlot.Star -> (c.fact.mark as? MarkSlot.Concrete)?.mark?.let { it !in a.excluded } ?: true
        }
        if (!meetMarks || out.fact.base != c.fact.base) return null
        val q = c.fact.path
        val s = out.fact.path
        return when {
            q == s -> if (c.fact.tail == Tail.ANY && out.fact.tail == Tail.EXACT)
                c.copy(fact = c.fact.copy(tail = Tail.EXACT)) else c
            q.startsWith(s) -> if (out.tailAdmits(q.drop(s.size))) c else null
            s.startsWith(q) -> when (c.fact.tail) {
                Tail.ANY -> c.copy(fact = c.fact.copy(path = s, tail = if (out.fact.tail == Tail.EXACT) Tail.EXACT else Tail.ANY))
                Tail.STAR -> if (c.exclusion.admits(s.drop(q.size))) c else null
                Tail.EXACT -> null
                Tail.ANY_TAINT -> error("$VERSION differential excludes must records")
            }
            else -> null
        }
    }
}
