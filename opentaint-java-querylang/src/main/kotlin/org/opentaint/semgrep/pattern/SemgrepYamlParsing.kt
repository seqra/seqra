package org.opentaint.semgrep.pattern

import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.UnknownPropertyException
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlException
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.serializer
import org.opentaint.semgrep.pattern.SemgrepTraceEntry.Step
import org.opentaint.semgrep.pattern.conversion.cartesianProductMapTo
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

@Serializable
data class SemgrepYamlRuleSet(
    val rules: List<SemgrepYamlRule>,
)

@Serializable
data class SemgrepYamlRule(
    val id: String,
    val languages: List<String>? = null,
    val pattern: String? = null,
    val mode: String? = null,
    val options: YamlMap? = null,
    val join: SemgrepYamlJoinRule? = null,
    val patterns: List<ComplexPattern> = emptyList(),
    @SerialName("pattern-either")
    val patternEither: List<ComplexPattern> = emptyList(),
    val message: String,
    val severity: String,
    val metadata: YamlMap? = null,
    @SerialName("pattern-sources")
    val patternSources: List<PatternSource> = emptyList(),
    @SerialName("pattern-sinks")
    val patternSinks: List<PatternSink> = emptyList(),
    @SerialName("pattern-propagators")
    val patternPropagators: List<PatternPropagator> = emptyList(),
    @SerialName("pattern-sanitizers")
    val patternSanitizers: List<PatternSanitizer> = emptyList(),
)

@Serializable
data class SemgrepYamlJoinRule(
    val refs: List<SemgrepYamlJoinRuleRef> = emptyList(),
    val rules: List<SemgrepYamlRule> = emptyList(),
    val on: List<String> = emptyList(),
)

@Serializable
data class SemgrepYamlJoinRuleRef(
    val rule: String,
    @SerialName("as")
    val `as`: String,
    val renames: List<SemgrepYamlJoinRuleRefRenames> = emptyList()
)

@Serializable
data class SemgrepYamlJoinRuleRefRenames(
    val from: String,
    val to: String,
)

@Serializable
data class ComplexPattern(
    @SerialName("pattern-either")
    val patternEither: List<ComplexPattern> = emptyList(),
    val pattern: String? = null,
    val patterns: List<ComplexPattern> = emptyList(),
    @SerialName("pattern-inside")
    private val rawPatternInside: YamlNode? = null,
    @SerialName("pattern-not")
    private val rawPatternNot: YamlNode? = null,
    @SerialName("pattern-not-inside")
    private val rawPatternNotInside: YamlNode? = null,
    @SerialName("metavariable-regex")
    val metavariableRegex: MetavariableRegexInfo? = null,
    @SerialName("metavariable-pattern")
    private val rawMetaVariablePattern: YamlNode? = null,
    @SerialName("metavariable-comparison")
    val metavariableComparison: MetavariableComparisonInfo? = null,
    @SerialName("pattern-regex")
    val patternRegex: String? = null,
    @SerialName("pattern-not-regex")
    val patternNotRegex: String? = null,
    @SerialName("focus-metavariable")
    private val rawFocusMetavariable: YamlNode? = null,
) {
    @Transient
    val metaVariablePattern: MetavariablePatternInfo? = rawMetaVariablePattern?.decodeMetavariablePatternInfo()
    @Transient
    val focusMetaVariables: List<String>? = rawFocusMetavariable?.decodeFocusMetaVariables()
    @Transient
    val patternInside: SimpleOrComplexPattern? = rawPatternInside?.decodeSimpleOrComplexPattern()
    @Transient
    val patternNot: SimpleOrComplexPattern? = rawPatternNot?.decodeSimpleOrComplexPattern()
    @Transient
    val patternNotInside: SimpleOrComplexPattern? = rawPatternNotInside?.decodeSimpleOrComplexPattern()
}

sealed interface SimpleOrComplexPattern {
    data class Simple(val pattern: String) : SimpleOrComplexPattern
    data class Complex(val pattern: ComplexPattern) : SimpleOrComplexPattern
}

private fun YamlNode.decodeSimpleOrComplexPattern(): SimpleOrComplexPattern {
    if (this is YamlScalar) {
        return SimpleOrComplexPattern.Simple(content)
    }

    val pattern = yaml.decodeFromYamlNode<ComplexPattern>(this)
    return SimpleOrComplexPattern.Complex(pattern)
}

private fun YamlNode.decodeMetavariablePatternInfo(): MetavariablePatternInfo =
    yaml.decodeFromYamlNode<MetavariablePatternInfo>(this)

private fun YamlNode.decodeFocusMetaVariables(): List<String> {
    if (this is YamlScalar) {
        return listOf(content)
    }

    return yaml.decodeFromYamlNode<List<String>>(this)
}

fun YamlMap.getBoolKeyOrFalse(key: String): Boolean =
    (get(key) as? YamlScalar)?.content?.lowercase() == "true"

fun YamlMap.readStrings(key: String): List<String>? {
    val entry = entries.entries.find { it.key.content.equals(key, ignoreCase = true) } ?: return null
    return when (val value = entry.value) {
        is YamlScalar -> {
            listOf(value.content)
        }
        is YamlList -> {
            value.items.mapNotNull { (it as? YamlScalar)?.content }
        }
        else -> null
    }
}

@Serializable
data class MetavariableRegexInfo(
    val metavariable: String,
    val regex: String,
)

@Serializable
data class MetavariableComparisonInfo(
    val metavariable: String,
    val comparison: String,
)

@Serializable(MetaVariablePatternInfoSerializer::class)
data class MetavariablePatternInfo(
    val metavariable: String,
    val metaVariablePattern: ComplexPattern,
)

@Serializable(PatternSanitizerSerializer::class)
data class PatternSanitizer(
    val exact: Boolean? = null,
    @SerialName("by-side-effect")
    val bySideEffect: Boolean? = null,
    val sanitizerPattern: ComplexPattern,
)

@Serializable(PatternPropagatorSerializer::class)
data class PatternPropagator(
    val from: String,
    val to: String,
    @SerialName("by-side-effect")
    val bySideEffect: Boolean? = null,
    val propagatorPattern: ComplexPattern,
)

@Serializable(PatternSourceSerializer::class)
data class PatternSource(
    val exact: Boolean? = null,
    val control: Boolean? = null,
    @SerialName("by-side-effect")
    val bySideEffect: Boolean? = null,
    @SerialName("label")
    val rawLabel: String? = null,
    @SerialName("requires")
    val rawRequires: String? = null,
    val sourcePattern: ComplexPattern,
) {
    @Transient
    val requires: SemgrepTaintRequires? = rawRequires?.decodeRequires()
    @Transient
    val label: SemgrepTaintLabel? = rawLabel?.let { SemgrepTaintLabel(it) }
}

@Serializable(PatternSinkSerializer::class)
data class PatternSink(
    @SerialName("requires")
    val rawRequires: YamlNode? = null,
    val sinkPattern: ComplexPattern,
) {
    @Transient
    val requires: SemgrepSinkTaintRequirement? = rawRequires?.decodeSinkRequires()
}

private fun YamlNode.decodeSinkRequires(): SemgrepSinkTaintRequirement? {
    if (this is YamlScalar) {
        return SemgrepSinkTaintRequirement.Simple(content.decodeRequires() ?: return null)
    }

    currentLoadTrace?.error(Step.LOAD_RULESET, UnsupportedRuleSinkWithRequires.RequiresMetaVars())
    return null
}

private fun String.decodeRequires(): SemgrepTaintRequires? {
    val result = parseRequires(this)
    if (result == null) {
        currentLoadTrace?.error(Step.LOAD_RULESET, UnsupportedRuleSinkWithRequires.UnparsedRequires(this))
    }
    return result
}

private const val metaVariableField = "metavariable"

object MetaVariablePatternInfoSerializer
    : InlineCompositeObjectSerializer<MetavariablePatternInfo, ComplexPattern>(
    name = "metavariable-pattern-info",
    objSerializer = ComplexPattern.serializer(),
    inlinedFields = listOf(metaVariableField to String.serializer())
) {
    override fun deserialize(obj: ComplexPattern, fields: Map<String, Optional<Any>>): MetavariablePatternInfo {
        val metaVar = fields[metaVariableField]?.map { it as String }?.getOrNull() ?: error("deserialization failed")
        return MetavariablePatternInfo(metaVar, obj)
    }
}

private const val bySideEffect = "by-side-effect"
private const val fromField = "from"
private const val toField = "to"

object PatternPropagatorSerializer
    : InlineCompositeObjectSerializer<PatternPropagator, ComplexPattern>(
    name = "pattern-propagator",
    objSerializer = ComplexPattern.serializer(),
    inlinedFields = listOf(
        fromField to String.serializer(),
        toField to String.serializer(),
        bySideEffect to Boolean.serializer(),
    ),
) {
    override fun deserialize(obj: ComplexPattern, fields: Map<String, Optional<Any>>): PatternPropagator {
        val fromValue = fields[fromField]?.map { it as String }?.getOrNull() ?: error("deserialization failed")
        val toValue = fields[toField]?.map { it as String }?.getOrNull() ?: error("deserialization failed")
        val bySf = fields[bySideEffect]?.map { it as Boolean }?.getOrNull()
        return PatternPropagator(fromValue, toValue, bySf, obj)
    }
}

private const val exactField = "exact"
private const val controlField = "control"
private const val labelField = "label"
private const val requiresField = "requires"

object PatternSourceSerializer : InlineCompositeObjectSerializer<PatternSource, ComplexPattern>(
    name = "pattern-source",
    objSerializer = ComplexPattern.serializer(),
    inlinedFields = listOf(
        labelField to String.serializer(),
        requiresField to String.serializer(),
        bySideEffect to Boolean.serializer(),
        exactField to Boolean.serializer(),
        controlField to Boolean.serializer(),
    ),
) {
    override fun deserialize(obj: ComplexPattern, fields: Map<String, Optional<Any>>): PatternSource {
        val label = fields[labelField]?.map { it as String }?.getOrNull()
        val requires = fields[requiresField]?.map { it as String }?.getOrNull()
        val bySf = fields[bySideEffect]?.map { it as Boolean }?.getOrNull()
        val exact = fields[exactField]?.map { it as Boolean }?.getOrNull()
        val control = fields[controlField]?.map { it as Boolean }?.getOrNull()
        return PatternSource(exact, control, bySf, label, requires, obj)
    }
}

object PatternSinkSerializer : InlineCompositeObjectSerializer<PatternSink, ComplexPattern>(
    name = "pattern-sink",
    objSerializer = ComplexPattern.serializer(),
    inlinedFields = listOf(requiresField to YamlNode.serializer()),
) {
    override fun deserialize(obj: ComplexPattern, fields: Map<String, Optional<Any>>): PatternSink {
        val requires = fields[requiresField]?.map { it as YamlNode }?.getOrNull()
        return PatternSink(requires, obj)
    }
}

object PatternSanitizerSerializer
    : InlineCompositeObjectSerializer<PatternSanitizer, ComplexPattern>(
    name = "pattern-sanitizer",
    objSerializer = ComplexPattern.serializer(),
    inlinedFields = listOf(exactField to Boolean.serializer(), bySideEffect to Boolean.serializer()),
) {
    override fun deserialize(obj: ComplexPattern, fields: Map<String, Optional<Any>>): PatternSanitizer {
        val exact = fields[exactField]?.map { it as Boolean }?.getOrNull()
        val bySf = fields[bySideEffect]?.map { it as Boolean }?.getOrNull()
        return PatternSanitizer(exact, bySf, obj)
    }
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

private val yamlConfig = YamlConfiguration(
    codePointLimit = Int.MAX_VALUE,
    strictMode = false,
    anchorsAndAliases = AnchorsAndAliases.Permitted()
)

private val yaml = Yaml(configuration = yamlConfig)
private val strictYaml = Yaml(configuration = yamlConfig.copy(strictMode = true))

private var currentLoadTrace: SemgrepFileLoadTrace? = null

fun parseSemgrepYaml(yml: String, trace: SemgrepFileLoadTrace): SemgrepYamlRuleSet? =
    try {
        currentLoadTrace = trace
        tryParseRuleSetYaml(yml)
    } catch (ex: Throwable) {
        trace.error(Step.LOAD_RULESET, FailedToLoadRuleSetFromYaml(ex.message))
        null
    } finally {
        currentLoadTrace = null
    }

private fun tryParseRuleSetYaml(yml: String): SemgrepYamlRuleSet = try {
    strictYaml.decodeFromString(SemgrepYamlRuleSet.serializer(), yml)
} catch (ex: YamlException) {
    val rootCause = ex.rootCause()
    if (rootCause !is UnknownPropertyException) throw ex

    currentLoadTrace?.error(Step.LOAD_RULESET, UnknownProperty(rootCause.propertyName))

    yaml.decodeFromString(SemgrepYamlRuleSet.serializer(), yml)
}

private fun YamlException.rootCause(): Throwable {
    var result: Throwable = this
    while (true) {
        result = result.cause ?: return result
    }
}

fun parseMatchingRule(rule: SemgrepYamlRule, trace: SemgrepRuleLoadStepTrace): SemgrepMatchingRule<Formula>? {
    val parsed = parseMatchingRuleFormula(rule, trace) ?: return null
    return SemgrepMatchingRule(listOf(parsed))
}

data class SemgrepYamlJoinRuleParsed(
    val refs: List<SemgrepYamlJoinRuleRef>,
    val rules: List<SemgrepYamlRule>,
    val on: List<SemgrepJoinRuleOn>,
)

data class SemgrepJoinRuleOn(
    val left: SemgrepJoinRuleOnVar,
    val right: SemgrepJoinRuleOnVar,
    val op: SemgrepJoinOnOperation
)

data class SemgrepJoinRuleOnVar(val ruleName: String, val varName: String)

enum class SemgrepJoinOnOperation(val op: String) {
    EQ("=="), COMPOSE("->"), TRANSITIVE("-->")
}

fun parseJoinRule(rule: SemgrepYamlJoinRule, trace: SemgrepRuleLoadStepTrace): SemgrepYamlJoinRuleParsed? {
    val parsedOn = rule.on.map { parseJoinOnCondition(it, trace) ?: return null }
    return SemgrepYamlJoinRuleParsed(rule.refs, rule.rules, parsedOn)
}

// Expected format: <rule-name>.<var> <op> <rule-name>.<var>
private val joinOnConditionPattern by lazy {
    val operations = SemgrepJoinOnOperation.entries.map { it.op }
    val ruleWithVarPattern = """(.+)\s*\.\s*(.+)"""
    Regex("""$ruleWithVarPattern\s*(${operations.joinToString("|")})\s*$ruleWithVarPattern""")
}

private fun parseJoinOnCondition(condition: String, trace: SemgrepRuleLoadStepTrace): SemgrepJoinRuleOn? {
    val match = joinOnConditionPattern.matchEntire(condition)
    if (match == null) {
        trace.error(JoinOnConditionParseFailed(condition))
        return null
    }

    val (leftRule, leftVar, opStr, rightRule, rightVar) = match.destructured.toList()

    val op = SemgrepJoinOnOperation.entries.first { it.op == opStr }
    val left = SemgrepJoinRuleOnVar(leftRule.trim(), leftVar.trim())
    val right = SemgrepJoinRuleOnVar(rightRule.trim(), rightVar.trim())
    return SemgrepJoinRuleOn(left, right, op)
}

fun parseTaintRule(rule: SemgrepYamlRule, trace: SemgrepRuleLoadStepTrace): SemgrepTaintRule<Formula> =
    SemgrepTaintRule(
        sources = rule.patternSources.mapNotNull {
            SemgrepTaintSource(
                it.exact, it.control, it.bySideEffect, it.label, it.requires,
                complexPatternToFormula(it.sourcePattern, trace) ?: return@mapNotNull null
            )
        },
        sinks = rule.patternSinks.mapNotNull {
            SemgrepTaintSink(
                it.requires,
                complexPatternToFormula(it.sinkPattern, trace) ?: return@mapNotNull null
            )
        },
        propagators = rule.patternPropagators.mapNotNull {
            SemgrepTaintPropagator(
                it.from, it.to, it.bySideEffect,
                complexPatternToFormula(it.propagatorPattern, trace) ?: return@mapNotNull null
            )
        },
        sanitizers = rule.patternSanitizers.mapNotNull {
            SemgrepTaintSanitizer(
                it.exact, it.bySideEffect,
                complexPatternToFormula(it.sanitizerPattern, trace) ?: return@mapNotNull null
            )
        }
    )

// TODO The case in which two or more of them are simultaneously contained has not been considered.
private fun parseMatchingRuleFormula(rule: SemgrepYamlRule, trace: SemgrepRuleLoadStepTrace): Formula? =
    if (rule.pattern != null) {
        Formula.LeafPattern(rule.pattern)
    } else if (rule.patterns.isNotEmpty()) {
        val children = rule.patterns.map { complexPatternToFormula(it, trace) ?: return null }
        Formula.And(children)
    } else if (rule.patternEither.isNotEmpty()) {
        val children = rule.patternEither.map { complexPatternToFormula(it, trace) ?: return null }
        Formula.Or(children)
    } else {
        trace.error(UnsupportedPattern())
        null
    }

fun convertToRawRule(rule: SemgrepRule<Formula>,
                     semgrepTrace : SemgrepRuleLoadStepTrace): SemgrepRule<RuleWithMetaVars<RawSemgrepRule, RawMetaVarInfo>> {
    return rule.flatMap { convertToRawRule(it, semgrepTrace) }
}

fun convertToRawRule(formula: Formula,
                     semgrepTrace : SemgrepRuleLoadStepTrace
): List<RuleWithMetaVars<RawSemgrepRule, RawMetaVarInfo>> {
    val formulaDnf = formula.normalizeToNNF(negated = false).toDNF()
    return formulaDnf.mapNotNull { convertToNormalizedRule(it.literals, semgrepTrace) }
}

private fun convertToNormalizedRule(literals: List<NormalizedFormula.Literal>,
                                    semgrepTrace : SemgrepRuleLoadStepTrace): RuleWithMetaVars<RawSemgrepRule, RawMetaVarInfo>? {
    val patterns = mutableListOf<String>()
    val patternNots = mutableListOf<String>()
    val patternInsides = mutableListOf<String>()
    val patternNotInsides = mutableListOf<String>()
    val metaVariableConstraints = hashMapOf<String, MutableSet<MetaVarConstraintFormula<RawMetaVarConstraint>>>()
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
                    ?: run {
                        semgrepTrace.error(PatternInsideIsNotLeaf(inside))
                        return null
                    }
                if (literal.negated) {
                    patternNotInsides.add(insideAsLeaf.pattern)
                } else {
                    patternInsides.add(insideAsLeaf.pattern)
                }
            }

            is Formula.MetavarFocus -> {
                // todo
                if (literal.negated)  {
                    semgrepTrace.error(NotImplementedNegatedMetavarFocus(f.name))
                    return null
                }

                focusMetaVars.add(f.name)
            }

            is Formula.MetavarCond -> {
                // todo
                semgrepTrace.error(NotImplementedMetavarCond(f.name))
                return null
            }

            is Formula.MetavarPattern -> {
                var metaVarConstraint = f.formula.toMetaVarPatternConstraint()
                if (metaVarConstraint == null) {
                    // todo
                    semgrepTrace.error(NotImplementedComplexMetavarPattern(f.name))
                    return null
                }

                if (literal.negated) {
                    metaVarConstraint = MetaVarConstraintFormula.mkNot(metaVarConstraint)
                }

                metaVariableConstraints.getOrPut(f.name, ::hashSetOf).add(metaVarConstraint)
            }

            is Formula.MetavarRegex -> {
                val regexConstraint = RawMetaVarConstraint.RegExp(f.regex)
                var metaVarConstraint: MetaVarConstraintFormula<RawMetaVarConstraint> = MetaVarConstraintFormula.Constraint(regexConstraint)

                if (literal.negated) {
                    metaVarConstraint = MetaVarConstraintFormula.mkNot(metaVarConstraint)
                }

                metaVariableConstraints.getOrPut(f.name, ::hashSetOf).add(metaVarConstraint)
            }

            is Formula.Regex -> {
                // todo
                semgrepTrace.error(NotImplementedRegex(f.pattern))
                return null
            }

            is Formula.Not,
            is Formula.And,
            is Formula.Or -> error("Unexpected formula in dnf")
        }
    }

    if (patterns.isEmpty() && patternInsides.isNotEmpty()) {
        // note: semgrep allows pattern-inside signature rules without pattern
        patterns.add("...")
    }

    if (patterns.isEmpty()) {
        semgrepTrace.error(RuleHasNoPatterns())
    }

    return RuleWithMetaVars(
        RawSemgrepRule(
            patterns, patternNots, patternInsides, patternNotInsides
        ),
        RawMetaVarInfo(
            focusMetaVars,
            metaVariableConstraints.mapValues { (_, constraints) ->
                MetaVarConstraintFormula.mkAnd(constraints)
            },
        )
    )
}

private fun Formula.toMetaVarPatternConstraint(): MetaVarConstraintFormula<RawMetaVarConstraint>? {
    return when (this) {
        is Formula.LeafPattern -> MetaVarConstraintFormula.Constraint(RawMetaVarConstraint.Pattern(pattern))
        is Formula.Regex -> MetaVarConstraintFormula.Constraint(RawMetaVarConstraint.RegExp(pattern))
        is Formula.Not -> child.toMetaVarPatternConstraint()?.let { MetaVarConstraintFormula.mkNot(it) }

        is Formula.And -> children.mapTo(hashSetOf()) { it.toMetaVarPatternConstraint() ?: return null }
            .let { MetaVarConstraintFormula.mkAnd(it) }

        is Formula.Or -> children.mapTo(hashSetOf()) { it.toMetaVarPatternConstraint() ?: return null }
            .let { MetaVarConstraintFormula.mkOr(it) }

        else -> null
    }
}

private sealed interface NormalizedFormula {
    data class Literal(val formula: Formula, val negated: Boolean) : NormalizedFormula
    data class And(val children: List<NormalizedFormula>) : NormalizedFormula
    data class Or(val children: List<NormalizedFormula>) : NormalizedFormula
}

private data class NormalizedFormulaCube(val literals: List<NormalizedFormula.Literal>)

private fun Formula.normalizeToNNF(negated: Boolean): NormalizedFormula = when (this) {
    is Formula.Inside, // todo: handle inside nested formula
    is Formula.LeafPattern,
    is Formula.MetavarCond,
    is Formula.MetavarRegex,
    is Formula.MetavarFocus,
    is Formula.Regex -> NormalizedFormula.Literal(this, negated)
    is Formula.MetavarPattern -> NormalizedFormula.Literal(this, negated)

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

private fun complexPatternToFormula(pattern: ComplexPattern, trace: SemgrepRuleLoadStepTrace): Formula? {
    return if (pattern.patternEither.isNotEmpty()) {
        val children = pattern.patternEither.map { complexPatternToFormula(it, trace) ?: return null }
        Formula.Or(children)
    } else if (pattern.pattern != null) {
        Formula.LeafPattern(pattern.pattern)
    } else if (pattern.patterns.isNotEmpty()) {
        val children = pattern.patterns.map { complexPatternToFormula(it, trace) ?: return null }
        Formula.And(children)
    } else if (pattern.patternInside != null) {
        Formula.Inside(
            complexPatternToFormula(pattern.patternInside, trace) ?: return null
        )
    } else if (pattern.patternNot != null) {
        Formula.Not(
            complexPatternToFormula(pattern.patternNot, trace) ?: return null
        )
    } else if (pattern.patternNotInside != null) {
        Formula.Not(
            Formula.Inside(
                complexPatternToFormula(pattern.patternNotInside, trace) ?: return null
            )
        )
    } else if (pattern.metaVariablePattern != null) {
        val nested = pattern.metaVariablePattern.metaVariablePattern
        val nestedFormula = complexPatternToFormula(nested, trace) ?: return null
        Formula.MetavarPattern(pattern.metaVariablePattern.metavariable, nestedFormula)
    } else if (pattern.metavariableRegex != null) {
        Formula.MetavarRegex(pattern.metavariableRegex.metavariable, pattern.metavariableRegex.regex)
    } else if (pattern.metavariableComparison != null) {
        Formula.MetavarCond(pattern.metavariableComparison.metavariable)
    } else if (pattern.patternRegex != null) {
        Formula.Regex(pattern.patternRegex)
    } else if (pattern.patternNotRegex != null) {
        Formula.Not(Formula.Regex(pattern.patternNotRegex))
    } else if (pattern.focusMetaVariables != null) {
        val focusVars = pattern.focusMetaVariables.map { Formula.MetavarFocus(it) }
        Formula.And(focusVars)
    } else {
        trace.error(UnexpectedComplexPattern(pattern))
        null
    }
}

private fun complexPatternToFormula(pattern: SimpleOrComplexPattern, trace: SemgrepRuleLoadStepTrace): Formula? =
    when (pattern) {
        is SimpleOrComplexPattern.Simple -> Formula.LeafPattern(pattern.pattern)
        is SimpleOrComplexPattern.Complex -> complexPatternToFormula(pattern.pattern, trace)
    }
