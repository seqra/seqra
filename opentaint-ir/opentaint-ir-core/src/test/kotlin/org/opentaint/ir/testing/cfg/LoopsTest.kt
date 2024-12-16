package org.opentaint.ir.testing.cfg

import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithDB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.opentaint.opentaint-ir.api.JIRClassOrInterface
import org.opentaint.opentaint-ir.api.JIRMethod
import org.opentaint.opentaint-ir.api.ext.findClass
import org.opentaint.opentaint-ir.impl.cfg.util.JIRLoop
import org.opentaint.opentaint-ir.impl.cfg.util.loops
import org.opentaint.opentaint-ir.impl.features.InMemoryHierarchy

class LoopsTest : BaseTest() {

    companion object : WithDB(InMemoryHierarchy)

    @Test
    fun `loop inside loop should work`() {
        val clazz = cp.findClass<JavaTasks>()
        with(clazz.findMethod("insertionSort").loops) {
            assertEquals(2, size)
            with(get(1)) {
                assertEquals(36, head.lineNumber)
                assertEquals(2, exits.size)
                assertSources(36, 37)
            }

            with(first()) {
                assertEquals(31, head.lineNumber)
                assertEquals(1, exits.size)
                assertSources(31, 41)
            }
        }
    }

    @Test
    fun `simple for loops`() {
        val clazz = cp.findClass<JavaTasks>()
        with(clazz.findMethod("heapSort").loops) {
            assertEquals(2, size)
            with(first()) {
                assertEquals(98, head.lineNumber)
                assertEquals(1, exits.size)
                assertSources(98, 99)
            }

            with(get(1)) {
                assertEquals(102, head.lineNumber)
                assertEquals(1, exits.size)
                assertSources(102, 107)
            }
        }
    }

    @Test
    fun `simple while loops`() {
        val clazz = cp.findClass<JavaTasks>()
        with(clazz.findMethod("sortTemperatures").loops) {
            assertEquals(2, size)
            with(first()) {
                assertEquals(135, head.lineNumber)
                assertEquals(1, exits.size)
                assertSources(135, 138)
            }
            with(get(1)) {
                assertEquals(148, head.lineNumber)
                assertEquals(1, exits.size)
                assertSources(148, 150)
            }
        }
    }

    @Test
    fun `combined loops`() {
        val clazz = cp.findClass<JavaTasks>()
        with(clazz.findMethod("sortTimes").loops) {
            assertEquals(3, size)
            with(first()) {
                assertEquals(53, head.lineNumber)
                assertEquals(listOf(53,61, 73) , exits.map { it.lineNumber }.toSet().sorted())
                assertSources(53, 75)
            }

            with(get(1)) {
                assertEquals(82, head.lineNumber)
                assertEquals(1, exits.size)
                assertSources(82, 84)
            }
            with(get(2)) {
                assertEquals(85, head.lineNumber)
                assertEquals(1, exits.size)
                assertSources(85, 87)
            }
        }
    }

    private fun JIRClassOrInterface.findMethod(name: String): JIRMethod = declaredMethods.first { it.name == name }

    private val JIRMethod.loops: List<JIRLoop>
        get() {
            return this.flowGraph().loops.toList().sortedBy { it.head.lineNumber }
        }

    private fun JIRLoop.assertSources(start: Int, end: Int) {
        val sourceLineNumbers = instructions.map { it.lineNumber }
        assertEquals(end, sourceLineNumbers.max())
        assertEquals(start, sourceLineNumbers.min())
    }
}