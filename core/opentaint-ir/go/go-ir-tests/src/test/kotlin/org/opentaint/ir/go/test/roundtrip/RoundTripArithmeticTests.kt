package org.opentaint.ir.go.test.roundtrip

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
            RoundTripTestCase.withInputs("add",
                "func rt_add(a, b int) int { return a + b }",
                "rt_add(%s, %s)",
                listOf(listOf("3","4"), listOf("-1","1"), listOf("0","0")),
            ),
            RoundTripTestCase.withInputs("sub",
                "func rt_sub(a, b int) int { return a - b }",
                "rt_sub(%s, %s)",
                listOf(listOf("10","3"), listOf("0","5"), listOf("-3","-7")),
            ),
            RoundTripTestCase.withInputs("mul",
                "func rt_mul(a, b int) int { return a * b }",
                "rt_mul(%s, %s)",
                listOf(listOf("6","7"), listOf("-3","4"), listOf("0","999")),
            ),
            RoundTripTestCase.withInputs("div",
                "func rt_div(a, b int) int { return a / b }",
                "rt_div(%s, %s)",
                listOf(listOf("10","3"), listOf("100","5"), listOf("-15","4")),
                randomRange = 1..1000, // avoid division by zero
            ),
            RoundTripTestCase.withInputs("mod",
                "func rt_mod(a, b int) int { return a % b }",
                "rt_mod(%s, %s)",
                listOf(listOf("10","3"), listOf("100","7"), listOf("15","5")),
                randomRange = 1..1000, // avoid mod by zero
            ),
            RoundTripTestCase.withInputs("add-sub",
                "func rt_addsub(a, b, c int) int { return a + b - c }",
                "rt_addsub(%s, %s, %s)",
                listOf(listOf("10","20","5"), listOf("0","0","0"), listOf("-1","-2","-3")),
            ),
            RoundTripTestCase.withInputs("mul-div",
                "func rt_muldiv(a, b, c int) int { return a * b / c }",
                "rt_muldiv(%s, %s, %s)",
                listOf(listOf("6","7","3"), listOf("100","2","10"), listOf("12","5","4")),
                randomRange = 1..1000, // avoid division by zero
            ),
            RoundTripTestCase.withInputs("chain",
                "func rt_chain(x int) int { return ((x + 1) * 2 - 3) / 2 }",
                "rt_chain(%s)",
                listOf(listOf("5"), listOf("10"), listOf("0"), listOf("100")),
            ),
            RoundTripTestCase.withInputs("negate",
                "func rt_negate(x int) int { return -x }",
                "rt_negate(%s)",
                listOf(listOf("5"), listOf("-3"), listOf("0")),
            ),
            RoundTripTestCase.withInputs("abs", """
func rt_abs(x int) int {
    if x < 0 { return -x }
    return x
}""",
                "rt_abs(%s)",
                listOf(listOf("5"), listOf("-3"), listOf("0")),
            ),
            RoundTripTestCase.withInputs("sign", """
func rt_sign(x int) int {
    if x > 0 { return 1 }
    if x < 0 { return -1 }
    return 0
}""",
                "rt_sign(%s)",
                listOf(listOf("42"), listOf("-7"), listOf("0")),
            ),
            RoundTripTestCase.withInputs("square",
                "func rt_square(x int) int { return x * x }",
                "rt_square(%s)",
                listOf(listOf("5"), listOf("-3"), listOf("0"), listOf("12")),
            ),
            RoundTripTestCase.withInputs("cube",
                "func rt_cube(x int) int { return x * x * x }",
                "rt_cube(%s)",
                listOf(listOf("3"), listOf("-2"), listOf("0"), listOf("5")),
            ),
            RoundTripTestCase.withInputs("sum-sq",
                "func rt_sumsq(a, b int) int { return a*a + b*b }",
                "rt_sumsq(%s, %s)",
                listOf(listOf("3","4"), listOf("0","5"), listOf("-3","4")),
            ),
            RoundTripTestCase.withInputs("diff-sq",
                "func rt_diffsq(a, b int) int { return a*a - b*b }",
                "rt_diffsq(%s, %s)",
                listOf(listOf("5","3"), listOf("10","10"), listOf("7","2")),
            ),
            RoundTripTestCase.withInputs("avg",
                "func rt_avg(a, b int) int { return (a + b) / 2 }",
                "rt_avg(%s, %s)",
                listOf(listOf("10","20"), listOf("7","3"), listOf("0","0"), listOf("1","2")),
            ),
            RoundTripTestCase.withInputs("poly",
                "func rt_poly(x int) int { return 3*x*x + 2*x + 1 }",
                "rt_poly(%s)",
                listOf(listOf("0"), listOf("1"), listOf("2"), listOf("-1"), listOf("5")),
            ),
            RoundTripTestCase.withInputs("dist-sq", """
func rt_distsq(x1, y1, x2, y2 int) int {
    dx := x2 - x1
    dy := y2 - y1
    return dx*dx + dy*dy
}""",
                "rt_distsq(%s, %s, %s, %s)",
                listOf(listOf("0","0","3","4"), listOf("1","1","4","5"), listOf("0","0","0","0")),
            ),
            RoundTripTestCase.withInputs("add3",
                "func rt_add3(a, b, c int) int { return a + b + c }",
                "rt_add3(%s, %s, %s)",
                listOf(listOf("1","2","3"), listOf("-1","0","1"), listOf("100","200","300")),
            ),
            RoundTripTestCase.withInputs("madd",
                "func rt_madd(a, b, c int) int { return a*b + c }",
                "rt_madd(%s, %s, %s)",
                listOf(listOf("3","4","5"), listOf("0","10","7"), listOf("-2","3","10"), listOf("1","1","1")),
            ),
        )
        return BatchRoundTripRunner.runBatchAndCreateTests(cases, builder)
    }
}
