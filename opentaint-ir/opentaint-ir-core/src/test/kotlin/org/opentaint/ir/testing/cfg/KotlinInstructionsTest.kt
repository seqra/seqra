package org.opentaint.ir.testing.cfg

import org.junit.jupiter.api.Test

class KotlinInstructionsTest: BaseInstructionsTest() {

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
    fun `kotlin range test 2`() = runTest(Ranges2::class.java.name)

//    @Test
//    fun `kotlin overloading test`() = runKotlinTest(Overloading::class.java.name)

    //We have to mute graph checker because of empty catch-es in try/catch blocks
    @Test
    fun `kotlin try catch finally`() = runTest(TryCatchFinally::class.java.name, muteGraphChecker = true)

    @Test
    fun `kotlin try catch finally 2`() = runTest(TryCatchFinally2::class.java.name, muteGraphChecker = true)

    @Test
    fun `kotlin try catch finally 3`() = runTest(TryCatchFinally3::class.java.name, muteGraphChecker = true)

    @Test
    fun `kotlin try catch finally 4`() = runTest(TryCatchFinally4::class.java.name, muteGraphChecker = true)

    @Test
    fun `kotlin method with exception`() = runTest(InvokeMethodWithException::class.java.name)

    @Test
    fun `kotlin typecast`() = runTest(DoubleComparison::class.java.name)

    @Test
    fun `kotlin when expr`() = runTest(WhenExpr::class.java.name)

    @Test
    fun `kotlin default args`() = runTest(DefaultArgs::class.java.name)

    @Test
    fun `kotlin arrays`() = runTest(Arrays::class.java.name)

}