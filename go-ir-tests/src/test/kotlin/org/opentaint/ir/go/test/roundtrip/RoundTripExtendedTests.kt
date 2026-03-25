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
 * Extended round-trip tests covering more language features.
 */
@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripExtendedTests {

    private fun roundTrip(builder: GoIRTestBuilder, originalSource: String, testName: String) {
        val originalOutput = GoRunner.runSource(originalSource)

        val prog = builder.buildFromSource(originalSource, "main")
        GoIRSanityChecker.check(prog).assertNoErrors()

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
    fun `round-trip bitwise operations`(builder: GoIRTestBuilder) {
        roundTrip(builder, """
            package main

            import "fmt"

            func bitAnd(a, b int) int { return a & b }
            func bitOr(a, b int) int { return a | b }
            func bitXor(a, b int) int { return a ^ b }
            func bitAndNot(a, b int) int { return a &^ b }

            func main() {
                fmt.Println(bitAnd(0xFF, 0x0F))
                fmt.Println(bitOr(0xFF, 0x0F))
                fmt.Println(bitXor(0xFF, 0x0F))
                fmt.Println(bitAndNot(0xFF, 0x0F))
                fmt.Println(bitAnd(12, 10))
                fmt.Println(bitOr(12, 10))
            }
        """.trimIndent(), "bitwise")
    }

    @Test
    fun `round-trip string operations`(builder: GoIRTestBuilder) {
        roundTrip(builder, """
            package main

            import "fmt"

            func greet(name string) string {
                return "Hello, " + name + "!"
            }

            func main() {
                fmt.Println(greet("World"))
                fmt.Println(greet("Go"))
                fmt.Println(greet(""))
            }
        """.trimIndent(), "strings")
    }

    @Test
    fun `round-trip nested loops`(builder: GoIRTestBuilder) {
        roundTrip(builder, """
            package main

            import "fmt"

            func mulTable(n int) int {
                total := 0
                for i := 1; i <= n; i++ {
                    for j := 1; j <= n; j++ {
                        total += i * j
                    }
                }
                return total
            }

            func main() {
                fmt.Println(mulTable(3))
                fmt.Println(mulTable(5))
                fmt.Println(mulTable(1))
            }
        """.trimIndent(), "nested-loops")
    }

    @Test
    fun `round-trip countdown loop`(builder: GoIRTestBuilder) {
        roundTrip(builder, """
            package main

            import "fmt"

            func countdown(n int) int {
                count := 0
                for n > 0 {
                    count += n
                    n--
                }
                return count
            }

            func main() {
                fmt.Println(countdown(5))
                fmt.Println(countdown(10))
                fmt.Println(countdown(0))
            }
        """.trimIndent(), "countdown")
    }

    @Test
    fun `round-trip recursion`(builder: GoIRTestBuilder) {
        roundTrip(builder, """
            package main

            import "fmt"

            func fib(n int) int {
                if n <= 1 {
                    return n
                }
                return fib(n-1) + fib(n-2)
            }

            func main() {
                fmt.Println(fib(0))
                fmt.Println(fib(1))
                fmt.Println(fib(5))
                fmt.Println(fib(10))
            }
        """.trimIndent(), "recursion")
    }

    @Test
    fun `round-trip switch-like if chain`(builder: GoIRTestBuilder) {
        roundTrip(builder, """
            package main

            import "fmt"

            func dayType(day int) int {
                if day == 0 || day == 6 {
                    return 0
                }
                if day >= 1 && day <= 5 {
                    return 1
                }
                return -1
            }

            func main() {
                for i := 0; i < 8; i++ {
                    fmt.Println(dayType(i))
                }
            }
        """.trimIndent(), "switch-if-chain")
    }

    @Test
    fun `round-trip early return with guard`(builder: GoIRTestBuilder) {
        roundTrip(builder, """
            package main

            import "fmt"

            func safeDivide(a, b int) int {
                if b == 0 {
                    return 0
                }
                if a < 0 {
                    a = -a
                }
                return a / b
            }

            func main() {
                fmt.Println(safeDivide(10, 3))
                fmt.Println(safeDivide(10, 0))
                fmt.Println(safeDivide(-10, 3))
                fmt.Println(safeDivide(-15, -5))
            }
        """.trimIndent(), "early-return-guard")
    }

    @Test
    fun `round-trip GCD algorithm`(builder: GoIRTestBuilder) {
        roundTrip(builder, """
            package main

            import "fmt"

            func gcd(a, b int) int {
                for b != 0 {
                    a, b = b, a%b
                }
                return a
            }

            func main() {
                fmt.Println(gcd(12, 8))
                fmt.Println(gcd(100, 75))
                fmt.Println(gcd(7, 3))
                fmt.Println(gcd(1, 1))
            }
        """.trimIndent(), "gcd")
    }

    @Test
    fun `round-trip power function`(builder: GoIRTestBuilder) {
        roundTrip(builder, """
            package main

            import "fmt"

            func power(base, exp int) int {
                result := 1
                for exp > 0 {
                    if exp%2 == 1 {
                        result *= base
                    }
                    base *= base
                    exp /= 2
                }
                return result
            }

            func main() {
                fmt.Println(power(2, 10))
                fmt.Println(power(3, 5))
                fmt.Println(power(5, 0))
                fmt.Println(power(7, 1))
            }
        """.trimIndent(), "power")
    }

    @Test
    fun `round-trip min-max with multiple comparisons`(builder: GoIRTestBuilder) {
        roundTrip(builder, """
            package main

            import "fmt"

            func clamp(x, lo, hi int) int {
                if x < lo {
                    return lo
                }
                if x > hi {
                    return hi
                }
                return x
            }

            func main() {
                fmt.Println(clamp(5, 0, 10))
                fmt.Println(clamp(-5, 0, 10))
                fmt.Println(clamp(15, 0, 10))
                fmt.Println(clamp(0, 0, 10))
                fmt.Println(clamp(10, 0, 10))
            }
        """.trimIndent(), "clamp")
    }

    @Test
    fun `round-trip collatz conjecture`(builder: GoIRTestBuilder) {
        roundTrip(builder, """
            package main

            import "fmt"

            func collatzSteps(n int) int {
                steps := 0
                for n != 1 {
                    if n%2 == 0 {
                        n = n / 2
                    } else {
                        n = 3*n + 1
                    }
                    steps++
                }
                return steps
            }

            func main() {
                fmt.Println(collatzSteps(1))
                fmt.Println(collatzSteps(6))
                fmt.Println(collatzSteps(27))
            }
        """.trimIndent(), "collatz")
    }

    @Test
    fun `round-trip digit sum`(builder: GoIRTestBuilder) {
        roundTrip(builder, """
            package main

            import "fmt"

            func digitSum(n int) int {
                if n < 0 {
                    n = -n
                }
                sum := 0
                for n > 0 {
                    sum += n % 10
                    n /= 10
                }
                return sum
            }

            func main() {
                fmt.Println(digitSum(123))
                fmt.Println(digitSum(9999))
                fmt.Println(digitSum(0))
                fmt.Println(digitSum(-456))
            }
        """.trimIndent(), "digit-sum")
    }

    @Test
    fun `round-trip isPrime`(builder: GoIRTestBuilder) {
        roundTrip(builder, """
            package main

            import "fmt"

            func isPrime(n int) bool {
                if n < 2 {
                    return false
                }
                for i := 2; i*i <= n; i++ {
                    if n%i == 0 {
                        return false
                    }
                }
                return true
            }

            func main() {
                for i := 0; i < 20; i++ {
                    if isPrime(i) {
                        fmt.Println(i)
                    }
                }
            }
        """.trimIndent(), "is-prime")
    }
}
