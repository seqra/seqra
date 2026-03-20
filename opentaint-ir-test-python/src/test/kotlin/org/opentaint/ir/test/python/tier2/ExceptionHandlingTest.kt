package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

@Tag("tier2")
class ExceptionHandlingTest : PIRTestBase() {

    @Test
    fun `try-except produces except handler blocks`() {
        val cp = buildFromSource("""
            def f(x: int) -> int:
                try:
                    return 1 // x
                except ZeroDivisionError:
                    return 0
        """)
        cp.use {
            val func = it.findFunctionOrNull("__test__.f")
            Assertions.assertNotNull(func, "Function __test__.f not found")
            val handlers = func!!.cfg.blocks.flatMap { b -> b.instructions }
                .filterIsInstance<PIRExceptHandler>()
            Assertions.assertTrue(handlers.isNotEmpty(), "Expected PIRExceptHandler instruction")
        }
    }

    @Test
    fun `raise produces PIRRaise`() {
        val cp = buildFromSource("""
            def f():
                raise ValueError("bad")
        """)
        cp.use {
            val func = it.findFunctionOrNull("__test__.f")
            Assertions.assertNotNull(func, "Function __test__.f not found")
            val raises = func!!.cfg.blocks.flatMap { b -> b.instructions }
                .filterIsInstance<PIRRaise>()
            Assertions.assertTrue(raises.isNotEmpty(), "Expected PIRRaise instruction")
        }
    }
}
