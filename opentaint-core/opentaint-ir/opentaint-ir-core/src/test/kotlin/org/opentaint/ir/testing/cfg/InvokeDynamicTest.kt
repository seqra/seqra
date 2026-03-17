package org.opentaint.ir.testing.cfg

import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRLambdaExpr
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.testing.WithGlobalDbImmutable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InvokeDynamicTest : BaseInstructionsTest() {

    companion object : WithGlobalDbImmutable()

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

    @Test
    fun `test resolving virtual lambda`() {
        val clazz = cp.findClass<InvokeDynamicExamples.CollectionWithInnerMap>()
        val method = clazz.declaredMethods.find { it.name == "putAll" }!!
        val instructions = method.instList
        val first = instructions[0] as JIRAssignInst
        assertTrue(first.rhv is JIRLambdaExpr)
        val third = instructions[2] as JIRAssignInst
        assertTrue(third.rhv is JIRLambdaExpr)
        runStaticMethod<InvokeDynamicExamples>("testNonStaticLambda")
    }

    @Test
    fun `invoke dynamic constructor`() = runStaticMethod<InvokeDynamicExamples>("testInvokeDynamicConstructor")

    private inline fun <reified T> runStaticMethod(name: String) {
        val clazz = cp.findClass<T>()

        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.single { it.name == name }
        val res = method.invoke(null)
        assertEquals("OK", res)
    }
}
