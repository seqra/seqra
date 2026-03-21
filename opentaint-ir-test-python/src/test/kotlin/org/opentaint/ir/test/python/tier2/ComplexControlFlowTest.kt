package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComplexControlFlowTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
def ccf_try_in_loop(items: list) -> int:
    total = 0
    for i in items:
        try:
            x = int(i)
            total += x
        except:
            pass
    return total

def ccf_loop_in_try(items: list) -> int:
    try:
        total = 0
        for item in items:
            total += item
        return total
    except:
        return -1

def ccf_nested_for(n: int) -> int:
    total = 0
    for i in range(n):
        for j in range(n):
            for k in range(n):
                total += 1
    return total

def ccf_while_in_for(items: list) -> int:
    total = 0
    for item in items:
        count = item
        while count > 0:
            total += 1
            count -= 1
    return total

def ccf_multi_break(matrix: list) -> int:
    result = 0
    for row in matrix:
        for val_ in row:
            if val_ < 0:
                break
            result += val_
    return result

def ccf_complex_condition(a: int, b: int, c: int) -> str:
    if a > 0 and (b < 10 or c == 5):
        return "match"
    return "no match"

def ccf_loop_else(items: list) -> int:
    for x in items:
        if x < 0:
            break
    else:
        return 0
    return -1

def ccf_while_else(n: int) -> bool:
    while n > 0:
        n -= 1
    else:
        return True
    return False
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }

    private fun func(name: String) = cp.findFunctionOrNull("__test__.$name")!!
    private fun insts(name: String) = func(name).cfg.blocks.flatMap { it.instructions }
    private inline fun <reified T : PIRInstruction> allOf(name: String): List<T> =
        insts(name).filterIsInstance<T>()

    @Test
    fun `ccf_try_in_loop - has for loop`() {
        assertTrue(allOf<PIRGetIter>("ccf_try_in_loop").isNotEmpty(), "Expected PIRGetIter")
        assertTrue(allOf<PIRNextIter>("ccf_try_in_loop").isNotEmpty(), "Expected PIRNextIter")
    }

    @Test
    fun `ccf_try_in_loop - has except handler`() {
        assertTrue(allOf<PIRExceptHandler>("ccf_try_in_loop").isNotEmpty(), "Expected PIRExceptHandler")
    }

    @Test
    fun `ccf_try_in_loop - has sufficient blocks`() {
        val f = func("ccf_try_in_loop")
        assertTrue(f.cfg.blocks.size >= 4, "Expected >= 4 blocks, got ${f.cfg.blocks.size}")
    }

    @Test
    fun `ccf_loop_in_try - has for loop inside try`() {
        assertTrue(allOf<PIRGetIter>("ccf_loop_in_try").isNotEmpty())
        assertTrue(allOf<PIRNextIter>("ccf_loop_in_try").isNotEmpty())
    }

    @Test
    fun `ccf_loop_in_try - has except handler`() {
        assertTrue(allOf<PIRExceptHandler>("ccf_loop_in_try").isNotEmpty())
    }

    @Test
    fun `ccf_loop_in_try - has two return paths`() {
        val returns = allOf<PIRReturn>("ccf_loop_in_try")
        assertTrue(returns.size >= 2, "Expected >= 2 returns, got ${returns.size}")
    }

    @Test
    fun `ccf_nested_for - has three GetIter`() {
        assertTrue(allOf<PIRGetIter>("ccf_nested_for").size >= 3,
            "Expected >= 3 PIRGetIter for triple-nested for")
    }

    @Test
    fun `ccf_nested_for - has three NextIter`() {
        assertTrue(allOf<PIRNextIter>("ccf_nested_for").size >= 3,
            "Expected >= 3 PIRNextIter for triple-nested for")
    }

    @Test
    fun `ccf_nested_for - has many blocks`() {
        assertTrue(func("ccf_nested_for").cfg.blocks.size >= 7,
            "Expected >= 7 blocks for triple-nested loop")
    }

    @Test
    fun `ccf_while_in_for - has for and while`() {
        assertTrue(allOf<PIRGetIter>("ccf_while_in_for").isNotEmpty())
        assertTrue(allOf<PIRBranch>("ccf_while_in_for").isNotEmpty())
    }

    @Test
    fun `ccf_while_in_for - has many blocks`() {
        assertTrue(func("ccf_while_in_for").cfg.blocks.size >= 5,
            "Expected >= 5 blocks for for+while")
    }

    @Test
    fun `ccf_multi_break - has nested loops`() {
        assertTrue(allOf<PIRGetIter>("ccf_multi_break").size >= 2,
            "Expected >= 2 PIRGetIter for nested loops")
    }

    @Test
    fun `ccf_multi_break - has goto for break`() {
        assertTrue(allOf<PIRGoto>("ccf_multi_break").isNotEmpty(), "Expected PIRGoto for break")
    }

    @Test
    fun `ccf_multi_break - has branch for condition`() {
        assertTrue(allOf<PIRBranch>("ccf_multi_break").isNotEmpty(), "Expected PIRBranch")
    }

    @Test
    fun `ccf_complex_condition - has multiple branches`() {
        assertTrue(allOf<PIRBranch>("ccf_complex_condition").size >= 2,
            "Expected >= 2 PIRBranch for short-circuit")
    }

    @Test
    fun `ccf_complex_condition - has GT, LT and EQ comparisons`() {
        val compares = allOf<PIRCompare>("ccf_complex_condition")
        assertTrue(compares.any { it.op == PIRCompareOperator.GT }, "Expected GT")
        assertTrue(compares.any { it.op == PIRCompareOperator.LT }, "Expected LT")
        assertTrue(compares.any { it.op == PIRCompareOperator.EQ }, "Expected EQ")
    }

    @Test
    fun `ccf_loop_else - has for loop`() {
        assertTrue(allOf<PIRGetIter>("ccf_loop_else").isNotEmpty())
        assertTrue(allOf<PIRNextIter>("ccf_loop_else").isNotEmpty())
    }

    @Test
    fun `ccf_loop_else - has goto for break`() {
        assertTrue(allOf<PIRGoto>("ccf_loop_else").isNotEmpty(), "Expected PIRGoto for break")
    }

    @Test
    fun `ccf_loop_else - has two return paths`() {
        assertTrue(allOf<PIRReturn>("ccf_loop_else").size >= 2,
            "Expected >= 2 returns (else + after loop)")
    }

    @Test
    fun `ccf_while_else - has branch for condition`() {
        assertTrue(allOf<PIRBranch>("ccf_while_else").isNotEmpty(), "Expected PIRBranch")
    }

    @Test
    fun `ccf_while_else - has two returns`() {
        assertTrue(allOf<PIRReturn>("ccf_while_else").size >= 2,
            "Expected >= 2 returns (else + after while)")
    }

    @Test
    fun `ccf_while_else - has multiple blocks`() {
        assertTrue(func("ccf_while_else").cfg.blocks.size >= 3,
            "Expected >= 3 blocks for while/else")
    }
}
