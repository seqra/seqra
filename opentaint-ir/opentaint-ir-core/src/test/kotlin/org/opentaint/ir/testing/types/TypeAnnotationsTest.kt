package org.opentaint.ir.testing.types

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRArrayType
import org.opentaint.ir.api.jvm.JIRBoundedWildcard
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.impl.types.JIRClassTypeImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypeAnnotationsTest : BaseTypesTest() {
    @Test
    fun `type annotations on fields`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()

        val expectedAnnotations = mapOf(
            "refNullable" to listOf(),
            "refNotNull" to listOf(jbNotNull),
            "explicitlyNullable" to listOf(jbNullable),
        )

        val fields = clazz.declaredFields.filter { it.name in expectedAnnotations.keys }
        val actualAnnotations = fields.associate { it.name to it.fieldType.annotations.simplified }

        assertEquals(expectedAnnotations, actualAnnotations)
    }

    @Test
    fun `type annotations on method parameters`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()
        val nullableMethod = clazz.declaredMethods.single { it.name == "nullableMethod" }

        val expectedAnnotations = listOf(listOf(jbNullable), listOf(jbNotNull), listOf())
        val actualParameterAnnotations = nullableMethod.parameters.map { it.type.annotations.simplified }

        assertEquals(expectedAnnotations, actualParameterAnnotations)
    }

    @Test
    fun `type annotations on method return value`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()

        val notNullMethod = clazz.declaredMethods.single { it.name == "notNullMethod" }
        assertEquals(listOf(jbNotNull), notNullMethod.method.annotations.simplified)
    }

    @Test
    fun `type annotations on wildcard bounds`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()

        val notNullMethod = clazz.declaredMethods.single { it.name == "wildcard" }
        val actualAnnotations = ((notNullMethod.returnType as JIRClassTypeImpl).typeArguments[0] as JIRBoundedWildcard)
            .upperBounds[0]
            .annotations
            .simplified
        assertEquals(listOf(jbNotNull), actualAnnotations)
    }

    @Test
    fun `type annotations on inner types`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()

        val innerMethod = clazz.declaredMethods.single { it.name == "inner" }
        val actualAnnotationsOnInner = innerMethod.returnType.annotations.simplified
        val actualAnnotationsOnOuter = (innerMethod.returnType as JIRClassType)
            .outerType!!
            .typeArguments[0]
            .annotations
            .simplified

        assertEquals(listOf(jbNullable), actualAnnotationsOnInner)
        assertEquals(listOf(jbNotNull), actualAnnotationsOnOuter)
    }

    @Test
    fun `type annotations on array types`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()

        val arrayMethod = clazz.declaredMethods.single { it.name == "array" }
        val actualAnnotationsOnArray = arrayMethod.returnType.annotations.simplified
        val actualAnnotationsOnArrayElement = (arrayMethod.returnType as JIRArrayType)
            .elementType
            .annotations
            .simplified

        assertEquals(listOf(jbNullable), actualAnnotationsOnArray)
        assertEquals(listOf(jbNotNull), actualAnnotationsOnArrayElement)
    }

    private val jbNullable = "org.jetbrains.annotations.Nullable"
    private val jbNotNull = "org.jetbrains.annotations.NotNull"
    private val Iterable<JIRAnnotation>.simplified get() = map { it.name }
}