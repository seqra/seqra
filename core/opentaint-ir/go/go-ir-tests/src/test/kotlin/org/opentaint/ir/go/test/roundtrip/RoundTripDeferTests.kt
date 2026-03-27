package org.opentaint.ir.go.test.roundtrip

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

/** QI3.6 — Cleanup/error-handling/LIFO patterns using pure functions and multi-return. */
@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripDeferTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase.withInputs("guard-returns", """
func rtd_guard(a, b, c int) int {
    if a < 0 { return -1 }
    if b < 0 { return -2 }
    if c < 0 { return -3 }
    return a*100 + b*10 + c
}""", "rtd_guard(%s, %s, %s)",
                listOf(listOf("1","2","3"), listOf("-1","2","3"), listOf("1","-2","3"), listOf("1","2","-3"), listOf("0","0","0"))),
            RoundTripTestCase.withInputs("error-code-div", """
func rtd_safe_div(a, b int) (int, int) {
    if b == 0 { return 0, -1 }
    return a / b, 0
}
func rtd_safe(a, b int) int {
    r, e := rtd_safe_div(a, b)
    if e != 0 { return -1 }
    return r
}""", "rtd_safe(%s, %s)",
                listOf(listOf("10","2"), listOf("10","0"), listOf("0","1"), listOf("100","3"))),
            RoundTripTestCase.withInputs("validate-range", """
func rtd_validate(x, lo, hi int) (int, int) {
    if x < lo { return lo, 1 }
    if x > hi { return hi, 2 }
    return x, 0
}
func rtd_val_test(x int) int {
    v, code := rtd_validate(x, 0, 100)
    return v*10 + code
}""", "rtd_val_test(%s)",
                listOf(listOf("50"), listOf("-5"), listOf("200"), listOf("0"), listOf("100"))),
            RoundTripTestCase.withInputs("multi-step-check", """
func rtd_step(x, limit int) (int, bool) {
    if x > limit { return limit, false }
    return x, true
}
func rtd_multi_check(a, b, c int) int {
    v1, ok1 := rtd_step(a, 100)
    if !ok1 { return -1 }
    v2, ok2 := rtd_step(b, 50)
    if !ok2 { return -2 }
    v3, ok3 := rtd_step(c, 25)
    if !ok3 { return -3 }
    return v1 + v2 + v3
}""", "rtd_multi_check(%s, %s, %s)",
                listOf(listOf("10","20","5"), listOf("200","20","5"), listOf("10","60","5"), listOf("10","20","30"))),
            RoundTripTestCase.withInputs("transaction-sim", """
func rtd_txn(n int) int {
    balance := 1000
    for i := 0; i < n; i++ {
        amount := (i + 1) * 50
        if balance >= amount { balance -= amount }
    }
    return balance
}""", "rtd_txn(%s)",
                listOf(listOf("0"), listOf("1"), listOf("3"), listOf("5"), listOf("10")), randomRange = 0..20),
            RoundTripTestCase.withInputs("rollback-state", """
func rtd_rollback(n int) int {
    state := 0
    prev := 0
    for i := 0; i < n; i++ {
        prev = state
        state += (i + 1) * 3
    }
    if n > 3 { state = prev }
    return state
}""", "rtd_rollback(%s)",
                listOf(listOf("0"), listOf("1"), listOf("3"), listOf("4"), listOf("5"), listOf("10")), randomRange = 0..20),
            RoundTripTestCase.withInputs("bracket-pattern", """
func rtd_bracket(x int) int {
    acquired := x * 5
    result := acquired + 42
    return result
}""", "rtd_bracket(%s)",
                listOf(listOf("0"), listOf("5"), listOf("10"), listOf("-3"))),
            RoundTripTestCase.withInputs("checkpoint-restore", """
func rtd_checkpoint(n int) int {
    state := 0
    checkpoint := 0
    for i := 0; i < n; i++ {
        if i%5 == 0 { checkpoint = state }
        state += i*i + 1
        if state > 1000 { state = checkpoint; break }
    }
    return state
}""", "rtd_checkpoint(%s)",
                listOf(listOf("0"), listOf("5"), listOf("10"), listOf("20"), listOf("50")), randomRange = 0..100),
            RoundTripTestCase.withInputs("safe-chain", """
func rtd_c1(x int) (int, int) { if x < 0 { return 0, 1 }; return x * 2, 0 }
func rtd_c2(x int) (int, int) { if x > 1000 { return 1000, 2 }; return x + 10, 0 }
func rtd_chain(x int) int {
    v, err := rtd_c1(x)
    if err != 0 { return -err }
    v, err = rtd_c2(v)
    if err != 0 { return -err }
    return v
}""", "rtd_chain(%s)",
                listOf(listOf("5"), listOf("-1"), listOf("0"), listOf("600"), listOf("100"))),
            RoundTripTestCase.withInputs("undo-sim", """
func rtd_undo(ops int) int {
    state := 0
    prev1 := 0
    prev2 := 0
    for i := 0; i < ops; i++ {
        prev2 = prev1
        prev1 = state
        state += i*2 + 1
    }
    if ops > 2 { return prev2 }
    return state
}""", "rtd_undo(%s)",
                listOf(listOf("0"), listOf("1"), listOf("2"), listOf("3"), listOf("5"), listOf("10")), randomRange = 0..20),
            RoundTripTestCase.withInputs("multi-return-check", """
func rtd_parse(x int) (int, int, int) {
    hundreds := x / 100
    tens := (x / 10) % 10
    ones := x % 10
    return hundreds, tens, ones
}
func rtd_parse_test(x int) int {
    h, t, o := rtd_parse(x)
    return h + t + o
}""", "rtd_parse_test(%s)",
                listOf(listOf("123"), listOf("0"), listOf("999"), listOf("456")), randomRange = 0..999),
            RoundTripTestCase.withInputs("accumulate-with-limit", """
func rtd_acc_limit(n, limit int) int {
    total := 0
    for i := 1; i <= n; i++ {
        next := total + i
        if next > limit { break }
        total = next
    }
    return total
}""", "rtd_acc_limit(%s, %s)",
                listOf(listOf("10","100"), listOf("10","10"), listOf("0","100"), listOf("100","500")), randomRange = 1..200),
        )
        return BatchRoundTripRunner.runBatchAndCreateTests(cases, builder)
    }
}
