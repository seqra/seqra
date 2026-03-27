package org.opentaint.ir.go.test.roundtrip

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

/** QI3.3 — Dispatch/strategy patterns using tag-based dispatch and multi-function calls. */
@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripInterfaceTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase.withInputs("tag-dispatch-2", """
func rti_op(tag, a, b int) int {
    if tag == 0 { return a + b }
    if tag == 1 { return a - b }
    return a * b
}""", "rti_op(%s, %s, %s)", listOf(listOf("0","3","4"), listOf("1","10","3"), listOf("2","5","6"))),
            RoundTripTestCase.withInputs("tag-dispatch-5", """
func rti_op5(tag, x int) int {
    if tag == 0 { return x }
    if tag == 1 { return x * 2 }
    if tag == 2 { return x * x }
    if tag == 3 { return x + 100 }
    return -x
}""", "rti_op5(%s, %s)", listOf(listOf("0","5"), listOf("1","5"), listOf("2","5"), listOf("3","5"), listOf("4","5"))),
            RoundTripTestCase.withInputs("chain-dispatch", """
func rti_step(tag, x int) int {
    if tag == 0 { return x + 1 }
    if tag == 1 { return x * 2 }
    return x - 3
}
func rti_chain3(op1, op2, op3, x int) int {
    return rti_step(op3, rti_step(op2, rti_step(op1, x)))
}""", "rti_chain3(%s, %s, %s, %s)", listOf(listOf("0","1","2","10"), listOf("1","1","1","1"), listOf("2","0","1","5"))),
            RoundTripTestCase.withInputs("multi-path", """
func rti_pathA(x int) int { return x * 3 + 1 }
func rti_pathB(x int) int { return x * 2 - 1 }
func rti_pathC(x int) int { return x + 10 }
func rti_multi(sel, x int) int {
    s := sel % 3
    if s < 0 { s += 3 }
    if s == 0 { return rti_pathA(x) }
    if s == 1 { return rti_pathB(x) }
    return rti_pathC(x)
}""", "rti_multi(%s, %s)", listOf(listOf("0","5"), listOf("1","5"), listOf("2","5"), listOf("3","7"), listOf("-1","3"))),
            RoundTripTestCase.withInputs("pipeline-3-funcs", """
func rti_p1(x int) int { return x + 5 }
func rti_p2(x int) int { return x * 3 }
func rti_p3(x int) int { return x - 2 }
func rti_pipe(x int) int { return rti_p3(rti_p2(rti_p1(x))) }""",
                "rti_pipe(%s)", listOf(listOf("0"), listOf("3"), listOf("5"), listOf("10"))),
            RoundTripTestCase.withInputs("conditional-pipeline", """
func rti_inc(x int) int { return x + 1 }
func rti_dbl(x int) int { return x * 2 }
func rti_cond_pipe(cond, x int) int {
    if cond > 0 { return rti_dbl(rti_inc(x)) }
    return rti_inc(rti_dbl(x))
}""", "rti_cond_pipe(%s, %s)", listOf(listOf("1","5"), listOf("0","5"), listOf("-1","3"), listOf("1","0"))),
            RoundTripTestCase.withInputs("recursive-dispatch", """
func rti_rec(n, x int) int {
    if n <= 0 { return x }
    op := n % 3
    if op == 0 { return rti_rec(n-1, x+1) }
    if op == 1 { return rti_rec(n-1, x*2) }
    return rti_rec(n-1, x-3)
}""", "rti_rec(%s, %s)", listOf(listOf("0","10"), listOf("3","5"), listOf("6","1"), listOf("9","0")),
                randomRange = 0..15),
            RoundTripTestCase.withInputs("accumulate-dispatch", """
func rti_acc_op(tag, acc, val_ int) int {
    if tag == 0 { return acc + val_ }
    if tag == 1 { return acc - val_ }
    return acc ^ val_
}
func rti_acc_test(n int) int {
    acc := 0
    for i := 0; i < n; i++ { acc = rti_acc_op(i%3, acc, i+1) }
    return acc
}""", "rti_acc_test(%s)", listOf(listOf("0"), listOf("1"), listOf("3"), listOf("6"), listOf("10")),
                randomRange = 0..50),
            RoundTripTestCase.withInputs("binary-tree-tag", """
func rti_tree(depth, x int) int {
    if depth <= 0 { return x }
    left := rti_tree(depth-1, x+1)
    right := rti_tree(depth-1, x+2)
    return left + right
}""", "rti_tree(%s, %s)", listOf(listOf("0","10"), listOf("1","4"), listOf("2","3"), listOf("3","1")),
                randomRange = 0..5),
            RoundTripTestCase.withInputs("dispatch-with-state", """
func rti_state(steps, x int) int {
    state := 0
    for i := 0; i < steps; i++ {
        if state == 0 { x += 1; if x > 10 { state = 1 } }
        if state == 1 { x *= 2; if x > 100 { state = 2 } }
        if state == 2 { x -= 10; if x < 50 { state = 0 } }
    }
    return x
}""", "rti_state(%s, %s)", listOf(listOf("0","5"), listOf("5","1"), listOf("10","3"), listOf("20","0")),
                randomRange = 0..20),
            RoundTripTestCase.withInputs("classify-sum", """
func rti_classify(x int) int {
    if x > 0 { return 1 }
    if x < 0 { return -1 }
    return 0
}
func rti_class_sum(a, b, c int) int {
    return rti_classify(a) + rti_classify(b) + rti_classify(c)
}""", "rti_class_sum(%s, %s, %s)",
                listOf(listOf("5","-3","0"), listOf("1","2","3"), listOf("-1","-2","-3"), listOf("0","0","0"))),
            RoundTripTestCase.withInputs("cascaded-calls", """
func rti_f1(x int) int { return x + 7 }
func rti_f2(x int) int { return x * 2 - 1 }
func rti_f3(x int) int { return x / 3 + 4 }
func rti_cascade(x int) int {
    a := rti_f1(x)
    b := rti_f2(a)
    c := rti_f3(b)
    return a + b + c
}""", "rti_cascade(%s)", listOf(listOf("0"), listOf("5"), listOf("10"), listOf("20"))),
            RoundTripTestCase.withInputs("interleaved-dispatch", """
func rti_even_op(x int) int { return x * 2 }
func rti_odd_op(x int) int { return x + 3 }
func rti_interleave(n, x int) int {
    for i := 0; i < n; i++ {
        if i%2 == 0 { x = rti_even_op(x) } else { x = rti_odd_op(x) }
    }
    return x
}""", "rti_interleave(%s, %s)", listOf(listOf("0","1"), listOf("1","1"), listOf("4","1"), listOf("6","2")),
                randomRange = 0..15),
        )
        return BatchRoundTripRunner.runBatchAndCreateTests(cases, builder)
    }
}
