package org.opentaint.ir.go.test.features

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.expr.*
import org.opentaint.ir.go.ext.findExpressions
import org.opentaint.ir.go.ext.findFunctionByName
import org.opentaint.ir.go.ext.findInstructions
import org.opentaint.ir.go.inst.*
import org.opentaint.ir.go.test.GoIRSanityChecker
import org.opentaint.ir.go.test.GoIRTestBuilder
import org.opentaint.ir.go.test.GoIRTestExtension

/**
 * Tests for slices, arrays, maps — collection operations.
 */
@ExtendWith(GoIRTestExtension::class)
class CollectionTests {

    @Test
    fun `make slice`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func f(n int) []int {
                return make([]int, n)
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("f")!!
        val makeSlices = fn.findExpressions<GoIRMakeSliceExpr>()
        assertThat(makeSlices).hasSize(1)

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `slice index and index addr`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func read(s []int, i int) int { return s[i] }
            func write(s []int, i int, v int) { s[i] = v }
        """.trimIndent())

        val readFn = prog.findFunctionByName("read")!!
        val indices = readFn.findExpressions<GoIRIndexExpr>()
        // go-ssa may use Index or IndexAddr
        val indexAddrs = readFn.findExpressions<GoIRIndexAddrExpr>()
        assertThat(indices.size + indexAddrs.size).isGreaterThan(0)

        val writeFn = prog.findFunctionByName("write")!!
        val writeIndexAddrs = writeFn.findExpressions<GoIRIndexAddrExpr>()
        assertThat(writeIndexAddrs).isNotEmpty()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `slice expression`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func subSlice(s []int) []int { return s[1:3] }
        """.trimIndent())

        val fn = prog.findFunctionByName("subSlice")!!
        val slices = fn.findExpressions<GoIRSliceExpr>()
        assertThat(slices).hasSize(1)

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `make map`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func f() map[string]int {
                return make(map[string]int)
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("f")!!
        val makeMaps = fn.findExpressions<GoIRMakeMapExpr>()
        assertThat(makeMaps).hasSize(1)

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `map lookup and update`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func get(m map[string]int, k string) int { return m[k] }
            func set(m map[string]int, k string, v int) { m[k] = v }
        """.trimIndent())

        val getFn = prog.findFunctionByName("get")!!
        val lookups = getFn.findExpressions<GoIRLookupExpr>()
        assertThat(lookups).hasSize(1)

        val setFn = prog.findFunctionByName("set")!!
        val updates = setFn.findInstructions<GoIRMapUpdate>()
        assertThat(updates).hasSize(1)

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `map lookup comma-ok`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func getOk(m map[string]int, k string) (int, bool) {
                v, ok := m[k]
                return v, ok
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("getOk")!!
        val lookups = fn.findExpressions<GoIRLookupExpr>()
        assertThat(lookups).hasSize(1)
        assertThat(lookups[0].commaOk).isTrue()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `fixed-size array`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func f() [3]int {
                return [3]int{1, 2, 3}
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("f")!!
        assertThat(fn.hasBody).isTrue()
        GoIRSanityChecker.check(prog).assertNoErrors()
    }
}
