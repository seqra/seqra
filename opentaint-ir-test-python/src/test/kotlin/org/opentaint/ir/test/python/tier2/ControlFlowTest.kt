package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ControlFlowTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
def cf_if_simple(x: int) -> int:
    if x > 0:
        x = x + 1
    return x

def cf_if_else(x: int) -> str:
    if x > 0:
        return "pos"
    else:
        return "neg"

def cf_if_elif_else(x: int) -> str:
    if x > 0:
        r = "pos"
    elif x < 0:
        r = "neg"
    else:
        r = "zero"
    return r

def cf_while(n: int) -> int:
    i = 0
    while i < n:
        i = i + 1
    return i

def cf_while_break(n: int) -> int:
    i = 0
    while i < 100:
        if i >= n:
            break
        i = i + 1
    return i

def cf_while_continue(n: int) -> int:
    i = 0
    total = 0
    while i < n:
        i = i + 1
        if i % 2 == 0:
            continue
        total = total + i
    return total

def cf_for(items: list) -> int:
    total = 0
    for x in items:
        total = total + x
    return total

def cf_for_break(items: list) -> int:
    for x in items:
        if x < 0:
            break
    return x

def cf_for_continue(items: list) -> int:
    total = 0
    for x in items:
        if x < 0:
            continue
        total = total + x
    return total

def cf_nested_loops(n: int) -> int:
    total = 0
    for i in range(n):
        for j in range(n):
            total = total + 1
    return total

def cf_return_value() -> int:
    return 42

def cf_return_none() -> None:
    x = 1
    return

def cf_ternary(x: int) -> str:
    return "pos" if x > 0 else "neg"

def cf_short_and(a: int, b: int) -> bool:
    return a > 0 and b > 0

def cf_short_or(a: int, b: int) -> bool:
    return a > 0 or b > 0
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }

    private fun func(name: String) = cp.findFunctionOrNull("__test__.$name")!!
    private fun insts(name: String) = func(name).cfg.blocks.flatMap { it.instructions }

    @Test fun `if simple produces branch`() {
        assertTrue(insts("cf_if_simple").any { it is PIRBranch })
        assertTrue(func("cf_if_simple").cfg.blocks.size >= 3)
    }

    @Test fun `if-else produces branch with two paths`() {
        val branches = insts("cf_if_else").filterIsInstance<PIRBranch>()
        assertTrue(branches.isNotEmpty())
        val returns = insts("cf_if_else").filterIsInstance<PIRReturn>()
        assertTrue(returns.size >= 2)
    }

    @Test fun `if-elif-else produces multiple branches`() {
        val branches = insts("cf_if_elif_else").filterIsInstance<PIRBranch>()
        assertTrue(branches.size >= 2, "Expected >= 2 branches for if/elif/else, got ${branches.size}")
    }

    @Test fun `while loop has back edge`() {
        val f = func("cf_while")
        assertTrue(f.cfg.blocks.size >= 3, "While loop needs >= 3 blocks")
        assertTrue(insts("cf_while").any { it is PIRBranch })
    }

    @Test fun `while break produces goto to exit`() {
        val gotos = insts("cf_while_break").filterIsInstance<PIRGoto>()
        assertTrue(gotos.isNotEmpty(), "Break should produce PIRGoto")
    }

    @Test fun `while continue produces goto to header`() {
        val gotos = insts("cf_while_continue").filterIsInstance<PIRGoto>()
        assertTrue(gotos.size >= 2, "Continue should produce gotos (to header and end)")
    }

    @Test fun `for loop produces GetIter and NextIter`() {
        assertTrue(insts("cf_for").any { it.isAssignOf<PIRIterExpr>() })
        assertTrue(insts("cf_for").any { it is PIRNextIter })
    }

    @Test fun `for break produces goto past loop`() {
        assertTrue(insts("cf_for_break").any { it is PIRGoto })
        assertTrue(insts("cf_for_break").any { it is PIRNextIter })
    }

    @Test fun `for continue produces goto to header`() {
        val gotos = insts("cf_for_continue").filterIsInstance<PIRGoto>()
        assertTrue(gotos.size >= 2)
    }

    @Test fun `nested loops produce two GetIter-NextIter pairs`() {
        val getIters = insts("cf_nested_loops").filterAssignOf<PIRIterExpr>()
        val nextIters = insts("cf_nested_loops").filterIsInstance<PIRNextIter>()
        assertTrue(getIters.size >= 2, "Expected 2 GetIter for nested loops, got ${getIters.size}")
        assertTrue(nextIters.size >= 2, "Expected 2 NextIter for nested loops, got ${nextIters.size}")
    }

    @Test fun `return with value`() {
        val rets = insts("cf_return_value").filterIsInstance<PIRReturn>()
        assertTrue(rets.any { it.value != null })
    }

    @Test fun `return none`() {
        val rets = insts("cf_return_none").filterIsInstance<PIRReturn>()
        assertTrue(rets.isNotEmpty())
    }

    @Test fun `ternary produces branch`() {
        assertTrue(insts("cf_ternary").any { it is PIRBranch })
        assertTrue(func("cf_ternary").cfg.blocks.size >= 3)
    }

    @Test fun `short-circuit and produces branch`() {
        assertTrue(insts("cf_short_and").any { it is PIRBranch })
    }

    @Test fun `short-circuit or produces branch`() {
        assertTrue(insts("cf_short_or").any { it is PIRBranch })
    }
}
