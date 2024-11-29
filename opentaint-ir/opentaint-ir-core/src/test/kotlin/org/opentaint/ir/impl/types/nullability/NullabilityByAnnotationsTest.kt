
package org.opentaint.ir.impl.types.nullability

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.ext.isNullable
import org.opentaint.ir.impl.types.BaseTypesTest
import org.opentaint.ir.impl.usages.NullAnnotationExamples

class NullabilityByAnnotationsTest: BaseTypesTest() {

    @Test
    fun `Test field nullability`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()

        val expectedNullability = mapOf(
            "refNullable" to null,
            "refNotNull" to false,
            "explicitlyNullable" to true,
            "primitiveValue" to false,
        )

        val fields = clazz.declaredFields.filter { it.name in expectedNullability.keys }
        val actualFieldNullability = fields.associate { it.name to it.field.isNullable }
        val actualFieldTypeNullability = fields.associate { it.name to it.fieldType.nullable }

        assertEquals(expectedNullability, actualFieldNullability)
        assertEquals(expectedNullability, actualFieldTypeNullability)
    }

    @Test
    fun `Test method parameter nullability`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()
        val nullableMethod = clazz.declaredMethods.single { it.name == "nullableMethod" }

        val expectedNullability = listOf(true, false, null)
        val actualParameterNullability = nullableMethod.parameters.map { it.nullable }
        val actualParameterTypeNullability = nullableMethod.parameters.map { it.type.nullable }

        assertEquals(expectedNullability, actualParameterNullability)
        assertEquals(expectedNullability, actualParameterTypeNullability)
    }

    @Test
    fun `Test method nullability`() = runBlocking {
        val clazz = findType<NullAnnotationExamples>()

        val nullableMethod = clazz.declaredMethods.single { it.name == "nullableMethod" }
        assertEquals(null, nullableMethod.method.isNullable)
        assertEquals(null, nullableMethod.returnType.nullable)

        val notNullMethod = clazz.declaredMethods.single { it.name == "notNullMethod" }
        assertEquals(false, notNullMethod.method.isNullable)
        assertEquals(false, notNullMethod.returnType.nullable)
    }
}