package org.opentaint.ir.testing.cfg

import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.testing.WithGlobalDB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InvokeDynamicTest : BaseInstructionsTest() {

    companion object : WithGlobalDB()

    @Test
    fun `test unary function`() = runStaticMethod<InvokeDynamicExamples>("testUnaryFunction")

    @Test
    fun `test method ref unary function`() = runStaticMethod<InvokeDynamicExamples>("testMethodRefUnaryFunction")

    @Test
    fun `test currying function`() = runStaticMethod<InvokeDynamicExamples>("testCurryingFunction")

    @Test
    fun `test sam function`() = runStaticMethod<InvokeDynamicExamples>("testSamFunction")

    @Test
    fun `test sam with default function`() = runStaticMethod<InvokeDynamicExamples>("testSamWithDefaultFunction")

    @Test
    fun `test complex invoke dynamic`() = runStaticMethod<InvokeDynamicExamples>("testComplexInvokeDynamic")

    private inline fun <reified T> runStaticMethod(name: String) {
        val clazz = cp.findClass<T>()

        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.single { it.name == name }
        val res = method.invoke(null)
        assertEquals("OK", res)
    }
}
