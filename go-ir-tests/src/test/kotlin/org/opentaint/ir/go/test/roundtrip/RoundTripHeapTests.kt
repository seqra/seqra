package org.opentaint.ir.go.test.roundtrip

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

/** QI3.1 — Heap manipulation: pointers, arrays, slices. No struct types (codegen limitation). */
@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripHeapTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase.withInputs("ptr-deref",
                """
func rth_ptr_deref(x int) int {
    p := &x
    return *p
}""",
                "rth_ptr_deref(%s)",
                listOf(listOf("42"), listOf("0"), listOf("-7"), listOf("100")),
            ),
            RoundTripTestCase.withInputs("ptr-modify",
                """
func rth_ptr_modify(x int) int {
    p := &x
    *p = *p + 10
    return x
}""",
                "rth_ptr_modify(%s)",
                listOf(listOf("5"), listOf("0"), listOf("-15"), listOf("100")),
            ),
            RoundTripTestCase.withInputs("ptr-double",
                """
func rth_ptr_double(x int) int {
    p := &x
    *p *= 2
    return *p
}""",
                "rth_ptr_double(%s)",
                listOf(listOf("3"), listOf("0"), listOf("-4"), listOf("50")),
            ),
            RoundTripTestCase.withInputs("ptr-chain-add",
                """
func rth_ptr_chain_add(a, b int) int {
    pa := &a
    pb := &b
    *pa += *pb
    return a
}""",
                "rth_ptr_chain_add(%s, %s)",
                listOf(listOf("3", "4"), listOf("0", "0"), listOf("-1", "1"), listOf("100", "200")),
            ),
            RoundTripTestCase.withInputs("ptr-accum-loop",
                """
func rth_ptr_accum(n int) int {
    total := 0
    p := &total
    for i := 1; i <= n; i++ { *p += i }
    return total
}""",
                "rth_ptr_accum(%s)",
                listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10"), listOf("100")),
                randomRange = 0..200,
            ),
            RoundTripTestCase.withInputs("ptr-abs",
                """
func rth_ptr_abs(x int) int {
    p := &x
    if *p < 0 { *p = -*p }
    return *p
}""",
                "rth_ptr_abs(%s)",
                listOf(listOf("5"), listOf("0"), listOf("-7"), listOf("-100")),
            ),
            RoundTripTestCase.withInputs("ptr-sq-sum",
                """
func rth_ptr_sq_sum(n int) int {
    total := 0
    p := &total
    for i := 1; i <= n; i++ { *p += i * i }
    return total
}""",
                "rth_ptr_sq_sum(%s)",
                listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10")),
                randomRange = 0..100,
            ),
            RoundTripTestCase.withInputs("array-sum-3",
                """
func rth_array_sum(a, b, c int) int {
    arr := [3]int{a, b, c}
    sum := 0
    for i := 0; i < 3; i++ { sum += arr[i] }
    return sum
}""",
                "rth_array_sum(%s, %s, %s)",
                listOf(listOf("1", "2", "3"), listOf("0", "0", "0"), listOf("-1", "1", "0"), listOf("100", "200", "300")),
            ),
            RoundTripTestCase.withInputs("array-reverse-encode",
                """
func rth_rev_enc(a, b, c, d int) int {
    arr := [4]int{a, b, c, d}
    sum := 0
    for i := 3; i >= 0; i-- { sum = sum*10 + arr[i] }
    return sum
}""",
                "rth_rev_enc(%s, %s, %s, %s)",
                listOf(listOf("1", "2", "3", "4"), listOf("0", "0", "0", "0"), listOf("9", "8", "7", "6")),
            ),
            RoundTripTestCase.withInputs("array-fill-sum",
                """
func rth_fill_sum(x int) int {
    arr := [5]int{0, 0, 0, 0, 0}
    for i := 0; i < 5; i++ { arr[i] = x * (i + 1) }
    total := 0
    for i := 0; i < 5; i++ { total += arr[i] }
    return total
}""",
                "rth_fill_sum(%s)",
                listOf(listOf("1"), listOf("3"), listOf("0"), listOf("10")),
            ),
            RoundTripTestCase.withInputs("array-max-5",
                """
func rth_arr_max(a, b, c, d, e int) int {
    arr := [5]int{a, b, c, d, e}
    mx := arr[0]
    for i := 1; i < 5; i++ { if arr[i] > mx { mx = arr[i] } }
    return mx
}""",
                "rth_arr_max(%s, %s, %s, %s, %s)",
                listOf(listOf("1", "5", "3", "4", "2"), listOf("10", "10", "10", "10", "10"), listOf("-1", "-5", "-3", "-4", "-2")),
            ),
            RoundTripTestCase.withInputs("array-dot-product",
                """
func rth_dot3(a1, a2, a3, b1, b2, b3 int) int {
    aa := [3]int{a1, a2, a3}
    bb := [3]int{b1, b2, b3}
    sum := 0
    for i := 0; i < 3; i++ { sum += aa[i] * bb[i] }
    return sum
}""",
                "rth_dot3(%s, %s, %s, %s, %s, %s)",
                listOf(listOf("1", "0", "0", "1", "0", "0"), listOf("1", "2", "3", "4", "5", "6")),
                argCount = 6,
                randomRange = -10..10,
            ),
            RoundTripTestCase.withInputs("array-min-max-diff",
                """
func rth_minmax_diff(a, b, c, d int) int {
    arr := [4]int{a, b, c, d}
    mn, mx := arr[0], arr[0]
    for i := 1; i < 4; i++ {
        if arr[i] < mn { mn = arr[i] }
        if arr[i] > mx { mx = arr[i] }
    }
    return mx - mn
}""",
                "rth_minmax_diff(%s, %s, %s, %s)",
                listOf(listOf("1", "5", "3", "7"), listOf("0", "0", "0", "0"), listOf("-3", "2", "-1", "4")),
            ),
            RoundTripTestCase.withInputs("ptr-cond-modify",
                """
func rth_cond_mod(x int) int {
    p := &x
    if *p > 0 { *p *= 3 } else { *p = -*p + 5 }
    return *p
}""",
                "rth_cond_mod(%s)",
                listOf(listOf("5"), listOf("0"), listOf("-3"), listOf("10"), listOf("-10")),
            ),
            RoundTripTestCase.withInputs("array-bubble-pass",
                """
func rth_bubble(a, b, c, d int) int {
    arr := [4]int{a, b, c, d}
    for i := 0; i < 3; i++ {
        if arr[i] > arr[i+1] { arr[i], arr[i+1] = arr[i+1], arr[i] }
    }
    return arr[0]*1000 + arr[1]*100 + arr[2]*10 + arr[3]
}""",
                "rth_bubble(%s, %s, %s, %s)",
                listOf(listOf("4", "3", "2", "1"), listOf("1", "2", "3", "4"), listOf("3", "1", "4", "2")),
                randomRange = 0..9,
            ),
        )
        return BatchRoundTripRunner.runBatchAndCreateTests(cases, builder)
    }
}
