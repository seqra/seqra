package org.opentaint.ir.go.test.roundtrip

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripRecursionTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase("rec-fib", "func rec_fib(n int) int { if n <= 1 { return n }; return rec_fib(n-1) + rec_fib(n-2) }",
                "fmt.Println(rec_fib(0)); fmt.Println(rec_fib(1)); fmt.Println(rec_fib(5)); fmt.Println(rec_fib(10))"),
            RoundTripTestCase("rec-fact", "func rec_fact(n int) int { if n <= 1 { return 1 }; return n * rec_fact(n-1) }",
                "fmt.Println(rec_fact(0)); fmt.Println(rec_fact(1)); fmt.Println(rec_fact(5)); fmt.Println(rec_fact(10))"),
            RoundTripTestCase("rec-sum", "func rec_sum(n int) int { if n <= 0 { return 0 }; return n + rec_sum(n-1) }",
                "fmt.Println(rec_sum(0)); fmt.Println(rec_sum(5)); fmt.Println(rec_sum(10))"),
            RoundTripTestCase("rec-pow", """
func rec_pow(base, exp int) int {
    if exp == 0 { return 1 }
    if exp%2 == 0 { half := rec_pow(base, exp/2); return half * half }
    return base * rec_pow(base, exp-1)
}""", "fmt.Println(rec_pow(2,10)); fmt.Println(rec_pow(3,5)); fmt.Println(rec_pow(5,0)); fmt.Println(rec_pow(2,0))"),
            RoundTripTestCase("rec-gcd", "func rec_gcd(a, b int) int { if b == 0 { return a }; return rec_gcd(b, a%b) }",
                "fmt.Println(rec_gcd(12,8)); fmt.Println(rec_gcd(100,75)); fmt.Println(rec_gcd(7,3))"),
            RoundTripTestCase("rec-count-digits", """
func rec_countDigits(n int) int {
    if n < 0 { n = -n }
    if n < 10 { return 1 }
    return 1 + rec_countDigits(n/10)
}""", "fmt.Println(rec_countDigits(0)); fmt.Println(rec_countDigits(5)); fmt.Println(rec_countDigits(123)); fmt.Println(rec_countDigits(-9999))"),
            RoundTripTestCase("rec-digit-sum", """
func rec_digitSum(n int) int {
    if n < 0 { n = -n }
    if n < 10 { return n }
    return n%10 + rec_digitSum(n/10)
}""", "fmt.Println(rec_digitSum(123)); fmt.Println(rec_digitSum(0)); fmt.Println(rec_digitSum(9999))"),
            RoundTripTestCase("rec-reverse", """
func rec_revHelper(n, acc int) int {
    if n == 0 { return acc }
    return rec_revHelper(n/10, acc*10+n%10)
}
func rec_reverse(n int) int { return rec_revHelper(n, 0) }""",
                "fmt.Println(rec_reverse(123)); fmt.Println(rec_reverse(1000)); fmt.Println(rec_reverse(0))"),
            RoundTripTestCase("rec-binary-search", """
func rec_binSearch(lo, hi, target int) int {
    if lo > hi { return -1 }
    mid := (lo + hi) / 2
    if mid == target { return mid }
    if mid > target { return rec_binSearch(lo, mid-1, target) }
    return rec_binSearch(mid+1, hi, target)
}""", "fmt.Println(rec_binSearch(0,100,42)); fmt.Println(rec_binSearch(0,100,0)); fmt.Println(rec_binSearch(0,100,100)); fmt.Println(rec_binSearch(0,100,101))"),
            RoundTripTestCase("rec-ackermann", """
func rec_ack(m, n int) int {
    if m == 0 { return n + 1 }
    if n == 0 { return rec_ack(m-1, 1) }
    return rec_ack(m-1, rec_ack(m, n-1))
}""", "fmt.Println(rec_ack(0,0)); fmt.Println(rec_ack(1,1)); fmt.Println(rec_ack(2,2)); fmt.Println(rec_ack(3,3))"),
            RoundTripTestCase("rec-catalan", """
func rec_catalan(n int) int {
    if n <= 1 { return 1 }
    result := 0
    for i := 0; i < n; i++ { result += rec_catalan(i) * rec_catalan(n-1-i) }
    return result
}""", "fmt.Println(rec_catalan(0)); fmt.Println(rec_catalan(1)); fmt.Println(rec_catalan(4)); fmt.Println(rec_catalan(5))"),
            RoundTripTestCase("rec-tribonacci", """
func rec_trib(n int) int {
    if n == 0 { return 0 }
    if n <= 2 { return 1 }
    return rec_trib(n-1) + rec_trib(n-2) + rec_trib(n-3)
}""", "fmt.Println(rec_trib(0)); fmt.Println(rec_trib(1)); fmt.Println(rec_trib(5)); fmt.Println(rec_trib(8))"),
            RoundTripTestCase("rec-max-depth", """
func rec_depth(n int) int {
    if n <= 1 { return 0 }
    return 1 + rec_depth(n/2)
}""", "fmt.Println(rec_depth(1)); fmt.Println(rec_depth(2)); fmt.Println(rec_depth(8)); fmt.Println(rec_depth(1024))"),
            RoundTripTestCase("rec-hanoi-count", """
func rec_hanoi(n int) int {
    if n == 0 { return 0 }
    return 2*rec_hanoi(n-1) + 1
}""", "fmt.Println(rec_hanoi(0)); fmt.Println(rec_hanoi(1)); fmt.Println(rec_hanoi(3)); fmt.Println(rec_hanoi(10))"),
            RoundTripTestCase("rec-pascal", """
func rec_pascal(row, col int) int {
    if col == 0 || col == row { return 1 }
    return rec_pascal(row-1, col-1) + rec_pascal(row-1, col)
}""", "fmt.Println(rec_pascal(4,2)); fmt.Println(rec_pascal(5,3)); fmt.Println(rec_pascal(6,0)); fmt.Println(rec_pascal(6,6))"),
            RoundTripTestCase("rec-collatz-depth", """
func rec_collatzD(n int) int {
    if n <= 1 { return 0 }
    if n%2 == 0 { return 1 + rec_collatzD(n/2) }
    return 1 + rec_collatzD(3*n+1)
}""", "fmt.Println(rec_collatzD(1)); fmt.Println(rec_collatzD(6)); fmt.Println(rec_collatzD(27))"),
            RoundTripTestCase("rec-max-of-pair", """
func rec_maxPair(a, b int) int { if a > b { return a }; return b }
func rec_maxThree(a, b, c int) int { return rec_maxPair(a, rec_maxPair(b, c)) }""",
                "fmt.Println(rec_maxThree(1,2,3)); fmt.Println(rec_maxThree(3,2,1)); fmt.Println(rec_maxThree(5,5,5))"),
            RoundTripTestCase("rec-multiply", """
func rec_mul(a, b int) int {
    if b == 0 { return 0 }
    if b < 0 { return -rec_mul(a, -b) }
    return a + rec_mul(a, b-1)
}""", "fmt.Println(rec_mul(3,4)); fmt.Println(rec_mul(0,5)); fmt.Println(rec_mul(7,-3))"),
            RoundTripTestCase("rec-sum-even", """
func rec_sumEven(n int) int {
    if n <= 0 { return 0 }
    if n%2 == 0 { return n + rec_sumEven(n-2) }
    return rec_sumEven(n-1)
}""", "fmt.Println(rec_sumEven(10)); fmt.Println(rec_sumEven(0)); fmt.Println(rec_sumEven(1)); fmt.Println(rec_sumEven(7))"),
            RoundTripTestCase("rec-nested-calls", """
func rec_double(x int) int { return x * 2 }
func rec_inc(x int) int { return x + 1 }
func rec_apply(n, x int) int {
    if n <= 0 { return x }
    return rec_apply(n-1, rec_double(rec_inc(x)))
}""", "fmt.Println(rec_apply(0,1)); fmt.Println(rec_apply(1,1)); fmt.Println(rec_apply(3,1)); fmt.Println(rec_apply(5,0))"),
        )
        val result = BatchRoundTripRunner.runBatch(cases, builder)
        return cases.map { c -> DynamicTest.dynamicTest(c.name) {
            assertThat(result.reconstructedOutputs[c.name]).isEqualTo(result.originalOutputs[c.name])
        }}
    }
}
