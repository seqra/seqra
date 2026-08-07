package org.opentaint.semgrep

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.semgrep.util.SampleBasedTest
import org.opentaint.semgrep.util.TestAnalysisRunner
import kotlin.test.Test

@TestInstance(PER_CLASS)
class StarOperatorTest : SampleBasedTest() {
    // The starred SOURCE ($*X = src()) taints the whole object and every field; a concrete
    // field read only inherits that taint once the any-accessor is unrolled to a field read.
    // Mirror the Go harness and enable unrolling for THIS sample only (StarSink/StarSanitizer
    // keep the default AnyAccessorDisabled). Removing the source `*` makes the Positive a false
    // negative, proving the star is load-bearing here.
    @Test
    fun `star source field flow`() =
        runTest<taint.StarSource>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    @Test
    fun `star sink any field`() = runTest<taint.StarSink>()

    @Test
    fun `star sanitizer clears field taint`() = runTest<taint.StarSanitizer>()

    // ---- Deep-nesting matrix: taint hidden 5+ fields deep and/or 5+ calls deep ----

    // Starred source, taint 5 fields deep, unhidden by a nested field read. Needs the
    // any-accessor unroll (like `star source field flow`) so the source star reaches a
    // concrete deep field read.
    @Test
    fun `star deep source field flow`() =
        runTest<taint.StarDeepSource>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    // Starred sink observes taint written 5 fields deep (default unroll).
    @Test
    fun `star deep sink any field`() = runTest<taint.StarDeepSink>()

    // Starred sanitizer must clear taint 5 fields deep (default unroll).
    @Test
    fun `star deep sanitizer clears field taint`() = runTest<taint.StarDeepSanitizer>()

    // Starred source threaded through a 5+ hop interprocedural chain that alternately hides
    // taint inside an object and exposes it. Needs the any-accessor unroll.
    @Test
    fun `star interprocedural chain`() =
        runTest<taint.StarInterproc>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    // Both ends starred: whole-object source + whole-object sink, nested object in between.
    @Test
    fun `star source and sink`() =
        runTest<taint.StarSourceAndSink>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    // Starred source + starred sanitizer over a deep field chain.
    @Test
    fun `star source and sanitizer`() =
        runTest<taint.StarSourceAndSanitizer>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    // ---- Combined matrix: 5+ interprocedural depth x 5+ field depth, sources/sinks deep ----
    //
    // Every StarMatrix* sample places the source statement 5 calls deep, the sink call 5 calls
    // deep, and moves the taint one field level per hop (or threads a 5-level object), so the
    // interprocedural and field dimensions are exercised TOGETHER, not separately.

    @Test
    fun `star matrix source - deep source, per-hop unwrap, deep sink`() =
        runTest<taint.StarMatrixSource>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    @Test
    fun `star matrix sink - deep source, per-hop wrap, deep starred sink`() =
        runTest<taint.StarMatrixSink>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    // Both propagator occurrences starred ($*T = pass($*F)): the FROM observes any-field taint
    // of the whole argument, the TO assigns whole-object taint verified by a per-hop unwrap.
    @Test
    fun `star matrix propagator - starred from and to move whole-object taint`() =
        runTest<taint.StarMatrixPropagator>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    // The starred clean sits inside a wrapper helper — the deep-mark-exclusion regression shape.
    @Test
    fun `star matrix sanitizer - wrapped whole-object clean across summaries`() =
        runTest<taint.StarMatrixSanitizer>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    // Composition stress for the deep mark exclusions: the starred clean under TWO wrapper
    // summaries, with the sanitized flow itself inside a further summarized helper.
    @Test
    fun `star nested wrapper sanitizer - deep exclusion composes across summary levels`() =
        runTest<taint.StarNestedWrapperSanitizer>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    // Mixed exclusion kinds on one flow: deep (starred clean) + plain (value clean) refinements
    // of the same initial fact — the always-propagate-deep-marks regression net.
    @Test
    fun `star mixed exclusion sanitizer - deep and plain exclusions compose`() =
        runTest<taint.StarMixedExclusionSanitizer>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    @Test
    fun `star matrix pattern-not - starred sink with excluded emit mode`() =
        runTest<taint.StarMatrixPatternNot>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    @Test
    fun `star matrix pattern-inside - starred sink gated by receiver origin`() =
        runTest<taint.StarMatrixPatternInside>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    // The pattern-inside context that wires the guard receiver ($G = checker(); ...) converts
    // via the state-var mechanism, like the shipped setContentType suppression.
    @Test
    fun `star matrix pattern-not-inside - starred sink suppressed by guard`() =
        runTest<taint.StarMatrixPatternNotInside>(
            expectStateVar = true,
            unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled,
        )

    @AfterAll
    fun close() {
        closeRunner()
    }
}
