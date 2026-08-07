package org.opentaint.semgrep

import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedItem
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.SinkRule
import org.opentaint.semgrep.pattern.SemgrepLoadTrace
import org.opentaint.semgrep.pattern.SemgrepRuleLoader
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.semgrep.pattern.conversion.JavaLanguageStrategy
import org.opentaint.semgrep.pattern.createTaintConfig
import org.opentaint.semgrep.pattern.errorEntries
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `pattern: sink($*X)` (whole-object / any-field) with a coinciding `pattern-not: sink($X)`
 * (base/value) at the SAME position is a SCOPED exclusion: exclude the base value, keep the
 * fields. With $X and $*X kept distinct and the implication A => A* encoded, that cell
 * (`!A ^ A*`) is SAT and must compile to `ContainsMarkOnAnyField(pos) ^ !ContainsMark(pos)`.
 */
class StarPatternNotFieldOnlyTest {
    private fun config(ruleText: String): SerializedTaintConfig {
        val trace = SemgrepLoadTrace()
        val loader = SemgrepRuleLoader(listOf(JavaLanguageStrategy()))
        loader.registerRuleSet(ruleText, Path("star.yaml"), Path("."), trace)
        val rules = loader.loadRules().rulesWithMeta
        val rule = rules.singleOrNull()?.first
            ?: error("expected exactly 1 rule, got ${rules.size}; trace errors=${trace.errorEntries().map { it.message }}")
        @Suppress("UNCHECKED_CAST")
        return (rule as TaintRuleFromSemgrep<SerializedItem>).createTaintConfig()
    }

    private fun flatten(c: SerializedCondition): List<SerializedCondition> = when (c) {
        is SerializedCondition.Or -> listOf(c) + c.anyOf.flatMap { flatten(it) }
        is SerializedCondition.And -> listOf(c) + c.allOf.flatMap { flatten(it) }
        is SerializedCondition.Not -> listOf(c) + flatten(c.not)
        else -> listOf(c)
    }

    private fun sinkConditions(cfg: SerializedTaintConfig): List<SerializedCondition> =
        (cfg.sink.orEmpty() + cfg.methodExitSink.orEmpty() + cfg.methodEntrySink.orEmpty())
            .filterIsInstance<SinkRule>()
            .mapNotNull { it.condition }
            .flatMap { flatten(it) }

    private val rule = """
        rules:
          - id: fo
            severity: NOTE
            message: x
            languages: [java]
            mode: taint
            pattern-sources:
              - pattern: ${'$'}X = src();
            pattern-sinks:
              - patterns:
                  - pattern: sink(${'$'}*X);
                  - pattern-not: sink(${'$'}X);
                  - focus-metavariable: ${'$'}X
    """.trimIndent()


    private fun ruleCount(sinkPatterns: String): Int {
        val trace = SemgrepLoadTrace()
        val loader = SemgrepRuleLoader(listOf(JavaLanguageStrategy()))
        val text = """
            rules:
              - id: c
                severity: NOTE
                message: x
                languages: [java]
                mode: taint
                pattern-sources:
                  - pattern: ${'$'}X = src();
                pattern-sinks:
                  - patterns:
$sinkPatterns
                      - focus-metavariable: ${'$'}X
        """.trimIndent()
        loader.registerRuleSet(text, Path("c.yaml"), Path("."), trace)
        return loader.loadRules().rulesWithMeta.size
    }

    @Test
    fun `starred sink with coinciding unstarred pattern-not is field-only`() {
        // !A ^ A*
        val conds = sinkConditions(config(rule))
        assertTrue(
            conds.any { it is SerializedCondition.ContainsMarkOnAnyField },
            "field-only sink must REQUIRE any-field taint (ContainsMarkOnAnyField); got $conds"
        )
        assertTrue(
            conds.any { it is SerializedCondition.Not && it.not is SerializedCondition.ContainsMark },
            "field-only sink must EXCLUDE base/value taint (Not(ContainsMark)); got $conds"
        )
    }

    @Test
    fun `A and A star both positive minimizes to A (base only, no any-field)`() {
        // A ^ A* == A: base subsumes base-or-any-field.
        val conds = sinkConditions(config("""
            rules:
              - id: aa
                severity: NOTE
                message: x
                languages: [java]
                mode: taint
                pattern-sources:
                  - pattern: ${'$'}X = src();
                pattern-sinks:
                  - patterns:
                      - pattern: sink(${'$'}X);
                      - pattern: sink(${'$'}*X);
                      - focus-metavariable: ${'$'}X
        """.trimIndent()))
        assertTrue(
            conds.any { it is SerializedCondition.ContainsMark },
            "A ^ A* must keep the base ContainsMark; got $conds"
        )
        assertTrue(
            conds.none { it is SerializedCondition.ContainsMarkOnAnyField },
            "A ^ A* must drop the redundant any-field (solution is A); got $conds"
        )
    }

    @Test
    fun `base positive with starred pattern-not is unsatisfiable`() {
        // A ^ !A* : A => A*, so excluding A* contradicts A -> UNSAT -> the sink variant is dropped.
        val n = ruleCount(
            "                      - pattern: sink(${'$'}X);\n" +
            "                      - pattern-not: sink(${'$'}*X);"
        )
        assertTrue(n == 0, "A ^ !A* must be unsatisfiable (rule dropped); got rules=$n")
    }
}
