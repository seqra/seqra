package org.opentaint.ir.impl.types

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.opentaint.ir.impl.types.Comparables.ComparableTest1
import org.opentaint.ir.impl.types.Comparables.ComparableTest2
import org.opentaint.ir.impl.types.Comparables.ComparableTest3
import org.opentaint.ir.impl.types.Comparables.ComparableTest5

class RecursiveTypesTest : BaseTypesTest() {

    @Test
    fun `recursive type`() {
        runBlocking {
            val comparable1 = findClassType<ComparableTest1>()
            val compareTo = comparable1.methods().first { it.name == "compareTo" }
            assertEquals("int", compareTo.returnType().typeName)
            compareTo.parameters().first().type().assertType<ComparableTest1>()
        }
    }

    @Test
    fun `declaration of recursive type`() {
        runBlocking {
            val comparable2 = findClassType<ComparableTest2<*>>()
            val compareTo = comparable2.methods().first { it.name == "compareTo" }
            assertEquals("int", compareTo.returnType().typeName)
            with(compareTo.parameters().first().type()) {
                assertEquals("T", typeName)
            }
        }
    }

    @Test
    fun `extending type with recursion in declaration`() {
        runBlocking {
            val comparable3 = findClassType<ComparableTest3>()
            val compareTo = comparable3.superType()!!.methods().first { it.name == "compareTo" }
            assertEquals("int", compareTo.returnType().typeName)
            compareTo.parameters().first().type().assertType<ComparableTest3>()
        }
    }

    @Test
    fun `extending recursive types in declaration`() {
        runBlocking {
            val comparable3 = findClassType<ComparableTest5>()
            with(comparable3.superType()!!.fields()) {
                first { it.name == "stateT" }.fieldType().assertType<Int>()
                first { it.name == "stateW" }.fieldType().assertType<Int>()
            }
        }
    }

}