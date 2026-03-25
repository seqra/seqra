package org.opentaint.ir.go.test

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
)
