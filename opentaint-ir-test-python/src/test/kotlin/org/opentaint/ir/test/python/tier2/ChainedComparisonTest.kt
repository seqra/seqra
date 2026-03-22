package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

/**
 * Tests for chained comparison lowering.
 * Python allows `a < b < c` which should be lowered to
 * `(a < b) AND (b < c)` using short-circuit branching.
 */
@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChainedComparisonTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
def cc_simple(x: int) -> bool:
    return 0 < x < 10

def cc_triple(x: int) -> bool:
    return 0 < x < 10 < 100

def cc_mixed_ops(x: int, y: int) -> bool:
    return x < y <= 100

def cc_equality_chain(a: int, b: int, c: int) -> bool:
    return a == b == c

def cc_inequality_chain(a: int, b: int, c: int) -> bool:
    return a != b != c

def cc_ge_chain(x: int) -> bool:
    return 100 >= x >= 0

def cc_in_if(x: int) -> int:
    if 0 < x < 10:
        return 1
    return 0

def cc_in_while(x: int) -> int:
    count = 0
    while 0 < x < 100:
        x = x - 1
        count += 1
    return count

def cc_with_function_call(x: int) -> bool:
    return 0 < abs(x) < 50

def cc_four_operands(a: int, b: int, c: int, d: int) -> bool:
    return a < b < c < d

def cc_mixed_is(x: object) -> bool:
    return x is not None

def cc_single_compare(x: int) -> bool:
    return x > 0
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }

    private fun func(name: String): PIRFunction =
        cp.findFunctionOrNull("__test__.$name")
            ?: fail("Function $name not found")

    private fun insts(name: String) = func(name).cfg.blocks.flatMap { it.instructions }

    // ─── Simple chained comparison ─────────────────────────

    @Test fun `chained comparison a lt b lt c produces 2 compares`() {
        val compares = insts("cc_simple").filterIsInstance<PIRCompare>()
        assertEquals(2, compares.size,
            "Expected exactly 2 PIRCompare for 0 < x < 10, got ${compares.size}")
    }

    @Test fun `chained comparison uses short-circuit branching`() {
        // After fixing DC-18: chained comparisons now use short-circuit AND
        // instead of BIT_AND. This means additional branches are emitted.
        val branches = insts("cc_simple").filterIsInstance<PIRBranch>()
        assertTrue(branches.isNotEmpty(),
            "Expected short-circuit branch for chained comparison")
    }

    @Test fun `chained comparison compare ops are LT`() {
        val compares = insts("cc_simple").filterIsInstance<PIRCompare>()
        assertTrue(compares.all { it.op == PIRCompareOperator.LT },
            "Expected all comparisons to be LT for 0 < x < 10")
    }

    // ─── Triple chained ────────────────────────────────────

    @Test fun `triple chain produces 3 compares`() {
        val compares = insts("cc_triple").filterIsInstance<PIRCompare>()
        assertEquals(3, compares.size,
            "Expected 3 PIRCompare for 0 < x < 10 < 100, got ${compares.size}")
    }

    @Test fun `triple chain uses 2 short-circuit branches`() {
        // 3-operand chain (a < b < c < d) uses 2 short-circuit branches
        val branches = insts("cc_triple").filterIsInstance<PIRBranch>()
        assertTrue(branches.size >= 2,
            "Expected at least 2 short-circuit branches for triple chain, got ${branches.size}")
    }

    // ─── Mixed operators ───────────────────────────────────

    @Test fun `mixed ops chain has LT and LE`() {
        val compares = insts("cc_mixed_ops").filterIsInstance<PIRCompare>()
        assertTrue(compares.any { it.op == PIRCompareOperator.LT },
            "Expected LT for x < y")
        assertTrue(compares.any { it.op == PIRCompareOperator.LE },
            "Expected LE for y <= 100")
    }

    @Test fun `equality chain produces EQ compares`() {
        val compares = insts("cc_equality_chain").filterIsInstance<PIRCompare>()
        assertEquals(2, compares.size, "Expected 2 EQ compares for a == b == c")
        assertTrue(compares.all { it.op == PIRCompareOperator.EQ })
    }

    @Test fun `inequality chain produces NE compares`() {
        val compares = insts("cc_inequality_chain").filterIsInstance<PIRCompare>()
        assertEquals(2, compares.size, "Expected 2 NE compares for a != b != c")
        assertTrue(compares.all { it.op == PIRCompareOperator.NE })
    }

    @Test fun `GE chain produces GE compares`() {
        val compares = insts("cc_ge_chain").filterIsInstance<PIRCompare>()
        assertEquals(2, compares.size, "Expected 2 GE compares for 100 >= x >= 0")
        assertTrue(compares.all { it.op == PIRCompareOperator.GE })
    }

    // ─── Chained in control flow ───────────────────────────

    @Test fun `chained in if produces branch`() {
        val branches = insts("cc_in_if").filterIsInstance<PIRBranch>()
        assertTrue(branches.isNotEmpty(), "Expected PIRBranch for if with chained comparison")
    }

    @Test fun `chained in if produces 2 compares with short-circuit`() {
        val compares = insts("cc_in_if").filterIsInstance<PIRCompare>()
        assertEquals(2, compares.size, "Expected 2 compares in if")
        // Short-circuit: branches for chained comparison + if-statement
        val branches = insts("cc_in_if").filterIsInstance<PIRBranch>()
        assertTrue(branches.size >= 2,
            "Expected at least 2 branches (short-circuit + if)")
    }

    @Test fun `chained in while has loop structure`() {
        val allInsts = insts("cc_in_while")
        assertTrue(allInsts.any { it is PIRCompare }, "Expected PIRCompare in while")
        assertTrue(allInsts.any { it is PIRBranch }, "Expected PIRBranch in while")
    }

    // ─── Four operands ─────────────────────────────────────

    @Test fun `four operand chain produces 3 compares with short-circuit`() {
        val compares = insts("cc_four_operands").filterIsInstance<PIRCompare>()
        assertEquals(3, compares.size, "Expected 3 compares for a < b < c < d")
        // 4-operand chain: 2 short-circuit branches between comparisons
        val branches = insts("cc_four_operands").filterIsInstance<PIRBranch>()
        assertTrue(branches.size >= 2,
            "Expected at least 2 short-circuit branches for 4-operand chain")
    }

    // ─── Single compare (baseline) ─────────────────────────

    @Test fun `single compare produces 1 compare no BIT_AND`() {
        val compares = insts("cc_single_compare").filterIsInstance<PIRCompare>()
        val bitAnds = insts("cc_single_compare").filterIsInstance<PIRBinOp>()
            .filter { it.op == PIRBinaryOperator.BIT_AND }
        assertEquals(1, compares.size, "Expected 1 compare for simple x > 0")
        assertEquals(0, bitAnds.size, "Expected 0 BIT_AND for simple compare")
    }

    // ─── With function call ────────────────────────────────

    @Test fun `chained with function call produces call and 2 compares`() {
        val compares = insts("cc_with_function_call").filterIsInstance<PIRCompare>()
        val calls = insts("cc_with_function_call").filterIsInstance<PIRCall>()
        assertEquals(2, compares.size, "Expected 2 compares for 0 < abs(x) < 50")
        assertTrue(calls.isNotEmpty(), "Expected PIRCall for abs(x)")
    }

    // ─── IS_NOT baseline ───────────────────────────────────

    @Test fun `is not None produces IS_NOT`() {
        val compares = insts("cc_mixed_is").filterIsInstance<PIRCompare>()
        assertTrue(compares.any { it.op == PIRCompareOperator.IS_NOT },
            "Expected IS_NOT for 'x is not None'")
    }

    // ─── General validity ──────────────────────────────────

    @Test fun `all chained comparison functions have valid CFGs`() {
        val funcNames = listOf(
            "cc_simple", "cc_triple", "cc_mixed_ops",
            "cc_equality_chain", "cc_inequality_chain", "cc_ge_chain",
            "cc_in_if", "cc_in_while", "cc_with_function_call",
            "cc_four_operands", "cc_mixed_is", "cc_single_compare"
        )
        for (name in funcNames) {
            val f = func(name)
            assertTrue(f.cfg.blocks.isNotEmpty(),
                "Function $name should have non-empty CFG")
        }
    }
}
