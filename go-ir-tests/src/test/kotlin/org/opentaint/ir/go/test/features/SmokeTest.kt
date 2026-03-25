package org.opentaint.ir.go.test.features

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.ext.findFunctionByName
import org.opentaint.ir.go.inst.GoIRReturn
import org.opentaint.ir.go.test.GoIRSanityChecker
import org.opentaint.ir.go.test.GoIRTestBuilder
import org.opentaint.ir.go.test.GoIRTestExtension

/**
 * Smoke tests: verify the full pipeline works end-to-end.
 */
@ExtendWith(GoIRTestExtension::class)
class SmokeTest {

    @Test
    fun `minimal Go function produces valid IR`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func hello() int { return 42 }
        """.trimIndent())

        assertThat(prog.packages).isNotEmpty
        val fn = prog.findFunctionByName("hello")
        assertThat(fn).isNotNull
        assertThat(fn!!.hasBody).isTrue()
        assertThat(fn.body!!.instructions).isNotEmpty()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `function with parameters`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func add(a, b int) int { return a + b }
        """.trimIndent())

        val fn = prog.findFunctionByName("add")
        assertThat(fn).isNotNull
        assertThat(fn!!.params).hasSize(2)
        assertThat(fn.params[0].name).isEqualTo("a")
        assertThat(fn.params[1].name).isEqualTo("b")
        assertThat(fn.hasBody).isTrue()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `multiple functions in same package`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func foo() int { return 1 }
            func bar() int { return 2 }
            func baz() int { return 3 }
        """.trimIndent())

        assertThat(prog.findFunctionByName("foo")).isNotNull
        assertThat(prog.findFunctionByName("bar")).isNotNull
        assertThat(prog.findFunctionByName("baz")).isNotNull

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `function body has valid CFG structure`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func identity(x int) int { return x }
        """.trimIndent())

        val fn = prog.findFunctionByName("identity")!!
        val body = fn.body!!

        // Should have at least one block
        assertThat(body.blocks).isNotEmpty()

        // Entry block
        assertThat(body.entryBlock).isEqualTo(body.blocks[0])

        // Instructions should be indexed sequentially
        body.instructions.forEachIndexed { idx, inst ->
            assertThat(inst.index).isEqualTo(idx)
        }

        // Should end with a return
        val lastBlock = body.blocks.last()
        assertThat(lastBlock.terminator).isInstanceOf(GoIRReturn::class.java)

        // Block graph and inst graph exist
        assertThat(body.blockGraph).isNotNull
        assertThat(body.instGraph).isNotNull

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `package metadata is populated`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func F() {}
        """.trimIndent())

        val pkg = prog.packages.values.find { it.name == "p" }
        assertThat(pkg).isNotNull
        assertThat(pkg!!.name).isEqualTo("p")
        assertThat(pkg.importPath).isNotEmpty()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }
}
