package org.opentaint.semgrep

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.semgrep.util.SampleBasedTest
import kotlin.test.Test

/**
 * Regression tests that pin behaviours we expect the analyzer to keep
 * supporting after touching the pattern matcher or join-mode loader.
 *
 * They originated as bisection points while investigating the rules/test
 * regression where {@code Files.writeString} and {@code Files.readString}
 * sink patterns do not fire on the source-built analyzer even though
 * {@code Files.write}/{@code Files.readAllBytes} do. The bug reproduces
 * only inside the full path-traversal rule chain (`path-traversal-in-servlet-app`
 * with the production servlet source and the 100+ Files patterns); each
 * minimal scenario below — taken in isolation — works correctly. Keeping
 * the tests as a baseline ensures the analyzer continues to handle them
 * once the root cause of the rules/test regression is fixed.
 */
@TestInstance(PER_CLASS)
class AnalyzerBugsTest : SampleBasedTest(configurationRequired = true) {

    /**
     * Static-method sanitizer patterns with `(...)` arguments must apply
     * to every overload of the named method. Verified against synthetic
     * helper classes — passes — and against multi-overload library methods
     * in rules/test, where it also passes.
     */
    @Test
    fun `sanitizer pattern applies to multi-overload static method`() =
        runTest<analyzerbugs.SanitizerOverloadBug>()

    /**
     * A static-method pattern in `pattern-sinks` must match a call whose
     * return value is discarded by the caller.
     */
    @Test
    fun `sink pattern matches call whose return value is discarded`() =
        runTest<analyzerbugs.SinkPatternBug>()

    /**
     * `Files.writeString($F, ...)` and `Files.readAllBytes($F, ...)` are
     * structurally identical sink patterns; both must fire when a tainted
     * Path flows into the call. This test exercises the JDK class directly
     * with both literal- and variable-arg shapes for the CharSequence
     * argument. The synthetic version passes; the equivalent annotated
     * sample in rules/test (PathTraversalNioSinksSamples$UnsafeWriteStringServlet)
     * does not — see the class doc above.
     */
    @Test
    fun `Files writeString sink fires the same way Files readAllBytes does`() =
        runTest<analyzerbugs.FilesWriteStringBug>()

    /**
     * Same shape as the test above but with the source pattern shifted
     * from a no-arg helper to an assignment from a getParameter-style call —
     * the production servlet source uses this form.
     */
    @Test
    fun `Files writeString fires with assignment-from-getParameter source`() =
        runTest<analyzerbugs.RealRuleFilesBug>()

    /**
     * Mirror of the failing production rule structure: a join-mode rule
     * whose refs point at separate source and sink sub-rules, joined via
     * `untrusted-data.$UNTRUSTED -> sink.$FILE`. The synthetic join below
     * resolves and fires; rules/test's production `path-traversal-in-servlet-app`
     * — which has the exact same structure but resolves refs to lib rules
     * loaded from disk — does not fire for the Files.writeString sink.
     */
    @Test
    fun `Files writeString fires under join-mode source-sink binding`() =
        runTest<analyzerbugs.FilesWriteStringJoinBug>()

    /**
     * The {@code $UNTRUSTED = ($TYPE $X).getter(...)} source pattern must
     * fire regardless of the enclosing method's name. Many rules/test
     * source-coverage samples use harness methods named, e.g.,
     * doGet_getQueryString that do not match the rule's entry-point
     * alternative; the assignment alternative must still kick in.
     */
    @Test
    fun `assignment-from-getter source fires inside non-doGet entry`() =
        runTest<analyzerbugs.AssignmentSourceBug>()

    /**
     * Source pattern `$UNTRUSTED = ($TYPE $X).$METHOD(...)` with metavariable-regex
     * on $METHOD must match assignment-from-getter inside any method body, not
     * only inside methods whose name matches a separate entry-point alternative.
     */
    @Test
    fun `assignment-from-typed-getter source fires inside non-canonical entry`() =
        runTest<analyzerbugs.ServletParamSourceBug>()

    @AfterAll fun tearDown() = closeRunner()
}
