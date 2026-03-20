package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

@Tag("tier2")
class BasicInstructionsTest : PIRTestBase() {

    @Test
    fun `assignment produces PIRAssign`() {
        val cp = buildFromSource("""
            def f() -> int:
                x = 42
                return x
        """)
        cp.use {
            val func = it.findFunctionOrNull("__test__.f")
            Assertions.assertNotNull(func, "Function __test__.f not found")
            val assigns = func!!.cfg.blocks.flatMap { b -> b.instructions }
                .filterIsInstance<PIRAssign>()
            Assertions.assertTrue(assigns.isNotEmpty(), "Expected PIRAssign instruction")
        }
    }

    @Test
    fun `binary operations produce PIRBinOp`() {
        val cp = buildFromSource("""
            def add(a: int, b: int) -> int:
                return a + b
        """)
        cp.use {
            val func = it.findFunctionOrNull("__test__.add")
            Assertions.assertNotNull(func, "Function __test__.add not found")
            val binOps = func!!.cfg.blocks.flatMap { b -> b.instructions }
                .filterIsInstance<PIRBinOp>()
            Assertions.assertTrue(binOps.any { op -> op.op == PIRBinaryOperator.ADD },
                "Expected ADD binary op, found: $binOps")
        }
    }

    @Test
    fun `function call produces PIRCall`() {
        val cp = buildFromSource("""
            def f() -> int:
                return len([1, 2, 3])
        """)
        cp.use {
            val func = it.findFunctionOrNull("__test__.f")
            Assertions.assertNotNull(func, "Function __test__.f not found")
            val calls = func!!.cfg.blocks.flatMap { b -> b.instructions }
                .filterIsInstance<PIRCall>()
            Assertions.assertTrue(calls.isNotEmpty(), "Expected PIRCall instruction")
        }
    }

    @Test
    fun `if-else produces branch`() {
        val cp = buildFromSource("""
            def f(x: int) -> str:
                if x > 0:
                    return "pos"
                else:
                    return "neg"
        """)
        cp.use {
            val func = it.findFunctionOrNull("__test__.f")
            Assertions.assertNotNull(func, "Function __test__.f not found")
            val branches = func!!.cfg.blocks.flatMap { b -> b.instructions }
                .filterIsInstance<PIRBranch>()
            Assertions.assertTrue(branches.isNotEmpty(), "Expected PIRBranch instruction")
        }
    }

    @Test
    fun `for loop produces GetIter and NextIter`() {
        val cp = buildFromSource("""
            def f(items: list) -> int:
                total = 0
                for x in items:
                    total += x
                return total
        """)
        cp.use {
            val func = it.findFunctionOrNull("__test__.f")
            Assertions.assertNotNull(func, "Function __test__.f not found")
            val insts = func!!.cfg.blocks.flatMap { b -> b.instructions }
            Assertions.assertTrue(insts.any { i -> i is PIRGetIter }, "Expected PIRGetIter")
            Assertions.assertTrue(insts.any { i -> i is PIRNextIter }, "Expected PIRNextIter")
        }
    }

    @Test
    fun `while loop produces proper CFG`() {
        val cp = buildFromSource("""
            def f(n: int) -> int:
                i = 0
                while i < n:
                    i += 1
                return i
        """)
        cp.use {
            val func = it.findFunctionOrNull("__test__.f")
            Assertions.assertNotNull(func, "Function __test__.f not found")
            // Should have multiple blocks (entry, header, body, exit)
            Assertions.assertTrue(func!!.cfg.blocks.size >= 3,
                "Expected at least 3 blocks for while loop, got ${func.cfg.blocks.size}")
        }
    }

    @Test
    fun `return produces PIRReturn`() {
        val cp = buildFromSource("""
            def f() -> int:
                return 42
        """)
        cp.use {
            val func = it.findFunctionOrNull("__test__.f")
            Assertions.assertNotNull(func, "Function __test__.f not found")
            val returns = func!!.cfg.blocks.flatMap { b -> b.instructions }
                .filterIsInstance<PIRReturn>()
            Assertions.assertTrue(returns.isNotEmpty(), "Expected PIRReturn instruction")
        }
    }

    @Test
    fun `list literal produces PIRBuildList`() {
        val cp = buildFromSource("""
            def f():
                x = [1, 2, 3]
                return x
        """)
        cp.use {
            val func = it.findFunctionOrNull("__test__.f")
            Assertions.assertNotNull(func, "Function __test__.f not found")
            val builds = func!!.cfg.blocks.flatMap { b -> b.instructions }
                .filterIsInstance<PIRBuildList>()
            Assertions.assertTrue(builds.isNotEmpty(), "Expected PIRBuildList instruction")
        }
    }
}
