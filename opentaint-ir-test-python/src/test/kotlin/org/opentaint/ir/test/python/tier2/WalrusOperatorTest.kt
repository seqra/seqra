package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

/**
 * Tests for the walrus operator (:= assignment expression).
 * This operator assigns a value to a variable as part of an expression.
 * The IR should lower it to a PIRAssign + use of the assigned value.
 */
@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WalrusOperatorTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
def w_simple_if(x: int) -> int:
    if (n := x + 1) > 5:
        return n
    return 0

def w_simple_assign(x: int) -> int:
    y = (z := x * 2) + z
    return y

def w_in_while(items: list) -> list:
    result = []
    i = 0
    while (v := items[i] if i < len(items) else None) is not None:
        result.append(v)
        i += 1
    return result

def w_in_list_comp(items: list) -> list:
    return [y for x in items if (y := x * 2) > 5]

def w_nested(x: int) -> int:
    a = (b := (c := x + 1) + 2) + 3
    return a + b + c

def w_reuse_in_expr(x: int) -> int:
    return (n := x * 3) + n + n

def w_in_condition_chain(x: int, y: int) -> int:
    if (a := x + y) > 10 and a < 100:
        return a
    return 0

def w_multiple_walrus(x: int, y: int) -> int:
    if (a := x + 1) > 0 and (b := y + 1) > 0:
        return a + b
    return 0

def w_in_ternary(x: int) -> int:
    return (n := x + 1) if x > 0 else (n := 0)

def w_in_assert(x: int) -> int:
    assert (n := x + 1) > 0, "must be positive"
    return n
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }

    private fun func(name: String): PIRFunction =
        cp.findFunctionOrNull("__test__.$name")
            ?: fail("Function $name not found")

    private fun insts(name: String) = func(name).cfg.blocks.flatMap { it.instructions }

    // ─── Basic walrus tests ────────────────────────────────

    @Test fun `walrus in if produces assign for n`() {
        val assigns = insts("w_simple_if").filterIsInstance<PIRAssign>()
        assertTrue(assigns.isNotEmpty(), "Expected PIRAssign for walrus operator")
    }

    @Test fun `walrus in if has branch`() {
        val branches = insts("w_simple_if").filterIsInstance<PIRBranch>()
        assertTrue(branches.isNotEmpty(), "Expected PIRBranch for if condition")
    }

    @Test fun `walrus in if has return in both paths`() {
        val returns = insts("w_simple_if").filterIsInstance<PIRReturn>()
        assertTrue(returns.size >= 2, "Expected >= 2 returns (then and else), got ${returns.size}")
    }

    @Test fun `walrus simple assign produces multiple assigns`() {
        val assigns = insts("w_simple_assign").filterIsInstance<PIRAssign>()
        // z := x*2 produces assign, then y = z + z produces another
        assertTrue(assigns.size >= 2,
            "Expected >= 2 assigns for walrus + outer assign, got ${assigns.size}")
    }

    @Test fun `walrus reuse in expr assigns once and uses multiple times`() {
        val assigns = insts("w_reuse_in_expr").filterIsInstance<PIRAssign>()
        val binOps = insts("w_reuse_in_expr").filterIsInstance<PIRBinOp>()
        assertTrue(assigns.isNotEmpty(), "Expected PIRAssign for walrus")
        assertTrue(binOps.size >= 2, "Expected >= 2 BinOps for n + n + n, got ${binOps.size}")
    }

    // ─── Walrus in compound expressions ────────────────────

    @Test fun `walrus in condition chain produces assign before branch`() {
        val allInsts = insts("w_in_condition_chain")
        assertTrue(allInsts.any { it is PIRAssign }, "Expected PIRAssign for walrus")
        assertTrue(allInsts.any { it is PIRBranch }, "Expected PIRBranch for condition")
    }

    @Test fun `multiple walrus in condition produces multiple assigns`() {
        val assigns = insts("w_multiple_walrus").filterIsInstance<PIRAssign>()
        assertTrue(assigns.size >= 2,
            "Expected >= 2 assigns for two walrus operators, got ${assigns.size}")
    }

    @Test fun `nested walrus produces 3 assigns`() {
        val assigns = insts("w_nested").filterIsInstance<PIRAssign>()
        // c := x+1, b := c+2, a = b+3
        assertTrue(assigns.size >= 3,
            "Expected >= 3 assigns for nested walrus, got ${assigns.size}")
    }

    // ─── Walrus in comprehension ───────────────────────────

    @Test fun `walrus in list comp has function structure`() {
        val f = func("w_in_list_comp")
        assertTrue(f.cfg.blocks.isNotEmpty(), "Expected CFG for walrus in comprehension")
        val allInsts = insts("w_in_list_comp")
        assertTrue(allInsts.isNotEmpty(), "Expected instructions in walrus comp function")
    }

    // ─── Walrus in ternary ─────────────────────────────────

    @Test fun `walrus in ternary produces assigns`() {
        val assigns = insts("w_in_ternary").filterIsInstance<PIRAssign>()
        assertTrue(assigns.isNotEmpty(), "Expected PIRAssign for walrus in ternary")
    }

    @Test fun `walrus in ternary has branch`() {
        val branches = insts("w_in_ternary").filterIsInstance<PIRBranch>()
        assertTrue(branches.isNotEmpty(), "Expected PIRBranch for ternary condition")
    }

    // ─── Walrus in assert ──────────────────────────────────

    @Test fun `walrus in assert produces assign`() {
        val allInsts = insts("w_in_assert")
        assertTrue(allInsts.any { it is PIRAssign }, "Expected PIRAssign for walrus in assert")
    }

    @Test fun `walrus in assert has branch for assertion check`() {
        val branches = insts("w_in_assert").filterIsInstance<PIRBranch>()
        assertTrue(branches.isNotEmpty(), "Expected PIRBranch for assert condition")
    }

    // ─── General structural checks ─────────────────────────

    @Test fun `all walrus functions have valid CFGs`() {
        val funcNames = listOf(
            "w_simple_if", "w_simple_assign", "w_in_while",
            "w_in_list_comp", "w_nested", "w_reuse_in_expr",
            "w_in_condition_chain", "w_multiple_walrus",
            "w_in_ternary", "w_in_assert"
        )
        for (name in funcNames) {
            val f = func(name)
            assertTrue(f.cfg.blocks.isNotEmpty(),
                "Function $name should have non-empty CFG")
            val blocks = f.cfg.blocks
            // Every CFG should have at least entry block
            assertNotNull(f.cfg.entry, "Function $name should have entry block")
        }
    }

    @Test fun `walrus in while has loop structure`() {
        val allInsts = insts("w_in_while")
        // While loop should have branch + goto (back edge)
        assertTrue(allInsts.any { it is PIRBranch } || allInsts.any { it is PIRGoto },
            "Expected loop structure (branch or goto) in while with walrus")
    }
}
