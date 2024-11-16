package org.opentaint.ir.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.JIRAnnotated
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.isNullable

class IsNullableTest: BaseTest() {

    companion object : WithDB()

    @Test
    fun `Test field isNullable`() = runBlocking {
        val clazz = cp.findClass<NullableExamples>()

        val actualAnnotations = clazz.declaredFields.associate { it.name to it.annotationsSimple }
        val expectedAnnotations = mapOf(
            "refNullable" to listOf(jbNullable),
            "refNotNull" to listOf(jbNotNull),
            "primitiveNullable" to listOf(jbNullable),
            "primitiveNotNull" to emptyList(),
        )
        assertEquals(expectedAnnotations, actualAnnotations)

        val actualNullability = clazz.declaredFields.associate { it.name to it.isNullable }
        val expectedNullability = mapOf(
            "refNullable" to true,
            "refNotNull" to false,
            "primitiveNullable" to true,
            "primitiveNotNull" to false,
        )
        assertEquals(expectedNullability, actualNullability)
    }

    @Test
    fun `Test method parameter isNullable`() = runBlocking {
        val clazz = cp.findClass<NullableExamples>()
        val nullableMethod = clazz.declaredMethods.single { it.name == "nullableMethod" }

        val actualAnnotations = nullableMethod.parameters.map { it.annotationsSimple }
        val expectedAnnotations = listOf(listOf(jbNullable), listOf(jbNotNull))
        assertEquals(expectedAnnotations, actualAnnotations)

        val actualNullability = nullableMethod.parameters.map { it.isNullable }
        val expectedNullability = listOf(true, false)
        assertEquals(expectedNullability, actualNullability)
    }

    @Test
    fun `Test method isNullable`() = runBlocking {
        val clazz = cp.findClass<NullableExamples>()

        val nullableMethod = clazz.declaredMethods.single { it.name == "nullableMethod" }
        assertEquals(listOf(jbNullable), nullableMethod.annotationsSimple)
        assertTrue(nullableMethod.isNullable)

        val notNullMethod = clazz.declaredMethods.single { it.name == "notNullMethod" }
        assertEquals(listOf(jbNotNull), notNullMethod.annotationsSimple)
        assertFalse(notNullMethod.isNullable)
    }

    private val jbNullable = "org.jetbrains.annotations.Nullable"
    private val jbNotNull  = "org.jetbrains.annotations.NotNull"
    private val JIRAnnotated.annotationsSimple get() = annotations.map { it.name }
}
