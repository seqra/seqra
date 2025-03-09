package org.opentaint.ir.testing.cfg

import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.testing.WithDB
import org.opentaint.ir.testing.ir.DoubleComparison
import org.opentaint.ir.testing.ir.InvokeMethodWithException
import org.opentaint.ir.testing.ir.WhenExpr
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReverseIRTest : BaseInstructionsTest() {
    companion object : WithDB()

    @Test
    fun comparison() {
        val clazz = testAndLoadClass(cp.findClass<DoubleComparison>())
        val m = clazz.declaredMethods.first { it.name == "box" }
        assertEquals("OK", m.invoke(null))
    }

    @Test
    fun `when`() {
        val clazz = testAndLoadClass(cp.findClass<WhenExpr>())
        val m = clazz.declaredMethods.first { it.name == "box" }
        assertEquals("OK", m.invoke(null))
    }

    @Test
    fun `local vars`() {
        val clazz = testAndLoadClass(cp.findClass<InvokeMethodWithException>())
        val m = clazz.declaredMethods.first { it.name == "box" }
        assertEquals("OK", m.invoke(null))
    }

}