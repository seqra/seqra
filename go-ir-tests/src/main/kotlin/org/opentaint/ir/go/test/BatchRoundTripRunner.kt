package org.opentaint.ir.go.test

import org.opentaint.ir.go.codegen.GoIRToGoCodeGenerator

/**
 * Batches multiple [RoundTripTestCase]s into a single Go program, so we only need
 * 2 `go run` invocations per batch (original + reconstructed) instead of 2 per test.
 *
 * Each test case's output is delimited with `===CASE:name===` markers so we can
 * split and compare per-case results individually.
 *
 * Usage in test class:
 * 1. Define test cases via [RoundTripTestCase]
 * 2. Call [runBatch] once in @BeforeAll or lazily
 * 3. Each @Test asserts its case from the result map
 */
object BatchRoundTripRunner {

    data class BatchResult(
        /** Per-case original output (keyed by case name) */
        val originalOutputs: Map<String, String>,
        /** Per-case reconstructed output (keyed by case name) */
        val reconstructedOutputs: Map<String, String>,
        /** Full reconstructed source code (for debugging) */
        val reconstructedCode: String,
    )

    /**
     * Merges all test cases into one Go program, runs original and reconstructed,
     * returns per-case outputs.
     */
    fun runBatch(cases: List<RoundTripTestCase>, builder: GoIRTestBuilder): BatchResult {
        val source = buildBatchSource(cases)
        val originalOutput = GoRunner.runSource(source)
        val originalMap = parseTaggedOutput(originalOutput, cases)

        val prog = builder.buildFromSource(source, "main")
        GoIRSanityChecker.check(prog).assertNoErrors()
        val reconstructedCode = GoIRToGoCodeGenerator().generate(prog)

        val reconstructedOutput = try {
            GoRunner.runSource(reconstructedCode)
        } catch (e: Exception) {
            System.err.println("=== BATCH RECONSTRUCTED CODE ===")
            System.err.println(reconstructedCode)
            throw AssertionError("Reconstructed batch code failed to compile/run:\n${e.message}", e)
        }
        val reconstructedMap = parseTaggedOutput(reconstructedOutput, cases)

        return BatchResult(originalMap, reconstructedMap, reconstructedCode)
    }

    private fun buildBatchSource(cases: List<RoundTripTestCase>): String {
        val sb = StringBuilder()
        sb.appendLine("package main")
        sb.appendLine()
        sb.appendLine("import \"fmt\"")
        sb.appendLine()
        // Suppress unused import warning — fmt is always used
        sb.appendLine("var _ = fmt.Println")
        sb.appendLine()

        // All function definitions (with unique prefixes to avoid name collisions)
        for (case in cases) {
            sb.appendLine("// --- ${case.name} ---")
            sb.appendLine(case.functions)
            sb.appendLine()
        }

        sb.appendLine("func main() {")
        for (case in cases) {
            sb.appendLine("\tfmt.Println(\"===CASE:${case.name}===\")")
            // Wrap each case body in a block to isolate variable declarations
            sb.appendLine("\t{")
            for (line in case.mainBody.lines()) {
                if (line.isNotBlank()) sb.appendLine("\t\t$line")
            }
            sb.appendLine("\t}")
        }
        sb.appendLine("}")

        return sb.toString()
    }

    private fun parseTaggedOutput(output: String, cases: List<RoundTripTestCase>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val lines = output.lines()
        var currentCase: String? = null
        val currentLines = mutableListOf<String>()

        for (line in lines) {
            val marker = "===CASE:"
            if (line.startsWith(marker) && line.endsWith("===")) {
                // Flush previous case
                if (currentCase != null) {
                    result[currentCase] = currentLines.joinToString("\n")
                    currentLines.clear()
                }
                currentCase = line.removePrefix(marker).removeSuffix("===")
            } else if (currentCase != null) {
                currentLines.add(line)
            }
        }
        // Flush last case
        if (currentCase != null) {
            result[currentCase] = currentLines.joinToString("\n")
        }

        return result
    }
}
