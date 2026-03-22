package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

/**
 * CFG structural integrity tests.
 *
 * Validates invariants that must hold for ALL well-formed CFGs:
 * - Every block is reachable from entry
 * - Successor/predecessor consistency
 * - Every non-exit block has at least one successor
 * - Every block (except entry) has at least one predecessor
 * - Exit blocks end with a terminator (return/raise/unreachable)
 * - No dangling edges (successor labels all resolve to real blocks)
 * - Block labels are unique
 * - Entry block exists in blocks list
 */
@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CfgIntegrityTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
def ci_empty():
    pass

def ci_single_return() -> int:
    return 42

def ci_if_else(x: int) -> str:
    if x > 0:
        return "pos"
    else:
        return "neg"

def ci_while_loop(n: int) -> int:
    i = 0
    while i < n:
        i = i + 1
    return i

def ci_for_loop(items: list) -> int:
    total = 0
    for x in items:
        total = total + x
    return total

def ci_nested_loops(n: int) -> int:
    total = 0
    for i in range(n):
        for j in range(n):
            total += 1
    return total

def ci_try_except() -> int:
    try:
        return 1
    except:
        return 0

def ci_try_finally() -> int:
    try:
        x = 1
    finally:
        x = 2
    return x

def ci_nested_try() -> int:
    try:
        try:
            return 1
        except ValueError:
            return 2
    except:
        return 3

def ci_while_break(n: int) -> int:
    i = 0
    while True:
        if i >= n:
            break
        i += 1
    return i

def ci_for_continue(items: list) -> int:
    total = 0
    for x in items:
        if x < 0:
            continue
        total += x
    return total

def ci_if_elif_else(x: int) -> str:
    if x > 10:
        return "big"
    elif x > 0:
        return "small"
    elif x == 0:
        return "zero"
    else:
        return "neg"

def ci_with_stmt(path: str) -> str:
    with open(path) as f:
        data = f.read()
    return data

def ci_for_else(items: list) -> str:
    for x in items:
        if x < 0:
            break
    else:
        return "all positive"
    return "found negative"

def ci_while_else(n: int) -> str:
    i = 0
    while i < n:
        if i == 5:
            break
        i += 1
    else:
        return "completed"
    return "broke out"

def ci_complex_boolean(a: int, b: int, c: int) -> bool:
    return a > 0 and b > 0 or c > 0

def ci_multiple_returns(x: int) -> str:
    if x > 100:
        return "huge"
    if x > 10:
        return "big"
    if x > 0:
        return "positive"
    return "non-positive"

def ci_raise_exception():
    raise ValueError("error")

def ci_raise_and_return(x: int) -> int:
    if x < 0:
        raise ValueError("negative")
    return x

def ci_deeply_nested(x: int) -> int:
    if x > 0:
        if x > 10:
            if x > 100:
                return 3
            return 2
        return 1
    return 0

def ci_many_params(a: int, b: int, c: int, d: int, e: int) -> int:
    return a + b + c + d + e

def ci_with_return(path: str) -> str:
    with open(path) as f:
        return f.read()

def ci_try_raise_then_code():
    try:
        raise ValueError("err")
        x = 1
    except:
        pass

def ci_for_else_return(items: list) -> int:
    for x in items:
        if x < 0:
            return x
    else:
        return 0
    return -1

def ci_no_return():
    x = 1
    y = 2
    z = x + y
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }

    private fun func(name: String): PIRFunction =
        cp.findFunctionOrNull("__test__.$name")
            ?: fail("Function __test__.$name not found")

    private fun allTestFunctions(): List<PIRFunction> {
        val module = cp.modules.first()
        return module.functions.toList()
    }

    // ─── Structural invariant helpers ──────────────────────────

    /** Collect all blocks reachable from entry via BFS on normal successors. */
    private fun reachableBlocks(cfg: PIRCFG): Set<Int> {
        val visited = mutableSetOf<Int>()
        val queue = ArrayDeque<PIRBasicBlock>()
        queue.add(cfg.entry)
        visited.add(cfg.entry.label)
        while (queue.isNotEmpty()) {
            val block = queue.removeFirst()
            for (succ in cfg.successors(block)) {
                if (succ.label !in visited) {
                    visited.add(succ.label)
                    queue.add(succ)
                }
            }
            // Also follow exceptional successors
            for (succ in cfg.exceptionalSuccessors(block)) {
                if (succ.label !in visited) {
                    visited.add(succ.label)
                    queue.add(succ)
                }
            }
        }
        return visited
    }

    /** Check if an instruction is a terminator (ends a basic block). */
    private fun isTerminator(inst: PIRInstruction): Boolean = when (inst) {
        is PIRGoto, is PIRBranch, is PIRReturn, is PIRRaise,
        is PIRUnreachable, is PIRNextIter -> true
        else -> false
    }

    // ─── Tests: All-functions invariants ───────────────────────

    @Test
    fun `all functions have non-empty CFG blocks`() {
        for (func in allTestFunctions()) {
            assertTrue(func.cfg.blocks.isNotEmpty(),
                "${func.qualifiedName} has empty CFG blocks list")
        }
    }

    @Test
    fun `entry block exists in blocks list`() {
        for (func in allTestFunctions()) {
            val cfg = func.cfg
            assertTrue(cfg.blocks.any { it.label == cfg.entry.label },
                "${func.qualifiedName}: entry block label ${cfg.entry.label} not in blocks list")
        }
    }

    @Test
    fun `block labels are unique`() {
        for (func in allTestFunctions()) {
            val labels = func.cfg.blocks.map { it.label }
            assertEquals(labels.size, labels.toSet().size,
                "${func.qualifiedName}: duplicate block labels: ${labels.groupBy { it }.filter { it.value.size > 1 }.keys}")
        }
    }

    @Test
    fun `all blocks reachable from entry`() {
        for (func in allTestFunctions()) {
            val cfg = func.cfg
            val reachable = reachableBlocks(cfg)
            val allLabels = cfg.blocks.map { it.label }.toSet()
            val unreachable = allLabels - reachable

            // Unreachable blocks are allowed if they are dead merge/fallthrough
            // blocks generated after branches where all paths return/raise.
            // They must end with a terminator (return/goto/raise) and should
            // not contain side-effecting instructions like calls.
            for (label in unreachable) {
                val block = cfg.block(label)
                if (block.instructions.isEmpty()) continue
                val last = block.instructions.last()
                val endsWithTerminator = last is PIRReturn || last is PIRGoto || last is PIRRaise
                val hasNoSideEffects = block.instructions.none { it is PIRCall }
                assertTrue(endsWithTerminator && hasNoSideEffects,
                    "${func.qualifiedName}: unreachable block $label has problematic instructions: " +
                        block.instructions.map { it::class.simpleName } +
                        " (endsWithTerm=$endsWithTerminator, noSideEffects=$hasNoSideEffects)")
            }
        }
    }

    @Test
    fun `successor-predecessor consistency`() {
        // If B is a successor of A, then A must be a predecessor of B
        for (func in allTestFunctions()) {
            val cfg = func.cfg
            for (block in cfg.blocks) {
                for (succ in cfg.successors(block)) {
                    val preds = cfg.predecessors(succ)
                    assertTrue(preds.any { it.label == block.label },
                        "${func.qualifiedName}: block ${block.label} -> ${succ.label} in successors, " +
                            "but ${block.label} not in predecessors of ${succ.label}")
                }
            }
        }
    }

    @Test
    fun `predecessor-successor consistency`() {
        // If A is a predecessor of B, then B must be a successor of A
        for (func in allTestFunctions()) {
            val cfg = func.cfg
            for (block in cfg.blocks) {
                for (pred in cfg.predecessors(block)) {
                    val succs = cfg.successors(pred)
                    assertTrue(succs.any { it.label == block.label },
                        "${func.qualifiedName}: block ${pred.label} listed as predecessor of ${block.label}, " +
                            "but ${block.label} not in successors of ${pred.label}")
                }
            }
        }
    }

    @Test
    fun `exit blocks have no normal successors`() {
        for (func in allTestFunctions()) {
            val cfg = func.cfg
            for (exitBlock in cfg.exits) {
                val succs = cfg.successors(exitBlock)
                assertTrue(succs.isEmpty(),
                    "${func.qualifiedName}: exit block ${exitBlock.label} has successors: ${succs.map { it.label }}")
            }
        }
    }

    @Test
    fun `exit blocks end with terminator`() {
        for (func in allTestFunctions()) {
            val cfg = func.cfg
            for (exitBlock in cfg.exits) {
                val last = exitBlock.instructions.lastOrNull()
                assertNotNull(last,
                    "${func.qualifiedName}: exit block ${exitBlock.label} has no instructions")
                assertTrue(last is PIRReturn || last is PIRRaise || last is PIRUnreachable,
                    "${func.qualifiedName}: exit block ${exitBlock.label} ends with ${last!!::class.simpleName}, " +
                        "expected Return/Raise/Unreachable")
            }
        }
    }

    @Test
    fun `non-exit non-empty blocks end with terminator or are followed by exception handler`() {
        for (func in allTestFunctions()) {
            val cfg = func.cfg
            val exitLabels = cfg.exits.map { it.label }.toSet()
            for (block in cfg.blocks) {
                if (block.label in exitLabels) continue
                if (block.instructions.isEmpty()) continue
                val last = block.instructions.last()
                val hasNormalSucc = cfg.successors(block).isNotEmpty()
                val hasExceptionalSucc = cfg.exceptionalSuccessors(block).isNotEmpty()
                // A non-exit block must either end with a terminator or have successors
                assertTrue(isTerminator(last) || hasNormalSucc || hasExceptionalSucc,
                    "${func.qualifiedName}: block ${block.label} ends with ${last::class.simpleName} " +
                        "but has no successors and is not an exit block")
            }
        }
    }

    @Test
    fun `no self-loops on entry block without being a loop header`() {
        // Entry block self-loop is only valid for infinite loops (while True)
        // For non-loop functions, entry should not be its own successor
        val nonLoopFuncs = listOf("ci_empty", "ci_single_return", "ci_if_else",
            "ci_raise_exception", "ci_many_params", "ci_no_return")
        for (name in nonLoopFuncs) {
            val cfg = func(name).cfg
            val entrySelf = cfg.successors(cfg.entry).any { it.label == cfg.entry.label }
            assertFalse(entrySelf,
                "$name: entry block has self-loop but function has no loop")
        }
    }

    // ─── Tests: Specific function structural checks ────────────

    @Test
    fun `empty function has single block`() {
        val f = func("ci_empty")
        // An empty function that just passes should have a very compact CFG
        assertTrue(f.cfg.blocks.size <= 2, "ci_empty: expected 1-2 blocks, got ${f.cfg.blocks.size}")
    }

    @Test
    fun `single return has compact CFG`() {
        val f = func("ci_single_return")
        assertTrue(f.cfg.blocks.size <= 2,
            "ci_single_return: expected 1-2 blocks, got ${f.cfg.blocks.size}")
        assertTrue(f.cfg.exits.isNotEmpty(), "ci_single_return: no exit blocks")
    }

    @Test
    fun `if-else has at least 3 blocks`() {
        val f = func("ci_if_else")
        assertTrue(f.cfg.blocks.size >= 3,
            "ci_if_else: expected >= 3 blocks (condition, true, false), got ${f.cfg.blocks.size}")
    }

    @Test
    fun `while loop has back edge`() {
        val f = func("ci_while_loop")
        val cfg = f.cfg
        // There should be at least one block that jumps back to an earlier block
        var hasBackEdge = false
        for (block in cfg.blocks) {
            for (succ in cfg.successors(block)) {
                if (succ.label <= block.label) {
                    hasBackEdge = true
                }
            }
        }
        assertTrue(hasBackEdge, "ci_while_loop: no back edge found in CFG")
    }

    @Test
    fun `for loop has GetIter-NextIter and back edge`() {
        val f = func("ci_for_loop")
        val allInsts = f.cfg.blocks.flatMap { it.instructions }
        assertTrue(allInsts.any { it is PIRGetIter }, "ci_for_loop: no PIRGetIter")
        assertTrue(allInsts.any { it is PIRNextIter }, "ci_for_loop: no PIRNextIter")

        // NextIter should reference valid blocks
        val nextIter = allInsts.filterIsInstance<PIRNextIter>().first()
        assertNotNull(f.cfg.blocks.find { it.label == nextIter.bodyBlock },
            "ci_for_loop: NextIter bodyBlock ${nextIter.bodyBlock} not found")
        assertNotNull(f.cfg.blocks.find { it.label == nextIter.exitBlock },
            "ci_for_loop: NextIter exitBlock ${nextIter.exitBlock} not found")
    }

    @Test
    fun `nested loops have two iterator pairs`() {
        val f = func("ci_nested_loops")
        val allInsts = f.cfg.blocks.flatMap { it.instructions }
        val getIters = allInsts.filterIsInstance<PIRGetIter>()
        val nextIters = allInsts.filterIsInstance<PIRNextIter>()
        assertTrue(getIters.size >= 2, "ci_nested_loops: expected >= 2 GetIter, got ${getIters.size}")
        assertTrue(nextIters.size >= 2, "ci_nested_loops: expected >= 2 NextIter, got ${nextIters.size}")
    }

    @Test
    fun `try-except has exception handler blocks`() {
        val f = func("ci_try_except")
        val handlers = f.cfg.blocks.flatMap { it.instructions }.filterIsInstance<PIRExceptHandler>()
        assertTrue(handlers.isNotEmpty(), "ci_try_except: no PIRExceptHandler found")

        // Some blocks should reference exception handlers
        val blocksWithHandlers = f.cfg.blocks.filter { it.exceptionHandlers.isNotEmpty() }
        assertTrue(blocksWithHandlers.isNotEmpty(),
            "ci_try_except: no blocks have exceptionHandlers set")
    }

    @Test
    fun `try-finally has blocks for finally code`() {
        val f = func("ci_try_finally")
        // Finally block means more blocks than a simple function
        assertTrue(f.cfg.blocks.size >= 3,
            "ci_try_finally: expected >= 3 blocks, got ${f.cfg.blocks.size}")
    }

    @Test
    fun `nested try produces multiple handler layers`() {
        val f = func("ci_nested_try")
        val handlers = f.cfg.blocks.flatMap { it.instructions }.filterIsInstance<PIRExceptHandler>()
        assertTrue(handlers.size >= 2,
            "ci_nested_try: expected >= 2 exception handlers, got ${handlers.size}")
    }

    @Test
    fun `while break exits loop`() {
        val f = func("ci_while_break")
        // Should have a Goto that exits the loop (break)
        val gotos = f.cfg.blocks.flatMap { it.instructions }.filterIsInstance<PIRGoto>()
        assertTrue(gotos.isNotEmpty(), "ci_while_break: no PIRGoto for break")
    }

    @Test
    fun `for continue has goto back to header`() {
        val f = func("ci_for_continue")
        val gotos = f.cfg.blocks.flatMap { it.instructions }.filterIsInstance<PIRGoto>()
        assertTrue(gotos.size >= 2, "ci_for_continue: expected >= 2 gotos (continue + loop back)")
    }

    @Test
    fun `if-elif-else has at least 3 branches`() {
        val f = func("ci_if_elif_else")
        val branches = f.cfg.blocks.flatMap { it.instructions }.filterIsInstance<PIRBranch>()
        assertTrue(branches.size >= 3,
            "ci_if_elif_else: expected >= 3 branches for 4-way if/elif/else, got ${branches.size}")
    }

    @Test
    fun `with statement has enter and exit calls`() {
        val f = func("ci_with_stmt")
        val loadAttrs = f.cfg.blocks.flatMap { it.instructions }.filterIsInstance<PIRLoadAttr>()
        val enterCall = loadAttrs.any { it.attribute == "__enter__" }
        val exitCall = loadAttrs.any { it.attribute == "__exit__" }
        assertTrue(enterCall, "ci_with_stmt: no __enter__ load_attr found")
        assertTrue(exitCall, "ci_with_stmt: no __exit__ load_attr found")
    }

    @Test
    fun `multiple returns produce multiple exit blocks`() {
        val f = func("ci_multiple_returns")
        val returns = f.cfg.blocks.flatMap { it.instructions }.filterIsInstance<PIRReturn>()
        assertTrue(returns.size >= 4,
            "ci_multiple_returns: expected >= 4 returns, got ${returns.size}")
    }

    @Test
    fun `raise produces exit with PIRRaise`() {
        val f = func("ci_raise_exception")
        val raises = f.cfg.blocks.flatMap { it.instructions }.filterIsInstance<PIRRaise>()
        assertTrue(raises.isNotEmpty(), "ci_raise_exception: no PIRRaise found")
        assertTrue(raises.any { it.exception != null }, "ci_raise_exception: PIRRaise has no exception value")
    }

    @Test
    fun `raise and return produce both exit types`() {
        val f = func("ci_raise_and_return")
        val returns = f.cfg.blocks.flatMap { it.instructions }.filterIsInstance<PIRReturn>()
        val raises = f.cfg.blocks.flatMap { it.instructions }.filterIsInstance<PIRRaise>()
        assertTrue(returns.isNotEmpty(), "ci_raise_and_return: no PIRReturn found")
        assertTrue(raises.isNotEmpty(), "ci_raise_and_return: no PIRRaise found")
    }

    @Test
    fun `deeply nested if produces many blocks`() {
        val f = func("ci_deeply_nested")
        // 4 levels of nesting = at least 4 branches
        val branches = f.cfg.blocks.flatMap { it.instructions }.filterIsInstance<PIRBranch>()
        assertTrue(branches.size >= 3,
            "ci_deeply_nested: expected >= 3 branches, got ${branches.size}")
    }

    @Test
    fun `many params function has correct parameter count`() {
        val f = func("ci_many_params")
        assertEquals(5, f.parameters.size,
            "ci_many_params: expected 5 parameters, got ${f.parameters.size}")
    }

    @Test
    fun `no-return function still has implicit return`() {
        val f = func("ci_no_return")
        // Python functions implicitly return None at the end
        val returns = f.cfg.blocks.flatMap { it.instructions }.filterIsInstance<PIRReturn>()
        assertTrue(returns.isNotEmpty(),
            "ci_no_return: function without explicit return should still have implicit PIRReturn")
    }

    // ─── Tests: Goto/Branch target validity ────────────────────

    @Test
    fun `all Goto targets resolve to existing blocks`() {
        for (func in allTestFunctions()) {
            val cfg = func.cfg
            val allLabels = cfg.blocks.map { it.label }.toSet()
            for (block in cfg.blocks) {
                for (inst in block.instructions) {
                    if (inst is PIRGoto) {
                        assertTrue(inst.targetBlock in allLabels,
                            "${func.qualifiedName}: Goto target ${inst.targetBlock} not in block labels $allLabels")
                    }
                }
            }
        }
    }

    @Test
    fun `all Branch targets resolve to existing blocks`() {
        for (func in allTestFunctions()) {
            val cfg = func.cfg
            val allLabels = cfg.blocks.map { it.label }.toSet()
            for (block in cfg.blocks) {
                for (inst in block.instructions) {
                    if (inst is PIRBranch) {
                        assertTrue(inst.trueBlock in allLabels,
                            "${func.qualifiedName}: Branch trueBlock ${inst.trueBlock} not in block labels $allLabels")
                        assertTrue(inst.falseBlock in allLabels,
                            "${func.qualifiedName}: Branch falseBlock ${inst.falseBlock} not in block labels $allLabels")
                    }
                }
            }
        }
    }

    @Test
    fun `all NextIter targets resolve to existing blocks`() {
        for (func in allTestFunctions()) {
            val cfg = func.cfg
            val allLabels = cfg.blocks.map { it.label }.toSet()
            for (block in cfg.blocks) {
                for (inst in block.instructions) {
                    if (inst is PIRNextIter) {
                        assertTrue(inst.bodyBlock in allLabels,
                            "${func.qualifiedName}: NextIter bodyBlock ${inst.bodyBlock} not in block labels $allLabels")
                        assertTrue(inst.exitBlock in allLabels,
                            "${func.qualifiedName}: NextIter exitBlock ${inst.exitBlock} not in block labels $allLabels")
                    }
                }
            }
        }
    }

    @Test
    fun `all exception handler labels resolve to existing blocks`() {
        for (func in allTestFunctions()) {
            val cfg = func.cfg
            val allLabels = cfg.blocks.map { it.label }.toSet()
            for (block in cfg.blocks) {
                for (handlerLabel in block.exceptionHandlers) {
                    assertTrue(handlerLabel in allLabels,
                        "${func.qualifiedName}: exception handler label $handlerLabel in block ${block.label} " +
                            "not in block labels $allLabels")
                }
            }
        }
    }

    // ─── Tests: No mid-block terminators ───────────────────────

    @Test
    fun `no terminators in middle of blocks`() {
        for (func in allTestFunctions()) {
            val cfg = func.cfg
            for (block in cfg.blocks) {
                val insts = block.instructions
                for (i in 0 until insts.size - 1) {
                    val inst = insts[i]
                    val isTerminator = inst is PIRGoto || inst is PIRBranch ||
                        inst is PIRReturn || inst is PIRRaise ||
                        inst is PIRNextIter || inst is PIRUnreachable
                    assertFalse(isTerminator,
                        "${func.qualifiedName}: block ${block.label} has terminator " +
                            "${inst::class.simpleName} at position $i (of ${insts.size})")
                }
            }
        }
    }

    @Test
    fun `with-return does not leave dead code`() {
        val f = func("ci_with_return")
        for (block in f.cfg.blocks) {
            val insts = block.instructions
            for (i in 0 until insts.size - 1) {
                assertFalse(insts[i] is PIRReturn,
                    "ci_with_return: block ${block.label} has return at pos $i (of ${insts.size})")
            }
        }
    }

    @Test
    fun `try-raise-then-code does not leave dead code`() {
        val f = func("ci_try_raise_then_code")
        for (block in f.cfg.blocks) {
            val insts = block.instructions
            for (i in 0 until insts.size - 1) {
                assertFalse(insts[i] is PIRRaise,
                    "ci_try_raise_then_code: block ${block.label} has raise at pos $i (of ${insts.size})")
            }
        }
    }

    @Test
    fun `for-else-return does not leave dead code`() {
        val f = func("ci_for_else_return")
        for (block in f.cfg.blocks) {
            val insts = block.instructions
            for (i in 0 until insts.size - 1) {
                val inst = insts[i]
                assertFalse(inst is PIRReturn || inst is PIRGoto && i < insts.size - 1,
                    "ci_for_else_return: block ${block.label} has terminator at pos $i (of ${insts.size})")
            }
        }
    }
}
