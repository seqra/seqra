package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.bidi.ap.*

/** Scalar group interpretation. It does not call the packed AP transfer, MarkGate,
 * runtimeFact, rule group index, or direct-static service. Full rule leaves/IDs remain inputs. */
internal class GroupedScalarMarks(private val table: MarkTable) {
    fun atom(mark: TaintMark): TaintMark = when (val key = table.key(mark)) {
        MarkAtomKey.ControlZero -> mark
        is MarkAtomKey.Group -> mark
        is MarkAtomKey.Full -> table.group(key.mark.group)
    }

    fun slot(mark: MarkSlot): MarkSlot = when (mark) {
        is MarkSlot.Concrete -> MarkSlot.Concrete(atom(mark.mark))
        is MarkSlot.Star -> MarkSlot.Star(MarkExclusions.of(table,
            mark.excluded.ids.map { atom(TaintMark(it)) }))
    }
    fun fact(fact: PathFact): PathFact = fact.copy(mark = slot(fact.mark))
    fun pattern(pattern: Pattern): Pattern = pattern.copy(fact = fact(pattern.fact))
    fun conclusion(value: Conclusion): Conclusion {
        if (value.fact.base == org.opentaint.dataflow.ap.ifds.AccessPathBase.Zero) return value
        val fact = fact(value.fact)
        return value.copy(fact = fact, demand = value.demand || fact.mark is MarkSlot.Concrete || fact.base == STATIC)
    }
    fun edge(edge: PathEdge): PathEdge = edge.copy(from = fact(edge.from), to = fact(edge.to))

    fun apply(value: Conclusion, edge: PathEdge, may: Boolean): EdgeOutcome {
        val input = conclusion(value)
        val form = edge(edge)
        // The scalar core uses full mx to suppress requests. Group interests are
        // unconditional on mx; erase it only for this FLOW->marked request gate.
        val query = if (input.fact.mark is MarkSlot.Star && form.from.mark is MarkSlot.Concrete)
            input.copy(fact = input.fact.copy(mark = MarkSlot.STAR)) else input
        return when (val result = concat(query, form, edgeDemand = may, run1 = true)) {
            is EdgeOutcome.Fact -> EdgeOutcome.Fact(conclusion(result.conclusion))
            else -> result
        }
    }

    fun check(initial: Pattern, value: Conclusion, literal: Pattern): CheckResult {
        val input = conclusion(value)
        val required = pattern(literal)
        val query = if (input.fact.mark is MarkSlot.Star)
            input.copy(fact = input.fact.copy(mark = MarkSlot.STAR)) else input
        return markCheck(initial, query, required, restricted = false)
    }

    fun clean(cleaner: Cleaner, value: Conclusion): CleanOut {
        val input = conclusion(value)
        // One selected full mark cannot remove an entire owner group. Structural
        // RemoveAll keeps the ordinary geometry-sensitive scalar clean operation.
        if (cleaner.mark != null && input.fact.mark is MarkSlot.Concrete)
            return CleanOut(listOf(input), null)
        val selected = cleaner.mark?.let(::atom)
        val out = cleanRes(Cleaner(cleaner.base, cleaner.path, cleaner.reach, selected), input, run1 = true)
        return CleanOut(out.facts.map(::conclusion), out.request?.let(::atom))
    }
}

internal fun selectedExpectedEdge(table: MarkTable, index: Int, edge: ObservedEdge): ObservedEdge {
    if (index != 1) return edge
    val scalar = GroupedScalarMarks(table)
    return edge.copy(premise = edge.premise.mapTo(LinkedHashSet(), scalar::pattern),
        conclusion = scalar.conclusion(edge.conclusion))
}
