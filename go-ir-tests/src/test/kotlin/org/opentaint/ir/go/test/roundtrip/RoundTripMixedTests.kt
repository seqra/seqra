package org.opentaint.ir.go.test.roundtrip

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

/** Mixed tests combining multiple features per function: conditionals+loops, recursion+math, etc. */
@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripMixedTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase("roman-val", """
func mx_romanVal(c byte) int {
    if c == 'I' { return 1 }; if c == 'V' { return 5 }; if c == 'X' { return 10 }
    if c == 'L' { return 50 }; if c == 'C' { return 100 }; if c == 'D' { return 500 }
    if c == 'M' { return 1000 }; return 0
}
func mx_roman(s string) int {
    total := 0
    for i := 0; i < len(s); i++ {
        v := mx_romanVal(s[i])
        if i+1 < len(s) && v < mx_romanVal(s[i+1]) { total -= v } else { total += v }
    }
    return total
}""", """fmt.Println(mx_roman("XIV")); fmt.Println(mx_roman("MCMXC")); fmt.Println(mx_roman("MMXXVI"))"""),
            RoundTripTestCase("balanced-parens", """
func mx_balanced(s string) bool {
    depth := 0
    for i := 0; i < len(s); i++ {
        if s[i] == '(' { depth++ } else if s[i] == ')' { depth--; if depth < 0 { return false } }
    }
    return depth == 0
}""", """fmt.Println(mx_balanced("(())")); fmt.Println(mx_balanced("(()")); fmt.Println(mx_balanced("")); fmt.Println(mx_balanced(")("))"""),
            RoundTripTestCase("run-length-count", """
func mx_runLen(s string) int {
    if len(s) == 0 { return 0 }
    runs := 1
    for i := 1; i < len(s); i++ { if s[i] != s[i-1] { runs++ } }
    return runs
}""", """fmt.Println(mx_runLen("")); fmt.Println(mx_runLen("a")); fmt.Println(mx_runLen("aabbc")); fmt.Println(mx_runLen("abcdef"))"""),
            RoundTripTestCase("max-consecutive-ones", """
func mx_maxOnes(n int) int {
    max := 0; cur := 0
    for n > 0 { if n&1 == 1 { cur++; if cur > max { max = cur } } else { cur = 0 }; n >>= 1 }
    return max
}""", "fmt.Println(mx_maxOnes(0)); fmt.Println(mx_maxOnes(7)); fmt.Println(mx_maxOnes(15)); fmt.Println(mx_maxOnes(0b1101110))"),
            RoundTripTestCase("encode-decode", """
func mx_encode(x, y int) int { return x*1000 + y }
func mx_decodeX(v int) int { return v / 1000 }
func mx_decodeY(v int) int { return v % 1000 }""",
                "v := mx_encode(42, 99); fmt.Println(v); fmt.Println(mx_decodeX(v)); fmt.Println(mx_decodeY(v))"),
            RoundTripTestCase("stairs-climb", """
func mx_stairs(n int) int {
    if n <= 1 { return 1 }
    a, b := 1, 1
    for i := 2; i <= n; i++ { a, b = b, a+b }
    return b
}""", "fmt.Println(mx_stairs(0)); fmt.Println(mx_stairs(1)); fmt.Println(mx_stairs(5)); fmt.Println(mx_stairs(10))"),
            RoundTripTestCase("coin-min", """
func mx_coinMin(amount int) int {
    coins := [4]int{25, 10, 5, 1}
    count := 0
    for i := 0; i < 4; i++ { count += amount / coins[i]; amount %= coins[i] }
    return count
}""", "fmt.Println(mx_coinMin(41)); fmt.Println(mx_coinMin(99)); fmt.Println(mx_coinMin(100)); fmt.Println(mx_coinMin(1))"),
            RoundTripTestCase("hamming-distance", """
func mx_hamming(a, b int) int {
    x := a ^ b; c := 0
    for x > 0 { c += x & 1; x >>= 1 }
    return c
}""", "fmt.Println(mx_hamming(0,0)); fmt.Println(mx_hamming(1,4)); fmt.Println(mx_hamming(0xFF,0)); fmt.Println(mx_hamming(7,7))"),
            RoundTripTestCase("gray-code", """
func mx_gray(n int) int { return n ^ (n >> 1) }
func mx_fromGray(g int) int { n := g; for g >>= 1; g != 0; g >>= 1 { n ^= g }; return n }""",
                "for i := 0; i < 8; i++ { g := mx_gray(i); fmt.Println(g, mx_fromGray(g)) }"),
            RoundTripTestCase("zigzag-encode", """
func mx_zigzagEnc(n int) int { if n >= 0 { return 2 * n }; return -2*n - 1 }
func mx_zigzagDec(n int) int { if n%2 == 0 { return n / 2 }; return -(n + 1) / 2 }""",
                "for i := -5; i <= 5; i++ { e := mx_zigzagEnc(i); fmt.Println(e, mx_zigzagDec(e)) }"),
            RoundTripTestCase("xor-checksum", """
func mx_xorCheck(n int) int {
    x := 0
    for i := 0; i < n; i++ { x ^= (i*37 + 13) % 256 }
    return x
}""", "fmt.Println(mx_xorCheck(10)); fmt.Println(mx_xorCheck(100)); fmt.Println(mx_xorCheck(1000))"),
            RoundTripTestCase("matrix-2x2-det", """
func mx_det(a, b, c, d int) int { return a*d - b*c }""",
                "fmt.Println(mx_det(1,2,3,4)); fmt.Println(mx_det(5,0,0,5)); fmt.Println(mx_det(1,0,0,1))"),
            RoundTripTestCase("dot-product", """
func mx_dot(a1, a2, a3, b1, b2, b3 int) int { return a1*b1 + a2*b2 + a3*b3 }""",
                "fmt.Println(mx_dot(1,2,3,4,5,6)); fmt.Println(mx_dot(1,0,0,0,1,0)); fmt.Println(mx_dot(-1,2,-3,4,-5,6))"),
            RoundTripTestCase("manhattan-dist", """
func mx_manhattan(x1, y1, x2, y2 int) int {
    dx := x2 - x1; if dx < 0 { dx = -dx }
    dy := y2 - y1; if dy < 0 { dy = -dy }
    return dx + dy
}""", "fmt.Println(mx_manhattan(0,0,3,4)); fmt.Println(mx_manhattan(1,1,1,1)); fmt.Println(mx_manhattan(-1,-1,1,1))"),
            RoundTripTestCase("polynomial-horner", """
func mx_horner(x int) int {
    c := [5]int{1, -3, 5, -2, 7}
    r := c[0]
    for i := 1; i < 5; i++ { r = r*x + c[i] }
    return r
}""", "fmt.Println(mx_horner(0)); fmt.Println(mx_horner(1)); fmt.Println(mx_horner(2)); fmt.Println(mx_horner(-1))"),
            RoundTripTestCase("base-n-digits", """
func mx_baseDigits(n, base int) int {
    if n == 0 { return 1 }; if n < 0 { n = -n }
    c := 0; for n > 0 { c++; n /= base }; return c
}""", "fmt.Println(mx_baseDigits(0,10)); fmt.Println(mx_baseDigits(255,2)); fmt.Println(mx_baseDigits(255,16)); fmt.Println(mx_baseDigits(1000,10))"),
            RoundTripTestCase("additive-persist", """
func mx_addPersist(n int) int {
    if n < 0 { n = -n }
    steps := 0
    for n >= 10 {
        s := 0; for n > 0 { s += n % 10; n /= 10 }
        n = s; steps++
    }
    return steps
}""", "fmt.Println(mx_addPersist(0)); fmt.Println(mx_addPersist(9)); fmt.Println(mx_addPersist(199)); fmt.Println(mx_addPersist(99999))"),
            RoundTripTestCase("multiplicative-persist", """
func mx_mulPersist(n int) int {
    if n < 10 { return 0 }
    steps := 0
    for n >= 10 {
        p := 1; for n > 0 { p *= n % 10; n /= 10 }
        n = p; steps++
    }
    return steps
}""", "fmt.Println(mx_mulPersist(0)); fmt.Println(mx_mulPersist(10)); fmt.Println(mx_mulPersist(25)); fmt.Println(mx_mulPersist(39)); fmt.Println(mx_mulPersist(77))"),
            RoundTripTestCase("look-and-say-len", """
func mx_lookSayLen(s string) int {
    if len(s) == 0 { return 0 }
    resultLen := 0; i := 0
    for i < len(s) {
        ch := s[i]; count := 0
        for i < len(s) && s[i] == ch { i++; count++ }
        resultLen += 2
    }
    return resultLen
}""", """fmt.Println(mx_lookSayLen("1")); fmt.Println(mx_lookSayLen("11")); fmt.Println(mx_lookSayLen("1211")); fmt.Println(mx_lookSayLen("111221"))"""),
            RoundTripTestCase("water-trapped-simple", """
func mx_water(a, b, c, d, e int) int {
    h := [5]int{a, b, c, d, e}
    total := 0
    for i := 1; i < 4; i++ {
        lMax := h[0]; for j := 0; j < i; j++ { if h[j] > lMax { lMax = h[j] } }
        rMax := h[i+1]; for j := i+1; j < 5; j++ { if h[j] > rMax { rMax = h[j] } }
        mn := lMax; if rMax < mn { mn = rMax }
        if mn > h[i] { total += mn - h[i] }
    }
    return total
}""", "fmt.Println(mx_water(3,0,2,0,4)); fmt.Println(mx_water(1,2,3,4,5)); fmt.Println(mx_water(5,1,1,1,5))"),
        )
        val result = BatchRoundTripRunner.runBatch(cases, builder)
        return cases.map { c -> DynamicTest.dynamicTest(c.name) {
            assertThat(result.reconstructedOutputs[c.name]).isEqualTo(result.originalOutputs[c.name])
        }}
    }
}
