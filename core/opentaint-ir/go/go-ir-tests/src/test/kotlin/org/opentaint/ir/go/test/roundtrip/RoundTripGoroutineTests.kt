package org.opentaint.ir.go.test.roundtrip

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

/** QI3.5 — Pipeline/processing patterns using arrays and multi-function calls. */
@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripGoroutineTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase.withInputs("producer-sum", """
func rtg_produce(n int) int {
    sum := 0
    for i := 1; i <= n; i++ { sum += i * i }
    return sum
}""", "rtg_produce(%s)", listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10")), randomRange = 0..50),
            RoundTripTestCase.withInputs("transform-chain", """
func rtg_t1(x int) int { return x * 2 }
func rtg_t2(x int) int { return x + 10 }
func rtg_t3(x int) int { return x - 3 }
func rtg_chain(x int) int { return rtg_t3(rtg_t2(rtg_t1(x))) }""",
                "rtg_chain(%s)", listOf(listOf("0"), listOf("5"), listOf("10"), listOf("-3"))),
            RoundTripTestCase.withInputs("fan-out-encode", """
func rtg_worker1(x int) int { return x + 1 }
func rtg_worker2(x int) int { return x * 2 }
func rtg_worker3(x int) int { return x - 5 }
func rtg_fan(x int) int { return rtg_worker1(x)*10000 + rtg_worker2(x)*100 + rtg_worker3(x) }""",
                "rtg_fan(%s)", listOf(listOf("10"), listOf("0"), listOf("5"), listOf("20"))),
            RoundTripTestCase.withInputs("batch-accum", """
func rtg_batch_sum(n, bs int) int {
    if bs <= 0 { bs = 1 }
    total := 0
    for start := 0; start < n; start += bs {
        end := start + bs; if end > n { end = n }
        batchSum := 0
        for i := start; i < end; i++ { batchSum += i + 1 }
        total += batchSum
    }
    return total
}""", "rtg_batch_sum(%s, %s)", listOf(listOf("0","1"), listOf("5","2"), listOf("10","3"), listOf("7","4")),
                randomRange = 1..50),
            RoundTripTestCase.withInputs("partition-reduce", """
func rtg_part(n, k int) int {
    if k <= 0 { k = 1 }
    sums := [4]int{0, 0, 0, 0}
    maxK := k; if maxK > 4 { maxK = 4 }
    for i := 0; i < n; i++ { idx := i % maxK; sums[idx] += i + 1 }
    total := 0
    for i := 0; i < maxK; i++ { total += sums[i] }
    return total
}""", "rtg_part(%s, %s)", listOf(listOf("0","1"), listOf("5","2"), listOf("10","3"), listOf("12","4")),
                randomRange = 1..50),
            RoundTripTestCase.withInputs("window-max", """
func rtg_window(n, w int) int {
    if n <= 0 || w <= 0 { return 0 }
    maxSum := 0
    for i := 0; i <= n-w; i++ {
        s := 0
        for j := i; j < i+w; j++ { s += (j+1) % 5 }
        if s > maxSum || i == 0 { maxSum = s }
    }
    return maxSum
}""", "rtg_window(%s, %s)", listOf(listOf("0","1"), listOf("5","2"), listOf("10","3"), listOf("8","4")),
                randomRange = 1..30),
            RoundTripTestCase.withInputs("ring-sim", """
func rtg_ring(n, cap_ int) int {
    if cap_ <= 0 { cap_ = 1 }
    buf := [8]int{0,0,0,0,0,0,0,0}
    c := cap_; if c > 8 { c = 8 }
    for i := 0; i < n; i++ { buf[i%c] = i + 1 }
    sum := 0
    for i := 0; i < c; i++ { sum += buf[i] }
    return sum
}""", "rtg_ring(%s, %s)", listOf(listOf("0","3"), listOf("3","3"), listOf("5","3"), listOf("10","4")),
                randomRange = 1..20),
            RoundTripTestCase.withInputs("merge-sorted-arrays", """
func rtg_merge(a1,a2,a3,b1,b2,b3 int) int {
    a := [3]int{a1,a2,a3}
    b := [3]int{b1,b2,b3}
    merged := [6]int{0,0,0,0,0,0}
    i, j, k := 0, 0, 0
    for i < 3 && j < 3 { if a[i] <= b[j] { merged[k] = a[i]; i++ } else { merged[k] = b[j]; j++ }; k++ }
    for i < 3 { merged[k] = a[i]; i++; k++ }
    for j < 3 { merged[k] = b[j]; j++; k++ }
    enc := 0
    for m := 0; m < 6; m++ { enc = enc*10 + merged[m] }
    return enc
}""", "rtg_merge(%s,%s,%s,%s,%s,%s)",
                listOf(listOf("1","3","5","2","4","6"), listOf("1","2","3","4","5","6")), argCount = 6, randomRange = 0..9),
            RoundTripTestCase.withInputs("two-pointer-sum", """
func rtg_two_ptr(n, target int) int {
    count := 0
    for l := 1; l <= n; l++ {
        for r := l + 1; r <= n; r++ {
            if l + r == target { count++ }
        }
    }
    return count
}""", "rtg_two_ptr(%s, %s)", listOf(listOf("5","6"), listOf("10","11"), listOf("0","0"), listOf("4","5")),
                randomRange = 1..30),
            RoundTripTestCase.withInputs("bucket-count", """
func rtg_bucket(n int) int {
    buckets := [10]int{0,0,0,0,0,0,0,0,0,0}
    for i := 0; i < n; i++ { buckets[(i*7+3)%10]++ }
    enc := 0
    for i := 0; i < 10; i++ { enc += buckets[i] * buckets[i] }
    return enc
}""", "rtg_bucket(%s)", listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10"), listOf("20")),
                randomRange = 0..50),
            RoundTripTestCase.withInputs("matrix-multiply-2x2", """
func rtg_mat_mul(a11,a12,a21,a22,b11,b12,b21,b22 int) int {
    c11 := a11*b11 + a12*b21
    c12 := a11*b12 + a12*b22
    c21 := a21*b11 + a22*b21
    c22 := a21*b12 + a22*b22
    return c11*1000 + c12*100 + c21*10 + c22
}""", "rtg_mat_mul(%s,%s,%s,%s,%s,%s,%s,%s)",
                listOf(listOf("1","0","0","1","2","3","4","5"), listOf("1","2","3","4","5","6","7","8")),
                argCount = 8, randomRange = 0..5),
            RoundTripTestCase.withInputs("histogram-4-bins", """
func rtg_hist(a,b,c,d,e,f,g,h int) int {
    bins := [4]int{0,0,0,0}
    vals := [8]int{a,b,c,d,e,f,g,h}
    for i := 0; i < 8; i++ {
        v := vals[i] % 4; if v < 0 { v += 4 }
        bins[v]++
    }
    return bins[0]*1000 + bins[1]*100 + bins[2]*10 + bins[3]
}""", "rtg_hist(%s,%s,%s,%s,%s,%s,%s,%s)",
                listOf(listOf("0","1","2","3","4","5","6","7"), listOf("1","1","1","1","1","1","1","1")),
                argCount = 8, randomRange = 0..20),
        )
        return BatchRoundTripRunner.runBatchAndCreateTests(cases, builder)
    }
}
