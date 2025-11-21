package org.opentaint.org.opentaint.semgrep.pattern

import org.opentaint.org.opentaint.semgrep.pattern.conversion.SemgrepPatternActionList

data class RuleWithFocusMetaVars<R>(val rule: R, val focusMetaVars: Set<String>){
    fun <T> map(body: (R) -> T) = RuleWithFocusMetaVars(body(rule), focusMetaVars)
    fun <T> flatMap(body: (R) -> List<T>) = body(rule).map { RuleWithFocusMetaVars(it, focusMetaVars) }
}

data class RawSemgrepRule(
    val patterns: List<String>,
    val patternNots: List<String>,
    val patternInsides: List<String>,
    val patternNotInsides: List<String>,
    val metaVariablePatterns: Map<String, Set<String>>,
    val metaVariableRegex: Map<String, Set<String>>,
)

data class ParsedSemgprepRule(
    val patterns: List<SemgrepJavaPattern>,
    val patternNots: List<SemgrepJavaPattern>,
    val patternInsides: List<SemgrepJavaPattern>,
    val patternNotInsides: List<SemgrepJavaPattern>,
    val metaVariablePatterns: Map<String, List<SemgrepJavaPattern>>,
    val metaVariableRegex: Map<String, Set<String>>,
)

data class NormalizedSemgrepRule(
    val patterns: List<SemgrepJavaPattern>,
    val patternNots: List<SemgrepJavaPattern>,
    val patternInsides: List<SemgrepJavaPattern>,
    val patternNotInsides: List<SemgrepJavaPattern>,
)

inline fun NormalizedSemgrepRule.map(
    body: (SemgrepJavaPattern) -> SemgrepJavaPattern
): NormalizedSemgrepRule = NormalizedSemgrepRule(
    patterns.map { body(it) },
    patternNots.map { body(it) },
    patternInsides.map { body(it) },
    patternNotInsides.map { body(it) },
)

data class ActionListSemgrepRule(
    val patterns: List<SemgrepPatternActionList>,
    val patternNots: List<SemgrepPatternActionList>,
    val patternInsides: List<SemgrepPatternActionList>,
    val patternNotInsides: List<SemgrepPatternActionList>,
) {
    fun modify(
        patterns: List<SemgrepPatternActionList>? = null,
        patternNots: List<SemgrepPatternActionList>? = null,
        patternInsides: List<SemgrepPatternActionList>? = null,
        patternNotInsides: List<SemgrepPatternActionList>? = null,
    ) = ActionListSemgrepRule(
        patterns = patterns ?: this.patterns,
        patternNots = patternNots ?: this.patternNots,
        patternInsides = patternInsides ?: this.patternInsides,
        patternNotInsides = patternNotInsides ?: this.patternNotInsides,
    )
}
