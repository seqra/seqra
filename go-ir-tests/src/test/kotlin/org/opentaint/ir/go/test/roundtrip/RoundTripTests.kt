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
 * Round-trip CFG tests (Strategy 3):
 * 1. Read original Go sample file
 * 2. Compile & run original -> capture output_A
 * 3. Build GoIR via Go server
 * 4. Convert GoIR CFG -> Go source (Kotlin code generator)
 * 5. Compile & run reconstructed -> capture output_B
 * 6. Assert output_A == output_B
 */
@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripTests {

    private fun roundTrip(builder: GoIRTestBuilder, originalSource: String, testName: String) {
        val originalOutput = GoRunner.runSource(originalSource)

        val prog = builder.buildFromSource(originalSource, "main")
        val sanityResult = GoIRSanityChecker.check(prog)
        sanityResult.assertNoErrors()

        val codegen = GoIRToGoCodeGenerator()
        val reconstructedCode = codegen.generate(prog)

        val reconstructedOutput = try {
            GoRunner.runSource(reconstructedCode)
        } catch (e: Exception) {
            System.err.println("=== RECONSTRUCTED CODE ($testName) ===")
            System.err.println(reconstructedCode)
            System.err.println("=== END RECONSTRUCTED CODE ===")
            throw AssertionError(
                "Reconstructed code failed to compile/run for '$testName':\n${e.message}\n\n" +
                "Reconstructed source:\n$reconstructedCode", e
            )
        }

        assertThat(reconstructedOutput)
            .withFailMessage {
                "Output mismatch for '$testName'!\n" +
                "Original output:\n$originalOutput\n" +
                "Reconstructed output:\n$reconstructedOutput\n" +
                "Reconstructed source:\n$reconstructedCode"
            }
            .isEqualTo(originalOutput)
    }

    @Test
    fun `round-trip arithmetic`(builder: GoIRTestBuilder) {
        roundTrip(builder, """
            package main

            import "fmt"

            func add(a, b int) int {
                return a + b
            }

            func sub(a, b int) int {
                return a - b
            }

            func mul(a, b int) int {
                return a * b
            }

            func main() {
                fmt.Println(add(3, 4))
                fmt.Println(sub(10, 3))
                fmt.Println(mul(5, 6))
                fmt.Println(add(sub(10, 3), mul(2, 3)))
            }
        """.trimIndent(), "arithmetic")
    }

    @Test
    fun `round-trip if-else`(builder: GoIRTestBuilder) {
        roundTrip(builder, """
            package main

            import "fmt"

            func abs(x int) int {
                if x < 0 {
                    return -x
                }
                return x
            }

            func max(a, b int) int {
                if a > b {
                    return a
                }
                return b
            }

            func main() {
                fmt.Println(abs(-5))
                fmt.Println(abs(3))
                fmt.Println(abs(0))
                fmt.Println(max(3, 7))
                fmt.Println(max(10, 2))
            }
        """.trimIndent(), "if-else")
    }

    @Test
    fun `round-trip boolean logic`(builder: GoIRTestBuilder) {
        roundTrip(builder, """
            package main

            import "fmt"

            func isPositive(x int) bool {
                return x > 0
            }

            func both(a, b bool) bool {
                if a {
                    if b {
                        return true
                    }
                }
                return false
            }

            func main() {
                fmt.Println(isPositive(5))
                fmt.Println(isPositive(-3))
                fmt.Println(isPositive(0))
                fmt.Println(both(true, true))
                fmt.Println(both(true, false))
                fmt.Println(both(false, true))
            }
        """.trimIndent(), "boolean-logic")
    }

    @Test
    fun `round-trip for loop with accumulator`(builder: GoIRTestBuilder) {
        roundTrip(builder, """
            package main

            import "fmt"

            func sumTo(n int) int {
                total := 0
                for i := 1; i <= n; i++ {
                    total += i
                }
                return total
            }

            func factorial(n int) int {
                result := 1
                for i := 2; i <= n; i++ {
                    result *= i
                }
                return result
            }

            func main() {
                fmt.Println(sumTo(10))
                fmt.Println(sumTo(0))
                fmt.Println(sumTo(1))
                fmt.Println(factorial(5))
                fmt.Println(factorial(1))
            }
        """.trimIndent(), "for-loop-accumulator")
    }

    @Test
    fun `round-trip nested if`(builder: GoIRTestBuilder) {
        roundTrip(builder, """
            package main

            import "fmt"

            func classify(x int) int {
                if x > 0 {
                    if x > 100 {
                        return 3
                    }
                    return 2
                }
                if x == 0 {
                    return 0
                }
                return -1
            }

            func main() {
                fmt.Println(classify(200))
                fmt.Println(classify(50))
                fmt.Println(classify(0))
                fmt.Println(classify(-10))
            }
        """.trimIndent(), "nested-if")
    }

    @Test
    fun `round-trip multiple returns`(builder: GoIRTestBuilder) {
        roundTrip(builder, """
            package main

            import "fmt"

            func divide(a, b int) int {
                if b == 0 {
                    return 0
                }
                return a / b
            }

            func main() {
                fmt.Println(divide(10, 3))
                fmt.Println(divide(10, 0))
                fmt.Println(divide(100, 5))
                fmt.Println(divide(-15, 3))
            }
        """.trimIndent(), "multiple-returns")
    }
}
