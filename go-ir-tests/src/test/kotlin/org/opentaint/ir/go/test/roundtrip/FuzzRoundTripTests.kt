package org.opentaint.ir.go.test.roundtrip

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.codegen.GoIRToGoCodeGenerator
import org.opentaint.ir.go.test.GoIRTestBuilder
import org.opentaint.ir.go.test.GoIRTestExtension
import org.opentaint.ir.go.test.GoIRSanityChecker
import org.opentaint.ir.go.test.GoRunner

/**
 * Multi-input round-trip tests (Strategy 3 extension).
 *
 * Instead of fuzzing, these tests compare original vs reconstructed function
 * outputs across many predefined input values — covering edge cases,
 * boundary conditions, and random-looking inputs deterministically.
 *
 * Each test creates a Go program that runs the target function on many inputs
 * and prints all results, then verifies the reconstructed version matches.
 */
@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class FuzzRoundTripTests {

    private fun multiInputRoundTrip(
        builder: GoIRTestBuilder,
        funcSource: String,
        mainBody: String,
        testName: String,
    ) {
        val source = """
package main
import "fmt"
$funcSource
func main() {
$mainBody
}
""".trimIndent()

        val originalOutput = GoRunner.runSource(source)
        val prog = builder.buildFromSource(source, "main")
        GoIRSanityChecker.check(prog).assertNoErrors()
        val reconstructedCode = GoIRToGoCodeGenerator().generate(prog)
        val reconstructedOutput = try { GoRunner.runSource(reconstructedCode) } catch (e: Exception) {
            System.err.println("=== RECONSTRUCTED ($testName) ===\n$reconstructedCode")
            throw AssertionError("Failed for '$testName': ${e.message}\n$reconstructedCode", e)
        }
        assertThat(reconstructedOutput).withFailMessage {
            "Mismatch '$testName'!\nOriginal:\n$originalOutput\nReconstructed:\n$reconstructedOutput\nCode:\n$reconstructedCode"
        }.isEqualTo(originalOutput)
    }

    @Test
    fun `multi-input arithmetic`(b: GoIRTestBuilder) {
        multiInputRoundTrip(b,
            funcSource = """
func compute(a, b int) int {
    sum := a + b
    diff := a - b
    if sum > 0 {
        return sum * diff
    }
    return diff
}""",
            mainBody = """
    inputs := [][2]int{{0,0},{1,2},{-5,3},{100,-100},{-1,-1},{7,7},{0,1},{1,0},{-50,50},{999,1},{-999,-1},{42,58},{13,-13},{256,256},{1000000,1}}
    for _, p := range inputs { fmt.Println(compute(p[0], p[1])) }
""",
            testName = "multi-arithmetic")
    }

    @Test
    fun `multi-input control flow`(b: GoIRTestBuilder) {
        multiInputRoundTrip(b,
            funcSource = """
func classify(x int) int {
    if x > 100 { return 3 }
    if x > 0 { return 2 }
    if x == 0 { return 0 }
    return -1
}""",
            mainBody = """
    inputs := []int{0, 1, -1, 200, 100, 50, -50, -100, 101, 99, -999, 1000000, 2, -2}
    for _, x := range inputs { fmt.Println(classify(x)) }
""",
            testName = "multi-control-flow")
    }

    @Test
    fun `multi-input loop accumulation`(b: GoIRTestBuilder) {
        multiInputRoundTrip(b,
            funcSource = """
func sumTo(n int) int {
    if n < 0 { n = -n }
    if n > 10000 { n = 10000 }
    total := 0
    for i := 0; i <= n; i++ {
        total += i
    }
    return total
}""",
            mainBody = """
    inputs := []int{0, 1, 5, 10, 100, -5, -10, 50, 99, 1000, 9999, 10000, 10001, 2, 3}
    for _, x := range inputs { fmt.Println(sumTo(x)) }
""",
            testName = "multi-loop")
    }
}
