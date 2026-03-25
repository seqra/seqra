package org.opentaint.ir.go.test.roundtrip

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripArithmeticTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase("add", "func rt_add(a, b int) int { return a + b }",
                "fmt.Println(rt_add(3,4)); fmt.Println(rt_add(-1,1)); fmt.Println(rt_add(0,0))"),
            RoundTripTestCase("sub", "func rt_sub(a, b int) int { return a - b }",
                "fmt.Println(rt_sub(10,3)); fmt.Println(rt_sub(0,5)); fmt.Println(rt_sub(-3,-7))"),
            RoundTripTestCase("mul", "func rt_mul(a, b int) int { return a * b }",
                "fmt.Println(rt_mul(6,7)); fmt.Println(rt_mul(-3,4)); fmt.Println(rt_mul(0,999))"),
            RoundTripTestCase("div", "func rt_div(a, b int) int { return a / b }",
                "fmt.Println(rt_div(10,3)); fmt.Println(rt_div(100,5)); fmt.Println(rt_div(-15,4))"),
            RoundTripTestCase("mod", "func rt_mod(a, b int) int { return a % b }",
                "fmt.Println(rt_mod(10,3)); fmt.Println(rt_mod(100,7)); fmt.Println(rt_mod(15,5))"),
            RoundTripTestCase("add-sub", "func rt_addsub(a, b, c int) int { return a + b - c }",
                "fmt.Println(rt_addsub(10,20,5)); fmt.Println(rt_addsub(0,0,0)); fmt.Println(rt_addsub(-1,-2,-3))"),
            RoundTripTestCase("mul-div", "func rt_muldiv(a, b, c int) int { return a * b / c }",
                "fmt.Println(rt_muldiv(6,7,3)); fmt.Println(rt_muldiv(100,2,10)); fmt.Println(rt_muldiv(12,5,4))"),
            RoundTripTestCase("chain", "func rt_chain(x int) int { return ((x + 1) * 2 - 3) / 2 }",
                "fmt.Println(rt_chain(5)); fmt.Println(rt_chain(10)); fmt.Println(rt_chain(0)); fmt.Println(rt_chain(100))"),
            RoundTripTestCase("negate", "func rt_negate(x int) int { return -x }",
                "fmt.Println(rt_negate(5)); fmt.Println(rt_negate(-3)); fmt.Println(rt_negate(0))"),
            RoundTripTestCase("abs", """
func rt_abs(x int) int {
    if x < 0 { return -x }
    return x
}""", "fmt.Println(rt_abs(5)); fmt.Println(rt_abs(-3)); fmt.Println(rt_abs(0))"),
            RoundTripTestCase("sign", """
func rt_sign(x int) int {
    if x > 0 { return 1 }
    if x < 0 { return -1 }
    return 0
}""", "fmt.Println(rt_sign(42)); fmt.Println(rt_sign(-7)); fmt.Println(rt_sign(0))"),
            RoundTripTestCase("square", "func rt_square(x int) int { return x * x }",
                "fmt.Println(rt_square(5)); fmt.Println(rt_square(-3)); fmt.Println(rt_square(0)); fmt.Println(rt_square(12))"),
            RoundTripTestCase("cube", "func rt_cube(x int) int { return x * x * x }",
                "fmt.Println(rt_cube(3)); fmt.Println(rt_cube(-2)); fmt.Println(rt_cube(0)); fmt.Println(rt_cube(5))"),
            RoundTripTestCase("sum-sq", "func rt_sumsq(a, b int) int { return a*a + b*b }",
                "fmt.Println(rt_sumsq(3,4)); fmt.Println(rt_sumsq(0,5)); fmt.Println(rt_sumsq(-3,4))"),
            RoundTripTestCase("diff-sq", "func rt_diffsq(a, b int) int { return a*a - b*b }",
                "fmt.Println(rt_diffsq(5,3)); fmt.Println(rt_diffsq(10,10)); fmt.Println(rt_diffsq(7,2))"),
            RoundTripTestCase("avg", "func rt_avg(a, b int) int { return (a + b) / 2 }",
                "fmt.Println(rt_avg(10,20)); fmt.Println(rt_avg(7,3)); fmt.Println(rt_avg(0,0)); fmt.Println(rt_avg(1,2))"),
            RoundTripTestCase("poly", "func rt_poly(x int) int { return 3*x*x + 2*x + 1 }",
                "fmt.Println(rt_poly(0)); fmt.Println(rt_poly(1)); fmt.Println(rt_poly(2)); fmt.Println(rt_poly(-1)); fmt.Println(rt_poly(5))"),
            RoundTripTestCase("dist-sq", """
func rt_distsq(x1, y1, x2, y2 int) int {
    dx := x2 - x1
    dy := y2 - y1
    return dx*dx + dy*dy
}""", "fmt.Println(rt_distsq(0,0,3,4)); fmt.Println(rt_distsq(1,1,4,5)); fmt.Println(rt_distsq(0,0,0,0))"),
            RoundTripTestCase("add3", "func rt_add3(a, b, c int) int { return a + b + c }",
                "fmt.Println(rt_add3(1,2,3)); fmt.Println(rt_add3(-1,0,1)); fmt.Println(rt_add3(100,200,300))"),
            RoundTripTestCase("madd", "func rt_madd(a, b, c int) int { return a*b + c }",
                "fmt.Println(rt_madd(3,4,5)); fmt.Println(rt_madd(0,10,7)); fmt.Println(rt_madd(-2,3,10)); fmt.Println(rt_madd(1,1,1))"),
        )
        val result = BatchRoundTripRunner.runBatch(cases, builder)
        return cases.map { case ->
            DynamicTest.dynamicTest(case.name) {
                assertThat(result.reconstructedOutputs[case.name])
                    .withFailMessage { "Mismatch for '${case.name}'!\nOrig: ${result.originalOutputs[case.name]}\nRecon: ${result.reconstructedOutputs[case.name]}\nCode:\n${result.reconstructedCode}" }
                    .isEqualTo(result.originalOutputs[case.name])
            }
        }
    }
}
