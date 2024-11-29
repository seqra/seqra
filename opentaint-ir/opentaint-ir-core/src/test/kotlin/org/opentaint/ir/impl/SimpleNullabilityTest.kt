
package org.opentaint.ir.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRType
import org.opentaint.ir.api.ext.findTypeOrNull
import org.opentaint.ir.api.ext.isNullable

class SimpleNullabilityTest:  BaseTest() {

    companion object : WithDB()

    @Test
    fun `Test field nullability`() = runBlocking {
        val clazz = typeOf<NullableExamples>() as JIRClassType

        val expectedNullability = mapOf(
            "refNullable" to true,
            "refNotNull" to false,
            "primitiveNullable" to true,
            "primitiveNotNull" to false,
        )

        val actualFieldNullability = clazz.declaredFields.associate { it.name to it.field.isNullable }
        val actualFieldTypeNullability = clazz.declaredFields.associate { it.name to it.fieldType.nullable }

        assertEquals(expectedNullability, actualFieldNullability)
        assertEquals(expectedNullability, actualFieldTypeNullability)
    }

    @Test
    fun `Test method parameter isNullable`() = runBlocking {
        val clazz = typeOf<NullableExamples>() as JIRClassType
        val nullableMethod = clazz.declaredMethods.single { it.name == "nullableMethod" }

        val expectedNullability = listOf(true, false)
        val actualParameterNullability = nullableMethod.parameters.map { it.nullable }
        val actualParameterTypeNullability = nullableMethod.parameters.map { it.type.nullable }

        assertEquals(expectedNullability, actualParameterNullability)
        assertEquals(expectedNullability, actualParameterTypeNullability)
    }

    @Test
    fun `Test method isNullable`() = runBlocking {
        val clazz = typeOf<NullableExamples>() as JIRClassType

        val nullableMethod = clazz.declaredMethods.single { it.name == "nullableMethod" }
        assertTrue(nullableMethod.method.isNullable)
        assertTrue(nullableMethod.returnType.nullable)

        val notNullMethod = clazz.declaredMethods.single { it.name == "notNullMethod" }
        assertFalse(notNullMethod.method.isNullable)
        assertFalse(notNullMethod.returnType.nullable)
    }

    private inline fun <reified T> typeOf(): JIRType {
        return cp.findTypeOrNull<T>() ?: throw IllegalStateException("Type ${T::class.java.name} not found")
    }
}