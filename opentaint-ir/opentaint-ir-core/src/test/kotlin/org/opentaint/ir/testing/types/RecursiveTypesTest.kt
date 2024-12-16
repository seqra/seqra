package org.opentaint.ir.testing.types

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.testing.types.Comparables.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.opentaint.opentaint-ir.api.ext.isSynthetic

class RecursiveTypesTest : BaseTypesTest() {

    @Test
    fun `recursive type`() {
        runBlocking {
            val comparable1 = findType<ComparableTest1>()
            val compareTo = comparable1.methods.first { it.name == "compareTo" && !it.method.isSynthetic }
            assertEquals("int", compareTo.returnType.typeName)
            compareTo.parameters.first().type.assertClassType<ComparableTest1>()
        }
    }

    @Test
    fun `declaration of recursive type`() {
        runBlocking {
            val comparable2 = findType<ComparableTest2<*>>()
            val compareTo = comparable2.methods.first { it.name == "compareTo" && !it.method.isSynthetic }
            assertEquals("int", compareTo.returnType.typeName)
            with(compareTo.parameters.first().type) {
                assertEquals("T", typeName)
            }
        }
    }

    @Test
    fun `extending type with recursion in declaration`() {
        runBlocking {
            val comparable3 = findType<ComparableTest3>()
            val compareTo = comparable3.superType!!.methods.first { it.name == "compareTo" && !it.method.isSynthetic }
            assertEquals("int", compareTo.returnType.typeName)
            compareTo.parameters.first().type.assertClassType<ComparableTest3>()
        }
    }

    @Test
    fun `extending recursive types in declaration`() {
        runBlocking {
            val comparable5 = findType<ComparableTest5>()
            with(comparable5.superType!!.fields) {
                first { it.name == "stateT" }.fieldType.assertClassType<Int>()
                first { it.name == "stateW" }.fieldType.assertClassType<Int>()
            }
        }
    }

}