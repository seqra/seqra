package org.opentaint.ir.testing.types

import org.opentaint.ir.api.JIRArrayType
import org.opentaint.ir.api.JIRPrimitiveType
import org.opentaint.ir.api.JIRTypeVariable
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.ext.toType
import org.opentaint.ir.impl.types.JIRClassTypeImpl
import org.opentaint.ir.impl.types.substition.JIRSubstitutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
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
        assertEquals(false, actualParameters.first().type.nullable)
        assertEquals(true, actualParameters.get(1).type.nullable)
    }

    @Test
    fun `inner-outer classes recursion`() {
        cp.findClass("com.zaxxer.hikari.pool.HikariPool").toType().interfaces
        cp.findClass("com.zaxxer.hikari.util.ConcurrentBag").toType()
    }

    @Test
    fun `kotlin private inline fun`() {
        val type = cp.findClass("kotlin.text.RegexKt\$fromInt\$1\$1").toType().interfaces.single().typeArguments.first()
        type as JIRTypeVariable
        assertTrue(type.bounds.isNotEmpty())
    }

    @Test
    fun `interfaces types test`() {
        val sessionCacheVisitorType = cp.findClass("sun.security.ssl.SSLSessionContextImpl\$SessionCacheVisitor").toType()
        val cacheVisitorType = sessionCacheVisitorType.interfaces.first()
        val firstParam = cacheVisitorType.typeArguments.first()

        assertEquals(firstParam.jIRClass, cp.findClass("sun.security.ssl.SessionId"))

        val secondParam = cacheVisitorType.typeArguments[1]
        assertEquals(secondParam.jIRClass, cp.findClass("sun.security.ssl.SSLSessionImpl"))
    }

    private val listClass = List::class.java.name

    @Test
    fun `raw types equality`() {
        val rawType1 = JIRClassTypeImpl(cp, listClass, null, JIRSubstitutor.empty, false, emptyList())
        val rawType2 = JIRClassTypeImpl(cp, listClass, null, JIRSubstitutor.empty, false, emptyList())
        assertEquals(rawType1, rawType2)
    }

    interface X : List<String>
    interface Y : List<String>

    @Test
    fun `parametrized types equality`() {
        val rawType = JIRClassTypeImpl(cp, listClass, null, JIRSubstitutor.empty, false, emptyList())
        val type1 = cp.findClass<X>().toType().interfaces.first()
        val type2 = cp.findClass<Y>().toType().interfaces.first()
        assertNotEquals(rawType, type1)
        assertNotEquals(rawType, type2)

        assertNotEquals(type1, type2)
    }

}