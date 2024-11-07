package org.opentaint.ir.impl.types

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRTypeVariable
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.isConstructor
import org.opentaint.ir.impl.types.Generics.LinkedImpl
import org.opentaint.ir.impl.types.Generics.SingleImpl

class LinkedGenericsTest : BaseTypesTest() {

    @Test
    fun `linked generics original parametrization`() = runBlocking {
        val partial = findClassType<LinkedImpl<*>>()
        with(partial.superType!!) {
            with(typeParameters.first()) {
                assertEquals("T", symbol)
                bounds.first().assertType<Any>()
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
        val partial = findClassType<LinkedImpl<*>>()
        with(partial.superType!!) {
            with(typeArguments[0]) {
                assertType<String>()
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
        val partial = findClassType<LinkedImpl<*>>()
        with(partial.superType!!) {
            val fields = fields
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
                val shouldBeW = (resolvedType.typeArguments.first() as JIRTypeVariable)
                assertEquals("java.util.List<java.lang.String>", shouldBeW.bounds.first().typeName)
            }
        }
    }


    @Test
    fun `generics applied for fields of super types`() {
        runBlocking {
            val superFooType = findClassType<SingleImpl>()
            with(superFooType.superType.assertClassType()) {
                val fields = fields
                assertEquals(2, fields.size)

                with(fields.first()) {
                    assertEquals("state", name)
                    fieldType().assertType<String>()
                }
                with(fields.get(1)) {
                    assertEquals("stateList", name)
                    with(fieldType().assertClassType()) {
                        assertEquals("java.util.ArrayList<java.lang.String>", typeName)
                    }
                }
            }
        }
    }

    @Test
    fun `direct generics from child types applied to methods`() {
        runBlocking {
            val superFooType = findClassType<SingleImpl>()
            val superType = superFooType.superType.assertClassType()
            val methods = superType.declaredMethods.filterNot { it.method.isConstructor }
            assertEquals(2, methods.size)

            with(methods.first { it.method.name == "run1" }) {
                returnType.assertType<String>()
                parameters.first().type.assertType<String>()
            }
        }
    }

    @Test
    fun `custom generics from child types applied to methods`() {
        runBlocking {
            val superFooType = findClassType<SingleImpl>()
            val superType = superFooType.superType.assertClassType()
            val methods = superType.declaredMethods.filterNot { it.method.isConstructor }
            assertEquals(2, methods.size)

            with(methods.first { it.method.name == "run2" }) {
                val params = parameters.first()
                val w = typeParameters.first()

                val bound = (params.type as JIRClassType).typeArguments.first()
                assertEquals("W", (bound as? JIRTypeVariable)?.symbol)
                assertEquals("W", w.symbol)
                bound as JIRTypeVariable
                bound.bounds.first().assertType<String>()
            }
        }
    }

}