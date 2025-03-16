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

//    @Test
//    fun `kotlin range test 2`() = runKotlinTest(Ranges2::class.java.name)
//
//    @Test
//    fun `kotlin overloading test`() = runKotlinTest(Overloading::class.java.name)

    //We have to mute graph checker because of empty catch-es in try/catch blocks
//    @Test
//    fun `kotlin try catch finally`() = runKotlinTest(TryCatchFinally::class.java.name, muteGraphChecker = true)

    @Test
    fun `kotlin try catch finally 2`() = runKotlinTest(TryCatchFinally2::class.java.name, muteGraphChecker = true)

    @Test
    fun `kotlin try catch finally 3`() = runKotlinTest(TryCatchFinally3::class.java.name, muteGraphChecker = true)

    @Test
    fun `kotlin try catch finally 4`() = runKotlinTest(TryCatchFinally4::class.java.name, muteGraphChecker = true)

    @Test
    fun `kotlin method with exception`() = runKotlinTest(InvokeMethodWithException::class.java.name)

    @Test
    fun `kotlin typecast`() = runKotlinTest(DoubleComparison::class.java.name)

    @Test
    fun `kotlin when expr`() = runKotlinTest(WhenExpr::class.java.name)

    @Test
    fun `kotlin default args`() = runKotlinTest(DefaultArgs::class.java.name)

    @Test
    fun `kotlin arrays`() = runKotlinTest(Arrays::class.java.name)

}