package org.opentaint.ir.go.test.roundtrip

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripLoopTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase("sum-to", "func lp_sumTo(n int) int { s := 0; for i := 1; i <= n; i++ { s += i }; return s }",
                "fmt.Println(lp_sumTo(10)); fmt.Println(lp_sumTo(100)); fmt.Println(lp_sumTo(0))"),
            RoundTripTestCase("factorial", "func lp_fact(n int) int { r := 1; for i := 2; i <= n; i++ { r *= i }; return r }",
                "fmt.Println(lp_fact(5)); fmt.Println(lp_fact(1)); fmt.Println(lp_fact(10))"),
            RoundTripTestCase("while", "func lp_countDown(n int) int { c := 0; for n > 0 { c++; n-- }; return c }",
                "fmt.Println(lp_countDown(5)); fmt.Println(lp_countDown(0)); fmt.Println(lp_countDown(10))"),
            RoundTripTestCase("nested", """
func lp_grid(n int) int {
    s := 0
    for i := 0; i < n; i++ { for j := 0; j < n; j++ { s += i*n + j } }
    return s
}""", "fmt.Println(lp_grid(3)); fmt.Println(lp_grid(4)); fmt.Println(lp_grid(1))"),
            RoundTripTestCase("first-div", """
func lp_firstDivBy(n, d int) int {
    for i := 1; i <= n; i++ { if i%d == 0 { return i } }
    return -1
}""", "fmt.Println(lp_firstDivBy(10,3)); fmt.Println(lp_firstDivBy(10,7)); fmt.Println(lp_firstDivBy(20,6))"),
            RoundTripTestCase("sum-odds", """
func lp_sumOdds(n int) int {
    s := 0
    for i := 1; i <= n; i++ { if i%2 == 0 { continue }; s += i }
    return s
}""", "fmt.Println(lp_sumOdds(10)); fmt.Println(lp_sumOdds(1)); fmt.Println(lp_sumOdds(7))"),
            RoundTripTestCase("rev-sum", "func lp_revSum(n int) int { s := 0; for i := n; i > 0; i-- { s += i }; return s }",
                "fmt.Println(lp_revSum(5)); fmt.Println(lp_revSum(10)); fmt.Println(lp_revSum(1))"),
            RoundTripTestCase("mul-table", """
func lp_mulSum(n int) int {
    s := 0
    for i := 1; i <= n; i++ { for j := 1; j <= n; j++ { s += i * j } }
    return s
}""", "fmt.Println(lp_mulSum(3)); fmt.Println(lp_mulSum(5)); fmt.Println(lp_mulSum(1))"),
            RoundTripTestCase("gcd", "func lp_gcd(a, b int) int { for b != 0 { a, b = b, a%b }; return a }",
                "fmt.Println(lp_gcd(12,8)); fmt.Println(lp_gcd(100,75)); fmt.Println(lp_gcd(7,3)); fmt.Println(lp_gcd(17,13))"),
            RoundTripTestCase("lcm", """
func lp_gcd2(a, b int) int { for b != 0 { a, b = b, a%b }; return a }
func lp_lcm(a, b int) int { return a / lp_gcd2(a, b) * b }""",
                "fmt.Println(lp_lcm(4,6)); fmt.Println(lp_lcm(12,8)); fmt.Println(lp_lcm(7,3))"),
            RoundTripTestCase("bin-to-dec", """
func lp_binToDec(n int) int {
    result := 0; base := 1
    for n > 0 { result += (n % 10) * base; n /= 10; base *= 2 }
    return result
}""", "fmt.Println(lp_binToDec(1010)); fmt.Println(lp_binToDec(1111)); fmt.Println(lp_binToDec(100)); fmt.Println(lp_binToDec(0))"),
            RoundTripTestCase("num-digits", """
func lp_numDigits(n int) int {
    if n == 0 { return 1 }
    if n < 0 { n = -n }
    c := 0; for n > 0 { c++; n /= 10 }; return c
}""", "fmt.Println(lp_numDigits(0)); fmt.Println(lp_numDigits(5)); fmt.Println(lp_numDigits(99)); fmt.Println(lp_numDigits(12345)); fmt.Println(lp_numDigits(-42))"),
            RoundTripTestCase("reverse", """
func lp_rev(n int) int {
    r := 0; for n > 0 { r = r*10 + n%10; n /= 10 }; return r
}""", "fmt.Println(lp_rev(123)); fmt.Println(lp_rev(1000)); fmt.Println(lp_rev(9876)); fmt.Println(lp_rev(0))"),
            RoundTripTestCase("digit-sum", """
func lp_digitSum(n int) int {
    if n < 0 { n = -n }; s := 0; for n > 0 { s += n % 10; n /= 10 }; return s
}""", "fmt.Println(lp_digitSum(123)); fmt.Println(lp_digitSum(9999)); fmt.Println(lp_digitSum(0)); fmt.Println(lp_digitSum(-456))"),
            RoundTripTestCase("collatz", """
func lp_collatz(n int) int {
    s := 0; for n != 1 { if n%2 == 0 { n /= 2 } else { n = 3*n + 1 }; s++ }; return s
}""", "fmt.Println(lp_collatz(1)); fmt.Println(lp_collatz(6)); fmt.Println(lp_collatz(27))"),
            RoundTripTestCase("power", """
func lp_pow(base, exp int) int {
    r := 1; for exp > 0 { if exp%2 == 1 { r *= base }; base *= base; exp /= 2 }; return r
}""", "fmt.Println(lp_pow(2,10)); fmt.Println(lp_pow(3,5)); fmt.Println(lp_pow(5,0)); fmt.Println(lp_pow(7,1))"),
            RoundTripTestCase("sum-evens", "func lp_sumEvens(n int) int { s := 0; for i := 2; i <= n; i += 2 { s += i }; return s }",
                "fmt.Println(lp_sumEvens(10)); fmt.Println(lp_sumEvens(1)); fmt.Println(lp_sumEvens(20))"),
            RoundTripTestCase("isqrt", """
func lp_isqrt(n int) int { r := 0; for (r+1)*(r+1) <= n { r++ }; return r }""",
                "fmt.Println(lp_isqrt(0)); fmt.Println(lp_isqrt(1)); fmt.Println(lp_isqrt(4)); fmt.Println(lp_isqrt(15)); fmt.Println(lp_isqrt(16)); fmt.Println(lp_isqrt(100))"),
            RoundTripTestCase("harmonic", """
func lp_harmonic(n int) int { s := 0; for i := 1; i <= n; i++ { s += 1000 / i }; return s }""",
                "fmt.Println(lp_harmonic(1)); fmt.Println(lp_harmonic(5)); fmt.Println(lp_harmonic(10))"),
            RoundTripTestCase("tri-num", """
func lp_tri(n int) int { return n * (n + 1) / 2 }
func lp_triLoop(n int) int { s := 0; for i := 1; i <= n; i++ { s += i }; return s }""",
                "fmt.Println(lp_tri(10)); fmt.Println(lp_triLoop(10)); fmt.Println(lp_tri(100)); fmt.Println(lp_triLoop(100))"),
            RoundTripTestCase("sum-cubes", """
func lp_sumCubes(n int) int { s := 0; for i := 1; i <= n; i++ { s += i * i * i }; return s }""",
                "fmt.Println(lp_sumCubes(3)); fmt.Println(lp_sumCubes(5)); fmt.Println(lp_sumCubes(10))"),
            RoundTripTestCase("palindrome", """
func lp_isPalin(n int) bool {
    if n < 0 { return false }
    orig := n; rev := 0
    for n > 0 { rev = rev*10 + n%10; n /= 10 }
    return orig == rev
}""", "fmt.Println(lp_isPalin(121)); fmt.Println(lp_isPalin(123)); fmt.Println(lp_isPalin(0)); fmt.Println(lp_isPalin(1221)); fmt.Println(lp_isPalin(-121))"),
            RoundTripTestCase("fib-iter", """
func lp_fib(n int) int {
    if n <= 1 { return n }
    a, b := 0, 1
    for i := 2; i <= n; i++ { a, b = b, a+b }
    return b
}""", "fmt.Println(lp_fib(0)); fmt.Println(lp_fib(1)); fmt.Println(lp_fib(5)); fmt.Println(lp_fib(10)); fmt.Println(lp_fib(20))"),
            RoundTripTestCase("tri-sum", """
func lp_triSum(n int) int {
    s := 0; for i := 1; i <= n; i++ { for j := 1; j <= i; j++ { s += j } }; return s
}""", "fmt.Println(lp_triSum(3)); fmt.Println(lp_triSum(5)); fmt.Println(lp_triSum(1))"),
            RoundTripTestCase("count-primes", """
func lp_countPrimes(n int) int {
    c := 0
    for i := 2; i < n; i++ {
        ok := true
        for j := 2; j*j <= i; j++ { if i%j == 0 { ok = false; break } }
        if ok { c++ }
    }
    return c
}""", "fmt.Println(lp_countPrimes(10)); fmt.Println(lp_countPrimes(20)); fmt.Println(lp_countPrimes(50))"),
        )
        return BatchRoundTripRunner.runBatchAndCreateTests(cases, builder)
    }
}
