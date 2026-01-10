package org.opentaint.org.opentaint.semgrep.pattern.conversion.automata

import org.opentaint.semgrep.pattern.ActionListSemgrepRule
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternActionList

class RuleDebugStats {
    private val stats = mutableListOf<RuleStats>()

    private data class RuleStats(
        val patterns: List<PatternStats>,
        val patternNots: List<PatternStats>,
        val patternInsides: List<PatternStats>,
        val patternNotInsides: List<PatternStats>,
    )

    fun collectRuleStats(rule: ActionListSemgrepRule) {
        stats += RuleStats(
            patterns = rule.patterns.map(::collectPatternStats),
            patternNots = rule.patternNots.map(::collectPatternStats),
            patternInsides = rule.patternInsides.map(::collectPatternStats),
            patternNotInsides = rule.patternNotInsides.map(::collectPatternStats),
        )
    }

    private data class PatternStats(
        val call: Count,
        val signature: Count,
        val exit: Count,
        val ellipsis: EllipsisType
    )

    private fun collectPatternStats(al: SemgrepPatternActionList): PatternStats {
        val actionStats = hashMapOf<ActionType, Int>()
        al.actions.forEach { actionStats(it, actionStats) }

        val ellipsisType = if (al.hasEllipsisInTheEnd) {
            if (al.hasEllipsisInTheBeginning) {
                EllipsisType.BEGIN_END
            } else {
                EllipsisType.END
            }
        } else {
            if (al.hasEllipsisInTheBeginning) {
                EllipsisType.BEGIN
            } else {
                EllipsisType.NO
            }
        }

        return PatternStats(
            call = actionStats.cnt(ActionType.CALL),
            signature = actionStats.cnt(ActionType.SIGNATURE),
            exit = actionStats.cnt(ActionType.EXIT),
            ellipsisType
        )
    }

    private fun actionStats(action: SemgrepPatternAction, stats: MutableMap<ActionType, Int>) {
        when (action) {
            is SemgrepPatternAction.ConstructorCall,
            is SemgrepPatternAction.MethodCall -> stats.inc(ActionType.CALL)

            is SemgrepPatternAction.MethodExit -> stats.inc(ActionType.EXIT)
            is SemgrepPatternAction.MethodSignature -> stats.inc(ActionType.SIGNATURE)
        }
    }

    private fun <K> MutableMap<K, Int>.inc(key: K) = compute(key) { _, value -> (value ?: 0) + 1 }

    private fun <K> MutableMap<K, Int>.cnt(key: K): Count {
        val value = this[key] ?: 0
        return when (value) {
            0 -> Count.ZERO
            1 -> Count.ONE
            else -> Count.MANY
        }
    }

    private enum class ActionType {
        CALL, EXIT, SIGNATURE
    }

    private enum class EllipsisType {
        NO, BEGIN, END, BEGIN_END
    }

    private enum class Count {
        ZERO, ONE, MANY
    }

    fun evalCurrentStats(): Int {
        val possiblePatternStats = generateAllPossiblePatternStats()
        val uncoveredPattern = possiblePatternStats.toMutableSet()
        val uncoveredPatternNot = possiblePatternStats.toMutableSet()
        val uncoveredPatternInside = possiblePatternStats.toMutableSet()
        val uncoveredPatternNotInside = possiblePatternStats.toMutableSet()

        for (stat in stats) {
            uncoveredPattern.removeAll(stat.patterns)
            uncoveredPatternNot.removeAll(stat.patternNots)
            uncoveredPatternInside.removeAll(stat.patternInsides)
            uncoveredPatternNotInside.removeAll(stat.patternNotInsides)
        }

        val uncoveredPatterns = listOf(
            uncoveredPattern, uncoveredPatternNot, uncoveredPatternInside, uncoveredPatternNotInside
        )
        return uncoveredPatterns.sumOf { it.size }
    }

    private fun randomSample(
        patterns: Collection<PatternStats>,
        patternNots: Collection<PatternStats>,
        patternInsides: Collection<PatternStats>,
        patternNotInsides: Collection<PatternStats>,
    ): RuleStats = RuleStats(
        patterns = listOfNotNull(patterns.randomOrNull()),
        patternNots = listOfNotNull(patternNots.randomOrNull()),
        patternInsides = listOfNotNull(patternInsides.randomOrNull()),
        patternNotInsides = listOfNotNull(patternNotInsides.randomOrNull())
    )

    private fun generateAllPossiblePatternStats(): List<PatternStats> {
        val possibleEllipsis = EllipsisType.entries
        val possibleCall = listOf(Count.ZERO, Count.ONE, /*Count.MANY*/)
        val possibleSignatures = listOf(Count.ZERO, Count.ONE)
        val possibleExit = listOf(Count.ZERO, Count.ONE)

        val results = mutableListOf<PatternStats>()
        possibleEllipsis.forEach { el ->
            possibleCall.forEach { call ->
                possibleSignatures.forEach { sig ->
                    for (exit in possibleExit) {
                        if (call == Count.ZERO && sig == Count.ZERO && exit == Count.ZERO) {
                            continue
                        }

                        if (sig != Count.ZERO && el != EllipsisType.END) {
                            continue
                        }

                        if (exit != Count.ZERO && (el == EllipsisType.END || el == EllipsisType.BEGIN_END)) {
                            continue
                        }

                        results += PatternStats(call, sig, exit, el)
                    }
                }
            }
        }
        return results
    }
}
