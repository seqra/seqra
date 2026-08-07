package org.opentaint.semgrep

import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SinkRule
import org.opentaint.semgrep.pattern.SemgrepLoadTrace
import org.opentaint.semgrep.pattern.SemgrepRuleLoader
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.semgrep.pattern.conversion.JavaLanguageStrategy
import org.opentaint.semgrep.pattern.createTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedItem
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.PositionModifier
import org.opentaint.dataflow.configuration.jvm.serialized.SourceRule
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class StarOperatorRuleGenTest {
    protected fun config(ruleText: String): SerializedTaintConfig {
        val trace = SemgrepLoadTrace()
        val loader = SemgrepRuleLoader(listOf(JavaLanguageStrategy()))
        loader.registerRuleSet(ruleText, Path("star.yaml"), Path("."), trace)
        val (rule, _) = loader.loadRules().rulesWithMeta.single()
        @Suppress("UNCHECKED_CAST")
        return (rule as TaintRuleFromSemgrep<SerializedItem>).createTaintConfig()
    }

    private fun allConditions(cfg: SerializedTaintConfig): List<SerializedCondition> {
        val sinkRules: List<SinkRule> = buildList {
            addAll(cfg.sink.orEmpty())
            addAll(cfg.methodExitSink.orEmpty())
            addAll(cfg.methodEntrySink.orEmpty())
        }
        val sourceRules: List<SourceRule> = buildList {
            addAll(cfg.source.orEmpty())
            addAll(cfg.methodExitSource.orEmpty())
            addAll(cfg.entryPoint.orEmpty())
        }
        val conditions = sinkRules.mapNotNull { it.condition } +
            sourceRules.mapNotNull { it.condition } +
            cfg.passThrough.orEmpty().mapNotNull { it.condition }
        return conditions.flatMap { flatten(it) }
    }

    private fun flatten(c: SerializedCondition): List<SerializedCondition> = when (c) {
        is SerializedCondition.Or -> listOf(c) + c.anyOf.flatMap { flatten(it) }
        is SerializedCondition.And -> listOf(c) + c.allOf.flatMap { flatten(it) }
        else -> listOf(c)
    }

    private fun sourceAssignPositions(cfg: SerializedTaintConfig): List<PositionBaseWithModifiers> =
        (cfg.source.orEmpty() + cfg.entryPoint.orEmpty()).filterIsInstance<SourceRule>()
            .flatMap { it.taint }
            .map { it.pos }

    private fun cleanPositions(cfg: SerializedTaintConfig): List<PositionBaseWithModifiers> =
        cfg.cleaner.orEmpty()
            .flatMap { it.cleans }
            .map { it.pos }

    @Test
    fun `starred source assigns value and any-field`() {
        val cfg = config(
            """
            rules:
              - id: star-source
                severity: NOTE
                message: x
                languages: [java]
                mode: taint
                pattern-sources:
                  - patterns:
                      - pattern: sink(${'$'}*X);
                      - focus-metavariable: ${'$'}X
                pattern-sinks:
                  - pattern: other(${'$'}Y);
            """.trimIndent()
        )
        val positions = sourceAssignPositions(cfg)
        assertTrue(
            positions.any { it is PositionBaseWithModifiers.BaseOnly },
            "expected a plain-value assign; got $positions"
        )
        assertTrue(
            positions.any {
                it is PositionBaseWithModifiers.WithModifiers &&
                    it.modifiers.contains(PositionModifier.AnyField)
            },
            "expected an any-field assign; got $positions"
        )
    }

    @Test
    fun `starred assignment-LHS source assigns value and any-field`() {
        // F1: the star sits on the assignment LHS metavar (`$*X = src()`), not a call argument.
        // The whole-object taint must still emit BOTH a plain-value assign and an any-field assign.
        val cfg = config(
            """
            rules:
              - id: star-assign-source
                severity: NOTE
                message: x
                languages: [java]
                mode: taint
                pattern-sources:
                  - pattern: ${'$'}*X = src();
                pattern-sinks:
                  - pattern: sink(${'$'}Y);
            """.trimIndent()
        )
        val positions = sourceAssignPositions(cfg)
        assertTrue(
            positions.any { it is PositionBaseWithModifiers.BaseOnly },
            "expected a plain-value assign; got $positions"
        )
        assertTrue(
            positions.any {
                it is PositionBaseWithModifiers.WithModifiers &&
                    it.modifiers.contains(PositionModifier.AnyField)
            },
            "expected an any-field assign; got $positions"
        )
    }

    @Test
    fun `starred typed-declaration source assigns value and any-field`() {
        // F5: a starred TYPED declaration (`String $*X = src()`) must load and thread the star
        // through the assignment path, emitting both a plain-value and an any-field assign.
        val cfg = config(
            """
            rules:
              - id: star-typed-decl-source
                severity: NOTE
                message: x
                languages: [java]
                mode: taint
                pattern-sources:
                  - pattern: String ${'$'}*X = src();
                pattern-sinks:
                  - pattern: sink(${'$'}Y);
            """.trimIndent()
        )
        val positions = sourceAssignPositions(cfg)
        assertTrue(
            positions.any { it is PositionBaseWithModifiers.BaseOnly },
            "expected a plain-value assign; got $positions"
        )
        assertTrue(
            positions.any {
                it is PositionBaseWithModifiers.WithModifiers &&
                    it.modifiers.contains(PositionModifier.AnyField)
            },
            "expected an any-field assign; got $positions"
        )
    }

    @Test
    fun `starred sanitizer cleans value and any-field`() {
        val cfg = config(
            """
            rules:
              - id: star-sanitizer
                severity: NOTE
                message: x
                languages: [java]
                mode: taint
                pattern-sources:
                  - pattern: ${'$'}X = src();
                pattern-sanitizers:
                  - patterns:
                      - pattern: clean(${'$'}*X);
                      - focus-metavariable: ${'$'}X
                pattern-sinks:
                  - pattern: sink(${'$'}X);
            """.trimIndent()
        )
        val positions = cleanPositions(cfg)
        val plain = positions.filterIsInstance<PositionBaseWithModifiers.BaseOnly>()
        val anyField = positions.filterIsInstance<PositionBaseWithModifiers.WithModifiers>()
            .filter { it.modifiers.contains(PositionModifier.AnyField) }
        assertTrue(plain.isNotEmpty(), "expected a plain-value clean; got $positions")
        assertTrue(anyField.isNotEmpty(), "expected an any-field clean; got $positions")
        // Base coherence: the any-field clean must sit on the SAME base as the
        // plain value clean (both PositionBase.Result), not the raw metavar
        // argument position — otherwise field taint survives the sanitizer.
        assertTrue(
            plain.any { it.base == PositionBase.Result },
            "expected plain clean on Result; got $positions"
        )
        assertTrue(
            anyField.any { it.base == PositionBase.Result },
            "expected any-field clean on Result (same base as plain value clean); got $positions"
        )
    }

    @Test
    fun `starred sanitizer assignment cleans returned value and any-field`() {
        val cfg = config(
            """
            rules:
              - id: starred-receiver-sanitizer
                severity: NOTE
                message: x
                languages: [java]
                mode: taint
                pattern-sources:
                  - pattern: ${'$'}X = src();
                pattern-sanitizers:
                  - patterns:
                      - pattern: ${'$'}*CLEAN = ${'$'}REQ.clean();
                      - focus-metavariable: ${'$'}CLEAN
                pattern-sinks:
                  - pattern: sink(${'$'}X);
            """.trimIndent()
        )
        val positions = cleanPositions(cfg)

        assertTrue(
            positions.any {
                it is PositionBaseWithModifiers.WithModifiers &&
                    it.base == PositionBase.Result &&
                    it.modifiers.contains(PositionModifier.AnyField)
            },
            "expected an any-field clean on Result; got $positions"
        )
    }

    @Test
    fun `starred sink produces ContainsMarkOnAnyField`() {
        val cfg = config(
            """
            rules:
              - id: star-sink
                severity: NOTE
                message: x
                languages: [java]
                mode: taint
                pattern-sources:
                  - pattern: ${'$'}X = src();
                pattern-sinks:
                  - patterns:
                      - pattern: sink(${'$'}*Y);
                      - focus-metavariable: ${'$'}Y
            """.trimIndent()
        )
        val conditions = allConditions(cfg)
        val anyField = conditions.filterIsInstance<SerializedCondition.ContainsMarkOnAnyField>()
        assertTrue(anyField.isNotEmpty(), "expected a ContainsMarkOnAnyField in the starred sink config")

        // Base coherence: every any-field check must be paired with a plain
        // ContainsMark on the SAME mark and SAME position base — a starred sink
        // matches the value OR any of its nested fields, both anchored to the
        // metavar's resolved position. A base/mark mismatch would be a silent bug.
        val plain = conditions.filterIsInstance<SerializedCondition.ContainsMark>()
        anyField.forEach { af ->
            assertTrue(
                plain.any { it.tainted == af.tainted && it.pos.base == af.pos.base },
                "any-field check $af has no paired plain ContainsMark on the same mark/base; plain=$plain"
            )
        }
    }

    @Test
    fun `starred propagator copies over any-field`() {
        val cfg = config(
            """
            rules:
              - id: star-prop
                severity: NOTE
                message: x
                languages: [java]
                mode: taint
                pattern-sources:
                  - pattern: ${'$'}X = src();
                pattern-propagators:
                  - patterns:
                      - pattern: ${'$'}TO = wrap(${'$'}*X);
                    from: ${'$'}X
                    to: ${'$'}TO
                pattern-sinks:
                  - pattern: sink(${'$'}TO);
            """.trimIndent()
        )
        // A starred propagator source occurrence must reference an any-field position
        val anyField = allConditions(cfg).any { it is SerializedCondition.ContainsMarkOnAnyField } ||
            sourceAssignPositions(cfg).any {
                it is PositionBaseWithModifiers.WithModifiers && it.modifiers.contains(PositionModifier.AnyField)
            }
        assertTrue(anyField, "expected any-field involvement in starred propagator")
    }

    @Test
    fun `starred pattern-not sink still generates any-field check`() {
        val cfg = config(
            """
            rules:
              - id: star-not
                severity: NOTE
                message: x
                languages: [java]
                mode: taint
                pattern-sources:
                  - pattern: ${'$'}X = src();
                pattern-sinks:
                  - patterns:
                      - pattern: sink(${'$'}*Y);
                      - pattern-not: sink(safe());
                      - focus-metavariable: ${'$'}Y
            """.trimIndent()
        )
        assertTrue(
            allConditions(cfg).any { it is SerializedCondition.ContainsMarkOnAnyField },
            "pattern-not sink must still carry the any-field check"
        )
    }

    // Mirrors the shipped xss sanitizer shape: the starred metavar sits inside a `pattern-either`
    // at a NON-first arg position with leading/trailing varargs `...`, focused separately. This is
    // the shape the OWASP escapeHtml sanitizer uses; if the any-field clean is lost here (but not
    // in the simple `clean($*X)` case), that explains why starring the shipped sanitizer was a no-op.
    @Test
    fun `starred sanitizer in pattern-either with varargs still cleans any-field on Result`() {
        val cfg = config(
            """
            rules:
              - id: star-san-either
                severity: NOTE
                message: x
                languages: [java]
                mode: taint
                pattern-sources:
                  - pattern: ${'$'}X = src();
                pattern-sanitizers:
                  - patterns:
                      - pattern-either:
                          - pattern: esc(..., ${'$'}*X, ...);
                      - focus-metavariable: ${'$'}X
                pattern-sinks:
                  - pattern: sink(${'$'}X);
            """.trimIndent()
        )
        val positions = cleanPositions(cfg)
        val anyField = positions.filterIsInstance<PositionBaseWithModifiers.WithModifiers>()
            .filter { it.modifiers.contains(PositionModifier.AnyField) }
        assertTrue(
            anyField.any { it.base == PositionBase.Result },
            "expected any-field clean on Result for the pattern-either/varargs sanitizer; got $positions"
        )
    }
}
