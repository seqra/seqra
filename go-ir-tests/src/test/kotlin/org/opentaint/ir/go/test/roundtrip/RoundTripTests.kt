package org.opentaint.ir.go.test.roundtrip

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

/** Original basic round-trip tests, converted to batched format. */
@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase("basic-arith", """
func b_add(a, b int) int { return a + b }
func b_sub(a, b int) int { return a - b }
func b_mul(a, b int) int { return a * b }""",
                "fmt.Println(b_add(3,4)); fmt.Println(b_sub(10,3)); fmt.Println(b_mul(5,6)); fmt.Println(b_add(b_sub(10,3), b_mul(2,3)))"),
            RoundTripTestCase("if-else", """
func b_abs(x int) int { if x < 0 { return -x }; return x }
func b_max(a, b int) int { if a > b { return a }; return b }""",
                "fmt.Println(b_abs(-5)); fmt.Println(b_abs(3)); fmt.Println(b_abs(0)); fmt.Println(b_max(3,7)); fmt.Println(b_max(10,2))"),
            RoundTripTestCase("bool-logic", """
func b_isPositive(x int) bool { return x > 0 }
func b_both(a, b bool) bool { if a { if b { return true } }; return false }""",
                "fmt.Println(b_isPositive(5)); fmt.Println(b_isPositive(-3)); fmt.Println(b_isPositive(0)); fmt.Println(b_both(true,true)); fmt.Println(b_both(true,false)); fmt.Println(b_both(false,true))"),
            RoundTripTestCase("for-accum", """
func b_sumTo(n int) int { total := 0; for i := 1; i <= n; i++ { total += i }; return total }
func b_factorial(n int) int { result := 1; for i := 2; i <= n; i++ { result *= i }; return result }""",
                "fmt.Println(b_sumTo(10)); fmt.Println(b_sumTo(0)); fmt.Println(b_sumTo(1)); fmt.Println(b_factorial(5)); fmt.Println(b_factorial(1))"),
            RoundTripTestCase("nested-if", """
func b_classify(x int) int {
    if x > 0 { if x > 100 { return 3 }; return 2 }
    if x == 0 { return 0 }; return -1
}""", "fmt.Println(b_classify(200)); fmt.Println(b_classify(50)); fmt.Println(b_classify(0)); fmt.Println(b_classify(-10))"),
            RoundTripTestCase("multi-ret", """
func b_divide(a, b int) int { if b == 0 { return 0 }; return a / b }""",
                "fmt.Println(b_divide(10,3)); fmt.Println(b_divide(10,0)); fmt.Println(b_divide(100,5)); fmt.Println(b_divide(-15,3))"),
        )
        val result = BatchRoundTripRunner.runBatch(cases, builder)
        return cases.map { c -> DynamicTest.dynamicTest(c.name) {
            assertThat(result.reconstructedOutputs[c.name]).isEqualTo(result.originalOutputs[c.name])
        }}
    }
}
