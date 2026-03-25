package org.opentaint.ir.go.test.roundtrip

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

/**
 * Multi-input stress tests: each function is tested with many predefined values
 * to catch edge cases. Replaces the old fuzz tests with deterministic coverage.
 */
@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripMultiInputTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase("stress-arith", """
func mi_compute(a, b int) int {
    sum := a + b; diff := a - b
    if sum > 0 { return sum * diff }; return diff
}""", """
    inputs := [][2]int{{0,0},{1,2},{-5,3},{100,-100},{-1,-1},{7,7},{0,1},{1,0},{-50,50},{999,1},{42,58},{13,-13},{256,256}}
    for _, p := range inputs { fmt.Println(mi_compute(p[0], p[1])) }
"""),
            RoundTripTestCase("stress-classify", """
func mi_classify(x int) int {
    if x > 100 { return 3 }; if x > 0 { return 2 }; if x == 0 { return 0 }; return -1
}""", """
    for _, x := range []int{0, 1, -1, 200, 100, 50, -50, -100, 101, 99, -999, 1000000, 2, -2} { fmt.Println(mi_classify(x)) }
"""),
            RoundTripTestCase("stress-loop", """
func mi_sumTo(n int) int {
    if n < 0 { n = -n }; if n > 10000 { n = 10000 }
    total := 0; for i := 0; i <= n; i++ { total += i }; return total
}""", """
    for _, x := range []int{0, 1, 5, 10, 100, -5, -10, 50, 99, 1000, 9999, 10000, 2, 3} { fmt.Println(mi_sumTo(x)) }
"""),
            RoundTripTestCase("stress-gcd", """
func mi_gcd(a, b int) int { for b != 0 { a, b = b, a%b }; return a }""", """
    pairs := [][2]int{{12,8},{100,75},{7,3},{1,1},{17,13},{100,10},{36,24},{15,25},{7,7},{1000,1}}
    for _, p := range pairs { fmt.Println(mi_gcd(p[0], p[1])) }
"""),
            RoundTripTestCase("stress-fib", """
func mi_fib(n int) int {
    if n <= 1 { return n }
    a, b := 0, 1; for i := 2; i <= n; i++ { a, b = b, a+b }; return b
}""", """
    for _, n := range []int{0, 1, 2, 3, 5, 8, 10, 15, 20, 25, 30} { fmt.Println(mi_fib(n)) }
"""),
            RoundTripTestCase("stress-power", """
func mi_pow(base, exp int) int {
    r := 1; for exp > 0 { if exp%2 == 1 { r *= base }; base *= base; exp /= 2 }; return r
}""", """
    pairs := [][2]int{{2,0},{2,1},{2,10},{3,5},{5,3},{7,2},{10,4},{1,100},{0,5},{2,20}}
    for _, p := range pairs { fmt.Println(mi_pow(p[0], p[1])) }
"""),
            RoundTripTestCase("stress-collatz", """
func mi_collatz(n int) int {
    s := 0; for n != 1 { if n%2 == 0 { n /= 2 } else { n = 3*n + 1 }; s++ }; return s
}""", """
    for _, n := range []int{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 27, 100} { fmt.Println(mi_collatz(n)) }
"""),
            RoundTripTestCase("stress-digit-sum", """
func mi_digitSum(n int) int {
    if n < 0 { n = -n }; s := 0; for n > 0 { s += n % 10; n /= 10 }; return s
}""", """
    for _, n := range []int{0, 1, 9, 10, 99, 123, 999, 9999, -42, -999, 100000} { fmt.Println(mi_digitSum(n)) }
"""),
            RoundTripTestCase("stress-prime-check", """
func mi_isPrime(n int) bool {
    if n < 2 { return false }
    for i := 2; i*i <= n; i++ { if n%i == 0 { return false } }; return true
}""", """
    for _, n := range []int{0, 1, 2, 3, 4, 5, 7, 9, 11, 13, 15, 17, 19, 23, 25, 29, 100, 101} { fmt.Println(mi_isPrime(n)) }
"""),
            RoundTripTestCase("stress-factorial", """
func mi_fact(n int) int { r := 1; for i := 2; i <= n; i++ { r *= i }; return r }""", """
    for _, n := range []int{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12} { fmt.Println(mi_fact(n)) }
"""),
            RoundTripTestCase("stress-abs-diff", """
func mi_absDiff(a, b int) int { d := a - b; if d < 0 { return -d }; return d }""", """
    pairs := [][2]int{{0,0},{1,0},{0,1},{5,3},{3,5},{-3,5},{100,100},{-10,-20}}
    for _, p := range pairs { fmt.Println(mi_absDiff(p[0], p[1])) }
"""),
            RoundTripTestCase("stress-clamp", """
func mi_clamp(x, lo, hi int) int {
    if x < lo { return lo }; if x > hi { return hi }; return x
}""", """
    triples := [][3]int{{5,0,10},{-5,0,10},{15,0,10},{0,0,10},{10,0,10},{5,5,5},{0,-10,10},{-20,-10,10}}
    for _, t := range triples { fmt.Println(mi_clamp(t[0], t[1], t[2])) }
"""),
            RoundTripTestCase("stress-max3", """
func mi_max3(a, b, c int) int {
    m := a; if b > m { m = b }; if c > m { m = c }; return m
}""", """
    triples := [][3]int{{1,2,3},{3,2,1},{2,3,1},{5,5,5},{-1,-2,-3},{0,0,0},{100,50,75}}
    for _, t := range triples { fmt.Println(mi_max3(t[0], t[1], t[2])) }
"""),
            RoundTripTestCase("stress-reverse-num", """
func mi_rev(n int) int { r := 0; for n > 0 { r = r*10 + n%10; n /= 10 }; return r }""", """
    for _, n := range []int{0, 1, 12, 123, 1000, 9876, 10001, 54321} { fmt.Println(mi_rev(n)) }
"""),
            RoundTripTestCase("stress-count-bits", """
func mi_popcount(n int) int {
    if n < 0 { n = -n }; c := 0; for n > 0 { c += n & 1; n >>= 1 }; return c
}""", """
    for _, n := range []int{0, 1, 2, 3, 7, 8, 15, 16, 255, 256, 1023, 1024} { fmt.Println(mi_popcount(n)) }
"""),
        )
        val result = BatchRoundTripRunner.runBatch(cases, builder)
        return cases.map { c -> DynamicTest.dynamicTest(c.name) {
            assertThat(result.reconstructedOutputs[c.name]).isEqualTo(result.originalOutputs[c.name])
        }}
    }
}
