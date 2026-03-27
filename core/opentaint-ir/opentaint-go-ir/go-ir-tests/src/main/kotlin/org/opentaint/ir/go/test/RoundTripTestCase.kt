package org.opentaint.ir.go.test

import kotlin.random.Random

/**
 * A single round-trip test case: a pure function + main invocations that print expected output.
 *
 * @property name unique test name within the batch
 * @property functions one or more Go function definitions (no package/import)
 * @property mainBody Go statements for main() that call the functions and print output via fmt.Println
 */
data class RoundTripTestCase(
    val name: String,
    val functions: String,
    val mainBody: String,
) {
    companion object {
        private val RNG = Random(42)

        /**
         * Creates a round-trip test case with explicit inputs plus 10 random inputs.
         *
         * @param name unique test name
         * @param functions Go function definitions (no package/import)
         * @param callExpr template for calling the function with args, e.g. "rt_add(%s, %s)"
         *                 — `%s` is replaced by each argument. Or use positional like `%1$s`, `%2$s`.
         * @param inputs list of explicit inputs — each input is a list of Go-literal arguments
         * @param argCount number of arguments the function takes (for random input generation)
         * @param randomRange range for random ints (default -1000..1000)
         * @param extraRandomInputs number of random inputs to append (default 10)
         */
        fun withInputs(
            name: String,
            functions: String,
            callExpr: String,
            inputs: List<List<String>>,
            argCount: Int = inputs.firstOrNull()?.size ?: 1,
            randomRange: IntRange = -1000..1000,
            extraRandomInputs: Int = 10,
        ): RoundTripTestCase {
            val allInputs = inputs.toMutableList()
            // Add random inputs
            repeat(extraRandomInputs) {
                val args = (0 until argCount).map { RNG.nextInt(randomRange.first, randomRange.last + 1).toString() }
                allInputs.add(args)
            }
            val mainBody = allInputs.joinToString("\n") { args ->
                val formatted = if (callExpr.contains("%1\$")) {
                    // Positional format: %1$s, %2$s
                    callExpr.format(*args.toTypedArray())
                } else {
                    // Simple sequential %s replacement
                    var result = callExpr
                    for (arg in args) {
                        result = result.replaceFirst("%s", arg)
                    }
                    result
                }
                "fmt.Println($formatted)"
            }
            return RoundTripTestCase(name, functions, mainBody)
        }
    }
}
