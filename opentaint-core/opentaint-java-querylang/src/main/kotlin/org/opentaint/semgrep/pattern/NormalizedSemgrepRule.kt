package org.opentaint.semgrep.pattern

import org.opentaint.semgrep.pattern.conversion.SemgrepPatternActionList

data class RuleWithMetaVars<R, C>(val rule: R, val metaVarInfo: C) {
    fun <T> map(body: (R) -> T) = RuleWithMetaVars(body(rule), metaVarInfo)
    fun <T> flatMap(body: (R) -> List<T>) = body(rule).map { RuleWithMetaVars(it, metaVarInfo) }
}

data class RawSemgrepRule(
    val patterns: List<String>,
    val patternNots: List<String>,
    val patternInsides: List<String>,
    val patternNotInsides: List<String>,
)

sealed interface RawMetaVarConstraint {
    data class RegExp(val regex: String) : RawMetaVarConstraint
    data class Pattern(val value: String) : RawMetaVarConstraint
}

data class RawMetaVarInfo(
    val focusMetaVars: Set<String>,
    val metaVariableConstraints: Map<String, MetaVarConstraintFormula<RawMetaVarConstraint>>,
)

sealed interface MetaVarConstraint {
    data class RegExp(val regex: String) : MetaVarConstraint
    data class Concrete(val value: String) : MetaVarConstraint
}

data class MetaVarConstraints(
    val constraint: MetaVarConstraintFormula<MetaVarConstraint>
)

data class ResolvedMetaVarInfo(
    val focusMetaVars: Set<String>,
    val metaVarConstraints: Map<String, MetaVarConstraints>
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
