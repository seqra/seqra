
package org.opentaint.ir.impl.types

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.JIRBoundedWildcard
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRTypeVariable
import org.opentaint.ir.impl.types.WildcardBounds.DirectBound
import org.opentaint.ir.impl.types.WildcardBounds.DirectBoundString
import org.opentaint.ir.impl.types.WildcardBounds.WildcardLowerBound
import org.opentaint.ir.impl.types.WildcardBounds.WildcardLowerBoundString
import org.opentaint.ir.impl.types.WildcardBounds.WildcardUpperBound
import org.opentaint.ir.impl.types.WildcardBounds.WildcardUpperBoundString

class WildcardTypesTest : BaseTypesTest() {

    @Test
    fun `direct types`() {
        runBlocking {
            val bounded = findType<DirectBound<*>>()
            with(bounded.fields.first()) {
                assertEquals("field", name)
                with(fieldType.assertIs<JIRClassType>()) {
                    assertEquals("java.util.List<T>", typeName)
                }
            }
        }
    }

    @Test
    fun `resolved direct types`() {
        runBlocking {
            val bounded = findType<DirectBoundString>()
            with(bounded.superType!!.fields.first()) {
                assertEquals("field", name)
                with(fieldType.assertIs<JIRClassType>()) {
                    assertEquals("java.util.List<java.lang.String>", typeName)
                }
            }
        }
    }

    @Test
    fun `upper bound types`() {
        runBlocking {
            val bounded = findType<WildcardUpperBound<*>>()
            with(bounded.fields.first()) {
                assertEquals("field", name)
                with(fieldType.assertIs<JIRClassType>()) {
                    assertEquals("java.util.List<? extends T>", typeName)
                    with(typeArguments.first().assertIs<JIRBoundedWildcard>()) {
                        upperBounds.first().assertIs<JIRTypeVariable>()
                    }
                }
            }
        }
    }

    @Test
    fun `resolved upper bound types`() {
        runBlocking {
            val bounded = findType<WildcardUpperBoundString>()
            with(bounded.superType!!.fields.first()) {
                assertEquals("field", name)
                with(fieldType.assertIs<JIRClassType>()) {
                    assertEquals("java.util.List<? extends java.lang.String>", typeName)
                    with(typeArguments.first().assertIs<JIRBoundedWildcard>()) {
                        upperBounds.first().assertClassType<String>()
                    }
                }
            }
        }
    }

    @Test
    fun `lower bound types`() {
        runBlocking {
            val bounded = findType<WildcardLowerBound<*>>()
            with(bounded.fields.first()) {
                assertEquals("field", name)
                with(fieldType.assertIs<JIRClassType>()) {
                    assertEquals("java.util.List<? super T>", typeName)
                    with(typeArguments.first().assertIs<JIRBoundedWildcard>()) {
                        lowerBounds.first().assertIs<JIRTypeVariable>()
                    }
                }
            }
        }
    }

    @Test
    fun `resolved lower bound types`() {
        runBlocking {
            val bounded = findType<WildcardLowerBoundString>()
            with(bounded.superType!!.fields.first()) {
                assertEquals("field", name)
                with(fieldType.assertIs<JIRClassType>()) {
                    assertEquals("java.util.List<? super java.lang.String>", typeName)
                    with(typeArguments.first().assertIs<JIRBoundedWildcard>()) {
                        lowerBounds.first().assertClassType<String>()
                    }
                }
            }
        }
    }
}