package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.jvm.BasicTestUtils
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodPositionBaseTypeResolver
import org.opentaint.dataflow.taint.PositionAccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JIRInternalBasePositionTest : BasicTestUtils() {
    private fun resolver() = JIRMethodPositionBaseTypeResolver(
        findMethod("sample.sequent.StatementSummarySample", "fieldRead"),
    )

    @Test
    fun `A01 JVM simple position type resolution rejects internal bases`() {
        val resolver = resolver()
        for (base in listOf(AccessPathBase.Zero, AccessPathBase.CleanerTemporary())) {
            assertFailsWith<IllegalStateException>("resolve $base") {
                resolver.resolve(PositionAccess.Simple(base))
            }
        }
    }

    @Test
    fun `A01 JVM accessed position type resolution rejects internal bases`() {
        val resolver = resolver()
        val field = FieldAccessor("Box", "value", "java.lang.Object")
        for (base in listOf(AccessPathBase.Zero, AccessPathBase.CleanerTemporary())) {
            assertFailsWith<IllegalStateException>("resolve accessed $base") {
                resolver.resolve(PositionAccess.Complex(PositionAccess.Simple(base), field))
            }
        }
    }

    @Test
    fun `A01 JVM program positions retain their types`() {
        val resolver = resolver()
        assertEquals("sample.sequent.StatementSummarySample",
            resolver.resolve(PositionAccess.Simple(AccessPathBase.This))?.typeName)
        assertEquals("sample.sequent.StatementSummarySample",
            resolver.resolve(PositionAccess.Simple(AccessPathBase.Argument(0)))?.typeName)
        assertEquals("java.lang.Object",
            resolver.resolve(PositionAccess.Simple(AccessPathBase.Return))?.typeName)
    }
}
