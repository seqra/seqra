package org.opentaint.ir.impl.types

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.JIRArrayType
import org.opentaint.ir.api.JIRPrimitiveType
import org.opentaint.ir.api.isConstructor

class TypesTest : BaseTypesTest() {

    @Test
    fun `primitive and array types`() {
        val primitiveAndArrays = findType<PrimitiveAndArrays>()
        val fields = primitiveAndArrays.declaredFields
        assertEquals(2, fields.size)

        with(fields.first()) {
            assertTrue(fieldType is JIRPrimitiveType)
            assertEquals("value", name)
            assertEquals("int", fieldType.typeName)
        }
        with(fields.get(1)) {
            assertTrue(fieldType is JIRArrayType)
            assertEquals("intArray", name)
            assertEquals("int[]", fieldType.typeName)
        }


        val methods = primitiveAndArrays.declaredMethods.filterNot { it.method.isConstructor }
        with(methods.first()) {
            assertTrue(returnType is JIRArrayType)
            assertEquals("int[]", returnType.typeName)

            assertEquals(1, parameters.size)
            with(parameters.get(0)) {
                assertTrue(type is JIRArrayType)
                assertEquals("java.lang.String[]", type.typeName)
            }
        }
    }
}