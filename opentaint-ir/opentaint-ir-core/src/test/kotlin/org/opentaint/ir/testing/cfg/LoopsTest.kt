package org.opentaint.ir.testing.cfg

import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.impl.cfg.util.JIRLoop
import org.opentaint.ir.impl.cfg.util.loops
import org.opentaint.ir.testing.BaseTest
import org.opentaint.ir.testing.WithGlobalDbImmutable
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnJre
import org.junit.jupiter.api.condition.JRE

class LoopsTest : BaseTest() {

    companion object : WithGlobalDbImmutable()

    @Test
    fun `loop inside loop should work`() {
        val clazz = cp.findClass<JavaTasks>()
        with(clazz.findMethod("insertionSort").loops) {
            assertEquals(2, size)
            with(get(1)) {
                assertEquals(20, head.lineNumber)
                assertEquals(2, exits.size)
                assertSources(20, 21)
            }

            with(first()) {
                assertEquals(15, head.lineNumber)
                assertEquals(1, exits.size)
                assertSources(15, 25)
            }
        }
    }

    @Test
    fun `simple for loops`() {
        val clazz = cp.findClass<JavaTasks>()
        with(clazz.findMethod("heapSort").loops) {
            assertEquals(2, size)
            with(first()) {
                assertEquals(82, head.lineNumber)
                assertEquals(1, exits.size)
                assertSources(82, 83)
            }

            with(get(1)) {
                assertEquals(86, head.lineNumber)
                assertEquals(1, exits.size)
                assertSources(86, 91)
            }
        }
    }

    @Test
    fun `simple while loops`() {
        val clazz = cp.findClass<JavaTasks>()
        with(clazz.findMethod("sortTemperatures").loops) {
            assertEquals(2, size)
            with(first()) {
                Assertions.assertTrue(head.lineNumber == 119 || head.lineNumber == 116)
                assertEquals(1, exits.size)
            }
            with(get(1)) {
                assertEquals(132, head.lineNumber)
                assertEquals(1, exits.size)
                assertSources(132, 134)
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
                assertEquals(37, head.lineNumber)
                assertEquals(listOf(37, 45, 57), exits.map { it.lineNumber }.toSet().sorted())
                assertSources(37, 59)
            }

            with(get(1)) {
                assertEquals(66, head.lineNumber)
                assertEquals(1, exits.size)
                assertSources(66, 68)
            }
            with(get(2)) {
                assertEquals(69, head.lineNumber)
                assertEquals(1, exits.size)
                assertSources(69, 71)
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
