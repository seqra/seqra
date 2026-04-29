package org.opentaint.semgrep

import issues.issue83
import issues.issue83aux
import issues.issue84
import issues.issue85
import issues.issue86
import issues.issue87
import issues.issue88
import issues.issue89
import issues.issue90
import issues.issue91
import issues.issue92
import issues.issue93
import issues.issue94
import issues.issue95
import issues.issue96
import issues.issue97
import issues.issue98
import issues.issue99
import issues.issue100
import issues.issue102
import issues.issue103
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.semgrep.util.SampleBasedTest
import kotlin.test.Test

@TestInstance(PER_CLASS)
class IssuesTest : SampleBasedTest() {
    @Test
    fun `issue 69`() = runTest<issues.issue69>()

    @Test // todo: variable assign
    @Disabled
    fun `issue 70`() = runTest<issues.issue70>()

    @Test
    fun `issue 71`() = runTest<issues.issue71>()

    @Test
    fun `issue 74`() = runTest<issues.issue74>()

    @Test
    fun `issue 75`() = runTest<issues.issue75>()

    @Test
    fun `issue 76`() = runTest<issues.issue76>()

    @Test
    fun `issue 77`() = runTest<issues.issue77>()

    @Test
    fun `issue 78`() = runTest<issues.issue78>()

    @Test
    fun `issue 83 aux`() = runTest<issue83aux>()

    @Test
    @Disabled // todo: sink on string concatenation
    fun `issue 83`() = runTest<issue83>()

    @Test
    @Disabled // todo: array element accessor
    fun `issue 84`() = runTest<issue84>()

    @Test
    fun `issue 85`() = runTest<issue85>()

    @Test
    fun `issue 86`() = runTest<issue86>()

    @Test
    fun `issue 87`() = runTest<issue87>()

    @Test
    @Disabled // todo: type complex pattern
    fun `issue 88`() = runTest<issue88>()

    @Test
    @Disabled // todo: assume metavariable can be a plain string
    fun `issue 89`() = runTest<issue89>()

    @Test
    @Disabled // todo: ignored pattern-not-inside
    fun `issue 90`() = runTest<issue90>()

    @Test
    fun `issue 91`() = runTest<issue91>()

    @Test
    @Disabled // todo: shorthand source objects
    fun `issue 92`() = runTest<issue92>()

    @Test
    @Disabled // todo: rule expects an argument at sink, but none also triggers the condition
    fun `issue 93`() = runTest<issue93>()

    @Test
    fun `issue 94`() = runTest<issue94>()

    @Test
    fun `issue 95`() = runTest<issue95>()

    @Test
    fun `issue 96`() = runTest<issue96>()

    @Test
    fun `issue 97`() = runTest<issue97>()

    @Test
    @Disabled // todo: pattern-not-inside containing a compound formula (pattern-either /
              // metavariable-pattern) is rejected as "pattern-inside must be a simple
              // pattern". Equivalent to multiple pattern-not-inside siblings; engine
              // should accept it.
    fun `issue 98`() = runTest<issue98>()

    @Test
    @Disabled // todo: two method-name metavariables on a single chained call
              // ($R.$M1(...).$M2(...)) fails with "Method name metavar constraints
              // intersection". Authoring two-step call chains where both methods are
              // metavar-constrained should be supported.
    fun `issue 99`() = runTest<issue99>()

    @Test
    @Disabled // todo: diamond / generic class names (Foo<>, Foo<$E>) used as
              // metavariable-pattern alternatives fail with "no viable alternative"
              // at the AST parser. The failed alternatives are silently dropped from
              // the pattern-either, so a rule with only such alternatives ends up
              // with an unsatisfiable Or(emptySet) constraint and never matches.
              // Should be both: fix the parser to accept these forms AND surface
              // the parse failure to the rule author.
    fun `issue 100`() = runTest<issue100>()

    @Test
    @Disabled // todo: a rule with many `pattern-not-inside` entries hits the
              // 2s automata build budget ("Failed to transform pattern to
              // automata: Operation timeout"). Each pattern-not-inside
              // complements an Inside automaton and intersects with the
              // running result; the operation count grows superlinearly.
              // Switching identical filtering to `pattern-not` (no Inside
              // wrapper) avoids the timeout. Engine should either widen the
              // budget for this construct, build the complement lazily, or
              // unify equivalent Inside automata before complementing.
    fun `issue 102`() = runTest<issue102>()

    @Test
    @Disabled // todo: the assignment-form sanitizer
              // `pattern: $RESULT = $X.safe(...).body(...)` with
              // `focus-metavariable: $RESULT` does not propagate cleanliness
              // to downstream uses of $RESULT. The IFDS engine does not
              // recognise this builder-chain shape as sanitising the body()
              // argument, so taint flows through `body()` and reaches the
              // sink. Documented in the original spring-xss-html-response
              // rule comments; the rewrite no longer attempts this form.
    fun `issue 103`() = runTest<issue103>()

    @AfterAll
    fun close() {
        closeRunner()
    }
}
