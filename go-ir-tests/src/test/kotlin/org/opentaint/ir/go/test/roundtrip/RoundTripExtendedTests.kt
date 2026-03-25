package org.opentaint.ir.go.test.roundtrip

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

/** Extended round-trip tests, converted to batched format. */
@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripExtendedTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase("bitwise", """
func ex_bitAnd(a, b int) int { return a & b }
func ex_bitOr(a, b int) int { return a | b }
func ex_bitXor(a, b int) int { return a ^ b }
func ex_bitAndNot(a, b int) int { return a &^ b }""",
                "fmt.Println(ex_bitAnd(0xFF,0x0F)); fmt.Println(ex_bitOr(0xFF,0x0F)); fmt.Println(ex_bitXor(0xFF,0x0F)); fmt.Println(ex_bitAndNot(0xFF,0x0F)); fmt.Println(ex_bitAnd(12,10)); fmt.Println(ex_bitOr(12,10))"),
            RoundTripTestCase("strings", """
func ex_greet(name string) string { return "Hello, " + name + "!" }""",
                """fmt.Println(ex_greet("World")); fmt.Println(ex_greet("Go")); fmt.Println(ex_greet(""))"""),
            RoundTripTestCase("nested-loops", """
func ex_mulTable(n int) int {
    total := 0
    for i := 1; i <= n; i++ { for j := 1; j <= n; j++ { total += i * j } }
    return total
}""", "fmt.Println(ex_mulTable(3)); fmt.Println(ex_mulTable(5)); fmt.Println(ex_mulTable(1))"),
            RoundTripTestCase("countdown", """
func ex_countdown(n int) int { count := 0; for n > 0 { count += n; n-- }; return count }""",
                "fmt.Println(ex_countdown(5)); fmt.Println(ex_countdown(10)); fmt.Println(ex_countdown(0))"),
            RoundTripTestCase("recursion", """
func ex_fib(n int) int { if n <= 1 { return n }; return ex_fib(n-1) + ex_fib(n-2) }""",
                "fmt.Println(ex_fib(0)); fmt.Println(ex_fib(1)); fmt.Println(ex_fib(5)); fmt.Println(ex_fib(10))"),
            RoundTripTestCase("switch-if", """
func ex_dayType(day int) int {
    if day == 0 || day == 6 { return 0 }
    if day >= 1 && day <= 5 { return 1 }
    return -1
}""", "for i := 0; i < 8; i++ { fmt.Println(ex_dayType(i)) }"),
            RoundTripTestCase("early-return", """
func ex_safeDivide(a, b int) int {
    if b == 0 { return 0 }
    if a < 0 { a = -a }
    return a / b
}""", "fmt.Println(ex_safeDivide(10,3)); fmt.Println(ex_safeDivide(10,0)); fmt.Println(ex_safeDivide(-10,3)); fmt.Println(ex_safeDivide(-15,-5))"),
            RoundTripTestCase("gcd", """
func ex_gcd(a, b int) int { for b != 0 { a, b = b, a%b }; return a }""",
                "fmt.Println(ex_gcd(12,8)); fmt.Println(ex_gcd(100,75)); fmt.Println(ex_gcd(7,3)); fmt.Println(ex_gcd(1,1))"),
            RoundTripTestCase("power", """
func ex_power(base, exp int) int {
    result := 1
    for exp > 0 { if exp%2 == 1 { result *= base }; base *= base; exp /= 2 }
    return result
}""", "fmt.Println(ex_power(2,10)); fmt.Println(ex_power(3,5)); fmt.Println(ex_power(5,0)); fmt.Println(ex_power(7,1))"),
            RoundTripTestCase("clamp", """
func ex_clamp(x, lo, hi int) int {
    if x < lo { return lo }; if x > hi { return hi }; return x
}""", "fmt.Println(ex_clamp(5,0,10)); fmt.Println(ex_clamp(-5,0,10)); fmt.Println(ex_clamp(15,0,10)); fmt.Println(ex_clamp(0,0,10)); fmt.Println(ex_clamp(10,0,10))"),
            RoundTripTestCase("collatz", """
func ex_collatzSteps(n int) int {
    steps := 0
    for n != 1 { if n%2 == 0 { n = n / 2 } else { n = 3*n + 1 }; steps++ }
    return steps
}""", "fmt.Println(ex_collatzSteps(1)); fmt.Println(ex_collatzSteps(6)); fmt.Println(ex_collatzSteps(27))"),
            RoundTripTestCase("digit-sum", """
func ex_digitSum(n int) int {
    if n < 0 { n = -n }; sum := 0; for n > 0 { sum += n % 10; n /= 10 }; return sum
}""", "fmt.Println(ex_digitSum(123)); fmt.Println(ex_digitSum(9999)); fmt.Println(ex_digitSum(0)); fmt.Println(ex_digitSum(-456))"),
            RoundTripTestCase("is-prime", """
func ex_isPrime(n int) bool {
    if n < 2 { return false }
    for i := 2; i*i <= n; i++ { if n%i == 0 { return false } }
    return true
}""", "for i := 0; i < 20; i++ { if ex_isPrime(i) { fmt.Println(i) } }"),
        )
        val result = BatchRoundTripRunner.runBatch(cases, builder)
        return cases.map { c -> DynamicTest.dynamicTest(c.name) {
            assertThat(result.reconstructedOutputs[c.name]).isEqualTo(result.originalOutputs[c.name])
        }}
    }
}
