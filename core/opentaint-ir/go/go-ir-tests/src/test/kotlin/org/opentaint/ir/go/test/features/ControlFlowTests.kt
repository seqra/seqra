package org.opentaint.ir.go.test.features

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.ext.findFunctionByName
import org.opentaint.ir.go.ext.findExpressions
import org.opentaint.ir.go.ext.findInstructions
import org.opentaint.ir.go.expr.*
import org.opentaint.ir.go.inst.*
import org.opentaint.ir.go.test.GoIRSanityChecker
import org.opentaint.ir.go.test.GoIRTestBuilder
import org.opentaint.ir.go.test.GoIRTestExtension

/**
 * Tests for control flow: if/else, for loops, switch, select, panic/recover.
 */
@ExtendWith(GoIRTestExtension::class)
class ControlFlowTests {

    @Test
    fun `if-else generates If instruction with two successors`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func abs(x int) int {
                if x < 0 { return -x }
                return x
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("abs")!!
        val body = fn.body!!

        // Should have If instruction
        val ifs = fn.findInstructions<GoIRIf>()
        assertThat(ifs).isNotEmpty()

        // If's block should have 2 successors
        val ifBlock = ifs[0].block
        assertThat(ifBlock.successors).hasSize(2)

        // Multiple blocks (at least: entry with If, true branch, false/merge)
        assertThat(body.blocks.size).isGreaterThanOrEqualTo(3)

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `for loop generates phi and back-edge`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func sum(n int) int {
                s := 0
                for i := 0; i < n; i++ {
                    s += i
                }
                return s
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("sum")!!
        val body = fn.body!!

        // Loops generate Phi nodes for loop variables
        val phis = fn.findInstructions<GoIRPhi>()
        assertThat(phis).isNotEmpty()

        // Should have Jump (back-edge in loop)
        val jumps = fn.findInstructions<GoIRJump>()
        assertThat(jumps).isNotEmpty()

        // Should have If (loop condition check)
        val ifs = fn.findInstructions<GoIRIf>()
        assertThat(ifs).isNotEmpty()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `for-range over slice`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func sumSlice(xs []int) int {
                total := 0
                for _, x := range xs {
                    total += x
                }
                return total
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("sumSlice")!!

        // Range loop generates Range instruction
        val ranges = fn.findExpressions<GoIRRangeExpr>()
        // go-ssa may or may not use Range depending on lowering, but Next should appear
        val nexts = fn.findExpressions<GoIRNextExpr>()
        // At least some loop control flow should exist
        val phis = fn.findInstructions<GoIRPhi>()
        assertThat(phis.size + ranges.size + nexts.size).isGreaterThan(0)

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `panic generates Panic terminator`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func mustPositive(x int) int {
                if x <= 0 { panic("must be positive") }
                return x
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("mustPositive")!!
        val panics = fn.findInstructions<GoIRPanic>()
        assertThat(panics).isNotEmpty()

        // Panic blocks have no successors
        for (p in panics) {
            assertThat(p.block.successors).isEmpty()
        }

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `multiple returns`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func classify(x int) string {
                if x > 0 { return "positive" }
                if x < 0 { return "negative" }
                return "zero"
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("classify")!!
        val returns = fn.findInstructions<GoIRReturn>()
        assertThat(returns).hasSize(3)

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `inst graph successors are consistent`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func branch(x int) int {
                if x > 0 { return x }
                return -x
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("branch")!!
        val body = fn.body!!
        val instGraph = body.instGraph

        // Entry instruction
        assertThat(instGraph.entry).isEqualTo(body.instructions.first())

        // Exits exist
        assertThat(instGraph.exits).isNotEmpty()

        // Each exit has no successors
        for (exit in instGraph.exits) {
            assertThat(instGraph.successors(exit)).isEmpty()
        }

        // Non-exit non-terminator instructions have at least one successor
        for (inst in body.instructions) {
            if (inst !is GoIRTerminator) {
                assertThat(instGraph.successors(inst))
                    .withFailMessage("Non-terminator inst#${inst.index} should have successors")
                    .isNotEmpty()
            }
        }

        GoIRSanityChecker.check(prog).assertNoErrors()
    }
}
