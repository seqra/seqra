package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

/**
 * Tests for complex combinations of exception handling + control flow.
 * These test interactions between try/except/finally and loops, with
 * statements, generators, and other control flow constructs.
 */
@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExceptionControlFlowComboTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
def ecfc_break_in_try(items: list) -> int:
    result = 0
    for x in items:
        try:
            if x < 0:
                break
            result += x
        except:
            pass
    return result

def ecfc_continue_in_except(items: list) -> int:
    total = 0
    for x in items:
        try:
            total += 10 // x
        except ZeroDivisionError:
            continue
    return total

def ecfc_return_in_try() -> int:
    try:
        return 42
    except:
        return -1

def ecfc_return_in_except() -> int:
    try:
        x = 1 // 0
    except ZeroDivisionError:
        return -1
    return 0

def ecfc_return_in_finally() -> int:
    try:
        x = 1
    finally:
        return 100

def ecfc_try_in_loop_with_continue(items: list) -> int:
    total = 0
    for x in items:
        try:
            if x == 0:
                continue
            total += 100 // x
        except:
            total -= 1
    return total

def ecfc_nested_try_with_break(items: list) -> int:
    result = 0
    for x in items:
        try:
            try:
                if x < 0:
                    break
                result += x
            except ValueError:
                result -= 1
        except:
            pass
    return result

def ecfc_while_with_try(n: int) -> int:
    total = 0
    i = 0
    while i < n:
        try:
            total += 10 // (i + 1)
        except:
            total -= 1
        i += 1
    return total

def ecfc_try_except_else_in_loop(items: list) -> int:
    total = 0
    for x in items:
        try:
            y = 10 // x
        except ZeroDivisionError:
            total -= 100
        else:
            total += y
    return total

def ecfc_try_finally_in_loop(items: list) -> int:
    total = 0
    for x in items:
        try:
            total += x
        finally:
            total += 1
    return total

def ecfc_except_with_raise(x: int) -> int:
    try:
        if x < 0:
            raise ValueError("negative")
        return x
    except ValueError:
        raise RuntimeError("wrapped")

def ecfc_multiple_except_handlers(x: str) -> int:
    try:
        return int(x)
    except ValueError:
        return -1
    except TypeError:
        return -2
    except:
        return -3

def ecfc_try_with_augmented_assign(items: list) -> int:
    total = 0
    for x in items:
        try:
            total += x * 2
        except TypeError:
            pass
    return total

def ecfc_bare_except_in_loop(items: list) -> int:
    count = 0
    for x in items:
        try:
            y = 10 // x
            count += 1
        except:
            pass
    return count

def ecfc_nested_finally() -> int:
    result = 0
    try:
        try:
            result = 1
        finally:
            result += 10
    finally:
        result += 100
    return result

def ecfc_except_as_in_loop(items: list) -> list:
    errors = []
    for x in items:
        try:
            y = 10 // x
        except ZeroDivisionError as e:
            errors.append(str(e))
    return errors
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }

    private fun func(name: String): PIRFunction =
        cp.findFunctionOrNull("__test__.$name")
            ?: fail("Function $name not found")

    private fun insts(name: String) = func(name).cfg.blocks.flatMap { it.instructions }

    // ─── Break in try ──────────────────────────────────────

    @Test fun `break in try has GetIter and Branch`() {
        val allInsts = insts("ecfc_break_in_try")
        assertTrue(allInsts.any { it.isAssignOf<PIRIterExpr>() }, "Expected GetIter for for-loop")
        assertTrue(allInsts.any { it is PIRBranch }, "Expected Branch for if condition")
    }

    @Test fun `break in try has exception handler`() {
        val handlers = insts("ecfc_break_in_try").filterIsInstance<PIRExceptHandler>()
        assertTrue(handlers.isNotEmpty(), "Expected PIRExceptHandler for try/except")
    }

    @Test fun `break in try has goto for break`() {
        val gotos = insts("ecfc_break_in_try").filterIsInstance<PIRGoto>()
        assertTrue(gotos.isNotEmpty(), "Expected PIRGoto for break")
    }

    // ─── Continue in except ────────────────────────────────

    @Test fun `continue in except has handler and goto`() {
        val allInsts = insts("ecfc_continue_in_except")
        assertTrue(allInsts.any { it is PIRExceptHandler })
        assertTrue(allInsts.any { it is PIRGoto }, "Expected goto for continue")
    }

    @Test fun `continue in except has typed handler`() {
        val handlers = insts("ecfc_continue_in_except").filterIsInstance<PIRExceptHandler>()
        assertTrue(handlers.any { it.exceptionTypes.isNotEmpty() },
            "Expected typed exception handler for ZeroDivisionError")
    }

    // ─── Return in try/except/finally ──────────────────────

    @Test fun `return in try has returns in both paths`() {
        val returns = insts("ecfc_return_in_try").filterIsInstance<PIRReturn>()
        assertTrue(returns.size >= 2, "Expected >= 2 returns (try + except), got ${returns.size}")
    }

    @Test fun `return in except path has handler`() {
        val allInsts = insts("ecfc_return_in_except")
        assertTrue(allInsts.any { it is PIRExceptHandler })
        assertTrue(allInsts.any { it is PIRReturn })
    }

    @Test fun `return in finally has return`() {
        val returns = insts("ecfc_return_in_finally").filterIsInstance<PIRReturn>()
        assertTrue(returns.isNotEmpty(), "Expected return in finally")
    }

    // ─── Try in loop with continue ─────────────────────────

    @Test fun `try in loop with continue has loop and handler`() {
        val allInsts = insts("ecfc_try_in_loop_with_continue")
        assertTrue(allInsts.any { it.isAssignOf<PIRIterExpr>() })
        assertTrue(allInsts.any { it is PIRExceptHandler })
    }

    // ─── Nested try with break ─────────────────────────────

    @Test fun `nested try with break has multiple handlers`() {
        val handlers = insts("ecfc_nested_try_with_break").filterIsInstance<PIRExceptHandler>()
        assertTrue(handlers.size >= 2,
            "Expected >= 2 handlers for nested try, got ${handlers.size}")
    }

    // ─── While with try ────────────────────────────────────

    @Test fun `while with try has branch and handler`() {
        val allInsts = insts("ecfc_while_with_try")
        assertTrue(allInsts.any { it is PIRBranch }, "Expected Branch for while")
        assertTrue(allInsts.any { it is PIRExceptHandler }, "Expected handler for try")
    }

    // ─── Try-except-else in loop ───────────────────────────

    @Test fun `try-except-else in loop has handler and loop`() {
        val allInsts = insts("ecfc_try_except_else_in_loop")
        assertTrue(allInsts.any { it.isAssignOf<PIRIterExpr>() })
        assertTrue(allInsts.any { it is PIRExceptHandler })
    }

    @Test fun `try-except-else in loop has multiple blocks`() {
        val f = func("ecfc_try_except_else_in_loop")
        assertTrue(f.cfg.blocks.size >= 5,
            "Expected >= 5 blocks for try/except/else in loop, got ${f.cfg.blocks.size}")
    }

    // ─── Try-finally in loop ───────────────────────────────

    @Test fun `try-finally in loop has loop structure`() {
        val allInsts = insts("ecfc_try_finally_in_loop")
        assertTrue(allInsts.any { it.isAssignOf<PIRIterExpr>() })
    }

    // ─── Except with raise ─────────────────────────────────

    @Test fun `except with re-raise has handler and raise`() {
        val allInsts = insts("ecfc_except_with_raise")
        assertTrue(allInsts.any { it is PIRExceptHandler })
        assertTrue(allInsts.any { it is PIRRaise })
    }

    // ─── Multiple handlers ─────────────────────────────────

    @Test fun `multiple except handlers produces 3+ handlers`() {
        val handlers = insts("ecfc_multiple_except_handlers").filterIsInstance<PIRExceptHandler>()
        assertTrue(handlers.size >= 3,
            "Expected >= 3 handlers for 3 except clauses, got ${handlers.size}")
    }

    @Test fun `multiple except handlers has returns in each`() {
        val returns = insts("ecfc_multiple_except_handlers").filterIsInstance<PIRReturn>()
        assertTrue(returns.size >= 4,
            "Expected >= 4 returns (try + 3 excepts), got ${returns.size}")
    }

    // ─── Nested finally ────────────────────────────────────

    @Test fun `nested finally has multiple blocks`() {
        val f = func("ecfc_nested_finally")
        assertTrue(f.cfg.blocks.size >= 3,
            "Expected >= 3 blocks for nested finally, got ${f.cfg.blocks.size}")
    }

    // ─── Except as in loop ─────────────────────────────────

    @Test fun `except as in loop has handler with exception var`() {
        val handlers = insts("ecfc_except_as_in_loop").filterIsInstance<PIRExceptHandler>()
        assertTrue(handlers.isNotEmpty(), "Expected handler for except as")
    }

    @Test fun `except as in loop has call to str()`() {
        val calls = insts("ecfc_except_as_in_loop").filterIsInstance<PIRCall>()
        assertTrue(calls.isNotEmpty(), "Expected PIRCall for str(e)")
    }

    // ─── Structural validity ───────────────────────────────

    @Test fun `all exception combo functions have valid CFGs`() {
        val funcNames = listOf(
            "ecfc_break_in_try", "ecfc_continue_in_except",
            "ecfc_return_in_try", "ecfc_return_in_except",
            "ecfc_return_in_finally", "ecfc_try_in_loop_with_continue",
            "ecfc_nested_try_with_break", "ecfc_while_with_try",
            "ecfc_try_except_else_in_loop", "ecfc_try_finally_in_loop",
            "ecfc_except_with_raise", "ecfc_multiple_except_handlers",
            "ecfc_try_with_augmented_assign", "ecfc_bare_except_in_loop",
            "ecfc_nested_finally", "ecfc_except_as_in_loop"
        )
        for (name in funcNames) {
            val f = func(name)
            assertTrue(f.cfg.blocks.isNotEmpty(),
                "Function $name should have non-empty CFG")
            assertNotNull(f.cfg.entry, "Function $name should have entry block")
        }
    }

    @Test fun `all exception combo functions have exception handlers in try blocks`() {
        val funcNames = listOf(
            "ecfc_break_in_try", "ecfc_continue_in_except",
            "ecfc_return_in_try", "ecfc_return_in_except",
            "ecfc_while_with_try", "ecfc_try_except_else_in_loop",
            "ecfc_multiple_except_handlers", "ecfc_bare_except_in_loop"
        )
        for (name in funcNames) {
            val f = func(name)
            val blocksWithHandlers = f.cfg.blocks.filter { it.exceptionHandlers.isNotEmpty() }
            assertTrue(blocksWithHandlers.isNotEmpty(),
                "Function $name should have blocks with exceptionHandlers set")
        }
    }
}
