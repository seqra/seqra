package org.opentaint.ir.testing.cfg

import org.opentaint.ir.testing.WithDB
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class KotlinInstructionsTest: BaseInstructionsTest() {

    companion object : WithDB()

    private fun runTest(className: String) {
        val clazz = cp.findClassOrNull(className)
        Assertions.assertNotNull(clazz)

        val javaClazz = testAndLoadClass(clazz!!)
        val clazzInstance = javaClazz.constructors.first().newInstance()
        val method = javaClazz.methods.first { it.name == "box" }
        val res = method.invoke(clazzInstance)
        Assertions.assertEquals("OK", res)
    }

    @Test
    fun `simple test`() = runTest(SimpleTest::class.java.name)

    @Test
    fun `kotlin vararg test`() = runTest(Varargs::class.java.name)

    @Test
    fun `kotlin equals test`() = runTest(Equals::class.java.name)
}