package org.opentaint.ir.go.test.features

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.ext.findFunctionByName
import org.opentaint.ir.go.ext.findInstructions
import org.opentaint.ir.go.inst.GoIRCall
import org.opentaint.ir.go.inst.GoIRMakeClosure
import org.opentaint.ir.go.test.GoIRSanityChecker
import org.opentaint.ir.go.test.GoIRTestBuilder
import org.opentaint.ir.go.test.GoIRTestExtension
import org.opentaint.ir.go.type.GoIRCallMode

/**
 * Tests for functions, closures, anonymous functions, higher-order functions.
 */
@ExtendWith(GoIRTestExtension::class)
class FunctionClosureTests {

    @Test
    fun `direct function call generates Call with DIRECT mode`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func double(x int) int { return x * 2 }
            func callDouble() int { return double(21) }
        """.trimIndent())

        val fn = prog.findFunctionByName("callDouble")!!
        val calls = fn.findInstructions<GoIRCall>()
        assertThat(calls).isNotEmpty()

        val directCall = calls.find { it.call.mode == GoIRCallMode.DIRECT }
        assertThat(directCall).isNotNull

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `closure captures variable with MakeClosure`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func counter() func() int {
                n := 0
                return func() int { n++; return n }
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("counter")!!

        // MakeClosure should be generated
        val closures = fn.findInstructions<GoIRMakeClosure>()
        assertThat(closures).isNotEmpty()

        // The closure should have bindings (captured variables)
        val closure = closures[0]
        assertThat(closure.bindings).isNotEmpty()

        // The anonymous function should have free variables
        val anonFn = closure.fn
        assertThat(anonFn.freeVars).isNotEmpty()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `multiple return values`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func divmod(a, b int) (int, int) {
                return a / b, a % b
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("divmod")!!
        assertThat(fn.signature.results).hasSize(2)
        assertThat(fn.hasBody).isTrue()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `variadic function`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func sumAll(nums ...int) int {
                total := 0
                for _, n := range nums {
                    total += n
                }
                return total
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("sumAll")!!
        assertThat(fn.signature.isVariadic).isTrue()
        assertThat(fn.hasBody).isTrue()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `anonymous function as argument`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func apply(f func(int) int, x int) int {
                return f(x)
            }
            func useApply() int {
                return apply(func(x int) int { return x * 2 }, 5)
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("useApply")!!
        assertThat(fn.hasBody).isTrue()

        // Should contain a call
        val calls = fn.findInstructions<GoIRCall>()
        assertThat(calls).isNotEmpty()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `method call on struct`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            type Adder struct { base int }
            func (a Adder) Add(x int) int { return a.base + x }
            func use() int {
                a := Adder{base: 10}
                return a.Add(5)
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("use")!!
        val calls = fn.findInstructions<GoIRCall>()
        assertThat(calls).isNotEmpty()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }
}
