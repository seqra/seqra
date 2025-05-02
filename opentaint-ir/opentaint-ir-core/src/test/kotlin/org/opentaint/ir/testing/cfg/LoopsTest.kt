package org.opentaint.ir.testing.cfg

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.impl.cfg.util.JIRLoop
import org.opentaint.ir.impl.cfg.util.loops
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithGlobalDB
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnJre
import org.junit.jupiter.api.condition.JRE

class LoopsTest : BaseTest() {

    companion object : WithGlobalDB()

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
                Assertions.assertTrue(head.lineNumber == 135 || head.lineNumber == 132)
                assertEquals(1, exits.size)
            }
            with(get(1)) {
                assertEquals(148, head.lineNumber)
                assertEquals(1, exits.size)
                assertSources(148, 150)
            }
        }
    }

    //Disabled on JAVA_8 because of different bytecode and different lineNumbers for loops
    @Test
    @DisabledOnJre(JRE.JAVA_8)
    fun `combined loops`() {
        val clazz = cp.findClass<JavaTasks>()
        with(clazz.findMethod("sortTimes").loops) {
            assertEquals(3, size)
            with(first()) {
                assertEquals(53, head.lineNumber)
                assertEquals(listOf(53, 61, 73), exits.map { it.lineNumber }.toSet().sorted())
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