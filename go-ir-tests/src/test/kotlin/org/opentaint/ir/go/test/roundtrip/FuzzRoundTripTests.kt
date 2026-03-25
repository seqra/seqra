package org.opentaint.ir.go.test.roundtrip

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.codegen.GoIRToGoCodeGenerator
import org.opentaint.ir.go.test.GoIRTestBuilder
import org.opentaint.ir.go.test.GoIRTestExtension
import org.opentaint.ir.go.test.GoIRSanityChecker
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Fuzz-based round-trip tests (Strategy 3 extension).
 *
 * Uses Go's built-in fuzzing to generate random inputs and compare
 * original vs reconstructed function outputs.
 *
 * Each test creates a Go module with:
 * - The target function (single int -> int or (int, int) -> int)
 * - A fuzz test that calls the function with random inputs
 *
 * We run fuzzing on both original and reconstructed separately,
 * then compare deterministic outputs on seed corpus.
 *
 * Run with: ./gradlew :go-ir-tests:fuzzTest
 */
@Tag("fuzz")
@ExtendWith(GoIRTestExtension::class)
class FuzzRoundTripTests {

    @Test
    fun `fuzz arithmetic functions`(builder: GoIRTestBuilder) {
        fuzzRoundTrip(
            builder,
            targetFunc = "compute",
            funcSource = """
                func compute(a, b int) int {
                    sum := a + b
                    diff := a - b
                    if sum > 0 {
                        return sum * diff
                    }
                    return diff
                }
            """.trimIndent(),
            paramTypes = listOf("int", "int"),
            seeds = listOf(listOf("0", "0"), listOf("1", "2"), listOf("-5", "3"), listOf("100", "-100")),
            testName = "fuzz-arithmetic",
        )
    }

    @Test
    fun `fuzz control flow`(builder: GoIRTestBuilder) {
        fuzzRoundTrip(
            builder,
            targetFunc = "classify",
            funcSource = """
                func classify(x int) int {
                    if x > 100 {
                        return 3
                    }
                    if x > 0 {
                        return 2
                    }
                    if x == 0 {
                        return 0
                    }
                    return -1
                }
            """.trimIndent(),
            paramTypes = listOf("int"),
            seeds = listOf(listOf("0"), listOf("1"), listOf("-1"), listOf("200")),
            testName = "fuzz-control-flow",
        )
    }

    @Test
    fun `fuzz loop accumulation`(builder: GoIRTestBuilder) {
        fuzzRoundTrip(
            builder,
            targetFunc = "sumTo",
            funcSource = """
                func sumTo(n int) int {
                    if n < 0 { n = -n }
                    if n > 10000 { n = 10000 }
                    total := 0
                    for i := 0; i <= n; i++ {
                        total += i
                    }
                    return total
                }
            """.trimIndent(),
            paramTypes = listOf("int"),
            seeds = listOf(listOf("0"), listOf("10"), listOf("100"), listOf("-5")),
            testName = "fuzz-loop",
        )
    }

    private fun fuzzRoundTrip(
        builder: GoIRTestBuilder,
        targetFunc: String,
        funcSource: String,
        paramTypes: List<String>,
        seeds: List<List<String>>,
        testName: String,
    ) {
        // Build the function as a full program for IR extraction
        val fullOriginal = """
            package main
            import "fmt"
            $funcSource
            func main() {
                ${seeds.joinToString("\n") { args ->
                    "fmt.Println($targetFunc(${args.joinToString(", ")}))"
                }}
            }
        """.trimIndent()

        // 1. Build IR and generate reconstructed code
        val prog = builder.buildFromSource(fullOriginal, "main")
        GoIRSanityChecker.check(prog).assertNoErrors()
        val reconstructedCode = GoIRToGoCodeGenerator().generate(prog)

        // 2. Extract just the target function from reconstructed code
        // The reconstructed code has the function with SSA variables
        val reconFunc = extractFunction(reconstructedCode, targetFunc)
            ?: throw AssertionError("Could not find function '$targetFunc' in reconstructed code:\n$reconstructedCode")

        // 3. Create fuzz test module
        val tmpDir = Files.createTempDirectory("goir-fuzz-$testName")
        tmpDir.resolve("go.mod").toFile().writeText("module fuzztest\ngo 1.22\n")

        // Write original function
        tmpDir.resolve("original.go").toFile().writeText("""
            package fuzztest

            $funcSource
        """.trimIndent())

        // Write reconstructed function (with renamed function)
        val reconFuncRenamed = reconFunc
            .replaceFirst("func $targetFunc(", "func recon_$targetFunc(")
        tmpDir.resolve("reconstructed.go").toFile().writeText("""
            package fuzztest

            $reconFuncRenamed
        """.trimIndent())

        // Write fuzz test
        val fuzzParams = paramTypes.mapIndexed { i, t -> "a$i $t" }.joinToString(", ")
        val callArgs = paramTypes.indices.joinToString(", ") { "a$it" }
        val seedAdds = seeds.joinToString("\n\t") { args ->
            "f.Add(${args.joinToString(", ")})"
        }

        tmpDir.resolve("fuzz_test.go").toFile().writeText("""
            package fuzztest

            import "testing"

            func FuzzRoundTrip(f *testing.F) {
                $seedAdds
                f.Fuzz(func(t *testing.T, $fuzzParams) {
                    orig := $targetFunc($callArgs)
                    recon := recon_$targetFunc($callArgs)
                    if orig != recon {
                        t.Errorf("mismatch on input (%v): orig=%v, recon=%v", []interface{}{$callArgs}, orig, recon)
                    }
                })
            }
        """.trimIndent())

        // 4. Run go test -fuzz
        val fuzzDuration = System.getProperty("goir.fuzz.duration", "5s")
        val proc = ProcessBuilder(
            "go", "test", "-fuzz=FuzzRoundTrip", "-fuzztime=$fuzzDuration"
        )
            .directory(tmpDir.toFile())
            .redirectErrorStream(true)
            .start()

        val output = proc.inputStream.readAllBytes().decodeToString()
        val exited = proc.waitFor(120, TimeUnit.SECONDS)

        tmpDir.toFile().deleteRecursively()

        if (!exited) {
            proc.destroyForcibly()
            throw AssertionError("Fuzz test timed out for '$testName'")
        }

        assertThat(proc.exitValue())
            .withFailMessage {
                "Fuzz test failed for '$testName':\n$output\n\nReconstructed func:\n$reconFunc"
            }
            .isEqualTo(0)
    }

    /**
     * Extract a function body from generated Go code.
     * Finds `func funcName(` and reads until the matching closing brace.
     */
    private fun extractFunction(code: String, funcName: String): String? {
        val pattern = "func $funcName("
        val startIdx = code.indexOf(pattern)
        if (startIdx == -1) return null

        // Find the opening brace
        val braceIdx = code.indexOf('{', startIdx)
        if (braceIdx == -1) return null

        // Count braces to find the end
        var depth = 0
        var endIdx = braceIdx
        for (i in braceIdx until code.length) {
            when (code[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        endIdx = i
                        break
                    }
                }
            }
        }

        return code.substring(startIdx, endIdx + 1)
    }
}
