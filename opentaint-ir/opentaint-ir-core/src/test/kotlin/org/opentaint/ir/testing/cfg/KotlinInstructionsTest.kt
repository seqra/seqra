package org.opentaint.ir.testing.cfg

import org.opentaint.ir.testing.WithDB
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
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
        assertEquals("OK", res)
    }

    @Test
    fun `simple test`() = runTest(SimpleTest::class.java.name)

    @Test
    fun `kotlin vararg test`() = runTest(Varargs::class.java.name)

    @Test
    fun `kotlin equals test`() = runTest(Equals::class.java.name)

    @Test
    fun `kotlin different receivers test`() = runTest(DifferentReceivers::class.java.name)

    @Test
    fun `kotlin sequence test`() = runTest(KotlinSequence::class.java.name)

    @Test
    fun `kotlin range test`() = runTest(Ranges::class.java.name)

    @Test
    fun `kotlin overloading test`() = runTest(Overloading::class.java.name)

    @Test
    fun `kotlin try catch finally`() = runTest(TryCatchFinally::class.java.name)

    @Test
    fun `kotlin method with exception`() = runTest(InvokeMethodWithException::class.java.name)

    @Test
    fun `kotlin typecast`() = runTest(DoubleComparison::class.java.name)

    @Test
    fun `kotlin when expr`() = runTest(WhenExpr::class.java.name)

    @Test
    fun `kotlin default args`() = runTest(DefaultArgs::class.java.name)

    @Test
    fun `kotlin iinc`() = runTest(Iinc::class.java.name)
}