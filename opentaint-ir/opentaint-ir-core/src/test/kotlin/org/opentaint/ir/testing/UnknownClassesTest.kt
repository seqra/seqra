package org.opentaint.ir.testing

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.ir.api.jvm.ext.cfg.fieldRef
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.api.jvm.ext.findFieldOrNull
import org.opentaint.ir.api.jvm.ext.findMethodOrNull
import org.opentaint.ir.api.jvm.ext.objectClass
import org.opentaint.ir.impl.features.classpaths.JIRUnknownClass
import org.opentaint.ir.impl.features.classpaths.UnknownClasses
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UnknownClassesTest : BaseTest() {

    companion object : WithGlobalDbImmutable(UnknownClasses)

    @Test
    fun `unknown class is resolved`() {
        val clazz = cp.findClass("xxx")
        assertTrue(clazz is JIRUnknownClass)
        assertTrue(clazz.declaredMethods.isEmpty())
        assertTrue(clazz.declaredFields.isEmpty())

        assertNotNull(clazz.declaration.location)
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

    @Test
    fun `instructions with references to unknown fields and methods are resolved`() {
        val clazz = listOf(
            cp.findClass("PhantomDeclarationConsumer")
        )
        clazz.forEach {
            it.declaredMethods.forEach { it.assertCfg() }
        }
    }

    @Test
    fun `object doesn't have unknown methods and fields`() {
        cp.objectClass.let { clazz ->
            assertTrue(clazz !is JIRUnknownClass)
            assertTrue(clazz.declaredFields.isEmpty())
            val xxxField = clazz.findFieldOrNull("xxx")
            assertNull(xxxField)
            val xxxMethod = clazz.findMethodOrNull("xxx", "(JILjava/lang/Exception;)V")
            assertNull(xxxMethod)
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
