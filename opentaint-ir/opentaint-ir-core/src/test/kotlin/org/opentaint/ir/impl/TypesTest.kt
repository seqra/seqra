package org.opentaint.ir.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.JIRDB
import org.opentaint.ir.api.JIRArrayType
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRPrimitiveType
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.JIRTypeVariable
import org.opentaint.ir.api.ext.findTypeOrNull
import org.opentaint.ir.api.isConstructor
import org.opentaint.ir.impl.types.PrimitiveAndArrays
import org.opentaint.ir.impl.types.SuperFoo
import org.opentaint.ir.jirdb

class TypesTest {

    companion object : LibrariesMixin {
        var db: JIRDB? = runBlocking {
            jirdb {
                loadByteCode(allClasspath)
                useProcessJavaRuntime()
            }.also {
                it.awaitBackgroundJobs()
            }
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            db?.close()
            db = null
        }
    }

    private val cp: JIRClasspath = runBlocking { db!!.classpath(allClasspath) }

    @AfterEach
    fun close() {
        cp.close()
    }

    @Test
    fun `generics for super types`() = runBlocking {
        val superFooType = findClassType<SuperFoo>()
        with(superFooType.superType().assertClassType()) {
            val fields = fields()
            assertEquals(2, fields.size)

            with(fields.first()) {
                assertEquals("state", name)
                fieldType().assertType<String>()
            }
        }
    }

    @Test
    fun `generics for methods 1`() = runBlocking {
        val superFooType = findClassType<SuperFoo>()
        val superType = superFooType.superType().assertClassType()
        val methods = superType.methods().filterNot { it.method.isConstructor }
        assertEquals(2, methods.size)

        with(methods.first { it.method.name == "run1" }) {
            returnType().assertType<String>()
            parameters().first().type().assertType<String>()
        }
    }

//    @Test
    fun `generics for methods 2`() = runBlocking {
        val superFooType = findClassType<SuperFoo>()
        val superType = superFooType.superType().assertClassType()
        val methods = superType.methods().filterNot { it.method.isConstructor }
        assertEquals(2, methods.size)

        with(methods.first { it.method.name == "run2" }) {
            val returnType = returnType()
            val params = parameters().first()
            val w = originalParameterization().first()
            assertEquals("W", (params.type() as? JIRTypeVariable)?.typeSymbol)
            assertEquals("W", w.symbol)
            assertEquals(cp.findTypeOrNull<String>(), w.bounds.first())
        }
    }

    @Test
    fun `primitive and array types`() = runBlocking {
        val primitiveAndArrays = findClassType<PrimitiveAndArrays>()
        val fields = primitiveAndArrays.fields()
        assertEquals(2, fields.size)

        with(fields.first()) {
            assertTrue(fieldType() is JIRPrimitiveType)
            assertEquals("int", name)
            assertEquals("int", fieldType().typeName)
        }
        with(fields.get(1)) {
            assertTrue(fieldType() is JIRArrayType)
            assertEquals("intArray", name)
            assertEquals("int[]", fieldType().typeName)
        }


        val methods = primitiveAndArrays.methods().filterNot { it.method.isConstructor }
        with(methods.first()) {
            assertTrue(returnType() is JIRArrayType)
            assertEquals("int[]", returnType().typeName)

            assertEquals(1, parameters().size)
            with(parameters().get(0)) {
                assertTrue(type() is JIRArrayType)
                assertEquals("java.lang.String[]", type().typeName)
            }
        }
    }

    private suspend inline fun <reified T> findClassType(): JIRClassType {
        val found = cp.findTypeOrNull(T::class.java.name)
        assertNotNull(found)
        assertTrue(found is JIRClassType)
        return found as JIRClassType
    }

    private fun JIRType?.assertClassType(): JIRClassType {
        assertNotNull(this)
        assertTrue(this is JIRClassType)
        return this as JIRClassType
    }

    private suspend inline fun <reified T> JIRType?.assertType() {
        val expected = findClassType<T>()
        assertNotNull(this)
        assertTrue(this is JIRClassType)
        assertEquals(expected.typeName, this?.typeName)
    }

    private val stringType get() = runBlocking { findClassType<String>() }
}