package org.opentaint.ir.go.test.roundtrip

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

/** QI3.2 — Array-based collection patterns (fixed-size arrays only, no dynamic slices/maps). */
@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripCollectionTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase.withInputs("arr-sum-6", """
func rtc_sum6(a, b, c, d, e, f int) int {
    arr := [6]int{a, b, c, d, e, f}
    s := 0
    for i := 0; i < 6; i++ { s += arr[i] }
    return s
}""", "rtc_sum6(%s, %s, %s, %s, %s, %s)",
                listOf(listOf("1","2","3","4","5","6"), listOf("0","0","0","0","0","0")), argCount = 6),
            RoundTripTestCase.withInputs("arr-product-4", """
func rtc_prod4(a, b, c, d int) int {
    arr := [4]int{a, b, c, d}
    p := 1
    for i := 0; i < 4; i++ { p *= arr[i] }
    return p
}""", "rtc_prod4(%s, %s, %s, %s)",
                listOf(listOf("1","2","3","4"), listOf("1","1","1","1"), listOf("2","2","2","2"))),
            RoundTripTestCase.withInputs("arr-count-positive", """
func rtc_count_pos(a, b, c, d, e int) int {
    arr := [5]int{a, b, c, d, e}
    c_ := 0
    for i := 0; i < 5; i++ { if arr[i] > 0 { c_++ } }
    return c_
}""", "rtc_count_pos(%s, %s, %s, %s, %s)",
                listOf(listOf("1","-2","3","-4","5"), listOf("0","0","0","0","0"), listOf("-1","-2","-3","-4","-5"))),
            RoundTripTestCase.withInputs("arr-second-largest", """
func rtc_second(a, b, c, d int) int {
    arr := [4]int{a, b, c, d}
    for i := 0; i < 3; i++ { for j := i+1; j < 4; j++ { if arr[i] > arr[j] { arr[i], arr[j] = arr[j], arr[i] } } }
    return arr[2]
}""", "rtc_second(%s, %s, %s, %s)",
                listOf(listOf("4","2","7","1"), listOf("5","5","5","5"), listOf("1","2","3","4"))),
            RoundTripTestCase.withInputs("arr-rotate-left", """
func rtc_rotate(a, b, c, d, e int) int {
    arr := [5]int{a, b, c, d, e}
    first := arr[0]
    for i := 0; i < 4; i++ { arr[i] = arr[i+1] }
    arr[4] = first
    enc := 0
    for i := 0; i < 5; i++ { enc = enc*10 + arr[i] }
    return enc
}""", "rtc_rotate(%s, %s, %s, %s, %s)",
                listOf(listOf("1","2","3","4","5"), listOf("5","4","3","2","1")), randomRange = 0..9),
            RoundTripTestCase.withInputs("arr-reverse-4", """
func rtc_rev4(a, b, c, d int) int {
    arr := [4]int{a, b, c, d}
    arr[0], arr[3] = arr[3], arr[0]
    arr[1], arr[2] = arr[2], arr[1]
    return arr[0]*1000 + arr[1]*100 + arr[2]*10 + arr[3]
}""", "rtc_rev4(%s, %s, %s, %s)",
                listOf(listOf("1","2","3","4"), listOf("0","0","0","0"), listOf("9","8","7","6")), randomRange = 0..9),
            RoundTripTestCase.withInputs("arr-even-sum-odd-count", """
func rtc_even_odd(a, b, c, d, e, f int) int {
    arr := [6]int{a, b, c, d, e, f}
    eSum, oCount := 0, 0
    for i := 0; i < 6; i++ { if arr[i]%2 == 0 { eSum += arr[i] } else { oCount++ } }
    return eSum*100 + oCount
}""", "rtc_even_odd(%s, %s, %s, %s, %s, %s)",
                listOf(listOf("1","2","3","4","5","6"), listOf("2","4","6","8","10","12")), argCount = 6),
            RoundTripTestCase.withInputs("arr-weighted-sum", """
func rtc_weighted(a, b, c, d int) int {
    vals := [4]int{a, b, c, d}
    weights := [4]int{1, 2, 3, 4}
    s := 0
    for i := 0; i < 4; i++ { s += vals[i] * weights[i] }
    return s
}""", "rtc_weighted(%s, %s, %s, %s)",
                listOf(listOf("1","1","1","1"), listOf("0","0","0","0"), listOf("10","20","30","40"))),
            RoundTripTestCase.withInputs("arr-adjacent-diff", """
func rtc_adj_diff(a, b, c, d, e int) int {
    arr := [5]int{a, b, c, d, e}
    s := 0
    for i := 0; i < 4; i++ {
        d_ := arr[i+1] - arr[i]
        if d_ < 0 { d_ = -d_ }
        s += d_
    }
    return s
}""", "rtc_adj_diff(%s, %s, %s, %s, %s)",
                listOf(listOf("1","3","2","5","4"), listOf("1","1","1","1","1"), listOf("5","4","3","2","1"))),
            RoundTripTestCase.withInputs("arr-running-max", """
func rtc_run_max(a, b, c, d, e int) int {
    arr := [5]int{a, b, c, d, e}
    mx := arr[0]
    s := mx
    for i := 1; i < 5; i++ { if arr[i] > mx { mx = arr[i] }; s += mx }
    return s
}""", "rtc_run_max(%s, %s, %s, %s, %s)",
                listOf(listOf("1","5","3","7","2"), listOf("5","4","3","2","1"), listOf("1","2","3","4","5"))),
            RoundTripTestCase.withInputs("arr-prefix-xor", """
func rtc_pxor(a, b, c, d int) int {
    arr := [4]int{a, b, c, d}
    for i := 1; i < 4; i++ { arr[i] ^= arr[i-1] }
    return arr[3]
}""", "rtc_pxor(%s, %s, %s, %s)",
                listOf(listOf("1","2","3","4"), listOf("0","0","0","0"), listOf("15","7","3","1"))),
            RoundTripTestCase.withInputs("arr-merge-encode", """
func rtc_merge_enc(a1, a2, a3, b1, b2, b3 int) int {
    a := [3]int{a1, a2, a3}
    b := [3]int{b1, b2, b3}
    enc := 0
    for i := 0; i < 3; i++ { enc = enc*100 + a[i]*10 + b[i] }
    return enc
}""", "rtc_merge_enc(%s, %s, %s, %s, %s, %s)",
                listOf(listOf("1","2","3","4","5","6"), listOf("0","0","0","0","0","0")), argCount = 6, randomRange = 0..9),
        )
        return BatchRoundTripRunner.runBatchAndCreateTests(cases, builder)
    }
}
