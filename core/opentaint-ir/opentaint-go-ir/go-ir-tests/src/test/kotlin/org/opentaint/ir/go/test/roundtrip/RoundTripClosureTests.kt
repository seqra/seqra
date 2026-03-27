package org.opentaint.ir.go.test.roundtrip

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

/** QI3.4 — Higher-order patterns: multi-function composition, callback simulation, currying via helpers. */
@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripClosureTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase.withInputs("double-apply", """
func rtcl_inc(x int) int { return x + 1 }
func rtcl_dbl(x int) int { return x * 2 }
func rtcl_double_apply(x int) int { return rtcl_dbl(rtcl_inc(x)) }""",
                "rtcl_double_apply(%s)", listOf(listOf("3"), listOf("0"), listOf("-5"), listOf("10"))),
            RoundTripTestCase.withInputs("triple-compose", """
func rtcl_a(x int) int { return x + 3 }
func rtcl_b(x int) int { return x * 2 }
func rtcl_c(x int) int { return x - 1 }
func rtcl_triple(x int) int { return rtcl_c(rtcl_b(rtcl_a(x))) }""",
                "rtcl_triple(%s)", listOf(listOf("0"), listOf("5"), listOf("10"), listOf("-3"))),
            RoundTripTestCase.withInputs("apply-n-inc", """
func rtcl_apply_n(n, x int) int {
    for i := 0; i < n; i++ { x += 1 }
    return x
}""", "rtcl_apply_n(%s, %s)", listOf(listOf("0","5"), listOf("3","0"), listOf("10","100"), listOf("1","-5"))),
            RoundTripTestCase.withInputs("apply-n-double", """
func rtcl_dbl_n(n, x int) int {
    for i := 0; i < n; i++ { x *= 2 }
    return x
}""", "rtcl_dbl_n(%s, %s)", listOf(listOf("0","5"), listOf("3","1"), listOf("5","2"), listOf("10","1")),
                randomRange = 0..15),
            RoundTripTestCase.withInputs("alternate-ops", """
func rtcl_alt(n, x int) int {
    for i := 0; i < n; i++ {
        if i%2 == 0 { x += 3 } else { x *= 2 }
    }
    return x
}""", "rtcl_alt(%s, %s)", listOf(listOf("0","1"), listOf("4","1"), listOf("6","0"), listOf("3","5")),
                randomRange = 0..15),
            RoundTripTestCase.withInputs("conditional-compose", """
func rtcl_step1(cond, x int) int { if cond > 0 { return x + 10 } else { return x - 10 } }
func rtcl_step2(cond, x int) int { if cond > 0 { return x * 3 } else { return x * 2 } }
func rtcl_cond_comp(c1, c2, x int) int { return rtcl_step2(c2, rtcl_step1(c1, x)) }""",
                "rtcl_cond_comp(%s, %s, %s)", listOf(listOf("1","1","5"), listOf("0","0","5"), listOf("1","0","5"), listOf("0","1","5"))),
            RoundTripTestCase.withInputs("curried-add", """
func rtcl_add_base(base, x int) int { return base + x }
func rtcl_add10(x int) int { return rtcl_add_base(10, x) }
func rtcl_add20(x int) int { return rtcl_add_base(20, x) }
func rtcl_curry_test(x int) int { return rtcl_add10(x)*100 + rtcl_add20(x) }""",
                "rtcl_curry_test(%s)", listOf(listOf("0"), listOf("5"), listOf("-3"), listOf("50")),
                randomRange = 0..80),
            RoundTripTestCase.withInputs("selector", """
func rtcl_op0(x int) int { return x + 100 }
func rtcl_op1(x int) int { return x * x }
func rtcl_op2(x int) int { return x - 50 }
func rtcl_select(sel, x int) int {
    s := sel % 3; if s < 0 { s += 3 }
    if s == 0 { return rtcl_op0(x) }
    if s == 1 { return rtcl_op1(x) }
    return rtcl_op2(x)
}""", "rtcl_select(%s, %s)", listOf(listOf("0","5"), listOf("1","4"), listOf("2","70"), listOf("-1","3"))),
            RoundTripTestCase.withInputs("chain-n-steps", """
func rtcl_chain_step(step, x int) int {
    s := step % 4; if s < 0 { s += 4 }
    if s == 0 { return x + 1 }
    if s == 1 { return x * 2 }
    if s == 2 { return x - 3 }
    return x / 2
}
func rtcl_chain_n(n, x int) int {
    for i := 0; i < n; i++ { x = rtcl_chain_step(i, x) }
    return x
}""", "rtcl_chain_n(%s, %s)", listOf(listOf("0","10"), listOf("4","5"), listOf("8","1"), listOf("3","100")),
                randomRange = 0..15),
            RoundTripTestCase.withInputs("multi-ret-compose", """
func rtcl_split(x int) (int, int) { return x / 10, x % 10 }
func rtcl_combine(a, b int) int { return a * b + a + b }
func rtcl_mrc_test(x int) int {
    a, b := rtcl_split(x)
    return rtcl_combine(a, b)
}""", "rtcl_mrc_test(%s)", listOf(listOf("0"), listOf("42"), listOf("99"), listOf("73"))),
            RoundTripTestCase.withInputs("mutual-step", """
func rtcl_even_step(n int) int { if n <= 0 { return 0 }; return n + rtcl_odd_step(n-1) }
func rtcl_odd_step(n int) int { if n <= 0 { return 1 }; return n * 2 + rtcl_even_step(n-1) }
func rtcl_mutual(n int) int { return rtcl_even_step(n) }""",
                "rtcl_mutual(%s)", listOf(listOf("0"), listOf("1"), listOf("3"), listOf("5"), listOf("8")),
                randomRange = 0..15),
            RoundTripTestCase.withInputs("transform-pair", """
func rtcl_transform(op, a, b int) (int, int) {
    if op == 0 { return a + b, a - b }
    if op == 1 { return a * b, a + b }
    return a - b, a * b
}
func rtcl_tp_test(a, b int) int {
    x, y := rtcl_transform(0, a, b)
    p, q := rtcl_transform(1, x, y)
    return p + q
}""", "rtcl_tp_test(%s, %s)", listOf(listOf("5","3"), listOf("0","0"), listOf("10","7"), listOf("1","1"))),
        )
        return BatchRoundTripRunner.runBatchAndCreateTests(cases, builder)
    }
}
