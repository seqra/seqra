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
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.isConstructor
import org.opentaint.ir.impl.types.ClassWithInners
import org.opentaint.ir.impl.types.ClassWithInnersLinkedToMethod
import org.opentaint.ir.impl.types.ClassWithInnersLinkedToMethod2
import org.opentaint.ir.impl.types.PartialParametrization
import org.opentaint.ir.impl.types.PrimitiveAndArrays
import org.opentaint.ir.impl.types.SuperFoo
import org.opentaint.ir.jirdb
import java.io.InputStream

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
    fun `generics for super types`() {
        runBlocking {
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
    }

    @Test
    fun `generics - linked types`() = runBlocking {
        val partial = findClassType<PartialParametrization<*>>()
        with(partial.superType().assertClassType()) {
            with(originalParametrization().first()) {
                assertEquals("T", symbol)
                bounds.first().assertType<Any>()
            }

            with(originalParametrization()[1]) {
                assertEquals("W", symbol)
                assertEquals(1, bounds.size)
                assertEquals("java.util.List<T>", bounds[0].typeName)
            }

            with(parametrization()["T"]!!) {
                assertType<String>()
            }

            with(parametrization()["W"]!!) {
                this as JIRTypeVariable
                assertEquals("W", symbol)
                assertEquals(1, bounds.size)
                assertEquals("java.util.List<java.lang.String>", bounds[0].typeName)
            }

            val fields = fields()
            assertEquals(3, fields.size)

            with(fields.first()) {
                assertEquals("state", name)
                fieldType().assertType<String>()
            }
            with(fields[1]) {
                assertEquals("stateW", name)
                assertEquals(
                    "java.util.List<java.lang.String>",
                    (fieldType() as JIRTypeVariable).bounds.first().typeName
                )
            }
            with(fields[2]) {
                assertEquals("stateListW", name)
                val resolvedType = fieldType().assertClassType()
                assertEquals(cp.findClass<List<*>>(), resolvedType.jirClass)
                val shouldBeW = (resolvedType.parametrization().values.first() as JIRTypeVariable)
                assertEquals("java.util.List<java.lang.String>", shouldBeW.bounds.first().typeName)
            }
        }
    }

    @Test
    fun `generics for methods 1`() {
        runBlocking {
            val superFooType = findClassType<SuperFoo>()
            val superType = superFooType.superType().assertClassType()
            val methods = superType.methods().filterNot { it.method.isConstructor }
            assertEquals(2, methods.size)

            with(methods.first { it.method.name == "run1" }) {
                returnType().assertType<String>()
                parameters().first().type().assertType<String>()
            }
        }
    }

    @Test
    fun `generics for methods 2`() {
        runBlocking {
            val superFooType = findClassType<SuperFoo>()
            val superType = superFooType.superType().assertClassType()
            val methods = superType.methods().filterNot { it.method.isConstructor }
            assertEquals(2, methods.size)

            with(methods.first { it.method.name == "run2" }) {
                val returnType = returnType()
                val params = parameters().first()
                val w = originalParameterization().first()

                val bound = (params.type() as JIRClassType).parametrization().values.first()
                assertEquals("W", (bound as? JIRTypeVariable)?.symbol)
                assertEquals("W", w.symbol)
                bound as JIRTypeVariable
                bound.bounds.first().assertType<String>()
            }
        }
    }

    @Test
    fun `inner classes`() {
        runBlocking {
            val classWithInners = findClassType<ClassWithInners<*>>().assertClassType()
            val inners = classWithInners.innerTypes()
            assertEquals(2, inners.size)
            assertEquals("org.opentaint.ir.impl.types.ClassWithInners\$Inner", inners.first().typeName)
            with(inners.first().fields()) {
                with(first { it.name == "state" }) {
                    fieldType().assertType<Int>()
                }
                with(first { it.name == "stateT" }) {
                    assertEquals("T", (fieldType() as JIRTypeVariable).symbol)
                }
                with(first { it.name == "stateListT" }) {
                    assertEquals("java.util.List<T>", fieldType().typeName)
                }
            }

            assertEquals("org.opentaint.ir.impl.types.ClassWithInners\$Static", inners[1].typeName)
            with((inners[1].superType()!!.fields().first().fieldType() as JIRClassType).fields()) {
                with(first { it.name == "state" }) {
                    fieldType().assertType<Int>()
                }
                with(first { it.name == "stateT" }) {
                    fieldType().assertType<InputStream>()
                }
                with(first { it.name == "stateListT" }) {
                    assertEquals("java.util.List<java.io.InputStream>", fieldType().typeName)
                }
            }
        }
    }

    @Test
    fun `inner classes linked to method`() {
        runBlocking {
            val classWithInners = findClassType<ClassWithInnersLinkedToMethod<*>>().assertClassType()
            val inners = classWithInners.innerTypes()
            assertEquals(1, inners.size)
            assertEquals("org.opentaint.ir.impl.types.ClassWithInnersLinkedToMethod\$1", inners.first().typeName)
            with(inners.first().fields()) {
                with(first { it.name == "stateT" }) {
                    assertEquals("T", (fieldType() as JIRTypeVariable).symbol)
                }
                with(first { it.name == "stateW" }) {
                    assertEquals("W", fieldType().typeName)
                }
            }
            with(inners.first().outerMethod()!!) {
                assertEquals("run", name)
            }
            with(inners.first().outerType()!!) {
                assertEquals("org.opentaint.ir.impl.types.ClassWithInnersLinkedToMethod<W : java.lang.Object>", typeName)
            }
        }
    }

    @Test
    fun `inner classes linked to method 2`() {
        runBlocking {
            val classWithInners = findClassType<ClassWithInnersLinkedToMethod2<*>>().assertClassType()
            val inners = classWithInners.innerTypes()
            assertEquals(3, inners.size)
            with(inners.first { it.typeName.contains("Impl") }) {
                with(superType()!!.methods().first { it.name == "run" }) {
                    val returnType = returnType().assertClassType()
                    assertEquals("org.opentaint.ir.impl.types.ClassWithInnersLinkedToMethod2\$State<java.lang.String>", returnType.typeName)
                }
            }
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

    private suspend inline fun <reified T> JIRType?.assertType(): JIRClassType {
        val expected = findClassType<T>()
        assertNotNull(this)
        assertTrue(this is JIRClassType)
        assertEquals(expected.typeName, this?.typeName)
        return this as JIRClassType
    }

}