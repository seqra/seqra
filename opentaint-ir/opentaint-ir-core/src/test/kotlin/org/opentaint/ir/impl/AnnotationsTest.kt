
package org.opentaint.ir.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.JIRAnnotated
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.impl.usages.NullAnnotationExamples

class AnnotationsTest : BaseTest() {

    companion object : WithDB()

    @Test
    fun `Test field annotations`() = runBlocking {
        val clazz = cp.findClass<NullAnnotationExamples>()

        val expectedAnnotations = mapOf(
            "refNullable" to emptyList(),
            "refNotNull" to listOf(jbNotNull),
            "explicitlyNullable" to listOf(jbNullable),
            "primitiveValue" to emptyList(),
        )
        val fields = clazz.declaredFields.filter { it.name in expectedAnnotations.keys }
        val actualAnnotations = fields.associate { it.name to it.annotationsSimple }

        assertEquals(expectedAnnotations, actualAnnotations)
    }

    @Test
    fun `Test method parameter annotations`() = runBlocking {
        val clazz = cp.findClass<NullAnnotationExamples>()
        val nullableMethod = clazz.declaredMethods.single { it.name == "nullableMethod" }

        val actualAnnotations = nullableMethod.parameters.map { it.annotationsSimple }
        val expectedAnnotations = listOf(listOf(jbNullable), listOf(jbNotNull), emptyList())
        assertEquals(expectedAnnotations, actualAnnotations)
    }

    @Test
    fun `Test method annotations`() = runBlocking {
        val clazz = cp.findClass<NullAnnotationExamples>()

        val nullableMethod = clazz.declaredMethods.single { it.name == "nullableMethod" }
        assertEquals(emptyList<String>(), nullableMethod.annotationsSimple)

        val notNullMethod = clazz.declaredMethods.single { it.name == "notNullMethod" }
        assertEquals(listOf(jbNotNull), notNullMethod.annotationsSimple)
    }

    private val jbNullable = "org.jetbrains.annotations.Nullable"
    private val jbNotNull  = "org.jetbrains.annotations.NotNull"
    private val JIRAnnotated.annotationsSimple get() = annotations.map { it.name }
}
