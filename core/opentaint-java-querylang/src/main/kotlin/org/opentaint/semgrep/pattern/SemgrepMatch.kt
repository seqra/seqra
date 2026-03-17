package org.opentaint.semgrep.pattern

import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.jvm.graph.JApplicationGraph

data class SemgrepMatch(
    val exprMetavars: Map<String, Pair<JIRExpr, ExprPosition?>>,
    val strMetavars: Map<String, String>,
) {
    companion object {
        val empty = SemgrepMatch(emptyMap(), emptyMap())
    }
}

data class ExprPosition(
    val inst: JIRInst,
    val isLValue: Boolean,
)

class SemgrepMatchingResult(val matches: Set<SemgrepMatch>) {
    val isMatch: Boolean
        get() = matches.isNotEmpty()

    companion object {
        val singleEmptyMatch = SemgrepMatchingResult(setOf(SemgrepMatch.empty))
        val noMatch = SemgrepMatchingResult(emptySet())

        fun single(match: SemgrepMatch) = SemgrepMatchingResult(setOf(match))
    }
}

enum class LocalVarStrategy {
    MAY,
    MUST,
}

fun mergeLocalVarVariantMatches(
    strategy: LocalVarStrategy,
    matches: Collection<SemgrepMatchingResult>,
): SemgrepMatchingResult =
    when (strategy) {
        LocalVarStrategy.MUST -> TODO()
        LocalVarStrategy.MAY -> {
            SemgrepMatchingResult(matches.flatMap { it.matches }.toSet())
        }
    }

private fun mergeMatchesIfNoConflict(
    graph: JApplicationGraph,
    strategy: LocalVarStrategy,
    first: SemgrepMatch,
    second: SemgrepMatch,
): SemgrepMatch? {
    for ((name, value) in first.exprMetavars.entries) {
        val anotherValue = second.exprMetavars[name]
        val strValue = second.strMetavars[name]
        if (strValue != null) {
            return null
        }
        if (anotherValue != null && !checkJIRExprConsistency(graph, strategy, value, anotherValue)) {
            return null
        }
    }

    for ((name, value) in first.strMetavars.entries) {
        val anotherValue = second.strMetavars[name]
        val exprValue = second.exprMetavars[name]
        if (exprValue != null) {
            return null
        }
        if (anotherValue != null && anotherValue != value) {
            return null
        }
    }

    return SemgrepMatch(
        exprMetavars = first.exprMetavars + second.exprMetavars,
        strMetavars = first.strMetavars + second.strMetavars,
    )
}

fun mergeMatchesForPartsOfPattern(
    graph: JApplicationGraph,
    strategy: LocalVarStrategy,
    first: SemgrepMatchingResult,
    second: SemgrepMatchingResult,
): SemgrepMatchingResult {
    val result = mutableSetOf<SemgrepMatch>()
    for (firstMatch in first.matches) {
        for (secondMatch in second.matches) {
            val match = mergeMatchesIfNoConflict(graph, strategy, firstMatch, secondMatch)
            match?.let { result.add(it) }
        }
    }
    return SemgrepMatchingResult(result)
}

fun uniteMatches(first: SemgrepMatchingResult, second: SemgrepMatchingResult): SemgrepMatchingResult =
    SemgrepMatchingResult(first.matches + second.matches)
