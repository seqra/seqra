package org.opentaint.semgrep

import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedItem
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.semgrep.pattern.SemgrepLoadTrace
import org.opentaint.semgrep.pattern.SemgrepRuleLoader
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.semgrep.pattern.conversion.JavaLanguageStrategy
import org.opentaint.semgrep.pattern.createTaintConfig
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A sanitizer's `focus-metavariable` names the value that gets sanitized. Every other metavariable in
 * the pattern is there to constrain the match, so a clean action must not be emitted for it.
 *
 * This matters for *accessor* sanitizers -- `$SAFE = ($REQ).getSomething();` focused on `$SAFE`.
 * Cleaning `$REQ` as well would untaint the receiver, and `request.getRequestURI()` says nothing about
 * `request.getParameter("url")`.
 */
class AccessorSanitizerScopeTest {
    private fun config(ruleText: String): SerializedTaintConfig {
        val trace = SemgrepLoadTrace()
        val loader = SemgrepRuleLoader(listOf(JavaLanguageStrategy()))
        loader.registerRuleSet(ruleText, Path("sanitizer.yaml"), Path("."), trace)
        val (rule, _) = loader.loadRules().rulesWithMeta.single()
        @Suppress("UNCHECKED_CAST")
        return (rule as TaintRuleFromSemgrep<SerializedItem>).createTaintConfig()
    }

    private fun cleanPositions(cfg: SerializedTaintConfig): List<PositionBaseWithModifiers> =
        cfg.cleaner.orEmpty().flatMap { it.cleans }.map { it.pos }

    private fun PositionBaseWithModifiers.isThis(): Boolean = base is PositionBase.This

    private fun PositionBaseWithModifiers.isResult(): Boolean = base is PositionBase.Result

    private fun PositionBaseWithModifiers.isArgument(): Boolean =
        base is PositionBase.Argument || base is PositionBase.AnyArgument

    private fun rule(sanitizer: String) = """
        rules:
          - id: san
            severity: NOTE
            message: x
            languages: [java]
            mode: taint
            pattern-sources:
              - pattern: ${'$'}X = src();
            pattern-sanitizers:
$sanitizer
            pattern-sinks:
              - patterns:
                  - pattern: sink(${'$'}Y);
                  - focus-metavariable: ${'$'}Y
    """.trimIndent()

    @Test
    fun `focusing an accessor result does not clean the bound receiver`() {
        val cfg = config(
            rule(
                """
              - patterns:
                  - pattern: ${'$'}*URI = (javax.servlet.http.HttpServletRequest ${'$'}REQ).getRequestURI();
                  - focus-metavariable: ${'$'}URI
                """.trimIndent().prependIndent("              ")
            )
        )
        val positions = cleanPositions(cfg)
        assertTrue(positions.isNotEmpty(), "expected a cleaner to be generated")
        assertTrue(
            positions.none { it.isThis() },
            "the receiver is only a match constraint and must stay tainted; got $positions"
        )
        assertTrue(positions.all { it.isResult() }, "expected the returned value only; got $positions")
    }

    @Test
    fun `an accessor sanitizer with an unbound receiver cleans only the result`() {
        val cfg = config(rule("              - pattern: (javax.servlet.http.HttpServletRequest).getRequestURI()"))
        val positions = cleanPositions(cfg)
        assertTrue(positions.isNotEmpty(), "expected a cleaner to be generated")
        assertTrue(positions.all { it.isResult() }, "expected the returned value only; got $positions")
    }

    @Test
    fun `pass-through sanitizer still cleans the sanitized argument`() {
        // Here the focus metavar *is* the argument, and cleaning that position is required: the clean
        // runs on the argument-keyed fact at call-to-start, where `Result` does not exist yet.
        val cfg = config(
            rule(
                """
              - patterns:
                  - pattern: clean(${'$'}C);
                  - focus-metavariable: ${'$'}C
                """.trimIndent().prependIndent("              ")
            )
        )
        val positions = cleanPositions(cfg)
        assertTrue(
            positions.any { it.isArgument() },
            "the sanitized argument itself must still be cleaned; got $positions"
        )
    }

    @Test
    fun `without a focus metavariable every matched position is still cleaned`() {
        // The narrowing is deliberately scoped to rules that declare a focus metavariable. A sanitizer
        // that declares none has no way to say which value it sanitizes, so it keeps the wide
        // behaviour rather than silently losing clean actions.
        val cfg = config(
            rule("              - pattern: ${'$'}SAFE = (javax.servlet.http.HttpServletRequest ${'$'}REQ).getRequestURI();")
        )
        val positions = cleanPositions(cfg)
        assertTrue(
            positions.any { it.isThis() },
            "expected the focus-free form to keep cleaning every matched position; got $positions"
        )
    }
}
