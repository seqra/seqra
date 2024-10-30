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
    fun `generics for parent types`() = runBlocking {
        val superFooType = cp.findTypeOrNull(SuperFoo::class.java.name)
        assertNotNull(superFooType!!)
        assertTrue(superFooType is JIRClassType)
        superFooType as JIRClassType
        with(superFooType.superType()) {
            assertNotNull(this)
            this!!
            assertTrue(this is JIRClassType)
            this as JIRClassType
            val fields = fields()
            assertEquals(2, fields.size)

            with(fields.first()) {
                assertEquals("state", name)
                assertTrue(fieldType() is JIRClassType)
            }
        }
    }

    @Test
    fun `primitive and array types`() = runBlocking {
        val primitiveAndArrays = cp.findTypeOrNull(PrimitiveAndArrays::class.java.name)
        assertTrue(primitiveAndArrays is JIRClassType)
        primitiveAndArrays as JIRClassType
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
}