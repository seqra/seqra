package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.CallStage
import org.opentaint.dataflow.bidi.interp.CleanStep
import org.opentaint.dataflow.bidi.interp.StatementSummary
import org.opentaint.dataflow.bidi.interp.StageKind
import org.opentaint.dataflow.bidi.interp.Guard
import org.opentaint.dataflow.bidi.driver.Seed

/** F71 concrete-design operations. Explicit opt-ins add argued END forms and refined F69 must flags;
 *  their tests cite those rules separately from the default fb789d0ab finite-program domain. */
internal object HistoricalF71 {
    const val VERSION = "F71 concrete design / proof snapshot fb789d0ab"

    /** Frozen pre-selective-preservation Reference.kt normalizer (fa0bd5887e7b25ef912852a19d701c0a21302bcb).
     * Historical demotion changes the tail; the selected layer-only normalizer must not rewrite this oracle. */
    fun normalize(f: PathFact, exclusion: ExclusionSet, demand: Boolean): Conclusion = when {
        f.tail == Tail.ANY -> Conclusion(f, ExclusionSet.Empty, demand = true)
        f.tail == Tail.ANY_TAINT -> {
            check(f.mark is MarkSlot.Concrete)
            if (demand) Conclusion(f.copy(tail = Tail.ANY), ExclusionSet.Empty, demand = true)
            else Conclusion(f, exclusion, demand = false)
        }
        f.tail != Tail.STAR || (f.mark is MarkSlot.Star && !demand) -> Conclusion(f, exclusion, demand)
        else -> Conclusion(f.copy(tail = Tail.ANY), ExclusionSet.Empty, demand = true)
    }

    fun validate(program: FiniteProgramFixtures, inputs: ProgramRunInputs,
                 allowEndFacts: Boolean = false, allowMustSources: Boolean = false) {
        fun summary(s: StatementSummary) {
            check(s.sources.isEmpty()) { "$VERSION differential excludes compiled source predicates" }
            for (e in s.edges) if (e.isSource) check(e.forward.to.tail == Tail.EXACT ||
                (allowMustSources && e.forward.to.tail == Tail.ANY_TAINT && e.forward.to.mark is MarkSlot.Concrete)) {
                "$VERSION differential excludes must sources and F76 must-record views"
            }
            check(s.edges.none { it.forward.from.tail == Tail.ANY_TAINT ||
                (it.forward.to.tail == Tail.ANY_TAINT && !(allowMustSources && it.isSource)) })
            check(s.conjunctions.none { it.target.tail == Tail.ANY_TAINT })
        }
        fun sink(s: org.opentaint.dataflow.bidi.interp.SinkRule) {
            check(s.predicate == null) { "$VERSION differential excludes compiled sink predicates" }
            summary(StatementSummary(emptySet(), s.endFacts, emptyList(), emptyMap()))
        }
        check(inputs.seeds.none { it is Seed.PredicateSink }) {
            "$VERSION differential excludes compiled full-condition sink seeds"
        }
        program.statementInputs.forEach { summary(it.summary) }
        for (e in program.entryInputs) {
            summary(e.rules.summary)
            summary(e.rules.endFacts)
            e.rules.sinks.forEach(::sink)
            check(e.rules.endFacts.conjunctions.isEmpty())
            if (!allowEndFacts) check(e.rules.endFacts.edges.isEmpty() && e.rules.sinks.all { it.endFacts.isEmpty() })
        }
        for (e in program.exitInputs) {
            summary(e.rules.rules.summary)
            summary(e.rules.rules.endFacts)
            e.rules.rules.sinks.forEach(::sink)
            check(e.rules.rules.endFacts.conjunctions.isEmpty())
            if (!allowEndFacts) check(e.rules.rules.endFacts.edges.isEmpty() && e.rules.rules.sinks.all { it.endFacts.isEmpty() })
        }
        for (row in program.callInputs) {
            row.plan.sinks.forEach(::sink)
            if (!allowEndFacts) check(row.plan.sinks.all { it.endFacts.isEmpty() })
            for (stage in row.plan.stages) when (stage) {
                is CallStage.Edges -> {
                    val permittedEnd = allowEndFacts && stage.kind == StageKind.END_FACTS &&
                        (stage.guard == null || stage.guard is Guard.SinkTriggered)
                    check(stage.kind != StageKind.ALIASES && (permittedEnd ||
                        (stage.kind != StageKind.END_FACTS && stage.guard == null && stage.trigger == null))) {
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
            if (!allowMustSources) {
                check(record.premise.fact.tail != Tail.ANY_TAINT && record.conclusion.fact.tail != Tail.ANY_TAINT)
                check(record.premise.exclusion == ExclusionSet.Empty && record.conclusion.exclusion == ExclusionSet.Empty)
            } else {
                check(!record.conclusion.demand)
                check(record.premise.exclusion == ExclusionSet.Empty || record.premise.fact.tail == Tail.STAR ||
                    record.premise.fact.tail == Tail.ANY_TAINT)
                check(record.conclusion.exclusion == ExclusionSet.Empty || record.conclusion.fact.tail == Tail.STAR ||
                    record.conclusion.fact.tail == Tail.ANY_TAINT)
            }
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
        val result = when {
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
        // AnyTaintExDefs.emitTX: the old model stores a separate must flag on an emitted any tail.
        // A same-path must keeps its own E; entering an admitted deeper chain forgets the old E.
        return if (a.fact.tail == Tail.ANY_TAINT && result?.fact?.tail == Tail.ANY)
            result.copy(fact = result.fact.copy(tail = Tail.ANY_TAINT),
                exclusion = if (p == q) a.exclusion else result.exclusion) else result
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
                else -> {
                    val q = cl.path?.toList().orEmpty()
                    val p = c.fact.path
                    if (c.fact.tail == Tail.ANY_TAINT && !c.demand && q.size == p.size + 1 &&
                        q.startsWith(p) && cl.reach != CleanReach.EXACT) {
                        val field = q.last()
                        val kept = c.copy(exclusion = c.exclusion.union(ExclusionSet.of(field)))
                        CleanOut(if (cl.reach == CleanReach.BELOW) listOf(kept,
                            Conclusion(c.fact.copy(path = q, tail = Tail.EXACT), ExclusionSet.Empty, false)) else listOf(kept), null)
                    } else CleanOut(concPart(cl, c), null)
                }
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
        if (c.demand || c.fact.tail == Tail.ANY_TAINT || !crossTail(p) ||
            (c.fact.mark !is MarkSlot.Star && p.fact.mark !is MarkSlot.Concrete)) return false
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
        // F69 amendment R3: old native must/E leaves have no F76 STAR read view.
        if (p.fact.tail == Tail.ANY_TAINT ||
            (c.fact.tail == Tail.ANY_TAINT && c.exclusion != ExclusionSet.Empty)) return null
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
            q == s -> when {
                c.fact.tail.isAny && out.fact.tail == Tail.EXACT ->
                    c.copy(fact = c.fact.copy(tail = Tail.EXACT), exclusion = ExclusionSet.Empty)
                c.fact.tail == Tail.ANY_TAINT && out.fact.tail == Tail.STAR ->
                    c.copy(exclusion = c.exclusion.union(out.exclusion))
                else -> c
            }
            q.startsWith(s) -> if (out.tailAdmits(q.drop(s.size))) c else null
            s.startsWith(q) -> when (c.fact.tail) {
                Tail.ANY -> c.copy(fact = c.fact.copy(path = s, tail = if (out.fact.tail == Tail.EXACT) Tail.EXACT else Tail.ANY))
                Tail.STAR -> if (c.exclusion.admits(s.drop(q.size))) c else null
                Tail.EXACT -> null
                Tail.ANY_TAINT -> if (c.exclusion.admits(s.drop(q.size))) c.copy(
                    fact = c.fact.copy(path = s, tail = if (out.fact.tail == Tail.EXACT) Tail.EXACT else Tail.ANY_TAINT),
                    exclusion = if (out.fact.tail == Tail.STAR) out.exclusion else ExclusionSet.Empty) else null
            }
            else -> null
        }
    }
}
