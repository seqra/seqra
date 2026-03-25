package org.opentaint.ir.go.test.features

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.ext.findFunctionByName
import org.opentaint.ir.go.ext.findNamedTypeByName
import org.opentaint.ir.go.test.GoIRSanityChecker
import org.opentaint.ir.go.test.GoIRTestBuilder
import org.opentaint.ir.go.test.GoIRTestExtension
import org.opentaint.ir.go.type.GoIRNamedTypeKind

/**
 * Tests for entity-level IR properties: packages, types, functions, globals.
 */
@ExtendWith(GoIRTestExtension::class)
class EntityTests {

    @Test
    fun `package with imports`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            import "fmt"
            func f() { fmt.Println("hello") }
        """.trimIndent())

        val pkg = prog.packages.values.find { it.name == "p" }
        assertThat(pkg).isNotNull
        assertThat(pkg!!.importPath).isNotEmpty()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `exported vs unexported functions`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func Exported() int { return 1 }
            func unexported() int { return 2 }
        """.trimIndent())

        val exported = prog.findFunctionByName("Exported")!!
        assertThat(exported.isExported).isTrue()

        val unexported = prog.findFunctionByName("unexported")!!
        assertThat(unexported.isExported).isFalse()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `method with value receiver`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            type Counter struct { N int }
            func (c Counter) Get() int { return c.N }
        """.trimIndent())

        val fn = prog.findFunctionByName("Get")
        assertThat(fn).isNotNull
        assertThat(fn!!.isMethod).isTrue()
        assertThat(fn.receiverType).isNotNull
        assertThat(fn.receiverType!!.name).isEqualTo("Counter")
        assertThat(fn.isPointerReceiver).isFalse()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `method with pointer receiver`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            type Counter struct { N int }
            func (c *Counter) Inc() { c.N++ }
        """.trimIndent())

        val fn = prog.findFunctionByName("Inc")
        assertThat(fn).isNotNull
        assertThat(fn!!.isMethod).isTrue()
        assertThat(fn.isPointerReceiver).isTrue()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `struct type with fields`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            type Person struct {
                Name string
                Age  int
            }
            func makePerson() Person { return Person{} }
        """.trimIndent())

        val typ = prog.findNamedTypeByName("Person")
        assertThat(typ).isNotNull
        assertThat(typ!!.kind).isEqualTo(GoIRNamedTypeKind.STRUCT)
        assertThat(typ.fields).hasSize(2)
        assertThat(typ.fields[0].name).isEqualTo("Name")
        assertThat(typ.fields[1].name).isEqualTo("Age")

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `interface type with methods`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            type Shape interface {
                Area() float64
                Perimeter() float64
            }
            type Circle struct { R float64 }
            func (c Circle) Area() float64 { return 3.14 * c.R * c.R }
            func (c Circle) Perimeter() float64 { return 2 * 3.14 * c.R }
        """.trimIndent())

        val shape = prog.findNamedTypeByName("Shape")
        assertThat(shape).isNotNull
        assertThat(shape!!.kind).isEqualTo(GoIRNamedTypeKind.INTERFACE)
        assertThat(shape.interfaceMethods).hasSize(2)

        val circle = prog.findNamedTypeByName("Circle")
        assertThat(circle).isNotNull
        assertThat(circle!!.kind).isEqualTo(GoIRNamedTypeKind.STRUCT)

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `embedded struct fields`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            type Base struct { ID int }
            type Extended struct {
                Base
                Extra string
            }
            func f() Extended { return Extended{} }
        """.trimIndent())

        val ext = prog.findNamedTypeByName("Extended")
        assertThat(ext).isNotNull
        assertThat(ext!!.fields).hasSize(2)
        assertThat(ext.fields[0].isEmbedded).isTrue()
        assertThat(ext.fields[0].name).isEqualTo("Base")

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `package-level global variable`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            var GlobalCounter int
            func inc() { GlobalCounter++ }
        """.trimIndent())

        val pkg = prog.packages.values.find { it.name == "p" }
        assertThat(pkg).isNotNull
        val global = pkg!!.globals.find { it.name == "GlobalCounter" }
        assertThat(global).isNotNull

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `variadic function`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func sum(nums ...int) int {
                total := 0
                for _, n := range nums {
                    total += n
                }
                return total
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("sum")!!
        assertThat(fn.signature.isVariadic).isTrue()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `function with multiple return values`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func divmod(a, b int) (int, int) { return a / b, a % b }
        """.trimIndent())

        val fn = prog.findFunctionByName("divmod")!!
        assertThat(fn.signature.results).hasSize(2)

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `anonymous function is captured`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func f() func() int {
                x := 42
                return func() int { return x }
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("f")!!
        assertThat(fn.anonymousFunctions).isNotEmpty()

        val anon = fn.anonymousFunctions[0]
        assertThat(anon.parent).isEqualTo(fn)
        assertThat(anon.freeVars).isNotEmpty()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }
}
