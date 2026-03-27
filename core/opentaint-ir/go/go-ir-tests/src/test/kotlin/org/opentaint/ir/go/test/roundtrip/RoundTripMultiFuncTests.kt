package org.opentaint.ir.go.test.roundtrip

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

/**
 * Round-trip tests exercising multi-return functions and inter-function calls.
 * These test the codegen's ability to handle Extract from tuple-producing Calls.
 */
@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripMultiFuncTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            // ── Basic multi-return ──────────────────────────────────────
            RoundTripTestCase.withInputs("divmod",
                """
func rtmf_divmod(a, b int) (int, int) {
    return a / b, a % b
}
func rtmf_divmod_call(a, b int) int {
    q, r := rtmf_divmod(a, b)
    return q*1000 + r
}""",
                "rtmf_divmod_call(%s, %s)",
                listOf(listOf("17", "5"), listOf("100", "7"), listOf("10", "3"), listOf("25", "5")),
                randomRange = 1..1000,
            ),
            RoundTripTestCase.withInputs("sum-diff",
                """
func rtmf_sumDiff(a, b int) (int, int) {
    return a + b, a - b
}
func rtmf_sumDiff_call(a, b int) int {
    s, d := rtmf_sumDiff(a, b)
    return s * d
}""",
                "rtmf_sumDiff_call(%s, %s)",
                listOf(listOf("5", "3"), listOf("10", "10"), listOf("7", "2"), listOf("0", "4")),
            ),
            RoundTripTestCase.withInputs("minmax",
                """
func rtmf_minMax(a, b int) (int, int) {
    if a < b { return a, b }
    return b, a
}
func rtmf_minMax_range(a, b int) int {
    mn, mx := rtmf_minMax(a, b)
    return mx - mn
}""",
                "rtmf_minMax_range(%s, %s)",
                listOf(listOf("3", "7"), listOf("10", "2"), listOf("5", "5"), listOf("-3", "3")),
            ),
            RoundTripTestCase.withInputs("split-join",
                """
func rtmf_split(n int) (int, int) {
    return n / 256, n % 256
}
func rtmf_splitJoin(n int) int {
    hi, lo := rtmf_split(n)
    return hi*256 + lo
}""",
                "rtmf_splitJoin(%s)",
                listOf(listOf("0"), listOf("255"), listOf("256"), listOf("1000"), listOf("65535")),
                randomRange = 0..65535,
            ),
            // ── Multi-return with conditionals ──────────────────────────
            RoundTripTestCase.withInputs("safe-div",
                """
func rtmf_safeDivMod(a, b int) (int, int, int) {
    if b == 0 { return 0, 0, 0 }
    return a / b, a % b, 1
}
func rtmf_safeDivEnc(a, b int) int {
    q, r, ok := rtmf_safeDivMod(a, b)
    if ok == 0 { return -1 }
    return q*100 + r
}""",
                "rtmf_safeDivEnc(%s, %s)",
                listOf(listOf("17", "5"), listOf("10", "0"), listOf("100", "3"), listOf("0", "1")),
            ),
            // ── Chain of multi-return calls ─────────────────────────────
            RoundTripTestCase.withInputs("chain-multi",
                """
func rtmf_addSub(a, b int) (int, int) {
    return a + b, a - b
}
func rtmf_mulDiv(a, b int) (int, int) {
    if b == 0 { return 0, 0 }
    return a * b, a / b
}
func rtmf_chainMulti(a, b int) int {
    s, d := rtmf_addSub(a, b)
    p, q := rtmf_mulDiv(s, d)
    return p + q
}""",
                "rtmf_chainMulti(%s, %s)",
                listOf(listOf("5", "3"), listOf("10", "4"), listOf("7", "7"), listOf("1", "0")),
            ),
            // ── Multi-return feeding into another function ──────────────
            RoundTripTestCase.withInputs("compose-multi",
                """
func rtmf_twoVals(x int) (int, int) {
    return x * 2, x + 10
}
func rtmf_combine(a, b int) int {
    return a*a + b*b
}
func rtmf_composeMR(x int) int {
    a, b := rtmf_twoVals(x)
    return rtmf_combine(a, b)
}""",
                "rtmf_composeMR(%s)",
                listOf(listOf("0"), listOf("1"), listOf("5"), listOf("-3"), listOf("10")),
            ),
            // ── Discard one return value ────────────────────────────────
            RoundTripTestCase.withInputs("discard-second",
                """
func rtmf_pair(x int) (int, int) {
    return x * 3, x + 100
}
func rtmf_discardSecond(x int) int {
    first, _ := rtmf_pair(x)
    return first
}""",
                "rtmf_discardSecond(%s)",
                listOf(listOf("0"), listOf("1"), listOf("5"), listOf("-2"), listOf("10")),
            ),
            RoundTripTestCase.withInputs("discard-first",
                """
func rtmf_pair2(x int) (int, int) {
    return x + 100, x * 3
}
func rtmf_discardFirst(x int) int {
    _, second := rtmf_pair2(x)
    return second
}""",
                "rtmf_discardFirst(%s)",
                listOf(listOf("0"), listOf("1"), listOf("5"), listOf("-2"), listOf("10")),
            ),
            // ── Multi-return in a loop ──────────────────────────────────
            RoundTripTestCase.withInputs("multi-in-loop",
                """
func rtmf_step(x int) (int, int) {
    return x + 1, x * 2
}
func rtmf_multiLoop(n int) int {
    if n < 0 { n = -n }
    if n > 20 { n = 20 }
    acc := 0
    val := 1
    for i := 0; i < n; i++ {
        val, acc = rtmf_step(val)
        acc = acc + val
    }
    return acc
}""",
                "rtmf_multiLoop(%s)",
                listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10"), listOf("15")),
            ),
            // ── Cross-function calls (single-return) ────────────────────
            RoundTripTestCase.withInputs("cross-call-simple",
                """
func rtmf_double(x int) int { return x * 2 }
func rtmf_inc(x int) int { return x + 1 }
func rtmf_crossSimple(x int) int {
    return rtmf_double(rtmf_inc(x))
}""",
                "rtmf_crossSimple(%s)",
                listOf(listOf("0"), listOf("5"), listOf("-3"), listOf("100")),
            ),
            RoundTripTestCase.withInputs("cross-call-chain",
                """
func rtmf_sq(x int) int { return x * x }
func rtmf_neg(x int) int { return -x }
func rtmf_chainCall(x int) int {
    a := rtmf_sq(x)
    b := rtmf_neg(a)
    c := rtmf_sq(b)
    return c
}""",
                "rtmf_chainCall(%s)",
                listOf(listOf("0"), listOf("1"), listOf("2"), listOf("-1"), listOf("3")),
            ),
            // ── Recursive multi-return ──────────────────────────────────
            RoundTripTestCase.withInputs("rec-fib-pair",
                """
func rtmf_fibPair(n int) (int, int) {
    if n <= 0 { return 0, 1 }
    a, b := rtmf_fibPair(n - 1)
    return b, a + b
}
func rtmf_fib(n int) int {
    if n < 0 { n = -n }
    if n > 30 { n = 30 }
    a, _ := rtmf_fibPair(n)
    return a
}""",
                "rtmf_fib(%s)",
                listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10"), listOf("20")),
                randomRange = 0..30,
            ),
            // ── GCD with extended info ──────────────────────────────────
            RoundTripTestCase.withInputs("ext-gcd",
                """
func rtmf_extGCD(a, b int) (int, int, int) {
    if b == 0 { return a, 1, 0 }
    g, x, y := rtmf_extGCD(b, a % b)
    return g, y, x - (a/b)*y
}
func rtmf_extGCDSum(a, b int) int {
    g, x, y := rtmf_extGCD(a, b)
    return g + x + y
}""",
                "rtmf_extGCDSum(%s, %s)",
                listOf(listOf("12", "8"), listOf("35", "15"), listOf("100", "23"), listOf("17", "13")),
                randomRange = 1..500,
            ),
            // ── Three return values ─────────────────────────────────────
            RoundTripTestCase.withInputs("triple-return",
                """
func rtmf_stats(a, b, c int) (int, int, int) {
    sum := a + b + c
    mn := a
    if b < mn { mn = b }
    if c < mn { mn = c }
    mx := a
    if b > mx { mx = b }
    if c > mx { mx = c }
    return sum, mn, mx
}
func rtmf_statsEnc(a, b, c int) int {
    s, mn, mx := rtmf_stats(a, b, c)
    return s*10000 + mn*100 + mx
}""",
                "rtmf_statsEnc(%s, %s, %s)",
                listOf(listOf("1", "2", "3"), listOf("5", "5", "5"), listOf("10", "1", "7"), listOf("-3", "0", "3")),
            ),
            // ── Multi-return used in conditional ────────────────────────
            RoundTripTestCase.withInputs("multi-ret-cond",
                """
func rtmf_classify(x int) (int, int) {
    if x > 0 { return 1, x }
    if x < 0 { return -1, -x }
    return 0, 0
}
func rtmf_classifyResult(x int) int {
    sign, abs := rtmf_classify(x)
    return sign * 1000 + abs
}""",
                "rtmf_classifyResult(%s)",
                listOf(listOf("5"), listOf("-3"), listOf("0"), listOf("100"), listOf("-100")),
            ),
            // ── Multi-return swap ───────────────────────────────────────
            RoundTripTestCase.withInputs("multi-swap",
                """
func rtmf_swap(a, b int) (int, int) {
    return b, a
}
func rtmf_swapEnc(a, b int) int {
    x, y := rtmf_swap(a, b)
    return x*1000 + y
}""",
                "rtmf_swapEnc(%s, %s)",
                listOf(listOf("1", "2"), listOf("7", "3"), listOf("0", "0"), listOf("100", "1")),
            ),
            // ── Cross-function with accumulator ─────────────────────────
            RoundTripTestCase.withInputs("cross-accum",
                """
func rtmf_transform(x, factor int) int {
    return x*factor + 1
}
func rtmf_crossAccum(n, factor int) int {
    if n < 0 { n = -n }
    if n > 100 { n = 100 }
    acc := 0
    for i := 1; i <= n; i++ {
        acc = acc + rtmf_transform(i, factor)
    }
    return acc
}""",
                "rtmf_crossAccum(%s, %s)",
                listOf(listOf("5", "2"), listOf("0", "1"), listOf("10", "3"), listOf("1", "0")),
            ),
            // ── Multiple functions calling each other ────────────────────
            RoundTripTestCase.withInputs("mutual-calls",
                """
func rtmf_isEven(n int) int {
    if n < 0 { n = -n }
    if n == 0 { return 1 }
    return rtmf_isOdd(n - 1)
}
func rtmf_isOdd(n int) int {
    if n < 0 { n = -n }
    if n == 0 { return 0 }
    return rtmf_isEven(n - 1)
}
func rtmf_evenOddEnc(n int) int {
    return rtmf_isEven(n)*10 + rtmf_isOdd(n)
}""",
                "rtmf_evenOddEnc(%s)",
                listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10"), listOf("7")),
                randomRange = 0..50,
            ),
            // ── Power via helper ────────────────────────────────────────
            RoundTripTestCase.withInputs("power-helper",
                """
func rtmf_multiply(a, b int) int { return a * b }
func rtmf_power(base, exp int) int {
    if exp < 0 { exp = 0 }
    if exp > 15 { exp = 15 }
    result := 1
    for i := 0; i < exp; i++ {
        result = rtmf_multiply(result, base)
    }
    return result
}""",
                "rtmf_power(%s, %s)",
                listOf(listOf("2", "10"), listOf("3", "5"), listOf("1", "0"), listOf("5", "3")),
                randomRange = 0..10,
            ),
        )
        return BatchRoundTripRunner.runBatchAndCreateTests(cases, builder)
    }
}
