
package org.opentaint.ir.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.JIRAnnotated
import org.opentaint.ir.api.ext.findClass

class AnnotationsTest: BaseTest() {

    companion object : WithDB()

    @Test
    fun `Test field annotations`() = runBlocking {
        val clazz = cp.findClass<NullableExamples>()

        val actualAnnotations = clazz.declaredFields.associate { it.name to it.annotationsSimple }
        val expectedAnnotations = mapOf(
            "refNullable" to listOf(jbNullable),
            "refNotNull" to listOf(jbNotNull),
            "primitiveNullable" to listOf(jbNullable),
            "primitiveNotNull" to emptyList(),
        )
        assertEquals(expectedAnnotations, actualAnnotations)
    }

    @Test
    fun `Test method parameter annotations`() = runBlocking {
        val clazz = cp.findClass<NullableExamples>()
        val nullableMethod = clazz.declaredMethods.single { it.name == "nullableMethod" }

        val actualAnnotations = nullableMethod.parameters.map { it.annotationsSimple }
        val expectedAnnotations = listOf(listOf(jbNullable), listOf(jbNotNull))
        assertEquals(expectedAnnotations, actualAnnotations)
    }

    @Test
    fun `Test method annotations`() = runBlocking {
        val clazz = cp.findClass<NullableExamples>()

        val nullableMethod = clazz.declaredMethods.single { it.name == "nullableMethod" }
        assertEquals(listOf(jbNullable), nullableMethod.annotationsSimple)

        val notNullMethod = clazz.declaredMethods.single { it.name == "notNullMethod" }
        assertEquals(listOf(jbNotNull), notNullMethod.annotationsSimple)
    }

    private val jbNullable = "org.jetbrains.annotations.Nullable"
    private val jbNotNull  = "org.jetbrains.annotations.NotNull"
    private val JIRAnnotated.annotationsSimple get() = annotations.map { it.name }
}
