package org.opentaint.ir.go.test.roundtrip

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

/** Tests that the IR correctly round-trips dynamic slices, maps, append, range, etc. */
@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripSliceMapRealTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase.withInputs("slice-make-len", """
func rtsm_make_len(n int) int {
    s := make([]int, n)
    return len(s)
}""", "rtsm_make_len(%s)", listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10")), randomRange = 0..50),

            RoundTripTestCase.withInputs("slice-append-sum", """
func rtsm_append(n int) int {
    s := make([]int, 0)
    for i := 1; i <= n; i++ { s = append(s, i) }
    sum := 0
    for i := 0; i < len(s); i++ { sum += s[i] }
    return sum
}""", "rtsm_append(%s)", listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10")), randomRange = 0..50),

            RoundTripTestCase.withInputs("slice-index-write", """
func rtsm_write(n int) int {
    s := make([]int, n)
    for i := 0; i < n; i++ { s[i] = (i + 1) * 3 }
    total := 0
    for i := 0; i < n; i++ { total += s[i] }
    return total
}""", "rtsm_write(%s)", listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10")), randomRange = 0..50),

            RoundTripTestCase.withInputs("slice-copy", """
func rtsm_copy(n int) int {
    src := make([]int, n)
    for i := 0; i < n; i++ { src[i] = i * 2 }
    dst := make([]int, n)
    copy(dst, src)
    sum := 0
    for i := 0; i < n; i++ { sum += dst[i] }
    return sum
}""", "rtsm_copy(%s)", listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10")), randomRange = 0..30),

            RoundTripTestCase.withInputs("map-insert-lookup", """
func rtsm_map(k, v int) int {
    m := make(map[int]int)
    m[k] = v
    return m[k]
}""", "rtsm_map(%s, %s)", listOf(listOf("1","42"), listOf("0","0"), listOf("-1","99"))),

            RoundTripTestCase.withInputs("map-has-key", """
func rtsm_has(k, v int) int {
    m := make(map[int]int)
    m[v] = 1
    _, ok := m[k]
    if ok { return 1 }
    return 0
}""", "rtsm_has(%s, %s)", listOf(listOf("1","1"), listOf("1","2"), listOf("0","0"))),

            RoundTripTestCase.withInputs("map-delete", """
func rtsm_del(n int) int {
    m := make(map[int]int)
    for i := 0; i < n; i++ { m[i] = i }
    for i := 0; i < n; i += 2 { delete(m, i) }
    return len(m)
}""", "rtsm_del(%s)", listOf(listOf("0"), listOf("1"), listOf("4"), listOf("5"), listOf("10")), randomRange = 0..30),

            RoundTripTestCase.withInputs("slice-subslice", """
func rtsm_sub(n int) int {
    s := make([]int, n)
    for i := 0; i < n; i++ { s[i] = i + 1 }
    if n < 2 { return 0 }
    sub := s[1 : n-1]
    total := 0
    for i := 0; i < len(sub); i++ { total += sub[i] }
    return total
}""", "rtsm_sub(%s)", listOf(listOf("0"), listOf("1"), listOf("2"), listOf("5"), listOf("10")), randomRange = 2..30),
        )
        return BatchRoundTripRunner.runBatchAndCreateTests(cases, builder)
    }
}
