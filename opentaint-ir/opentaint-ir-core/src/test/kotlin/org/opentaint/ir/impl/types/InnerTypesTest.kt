package org.opentaint.ir.impl.types

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.opentaint.ir.api.JIRClassType
import org.opentaint.ir.api.JIRTypeVariable
import java.io.Closeable


class InnerTypesTest : BaseTypesTest() {

    @Test
    fun `inner classes linked to method`() {
        runBlocking {
            val classWithInners = findClassType<InnerClasses<*>>()
            val inners = classWithInners.innerTypes()
            assertEquals(4, inners.size)
            val methodLinked = inners.first { it.typeName == "org.opentaint.ir.impl.types.InnerClasses<W>.InnerClasses\$1" }
            with(methodLinked.fields()) {
                with(first { it.name == "stateT" }) {
                    assertEquals("T", (fieldType() as JIRTypeVariable).symbol)
                }
                with(first { it.name == "stateW" }) {
                    assertEquals("W", fieldType().typeName)
                }
            }
        }
    }

    @Test
    fun `get not parameterized inner types`() {
        runBlocking {
            val innerClasses = findClassType<InnerClasses<*>>().innerTypes()
            assertEquals(4, innerClasses.size)
            with(innerClasses.first { it.typeName.contains("InnerState") }) {
                val fields = fields()
                assertEquals(2, fields.size)

                with(fields.first { it.name == "stateW" }) {
                    with(fieldType().assertIs<JIRTypeVariable>()) {
                        assertEquals("W", symbol)
                    }
                }
            }
        }
    }

    @Test
    fun `get inner type linked to parameterized method`() {
        runBlocking {
            val innerClasses = findClassType<InnerClasses<*>>().innerTypes()
            assertEquals(4, innerClasses.size)
            with(innerClasses.first { it.typeName.contains("1") }) {
                val fields = fields()
                assertEquals(4, fields.size)
                interfaces().first().assertType<Runnable>()

                with(fields.first { it.name == "stateT" }) {
                    assertEquals("stateT", name)
                    with(fieldType().assertIs<JIRTypeVariable>()) {
                        assertEquals("T", symbol)
                    }
                }
                with(fields.first { it.name == "stateW" }) {
                    assertEquals("stateW", name)
                    with(fieldType().assertIs<JIRTypeVariable>()) {
                        assertEquals("W", symbol)
                    }
                }
            }
        }
    }

    @Test
    fun `parameterized inner type with parent type parameterization`() {
        runBlocking {
            with(field("stateString")) {
                fields().first { it.name == "stateW" }.fieldType().assertType<String>()
            }
        }
    }

    @Test
    fun `custom parameterization of method overrides outer class parameterization`() {
        runBlocking {
            with(field("stateString")) {
                with(methods().first { it.name == "method" }) {
                    with(returnType().assertIs<JIRTypeVariable>()) {
                        assertEquals("W", symbol)
                        assertEquals("java.util.List<java.io.Closeable>", bounds.first().typeName)
                    }
                }
            }
        }
    }

    private suspend fun field(fieldName: String): JIRClassType {
        return findClassType<InnerClasses<*>>().fields().first {
            it.name == fieldName
        }.fieldType().assertClassType()
    }

    @Test
    fun `custom parameterization of inner type overrides outer class parameterization`() {
        runBlocking {
            with(field("stateClosable")) {
                with(fields().first { it.name == "stateW" }) {
                    fieldType().assertType<Closeable>()
                }
                with(methods().first { it.name == "method" }) {
                    with(returnType().assertIs<JIRTypeVariable>()) {
                        assertEquals("W", symbol)
                        assertEquals("java.util.List<java.lang.Integer>", bounds.first().typeName)
                    }
                }
            }
        }
    }

}