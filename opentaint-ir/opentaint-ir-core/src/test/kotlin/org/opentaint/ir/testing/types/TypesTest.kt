package org.opentaint.ir.testing.types

import com.zaxxer.hikari.pool.HikariPool
import com.zaxxer.hikari.util.ConcurrentBag
import org.opentaint.ir.api.JIRArrayType
import org.opentaint.ir.api.JIRPrimitiveType
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.ext.toType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TypesTest : BaseTypesTest() {

    @Test
    fun `primitive and array types`() {
        val primitiveAndArrays = findType<PrimitiveAndArrays>()
        val fields = primitiveAndArrays.declaredFields
        assertEquals(2, fields.size)

        with(fields.first()) {
            assertTrue(fieldType is JIRPrimitiveType)
            assertEquals("value", name)
            assertEquals("int", fieldType.typeName)
        }
        with(fields.get(1)) {
            assertTrue(fieldType is JIRArrayType)
            assertEquals("intArray", name)
            assertEquals("int[]", fieldType.typeName)
        }

        val methods = primitiveAndArrays.declaredMethods.filterNot { it.method.isConstructor }
        with(methods.first()) {
            assertTrue(returnType is JIRArrayType)
            assertEquals("int[]", returnType.typeName)

            assertEquals(1, parameters.size)
            with(parameters.get(0)) {
                assertTrue(type is JIRArrayType)
                assertEquals("java.lang.String[]", type.typeName)
            }
        }
    }

    @Test
    fun `parameters test`() {
        class Example {
            fun f(notNullable: String, nullable: String?): Int {
                return 0
            }
        }

        val type = findType<Example>()
        val actualParameters = type.declaredMethods.single { it.name == "f" }.parameters
        assertEquals(listOf("notNullable", "nullable"), actualParameters.map { it.name })
        assertEquals(false, actualParameters.first().nullable)
        assertEquals(true, actualParameters.get(1).nullable)
    }

    @Test
    fun `inner-outer classes recursion`() {
        cp.findClass<HikariPool>().toType().interfaces
        cp.findClass<ConcurrentBag<*>>().toType()
    }

    @Test
    fun `kotlin private inline fun`() {
        cp.findClass("kotlin.text.RegexKt\$fromInt\$1\$1").toType().interfaces.single().typeArguments
    }

}