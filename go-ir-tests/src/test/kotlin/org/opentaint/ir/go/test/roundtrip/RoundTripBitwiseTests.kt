package org.opentaint.ir.go.test.roundtrip

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripBitwiseTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase("and", "func bw_and(a, b int) int { return a & b }",
                "fmt.Println(bw_and(0xFF,0x0F)); fmt.Println(bw_and(12,10)); fmt.Println(bw_and(0,0xFF))"),
            RoundTripTestCase("or", "func bw_or(a, b int) int { return a | b }",
                "fmt.Println(bw_or(0xFF,0x0F)); fmt.Println(bw_or(12,10)); fmt.Println(bw_or(0,0))"),
            RoundTripTestCase("xor", "func bw_xor(a, b int) int { return a ^ b }",
                "fmt.Println(bw_xor(0xFF,0x0F)); fmt.Println(bw_xor(12,10)); fmt.Println(bw_xor(7,7))"),
            RoundTripTestCase("and-not", "func bw_andnot(a, b int) int { return a &^ b }",
                "fmt.Println(bw_andnot(0xFF,0x0F)); fmt.Println(bw_andnot(15,3))"),
            RoundTripTestCase("shl", "func bw_shl(a, n int) int { return a << uint(n) }",
                "fmt.Println(bw_shl(1,0)); fmt.Println(bw_shl(1,3)); fmt.Println(bw_shl(5,2))"),
            RoundTripTestCase("shr", "func bw_shr(a, n int) int { return a >> uint(n) }",
                "fmt.Println(bw_shr(16,2)); fmt.Println(bw_shr(255,4)); fmt.Println(bw_shr(1,0))"),
            RoundTripTestCase("complement", "func bw_comp(a int) int { return ^a }",
                "fmt.Println(bw_comp(0)); fmt.Println(bw_comp(1)); fmt.Println(bw_comp(-1))"),
            RoundTripTestCase("is-power-of-2", """
func bw_isPow2(n int) bool {
    if n <= 0 { return false }
    return n&(n-1) == 0
}""", "fmt.Println(bw_isPow2(1)); fmt.Println(bw_isPow2(2)); fmt.Println(bw_isPow2(3)); fmt.Println(bw_isPow2(16)); fmt.Println(bw_isPow2(0))"),
            RoundTripTestCase("count-bits", """
func bw_countBits(n int) int {
    if n < 0 { n = -n }
    c := 0; for n > 0 { c += n & 1; n >>= 1 }; return c
}""", "fmt.Println(bw_countBits(0)); fmt.Println(bw_countBits(7)); fmt.Println(bw_countBits(255)); fmt.Println(bw_countBits(1024))"),
            RoundTripTestCase("lowest-bit", "func bw_lowest(n int) int { return n & (-n) }",
                "fmt.Println(bw_lowest(12)); fmt.Println(bw_lowest(8)); fmt.Println(bw_lowest(7)); fmt.Println(bw_lowest(0))"),
            RoundTripTestCase("clear-lowest", "func bw_clearLowest(n int) int { return n & (n - 1) }",
                "fmt.Println(bw_clearLowest(12)); fmt.Println(bw_clearLowest(8)); fmt.Println(bw_clearLowest(7))"),
            RoundTripTestCase("set-bit", "func bw_setBit(n, pos int) int { return n | (1 << uint(pos)) }",
                "fmt.Println(bw_setBit(0,3)); fmt.Println(bw_setBit(5,1)); fmt.Println(bw_setBit(8,3))"),
            RoundTripTestCase("clear-bit", "func bw_clearBit(n, pos int) int { return n &^ (1 << uint(pos)) }",
                "fmt.Println(bw_clearBit(15,2)); fmt.Println(bw_clearBit(8,3)); fmt.Println(bw_clearBit(7,0))"),
            RoundTripTestCase("test-bit", "func bw_testBit(n, pos int) bool { return n&(1<<uint(pos)) != 0 }",
                "fmt.Println(bw_testBit(8,3)); fmt.Println(bw_testBit(8,2)); fmt.Println(bw_testBit(15,0))"),
            RoundTripTestCase("swap-xor", """
func bw_swapResult(a, b int) int {
    a = a ^ b; b = a ^ b; a = a ^ b
    return a*1000 + b
}""", "fmt.Println(bw_swapResult(3,7)); fmt.Println(bw_swapResult(0,5)); fmt.Println(bw_swapResult(10,10))"),
        )
        val result = BatchRoundTripRunner.runBatch(cases, builder)
        return cases.map { c -> DynamicTest.dynamicTest(c.name) {
            assertThat(result.reconstructedOutputs[c.name]).isEqualTo(result.originalOutputs[c.name])
        }}
    }
}
