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
 * Tests for type conversions, type assertions, interface boxing.
 */
@ExtendWith(GoIRTestExtension::class)
class TypeConversionTests {

    @Test
    fun `numeric conversion int to float64`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func toFloat(x int) float64 { return float64(x) }
        """.trimIndent())

        val fn = prog.findFunctionByName("toFloat")!!
        val converts = fn.findInstructions<GoIRConvert>()
        assertThat(converts).hasSize(1)

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `string to byte slice conversion`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            func toBytes(s string) []byte { return []byte(s) }
        """.trimIndent())

        val fn = prog.findFunctionByName("toBytes")!!
        val converts = fn.findInstructions<GoIRConvert>()
        assertThat(converts).hasSize(1)

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `interface boxing with MakeInterface`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            type Stringer interface { String() string }
            type MyStr string
            func (m MyStr) String() string { return string(m) }
            func box(s MyStr) Stringer { return s }
        """.trimIndent())

        val fn = prog.findFunctionByName("box")!!
        val makeIfaces = fn.findInstructions<GoIRMakeInterface>()
        assertThat(makeIfaces).hasSize(1)

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `type assertion without comma-ok panics on failure`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            type Stringer interface { String() string }
            type MyStr string
            func (m MyStr) String() string { return string(m) }
            func unbox(s Stringer) MyStr { return s.(MyStr) }
        """.trimIndent())

        val fn = prog.findFunctionByName("unbox")!!
        val asserts = fn.findInstructions<GoIRTypeAssert>()
        assertThat(asserts).hasSize(1)
        assertThat(asserts[0].commaOk).isFalse()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `type assertion with comma-ok`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            type Stringer interface { String() string }
            type MyStr string
            func (m MyStr) String() string { return string(m) }
            func tryUnbox(s Stringer) (MyStr, bool) {
                v, ok := s.(MyStr)
                return v, ok
            }
        """.trimIndent())

        val fn = prog.findFunctionByName("tryUnbox")!!
        val asserts = fn.findInstructions<GoIRTypeAssert>()
        assertThat(asserts).hasSize(1)
        assertThat(asserts[0].commaOk).isTrue()

        GoIRSanityChecker.check(prog).assertNoErrors()
    }

    @Test
    fun `change type for same underlying type`(builder: GoIRTestBuilder) {
        val prog = builder.buildFromSource("""
            package p
            type Meters float64
            type Feet float64
            func convert(m Meters) Feet { return Feet(m * 3.28084) }
        """.trimIndent())

        val fn = prog.findFunctionByName("convert")!!
        assertThat(fn.hasBody).isTrue()
        // Either Convert or ChangeType depending on go-ssa analysis
        val converts = fn.findInstructions<GoIRConvert>()
        val changes = fn.findInstructions<GoIRChangeType>()
        assertThat(converts.size + changes.size).isGreaterThan(0)

        GoIRSanityChecker.check(prog).assertNoErrors()
    }
}
