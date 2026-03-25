package org.opentaint.ir.go.test.roundtrip

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripControlFlowTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase("simple-if", "func cf_simpleIf(x int) int { if x > 0 { return 1 }; return 0 }",
                "fmt.Println(cf_simpleIf(5)); fmt.Println(cf_simpleIf(-3)); fmt.Println(cf_simpleIf(0))"),
            RoundTripTestCase("if-else", "func cf_ifElse(x int) int { if x > 0 { return 1 } else { return -1 } }",
                "fmt.Println(cf_ifElse(5)); fmt.Println(cf_ifElse(-3))"),
            RoundTripTestCase("if-elif", """
func cf_ifElif(x int) int {
    if x > 0 { return 1 }
    if x < 0 { return -1 }
    return 0
}""", "fmt.Println(cf_ifElif(5)); fmt.Println(cf_ifElif(-3)); fmt.Println(cf_ifElif(0))"),
            RoundTripTestCase("grade", """
func cf_grade(score int) int {
    if score >= 90 { return 5 }
    if score >= 80 { return 4 }
    if score >= 70 { return 3 }
    if score >= 60 { return 2 }
    return 1
}""", "fmt.Println(cf_grade(95)); fmt.Println(cf_grade(85)); fmt.Println(cf_grade(75)); fmt.Println(cf_grade(65)); fmt.Println(cf_grade(50))"),
            RoundTripTestCase("nested-deep", """
func cf_deep(a, b, c int) int {
    if a > 0 {
        if b > 0 {
            if c > 0 { return 1 }
            return 2
        }
        return 3
    }
    return 4
}""", "fmt.Println(cf_deep(1,1,1)); fmt.Println(cf_deep(1,1,0)); fmt.Println(cf_deep(1,0,1)); fmt.Println(cf_deep(0,1,1))"),
            RoundTripTestCase("guard", """
func cf_safeDivide(a, b int) int {
    if b == 0 { return 0 }
    return a / b
}""", "fmt.Println(cf_safeDivide(10,3)); fmt.Println(cf_safeDivide(10,0)); fmt.Println(cf_safeDivide(100,5))"),
            RoundTripTestCase("multi-guard", """
func cf_process(x int) int {
    if x < 0 { return -1 }
    if x == 0 { return 0 }
    if x > 1000 { return 1000 }
    return x
}""", "fmt.Println(cf_process(-5)); fmt.Println(cf_process(0)); fmt.Println(cf_process(500)); fmt.Println(cf_process(2000))"),
            RoundTripTestCase("cond-assign", """
func cf_condAssign(x int) int {
    result := 0
    if x > 10 { result = x * 2 } else { result = x + 10 }
    return result
}""", "fmt.Println(cf_condAssign(15)); fmt.Println(cf_condAssign(5)); fmt.Println(cf_condAssign(10))"),
            RoundTripTestCase("flag", """
func cf_flag(x int) int {
    flag := 0
    if x > 0 { flag = 1 }
    if x > 100 { flag = 2 }
    if x > 1000 { flag = 3 }
    return flag
}""", "fmt.Println(cf_flag(-5)); fmt.Println(cf_flag(50)); fmt.Println(cf_flag(500)); fmt.Println(cf_flag(5000))"),
            RoundTripTestCase("fizzbuzz", """
func cf_fizzbuzz(n int) int {
    if n%15 == 0 { return 3 }
    if n%3 == 0 { return 1 }
    if n%5 == 0 { return 2 }
    return 0
}""", "for i := 1; i <= 20; i++ { fmt.Println(cf_fizzbuzz(i)) }"),
            RoundTripTestCase("leap-year", """
func cf_isLeap(y int) bool {
    if y%400 == 0 { return true }
    if y%100 == 0 { return false }
    if y%4 == 0 { return true }
    return false
}""", "fmt.Println(cf_isLeap(2000)); fmt.Println(cf_isLeap(1900)); fmt.Println(cf_isLeap(2024)); fmt.Println(cf_isLeap(2023))"),
            RoundTripTestCase("triangle", """
func cf_triType(a, b, c int) int {
    if a == b && b == c { return 3 }
    if a == b || b == c || a == c { return 2 }
    return 1
}""", "fmt.Println(cf_triType(3,3,3)); fmt.Println(cf_triType(3,3,4)); fmt.Println(cf_triType(3,4,5))"),
            RoundTripTestCase("median", """
func cf_median(a, b, c int) int {
    if a >= b && a <= c || a <= b && a >= c { return a }
    if b >= a && b <= c || b <= a && b >= c { return b }
    return c
}""", "fmt.Println(cf_median(1,2,3)); fmt.Println(cf_median(3,1,2)); fmt.Println(cf_median(2,3,1)); fmt.Println(cf_median(5,5,5))"),
            RoundTripTestCase("weekday", """
func cf_dayType(d int) int {
    if d == 0 || d == 6 { return 0 }
    if d >= 1 && d <= 5 { return 1 }
    return -1
}""", "for i := 0; i <= 7; i++ { fmt.Println(cf_dayType(i)) }"),
            RoundTripTestCase("bmi", """
func cf_bmiCat(bmi int) int {
    if bmi < 18 { return 0 }
    if bmi < 25 { return 1 }
    if bmi < 30 { return 2 }
    return 3
}""", "fmt.Println(cf_bmiCat(15)); fmt.Println(cf_bmiCat(22)); fmt.Println(cf_bmiCat(27)); fmt.Println(cf_bmiCat(35))"),
            RoundTripTestCase("season", """
func cf_season(m int) int {
    if m >= 3 && m <= 5 { return 1 }
    if m >= 6 && m <= 8 { return 2 }
    if m >= 9 && m <= 11 { return 3 }
    return 0
}""", "for i := 1; i <= 12; i++ { fmt.Println(cf_season(i)) }"),
            RoundTripTestCase("abs-diff", """
func cf_absDiff(a, b int) int {
    d := a - b
    if d < 0 { return -d }
    return d
}""", "fmt.Println(cf_absDiff(10,3)); fmt.Println(cf_absDiff(3,10)); fmt.Println(cf_absDiff(5,5))"),
            RoundTripTestCase("quadrant", """
func cf_quadrant(x, y int) int {
    if x > 0 && y > 0 { return 1 }
    if x < 0 && y > 0 { return 2 }
    if x < 0 && y < 0 { return 3 }
    if x > 0 && y < 0 { return 4 }
    return 0
}""", "fmt.Println(cf_quadrant(1,1)); fmt.Println(cf_quadrant(-1,1)); fmt.Println(cf_quadrant(-1,-1)); fmt.Println(cf_quadrant(1,-1)); fmt.Println(cf_quadrant(0,0))"),
            RoundTripTestCase("is-pow2", """
func cf_isPow2(n int) bool {
    if n <= 0 { return false }
    return n&(n-1) == 0
}""", "fmt.Println(cf_isPow2(1)); fmt.Println(cf_isPow2(2)); fmt.Println(cf_isPow2(3)); fmt.Println(cf_isPow2(4)); fmt.Println(cf_isPow2(16)); fmt.Println(cf_isPow2(0))"),
            RoundTripTestCase("century", """
func cf_century(y int) int {
    if y%100 == 0 { return y / 100 }
    return y/100 + 1
}""", "fmt.Println(cf_century(2000)); fmt.Println(cf_century(2001)); fmt.Println(cf_century(1900)); fmt.Println(cf_century(1))"),
            RoundTripTestCase("num-size", """
func cf_numSize(n int) int {
    if n < 0 { n = -n }
    if n < 10 { return 1 }
    if n < 100 { return 2 }
    if n < 1000 { return 3 }
    if n < 10000 { return 4 }
    return 5
}""", "fmt.Println(cf_numSize(0)); fmt.Println(cf_numSize(9)); fmt.Println(cf_numSize(99)); fmt.Println(cf_numSize(999)); fmt.Println(cf_numSize(9999)); fmt.Println(cf_numSize(99999))"),
            RoundTripTestCase("multi-cond", """
func cf_check(a, b, c int) int {
    if a > 0 && b > 0 && c > 0 { return 1 }
    if a < 0 && b < 0 && c < 0 { return -1 }
    return 0
}""", "fmt.Println(cf_check(1,2,3)); fmt.Println(cf_check(-1,-2,-3)); fmt.Println(cf_check(1,-2,3)); fmt.Println(cf_check(0,0,0))"),
            RoundTripTestCase("safe-mod", """
func cf_safeMod(a, b int) int {
    if b == 0 { return 0 }
    return a % b
}""", "fmt.Println(cf_safeMod(10,3)); fmt.Println(cf_safeMod(10,0)); fmt.Println(cf_safeMod(15,5))"),
            RoundTripTestCase("between", """
func cf_between(x, lo, hi int) bool {
    if x < lo { return false }
    if x > hi { return false }
    return true
}""", "fmt.Println(cf_between(5,1,10)); fmt.Println(cf_between(0,1,10)); fmt.Println(cf_between(11,1,10)); fmt.Println(cf_between(1,1,10)); fmt.Println(cf_between(10,1,10))"),
        )
        val result = BatchRoundTripRunner.runBatch(cases, builder)
        return cases.map { case ->
            DynamicTest.dynamicTest(case.name) {
                assertThat(result.reconstructedOutputs[case.name]).isEqualTo(result.originalOutputs[case.name])
            }
        }
    }
}
