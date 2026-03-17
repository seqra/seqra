package org.opentaint.ir.testing.types

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRTypedMethod
import org.opentaint.ir.api.jvm.ext.constructors
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.api.jvm.ext.methods
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.ir.testing.hierarchies.Overrides
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.Closeable

class OverridesTest : BaseTypesTest() {

    @Test
    fun `types methods should respect overrides`() {
        val impl1 = cp.findClass<Overrides.Impl1>().toType()
        assertEquals(1, impl1.constructors.size)
        assertEquals(2, impl1.declaredMethods.typedNotSynthetic().size)
        with(impl1.methods.typedNotSynthetic().filter { it.name == "runMain" }) {
            assertEquals(2, size)
            assertTrue(any { it.parameters.first().type.typeName == String::class.java.name })
            assertTrue(any { it.parameters.first().type.typeName == "java.util.List<java.lang.String>" })
        }

        val impl2 = cp.findClass<Overrides.Impl2>().toType()
        assertEquals(1, impl2.constructors.size)
        assertEquals(2, impl2.declaredMethods.typedNotSynthetic().size)
        with(impl2.methods.typedNotSynthetic().filter { it.name == "runMain" }) {
            assertEquals(3, size)
            assertTrue(any { it.parameters.first().type.typeName == Closeable::class.java.name })
            assertTrue(any { it.parameters.first().type.typeName == String::class.java.name })
            assertTrue(any { it.parameters.first().type.typeName == "java.util.List<java.lang.String>" })
        }
    }

    @Test
    fun `types fields should respect overrides and visibility`() {
        val impl1 = cp.findClass<Overrides.Impl1>().toType()
        assertEquals(0, impl1.declaredFields.size)
        with(impl1.fields) {
            assertEquals(2, size)
            first { it.name == "protectedMain" }.type.assertClassType<String>()
            first { it.name == "publicMain" }.type.assertClassType<String>()
        }

        val impl2 = cp.findClass<Overrides.Impl2>().toType()
        assertEquals(3, impl2.declaredFields.size)
        with(impl2.fields) {
            assertEquals(5, size)
            assertEquals("java.util.List<java.io.Closeable>", first { it.name == "publicMain1" }.type.typeName)
            assertEquals("java.util.List<java.io.Closeable>", first { it.name == "protectedMain1" }.type.typeName)

            with(first { it.name == "main" }) {
                type.assertClassType<String>()
                enclosingType.assertClassType<Overrides.Impl2>()
            }
            first { it.name == "publicMain" }.type.assertClassType<String>()
            first { it.name == "protectedMain" }.type.assertClassType<String>()
        }
    }

    @Test
    fun `class methods should respect overrides`() {
        val impl1 = cp.findClass<Overrides.Impl1>()
        assertEquals(1, impl1.constructors.size)
        assertEquals(2, impl1.declaredMethods.notSynthetic().size)
        assertEquals(2, impl1.methods.notSynthetic().filter { it.name == "runMain" }.size)

        val impl2 = cp.findClass<Overrides.Impl2>()
        assertEquals(1, impl2.constructors.size)
        assertEquals(2, impl2.declaredMethods.notSynthetic().size)
        assertEquals(3, impl2.methods.notSynthetic().filter { it.name == "runMain" }.size)
    }

    private fun List<JIRMethod>.notSynthetic() = filterNot { it.isSynthetic || it.isConstructor }

    private fun List<JIRTypedMethod>.typedNotSynthetic() = filterNot { it.method.isSynthetic || it.method.isConstructor }

}
