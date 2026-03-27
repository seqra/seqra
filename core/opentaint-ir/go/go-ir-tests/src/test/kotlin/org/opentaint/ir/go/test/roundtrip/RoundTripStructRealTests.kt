package org.opentaint.ir.go.test.roundtrip

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

/** Tests that the IR correctly round-trips struct types, field access, and methods. */
@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripStructRealTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase.withInputs("struct-create-field", """
type rtsr_Point struct { X int; Y int }
func rtsr_create(a, b int) int {
    p := rtsr_Point{X: a, Y: b}
    return p.X + p.Y
}""", "rtsr_create(%s, %s)", listOf(listOf("3","4"), listOf("0","0"), listOf("-1","1"), listOf("100","200"))),

            RoundTripTestCase.withInputs("struct-ptr-field-modify", """
type rtsr_Box struct { Val int }
func rtsr_ptr_field(x int) int {
    b := &rtsr_Box{Val: x}
    b.Val *= 3
    return b.Val
}""", "rtsr_ptr_field(%s)", listOf(listOf("5"), listOf("0"), listOf("-2"), listOf("33"))),

            RoundTripTestCase.withInputs("struct-nested", """
type rtsr_Inner struct { V int }
type rtsr_Outer struct { In rtsr_Inner; Extra int }
func rtsr_nested(a, b int) int {
    o := rtsr_Outer{In: rtsr_Inner{V: a}, Extra: b}
    return o.In.V + o.Extra
}""", "rtsr_nested(%s, %s)", listOf(listOf("1","2"), listOf("10","20"), listOf("-5","5"), listOf("0","0"))),

            RoundTripTestCase.withInputs("struct-method-value-recv", """
type rtsr_Counter struct { N int }
func rtsr_inc(c *rtsr_Counter, delta int) { c.N += delta }
func rtsr_method_test(n int) int {
    c := &rtsr_Counter{N: 0}
    for i := 0; i < n; i++ { rtsr_inc(c, i+1) }
    return c.N
}""", "rtsr_method_test(%s)", listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10")), randomRange = 0..50),

            RoundTripTestCase.withInputs("struct-pass-by-value", """
type rtsr_Pair struct { A int; B int }
func rtsr_swap_encode(x, y int) int {
    p := rtsr_Pair{A: x, B: y}
    p.A, p.B = p.B, p.A
    return p.A*100 + p.B
}""", "rtsr_swap_encode(%s, %s)", listOf(listOf("3","7"), listOf("1","1"), listOf("0","99")), randomRange = 0..99),

            RoundTripTestCase.withInputs("struct-slice-field", """
type rtsr_Bag struct { Items []int }
func rtsr_bag_total(n int) int {
    b := rtsr_Bag{Items: make([]int, n)}
    for i := 0; i < n; i++ { b.Items[i] = i * 2 }
    total := 0
    for i := 0; i < n; i++ { total += b.Items[i] }
    return total
}""", "rtsr_bag_total(%s)", listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10")), randomRange = 0..30),
        )
        return BatchRoundTripRunner.runBatchAndCreateTests(cases, builder)
    }
}
