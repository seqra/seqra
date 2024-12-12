package org.opentaint.opentaint-ir.impl.cfg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.opentaint.opentaint-ir.api.JIRClassOrInterface
import org.opentaint.opentaint-ir.api.JIRMethod
import org.opentaint.opentaint-ir.api.ext.findClass
import org.opentaint.opentaint-ir.impl.BaseTest
import org.opentaint.opentaint-ir.impl.WithDB
import org.opentaint.opentaint-ir.impl.cfg.util.loops
import org.opentaint.opentaint-ir.impl.features.InMemoryHierarchy

class LoopsTest : BaseTest() {

    companion object : WithDB(InMemoryHierarchy)

    @Test
    fun `loop inside loop should work`() {
        val clazz = cp.findClass<JavaTasks>()
        with(clazz.findMethod("insertionSort").flowGraph().loops) {
            assertEquals(2, size)
        }
    }

    @Test
    fun `simple for loops`() {
        val clazz = cp.findClass<JavaTasks>()
        with(clazz.findMethod("heapSort").flowGraph().loops) {
            assertEquals(2, size)
        }
    }

    @Test
    fun `simple while loops`() {
        val clazz = cp.findClass<JavaTasks>()
        with(clazz.findMethod("sortTemperatures").flowGraph().loops) {
            assertEquals(2, size)
        }
    }

    @Test
    fun `combined loops`() {
        val clazz = cp.findClass<JavaTasks>()
        with(clazz.findMethod("sortTimes").flowGraph().loops) {
            assertEquals(3, size)
        }
    }

    private fun JIRClassOrInterface.findMethod(name: String): JIRMethod = declaredMethods.first { it.name == name }

}