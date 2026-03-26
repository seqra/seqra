package org.opentaint.ir.go.test.roundtrip

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripEdgeCaseTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase("zero-args", """func ec_zero() int { return 42 }""",
                "fmt.Println(ec_zero()); fmt.Println(ec_zero())"),
            RoundTripTestCase("identity", """func ec_id(x int) int { return x }""",
                "fmt.Println(ec_id(0)); fmt.Println(ec_id(1)); fmt.Println(ec_id(-1)); fmt.Println(ec_id(999999))"),
            RoundTripTestCase("always-true", """func ec_alwaysTrue() bool { return true }""",
                "fmt.Println(ec_alwaysTrue())"),
            RoundTripTestCase("always-false", """func ec_alwaysFalse() bool { return false }""",
                "fmt.Println(ec_alwaysFalse())"),
            RoundTripTestCase("const-expr", """func ec_const() int { return 2*3 + 4*5 }""",
                "fmt.Println(ec_const())"),
            RoundTripTestCase("many-params", """func ec_many(a, b, c, d, e int) int { return a + b + c + d + e }""",
                "fmt.Println(ec_many(1,2,3,4,5)); fmt.Println(ec_many(0,0,0,0,0)); fmt.Println(ec_many(-1,-2,-3,-4,-5))"),
            RoundTripTestCase("deeply-nested-arith", """
func ec_deep(x int) int { return ((((x+1)*2)-3)*4)+5 }""",
                "fmt.Println(ec_deep(0)); fmt.Println(ec_deep(1)); fmt.Println(ec_deep(10))"),
            RoundTripTestCase("large-chain", """
func ec_chain(x int) int {
    x = x + 1; x = x * 2; x = x - 3; x = x / 2; x = x + 10
    x = x * 3; x = x - 7; x = x + 1; x = x * 2; x = x - 5
    return x
}""", "fmt.Println(ec_chain(0)); fmt.Println(ec_chain(5)); fmt.Println(ec_chain(10))"),
            RoundTripTestCase("many-conditions", """
func ec_manyCond(x int) int {
    if x == 1 { return 10 }
    if x == 2 { return 20 }
    if x == 3 { return 30 }
    if x == 4 { return 40 }
    if x == 5 { return 50 }
    if x == 6 { return 60 }
    if x == 7 { return 70 }
    if x == 8 { return 80 }
    if x == 9 { return 90 }
    if x == 10 { return 100 }
    return 0
}""", "for i := 0; i <= 11; i++ { fmt.Println(ec_manyCond(i)) }"),
            RoundTripTestCase("triple-nested-loop", """
func ec_tripleLoop(n int) int {
    s := 0
    for i := 0; i < n; i++ {
        for j := 0; j < n; j++ {
            for k := 0; k < n; k++ { s++ }
        }
    }
    return s
}""", "fmt.Println(ec_tripleLoop(1)); fmt.Println(ec_tripleLoop(3)); fmt.Println(ec_tripleLoop(5))"),
            RoundTripTestCase("alternating-sum", """
func ec_altSum(n int) int {
    s := 0; sign := 1
    for i := 1; i <= n; i++ { s += sign * i * i; sign = -sign }
    return s
}""", "fmt.Println(ec_altSum(1)); fmt.Println(ec_altSum(5)); fmt.Println(ec_altSum(10))"),
            RoundTripTestCase("cascade-if", """
func ec_cascade(a, b, c, d int) int {
    r := 0
    if a > 0 { r += 1 }
    if b > 0 { r += 2 }
    if c > 0 { r += 4 }
    if d > 0 { r += 8 }
    return r
}""", "fmt.Println(ec_cascade(1,1,1,1)); fmt.Println(ec_cascade(0,0,0,0)); fmt.Println(ec_cascade(1,0,1,0)); fmt.Println(ec_cascade(0,1,0,1))"),
            RoundTripTestCase("long-loop", """
func ec_longLoop(n int) int {
    s := 0; for i := 0; i < n; i++ { s += i % 7 }; return s
}""", "fmt.Println(ec_longLoop(100)); fmt.Println(ec_longLoop(1000)); fmt.Println(ec_longLoop(0))"),
            RoundTripTestCase("mutual-calls", """
func ec_f1(x int) int { if x <= 0 { return 0 }; return x + ec_f2(x-1) }
func ec_f2(x int) int { if x <= 0 { return 1 }; return x * ec_f1(x-1) }""",
                "fmt.Println(ec_f1(5)); fmt.Println(ec_f2(5)); fmt.Println(ec_f1(0)); fmt.Println(ec_f2(0))"),
            RoundTripTestCase("five-funcs", """
func ec_a(x int) int { return x + 1 }
func ec_b(x int) int { return ec_a(x) * 2 }
func ec_c(x int) int { return ec_b(x) - 3 }
func ec_d(x int) int { return ec_c(x) + ec_a(x) }
func ec_e(x int) int { return ec_d(x) * ec_b(x) }""",
                "fmt.Println(ec_e(1)); fmt.Println(ec_e(5)); fmt.Println(ec_e(0))"),
            RoundTripTestCase("bool-chain", """
func ec_boolChain(a, b, c, d bool) int {
    r := 0
    if a { r += 1 }; if b { r += 2 }; if c { r += 4 }; if d { r += 8 }
    return r
}""", "fmt.Println(ec_boolChain(true,false,true,false)); fmt.Println(ec_boolChain(false,false,false,false)); fmt.Println(ec_boolChain(true,true,true,true))"),
            RoundTripTestCase("negative-modulo", """
func ec_negMod(a, m int) int {
    r := a % m
    if r < 0 { r += m }
    return r
}""", "fmt.Println(ec_negMod(7,3)); fmt.Println(ec_negMod(-7,3)); fmt.Println(ec_negMod(0,5))"),
            RoundTripTestCase("saturating-add", """
func ec_satAdd(a, b, max int) int {
    s := a + b
    if s > max { return max }
    if s < -max { return -max }
    return s
}""", "fmt.Println(ec_satAdd(50,60,100)); fmt.Println(ec_satAdd(50,30,100)); fmt.Println(ec_satAdd(-50,-60,100))"),
            RoundTripTestCase("wrapping-add", """
func ec_wrapAdd(a, b, mod int) int { return ((a + b) % mod + mod) % mod }""",
                "fmt.Println(ec_wrapAdd(3,4,10)); fmt.Println(ec_wrapAdd(8,5,10)); fmt.Println(ec_wrapAdd(-3,1,10))"),
            RoundTripTestCase("step-function", """
func ec_step(x, threshold int) int { if x >= threshold { return 1 }; return 0 }""",
                "fmt.Println(ec_step(5,3)); fmt.Println(ec_step(3,3)); fmt.Println(ec_step(2,3)); fmt.Println(ec_step(0,0))"),
            RoundTripTestCase("linear-interp", """
func ec_lerp(a, b, t int) int { return a + (b-a)*t/100 }""",
                "fmt.Println(ec_lerp(0,100,50)); fmt.Println(ec_lerp(0,100,0)); fmt.Println(ec_lerp(0,100,100)); fmt.Println(ec_lerp(10,20,75))"),
            RoundTripTestCase("parity-check", """
func ec_parity(x int) int {
    p := 0; if x < 0 { x = -x }
    for x > 0 { p ^= x & 1; x >>= 1 }
    return p
}""", "fmt.Println(ec_parity(0)); fmt.Println(ec_parity(1)); fmt.Println(ec_parity(3)); fmt.Println(ec_parity(7)); fmt.Println(ec_parity(255))"),
        )
        return BatchRoundTripRunner.runBatchAndCreateTests(cases, builder)
    }
}
