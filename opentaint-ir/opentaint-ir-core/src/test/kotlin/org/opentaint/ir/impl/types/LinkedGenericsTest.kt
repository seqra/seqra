package org.opentaint.opentaint-ir.impl.types

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.opentaint.opentaint-ir.api.JIRClassType
import org.opentaint.opentaint-ir.api.JIRTypeVariable
import org.opentaint.opentaint-ir.api.ext.findClass
import org.opentaint.opentaint-ir.api.ext.isConstructor
import org.opentaint.opentaint-ir.impl.types.Generics.LinkedImpl
import org.opentaint.opentaint-ir.impl.types.Generics.SingleImpl

class LinkedGenericsTest : BaseTypesTest() {

    @Test
    fun `linked generics original parametrization`() = runBlocking {
        val partial = findType<LinkedImpl<*>>()
        with(partial.superType!!) {
            with(typeParameters.first()) {
                assertEquals("T", symbol)
                bounds.first().assertClassType<Any>()
            }

            with(typeParameters[1]) {
                assertEquals("W", symbol)
                assertEquals(1, bounds.size)
                assertEquals("java.util.List<T>", bounds[0].typeName)
            }
        }
    }

    @Test
    fun `linked generics current parametrization`() = runBlocking {
        val partial = findType<LinkedImpl<*>>()
        with(partial.superType!!) {
            with(typeArguments[0]) {
                assertClassType<String>()
            }

            with(typeArguments[1]) {
                this as JIRTypeVariable
                assertEquals("W", symbol)
                assertEquals(1, bounds.size)
                assertEquals("java.util.List<java.lang.String>", bounds[0].typeName)
            }
        }
    }

    @Test
    fun `linked generics fields parametrization`() = runBlocking {
        val partial = findType<LinkedImpl<*>>()
        with(partial.superType!!) {
            val fields = fields
            assertEquals(3, fields.size)

            with(fields.first { it.name == "state" }) {
                assertEquals("state", name)
                fieldType.assertClassType<String>()
            }
            with(fields.first { it.name == "stateW" }) {
                assertEquals(
                    "java.util.List<java.lang.String>",
                    (fieldType as JIRTypeVariable).bounds.first().typeName
                )
            }
            with(fields.first { it.name == "stateListW" }) {
                val resolvedType = fieldType.assertIsClass()
                assertEquals(cp.findClass<List<*>>(), resolvedType.jIRClass)
                val shouldBeW = (resolvedType.typeArguments.first() as JIRTypeVariable)
                assertEquals("java.util.List<java.lang.String>", shouldBeW.bounds.first().typeName)
            }
        }
    }

    @Test
    fun `generics applied for fields of super types`() {
        runBlocking {
            val superFooType = findType<SingleImpl>()
            with(superFooType.superType.assertIsClass()) {
                val fields = fields
                assertEquals(2, fields.size)

                with(fields.first()) {
                    assertEquals("state", name)
                    fieldType.assertClassType<String>()
                }
                with(fields.get(1)) {
                    assertEquals("stateList", name)
                    with(fieldType.assertIsClass()) {
                        assertEquals("java.util.ArrayList<java.lang.String>", typeName)
                    }
                }
            }
        }
    }

    @Test
    fun `direct generics from child types applied to methods`() {
        runBlocking {
            val superFooType = findType<SingleImpl>()
            val superType = superFooType.superType.assertIsClass()
            val methods = superType.declaredMethods.filterNot { it.method.isConstructor }
            assertEquals(2, methods.size)

            with(methods.first { it.method.name == "run1" }) {
                returnType.assertClassType<String>()
                parameters.first().type.assertClassType<String>()
            }
        }
    }

    @Test
    fun `custom generics from child types applied to methods`() {
        runBlocking {
            val superFooType = findType<SingleImpl>()
            val superType = superFooType.superType.assertIsClass()
            val methods = superType.declaredMethods.filterNot { it.method.isConstructor }
            assertEquals(2, methods.size)

            with(methods.first { it.method.name == "run2" }) {
                val params = parameters.first()
                val w = typeParameters.first()

                val bound = (params.type as JIRClassType).typeArguments.first()
                assertEquals("W", (bound as? JIRTypeVariable)?.symbol)
                assertEquals("W", w.symbol)
                bound as JIRTypeVariable
                bound.bounds.first().assertClassType<String>()
            }
        }
    }

}