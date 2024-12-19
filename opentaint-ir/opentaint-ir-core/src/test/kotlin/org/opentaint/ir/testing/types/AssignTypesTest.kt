package org.opentaint.ir.testing.types

import org.opentaint.ir.api.ext.isAssignable
import org.opentaint.ir.api.ext.objectType
import org.opentaint.ir.api.throwClassNotFound
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AssignTypesTest : BaseTypesTest() {

    @Test
    fun `unboxing is working`() {
        assertTrue("java.lang.Byte".type.isAssignable("byte".type))
        assertTrue("byte".type.isAssignable("java.lang.Byte".type))

        assertTrue("int".type.isAssignable("java.lang.Integer".type))
        assertTrue("java.lang.Integer".type.isAssignable("int".type))
    }

    @Test
    fun `arrays is working`() {
        assertTrue("byte[]".type.isAssignable(cp.objectType))
        assertFalse("java.lang.Byte[]".type.isAssignable("byte[]".type))
        assertFalse("byte[]".type.isAssignable("java.lang.Byte[]".type))
        assertFalse("int[]".type.isAssignable("byte[]".type))

        assertTrue("boolean[][]".type.isAssignable("java.lang.Object[]".type))
        assertTrue("boolean[][][]".type.isAssignable("java.lang.Cloneable[][]".type))
    }

    @Test
    fun `class type is working`() {
        assertTrue("java.util.List".type.isAssignable("java.util.Collection".type))
        assertTrue("java.util.AbstractList".type.isAssignable("java.util.Collection".type))

        assertFalse(cp.objectType.isAssignable("java.util.Collection".type))
    }

    @Test
    fun `primitive type is working`() {
        assertTrue("byte".type.isAssignable("int".type))
        assertTrue("byte".type.isAssignable("byte".type))
        assertTrue("java.lang.Byte".type.isAssignable("byte".type))
        assertTrue("java.lang.Integer".type.isAssignable("int".type))

        assertTrue("long".type.isAssignable("double".type))
        assertFalse("boolean".type.isAssignable("short".type))
    }

    private val String.type get() = cp.findTypeOrNull(this) ?: throwClassNotFound()
}