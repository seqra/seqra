package org.opentaint.ir.testing

import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.ext.cfg.callExpr
import org.opentaint.ir.api.ext.cfg.fieldRef
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.impl.features.classpaths.JIRUnknownClass
import org.opentaint.ir.impl.features.classpaths.UnknownClasses
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UnknownClassesTest : BaseTest() {

    companion object : WithDB(UnknownClasses)

    @Test
    fun `unknown class is resolved`() {
        val clazz = cp.findClass("xxx")
        assertTrue(clazz is JIRUnknownClass)
        assertTrue(clazz.declaredMethods.isEmpty())
        assertTrue(clazz.declaredFields.isEmpty())
    }

    @Test
    fun `fields and methods of unknown class is empty`() {
        val clazz = cp.findClass("PhantomClassSubclass").superClass
        assertTrue(clazz is JIRUnknownClass)
        assertNotNull(clazz!!)
        assertTrue(clazz.declaredMethods.isEmpty())
        assertTrue(clazz.declaredFields.isEmpty())
    }

    @Test
    fun `parent of class is resolved`() {
        val clazz = cp.findClass("PhantomClassSubclass")
        assertTrue(clazz.superClass is JIRUnknownClass)
    }

    @Test
    fun `instructions with references to unknown classes are resolved`() {
        val clazz = listOf(
            cp.findClass("PhantomClassSubclass"),
            cp.findClass("PhantomCodeConsumer")
        )
        clazz.forEach {
            it.declaredMethods.forEach { it.assertCfg() }
        }
    }

    private fun JIRMethod.assertCfg(){
        val cfg = flowGraph()
        cfg.instructions.forEach {
            it.callExpr?.let {
                assertNotNull(it.method)
            }
            it.fieldRef?.let {
                assertNotNull(it.field)
            }
        }
    }
}