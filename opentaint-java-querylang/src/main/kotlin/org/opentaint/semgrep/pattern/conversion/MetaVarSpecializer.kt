package org.opentaint.org.opentaint.semgrep.pattern.conversion

import org.opentaint.dataflow.configuration.jvm.extractAlternatives
import org.opentaint.org.opentaint.semgrep.pattern.ConcreteName
import org.opentaint.org.opentaint.semgrep.pattern.Metavar
import org.opentaint.org.opentaint.semgrep.pattern.MetavarName
import org.opentaint.org.opentaint.semgrep.pattern.Name
import org.opentaint.org.opentaint.semgrep.pattern.NormalizedSemgrepRule
import org.opentaint.org.opentaint.semgrep.pattern.ParsedSemgprepRule
import org.opentaint.org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.org.opentaint.semgrep.pattern.StringLiteral
import org.opentaint.org.opentaint.semgrep.pattern.TypeName
import org.opentaint.org.opentaint.semgrep.pattern.TypedMetavar
import org.opentaint.org.opentaint.semgrep.pattern.conversion.MetaVarSpecializer.MetaVarValue
import org.opentaint.org.opentaint.semgrep.pattern.map

fun specializeMetaVars(
    rule: ParsedSemgprepRule,
): List<NormalizedSemgrepRule>? {
    val normalizedRule = with(rule) {
        NormalizedSemgrepRule(patterns, patternNots, patternInsides, patternNotInsides)
    }

    if (rule.metaVariableRegex.isEmpty() && rule.metaVariablePatterns.isEmpty()) {
        return listOf(normalizedRule)
    }

    val concreteValueMetaVarsCollector = ConcreteValueMetaVarsCollector()
    with(MetaVarSpecializer(concreteValueMetaVarsCollector)) {
        normalizedRule.map { it.specializeMetaVars() ?: it }
    }

    if (concreteValueMetaVarsCollector.accessedMetaVars.isEmpty()) return listOf(normalizedRule)

    val ctx = MetaVarContext(rule.metaVariableRegex, rule.metaVariablePatterns)

    val values = createMetaVarValues(
        ctx,
        concreteValueMetaVarsCollector.accessedMetaVars,
        concreteValueMetaVarsCollector.concreteMetaVars
    ) ?: return null

    if (values.isEmpty()) return listOf(normalizedRule)

    return values.map { value ->
        with(MetaVarSpecializer(value)) {
            normalizedRule.map { it.specializeMetaVars() ?: return null }
        }
    }
}

private class MetaVarContext(
    private val metaVarRegex: Map<String, Set<String>>,
    private val metaVarPattern: Map<String, List<SemgrepJavaPattern>>,
) {
    data class Constraints(val regex: Set<String>, val pattern: Set<SemgrepJavaPattern>)

    fun varConstraints(metaVar: String): Constraints? {
        val regex = metaVarRegex[metaVar]
        val pattern = metaVarPattern[metaVar]
        if (regex == null && pattern == null) return null
        return Constraints(regex.orEmpty(), pattern?.toSet().orEmpty())
    }
}

private fun createMetaVarValues(
    context: MetaVarContext,
    accessedVars: Set<String>,
    concreteValueVars: Set<String>,
): List<MetaVarSpecializer.MetaVarSpecializationContext>? {
    val constraints = accessedVars.mapNotNull {
        it to (context.varConstraints(it) ?: return@mapNotNull null)
    }

    if (constraints.isEmpty()) return emptyList()

    val values = constraints.map { (varName, constraints) ->
        generateValues(constraints, varName in concreteValueVars)
            ?.map { varName to it }
            ?: return null
    }

    val result = mutableListOf<MetaVarSpecializer.MetaVarSpecializationContext>()
    values.cartesianProductMapTo { varValues ->
        result += MetaVarValueProvider(varValues.associate { it })
    }

    return result
}

private fun generateValues(
    constraints: MetaVarContext.Constraints,
    concreteValueRequired: Boolean,
): List<MetaVarValue>? {
    if (constraints.pattern.isEmpty()) {
        val regex = constraints.regex.map { MetaVarValue.RegExp(it) }
        if (!concreteValueRequired) {
            return regex
        }

        return regex.flatMap { it.tryConcretize() ?: return null }
    }

    if (constraints.regex.isEmpty()) {
        val pattern = constraints.pattern.singleOrNull() ?: return null
        val stringValue = pattern.stringValue() ?: return null
        return listOf(MetaVarValue.Concrete(stringValue))
    }

    // todo
    return null
}

private fun SemgrepJavaPattern.stringValue(): String? =
    tryExtractPatterDotSeparatedParts(this)?.joinToString(separator = ".")

private fun MetaVarValue.RegExp.tryConcretize(): List<MetaVarValue.Concrete>? {
    val simpRegex = value.trim().trimStart('^').trimEnd('$')
    val alternatives = simpRegex.extractAlternatives()
    val concreteValues = alternatives.filter { str -> str.all { it.isLetterOrDigit() } }

    if (concreteValues.size != alternatives.size) {
        return null
    }

    return concreteValues.map { MetaVarValue.Concrete(it) }
}

private class ConcreteValueMetaVarsCollector : MetaVarSpecializer.MetaVarSpecializationContext {
    val accessedMetaVars = hashSetOf<String>()
    val concreteMetaVars = hashSetOf<String>()

    override fun varValue(
        metaVar: String, concreteValueRequired: Boolean
    ): MetaVarValue? {
        accessedMetaVars.add(metaVar)

        if (concreteValueRequired) {
            concreteMetaVars.add(metaVar)
        }
        return null
    }
}

private class MetaVarValueProvider(
    val varValues: Map<String, MetaVarValue>
) : MetaVarSpecializer.MetaVarSpecializationContext {
    override fun varValue(metaVar: String, concreteValueRequired: Boolean): MetaVarValue? =
        varValues[metaVar]
}

private class MetaVarSpecializer(
    val metaVarContext: MetaVarSpecializationContext
) {
    interface MetaVarSpecializationContext {
        fun varValue(metaVar: String, concreteValueRequired: Boolean = false): MetaVarValue?
    }

    sealed interface MetaVarValue {
        data class Concrete(val value: String) : MetaVarValue
        data class RegExp(val value: String) : MetaVarValue
    }

    fun SemgrepJavaPattern.specializeMetaVars(): SemgrepJavaPattern? {
        val rewriter = object : PatternRewriter {
            override fun Metavar.rewriteMetavar(): SemgrepJavaPattern {
                val constraints = metaVarContext.varValue(name, concreteValueRequired = true)
                return when (constraints) {
                    is MetaVarValue.Concrete -> {
                        constraints.value.createIdentifierMatcher()
                    }

                    is MetaVarValue.RegExp -> {
                        // todo
                        rewriteFailure("todo: metavar")
                    }

                    null -> this
                }
            }

            override fun createTypedMetavar(name: String, type: TypeName): SemgrepJavaPattern {
                val constraints = metaVarContext.varValue(name, concreteValueRequired = true)
                if (constraints == null) {
                    return TypedMetavar(name, type)
                } else {
                    // todo
                    rewriteFailure("todo: metavar")
                }
            }

            override fun StringLiteral.rewriteStringLiteral(): SemgrepJavaPattern {
                val specializedContent = content
                    .specializeMetaVars(concreteNameRequired = false)

                return createStringLiteral(specializedContent)
            }

            override fun TypeName.rewriteTypeName(): TypeName =
                TypeName(dotSeparatedParts.flatMap { rewriteNameEnsureNoDots(it) })

            private fun rewriteNameEnsureNoDots(name: Name): List<Name> = when (name) {
                is ConcreteName -> listOf(name.rewriteName())
                is Name.Pattern -> listOf(name.rewriteName())
                is MetavarName -> {
                    val result = name.rewriteName()
                    when (result) {
                        is MetavarName -> listOf(result)
                        is Name.Pattern -> listOf(result)
                        is ConcreteName -> result.name.split('.').map { ConcreteName(it) }
                    }
                }
            }

            override fun Name.rewriteName(): Name =
                specializeMetaVars(concreteNameRequired = true)
        }

        return rewriter.safeRewrite(this) {
            return null
        }
    }

    private fun Name.specializeMetaVars(concreteNameRequired: Boolean): Name {
        return when (this) {
            is ConcreteName -> this
            is Name.Pattern -> this

            is MetavarName -> {
                val constraints = metaVarContext.varValue(metavarName, concreteNameRequired) ?: return this
                when (constraints) {
                    is MetaVarValue.Concrete -> ConcreteName(constraints.value)
                    is MetaVarValue.RegExp -> {
                        if (concreteNameRequired) {
                            // todo
                            rewriteFailure("Concrete name required")
                        }

                        Name.Pattern(constraints.value)
                    }
                }
            }
        }
    }

    private fun String.createIdentifierMatcher(): SemgrepJavaPattern {
        val dotSeparatedParts = this.split(".")
        return patternFromDotSeparatedParts(dotSeparatedParts)
    }
}
