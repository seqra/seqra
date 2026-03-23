package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdvancedExceptionsTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
def exc_try_except_as() -> str:
    try:
        x = 1 / 0
    except ZeroDivisionError as e:
        return str(e)
    return "ok"

def exc_try_multiple() -> int:
    try:
        x = int("abc")
    except ValueError:
        return 1
    except TypeError:
        return 2
    return 0

def exc_try_else() -> str:
    try:
        x = 1
    except Exception:
        return "error"
    else:
        return "ok"
    return "?"

def exc_try_finally() -> int:
    result = 0
    try:
        result = 1
    finally:
        result = result + 10
    return result

def exc_try_except_finally() -> int:
    result = 0
    try:
        result = 1
    except Exception:
        result = -1
    else:
        result = result + 5
    finally:
        result = result + 100
    return result

def exc_raise_from() -> None:
    try:
        x = 1 / 0
    except ZeroDivisionError as e:
        raise ValueError("bad") from e

def exc_bare_raise() -> None:
    try:
        x = 1 / 0
    except ZeroDivisionError:
        raise

def exc_nested_try() -> int:
    try:
        try:
            x = 1 / 0
        except ZeroDivisionError:
            return 1
    except Exception:
        return 2
    return 0
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }
    private fun func(name: String) = cp.findFunctionOrNull("__test__.$name")!!
    private fun insts(name: String) = func(name).cfg.blocks.flatMap { it.instructions }

    @Test fun `try-except-as has handler with target`() {
        val handlers = insts("exc_try_except_as").filterIsInstance<PIRExceptHandler>()
        assertTrue(handlers.isNotEmpty(), "Expected except handlers")
        assertTrue(handlers.any { it.target != null }, "Handler with 'as e' should have a target")
    }

    @Test fun `try with multiple except`() {
        val handlers = insts("exc_try_multiple").filterIsInstance<PIRExceptHandler>()
        assertTrue(handlers.size >= 2, "Expected >= 2 except handlers, got ${handlers.size}")
    }

    @Test fun `try-else creates else block`() {
        val f = func("exc_try_else")
        // Else block should produce additional control flow blocks
        assertTrue(f.cfg.blocks.size >= 3, "try/else should have >= 3 blocks")
    }

    @Test fun `try-finally creates finally block`() {
        val f = func("exc_try_finally")
        // Finally should add blocks
        assertTrue(f.cfg.blocks.size >= 2, "try/finally should have >= 2 blocks")
    }

    @Test fun `try-except-else-finally full structure`() {
        val f = func("exc_try_except_finally")
        val handlers = insts("exc_try_except_finally").filterIsInstance<PIRExceptHandler>()
        assertTrue(handlers.isNotEmpty(), "Should have except handler")
        assertTrue(f.cfg.blocks.size >= 4, "Full try should have >= 4 blocks, got ${f.cfg.blocks.size}")
    }

    @Test fun `raise from has cause`() {
        val raises = insts("exc_raise_from").filterIsInstance<PIRRaise>()
        assertTrue(raises.any { it.cause != null }, "raise ... from ... should have cause")
    }

    @Test fun `bare raise has no exception value`() {
        val raises = insts("exc_bare_raise").filterIsInstance<PIRRaise>()
        assertTrue(raises.any { it.exception == null }, "bare 'raise' should have exception=null")
    }

    @Test fun `nested try produces multiple handler blocks`() {
        val f = func("exc_nested_try")
        val handlers = insts("exc_nested_try").filterIsInstance<PIRExceptHandler>()
        assertTrue(handlers.size >= 2, "Nested try should have >= 2 handlers, got ${handlers.size}")
    }
}
