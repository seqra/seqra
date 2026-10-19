package org.opentaint.dataflow.bidi.interp

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.Cleaner
import org.opentaint.dataflow.bidi.ap.MarkSet
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.Pattern
import org.opentaint.dataflow.bidi.ap.Tail
import org.opentaint.dataflow.bidi.ap.TaintMark
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink

/** One stable sink alternative. Repeated patterns retain separate literal slots. */
class SinkRule(
    val rule: CommonTaintConfigurationSink,
    val alternative: Int,
    patterns: List<Pattern>,
    endFacts: List<MicroEdge>,
    val occurrenceId: RuleOccurrenceId? = null,
    val predicate: RulePredicate? = null,
) {
    /** Legacy/manual forms keep their fresh object scope; JVM predicate forms supply a value key. */
    val joinKey: Any get() = occurrenceId ?: this
    val patterns: List<Pattern> = snapshotPatterns(patterns)
    /** Keep cached micro edges and their original forward identities. Their producers own their paths. */
    val endFacts: List<MicroEdge> = ownedList(endFacts)

    init {
        require(predicate == null || occurrenceId != null) { "A compiled predicate needs a stable rule occurrence" }
        check(this.patterns.isNotEmpty()) { "A sink has a positive literal or the zero pattern" }
        check(this.patterns.all {
            it.fact.mark is MarkSlot.Concrete && (it.fact.tail == Tail.EXACT || it.fact.tail == Tail.ANY)
        }) { "A sink literal has a concrete mark and an exact or may tail" }
    }

    val unconditional: Boolean get() = patterns.size == 1 && patterns[0].fact.base == AccessPathBase.Zero
    val conjunctive: Boolean get() = patterns.size >= 2

    /** Zero already enters every method; each other literal seeds its original exact or may requirement. */
    fun seedPatterns(): List<Pattern> = if (unconditional) emptyList() else patterns
}

private fun snapshotPatterns(patterns: List<Pattern>): List<Pattern> {
    val iterator = patterns.iterator()
    return ownedList(patterns.size) {
        val p = iterator.next()
        p.copy(fact = p.fact.copy(path = ownedList(p.fact.path)))
    }
}

/** Statement-mode rule actions and GEN end facts share stable sink alternatives. */
class RuleStatement(val summary: StatementSummary, val endFacts: StatementSummary, sinks: List<SinkRule>) {
    val sinks: List<SinkRule> = ownedList(sinks)

    /** Reverse the two action modes separately; untouched forward GEN targets need A5 pass-over identities. */
    fun reversed(): RuleStatement {
        val s = summary.reversed()
        val revEnd = StatementSummary(emptySet(), endFacts.edges.map { MicroEdge(revOrFail(it.edge), it.forward, it.conjunctive, it.actionId) },
            emptyList(), emptyMap())
        return RuleStatement(s.withIdentities(passOverBases(endFacts.targets, s.touched)), revEnd, sinks)
    }

    companion object { val EMPTY = RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, emptyList()) }
}

/** Forward normal-exit removals. Exceptional exits use no drop flag and an empty entry-mark set. */
class ExitRules(val rules: RuleStatement, val globalStateDrop: Boolean, entryMarks: Set<TaintMark>) {
    val entryMarks: Set<TaintMark> = ownedSet(entryMarks)
    private val entryMarkSet = if (this.entryMarks.isEmpty()) MarkSet.EMPTY
        else MarkSet(this.entryMarks.map { it.id }.toIntArray())

    /** G2 removals belong only to the forward exit; the reversed value contains rule actions alone. */
    fun reversed(): RuleStatement = rules.reversed()

    fun entryMarkRemoval(base: AccessPathBase): MarkSet? =
        if (entryMarkSet.isEmpty || (base != AccessPathBase.This && base !is AccessPathBase.Argument)) null else entryMarkSet

    companion object { val EMPTY = ExitRules(RuleStatement.EMPTY, false, emptySet()) }
}

/** Generated callees supply callee-coordinate cleaners for the cached ordered Actions stage. */
interface CallCleaners {
    val callCleaners: List<Cleaner>
}

/** Field actions keep their read and strong write as statements around one fresh temporary cleaner. */
sealed interface CleanStep {
    fun reversed(): CleanStep

    class Clean(val cleaner: Cleaner) : CleanStep {
        override fun reversed() = this
    }

    class Kill(val keepEdges: StatementSummary) : CleanStep {
        override fun reversed() = Kill(keepEdges.reversed())
    }

    class Field(val temporary: AccessPathBase, val read: StatementSummary,
                val cleaner: Cleaner, val write: StatementSummary) : CleanStep {
        init { check(cleaner.base == temporary && cleaner.path == null) { "A field cleaner acts at its temporary root" } }
        override fun reversed() = Field(temporary, write.reversed(), cleaner, read.reversed())
    }
}
