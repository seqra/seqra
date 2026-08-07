package org.opentaint.semgrep

import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedItem
import org.opentaint.semgrep.pattern.SemgrepLoadTrace
import org.opentaint.semgrep.pattern.SemgrepRuleLoader
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.semgrep.pattern.conversion.JavaLanguageStrategy
import org.opentaint.semgrep.pattern.createTaintConfig
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The star / pattern-not coincidence matrix on a METHOD-DECLARATION source (a `pattern-not` that
 * negates the same parameter position). This is a distinct code path from the call-argument shape in
 * [StarPatternNotFieldOnlyTest]: a method-declaration `pattern-not` reaches
 * `MethodConstraintsSolver.addNegative`, whereas a call-argument `pattern-not` collapses at an
 * earlier automata-transform phase.
 *
 * With `$X` (base, A) and `$*X` (whole-object, A*) kept DISTINCT under the implication `A => A*`:
 *   - T/F (`$*X` positive ^ `pattern-not $X`) => `!A ^ A*` = field-only (keep field, drop base).
 *   - T/T (`$*X` positive ^ `pattern-not $*X`) => `!A* ^ A*` = contradiction = exclude-all.
 * The two must produce DIFFERENT configs.
 */
class StarPatternNotCoincidenceTest {
    private fun loadConfig(ruleText: String): SerializedTaintConfig? {
        val trace = SemgrepLoadTrace()
        val loader = SemgrepRuleLoader(listOf(JavaLanguageStrategy()))
        loader.registerRuleSet(ruleText, Path("star.yaml"), Path("."), trace)
        val ruleWithMeta = loader.loadRules().rulesWithMeta.singleOrNull()
        @Suppress("UNCHECKED_CAST")
        return (ruleWithMeta?.first as? TaintRuleFromSemgrep<SerializedItem>)?.createTaintConfig()
    }

    /**
     * A method-declaration source whose `pattern-not` negates the same `$UNTRUSTED` parameter.
     * @param positiveStar  star on the positive `$UNTRUSTED` occurrence
     * @param notMetavar    the metavar spelled in the `pattern-not` param slot (with or without `*`)
     */
    private fun methodRule(id: String, positiveStar: Boolean, notMetavar: String): String {
        val pos = if (positiveStar) "${'$'}*UNTRUSTED" else "${'$'}UNTRUSTED"
        return """
            rules:
              - id: $id
                severity: NOTE
                message: x
                languages: [java]
                mode: taint
                pattern-sources:
                  - patterns:
                      - pattern: |
                          @${'$'}ANNOTATION(...)
                          ${'$'}RETURNTYPE ${'$'}METHODNAME(..., ${'$'}TYPE $pos,...) {
                            ...
                          }
                      - pattern-not: |
                          @${'$'}ANNOTATION(...)
                          ${'$'}RETURNTYPE ${'$'}METHODNAME(..., @PathVariable ${'$'}TYPE $notMetavar,...) {
                            ...
                          }
                pattern-sinks:
                  - pattern: sink(${'$'}S);
        """.trimIndent()
    }

    @Test
    fun `T-F is a supported field-only exclusion and loads`() {
        // `$*UNTRUSTED` positive (A*) + `pattern-not $UNTRUSTED` (base A) => `!A ^ A*` = field-only.
        val config = loadConfig(methodRule("cmp", positiveStar = true, notMetavar = "${'$'}UNTRUSTED"))
        assertTrue(config != null, "field-only T/F rule must load")
    }

    @Test
    fun `T-F is field-only, distinct from the full-exclusion T-T case`() {
        // Same id so the two configs are comparable (marks embed the rule id).
        val tf = loadConfig(methodRule("cmp", positiveStar = true, notMetavar = "${'$'}UNTRUSTED"))
        val tt = loadConfig(methodRule("cmp", positiveStar = true, notMetavar = "${'$'}*UNTRUSTED"))
        assertTrue(tf != null, "T/F (field-only) rule must load")
        // T/T (`$*UNTRUSTED` ^ `pattern-not $*UNTRUSTED`) is a genuine contradiction => exclude-all,
        // which drops the source, so its config differs from the field-only T/F config.
        assertTrue(tf != tt, "T/F (field-only) must differ from T/T (exclude-all); tt=$tt")
    }

    @Test
    fun `structural non-coinciding pattern-not loads`() {
        // The pattern-not negates the same position with a DIFFERENT metavar (`$OTHER`) — a genuine
        // structural exclusion, not a coincidence with the positive `$*UNTRUSTED`.
        val config = loadConfig(methodRule("structural", positiveStar = true, notMetavar = "${'$'}OTHER"))
        assertTrue(config != null, "a non-coinciding structural pattern-not rule must load")
    }
}
