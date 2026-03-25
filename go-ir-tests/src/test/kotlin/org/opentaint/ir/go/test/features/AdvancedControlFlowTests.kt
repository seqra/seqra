package org.opentaint.ir.go.test.features

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.ext.findFunctionByName
import org.opentaint.ir.go.ext.findInstructions
import org.opentaint.ir.go.inst.*
import org.opentaint.ir.go.test.GoIRSanityChecker
import org.opentaint.ir.go.test.GoIRTestBuilder
import org.opentaint.ir.go.test.GoIRTestExtension

/**
 * Advanced control flow: switch, select, nested loops, break/continue, defer.
 */
@ExtendWith(GoIRTestExtension::class)
class AdvancedControlFlowTests {

    @Test
    fun `switch generates chain of If blocks`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func classify(x int) string {
                switch {
                case x > 0: return "positive"
                case x < 0: return "negative"
                default: return "zero"
                }
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("classify")!!
        // go-ssa lowers switch to if-else chains
        val ifs = fn.findInstructions<GoIRIf>()
        assertThat(ifs).hasSizeGreaterThanOrEqualTo(2)

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `nested for loops`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func matMul(n int) int {
                total := 0
                for i := 0; i < n; i++ {
                    for j := 0; j < n; j++ {
                        total += i * j
                    }
                }
                return total
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("matMul")!!
        val phis = fn.findInstructions<GoIRPhi>()
        // Outer loop variables + inner loop variables
        assertThat(phis).hasSizeGreaterThanOrEqualTo(3)

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `for range with index and value`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func sumWithWeight(xs []int) int {
                total := 0
                for i, x := range xs {
                    total += i * x
                }
                return total
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("sumWithWeight")!!
        assertThat(fn.hasBody).isTrue()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `select with multiple cases`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func fanIn(ch1, ch2 chan int) int {
                select {
                case v := <-ch1:
                    return v
                case v := <-ch2:
                    return v
                }
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("fanIn")!!
        val selects = fn.findInstructions<GoIRSelect>()
        assertThat(selects).hasSize(1)
        assertThat(selects[0].states).hasSizeGreaterThanOrEqualTo(2)

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `select with default is non-blocking`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func tryRecv(ch chan int) int {
                select {
                case v := <-ch:
                    return v
                default:
                    return -1
                }
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("tryRecv")!!
        val selects = fn.findInstructions<GoIRSelect>()
        assertThat(selects).hasSize(1)
        assertThat(selects[0].isBlocking).isFalse()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `defer and RunDefers`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            import "fmt"
            func f() {
                defer fmt.Println("deferred")
                fmt.Println("normal")
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("f")!!
        val defers = fn.findInstructions<GoIRDefer>()
        assertThat(defers).hasSize(1)
        val runDefers = fn.findInstructions<GoIRRunDefers>()
        assertThat(runDefers).isNotEmpty()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `goroutine launch`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func f(ch chan int) {
                go func() { ch <- 42 }()
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("f")!!
        val gos = fn.findInstructions<GoIRGo>()
        assertThat(gos).hasSize(1)

        GoIRSanityChecker.check(prog).assertNoErrors()
    }
}
