package org.opentaint.semgrep.pattern

import org.opentaint.dataflow.configuration.go.serialized.GoSerializedAssignAction
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedCleanAction
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedCondition
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedItem
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedRule
import org.opentaint.semgrep.go.pattern.conversion.GoLanguageStrategy
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Unit-level checks that the Go taint-rule emitter threads the `$*X` star operator, giving Go the
 * same semantics as Java: `$X` = base-only taint; `$*X` = base + all nested fields (any-accessor).
 *
 * Mirrors [org.opentaint.semgrep.StarOperatorRuleGenTest] on the Java side, but the Go engine models
 * "any field" as a distinct ACTION/CONDITION variant on the SAME position (AnyAccessor /
 * ContainsMarkOnAnyAccessor), not as an AnyField position modifier.
 */
class GoStarOperatorEmitTest {

    private fun emitItems(ruleText: String): List<GoSerializedItem> {
        val loader = SemgrepRuleLoader(listOf(GoLanguageStrategy()))
        loader.registerRuleSet(ruleText, Path("star.yaml"), Path("."), SemgrepLoadTrace())
        val (rule, _) = loader.loadRules().rulesWithMeta.single()

        @Suppress("UNCHECKED_CAST")
        val taintRule = rule as TaintRuleFromSemgrep<GoSerializedItem>
        return taintRule.taintRules.flatMap { it.rules }
    }

    private fun flatten(c: GoSerializedCondition): List<GoSerializedCondition> = when (c) {
        is GoSerializedCondition.Or -> listOf(c) + c.anyOf.flatMap { flatten(it) }
        is GoSerializedCondition.And -> listOf(c) + c.allOf.flatMap { flatten(it) }
        is GoSerializedCondition.Not -> listOf(c) + flatten(c.not)
        else -> listOf(c)
    }

    private fun sinkConditions(items: List<GoSerializedItem>): List<GoSerializedCondition> =
        items.filterIsInstance<GoSerializedRule.Sink>()
            .mapNotNull { it.condition }
            .flatMap { flatten(it) }

    private fun sourceTaint(items: List<GoSerializedItem>): List<GoSerializedAssignAction> =
        items.filterIsInstance<GoSerializedRule.Source>().flatMap { it.taint }

    private fun cleanerCleans(items: List<GoSerializedItem>): List<GoSerializedCleanAction> =
        items.filterIsInstance<GoSerializedRule.Cleaner>().flatMap { it.cleans }

    @Test
    fun `starred sink checks base and any-accessor on the same arg`() {
        val items = emitItems(
            """
            rules:
              - id: go-star-sink
                languages: [go]
                mode: taint
                message: x
                severity: ERROR
                pattern-sources:
                  - pattern: "util.Source(...)"
                pattern-sinks:
                  - patterns:
                      - pattern: "util.Sink(${'$'}*Y)"
                      - focus-metavariable: ${'$'}Y
            """.trimIndent()
        )
        val conditions = sinkConditions(items)
        val base = conditions.filterIsInstance<GoSerializedCondition.ContainsMark>()
        val anyAccessor = conditions.filterIsInstance<GoSerializedCondition.ContainsMarkOnAnyAccessor>()

        assertTrue(anyAccessor.isNotEmpty(), "expected a ContainsMarkOnAnyAccessor in the starred sink; got $conditions")
        assertTrue(base.isNotEmpty(), "expected a plain ContainsMark in the starred sink; got $conditions")

        // Base coherence: each any-accessor check must be paired with a plain ContainsMark on the
        // SAME mark and SAME position -- the two arms of an Or over the metavar's resolved position.
        anyAccessor.forEach { af ->
            assertTrue(
                base.any { it.tainted == af.tainted && it.pos == af.pos },
                "any-accessor check $af has no paired plain ContainsMark on same mark/pos; base=$base"
            )
        }
    }

    @Test
    fun `starred assignment-LHS source taints base and any-accessor`() {
        val items = emitItems(
            """
            rules:
              - id: go-star-source
                languages: [go]
                mode: taint
                message: x
                severity: ERROR
                pattern-sources:
                  - pattern: "${'$'}*X = util.Source()"
                pattern-sinks:
                  - pattern: "util.Sink(${'$'}Y)"
            """.trimIndent()
        )
        val taint = sourceTaint(items)
        val direct = taint.filterIsInstance<GoSerializedAssignAction.Direct>()
        val anyAccessor = taint.filterIsInstance<GoSerializedAssignAction.AnyAccessor>()

        assertTrue(direct.isNotEmpty(), "expected a Direct assign in the starred source; got $taint")
        assertTrue(anyAccessor.isNotEmpty(), "expected an AnyAccessor assign in the starred source; got $taint")

        // Coherence: every any-accessor assign mirrors a direct one on the same mark and position.
        anyAccessor.forEach { any ->
            assertTrue(
                direct.any { it.kind == any.kind && it.pos == any.pos },
                "any-accessor assign $any has no paired Direct on same mark/pos; direct=$direct"
            )
        }
    }

    @Test
    fun `starred sanitizer cleans base and any-accessor`() {
        val items = emitItems(
            """
            rules:
              - id: go-star-sanitizer
                languages: [go]
                mode: taint
                message: x
                severity: ERROR
                pattern-sources:
                  - pattern: "${'$'}X = util.Source()"
                pattern-sanitizers:
                  - patterns:
                      - pattern: "util.Clean(${'$'}*X)"
                      - focus-metavariable: ${'$'}X
                pattern-sinks:
                  - pattern: "util.Sink(${'$'}X)"
            """.trimIndent()
        )
        val cleans = cleanerCleans(items)
        val direct = cleans.filterIsInstance<GoSerializedCleanAction.Direct>()
        val anyAccessor = cleans.filterIsInstance<GoSerializedCleanAction.AnyAccessor>()

        assertTrue(direct.isNotEmpty(), "expected a Direct clean in the starred sanitizer; got $cleans")
        assertTrue(anyAccessor.isNotEmpty(), "expected an AnyAccessor clean in the starred sanitizer; got $cleans")

        // Coherence: every any-accessor clean mirrors a direct one on the same mark and position.
        // The direct clean is REQUIRED: the any-accessor clean removes marks stored under an ANY
        // accessor but does NOT reach a concrete base mark, so both must be emitted.
        anyAccessor.forEach { any ->
            assertTrue(
                direct.any { it.taintKind == any.taintKind && it.pos == any.pos },
                "any-accessor clean $any has no paired Direct on same mark/pos; direct=$direct"
            )
        }
    }

    @Test
    fun `starred TYPED sink checks base and any-accessor with the type constraint retained`() {
        // `($*Y : string)` is a starred TYPED metavar. It lowers to And(IsMetavar(star), TypeIs),
        // which the automata flattens into two per-position atoms; the starred IsMetavar atom must
        // still thread the any-accessor arm, and the TypeIs atom must still emit an IsType check.
        val items = emitItems(
            """
            rules:
              - id: go-star-typed-sink
                languages: [go]
                mode: taint
                message: x
                severity: ERROR
                pattern-sources:
                  - pattern: "util.Source(...)"
                pattern-sinks:
                  - patterns:
                      - pattern: "util.Sink((${'$'}*Y : string))"
                      - focus-metavariable: ${'$'}Y
            """.trimIndent()
        )
        val conditions = sinkConditions(items)
        val base = conditions.filterIsInstance<GoSerializedCondition.ContainsMark>()
        val anyAccessor = conditions.filterIsInstance<GoSerializedCondition.ContainsMarkOnAnyAccessor>()

        assertTrue(anyAccessor.isNotEmpty(), "expected a ContainsMarkOnAnyAccessor for the starred typed sink; got $conditions")
        assertTrue(base.isNotEmpty(), "expected a plain ContainsMark for the starred typed sink; got $conditions")
        assertTrue(
            conditions.any { it is GoSerializedCondition.IsType },
            "expected the type constraint (IsType) to survive alongside the star; got $conditions"
        )
        anyAccessor.forEach { af ->
            assertTrue(
                base.any { it.tainted == af.tainted && it.pos == af.pos },
                "any-accessor check $af has no paired plain ContainsMark on same mark/pos; base=$base"
            )
        }
    }

    @Test
    fun `non-star sink checks base only`() {
        val items = emitItems(
            """
            rules:
              - id: go-plain-sink
                languages: [go]
                mode: taint
                message: x
                severity: ERROR
                pattern-sources:
                  - pattern: "util.Source(...)"
                pattern-sinks:
                  - patterns:
                      - pattern: "util.Sink(${'$'}Y)"
                      - focus-metavariable: ${'$'}Y
            """.trimIndent()
        )
        val conditions = sinkConditions(items)
        assertTrue(
            conditions.none { it is GoSerializedCondition.ContainsMarkOnAnyAccessor },
            "a non-star \$Y sink must be base-only (no ContainsMarkOnAnyAccessor); got $conditions"
        )
        assertTrue(
            conditions.any { it is GoSerializedCondition.ContainsMark },
            "expected a base ContainsMark in the plain sink; got $conditions"
        )
    }
}
