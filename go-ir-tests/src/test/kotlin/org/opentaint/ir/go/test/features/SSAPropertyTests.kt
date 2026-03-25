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
 * Tests verifying SSA-specific properties: phi nodes, dominance, unique names.
 */
@ExtendWith(GoIRTestExtension::class)
class SSAPropertyTests {

    @Test
    fun `phi nodes at loop header`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func sum(n int) int {
                total := 0
                for i := 0; i < n; i++ { total += i }
                return total
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("sum")!!
        val phis = fn.findInstructions<GoIRPhi>()
        assertThat(phis).hasSizeGreaterThanOrEqualTo(2) // i and total

        // Each phi should have 2 edges (init + back-edge)
        for (phi in phis) {
            assertThat(phi.edges).hasSize(phi.block.predecessors.size)
        }

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `phi at merge after if-else`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func f(x int) int {
                var result int
                if x > 0 {
                    result = x
                } else {
                    result = -x
                }
                return result
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("f")!!
        val phis = fn.findInstructions<GoIRPhi>()
        // SSA should have a phi at the merge point
        assertThat(phis).isNotEmpty()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `unique SSA value names within function`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func complex(a, b, c int) int {
                x := a + b
                y := b + c
                z := x * y
                if z > 0 {
                    return z + a
                }
                return z - b
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("complex")!!
        val body = fn.body!!
        val names = body.instructions.filterIsInstance<GoIRValueInst>()
            .map { it.name }
            .filter { it.isNotEmpty() }

        // All names should be unique (SSA property)
        assertThat(names).doesNotHaveDuplicates()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `entry block has no predecessors`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func f(x int) int {
                if x > 0 { return x }
                return -x
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("f")!!
        val entry = fn.body!!.entryBlock
        assertThat(entry.predecessors).isEmpty()
        assertThat(entry.index).isEqualTo(0)

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `phis only at beginning of blocks`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func f(n int) int {
                s := 0
                for i := 1; i <= n; i++ { s += i }
                return s
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("f")!!
        val body = fn.body!!
        for (block in body.blocks) {
            var seenNonPhi = false
            for (inst in block.instructions) {
                if (inst is GoIRPhi) {
                    assertThat(seenNonPhi)
                        .withFailMessage("Phi after non-phi in block ${block.index}")
                        .isFalse()
                } else {
                    seenNonPhi = true
                }
            }
        }

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `instruction indices are sequential`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func f(a, b int) int {
                sum := a + b
                prod := a * b
                if sum > prod { return sum }
                return prod
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("f")!!
        val body = fn.body!!
        body.instructions.forEachIndexed { idx, inst ->
            assertThat(inst.index).isEqualTo(idx)
        }

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `block graph exit blocks have Return or Panic`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func f(x int) int {
                if x < 0 { panic("negative") }
                return x
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("f")!!
        val blockGraph = fn.body!!.blockGraph
        val exits = blockGraph.exitBlocks()
        assertThat(exits).isNotEmpty()
        for (exit in exits) {
            assertThat(exit.terminator).isInstanceOfAny(GoIRReturn::class.java, GoIRPanic::class.java)
        }

        GoIRSanityChecker.check(prog).assertNoErrors()
    }
}
