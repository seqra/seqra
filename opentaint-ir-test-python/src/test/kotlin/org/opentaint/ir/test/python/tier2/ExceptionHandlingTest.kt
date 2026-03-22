package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExceptionHandlingTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
def eh_try_except(x: int) -> int:
    try:
        return 1 // x
    except ZeroDivisionError:
        return 0

def eh_raise():
    raise ValueError("bad")
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }

    private fun func(name: String) = cp.findFunctionOrNull("__test__.$name")!!
    private fun insts(name: String) = func(name).cfg.blocks.flatMap { it.instructions }

    @Test
    fun `try-except produces except handler blocks`() {
        val handlers = insts("eh_try_except").filterIsInstance<PIRExceptHandler>()
        Assertions.assertTrue(handlers.isNotEmpty(), "Expected PIRExceptHandler instruction")
    }

    @Test
    fun `raise produces PIRRaise`() {
        val raises = insts("eh_raise").filterIsInstance<PIRRaise>()
        Assertions.assertTrue(raises.isNotEmpty(), "Expected PIRRaise instruction")
    }
}
