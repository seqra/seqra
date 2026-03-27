package org.opentaint.ir.go.test.features

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.ext.findExpressions
import org.opentaint.ir.go.ext.findFunctionByName
import org.opentaint.ir.go.ext.findInstructions
import org.opentaint.ir.go.expr.*
import org.opentaint.ir.go.inst.*
import org.opentaint.ir.go.test.GoIRSanityChecker
import org.opentaint.ir.go.test.GoIRTestBuilder
import org.opentaint.ir.go.test.GoIRTestExtension
import org.opentaint.ir.go.type.GoIRUnaryOp

/**
 * Tests for pointers, memory, allocation.
 */
@ExtendWith(GoIRTestExtension::class)
class PointerMemoryTests {

    @Test
    fun `stack allocation with address-of`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func f() *int {
                x := 42
                return &x
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("f")!!
        val allocs = fn.findExpressions<GoIRAllocExpr>()
        assertThat(allocs).isNotEmpty()
        // Since x escapes (returned), it should be heap-allocated
        assertThat(allocs.any { it.isHeap }).isTrue()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `heap allocation with new`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func f() *int {
                p := new(int)
                *p = 42
                return p
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("f")!!
        val allocs = fn.findExpressions<GoIRAllocExpr>()
        assertThat(allocs.any { it.isHeap }).isTrue()

        // Should have a Store instruction for *p = 42
        val stores = fn.findInstructions<GoIRStore>()
        assertThat(stores).isNotEmpty()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `pointer dereference`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func deref(p *int) int {
                return *p
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("deref")!!
        val unops = fn.findExpressions<GoIRUnOpExpr>()
        assertThat(unops.any { it.op == GoIRUnaryOp.DEREF }).isTrue()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `composite literal struct on heap`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            type Point struct { X, Y int }
            func makePoint(x, y int) *Point {
                return &Point{X: x, Y: y}
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("makePoint")!!
        val allocs = fn.findExpressions<GoIRAllocExpr>()
        assertThat(allocs).isNotEmpty()
        val fieldAddrs = fn.findExpressions<GoIRFieldAddrExpr>()
        assertThat(fieldAddrs).isNotEmpty()
        val stores = fn.findInstructions<GoIRStore>()
        assertThat(stores).isNotEmpty()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `global variable access`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            var counter int
            func increment() {
                counter++
            }
            func getCounter() int {
                return counter
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("getCounter")!!
        assertThat(fn.hasBody).isTrue()
        // Global access typically involves UnOp(DEREF) on a global address
        val unops = fn.findExpressions<GoIRUnOpExpr>()
        assertThat(unops.any { it.op == GoIRUnaryOp.DEREF }).isTrue()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }
}
