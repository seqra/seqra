package org.opentaint.ir.testing.cfg

import org.opentaint.ir.testing.WithDB
import org.junit.jupiter.api.Test

class KotlinInstructionsTest: BaseInstructionsTest() {

    companion object : WithDB()

    @Test
    fun `simple test`() = runKotlinTest(SimpleTest::class.java.name)

    @Test
    fun `kotlin vararg test`() = runKotlinTest(Varargs::class.java.name)

    @Test
    fun `kotlin equals test`() = runKotlinTest(Equals::class.java.name)

    @Test
    fun `kotlin different receivers test`() = runKotlinTest(DifferentReceivers::class.java.name)

    @Test
    fun `kotlin sequence test`() = runKotlinTest(KotlinSequence::class.java.name)

    @Test
    fun `kotlin range test`() = runKotlinTest(Ranges::class.java.name)

    @Test
    fun `kotlin overloading test`() = runKotlinTest(Overloading::class.java.name)

    @Test
    fun `kotlin try catch finally`() = runKotlinTest(TryCatchFinally::class.java.name)

    @Test
    fun `kotlin method with exception`() = runKotlinTest(InvokeMethodWithException::class.java.name)

    @Test
    fun `kotlin typecast`() = runKotlinTest(DoubleComparison::class.java.name)

    @Test
    fun `kotlin when expr`() = runKotlinTest(WhenExpr::class.java.name)

    @Test
    fun `kotlin default args`() = runKotlinTest(DefaultArgs::class.java.name)

}