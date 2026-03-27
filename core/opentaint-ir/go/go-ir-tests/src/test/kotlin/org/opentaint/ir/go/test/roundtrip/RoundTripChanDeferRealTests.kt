package org.opentaint.ir.go.test.roundtrip

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

/** Tests that the IR correctly round-trips channels, goroutines, and defer. */
@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripChanDeferRealTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase.withInputs("chan-send-recv", """
func rtcd_basic(x int) int {
    ch := make(chan int, 1)
    ch <- x
    return <-ch
}""", "rtcd_basic(%s)", listOf(listOf("42"), listOf("0"), listOf("-7"), listOf("999"))),

            RoundTripTestCase.withInputs("goroutine-sum", """
func rtcd_go(n int) int {
    ch := make(chan int)
    go func() {
        sum := 0
        for i := 1; i <= n; i++ { sum += i }
        ch <- sum
    }()
    return <-ch
}""", "rtcd_go(%s)", listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10")), randomRange = 0..100),

            RoundTripTestCase.withInputs("defer-named-return", """
func rtcd_defer(x int) (result int) {
    defer func() { result += 100 }()
    result = x * 2
    return
}""", "rtcd_defer(%s)", listOf(listOf("5"), listOf("0"), listOf("-10"), listOf("50"))),

            RoundTripTestCase.withInputs("defer-lifo", """
func rtcd_lifo() int {
    result := 0
    p := &result
    defer func() { *p = *p*10 + 3 }()
    defer func() { *p = *p*10 + 2 }()
    defer func() { *p = *p*10 + 1 }()
    return result
}""", "rtcd_lifo()", listOf(listOf<String>()), argCount = 0),

            RoundTripTestCase.withInputs("chan-buffered-multi", """
func rtcd_buf(a, b, c int) int {
    ch := make(chan int, 3)
    ch <- a
    ch <- b
    ch <- c
    return <-ch + <-ch + <-ch
}""", "rtcd_buf(%s, %s, %s)", listOf(listOf("1","2","3"), listOf("0","0","0"), listOf("10","20","30"))),

            RoundTripTestCase.withInputs("goroutine-channel-double", """
func rtcd_double(x int) int {
    ch := make(chan int)
    go func() { ch <- x * 2 }()
    return <-ch
}""", "rtcd_double(%s)", listOf(listOf("5"), listOf("0"), listOf("-7"), listOf("100"))),

            RoundTripTestCase.withInputs("chan-range-sum", """
func rtcd_chansum(n int) int {
    ch := make(chan int, n)
    for i := 1; i <= n; i++ { ch <- i }
    close(ch)
    sum := 0
    for v := range ch { sum += v }
    return sum
}""", "rtcd_chansum(%s)", listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10")), randomRange = 0..50),

            RoundTripTestCase.withInputs("chan-pipeline", """
func rtcd_gen(n int) <-chan int {
    ch := make(chan int)
    go func() { for i := 1; i <= n; i++ { ch <- i }; close(ch) }()
    return ch
}
func rtcd_sq(in_ <-chan int) <-chan int {
    ch := make(chan int)
    go func() { for v := range in_ { ch <- v * v }; close(ch) }()
    return ch
}
func rtcd_pipe(n int) int {
    sum := 0
    for v := range rtcd_sq(rtcd_gen(n)) { sum += v }
    return sum
}""", "rtcd_pipe(%s)", listOf(listOf("0"), listOf("1"), listOf("3"), listOf("5")), randomRange = 0..10),
        )
        return BatchRoundTripRunner.runBatchAndCreateTests(cases, builder)
    }
}
