package org.opentaint.ir.go.test.roundtrip

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

/** Tests that the IR correctly round-trips closures, interfaces, and type assertions. */
@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripClosureIfaceRealTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase.withInputs("closure-capture-read", """
func rtci_cap(x int) int {
    add := func(y int) int { return x + y }
    return add(10) + add(20)
}""", "rtci_cap(%s)", listOf(listOf("5"), listOf("0"), listOf("-3"), listOf("100"))),

            RoundTripTestCase.withInputs("closure-capture-write", """
func rtci_write(x int) int {
    sum := 0
    add := func(v int) { sum += v }
    add(x)
    add(x * 2)
    return sum
}""", "rtci_write(%s)", listOf(listOf("1"), listOf("0"), listOf("5"), listOf("10"))),

            RoundTripTestCase.withInputs("closure-counter", """
func rtci_counter(n int) int {
    count := 0
    inc := func() { count++ }
    for i := 0; i < n; i++ { inc() }
    return count
}""", "rtci_counter(%s)", listOf(listOf("0"), listOf("1"), listOf("5"), listOf("100")), randomRange = 0..200),

            RoundTripTestCase.withInputs("closure-return-func", """
func rtci_adder(base int) func(int) int {
    return func(x int) int { return base + x }
}
func rtci_adder_test(a, b int) int {
    f := rtci_adder(a)
    return f(b)
}""", "rtci_adder_test(%s, %s)", listOf(listOf("10","5"), listOf("0","0"), listOf("-3","3"))),

            RoundTripTestCase.withInputs("iface-basic", """
type rtci_Sizer interface { Size() int }
type rtci_Box struct { W, H int }
func (b rtci_Box) Size() int { return b.W * b.H }
func rtci_iface(w, h int) int {
    var s rtci_Sizer = rtci_Box{W: w, H: h}
    return s.Size()
}""", "rtci_iface(%s, %s)", listOf(listOf("3","4"), listOf("0","5"), listOf("7","1"), listOf("10","10"))),

            RoundTripTestCase.withInputs("iface-type-assert", """
type rtci_Animal interface { Legs() int }
type rtci_Dog struct{}
func (rtci_Dog) Legs() int { return 4 }
type rtci_Snake struct{}
func (rtci_Snake) Legs() int { return 0 }
func rtci_assert(useDog bool) int {
    var a rtci_Animal
    if useDog { a = rtci_Dog{} } else { a = rtci_Snake{} }
    _, ok := a.(rtci_Dog)
    if ok { return 1 }
    return 0
}
func rtci_assert_test(x int) int {
    return rtci_assert(x > 0)*10 + rtci_assert(x <= 0)
}""", "rtci_assert_test(%s)", listOf(listOf("1"), listOf("0"), listOf("-5"), listOf("10"))),

            RoundTripTestCase.withInputs("iface-polymorphism", """
type rtci_Eval interface { Eval(x int) int }
type rtci_Double struct{}
func (rtci_Double) Eval(x int) int { return x * 2 }
type rtci_Square struct{}
func (rtci_Square) Eval(x int) int { return x * x }
func rtci_poly(useDouble bool, x int) int {
    var e rtci_Eval
    if useDouble { e = rtci_Double{} } else { e = rtci_Square{} }
    return e.Eval(x)
}
func rtci_poly_test(x int) int {
    return rtci_poly(true, x)*1000 + rtci_poly(false, x)
}""", "rtci_poly_test(%s)", listOf(listOf("3"), listOf("0"), listOf("5"), listOf("10")), randomRange = 0..30),

            RoundTripTestCase.withInputs("recursive-closure", """
func rtci_fib(n int) int {
    var fib func(int) int
    fib = func(x int) int {
        if x <= 1 { return x }
        return fib(x-1) + fib(x-2)
    }
    return fib(n)
}""", "rtci_fib(%s)", listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10")), randomRange = 0..20),
        )
        return BatchRoundTripRunner.runBatchAndCreateTests(cases, builder)
    }
}
