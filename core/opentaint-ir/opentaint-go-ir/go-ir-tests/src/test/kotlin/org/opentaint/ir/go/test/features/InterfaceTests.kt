package org.opentaint.ir.go.test.features

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.ext.findFunctionByName
import org.opentaint.ir.go.ext.findExpressions
import org.opentaint.ir.go.ext.findNamedTypeByName
import org.opentaint.ir.go.expr.*
import org.opentaint.ir.go.test.GoIRSanityChecker
import org.opentaint.ir.go.test.GoIRTestBuilder
import org.opentaint.ir.go.test.GoIRTestExtension
import org.opentaint.ir.go.type.GoIRNamedTypeKind

/**
 * Tests for Go interface declarations, type assertions, interface conversions.
 */
@ExtendWith(GoIRTestExtension::class)
class InterfaceTests {

    @Test
    fun `simple interface declaration`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            type Stringer interface {
                String() string
            }
            type MyStr struct{ s string }
            func (m MyStr) String() string { return m.s }
            func use() Stringer { return MyStr{s: "hello"} }
        """.trimIndent())

        val iface = prog.findNamedTypeByName("Stringer")
        assertThat(iface).isNotNull
        assertThat(iface!!.kind).isEqualTo(GoIRNamedTypeKind.INTERFACE)
        assertThat(iface.interfaceMethods).hasSize(1)
        assertThat(iface.interfaceMethods[0].name).isEqualTo("String")

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `interface with multiple methods`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            type ReadWriter interface {
                Read(buf []byte) (int, error)
                Write(buf []byte) (int, error)
            }
            type dummy struct{}
            func (d dummy) Read(buf []byte) (int, error) { return 0, nil }
            func (d dummy) Write(buf []byte) (int, error) { return 0, nil }
            func use() ReadWriter { return dummy{} }
        """.trimIndent())

        val iface = prog.findNamedTypeByName("ReadWriter")!!
        assertThat(iface.interfaceMethods).hasSize(2)
        assertThat(iface.interfaceMethods.map { it.name }).containsExactlyInAnyOrder("Read", "Write")

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `type assertion generates TypeAssert instruction`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            type Animal interface { Speak() string }
            type Dog struct{}
            func (d Dog) Speak() string { return "woof" }
            func assertDog(a Animal) Dog {
                return a.(Dog)
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("assertDog")!!
        val asserts = fn.findExpressions<GoIRTypeAssertExpr>()
        assertThat(asserts).isNotEmpty()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `type assertion with comma-ok`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            type Sayer interface { Say() string }
            type Cat struct{}
            func (c Cat) Say() string { return "meow" }
            func tryCat(s Sayer) (Cat, bool) {
                c, ok := s.(Cat)
                return c, ok
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("tryCat")!!
        val asserts = fn.findExpressions<GoIRTypeAssertExpr>()
        assertThat(asserts).isNotEmpty()
        assertThat(asserts.any { it.commaOk }).isTrue()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `make interface produces MakeInterface instruction`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            type Foo interface { Bar() }
            type baz struct{}
            func (b baz) Bar() {}
            func wrap() Foo {
                return baz{}
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("wrap")!!
        val makeIfaces = fn.findExpressions<GoIRMakeInterfaceExpr>()
        assertThat(makeIfaces).isNotEmpty()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }
}
