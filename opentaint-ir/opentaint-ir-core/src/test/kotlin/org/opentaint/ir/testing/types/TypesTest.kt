package org.opentaint.ir.testing.types

import org.opentaint.ir.api.jvm.JIRArrayType
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRPrimitiveType
import org.opentaint.ir.api.jvm.JIRTypeVariable
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.api.jvm.ext.findMethodOrNull
import org.opentaint.ir.api.jvm.ext.humanReadableSignature
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.ir.impl.types.JIRClassTypeImpl
import org.opentaint.ir.impl.types.signature.JvmClassRefType
import org.opentaint.ir.impl.types.substition.JIRSubstitutorImpl
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.InputStream

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
        val sessionCacheVisitorType =
            cp.findClass("sun.security.ssl.SSLSessionContextImpl\$SessionCacheVisitor").toType()
        val cacheVisitorType = sessionCacheVisitorType.interfaces.first()
        val firstParam = cacheVisitorType.typeArguments.first()

        assertEquals(firstParam.jIRClass, cp.findClass("sun.security.ssl.SessionId"))

        val secondParam = cacheVisitorType.typeArguments[1]
        assertEquals(secondParam.jIRClass, cp.findClass("sun.security.ssl.SSLSessionImpl"))
    }

    private val listClass = List::class.java.name

    @Test
    fun `raw types equality`() {
        val rawType1 = rawList()
        val rawType2 = rawList()
        assertEquals(rawType1, rawType2)
    }

    @Test
    fun `parametrized types equality`() {
        val rawType = rawList()
        val type1 = listType<String>()
        val type2 = listType<String>()
        assertNotEquals(rawType, type1)
        assertNotEquals(rawType, type2)

        assertEquals(type1, type2)
    }

    @Test
    fun `parametrized typed method equality`() {

        val objectList = listType<Any>()
        val stringList1 = listType<String>()
        val stringList2 = listType<String>()
        val isList = listType<InputStream>()

        assertEquals(stringList1.iterator, stringList2.iterator)
        assertNotEquals(isList.iterator, stringList1.iterator)
        assertNotEquals(objectList.iterator, stringList1.iterator)
    }

    @Test
    fun `humanReadableSignature should work`() {
        val type = listType<String>()
        assertEquals(
            "java.util.List<java.lang.String>#isEmpty():boolean",
            type.declaredMethods.first { it.name == "isEmpty" }.humanReadableSignature
        )
    }

    private inline fun <reified T> listType(raw: Boolean = false): JIRClassType {
        val elementName = T::class.java.name
        return JIRClassTypeImpl(
            cp, listClass, null,
            when {
                raw -> emptyList()
                else -> listOf(JvmClassRefType(elementName, false, emptyList()))
            }, false, emptyList()
        )
    }

    private fun rawList(): JIRClassType {
        return JIRClassTypeImpl(cp, listClass, null, JIRSubstitutorImpl.empty, false, emptyList())
    }

    private val JIRClassType.iterator get() = findMethodOrNull { it.name == "iterator" && it.parameters.isEmpty() }

}