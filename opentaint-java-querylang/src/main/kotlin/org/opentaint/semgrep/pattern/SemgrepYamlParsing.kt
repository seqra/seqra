package org.opentaint.org.opentaint.semgrep.pattern

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opentaint.org.opentaint.semgrep.pattern.conversion.cartesianProductMapTo

@Serializable
data class SemgrepYamlRuleSet(
    val rules: List<SemgrepYamlRule>,
)

@Serializable
data class SemgrepYamlRule(
    val id: String,
    val languages: List<String>,
    val pattern: String? = null,
    val mode: String? = null,
    val patterns: List<ComplexPattern> = emptyList(),
    @SerialName("pattern-either")
    val patternEither: List<ComplexPattern> = emptyList(),
    val message: String,
    val severity: String,
    val metadata: YamlMap? = null,
    @SerialName("pattern-sources")
    val patternSources: List<ComplexPattern> = emptyList(),
    @SerialName("pattern-sinks")
    val patternSinks: List<ComplexPattern> = emptyList(),
    @SerialName("pattern-propagators")
    val patternPropagators: List<PatternPropagator> = emptyList(),
    @SerialName("pattern-sanitizers")
    val patternSanitizers: List<ComplexPattern> = emptyList(),
)

@Serializable
data class ComplexPattern(
    @SerialName("pattern-either")
    val patternEither: List<ComplexPattern> = emptyList(),
    val pattern: String? = null,
    val patterns: List<ComplexPattern> = emptyList(),
    @SerialName("pattern-inside")
    val patternInside: String? = null,
    @SerialName("pattern-not")
    val patternNot: String? = null,
    @SerialName("pattern-not-inside")
    val patternNotInside: String? = null,
    @SerialName("metavariable-regex")
    val metavariableRegex: MetavariableRegexInfo? = null,
    @SerialName("metavariable-pattern")
    val metavariablePattern: MetavariablePatternInfo? = null,
    @SerialName("metavariable-comparison")
    val metavariableComparison: MetavariablePatternInfo? = null,
    @SerialName("pattern-regex")
    val patternRegex: String? = null,
    @SerialName("pattern-not-regex")
    val patternNotRegex: String? = null,
    @SerialName("focus-metavariable")
    val focusMetavariable: YamlNode? = null,
)

@Serializable
data class MetavariableRegexInfo(
    val metavariable: String,
    val regex: String,
)

@Serializable
data class MetavariablePatternInfo(
    val metavariable: String,

    // todo: complex pattern inlined here
    @SerialName("pattern-either")
    val patternEither: List<ComplexPattern> = emptyList(),
    val pattern: String? = null,
    val patterns: List<ComplexPattern> = emptyList(),
    @SerialName("pattern-inside")
    val patternInside: String? = null,
    @SerialName("pattern-not")
    val patternNot: String? = null,
    @SerialName("pattern-not-inside")
    val patternNotInside: String? = null,
    @SerialName("metavariable-regex")
    val metavariableRegex: MetavariableRegexInfo? = null,
    @SerialName("metavariable-pattern")
    val metavariablePattern: MetavariablePatternInfo? = null,
    @SerialName("metavariable-comparison")
    val metavariableComparison: MetavariablePatternInfo? = null,
    @SerialName("pattern-regex")
    val patternRegex: String? = null,
    @SerialName("pattern-not-regex")
    val patternNotRegex: String? = null,
    @SerialName("focus-metavariable")
    val focusMetavariable: YamlNode? = null,
) {
    fun pattern() = ComplexPattern(
        patternEither, pattern, patterns, patternInside, patternNot,
        patternNotInside, metavariableRegex, metavariablePattern,
        metavariableComparison, patternRegex, patternNotRegex, focusMetavariable
    )
}

@Serializable
data class PatternPropagator(
    val from: String,
    val to: String,

    // todo: complex pattern inlined here
    @SerialName("pattern-either")
    val patternEither: List<ComplexPattern> = emptyList(),
    val pattern: String? = null,
    val patterns: List<ComplexPattern> = emptyList(),
    @SerialName("pattern-inside")
    val patternInside: String? = null,
    @SerialName("pattern-not")
    val patternNot: String? = null,
    @SerialName("pattern-not-inside")
    val patternNotInside: String? = null,
    @SerialName("metavariable-regex")
    val metavariableRegex: MetavariableRegexInfo? = null,
    @SerialName("metavariable-pattern")
    val metavariablePattern: MetavariablePatternInfo? = null,
    @SerialName("metavariable-comparison")
    val metavariableComparison: MetavariablePatternInfo? = null,
    @SerialName("pattern-regex")
    val patternRegex: String? = null,
    @SerialName("pattern-not-regex")
    val patternNotRegex: String? = null,
    @SerialName("focus-metavariable")
    val focusMetavariable: YamlNode? = null,
) {
    fun pattern() = ComplexPattern(
        patternEither, pattern, patterns, patternInside, patternNot,
        patternNotInside, metavariableRegex, metavariablePattern,
        metavariableComparison, patternRegex, patternNotRegex, focusMetavariable
    )
}

sealed interface Formula {
    data class LeafPattern(val pattern: String) : Formula
    class And(val children: List<Formula>) : Formula
    class Or(val children: List<Formula>) : Formula
    class Not(val child: Formula) : Formula
    class Inside(val child: Formula) : Formula
    data class MetavarRegex(val name: String, val regex: String) : Formula
    data class MetavarFocus(val name: String) : Formula
    data class MetavarPattern(val name: String, val formula: Formula) : Formula
    data class MetavarCond(val name: String) : Formula // TODO
    data class Regex(val pattern: String) : Formula
}

fun collectPatterns(formula: Formula): List<String> =
    when (formula) {
        is Formula.LeafPattern -> listOf(formula.pattern)
        is Formula.And -> formula.children.flatMap { collectPatterns(it) }
        is Formula.Or -> formula.children.flatMap { collectPatterns(it) }
        is Formula.Not -> collectPatterns(formula.child)
        is Formula.Inside -> collectPatterns(formula.child)
        is Formula.MetavarCond,
        is Formula.MetavarRegex,
        is Formula.MetavarPattern,
        is Formula.Regex,
        is Formula.MetavarFocus -> emptyList()
    }

private val yaml = Yaml(
    configuration = YamlConfiguration(
        strictMode = false,
    )
)

fun parseSemgrepYaml(yml: String): SemgrepYamlRuleSet =
    yaml.decodeFromString(SemgrepYamlRuleSet.serializer(), yml)

fun yamlToSemgrepRule(yml: String): List<SemgrepYamlRule> {
    val ruleSet = parseSemgrepYaml(yml)
    return ruleSet.rules.filter { rule ->
        "java" in rule.languages.map { it.lowercase() }
    }
}

fun parseSemgrepRule(rule: SemgrepYamlRule): SemgrepRule<Formula> =
    if (rule.mode == "taint") {
        parseTaintRule(rule)
    } else {
        SemgrepMatchingRule(listOf(parseMatchingRuleFormula(rule)))
    }

private fun parseTaintRule(rule: SemgrepYamlRule): SemgrepTaintRule<Formula> =
    SemgrepTaintRule(
        sources = rule.patternSources.map { complexPatternToFormula(it) },
        sinks = rule.patternSinks.map { complexPatternToFormula(it) },
        propagators = rule.patternPropagators.map {
            SemgrepTaintPropagator(it.from, it.to, complexPatternToFormula(it.pattern()))
        },
        sanitizers = rule.patternSanitizers.map { complexPatternToFormula(it) }
    )

private fun parseMatchingRuleFormula(rule: SemgrepYamlRule): Formula =
    if (rule.pattern != null) {
        Formula.LeafPattern(rule.pattern)
    } else if (rule.patterns.isNotEmpty()) {
        val children = rule.patterns.map { complexPatternToFormula(it) }
        Formula.And(children)
    } else if (rule.patternEither.isNotEmpty()) {
        val children = rule.patternEither.map { complexPatternToFormula(it) }
        Formula.Or(children)
    } else {
        TODO()
    }

fun convertToRawRule(rule: SemgrepRule<Formula>): SemgrepRule<RuleWithFocusMetaVars<RawSemgrepRule>> {
    return rule.flatMap { convertToRawRule(it) }
}

fun convertToRawRule(formula: Formula): List<RuleWithFocusMetaVars<RawSemgrepRule>> {
    val formulaDnf = formula.normalizeToNNF(negated = false).toDNF()
    return formulaDnf.mapNotNull { convertToNormalizedRule(it.literals) }
}

private fun convertToNormalizedRule(literals: List<NormalizedFormula.Literal>): RuleWithFocusMetaVars<RawSemgrepRule>? {
    val patterns = mutableListOf<String>()
    val patternNots = mutableListOf<String>()
    val patternInsides = mutableListOf<String>()
    val patternNotInsides = mutableListOf<String>()
    val metaVariablePatterns = hashMapOf<String, MutableSet<String>>()
    val metaVariableRegex = hashMapOf<String, MutableSet<String>>()
    val focusMetaVars = hashSetOf<String>()

    for (literal in literals) {
        when (val f = literal.formula) {
            is Formula.LeafPattern -> if (literal.negated) {
                patternNots.add(f.pattern)
            } else {
                patterns.add(f.pattern)
            }

            is Formula.Inside -> {
                val inside = f.child
                val insideAsLeaf = inside as? Formula.LeafPattern
                    ?: TODO()
                if (literal.negated) {
                    patternNotInsides.add(insideAsLeaf.pattern)
                } else {
                    patternInsides.add(insideAsLeaf.pattern)
                }
            }

            is Formula.MetavarFocus -> {
                // todo
                if (literal.negated) return null

                focusMetaVars.add(f.name)
            }

            is Formula.MetavarCond -> {
                // todo
                return null
            }

            is Formula.MetavarPattern -> {
                if (literal.negated) {
                    // todo
                    return null
                } else {
                    if (f.formula is Formula.LeafPattern) {
                        metaVariablePatterns.getOrPut(f.name, ::hashSetOf).add(f.formula.pattern)
                    } else {
                        // todo
                        return null
                    }
                }
            }

            is Formula.MetavarRegex -> {
                if (literal.negated) {
                    // todo
                    return null
                } else {
                    metaVariableRegex.getOrPut(f.name, ::hashSetOf).add(f.regex)
                }
            }
            is Formula.Regex -> {
                // todo
                return null
            }

            is Formula.Not,
            is Formula.And,
            is Formula.Or -> error("Unexpected formula in dnf")
        }
    }

    return RuleWithFocusMetaVars(
        RawSemgrepRule(
            patterns, patternNots, patternInsides, patternNotInsides,
            metaVariablePatterns, metaVariableRegex
        ),
        focusMetaVars
    )
}

private sealed interface NormalizedFormula {
    data class Literal(val formula: Formula, val negated: Boolean) : NormalizedFormula
    data class And(val children: List<NormalizedFormula>) : NormalizedFormula
    data class Or(val children: List<NormalizedFormula>) : NormalizedFormula
}

private data class NormalizedFormulaCube(val literals: List<NormalizedFormula.Literal>)

private fun NormalizedFormulaCube.toFormula(): Formula {
    val args = literals.map { if (it.negated) Formula.Not(it.formula) else it.formula }
    return when (args.size) {
        1 -> args.first()
        else -> Formula.And(args)
    }
}

private fun Formula.normalizeToNNF(negated: Boolean): NormalizedFormula = when (this) {
    is Formula.Inside, // todo: handle inside nested formula
    is Formula.LeafPattern,
    is Formula.MetavarCond,
    is Formula.MetavarRegex,
    is Formula.MetavarFocus,
    is Formula.Regex -> NormalizedFormula.Literal(this, negated)

    is Formula.MetavarPattern -> {
        if (!negated) {
            val nestedDnf = formula.normalizeToNNF(negated = false).toDNF()
            val lits = nestedDnf.map {
                val f = Formula.MetavarPattern(name, it.toFormula())
                NormalizedFormula.Literal(f, negated)
            }
            NormalizedFormula.Or(lits)
        } else {
            NormalizedFormula.Literal(this, negated)
        }
    }

    is Formula.Not -> child.normalizeToNNF(!negated)
    is Formula.And -> if (!negated) {
        NormalizedFormula.And(children.map { it.normalizeToNNF(negated = false) })
    } else {
        NormalizedFormula.Or(children.map { it.normalizeToNNF(negated = true) })
    }

    is Formula.Or -> if (!negated) {
        NormalizedFormula.Or(children.map { it.normalizeToNNF(negated = false) })
    } else {
        NormalizedFormula.And(children.map { it.normalizeToNNF(negated = true) })
    }
}

private fun NormalizedFormula.toDNF(): List<NormalizedFormulaCube> = when (this) {
    is NormalizedFormula.Literal -> listOf(NormalizedFormulaCube(listOf(this)))
    is NormalizedFormula.Or -> children.flatMap { it.toDNF() }

    is NormalizedFormula.And -> {
        val dnfChildren = children.map { it.toDNF() }
        val resultCubes = mutableListOf<NormalizedFormulaCube>()
        dnfChildren.cartesianProductMapTo { cubes ->
            val literals = mutableListOf<NormalizedFormula.Literal>()
            cubes.forEach { literals.addAll(it.literals) }
            resultCubes += NormalizedFormulaCube(literals)
        }
        resultCubes
    }
}

private fun complexPatternToFormula(pattern: ComplexPattern): Formula {
    return if (pattern.patternEither.isNotEmpty()) {
        val children = pattern.patternEither.map { complexPatternToFormula(it) }
        Formula.Or(children)
    } else if (pattern.pattern != null) {
        Formula.LeafPattern(pattern.pattern)
    } else if (pattern.patterns.isNotEmpty()) {
        val children = pattern.patterns.map { complexPatternToFormula(it) }
        Formula.And(children)
    } else if (pattern.patternInside != null) {
        Formula.Inside(
            Formula.LeafPattern(pattern.patternInside)
        )
    } else if (pattern.patternNot != null) {
        Formula.Not(
            Formula.LeafPattern(pattern.patternNot)
        )
    } else if (pattern.patternNotInside != null) {
        Formula.Not(
            Formula.Inside(
                Formula.LeafPattern(pattern.patternNotInside)
            )
        )
    } else if (pattern.metavariablePattern != null) {
        val nested = pattern.metavariablePattern.pattern()
        val nestedFormula = complexPatternToFormula(nested)
        Formula.MetavarPattern(pattern.metavariablePattern.metavariable, nestedFormula)
    } else if (pattern.metavariableRegex != null) {
        Formula.MetavarRegex(pattern.metavariableRegex.metavariable, pattern.metavariableRegex.regex)
    } else if (pattern.metavariableComparison != null) {
        Formula.MetavarCond(pattern.metavariableComparison.metavariable)
    } else if (pattern.patternRegex != null) {
        Formula.Regex(pattern.patternRegex)
    } else if (pattern.patternNotRegex != null) {
        Formula.Not(Formula.Regex(pattern.patternNotRegex))
    } else if (pattern.focusMetavariable != null) {
        when (pattern.focusMetavariable) {
            is YamlScalar -> Formula.MetavarFocus(pattern.focusMetavariable.content)
            is YamlList -> {
                val elements = pattern.focusMetavariable.items.map {
                    (it as? YamlScalar)?.content ?: error("Unexpected value in focus-metavariable list: $it")
                }
                Formula.And(elements.map { Formula.MetavarFocus(it) })
            }
            else -> error("Unexpected value for focus-metavariable: ${pattern.focusMetavariable}")
        }
    } else {
        TODO()
    }
}
