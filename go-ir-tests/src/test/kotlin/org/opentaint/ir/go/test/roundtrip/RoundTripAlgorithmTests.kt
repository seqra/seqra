package org.opentaint.ir.go.test.roundtrip

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripAlgorithmTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase("bubble-pass", """
func alg_bubblePass(a, b, c, d int) int {
    if a > b { a, b = b, a }
    if b > c { b, c = c, b }
    if c > d { c, d = d, c }
    if a > b { a, b = b, a }
    if b > c { b, c = c, b }
    if a > b { a, b = b, a }
    return a*1000 + b*100 + c*10 + d
}""", "fmt.Println(alg_bubblePass(4,3,2,1)); fmt.Println(alg_bubblePass(1,2,3,4)); fmt.Println(alg_bubblePass(3,1,4,2))"),
            RoundTripTestCase("newton-isqrt", """
func alg_newtonSqrt(n int) int {
    if n <= 0 { return 0 }
    x := n
    for x*x > n { x = (x + n/x) / 2 }
    return x
}""", "fmt.Println(alg_newtonSqrt(0)); fmt.Println(alg_newtonSqrt(1)); fmt.Println(alg_newtonSqrt(100)); fmt.Println(alg_newtonSqrt(99))"),
            RoundTripTestCase("sieve-count", """
func alg_sieveCount(n int) int {
    if n < 2 { return 0 }
    count := 0
    for i := 2; i < n; i++ {
        prime := true
        for j := 2; j*j <= i; j++ { if i%j == 0 { prime = false; break } }
        if prime { count++ }
    }
    return count
}""", "fmt.Println(alg_sieveCount(10)); fmt.Println(alg_sieveCount(30)); fmt.Println(alg_sieveCount(100))"),
            RoundTripTestCase("ext-gcd-x", """
func alg_extGcd(a, b int) int {
    if b == 0 { return a }
    return alg_extGcd(b, a%b)
}""", "fmt.Println(alg_extGcd(35,15)); fmt.Println(alg_extGcd(12,8)); fmt.Println(alg_extGcd(100,75))"),
            RoundTripTestCase("euler-totient", """
func alg_totient(n int) int {
    count := 0
    for i := 1; i < n; i++ {
        a, b := i, n
        for b != 0 { a, b = b, a%b }
        if a == 1 { count++ }
    }
    return count
}""", "fmt.Println(alg_totient(1)); fmt.Println(alg_totient(10)); fmt.Println(alg_totient(12))"),
            RoundTripTestCase("digital-root", """
func alg_digitalRoot(n int) int {
    if n < 0 { n = -n }
    for n >= 10 {
        s := 0; for n > 0 { s += n % 10; n /= 10 }; n = s
    }
    return n
}""", "fmt.Println(alg_digitalRoot(0)); fmt.Println(alg_digitalRoot(9)); fmt.Println(alg_digitalRoot(123)); fmt.Println(alg_digitalRoot(9999))"),
            RoundTripTestCase("happy-number", """
func alg_isHappy(n int) bool {
    for step := 0; step < 100; step++ {
        if n == 1 { return true }
        s := 0
        for n > 0 { d := n % 10; s += d * d; n /= 10 }
        n = s
    }
    return false
}""", "fmt.Println(alg_isHappy(1)); fmt.Println(alg_isHappy(7)); fmt.Println(alg_isHappy(4)); fmt.Println(alg_isHappy(19))"),
            RoundTripTestCase("perfect-number", """
func alg_isPerfect(n int) bool {
    if n <= 1 { return false }
    s := 1
    for i := 2; i*i <= n; i++ {
        if n%i == 0 { s += i; if i != n/i { s += n / i } }
    }
    return s == n
}""", "fmt.Println(alg_isPerfect(6)); fmt.Println(alg_isPerfect(28)); fmt.Println(alg_isPerfect(12)); fmt.Println(alg_isPerfect(1))"),
            RoundTripTestCase("abundant", """
func alg_isAbundant(n int) bool {
    if n <= 1 { return false }
    s := 1
    for i := 2; i*i <= n; i++ {
        if n%i == 0 { s += i; if i != n/i { s += n / i } }
    }
    return s > n
}""", "fmt.Println(alg_isAbundant(12)); fmt.Println(alg_isAbundant(6)); fmt.Println(alg_isAbundant(28)); fmt.Println(alg_isAbundant(18))"),
            RoundTripTestCase("harshad", """
func alg_isHarshad(n int) bool {
    if n <= 0 { return false }
    s := 0; tmp := n
    for tmp > 0 { s += tmp % 10; tmp /= 10 }
    return n%s == 0
}""", "fmt.Println(alg_isHarshad(18)); fmt.Println(alg_isHarshad(21)); fmt.Println(alg_isHarshad(19)); fmt.Println(alg_isHarshad(1))"),
            RoundTripTestCase("kaprekar-step", """
func alg_kaprekar(n int) int {
    for step := 0; step < 10; step++ {
        if n == 6174 { return step }
        d := [4]int{}
        for i := 0; i < 4; i++ { d[i] = n % 10; n /= 10 }
        for i := 0; i < 3; i++ { for j := i+1; j < 4; j++ { if d[i] > d[j] { d[i], d[j] = d[j], d[i] } } }
        asc := d[0]*1000 + d[1]*100 + d[2]*10 + d[3]
        desc := d[3]*1000 + d[2]*100 + d[1]*10 + d[0]
        n = desc - asc
    }
    return -1
}""", "fmt.Println(alg_kaprekar(3524)); fmt.Println(alg_kaprekar(6174)); fmt.Println(alg_kaprekar(1234))"),
            RoundTripTestCase("armstrong", """
func alg_isArmstrong(n int) bool {
    if n < 0 { return false }
    digits := 0; tmp := n
    for tmp > 0 { digits++; tmp /= 10 }
    s := 0; tmp = n
    for tmp > 0 {
        d := tmp % 10; p := 1
        for i := 0; i < digits; i++ { p *= d }
        s += p; tmp /= 10
    }
    return s == n
}""", "fmt.Println(alg_isArmstrong(153)); fmt.Println(alg_isArmstrong(370)); fmt.Println(alg_isArmstrong(100)); fmt.Println(alg_isArmstrong(0))"),
            RoundTripTestCase("luhn-check", """
func alg_luhn(n int) bool {
    if n <= 0 { return false }
    sum := 0; alt := false
    for n > 0 {
        d := n % 10; n /= 10
        if alt { d *= 2; if d > 9 { d -= 9 } }
        sum += d; alt = !alt
    }
    return sum%10 == 0
}""", "fmt.Println(alg_luhn(79927398713)); fmt.Println(alg_luhn(79927398710)); fmt.Println(alg_luhn(1234))"),
            RoundTripTestCase("insertion-sort-4", """
func alg_sort4(a, b, c, d int) int {
    if b < a { a, b = b, a }
    if c < b { c, b = b, c; if b < a { a, b = b, a } }
    if d < c { d, c = c, d; if c < b { c, b = b, c; if b < a { a, b = b, a } } }
    return a*1000 + b*100 + c*10 + d
}""", "fmt.Println(alg_sort4(4,3,2,1)); fmt.Println(alg_sort4(1,2,3,4)); fmt.Println(alg_sort4(2,4,1,3))"),
            RoundTripTestCase("count-divisors", """
func alg_countDiv(n int) int {
    if n <= 0 { return 0 }
    c := 0
    for i := 1; i*i <= n; i++ { if n%i == 0 { c += 2; if i*i == n { c-- } } }
    return c
}""", "fmt.Println(alg_countDiv(1)); fmt.Println(alg_countDiv(12)); fmt.Println(alg_countDiv(100)); fmt.Println(alg_countDiv(7))"),
            RoundTripTestCase("sum-divisors", """
func alg_sumDiv(n int) int {
    if n <= 0 { return 0 }
    s := 0
    for i := 1; i*i <= n; i++ { if n%i == 0 { s += i; if i != n/i { s += n / i } } }
    return s
}""", "fmt.Println(alg_sumDiv(1)); fmt.Println(alg_sumDiv(12)); fmt.Println(alg_sumDiv(28))"),
            RoundTripTestCase("base-convert", """
func alg_toBase(n, base int) int {
    if n == 0 { return 0 }
    r := 0; mul := 1
    for n > 0 { r += (n % base) * mul; n /= base; mul *= 10 }
    return r
}""", "fmt.Println(alg_toBase(10,2)); fmt.Println(alg_toBase(255,16)); fmt.Println(alg_toBase(8,8))"),
            RoundTripTestCase("count-trailing-zeros", """
func alg_ctz(n int) int {
    if n == 0 { return -1 }
    c := 0
    for n%2 == 0 { c++; n /= 2 }
    return c
}""", "fmt.Println(alg_ctz(8)); fmt.Println(alg_ctz(12)); fmt.Println(alg_ctz(1)); fmt.Println(alg_ctz(0))"),
            RoundTripTestCase("mod-exp", """
func alg_modExp(base, exp, mod int) int {
    result := 1; base = base % mod
    for exp > 0 {
        if exp%2 == 1 { result = result * base % mod }
        exp /= 2; base = base * base % mod
    }
    return result
}""", "fmt.Println(alg_modExp(2,10,1000)); fmt.Println(alg_modExp(3,13,100)); fmt.Println(alg_modExp(5,0,7))"),
            RoundTripTestCase("nth-prime", """
func alg_nthPrime(n int) int {
    count := 0; num := 1
    for count < n {
        num++; isPrime := true
        for i := 2; i*i <= num; i++ { if num%i == 0 { isPrime = false; break } }
        if isPrime { count++ }
    }
    return num
}""", "fmt.Println(alg_nthPrime(1)); fmt.Println(alg_nthPrime(5)); fmt.Println(alg_nthPrime(10)); fmt.Println(alg_nthPrime(25))"),
            RoundTripTestCase("prime-factors-sum", """
func alg_primeFactorSum(n int) int {
    s := 0
    for d := 2; d*d <= n; d++ { for n%d == 0 { s += d; n /= d } }
    if n > 1 { s += n }
    return s
}""", "fmt.Println(alg_primeFactorSum(12)); fmt.Println(alg_primeFactorSum(100)); fmt.Println(alg_primeFactorSum(7))"),
            RoundTripTestCase("goldbach-pair", """
func alg_goldbach(n int) int {
    if n < 4 || n%2 != 0 { return -1 }
    for i := 2; i <= n/2; i++ {
        ip := true; for j := 2; j*j <= i; j++ { if i%j == 0 { ip = false; break } }
        if !ip { continue }
        k := n - i
        kp := true; for j := 2; j*j <= k; j++ { if k%j == 0 { kp = false; break } }
        if kp { return i }
    }
    return -1
}""", "fmt.Println(alg_goldbach(4)); fmt.Println(alg_goldbach(10)); fmt.Println(alg_goldbach(100))"),
            RoundTripTestCase("catalan-iter", """
func alg_catalan(n int) int {
    if n <= 1 { return 1 }
    c := 1
    for i := 0; i < n; i++ { c = c * 2 * (2*i + 1) / (i + 2) }
    return c
}""", "fmt.Println(alg_catalan(0)); fmt.Println(alg_catalan(1)); fmt.Println(alg_catalan(5)); fmt.Println(alg_catalan(10))"),
            RoundTripTestCase("zigzag-sum", """
func alg_zigzag(n int) int {
    s := 0; sign := 1
    for i := 1; i <= n; i++ { s += sign * i; sign = -sign }
    return s
}""", "fmt.Println(alg_zigzag(1)); fmt.Println(alg_zigzag(4)); fmt.Println(alg_zigzag(10)); fmt.Println(alg_zigzag(0))"),
        )
        val result = BatchRoundTripRunner.runBatch(cases, builder)
        return cases.map { c -> DynamicTest.dynamicTest(c.name) {
            assertThat(result.reconstructedOutputs[c.name]).isEqualTo(result.originalOutputs[c.name])
        }}
    }
}
