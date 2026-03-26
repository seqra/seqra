package org.opentaint.ir.go.test.roundtrip

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

/** Struct, pointer, and method-like operation tests using standalone functions. */
@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripStructTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            // ── Pointer operations ──────────────────────────────────────
            RoundTripTestCase.withInputs("ptr-deref", """
func rst_ptr_deref(x int) int {
    p := &x
    return *p
}""",
                "rst_ptr_deref(%s)",
                listOf(listOf("42"), listOf("0"), listOf("-7"), listOf("100")),
            ),
            RoundTripTestCase.withInputs("ptr-modify", """
func rst_ptr_modify(x int) int {
    p := &x
    *p = *p + 10
    return x
}""",
                "rst_ptr_modify(%s)",
                listOf(listOf("5"), listOf("0"), listOf("-15"), listOf("100")),
            ),
            RoundTripTestCase.withInputs("ptr-double", """
func rst_ptr_double(x int) int {
    p := &x
    *p *= 2
    return *p
}""",
                "rst_ptr_double(%s)",
                listOf(listOf("3"), listOf("0"), listOf("-4"), listOf("50")),
            ),
            RoundTripTestCase.withInputs("ptr-chain-add", """
func rst_ptr_chain_add(a, b int) int {
    pa := &a
    pb := &b
    *pa += *pb
    return a
}""",
                "rst_ptr_chain_add(%s, %s)",
                listOf(listOf("3", "4"), listOf("0", "0"), listOf("-1", "1"), listOf("100", "200")),
            ),
            RoundTripTestCase.withInputs("ptr-swap-encode", """
func rst_ptr_swap_enc(a, b int) int {
    pa := &a
    pb := &b
    *pa, *pb = *pb, *pa
    return a*100 + b
}""",
                "rst_ptr_swap_enc(%s, %s)",
                listOf(listOf("3", "7"), listOf("1", "2"), listOf("0", "9"), listOf("5", "5")),
                randomRange = 0..99,
            ),
            RoundTripTestCase.withInputs("ptr-accum", """
func rst_ptr_accum(n int) int {
    total := 0
    p := &total
    for i := 1; i <= n; i++ { *p += i }
    return total
}""",
                "rst_ptr_accum(%s)",
                listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10"), listOf("100")),
                randomRange = 0..200,
            ),
            RoundTripTestCase.withInputs("ptr-abs", """
func rst_ptr_abs(x int) int {
    p := &x
    if *p < 0 { *p = -*p }
    return *p
}""",
                "rst_ptr_abs(%s)",
                listOf(listOf("5"), listOf("-3"), listOf("0"), listOf("-100")),
            ),
            RoundTripTestCase.withInputs("ptr-cond-modify", """
func rst_ptr_cond(x int) int {
    p := &x
    if *p > 0 { *p = *p * 2 } else { *p = *p - 1 }
    return *p
}""",
                "rst_ptr_cond(%s)",
                listOf(listOf("5"), listOf("-3"), listOf("0"), listOf("1"), listOf("-1")),
            ),

            // ── Variable swap patterns ──────────────────────────────────
            RoundTripTestCase.withInputs("swap-sum", """
func rst_swap_sum(a, b int) int {
    a, b = b, a
    return a*10 + b
}""",
                "rst_swap_sum(%s, %s)",
                listOf(listOf("3", "7"), listOf("1", "2"), listOf("0", "5"), listOf("9", "9")),
                randomRange = 0..9,
            ),
            RoundTripTestCase.withInputs("swap-three", """
func rst_swap3(a, b, c int) int {
    a, b, c = c, a, b
    return a*10000 + b*100 + c
}""",
                "rst_swap3(%s, %s, %s)",
                listOf(listOf("1", "2", "3"), listOf("9", "8", "7"), listOf("0", "0", "0")),
                randomRange = 0..99,
            ),

            // ── Array-based struct simulation ───────────────────────────
            RoundTripTestCase.withInputs("arr-point-distsq", """
func rst_point_distsq(x1, y1, x2, y2 int) int {
    dx := x2 - x1
    dy := y2 - y1
    return dx*dx + dy*dy
}""",
                "rst_point_distsq(%s, %s, %s, %s)",
                listOf(
                    listOf("0", "0", "3", "4"),
                    listOf("1", "1", "4", "5"),
                    listOf("0", "0", "0", "0"),
                    listOf("-1", "-1", "1", "1"),
                ),
            ),
            RoundTripTestCase.withInputs("arr-rgb-encode", """
func rst_rgb_encode(r, g, b int) int {
    return r*65536 + g*256 + b
}""",
                "rst_rgb_encode(%s, %s, %s)",
                listOf(
                    listOf("255", "0", "0"),
                    listOf("0", "255", "0"),
                    listOf("0", "0", "255"),
                    listOf("128", "128", "128"),
                ),
                randomRange = 0..255,
            ),
            RoundTripTestCase.withInputs("arr-index-lookup", """
func rst_arr_idx(idx int) int {
    arr := [5]int{10, 20, 30, 40, 50}
    if idx < 0 || idx >= 5 { return -1 }
    return arr[idx]
}""",
                "rst_arr_idx(%s)",
                listOf(listOf("0"), listOf("1"), listOf("4"), listOf("-1"), listOf("5")),
                randomRange = -2..6,
            ),
            RoundTripTestCase.withInputs("arr-sum-four", """
func rst_arr_sum4(a, b, c, d int) int {
    arr := [4]int{a, b, c, d}
    s := 0
    for i := 0; i < 4; i++ { s += arr[i] }
    return s
}""",
                "rst_arr_sum4(%s, %s, %s, %s)",
                listOf(
                    listOf("1", "2", "3", "4"),
                    listOf("0", "0", "0", "0"),
                    listOf("-1", "1", "-1", "1"),
                ),
            ),
            RoundTripTestCase.withInputs("arr-max-four", """
func rst_arr_max4(a, b, c, d int) int {
    arr := [4]int{a, b, c, d}
    mx := arr[0]
    for i := 1; i < 4; i++ { if arr[i] > mx { mx = arr[i] } }
    return mx
}""",
                "rst_arr_max4(%s, %s, %s, %s)",
                listOf(
                    listOf("1", "2", "3", "4"),
                    listOf("4", "3", "2", "1"),
                    listOf("5", "5", "5", "5"),
                    listOf("-1", "-2", "-3", "-4"),
                ),
            ),
            RoundTripTestCase.withInputs("arr-pair-encode", """
func rst_pair_enc(x, y int) int { return x*10000 + y }""",
                "rst_pair_enc(%s, %s)",
                listOf(listOf("0", "0"), listOf("1", "2"), listOf("99", "99"), listOf("42", "7")),
                randomRange = 0..9999,
            ),
            RoundTripTestCase.withInputs("arr-triple-encode", """
func rst_triple_enc(a, b, c int) int {
    return a*10000 + b*100 + c
}""",
                "rst_triple_enc(%s, %s, %s)",
                listOf(listOf("1", "2", "3"), listOf("0", "0", "0"), listOf("99", "99", "99")),
                randomRange = 0..99,
            ),

            // ── Accumulator patterns ────────────────────────────────────
            RoundTripTestCase.withInputs("accum-sum-to", """
func rst_accum_sum(n int) int {
    s := 0
    for i := 1; i <= n; i++ { s += i }
    return s
}""",
                "rst_accum_sum(%s)",
                listOf(listOf("0"), listOf("1"), listOf("10"), listOf("100")),
                randomRange = 0..500,
            ),
            RoundTripTestCase.withInputs("accum-even", """
func rst_accum_even(n int) int {
    s := 0
    for i := 1; i <= n; i++ {
        if i%2 == 0 { s += i }
    }
    return s
}""",
                "rst_accum_even(%s)",
                listOf(listOf("0"), listOf("1"), listOf("10"), listOf("100")),
                randomRange = 0..500,
            ),
            RoundTripTestCase.withInputs("accum-odd-squares", """
func rst_accum_odd_sq(n int) int {
    s := 0
    for i := 1; i <= n; i++ {
        if i%2 != 0 { s += i * i }
    }
    return s
}""",
                "rst_accum_odd_sq(%s)",
                listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10"), listOf("20")),
                randomRange = 0..100,
            ),
            RoundTripTestCase.withInputs("accum-threshold", """
func rst_accum_thresh(n, thresh int) int {
    s := 0
    for i := 1; i <= n; i++ {
        v := i * i
        if v > thresh { s += v }
    }
    return s
}""",
                "rst_accum_thresh(%s, %s)",
                listOf(listOf("10", "50"), listOf("5", "0"), listOf("0", "10"), listOf("20", "100")),
                randomRange = 0..100,
            ),

            // ── Nested function calls ───────────────────────────────────
            RoundTripTestCase.withInputs("nested-sq-plus", """
func rst_inner_sq(x int) int { return x * x + 1 }
func rst_outer_sq(x int) int { return rst_inner_sq(x) + rst_inner_sq(x + 1) }""",
                "rst_outer_sq(%s)",
                listOf(listOf("0"), listOf("1"), listOf("5"), listOf("-3")),
            ),
            RoundTripTestCase.withInputs("triple-nested", """
func rst_tn_f1(x int) int { return x + 1 }
func rst_tn_f2(x int) int { return rst_tn_f1(x) * 2 }
func rst_tn_f3(x int) int { return rst_tn_f2(x) + rst_tn_f1(x) }""",
                "rst_tn_f3(%s)",
                listOf(listOf("0"), listOf("5"), listOf("-1"), listOf("10")),
            ),
            RoundTripTestCase.withInputs("nested-step-sum", """
func rst_step(x int) int {
    if x > 0 { return 1 }
    if x < 0 { return -1 }
    return 0
}
func rst_step_sum(a, b, c int) int {
    return rst_step(a) + rst_step(b) + rst_step(c)
}""",
                "rst_step_sum(%s, %s, %s)",
                listOf(
                    listOf("1", "0", "-1"),
                    listOf("5", "5", "5"),
                    listOf("-3", "-3", "-3"),
                    listOf("0", "0", "0"),
                ),
            ),

            // ── Min/max chains ──────────────────────────────────────────
            RoundTripTestCase.withInputs("min-two", """
func rst_min2(a, b int) int {
    if a < b { return a }
    return b
}""",
                "rst_min2(%s, %s)",
                listOf(listOf("3", "7"), listOf("7", "3"), listOf("5", "5"), listOf("-1", "1")),
            ),
            RoundTripTestCase.withInputs("max-chain-three", """
func rst_max2(a, b int) int { if a > b { return a }; return b }
func rst_max_ch3(a, b, c int) int { return rst_max2(rst_max2(a, b), c) }""",
                "rst_max_ch3(%s, %s, %s)",
                listOf(
                    listOf("1", "2", "3"),
                    listOf("3", "2", "1"),
                    listOf("5", "5", "5"),
                    listOf("-1", "-2", "-3"),
                ),
            ),
            RoundTripTestCase.withInputs("min-chain-three", """
func rst_min2b(a, b int) int { if a < b { return a }; return b }
func rst_min_ch3(a, b, c int) int { return rst_min2b(rst_min2b(a, b), c) }""",
                "rst_min_ch3(%s, %s, %s)",
                listOf(
                    listOf("1", "2", "3"),
                    listOf("3", "2", "1"),
                    listOf("5", "5", "5"),
                    listOf("-1", "-2", "-3"),
                ),
            ),
            RoundTripTestCase.withInputs("clamp", """
func rst_clamp(x, lo, hi int) int {
    if x < lo { return lo }
    if x > hi { return hi }
    return x
}""",
                "rst_clamp(%s, %s, %s)",
                listOf(
                    listOf("5", "0", "10"),
                    listOf("-5", "0", "10"),
                    listOf("15", "0", "10"),
                    listOf("0", "0", "0"),
                ),
            ),

            // ── Bit counting / bit manipulation ─────────────────────────
            RoundTripTestCase.withInputs("bit-count", """
func rst_bitcount(n int) int {
    if n < 0 { n = -n }
    c := 0
    for n > 0 { c += n & 1; n >>= 1 }
    return c
}""",
                "rst_bitcount(%s)",
                listOf(listOf("0"), listOf("1"), listOf("7"), listOf("255"), listOf("1024")),
                randomRange = 0..10000,
            ),
            RoundTripTestCase.withInputs("highest-bit", """
func rst_highbit(n int) int {
    if n <= 0 { return -1 }
    pos := 0
    for n > 1 { n >>= 1; pos++ }
    return pos
}""",
                "rst_highbit(%s)",
                listOf(listOf("0"), listOf("1"), listOf("2"), listOf("8"), listOf("255")),
                randomRange = 0..10000,
            ),
            RoundTripTestCase.withInputs("bit-reverse-8", """
func rst_bitrev8(n int) int {
    r := 0
    for i := 0; i < 8; i++ {
        r = (r << 1) | (n & 1)
        n >>= 1
    }
    return r
}""",
                "rst_bitrev8(%s)",
                listOf(listOf("0"), listOf("1"), listOf("128"), listOf("255"), listOf("170")),
                randomRange = 0..255,
            ),
            RoundTripTestCase.withInputs("xor-pair-eq", """
func rst_xor_pair(a, b int) int {
    x := a ^ b
    if x == 0 { return 1 }
    return 0
}""",
                "rst_xor_pair(%s, %s)",
                listOf(listOf("5", "5"), listOf("3", "7"), listOf("0", "0"), listOf("255", "255"), listOf("1", "2")),
            ),

            // ── Digit / number operations ───────────────────────────────
            RoundTripTestCase.withInputs("digit-count", """
func rst_digits(n int) int {
    if n == 0 { return 1 }
    if n < 0 { n = -n }
    c := 0
    for n > 0 { c++; n /= 10 }
    return c
}""",
                "rst_digits(%s)",
                listOf(listOf("0"), listOf("1"), listOf("99"), listOf("1000"), listOf("-42")),
            ),

            // ── Multiple conditionals with early returns ────────────────
            RoundTripTestCase.withInputs("classify-range", """
func rst_classify(x int) int {
    if x < -100 { return -3 }
    if x < -10 { return -2 }
    if x < 0 { return -1 }
    if x == 0 { return 0 }
    if x <= 10 { return 1 }
    if x <= 100 { return 2 }
    return 3
}""",
                "rst_classify(%s)",
                listOf(
                    listOf("-200"), listOf("-50"), listOf("-5"),
                    listOf("0"), listOf("5"), listOf("50"), listOf("200"),
                ),
            ),
            RoundTripTestCase.withInputs("fizzbuzz-code", """
func rst_fizzbuzz(n int) int {
    if n%15 == 0 { return 15 }
    if n%3 == 0 { return 3 }
    if n%5 == 0 { return 5 }
    return n
}""",
                "rst_fizzbuzz(%s)",
                listOf(listOf("1"), listOf("3"), listOf("5"), listOf("15"), listOf("30")),
                randomRange = 1..100,
            ),
            RoundTripTestCase.withInputs("div-classify", """
func rst_div_class(n int) int {
    if n%2 == 0 && n%3 == 0 { return 6 }
    if n%2 == 0 { return 2 }
    if n%3 == 0 { return 3 }
    if n%5 == 0 { return 5 }
    return 1
}""",
                "rst_div_class(%s)",
                listOf(listOf("6"), listOf("4"), listOf("9"), listOf("10"), listOf("7")),
                randomRange = 1..100,
            ),

            // ── Weighted / combined patterns ────────────────────────────
            RoundTripTestCase.withInputs("weighted-sum", """
func rst_weighted(a, b, c int) int {
    return a*1 + b*2 + c*3
}""",
                "rst_weighted(%s, %s, %s)",
                listOf(listOf("1", "1", "1"), listOf("0", "0", "0"), listOf("10", "20", "30"), listOf("-1", "2", "-3")),
            ),
            RoundTripTestCase.withInputs("horner-eval", """
func rst_horner(x, a, b, c, d int) int {
    return ((a*x + b)*x + c)*x + d
}""",
                "rst_horner(%s, %s, %s, %s, %s)",
                listOf(listOf("2", "1", "0", "0", "0"), listOf("3", "1", "2", "3", "4"), listOf("0", "5", "5", "5", "5")),
            ),
        )
        return BatchRoundTripRunner.runBatchAndCreateTests(cases, builder)
    }
}
