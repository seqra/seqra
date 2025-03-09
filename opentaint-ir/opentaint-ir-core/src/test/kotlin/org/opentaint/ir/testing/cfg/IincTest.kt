package org.opentaint.ir.testing.cfg

import org.opentaint.ir.api.ext.findClass
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IincTest : BaseInstructionsTest() {

    @Test
    fun `iinc should work`() {
        val clazz = cp.findClass<Incrementation>()

        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "iinc" }
        val res = method.invoke(null, 0)
        assertEquals(0, res)
    }

    @Test
    fun `iinc arrayIntIdx should work`() {
        val clazz = cp.findClass<Incrementation>()

        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "iincArrayIntIdx" }
        val res = method.invoke(null)
        assertArrayEquals(intArrayOf(1, 0, 2), res as IntArray)
    }

    @Test
    fun `iinc arrayByteIdx should work`() {
        val clazz = cp.findClass<Incrementation>()

        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "iincArrayByteIdx" }
        val res = method.invoke(null)
        assertArrayEquals(intArrayOf(1, 0, 2), res as IntArray)
    }

    @Test
    fun `iinc for`() {
        val clazz = cp.findClass<Incrementation>()

        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "iincFor" }
        val res = method.invoke(null)
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 4), res as IntArray)
    }

    @Test
    fun `iinc if`() {
        val clazz = cp.findClass<Incrementation>()

        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "iincIf" }
        assertArrayEquals(intArrayOf(), method.invoke(null, true, true) as IntArray)
        assertArrayEquals(intArrayOf(0), method.invoke(null, true, false) as IntArray)
    }

    @Test
    fun `iinc if 2`() {
        val clazz = cp.findClass<Incrementation>()

        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "iincIf2" }
        assertEquals(2, method.invoke(null, 1))
        assertEquals(4, method.invoke(null, 2))
    }

//    @Test
    fun `iinc while`() {
        val clazz = cp.findClass<Incrementation>()

        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "iincWhile" }
        assertEquals(2, method.invoke(null) as IntArray)
    }

}