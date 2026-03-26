package org.opentaint.ir.go.test.roundtrip

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripFunctionTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            // ── Nested function calls (f calling g calling h) ──────────────

            // 1. Three-level nesting: add -> multiply -> combine
            RoundTripTestCase.withInputs("nested-add-mul-combine", """
func rtf_add(a, b int) int { return a + b }
func rtf_mul(a, b int) int { return a * b }
func rtf_combine(a, b int) int { return rtf_add(rtf_mul(a, b), rtf_mul(a+1, b+1)) }""",
                "rtf_combine(%s, %s)",
                listOf(listOf("3","4"), listOf("0","5"), listOf("-2","3"), listOf("10","10"), listOf("7","-1")),
            ),

            // 2. Four-level composition: square -> double -> inc -> compose
            RoundTripTestCase.withInputs("nested-compose-4", """
func rtf_sq(x int) int { return x * x }
func rtf_dbl(x int) int { return x * 2 }
func rtf_inc(x int) int { return x + 1 }
func rtf_compose4(x int) int { return rtf_inc(rtf_dbl(rtf_sq(x))) }""",
                "rtf_compose4(%s)",
                listOf(listOf("3"), listOf("0"), listOf("-4"), listOf("7"), listOf("10")),
            ),

            // 3. Nested abs-diff used in three-level diff
            RoundTripTestCase.withInputs("nested-abs-diff", """
func rtf_abs(x int) int { if x < 0 { return -x }; return x }
func rtf_absDiff(a, b int) int { return rtf_abs(a - b) }
func rtf_triDiff(a, b, c int) int { return rtf_absDiff(rtf_absDiff(a, b), c) }""",
                "rtf_triDiff(%s, %s, %s)",
                listOf(listOf("10","3","2"), listOf("1","1","0"), listOf("-5","5","10"), listOf("100","50","25"), listOf("0","0","0")),
            ),

            // 4. Nested min/max composition for clamping
            RoundTripTestCase.withInputs("nested-min-max", """
func rtf_min(a, b int) int { if a < b { return a }; return b }
func rtf_max(a, b int) int { if a > b { return a }; return b }
func rtf_clamp(x, lo, hi int) int { return rtf_max(lo, rtf_min(x, hi)) }""",
                "rtf_clamp(%s, %s, %s)",
                listOf(listOf("5","0","10"), listOf("-3","0","10"), listOf("15","0","10"), listOf("50","20","30"), listOf("0","0","0")),
            ),

            // 5. Nested normalize -> scale -> offset
            RoundTripTestCase.withInputs("nested-transform", """
func rtf_norm(x int) int { if x < 0 { return -x }; return x }
func rtf_scale(x, factor int) int { return rtf_norm(x) * factor }
func rtf_offset(x, factor, off int) int { return rtf_scale(x, factor) + off }""",
                "rtf_offset(%s, %s, %s)",
                listOf(listOf("-7","3","10"), listOf("5","2","0"), listOf("0","10","100"), listOf("-100","1","-50"), listOf("42","0","7")),
            ),

            // 6. Nested conditional dispatch through helper functions
            RoundTripTestCase.withInputs("nested-dispatch", """
func rtf_opAdd(a, b int) int { return a + b }
func rtf_opSub(a, b int) int { return a - b }
func rtf_opMul(a, b int) int { return a * b }
func rtf_dispatch(op, a, b int) int {
    if op%3 == 0 { return rtf_opAdd(a, b) }
    if op%3 == 1 { return rtf_opSub(a, b) }
    return rtf_opMul(a, b)
}""",
                "rtf_dispatch(%s, %s, %s)",
                listOf(listOf("0","10","5"), listOf("1","10","5"), listOf("2","10","5"), listOf("3","7","3"), listOf("5","-2","4")),
            ),

            // ── Recursive functions (all bounded) ──────────────────────────

            // 7. Recursive GCD
            RoundTripTestCase.withInputs("rec-gcd", """
func rtf_gcd(a, b int) int {
    if a < 0 { a = -a }
    if b < 0 { b = -b }
    if b == 0 { return a }
    return rtf_gcd(b, a%b)
}""",
                "rtf_gcd(%s, %s)",
                listOf(listOf("12","8"), listOf("100","75"), listOf("7","3"), listOf("0","5"), listOf("36","24")),
                randomRange = 1..100,
            ),

            // 8. Recursive Fibonacci (bounded to n<=25)
            RoundTripTestCase.withInputs("rec-fibonacci", """
func rtf_fib(n int) int {
    if n < 0 { n = -n }
    if n > 25 { n = 25 }
    if n <= 1 { return n }
    return rtf_fib(n-1) + rtf_fib(n-2)
}""",
                "rtf_fib(%s)",
                listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10"), listOf("15")),
            ),

            // 9. Recursive fast power (bounded exponent)
            RoundTripTestCase.withInputs("rec-power", """
func rtf_pow(base, exp int) int {
    if exp < 0 { exp = -exp }
    if exp > 20 { exp = 20 }
    if exp == 0 { return 1 }
    if exp%2 == 0 {
        half := rtf_pow(base, exp/2)
        return half * half
    }
    return base * rtf_pow(base, exp-1)
}""",
                "rtf_pow(%s, %s)",
                listOf(listOf("2","10"), listOf("3","5"), listOf("5","0"), listOf("-2","3"), listOf("1","20")),
            ),

            // 10. Recursive factorial (bounded to n<=20)
            RoundTripTestCase.withInputs("rec-factorial", """
func rtf_fact(n int) int {
    if n < 0 { n = -n }
    if n > 20 { n = 20 }
    if n <= 1 { return 1 }
    return n * rtf_fact(n-1)
}""",
                "rtf_fact(%s)",
                listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10"), listOf("12")),
            ),

            // 11. Recursive digit sum
            RoundTripTestCase.withInputs("rec-digit-sum", """
func rtf_digitSum(n int) int {
    if n < 0 { n = -n }
    if n < 10 { return n }
    return n%10 + rtf_digitSum(n/10)
}""",
                "rtf_digitSum(%s)",
                listOf(listOf("123"), listOf("0"), listOf("9999"), listOf("5"), listOf("-456")),
            ),

            // 12. Recursive binary length (bit count)
            RoundTripTestCase.withInputs("rec-bit-length", """
func rtf_bitLen(n int) int {
    if n < 0 { n = -n }
    if n <= 1 { return 1 }
    return 1 + rtf_bitLen(n/2)
}""",
                "rtf_bitLen(%s)",
                listOf(listOf("1"), listOf("2"), listOf("255"), listOf("1024"), listOf("0")),
            ),

            // ── Functions with 3-5 parameters ──────────────────────────────

            // 13. Median of three
            RoundTripTestCase.withInputs("params-median-3", """
func rtf_median3(a, b, c int) int {
    if a > b {
        if b > c { return b }
        if a > c { return c }
        return a
    }
    if a > c { return a }
    if b > c { return c }
    return b
}""",
                "rtf_median3(%s, %s, %s)",
                listOf(listOf("1","2","3"), listOf("3","1","2"), listOf("5","5","5"), listOf("-1","0","1"), listOf("100","50","75")),
            ),

            // 14. Weighted sum of 4 parameters
            RoundTripTestCase.withInputs("params-weighted-4", """
func rtf_weighted4(a, b, c, d int) int {
    return a*1 + b*2 + c*3 + d*4
}""",
                "rtf_weighted4(%s, %s, %s, %s)",
                listOf(listOf("1","2","3","4"), listOf("10","0","0","0"), listOf("-1","-2","-3","-4"), listOf("5","5","5","5"), listOf("0","0","0","1")),
            ),

            // 15. Clamp sum of 5 parameters to [-1000, 1000]
            RoundTripTestCase.withInputs("params-clamp-5", """
func rtf_clampSum5(a, b, c, d, e int) int {
    s := a + b + c + d + e
    if s < -1000 { s = -1000 }
    if s > 1000 { s = 1000 }
    return s
}""",
                "rtf_clampSum5(%s, %s, %s, %s, %s)",
                listOf(listOf("100","200","300","400","500"), listOf("-100","-200","-300","-400","-500"), listOf("1","2","3","4","5"), listOf("0","0","0","0","0"), listOf("-999","0","0","0","0")),
            ),

            // 16. Sort three values and encode as a*10000 + b*100 + c
            RoundTripTestCase.withInputs("params-sort-3", """
func rtf_sort3(a, b, c int) int {
    if a > b { a, b = b, a }
    if b > c { b, c = c, b }
    if a > b { a, b = b, a }
    return a*10000 + b*100 + c
}""",
                "rtf_sort3(%s, %s, %s)",
                listOf(listOf("3","1","2"), listOf("5","5","5"), listOf("10","20","30"), listOf("-1","0","1"), listOf("99","1","50")),
            ),

            // 17. Min/max range of 4 values
            RoundTripTestCase.withInputs("params-range-4", """
func rtf_range4(a, b, c, d int) int {
    mn := a
    mx := a
    if b < mn { mn = b }
    if c < mn { mn = c }
    if d < mn { mn = d }
    if b > mx { mx = b }
    if c > mx { mx = c }
    if d > mx { mx = d }
    return mx - mn
}""",
                "rtf_range4(%s, %s, %s, %s)",
                listOf(listOf("1","2","3","4"), listOf("10","10","10","10"), listOf("-5","5","-10","10"), listOf("100","1","50","75"), listOf("0","0","0","0")),
            ),

            // 18. Dot-product-like with 5 params: a*b + c*d + e
            RoundTripTestCase.withInputs("params-dot-5", """
func rtf_dot5(a, b, c, d, e int) int {
    return a*b + c*d + e
}""",
                "rtf_dot5(%s, %s, %s, %s, %s)",
                listOf(listOf("10","20","30","40","50"), listOf("1","1","1","1","1"), listOf("0","0","0","0","0"), listOf("-5","5","-5","5","-5"), listOf("3","7","2","8","1")),
            ),

            // ── Complex if/else chains ──────────────────────────────────────

            // 19. Grade classification with many thresholds
            RoundTripTestCase.withInputs("ifelse-grade", """
func rtf_grade(score int) int {
    if score < 0 { score = -score }
    if score >= 90 { return 5 }
    if score >= 80 { return 4 }
    if score >= 70 { return 3 }
    if score >= 60 { return 2 }
    if score >= 50 { return 1 }
    return 0
}""",
                "rtf_grade(%s)",
                listOf(listOf("95"), listOf("85"), listOf("72"), listOf("55"), listOf("30")),
            ),

            // 20. Quadrant classification from two values
            RoundTripTestCase.withInputs("ifelse-classify", """
func rtf_classify(a, b int) int {
    if a > 0 && b > 0 { return 1 }
    if a > 0 && b < 0 { return 2 }
    if a < 0 && b > 0 { return 3 }
    if a < 0 && b < 0 { return 4 }
    if a == 0 && b == 0 { return 0 }
    if a == 0 { return 5 }
    return 6
}""",
                "rtf_classify(%s, %s)",
                listOf(listOf("1","1"), listOf("1","-1"), listOf("-1","1"), listOf("-1","-1"), listOf("0","0")),
            ),

            // 21. Range bucketing with nested sub-ranges
            RoundTripTestCase.withInputs("ifelse-bucket", """
func rtf_bucket(x int) int {
    if x < 0 { x = -x }
    if x > 1000 { return 10 }
    if x > 500 { return 9 }
    if x > 200 { return 8 }
    if x > 100 {
        if x > 150 { return 7 }
        return 6
    }
    if x > 50 {
        if x > 75 { return 5 }
        return 4
    }
    if x > 20 { return 3 }
    if x > 10 { return 2 }
    if x > 0 { return 1 }
    return 0
}""",
                "rtf_bucket(%s)",
                listOf(listOf("0"), listOf("5"), listOf("25"), listOf("60"), listOf("500")),
            ),

            // 22. Deeply nested three-param if/else chain
            RoundTripTestCase.withInputs("ifelse-nested-chain", """
func rtf_nestedChain(a, b, c int) int {
    if a > b {
        if b > c {
            return a + b + c
        } else if a > c {
            return a*2 + c
        } else {
            return c * 3
        }
    } else {
        if a > c {
            return a + b*2
        } else if b > c {
            return b*2 + a
        } else {
            return c - a - b
        }
    }
}""",
                "rtf_nestedChain(%s, %s, %s)",
                listOf(listOf("10","5","3"), listOf("3","10","5"), listOf("3","5","10"), listOf("5","5","5"), listOf("1","2","3")),
            ),

            // ── For loops + accumulators ────────────────────────────────────

            // 23. Sum skipping multiples of 3
            RoundTripTestCase.withInputs("loop-accum-skip", """
func rtf_accumSkip(n int) int {
    if n < 0 { n = -n }
    if n > 1000 { n = 1000 }
    s := 0
    for i := 1; i <= n; i++ {
        if i%3 == 0 { continue }
        s += i
    }
    return s
}""",
                "rtf_accumSkip(%s)",
                listOf(listOf("10"), listOf("100"), listOf("1"), listOf("0"), listOf("50")),
            ),

            // 24. Alternating sign accumulator
            RoundTripTestCase.withInputs("loop-accum-alt", """
func rtf_accumAlt(n int) int {
    if n < 0 { n = -n }
    if n > 1000 { n = 1000 }
    s := 0
    for i := 1; i <= n; i++ {
        if i%2 == 0 {
            s += i
        } else {
            s -= i
        }
    }
    return s
}""",
                "rtf_accumAlt(%s)",
                listOf(listOf("10"), listOf("7"), listOf("100"), listOf("1"), listOf("0")),
            ),

            // 25. Running max from deterministic pseudo-random sequence
            RoundTripTestCase.withInputs("loop-running-max", """
func rtf_runMax(n, seed int) int {
    if n < 0 { n = -n }
    if n > 1000 { n = 1000 }
    mx := 0
    v := seed
    for i := 0; i < n; i++ {
        v = (v*37 + 13) % 1000
        if v > mx { mx = v }
    }
    return mx
}""",
                "rtf_runMax(%s, %s)",
                listOf(listOf("10","7"), listOf("100","42"), listOf("50","1"), listOf("1","99"), listOf("0","0")),
            ),

            // 26. Bounded product with modular reduction
            RoundTripTestCase.withInputs("loop-accum-product", """
func rtf_accumProd(n int) int {
    if n < 0 { n = -n }
    if n > 1000 { n = 1000 }
    p := 1
    for i := 1; i <= n; i++ {
        p = (p * (i%10 + 1)) % 1000000
    }
    return p
}""",
                "rtf_accumProd(%s)",
                listOf(listOf("5"), listOf("10"), listOf("50"), listOf("1"), listOf("0")),
            ),

            // ── Sequence computation ────────────────────────────────────────

            // 27. Sum 1..n
            RoundTripTestCase.withInputs("seq-sum-n", """
func rtf_sumN(n int) int {
    if n < 0 { n = -n }
    if n > 1000 { n = 1000 }
    s := 0
    for i := 1; i <= n; i++ {
        s += i
    }
    return s
}""",
                "rtf_sumN(%s)",
                listOf(listOf("10"), listOf("100"), listOf("0"), listOf("1"), listOf("500")),
            ),

            // 28. Sum of squares 1..n
            RoundTripTestCase.withInputs("seq-sum-squares", """
func rtf_sumSq(n int) int {
    if n < 0 { n = -n }
    if n > 1000 { n = 1000 }
    s := 0
    for i := 1; i <= n; i++ {
        s += i * i
    }
    return s
}""",
                "rtf_sumSq(%s)",
                listOf(listOf("5"), listOf("10"), listOf("100"), listOf("0"), listOf("1")),
            ),

            // 29. Sum of cubes 1..n
            RoundTripTestCase.withInputs("seq-sum-cubes", """
func rtf_sumCubes(n int) int {
    if n < 0 { n = -n }
    if n > 1000 { n = 1000 }
    s := 0
    for i := 1; i <= n; i++ {
        s += i * i * i
    }
    return s
}""",
                "rtf_sumCubes(%s)",
                listOf(listOf("5"), listOf("10"), listOf("50"), listOf("1"), listOf("0")),
            ),

            // 30. Sum of odd numbers 1..n
            RoundTripTestCase.withInputs("seq-sum-odds", """
func rtf_sumOdds(n int) int {
    if n < 0 { n = -n }
    if n > 1000 { n = 1000 }
    s := 0
    for i := 1; i <= n; i += 2 {
        s += i
    }
    return s
}""",
                "rtf_sumOdds(%s)",
                listOf(listOf("10"), listOf("7"), listOf("100"), listOf("1"), listOf("0")),
            ),

            // ── Multiple exit points (early returns) ────────────────────────

            // 31. Multiple early returns based on input magnitude
            RoundTripTestCase.withInputs("early-return-ranges", """
func rtf_earlyRanges(n int) int {
    if n < 0 { return -1 }
    if n == 0 { return 0 }
    if n == 1 { return 1 }
    if n < 10 { return n * 2 }
    if n < 100 { return n + 50 }
    if n < 1000 { return n / 10 }
    return 999
}""",
                "rtf_earlyRanges(%s)",
                listOf(listOf("-5"), listOf("0"), listOf("1"), listOf("7"), listOf("50")),
            ),

            // 32. Validation-style with multiple reject points
            RoundTripTestCase.withInputs("early-return-validate", """
func rtf_validate(a, b int) int {
    if a == 0 { return -1 }
    if b == 0 { return -2 }
    if a < 0 && b < 0 { return -3 }
    if a > 1000 || b > 1000 { return -4 }
    r := a + b
    if r == 0 { return -5 }
    if r > 500 { return r - 500 }
    return r
}""",
                "rtf_validate(%s, %s)",
                listOf(listOf("0","5"), listOf("5","0"), listOf("-1","-1"), listOf("1001","5"), listOf("100","200")),
            ),

            // 33. Search with early return when accumulated sum hits target
            RoundTripTestCase.withInputs("early-return-search", """
func rtf_earlySearch(n, target int) int {
    if n < 0 { n = -n }
    if n > 1000 { n = 1000 }
    if target <= 0 { return -1 }
    s := 0
    for i := 1; i <= n; i++ {
        s += i
        if s >= target { return i }
    }
    return -1
}""",
                "rtf_earlySearch(%s, %s)",
                listOf(listOf("100","50"), listOf("10","100"), listOf("50","1"), listOf("0","5"), listOf("1000","5000")),
            ),

            // ── Search functions ────────────────────────────────────────────

            // 34. Find first (smallest) divisor > 1
            RoundTripTestCase.withInputs("search-first-divisor", """
func rtf_firstDiv(n int) int {
    if n < 0 { n = -n }
    if n < 2 { return 0 }
    if n > 10000 { n = 10000 }
    for i := 2; i*i <= n; i++ {
        if n%i == 0 { return i }
    }
    return n
}""",
                "rtf_firstDiv(%s)",
                listOf(listOf("12"), listOf("7"), listOf("100"), listOf("1"), listOf("49")),
                randomRange = 1..100,
            ),

            // 35. Find largest power of 2 that is <= n
            RoundTripTestCase.withInputs("search-power-of-2", """
func rtf_largestPow2(n int) int {
    if n < 0 { n = -n }
    if n == 0 { return 0 }
    if n > 1000000 { n = 1000000 }
    p := 1
    for p*2 <= n {
        p *= 2
    }
    return p
}""",
                "rtf_largestPow2(%s)",
                listOf(listOf("1"), listOf("16"), listOf("100"), listOf("1023"), listOf("0")),
            ),

            // 36. Find first triangular number index >= target
            RoundTripTestCase.withInputs("search-triangular", """
func rtf_searchTri(target int) int {
    if target < 0 { target = -target }
    if target > 1000000 { target = 1000000 }
    if target == 0 { return 0 }
    s := 0
    for i := 1; i <= 2000; i++ {
        s += i
        if s >= target { return i }
    }
    return -1
}""",
                "rtf_searchTri(%s)",
                listOf(listOf("1"), listOf("10"), listOf("100"), listOf("0"), listOf("55")),
            ),

            // ── Nested loops ────────────────────────────────────────────────

            // 37. Count pairs (i,j) where i+j divides n
            RoundTripTestCase.withInputs("nested-loop-pair-count", """
func rtf_pairCount(n int) int {
    if n < 0 { n = -n }
    if n == 0 { return 0 }
    if n > 100 { n = 100 }
    count := 0
    for i := 1; i <= n; i++ {
        for j := i; j <= n; j++ {
            s := i + j
            if s > 0 && n%s == 0 {
                count++
            }
        }
    }
    return count
}""",
                "rtf_pairCount(%s)",
                listOf(listOf("10"), listOf("12"), listOf("1"), listOf("50"), listOf("7")),
                randomRange = 1..100,
            ),

            // 38. Matrix-like row*col accumulation
            RoundTripTestCase.withInputs("nested-loop-matrix-sum", """
func rtf_matSum(n, m int) int {
    if n < 0 { n = -n }
    if m < 0 { m = -m }
    if n > 50 { n = 50 }
    if m > 50 { m = 50 }
    s := 0
    for i := 1; i <= n; i++ {
        for j := 1; j <= m; j++ {
            s += i * j
        }
    }
    return s
}""",
                "rtf_matSum(%s, %s)",
                listOf(listOf("5","5"), listOf("10","3"), listOf("1","1"), listOf("0","10"), listOf("20","20")),
            ),

            // ── Digit manipulation ──────────────────────────────────────────

            // 39. Reverse the digits of a number
            RoundTripTestCase.withInputs("digit-reverse", """
func rtf_reverseDigits(n int) int {
    neg := 0
    if n < 0 { n = -n; neg = 1 }
    rev := 0
    for n > 0 {
        rev = rev*10 + n%10
        n /= 10
    }
    if neg == 1 { return -rev }
    return rev
}""",
                "rtf_reverseDigits(%s)",
                listOf(listOf("123"), listOf("1000"), listOf("0"), listOf("-456"), listOf("12321")),
            ),

            // 40. Count occurrences of a specific digit in a number
            RoundTripTestCase.withInputs("digit-count-occur", """
func rtf_countDigit(n, d int) int {
    if n < 0 { n = -n }
    if d < 0 { d = -d }
    if d > 9 { d = d % 10 }
    if n == 0 {
        if d == 0 { return 1 }
        return 0
    }
    count := 0
    for n > 0 {
        if n%10 == d { count++ }
        n /= 10
    }
    return count
}""",
                "rtf_countDigit(%s, %s)",
                listOf(listOf("11211","1"), listOf("123","4"), listOf("0","0"), listOf("99999","9"), listOf("12345","3")),
                randomRange = 1..100,
            ),
        )
        return BatchRoundTripRunner.runBatchAndCreateTests(cases, builder)
    }
}
