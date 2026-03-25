package org.opentaint.ir.go.test.roundtrip

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

/** Math-oriented round-trip tests: sequences, series, number theory, combinatorics. */
@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripMathTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase("sum-sq-formula", "func m_sumSq(n int) int { return n*(n+1)*(2*n+1)/6 }",
                "fmt.Println(m_sumSq(1)); fmt.Println(m_sumSq(5)); fmt.Println(m_sumSq(10)); fmt.Println(m_sumSq(100))"),
            RoundTripTestCase("sum-cubes-formula", "func m_sumCubes(n int) int { s := n*(n+1)/2; return s*s }",
                "fmt.Println(m_sumCubes(1)); fmt.Println(m_sumCubes(5)); fmt.Println(m_sumCubes(10))"),
            RoundTripTestCase("geometric-sum", """
func m_geoSum(a, r, n int) int {
    s := 0; p := a
    for i := 0; i < n; i++ { s += p; p *= r }
    return s
}""", "fmt.Println(m_geoSum(1,2,10)); fmt.Println(m_geoSum(3,3,5)); fmt.Println(m_geoSum(1,1,100))"),
            RoundTripTestCase("fibonacci-mod", """
func m_fibMod(n, m int) int {
    if n <= 1 { return n % m }
    a, b := 0, 1
    for i := 2; i <= n; i++ { a, b = b, (a+b)%m }
    return b
}""", "fmt.Println(m_fibMod(10,100)); fmt.Println(m_fibMod(50,1000)); fmt.Println(m_fibMod(100,997))"),
            RoundTripTestCase("lucas-number", """
func m_lucas(n int) int {
    if n == 0 { return 2 }; if n == 1 { return 1 }
    a, b := 2, 1
    for i := 2; i <= n; i++ { a, b = b, a+b }
    return b
}""", "fmt.Println(m_lucas(0)); fmt.Println(m_lucas(1)); fmt.Println(m_lucas(5)); fmt.Println(m_lucas(10))"),
            RoundTripTestCase("pell-number", """
func m_pell(n int) int {
    if n == 0 { return 0 }; if n == 1 { return 1 }
    a, b := 0, 1
    for i := 2; i <= n; i++ { a, b = b, 2*b+a }
    return b
}""", "fmt.Println(m_pell(0)); fmt.Println(m_pell(1)); fmt.Println(m_pell(5)); fmt.Println(m_pell(8))"),
            RoundTripTestCase("pentagonal", "func m_pent(n int) int { return n*(3*n-1)/2 }",
                "fmt.Println(m_pent(1)); fmt.Println(m_pent(5)); fmt.Println(m_pent(10))"),
            RoundTripTestCase("tetrahedral", "func m_tetra(n int) int { return n*(n+1)*(n+2)/6 }",
                "fmt.Println(m_tetra(1)); fmt.Println(m_tetra(4)); fmt.Println(m_tetra(10))"),
            RoundTripTestCase("binomial", """
func m_binom(n, k int) int {
    if k > n-k { k = n - k }
    r := 1
    for i := 0; i < k; i++ { r = r * (n - i) / (i + 1) }
    return r
}""", "fmt.Println(m_binom(5,2)); fmt.Println(m_binom(10,3)); fmt.Println(m_binom(20,10))"),
            RoundTripTestCase("stirling-approx", """
func m_logFloor(n int) int {
    if n <= 0 { return 0 }; c := 0
    for n > 1 { c++; n /= 2 }; return c
}""", "fmt.Println(m_logFloor(1)); fmt.Println(m_logFloor(8)); fmt.Println(m_logFloor(1000))"),
            RoundTripTestCase("mobius", """
func m_mobius(n int) int {
    if n == 1 { return 1 }
    pf := 0
    for d := 2; d*d <= n; d++ {
        if n%d == 0 { pf++; n /= d; if n%d == 0 { return 0 } }
    }
    if n > 1 { pf++ }
    if pf%2 == 0 { return 1 }; return -1
}""", "fmt.Println(m_mobius(1)); fmt.Println(m_mobius(2)); fmt.Println(m_mobius(4)); fmt.Println(m_mobius(6)); fmt.Println(m_mobius(30))"),
            RoundTripTestCase("jacobi-symbol", """
func m_jacobi(a, n int) int {
    if n <= 0 || n%2 == 0 { return 0 }
    a = a % n; if a < 0 { a += n }
    result := 1
    for a != 0 {
        for a%2 == 0 { a /= 2; if n%8 == 3 || n%8 == 5 { result = -result } }
        a, n = n, a
        if a%4 == 3 && n%4 == 3 { result = -result }
        a = a % n
    }
    if n == 1 { return result }; return 0
}""", "fmt.Println(m_jacobi(2,7)); fmt.Println(m_jacobi(5,11)); fmt.Println(m_jacobi(3,9))"),
            RoundTripTestCase("power-sum", """
func m_powerSum(n, p int) int {
    s := 0
    for i := 1; i <= n; i++ {
        v := 1; for j := 0; j < p; j++ { v *= i }; s += v
    }
    return s
}""", "fmt.Println(m_powerSum(5,1)); fmt.Println(m_powerSum(5,2)); fmt.Println(m_powerSum(5,3))"),
            RoundTripTestCase("abundant-sum", """
func m_divSum(n int) int {
    s := 1
    for i := 2; i*i <= n; i++ { if n%i == 0 { s += i; if i != n/i { s += n / i } } }
    return s
}
func m_countAbundant(limit int) int {
    c := 0
    for i := 2; i <= limit; i++ { if m_divSum(i) > i { c++ } }
    return c
}""", "fmt.Println(m_countAbundant(20)); fmt.Println(m_countAbundant(50)); fmt.Println(m_countAbundant(100))"),
            RoundTripTestCase("euler-phi-sum", """
func m_phiSum(n int) int {
    s := 0
    for k := 1; k <= n; k++ {
        phi := k; tmp := k
        for d := 2; d*d <= tmp; d++ {
            if tmp%d == 0 { for tmp%d == 0 { tmp /= d }; phi -= phi / d }
        }
        if tmp > 1 { phi -= phi / tmp }
        s += phi
    }
    return s
}""", "fmt.Println(m_phiSum(1)); fmt.Println(m_phiSum(10)); fmt.Println(m_phiSum(20))"),
            RoundTripTestCase("nth-triangular-above", """
func m_nthTriAbove(target int) int {
    n := 0; for n*(n+1)/2 < target { n++ }; return n
}""", "fmt.Println(m_nthTriAbove(1)); fmt.Println(m_nthTriAbove(10)); fmt.Println(m_nthTriAbove(100))"),
            RoundTripTestCase("integer-log10", """
func m_log10(n int) int {
    if n <= 0 { return -1 }; c := 0; for n >= 10 { c++; n /= 10 }; return c
}""", "fmt.Println(m_log10(1)); fmt.Println(m_log10(9)); fmt.Println(m_log10(100)); fmt.Println(m_log10(999)); fmt.Println(m_log10(10000))"),
            RoundTripTestCase("ceil-div", "func m_ceilDiv(a, b int) int { return (a + b - 1) / b }",
                "fmt.Println(m_ceilDiv(10,3)); fmt.Println(m_ceilDiv(9,3)); fmt.Println(m_ceilDiv(1,5)); fmt.Println(m_ceilDiv(7,1))"),
            RoundTripTestCase("round-to-nearest", """
func m_roundTo(x, n int) int {
    if n == 0 { return x }
    r := x % n
    if r*2 >= n { return x + n - r }
    return x - r
}""", "fmt.Println(m_roundTo(13,5)); fmt.Println(m_roundTo(12,5)); fmt.Println(m_roundTo(15,5)); fmt.Println(m_roundTo(0,5))"),
            RoundTripTestCase("is-square", """
func m_isSquare(n int) bool {
    if n < 0 { return false }
    r := 0; for (r+1)*(r+1) <= n { r++ }
    return r*r == n
}""", "fmt.Println(m_isSquare(0)); fmt.Println(m_isSquare(1)); fmt.Println(m_isSquare(4)); fmt.Println(m_isSquare(5)); fmt.Println(m_isSquare(16)); fmt.Println(m_isSquare(17))"),
        )
        val result = BatchRoundTripRunner.runBatch(cases, builder)
        return cases.map { c -> DynamicTest.dynamicTest(c.name) {
            assertThat(result.reconstructedOutputs[c.name]).isEqualTo(result.originalOutputs[c.name])
        }}
    }
}
