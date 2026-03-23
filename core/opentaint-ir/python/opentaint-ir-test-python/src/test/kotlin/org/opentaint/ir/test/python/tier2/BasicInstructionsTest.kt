package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BasicInstructionsTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
def bi_assign() -> int:
    x = 42
    return x

def bi_add(a: int, b: int) -> int:
    return a + b

def bi_call() -> int:
    return len([1, 2, 3])

def bi_if_else(x: int) -> str:
    if x > 0:
        return "pos"
    else:
        return "neg"

def bi_for_loop(items: list) -> int:
    total = 0
    for x in items:
        total += x
    return total

def bi_while_loop(n: int) -> int:
    i = 0
    while i < n:
        i += 1
    return i

def bi_return() -> int:
    return 42

def bi_list():
    x = [1, 2, 3]
    return x
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }

    private fun func(name: String) = cp.findFunctionOrNull("__test__.$name")!!
    private fun insts(name: String) = func(name).cfg.blocks.flatMap { it.instructions }

    @Test
    fun `assignment produces PIRAssign`() {
        val assigns = insts("bi_assign").filterIsInstance<PIRAssign>()
        Assertions.assertTrue(assigns.isNotEmpty(), "Expected PIRAssign instruction")
    }

    @Test
    fun `binary operations produce PIRBinOp`() {
        val binOps = insts("bi_add").filterAssignOf<PIRBinExpr>()
        Assertions.assertTrue(binOps.any { it.binExpr.op == PIRBinaryOperator.ADD },
            "Expected ADD binary op, found: $binOps")
    }

    @Test
    fun `function call produces PIRCall`() {
        val calls = insts("bi_call").filterIsInstance<PIRCall>()
        Assertions.assertTrue(calls.isNotEmpty(), "Expected PIRCall instruction")
    }

    @Test
    fun `if-else produces branch`() {
        val branches = insts("bi_if_else").filterIsInstance<PIRBranch>()
        Assertions.assertTrue(branches.isNotEmpty(), "Expected PIRBranch instruction")
    }

    @Test
    fun `for loop produces GetIter and NextIter`() {
        val allInsts = insts("bi_for_loop")
        Assertions.assertTrue(allInsts.any { it.isAssignOf<PIRIterExpr>() }, "Expected PIRGetIter")
        Assertions.assertTrue(allInsts.any { it is PIRNextIter }, "Expected PIRNextIter")
    }

    @Test
    fun `while loop produces proper CFG`() {
        val f = func("bi_while_loop")
        Assertions.assertTrue(f.cfg.blocks.size >= 3,
            "Expected at least 3 blocks for while loop, got ${f.cfg.blocks.size}")
    }

    @Test
    fun `return produces PIRReturn`() {
        val returns = insts("bi_return").filterIsInstance<PIRReturn>()
        Assertions.assertTrue(returns.isNotEmpty(), "Expected PIRReturn instruction")
    }

    @Test
    fun `list literal produces PIRBuildList`() {
        val builds = insts("bi_list").filterAssignOf<PIRListExpr>()
        Assertions.assertTrue(builds.isNotEmpty(), "Expected PIRBuildList instruction")
    }
}
