package org.opentaint.ir.go.test.roundtrip

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripComparisonTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase("eq", "func rt_eq(a, b int) bool { return a == b }",
                "fmt.Println(rt_eq(1,1)); fmt.Println(rt_eq(1,2)); fmt.Println(rt_eq(0,0))"),
            RoundTripTestCase("neq", "func rt_neq(a, b int) bool { return a != b }",
                "fmt.Println(rt_neq(1,1)); fmt.Println(rt_neq(1,2)); fmt.Println(rt_neq(-1,1))"),
            RoundTripTestCase("lt", "func rt_lt(a, b int) bool { return a < b }",
                "fmt.Println(rt_lt(1,2)); fmt.Println(rt_lt(2,1)); fmt.Println(rt_lt(1,1))"),
            RoundTripTestCase("lte", "func rt_lte(a, b int) bool { return a <= b }",
                "fmt.Println(rt_lte(1,2)); fmt.Println(rt_lte(2,1)); fmt.Println(rt_lte(1,1))"),
            RoundTripTestCase("gt", "func rt_gt(a, b int) bool { return a > b }",
                "fmt.Println(rt_gt(1,2)); fmt.Println(rt_gt(2,1)); fmt.Println(rt_gt(1,1))"),
            RoundTripTestCase("gte", "func rt_gte(a, b int) bool { return a >= b }",
                "fmt.Println(rt_gte(1,2)); fmt.Println(rt_gte(2,1)); fmt.Println(rt_gte(1,1))"),
            RoundTripTestCase("max2", "func rt_max2(a, b int) int { if a > b { return a }; return b }",
                "fmt.Println(rt_max2(3,7)); fmt.Println(rt_max2(10,2)); fmt.Println(rt_max2(5,5))"),
            RoundTripTestCase("min2", "func rt_min2(a, b int) int { if a < b { return a }; return b }",
                "fmt.Println(rt_min2(3,7)); fmt.Println(rt_min2(10,2)); fmt.Println(rt_min2(5,5))"),
            RoundTripTestCase("max3", """
func rt_max3(a, b, c int) int {
    m := a
    if b > m { m = b }
    if c > m { m = c }
    return m
}""", "fmt.Println(rt_max3(1,2,3)); fmt.Println(rt_max3(3,2,1)); fmt.Println(rt_max3(2,3,1)); fmt.Println(rt_max3(5,5,5))"),
            RoundTripTestCase("min3", """
func rt_min3(a, b, c int) int {
    m := a
    if b < m { m = b }
    if c < m { m = c }
    return m
}""", "fmt.Println(rt_min3(1,2,3)); fmt.Println(rt_min3(3,2,1)); fmt.Println(rt_min3(2,3,1)); fmt.Println(rt_min3(5,5,5))"),
            RoundTripTestCase("and", "func rt_and(a, b bool) bool { if a { if b { return true } }; return false }",
                "fmt.Println(rt_and(true,true)); fmt.Println(rt_and(true,false)); fmt.Println(rt_and(false,true)); fmt.Println(rt_and(false,false))"),
            RoundTripTestCase("or", "func rt_or(a, b bool) bool { if a { return true }; if b { return true }; return false }",
                "fmt.Println(rt_or(true,true)); fmt.Println(rt_or(true,false)); fmt.Println(rt_or(false,true)); fmt.Println(rt_or(false,false))"),
            RoundTripTestCase("not", "func rt_not(a bool) bool { if a { return false }; return true }",
                "fmt.Println(rt_not(true)); fmt.Println(rt_not(false))"),
            RoundTripTestCase("in-range", "func rt_inRange(x, lo, hi int) bool { return x >= lo && x <= hi }",
                "fmt.Println(rt_inRange(5,1,10)); fmt.Println(rt_inRange(0,1,10)); fmt.Println(rt_inRange(11,1,10)); fmt.Println(rt_inRange(1,1,1))"),
            RoundTripTestCase("is-pos", "func rt_isPos(x int) bool { return x > 0 }",
                "fmt.Println(rt_isPos(5)); fmt.Println(rt_isPos(-3)); fmt.Println(rt_isPos(0))"),
            RoundTripTestCase("is-even", "func rt_isEven(x int) bool { return x%2 == 0 }",
                "fmt.Println(rt_isEven(4)); fmt.Println(rt_isEven(7)); fmt.Println(rt_isEven(0)); fmt.Println(rt_isEven(-2))"),
            RoundTripTestCase("is-odd", "func rt_isOdd(x int) bool { return x%2 != 0 }",
                "fmt.Println(rt_isOdd(4)); fmt.Println(rt_isOdd(7)); fmt.Println(rt_isOdd(0)); fmt.Println(rt_isOdd(-3))"),
            RoundTripTestCase("div-by", "func rt_divBy(x, d int) bool { return x%d == 0 }",
                "fmt.Println(rt_divBy(10,5)); fmt.Println(rt_divBy(10,3)); fmt.Println(rt_divBy(0,7)); fmt.Println(rt_divBy(15,3))"),
            RoundTripTestCase("ternary", "func rt_tern(cond bool, a, b int) int { if cond { return a }; return b }",
                "fmt.Println(rt_tern(true,1,2)); fmt.Println(rt_tern(false,1,2)); fmt.Println(rt_tern(true,99,0))"),
            RoundTripTestCase("clamp", """
func rt_clamp(x, lo, hi int) int {
    if x < lo { return lo }
    if x > hi { return hi }
    return x
}""", "fmt.Println(rt_clamp(5,0,10)); fmt.Println(rt_clamp(-5,0,10)); fmt.Println(rt_clamp(15,0,10)); fmt.Println(rt_clamp(0,0,10))"),
        )
        return BatchRoundTripRunner.runBatchAndCreateTests(cases, builder)
    }
}
