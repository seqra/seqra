package org.opentaint.semgrep

import org.opentaint.semgrep.pattern.Metavar
import org.opentaint.semgrep.pattern.SemgrepJavaPattern
import org.opentaint.semgrep.pattern.SemgrepJavaPatternParser
import org.opentaint.semgrep.pattern.SemgrepJavaPatternParsingResult
import org.opentaint.semgrep.pattern.SemgrepLoadTrace
import org.opentaint.semgrep.pattern.SemgrepRuleLoader
import org.opentaint.semgrep.pattern.TypedMetavar
import org.opentaint.semgrep.pattern.conversion.JavaLanguageStrategy
import org.opentaint.semgrep.pattern.errorEntries
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class StarOperatorParseTest {
    private fun collect(p: SemgrepJavaPattern): List<SemgrepJavaPattern> =
        listOf(p) + p.children.flatMap { collect(it) }

    private fun metavars(pattern: String): List<Metavar> =
        collect(parseJavaSemgrepPattern(pattern)).filterIsInstance<Metavar>()

    private fun blockingErrors(ruleText: String): List<String> {
        val trace = SemgrepLoadTrace()
        val loader = SemgrepRuleLoader(listOf(JavaLanguageStrategy()))
        loader.registerRuleSet(ruleText, Path("repro.yaml"), Path("."), trace)
        loader.loadRules()
        return trace.errorEntries().map { "${it.severity}/${it.step}: ${it.message}" }
    }

    @Test
    fun `starred metavar in call argument`() {
        val mvs = metavars("sink(\$*Y);")
        val y = mvs.single { it.name == "\$Y" }
        assertTrue(y.star, "expected \$*Y to be starred")
    }

    @Test
    fun `starred typed variable declaration`() {
        // F5: the starred `variableDeclaratorId` alternative in a TYPED declaration must load
        // (no parse exception) and the declared LHS metavar must carry star=true.
        val mvs = metavars("String \$*UNTRUSTED = \$REQ.getParameter(\"q\");")
        val u = mvs.single { it.name == "\$UNTRUSTED" }
        assertTrue(u.star, "expected typed-declaration \$*UNTRUSTED to be starred")
    }

    @Test
    fun `starred typed metavar in receiver position`() {
        // `(Type $*VAR).m()` is how a sink observes taint buried in a field of its RECEIVER —
        // e.g. a java.io.File whose path is tainted but whose base carries no mark. Before the
        // typedVariableExpression grammar accepted STARRED_METAVAR this failed to parse, and the
        // whole pattern was dropped SILENTLY (the rule count shrank, no error was reported).
        val typed = collect(parseJavaSemgrepPattern("(java.io.File \$*FILE).exists();"))
            .filterIsInstance<TypedMetavar>()
        val f = typed.single { it.name == "\$FILE" }
        assertTrue(f.star, "expected receiver \$*FILE to be starred")
    }

    @Test
    fun `starred typed metavar in argument position`() {
        val typed = collect(parseJavaSemgrepPattern("sink((java.nio.file.Path \$*P));"))
            .filterIsInstance<TypedMetavar>()
        val p = typed.single { it.name == "\$P" }
        assertTrue(p.star, "expected parenthesised typed \$*P to be starred")
    }

    @Test
    fun `starred bare return value`() {
        val mvs = metavars("return \$*UNTRUSTED;")
        val u = mvs.single { it.name == "\$UNTRUSTED" }
        assertTrue(u.star)
    }

    @Test
    fun `star pattern loads without blocking errors and binds same name`() {
        // \$UNTRUSTED appears starred in the source and unstarred in the sink;
        // they must refer to the same metavariable and the rule must load cleanly.
        val rule = """
            rules:
              - id: star-bind-repro
                options: { lib: true }
                severity: NOTE
                message: x
                languages: [java]
                mode: taint
                pattern-sources:
                  - pattern: ${'$'}*UNTRUSTED = src();
                pattern-sinks:
                  - pattern: sink(${'$'}UNTRUSTED);
        """.trimIndent()
        val errors = blockingErrors(rule)
        assertTrue(errors.isEmpty(), "star rule failed to load:\n" + errors.joinToString("\n"))
    }

    /**
     * Parses a single Java semgrep pattern string into a [SemgrepJavaPattern], throwing on failure.
     *
     * Thin convenience wrapper over [SemgrepJavaPatternParser.parseSemgrepJavaPattern] using the exact
     * same parser path the rule loader uses (see conversion/SemgrepPatternParser.kt).
     */
    private fun parseJavaSemgrepPattern(pattern: String): SemgrepJavaPattern =
        when (val result = SemgrepJavaPatternParser().parseSemgrepJavaPattern(pattern)) {
            is SemgrepJavaPatternParsingResult.Ok -> result.pattern
            is SemgrepJavaPatternParsingResult.ParserFailure -> throw result.exception
            is SemgrepJavaPatternParsingResult.OtherFailure -> throw result.exception
            is SemgrepJavaPatternParsingResult.FailedASTParsing ->
                error("Failed to parse pattern '$pattern': ${result.errorMessages}")
        }
}
