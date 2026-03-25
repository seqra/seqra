package org.opentaint.ir.go.test.roundtrip

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

/** Pattern-based tests: common programming patterns exercising various CFG shapes. */
@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripPatternTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase("accumulate-filtered", """
func pt_sumFiltered(n int) int {
    s := 0
    for i := 1; i <= n; i++ { if i%3 == 0 || i%5 == 0 { s += i } }
    return s
}""", "fmt.Println(pt_sumFiltered(10)); fmt.Println(pt_sumFiltered(100)); fmt.Println(pt_sumFiltered(1000))"),
            RoundTripTestCase("count-with-pred", """
func pt_countPred(n int) int {
    c := 0
    for i := 1; i <= n; i++ {
        d := 0; tmp := i
        for tmp > 0 { d += tmp % 10; tmp /= 10 }
        if d%2 == 0 { c++ }
    }
    return c
}""", "fmt.Println(pt_countPred(10)); fmt.Println(pt_countPred(50)); fmt.Println(pt_countPred(100))"),
            RoundTripTestCase("find-first-match", """
func pt_findFirst(n int) int {
    for i := 1; i <= n; i++ {
        if i*i > n { return i }
    }
    return -1
}""", "fmt.Println(pt_findFirst(10)); fmt.Println(pt_findFirst(100)); fmt.Println(pt_findFirst(1000))"),
            RoundTripTestCase("find-last-match", """
func pt_findLast(n int) int {
    last := -1
    for i := 1; i <= n; i++ { if i%7 == 0 { last = i } }
    return last
}""", "fmt.Println(pt_findLast(10)); fmt.Println(pt_findLast(50)); fmt.Println(pt_findLast(100))"),
            RoundTripTestCase("double-break", """
func pt_doubleBreak(n int) int {
    for i := 1; i < n; i++ {
        for j := i + 1; j <= n; j++ {
            if i*j > n { return i*1000 + j }
        }
    }
    return -1
}""", "fmt.Println(pt_doubleBreak(10)); fmt.Println(pt_doubleBreak(50)); fmt.Println(pt_doubleBreak(100))"),
            RoundTripTestCase("running-max", """
func pt_runMax(n int) int {
    mx := 0
    for i := 1; i <= n; i++ {
        v := (i * 37 + 13) % 100
        if v > mx { mx = v }
    }
    return mx
}""", "fmt.Println(pt_runMax(10)); fmt.Println(pt_runMax(50)); fmt.Println(pt_runMax(200))"),
            RoundTripTestCase("running-min", """
func pt_runMin(n int) int {
    mn := 9999
    for i := 1; i <= n; i++ {
        v := (i * 37 + 13) % 100
        if v < mn { mn = v }
    }
    return mn
}""", "fmt.Println(pt_runMin(10)); fmt.Println(pt_runMin(50)); fmt.Println(pt_runMin(200))"),
            RoundTripTestCase("two-pointer", """
func pt_twoPtr(n int) int {
    lo := 1; hi := n; c := 0
    for lo < hi {
        s := lo + hi
        if s == n { c++; lo++; hi-- } else if s < n { lo++ } else { hi-- }
    }
    return c
}""", "fmt.Println(pt_twoPtr(10)); fmt.Println(pt_twoPtr(20)); fmt.Println(pt_twoPtr(100))"),
            RoundTripTestCase("state-machine", """
func pt_stateMachine(n int) int {
    state := 0; count := 0
    for i := 0; i < n; i++ {
        v := (i * 31 + 7) % 4
        if state == 0 && v == 1 { state = 1 }
        if state == 1 && v == 2 { state = 2; count++ }
        if state == 2 { state = 0 }
    }
    return count
}""", "fmt.Println(pt_stateMachine(10)); fmt.Println(pt_stateMachine(50)); fmt.Println(pt_stateMachine(200))"),
            RoundTripTestCase("histogram", """
func pt_histogram(n int) int {
    b0 := 0; b1 := 0; b2 := 0; b3 := 0
    for i := 0; i < n; i++ {
        v := (i * 17 + 5) % 4
        if v == 0 { b0++ }
        if v == 1 { b1++ }
        if v == 2 { b2++ }
        if v == 3 { b3++ }
    }
    return b0*1000 + b1*100 + b2*10 + b3
}""", "fmt.Println(pt_histogram(16)); fmt.Println(pt_histogram(100)); fmt.Println(pt_histogram(1000))"),
            RoundTripTestCase("prefix-sum", """
func pt_prefixSum(n int) int {
    s := 0; maxS := 0
    for i := 1; i <= n; i++ {
        v := (i*41 + 3) % 21 - 10
        s += v
        if s > maxS { maxS = s }
    }
    return maxS
}""", "fmt.Println(pt_prefixSum(10)); fmt.Println(pt_prefixSum(50)); fmt.Println(pt_prefixSum(200))"),
            RoundTripTestCase("zigzag-traverse", """
func pt_zigzag(n int) int {
    s := 0; dir := 1
    for i := 0; i < n; i++ { s += i * dir; dir = -dir }
    return s
}""", "fmt.Println(pt_zigzag(5)); fmt.Println(pt_zigzag(10)); fmt.Println(pt_zigzag(100))"),
            RoundTripTestCase("sliding-window", """
func pt_slidingMax(n, w int) int {
    maxSum := 0; cur := 0
    for i := 0; i < n; i++ {
        cur += (i*13 + 7) % 10
        if i >= w { cur -= ((i-w)*13 + 7) % 10 }
        if i >= w-1 && cur > maxSum { maxSum = cur }
    }
    return maxSum
}""", "fmt.Println(pt_slidingMax(10,3)); fmt.Println(pt_slidingMax(20,5)); fmt.Println(pt_slidingMax(100,10))"),
            RoundTripTestCase("majority-vote", """
func pt_majority(n int) int {
    cand := 0; count := 0
    for i := 0; i < n; i++ {
        v := (i*23 + 11) % 5
        if count == 0 { cand = v; count = 1 } else if v == cand { count++ } else { count-- }
    }
    return cand
}""", "fmt.Println(pt_majority(10)); fmt.Println(pt_majority(50)); fmt.Println(pt_majority(200))"),
            RoundTripTestCase("dutch-flag-count", """
func pt_dutchFlag(n int) int {
    r := 0; w := 0; b := 0
    for i := 0; i < n; i++ {
        v := (i*29 + 3) % 3
        if v == 0 { r++ } else if v == 1 { w++ } else { b++ }
    }
    return r*10000 + w*100 + b
}""", "fmt.Println(pt_dutchFlag(12)); fmt.Println(pt_dutchFlag(99)); fmt.Println(pt_dutchFlag(300))"),
            RoundTripTestCase("kadane-max-subarray", """
func pt_kadane(n int) int {
    maxSoFar := 0; maxEndHere := 0
    for i := 0; i < n; i++ {
        v := (i*41 + 3) % 21 - 10
        maxEndHere += v
        if maxEndHere < 0 { maxEndHere = 0 }
        if maxEndHere > maxSoFar { maxSoFar = maxEndHere }
    }
    return maxSoFar
}""", "fmt.Println(pt_kadane(10)); fmt.Println(pt_kadane(50)); fmt.Println(pt_kadane(200))"),
            RoundTripTestCase("longest-streak", """
func pt_streak(n int) int {
    maxStreak := 0; cur := 0; prev := -1
    for i := 0; i < n; i++ {
        v := (i*17 + 5) % 3
        if v == prev { cur++ } else { cur = 1; prev = v }
        if cur > maxStreak { maxStreak = cur }
    }
    return maxStreak
}""", "fmt.Println(pt_streak(10)); fmt.Println(pt_streak(50)); fmt.Println(pt_streak(300))"),
            RoundTripTestCase("skip-counter", """
func pt_skipCount(n, skip int) int {
    c := 0
    for i := 0; i < n; i += skip { c++ }
    return c
}""", "fmt.Println(pt_skipCount(100,7)); fmt.Println(pt_skipCount(50,3)); fmt.Println(pt_skipCount(10,1))"),
            RoundTripTestCase("bounded-retry", """
func pt_retry(target, maxRetry int) int {
    for i := 0; i < maxRetry; i++ {
        v := (i*37 + 13) % 100
        if v == target { return i }
    }
    return -1
}""", "fmt.Println(pt_retry(50,100)); fmt.Println(pt_retry(0,10)); fmt.Println(pt_retry(99,5))"),
            RoundTripTestCase("convergence", """
func pt_converge(start int) int {
    x := start; steps := 0
    for {
        next := (x + 100/x) / 2
        if next == x { return steps }
        x = next; steps++
        if steps > 100 { return steps }
    }
}""", "fmt.Println(pt_converge(50)); fmt.Println(pt_converge(1)); fmt.Println(pt_converge(100))"),
        )
        val result = BatchRoundTripRunner.runBatch(cases, builder)
        return cases.map { c -> DynamicTest.dynamicTest(c.name) {
            assertThat(result.reconstructedOutputs[c.name]).isEqualTo(result.originalOutputs[c.name])
        }}
    }
}
