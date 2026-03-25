package org.opentaint.ir.go.test.features

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.ext.findFunctionByName
import org.opentaint.ir.go.ext.findInstructions
import org.opentaint.ir.go.ext.findNamedTypeByName
import org.opentaint.ir.go.inst.GoIRAlloc
import org.opentaint.ir.go.inst.GoIRFieldAddr
import org.opentaint.ir.go.inst.GoIRStore
import org.opentaint.ir.go.test.GoIRSanityChecker
import org.opentaint.ir.go.test.GoIRTestBuilder
import org.opentaint.ir.go.test.GoIRTestExtension
import org.opentaint.ir.go.type.GoIRNamedTypeKind

/**
 * Tests for Go struct declarations, field access, embedded structs.
 */
@ExtendWith(GoIRTestExtension::class)
class StructTests {

    @Test
    fun `simple struct declaration`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            type Point struct { X, Y int }
            func use() { _ = Point{X: 1, Y: 2} }
        """.trimIndent())

        val nt = prog.findNamedTypeByName("Point")
        assertThat(nt).isNotNull
        assertThat(nt!!.kind).isEqualTo(GoIRNamedTypeKind.STRUCT)
        assertThat(nt.fields).hasSize(2)
        assertThat(nt.fields[0].name).isEqualTo("X")
        assertThat(nt.fields[1].name).isEqualTo("Y")

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `struct field access via pointer generates FieldAddr`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            type Point struct { X, Y int }
            func getX(p *Point) int { return p.X }
        """.trimIndent())

        val fn = prog.findFunctionByName("getX")!!
        assertThat(fn.hasBody).isTrue()

        val fieldAddrs = fn.findInstructions<GoIRFieldAddr>()
        assertThat(fieldAddrs).isNotEmpty()
        assertThat(fieldAddrs.any { it.fieldName == "X" }).isTrue()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `struct with embedded field`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            type Base struct { ID int }
            type Derived struct { Base; Name string }
            func use() { _ = Derived{} }
        """.trimIndent())

        val derived = prog.findNamedTypeByName("Derived")!!
        assertThat(derived.kind).isEqualTo(GoIRNamedTypeKind.STRUCT)
        assertThat(derived.fields).hasSize(2)

        val embeddedField = derived.fields.find { it.name == "Base" }
        assertThat(embeddedField).isNotNull
        assertThat(embeddedField!!.isEmbedded).isTrue()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `struct literal creates alloc and stores`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            type Pair struct { A, B int }
            func makePair(a, b int) *Pair {
                return &Pair{A: a, B: b}
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("makePair")!!
        assertThat(fn.hasBody).isTrue()

        // Should have alloc for the struct
        val allocs = fn.findInstructions<GoIRAlloc>()
        assertThat(allocs).isNotEmpty()

        // Should have stores for field values
        val stores = fn.findInstructions<GoIRStore>()
        assertThat(stores).isNotEmpty()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `struct with method`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            type Counter struct { count int }
            func (c *Counter) Increment() { c.count++ }
            func (c Counter) Value() int { return c.count }
        """.trimIndent())

        val nt = prog.findNamedTypeByName("Counter")!!

        // Should have methods
        val allMethods = nt.allMethods()
        assertThat(allMethods).isNotEmpty()

        // Pointer receiver method
        val inc = allMethods.find { it.name == "Increment" }
        assertThat(inc).isNotNull
        assertThat(inc!!.isMethod).isTrue()

        // Value receiver method
        val value = allMethods.find { it.name == "Value" }
        assertThat(value).isNotNull
        assertThat(value!!.isMethod).isTrue()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }
}
