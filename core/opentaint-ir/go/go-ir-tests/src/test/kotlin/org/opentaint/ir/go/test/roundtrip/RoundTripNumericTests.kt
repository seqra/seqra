package org.opentaint.ir.go.test.roundtrip

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

/** Numeric algorithm round-trip tests: integer math, number theory, bit manipulation, sequences. */
@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripNumericTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(

            // ── square root & roots ──────────────────────────────────────

            RoundTripTestCase.withInputs("isqrt-newton", """
func rtn_isqrt(n int) int {
    if n < 0 { n = -n }
    if n > 10000 { n = 10000 }
    if n == 0 { return 0 }
    x := n
    for x*x > n { x = (x + n/x) / 2 }
    return x
}""",
                "rtn_isqrt(%s)",
                listOf(listOf("0"), listOf("1"), listOf("4"), listOf("100"), listOf("9999")),
                randomRange = 1..1000,
            ),

            RoundTripTestCase.withInputs("icbrt", """
func rtn_icbrt(n int) int {
    if n < 0 { n = -n }
    if n > 10000 { n = 10000 }
    r := 0
    for (r+1)*(r+1)*(r+1) <= n { r++ }
    return r
}""",
                "rtn_icbrt(%s)",
                listOf(listOf("0"), listOf("1"), listOf("8"), listOf("27"), listOf("9999")),
                randomRange = 1..1000,
            ),

            // ── exponentiation ───────────────────────────────────────────

            RoundTripTestCase.withInputs("power-iter", """
func rtn_power(base, exp int) int {
    if base < 0 { base = -base }
    if base > 10 { base = 10 }
    if exp < 0 { exp = 0 }
    if exp > 13 { exp = 13 }
    result := 1
    for i := 0; i < exp; i++ { result *= base }
    return result
}""",
                "rtn_power(%s, %s)",
                listOf(listOf("2", "10"), listOf("3", "5"), listOf("1", "100"), listOf("0", "5"), listOf("7", "0")),
                randomRange = 1..20,
            ),

            RoundTripTestCase.withInputs("fast-power", """
func rtn_fastpow(base, exp int) int {
    if base < 0 { base = -base }
    if base > 10 { base = 10 }
    if exp < 0 { exp = 0 }
    if exp > 13 { exp = 13 }
    result := 1
    for exp > 0 {
        if exp%2 == 1 { result *= base }
        base *= base
        exp /= 2
    }
    return result
}""",
                "rtn_fastpow(%s, %s)",
                listOf(listOf("2", "10"), listOf("3", "7"), listOf("5", "3"), listOf("1", "20"), listOf("0", "5")),
                randomRange = 1..20,
            ),

            RoundTripTestCase.withInputs("mod-exp", """
func rtn_modexp(base, exp, mod int) int {
    if base < 0 { base = -base }
    if exp < 0 { exp = -exp }
    if mod < 1 { mod = 1 }
    if base > 10000 { base = 10000 }
    if exp > 10000 { exp = 10000 }
    if mod > 10000 { mod = 10000 }
    result := 1
    base = base % mod
    for exp > 0 {
        if exp%2 == 1 { result = result * base % mod }
        exp /= 2
        base = base * base % mod
    }
    return result
}""",
                "rtn_modexp(%s, %s, %s)",
                listOf(listOf("2", "10", "1000"), listOf("3", "13", "100"), listOf("5", "0", "7"), listOf("7", "3", "11"), listOf("2", "20", "97")),
                randomRange = 1..1000,
            ),

            // ── GCD / LCM ───────────────────────────────────────────────

            RoundTripTestCase.withInputs("gcd", """
func rtn_gcd(a, b int) int {
    if a < 0 { a = -a }
    if b < 0 { b = -b }
    for b != 0 { a, b = b, a%b }
    return a
}""",
                "rtn_gcd(%s, %s)",
                listOf(listOf("12", "8"), listOf("35", "15"), listOf("100", "75"), listOf("17", "13")),
                randomRange = 1..1000,
            ),

            RoundTripTestCase.withInputs("lcm", """
func rtn_lcm(a, b int) int {
    if a < 0 { a = -a }
    if b < 0 { b = -b }
    if a == 0 || b == 0 { return 0 }
    g := a; h := b
    for h != 0 { g, h = h, g%h }
    return a / g * b
}""",
                "rtn_lcm(%s, %s)",
                listOf(listOf("4", "6"), listOf("12", "8"), listOf("7", "13"), listOf("100", "75")),
                randomRange = 1..1000,
            ),

            RoundTripTestCase.withInputs("ext-gcd", """
func rtn_extgcd(a, b int) int {
    if a < 0 { a = -a }
    if b < 0 { b = -b }
    if a > 10000 { a = 10000 }
    if b > 10000 { b = 10000 }
    if b == 0 { return 1 }
    old_r, r := a, b
    old_s, s := 1, 0
    for r != 0 {
        q := old_r / r
        old_r, r = r, old_r-q*r
        old_s, s = s, old_s-q*s
    }
    return old_s
}""",
                "rtn_extgcd(%s, %s)",
                listOf(listOf("35", "15"), listOf("12", "8"), listOf("99", "78"), listOf("1", "1"), listOf("100", "75")),
                randomRange = 1..1000,
            ),

            // ── sequences ────────────────────────────────────────────────

            RoundTripTestCase.withInputs("fibonacci", """
func rtn_fib(n int) int {
    if n < 0 { n = -n }
    if n > 40 { n = 40 }
    if n <= 1 { return n }
    a, b := 0, 1
    for i := 2; i <= n; i++ { a, b = b, a+b }
    return b
}""",
                "rtn_fib(%s)",
                listOf(listOf("0"), listOf("1"), listOf("10"), listOf("20"), listOf("40")),
                randomRange = 1..40,
            ),

            RoundTripTestCase.withInputs("tribonacci", """
func rtn_tribonacci(n int) int {
    if n < 0 { n = -n }
    if n > 35 { n = 35 }
    if n == 0 { return 0 }
    if n <= 2 { return 1 }
    a, b, c := 0, 1, 1
    for i := 3; i <= n; i++ { a, b, c = b, c, a+b+c }
    return c
}""",
                "rtn_tribonacci(%s)",
                listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10"), listOf("25")),
                randomRange = 1..35,
            ),

            RoundTripTestCase.withInputs("lucas-number", """
func rtn_lucas(n int) int {
    if n < 0 { n = -n }
    if n > 40 { n = 40 }
    if n == 0 { return 2 }
    if n == 1 { return 1 }
    a, b := 2, 1
    for i := 2; i <= n; i++ { a, b = b, a+b }
    return b
}""",
                "rtn_lucas(%s)",
                listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10"), listOf("30")),
                randomRange = 1..40,
            ),

            RoundTripTestCase.withInputs("collatz-len", """
func rtn_collatz(n int) int {
    if n < 1 { n = 1 }
    if n > 10000 { n = 10000 }
    steps := 0
    for n != 1 && steps < 1000 {
        if n%2 == 0 { n /= 2 } else { n = 3*n + 1 }
        steps++
    }
    return steps
}""",
                "rtn_collatz(%s)",
                listOf(listOf("1"), listOf("2"), listOf("6"), listOf("27"), listOf("100")),
                randomRange = 1..1000,
            ),

            // ── combinatorics ────────────────────────────────────────────

            RoundTripTestCase.withInputs("factorial", """
func rtn_fact(n int) int {
    if n < 0 { n = -n }
    if n > 20 { n = 20 }
    r := 1
    for i := 2; i <= n; i++ { r *= i }
    return r
}""",
                "rtn_fact(%s)",
                listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10"), listOf("20")),
                randomRange = 1..20,
            ),

            RoundTripTestCase.withInputs("catalan", """
func rtn_catalan(n int) int {
    if n < 0 { n = -n }
    if n > 15 { n = 15 }
    if n <= 1 { return 1 }
    c := 1
    for i := 0; i < n; i++ { c = c * 2 * (2*i + 1) / (i + 2) }
    return c
}""",
                "rtn_catalan(%s)",
                listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10"), listOf("15")),
                randomRange = 1..15,
            ),

            RoundTripTestCase.withInputs("pascal-entry", """
func rtn_pascal(n, k int) int {
    if n < 0 { n = -n }
    if k < 0 { k = -k }
    if n > 20 { n = 20 }
    if k > n { k = n }
    if k > n-k { k = n - k }
    r := 1
    for i := 0; i < k; i++ { r = r * (n - i) / (i + 1) }
    return r
}""",
                "rtn_pascal(%s, %s)",
                listOf(listOf("5", "2"), listOf("10", "3"), listOf("20", "10"), listOf("0", "0"), listOf("7", "7")),
                randomRange = 1..20,
            ),

            RoundTripTestCase.withInputs("binomial", """
func rtn_binom(n, k int) int {
    if n < 0 { n = -n }
    if k < 0 { k = -k }
    if n > 20 { n = 20 }
    if k > n { k = n }
    if k > n-k { k = n - k }
    r := 1
    for i := 0; i < k; i++ { r = r * (n - i) / (i + 1) }
    return r
}""",
                "rtn_binom(%s, %s)",
                listOf(listOf("10", "5"), listOf("15", "7"), listOf("20", "0"), listOf("1", "1"), listOf("12", "4")),
                randomRange = 1..20,
            ),

            RoundTripTestCase.withInputs("partition-count", """
func rtn_partition(n int) int {
    if n < 0 { n = -n }
    if n > 100 { n = 100 }
    var dp [101]int
    dp[0] = 1
    for i := 1; i <= n; i++ {
        for j := i; j <= n; j++ { dp[j] += dp[j-i] }
    }
    return dp[n]
}""",
                "rtn_partition(%s)",
                listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10"), listOf("50")),
                randomRange = 1..100,
            ),

            // ── digit manipulation ───────────────────────────────────────

            RoundTripTestCase.withInputs("digit-count", """
func rtn_ndigits(n int) int {
    if n < 0 { n = -n }
    if n > 10000 { n = 10000 }
    if n == 0 { return 1 }
    c := 0
    for n > 0 { c++; n /= 10 }
    return c
}""",
                "rtn_ndigits(%s)",
                listOf(listOf("0"), listOf("9"), listOf("100"), listOf("9999"), listOf("10000")),
                randomRange = 1..10000,
            ),

            RoundTripTestCase.withInputs("digit-sum", """
func rtn_digitsum(n int) int {
    if n < 0 { n = -n }
    if n > 10000 { n = 10000 }
    s := 0
    for n > 0 { s += n % 10; n /= 10 }
    return s
}""",
                "rtn_digitsum(%s)",
                listOf(listOf("0"), listOf("123"), listOf("9999"), listOf("100"), listOf("5678")),
                randomRange = 1..10000,
            ),

            RoundTripTestCase.withInputs("digit-product", """
func rtn_digitprod(n int) int {
    if n < 0 { n = -n }
    if n > 10000 { n = 10000 }
    if n == 0 { return 0 }
    p := 1
    for n > 0 { p *= n % 10; n /= 10 }
    return p
}""",
                "rtn_digitprod(%s)",
                listOf(listOf("0"), listOf("123"), listOf("999"), listOf("111"), listOf("234")),
                randomRange = 1..10000,
            ),

            RoundTripTestCase.withInputs("digital-root", """
func rtn_digroot(n int) int {
    if n < 0 { n = -n }
    if n > 10000 { n = 10000 }
    for n >= 10 {
        s := 0
        for n > 0 { s += n % 10; n /= 10 }
        n = s
    }
    return n
}""",
                "rtn_digroot(%s)",
                listOf(listOf("0"), listOf("9"), listOf("123"), listOf("9999"), listOf("493")),
                randomRange = 1..10000,
            ),

            RoundTripTestCase.withInputs("reverse-int", """
func rtn_reverse(n int) int {
    if n < 0 { n = -n }
    if n > 10000 { n = 10000 }
    r := 0
    for n > 0 { r = r*10 + n%10; n /= 10 }
    return r
}""",
                "rtn_reverse(%s)",
                listOf(listOf("0"), listOf("123"), listOf("1000"), listOf("9999"), listOf("1221")),
                randomRange = 1..10000,
            ),

            // ── number predicates (returning 1/0) ───────────────────────

            RoundTripTestCase.withInputs("palindrome-check", """
func rtn_ispalindrome(n int) int {
    if n < 0 { n = -n }
    if n > 10000 { n = 10000 }
    orig := n; rev := 0
    for n > 0 { rev = rev*10 + n%10; n /= 10 }
    if orig == rev { return 1 }
    return 0
}""",
                "rtn_ispalindrome(%s)",
                listOf(listOf("121"), listOf("123"), listOf("0"), listOf("1001"), listOf("9")),
                randomRange = 1..10000,
            ),

            RoundTripTestCase.withInputs("armstrong-check", """
func rtn_isarmstrong(n int) int {
    if n < 0 { n = -n }
    if n > 10000 { n = 10000 }
    orig := n
    digits := 0; tmp := n
    if tmp == 0 { digits = 1 }
    for tmp > 0 { digits++; tmp /= 10 }
    s := 0; tmp = n
    for tmp > 0 {
        d := tmp % 10; p := 1
        for i := 0; i < digits; i++ { p *= d }
        s += p; tmp /= 10
    }
    if s == orig { return 1 }
    return 0
}""",
                "rtn_isarmstrong(%s)",
                listOf(listOf("153"), listOf("370"), listOf("100"), listOf("0"), listOf("1")),
                randomRange = 1..1000,
            ),

            RoundTripTestCase.withInputs("perfect-check", """
func rtn_isperfect(n int) int {
    if n < 0 { n = -n }
    if n > 10000 { n = 10000 }
    if n <= 1 { return 0 }
    s := 1
    for i := 2; i*i <= n; i++ {
        if n%i == 0 { s += i; if i != n/i { s += n / i } }
    }
    if s == n { return 1 }
    return 0
}""",
                "rtn_isperfect(%s)",
                listOf(listOf("6"), listOf("28"), listOf("496"), listOf("12"), listOf("1")),
                randomRange = 1..1000,
            ),

            // ── divisor functions ────────────────────────────────────────

            RoundTripTestCase.withInputs("count-divisors", """
func rtn_countdiv(n int) int {
    if n < 0 { n = -n }
    if n > 10000 { n = 10000 }
    if n == 0 { return 0 }
    c := 0
    for i := 1; i*i <= n; i++ {
        if n%i == 0 { c += 2; if i*i == n { c-- } }
    }
    return c
}""",
                "rtn_countdiv(%s)",
                listOf(listOf("1"), listOf("12"), listOf("100"), listOf("7"), listOf("360")),
                randomRange = 1..1000,
            ),

            RoundTripTestCase.withInputs("sum-divisors", """
func rtn_sumdiv(n int) int {
    if n < 0 { n = -n }
    if n > 10000 { n = 10000 }
    if n == 0 { return 0 }
    s := 0
    for i := 1; i*i <= n; i++ {
        if n%i == 0 { s += i; if i != n/i { s += n / i } }
    }
    return s
}""",
                "rtn_sumdiv(%s)",
                listOf(listOf("1"), listOf("12"), listOf("28"), listOf("100"), listOf("7")),
                randomRange = 1..1000,
            ),

            // ── number theory ────────────────────────────────────────────

            RoundTripTestCase.withInputs("euler-totient", """
func rtn_totient(n int) int {
    if n < 0 { n = -n }
    if n > 10000 { n = 10000 }
    if n == 0 { return 0 }
    result := n; tmp := n
    for d := 2; d*d <= tmp; d++ {
        if tmp%d == 0 {
            for tmp%d == 0 { tmp /= d }
            result -= result / d
        }
    }
    if tmp > 1 { result -= result / tmp }
    return result
}""",
                "rtn_totient(%s)",
                listOf(listOf("1"), listOf("10"), listOf("12"), listOf("97"), listOf("100")),
                randomRange = 1..1000,
            ),

            RoundTripTestCase.withInputs("prime-count", """
func rtn_primecount(n int) int {
    if n < 0 { n = -n }
    if n > 10000 { n = 10000 }
    if n < 2 { return 0 }
    count := 0
    for i := 2; i <= n; i++ {
        prime := 1
        for j := 2; j*j <= i; j++ {
            if i%j == 0 { prime = 0; break }
        }
        count += prime
    }
    return count
}""",
                "rtn_primecount(%s)",
                listOf(listOf("10"), listOf("30"), listOf("100"), listOf("1"), listOf("2")),
                randomRange = 1..500,
            ),

            RoundTripTestCase.withInputs("next-prime", """
func rtn_nextprime(n int) int {
    if n < 0 { n = -n }
    if n > 10000 { n = 10000 }
    candidate := n + 1
    if candidate < 2 { candidate = 2 }
    for i := 0; i < 10000; i++ {
        prime := 1
        for j := 2; j*j <= candidate; j++ {
            if candidate%j == 0 { prime = 0; break }
        }
        if prime == 1 { return candidate }
        candidate++
    }
    return -1
}""",
                "rtn_nextprime(%s)",
                listOf(listOf("1"), listOf("2"), listOf("10"), listOf("100"), listOf("997")),
                randomRange = 1..1000,
            ),

            RoundTripTestCase.withInputs("jacobi", """
func rtn_jacobi(a, n int) int {
    if n < 0 { n = -n }
    if n > 10000 { n = 10000 }
    if n <= 0 || n%2 == 0 { return 0 }
    a = a % n
    if a < 0 { a += n }
    result := 1
    for a != 0 {
        for a%2 == 0 {
            a /= 2
            if n%8 == 3 || n%8 == 5 { result = -result }
        }
        a, n = n, a
        if a%4 == 3 && n%4 == 3 { result = -result }
        a = a % n
    }
    if n == 1 { return result }
    return 0
}""",
                "rtn_jacobi(%s, %s)",
                listOf(listOf("2", "7"), listOf("5", "11"), listOf("3", "9"), listOf("1", "15"), listOf("6", "13")),
                randomRange = 1..500,
            ),

            RoundTripTestCase.withInputs("trailing-zeros-fact", """
func rtn_trailingzeros(n int) int {
    if n < 0 { n = -n }
    if n > 10000 { n = 10000 }
    count := 0
    for n >= 5 { n /= 5; count += n }
    return count
}""",
                "rtn_trailingzeros(%s)",
                listOf(listOf("0"), listOf("5"), listOf("25"), listOf("100"), listOf("1000")),
                randomRange = 1..10000,
            ),

            // ── bit manipulation ─────────────────────────────────────────

            RoundTripTestCase.withInputs("log2", """
func rtn_log2(n int) int {
    if n < 0 { n = -n }
    if n > 10000 { n = 10000 }
    if n == 0 { return -1 }
    c := 0
    for n > 1 { c++; n /= 2 }
    return c
}""",
                "rtn_log2(%s)",
                listOf(listOf("1"), listOf("2"), listOf("8"), listOf("1000"), listOf("1024")),
                randomRange = 1..10000,
            ),

            RoundTripTestCase.withInputs("popcount", """
func rtn_popcount(n int) int {
    if n < 0 { n = -n }
    if n > 10000 { n = 10000 }
    c := 0
    for n > 0 { c += n & 1; n >>= 1 }
    return c
}""",
                "rtn_popcount(%s)",
                listOf(listOf("0"), listOf("1"), listOf("7"), listOf("255"), listOf("1023")),
                randomRange = 1..10000,
            ),

            RoundTripTestCase.withInputs("bit-reverse", """
func rtn_bitrev(n int) int {
    if n < 0 { n = -n }
    if n > 10000 { n = 10000 }
    r := 0
    for i := 0; i < 16; i++ {
        r = (r << 1) | (n & 1)
        n >>= 1
    }
    return r
}""",
                "rtn_bitrev(%s)",
                listOf(listOf("0"), listOf("1"), listOf("255"), listOf("1024"), listOf("5000")),
                randomRange = 1..10000,
            ),

            RoundTripTestCase.withInputs("hamming-distance", """
func rtn_hamming(a, b int) int {
    if a < 0 { a = -a }
    if b < 0 { b = -b }
    if a > 10000 { a = 10000 }
    if b > 10000 { b = 10000 }
    x := a ^ b
    c := 0
    for x > 0 { c += x & 1; x >>= 1 }
    return c
}""",
                "rtn_hamming(%s, %s)",
                listOf(listOf("0", "0"), listOf("1", "2"), listOf("255", "0"), listOf("7", "7"), listOf("100", "200")),
                randomRange = 1..10000,
            ),

            // ── Stirling & Ackermann ─────────────────────────────────────

            RoundTripTestCase.withInputs("stirling-approx", """
func rtn_stirling(n int) int {
    if n < 0 { n = -n }
    if n > 10000 { n = 10000 }
    if n <= 1 { return 0 }
    lg := 0; tmp := n
    for tmp > 1 { lg++; tmp /= 2 }
    return n*lg - n + 1
}""",
                "rtn_stirling(%s)",
                listOf(listOf("1"), listOf("2"), listOf("10"), listOf("100"), listOf("1000")),
                randomRange = 1..1000,
            ),

            RoundTripTestCase.withInputs("ackermann-bounded", """
func rtn_ackermann(m, n int) int {
    if m < 0 { m = 0 }
    if n < 0 { n = 0 }
    if m > 3 { m = 3 }
    if n > 12 { n = 12 }
    if m == 0 { return n + 1 }
    if m == 1 { return n + 2 }
    if m == 2 { return 2*n + 3 }
    p := 1
    for i := 0; i < n+3; i++ { p *= 2 }
    return p - 3
}""",
                "rtn_ackermann(%s, %s)",
                listOf(listOf("0", "0"), listOf("1", "5"), listOf("2", "4"), listOf("3", "3"), listOf("3", "10")),
                randomRange = 0..12,
            ),

            // ── multiplication algorithms ────────────────────────────────

            RoundTripTestCase.withInputs("egyptian-mul", """
func rtn_egyptian(a, b int) int {
    if a < 0 { a = -a }
    if b < 0 { b = -b }
    if a > 10000 { a = 10000 }
    if b > 10000 { b = 10000 }
    result := 0
    for a > 0 {
        if a&1 == 1 { result += b }
        a >>= 1
        b <<= 1
    }
    return result
}""",
                "rtn_egyptian(%s, %s)",
                listOf(listOf("6", "7"), listOf("0", "5"), listOf("13", "17"), listOf("1", "999")),
                randomRange = 1..1000,
            ),

            RoundTripTestCase.withInputs("russian-peasant", """
func rtn_peasant(a, b int) int {
    if a < 0 { a = -a }
    if b < 0 { b = -b }
    if a > 10000 { a = 10000 }
    if b > 10000 { b = 10000 }
    result := 0
    for a > 0 {
        if a%2 != 0 { result += b }
        a /= 2
        b *= 2
    }
    return result
}""",
                "rtn_peasant(%s, %s)",
                listOf(listOf("6", "7"), listOf("0", "5"), listOf("13", "17"), listOf("1", "999")),
                randomRange = 1..1000,
            ),
        )
        return BatchRoundTripRunner.runBatchAndCreateTests(cases, builder)
    }
}
