package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

/**
 * Tests for for-loop with tuple unpacking targets.
 * `for a, b in pairs:` should produce PIRNextIter + PIRUnpack.
 * Also tests starred unpacking in for loops and dict.items() patterns.
 */
@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForTupleUnpackTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
def ftu_pair_unpack(pairs: list) -> int:
    total = 0
    for a, b in pairs:
        total += a + b
    return total

def ftu_triple_unpack(triples: list) -> int:
    total = 0
    for a, b, c in triples:
        total += a + b + c
    return total

def ftu_nested_unpack(items: list) -> list:
    result = []
    for (a, b), c in items:
        result.append(a + b + c)
    return result

def ftu_dict_items(d: dict) -> list:
    result = []
    for k, v in d.items():
        result.append(k)
    return result

def ftu_enumerate(items: list) -> int:
    total = 0
    for i, x in enumerate(items):
        total += i * x
    return total

def ftu_zip(a: list, b: list) -> list:
    result = []
    for x, y in zip(a, b):
        result.append(x + y)
    return result

def ftu_single_target(items: list) -> int:
    total = 0
    for x in items:
        total += x
    return total

def ftu_starred_unpack(items: list) -> list:
    result = []
    for first, *rest in items:
        result.append(first)
    return result

def ftu_four_targets(items: list) -> int:
    total = 0
    for a, b, c, d in items:
        total += a + b + c + d
    return total

def ftu_ignore_second(pairs: list) -> list:
    result = []
    for a, _ in pairs:
        result.append(a)
    return result
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }

    private fun func(name: String): PIRFunction =
        cp.findFunctionOrNull("__test__.$name")
            ?: fail("Function $name not found")

    private fun insts(name: String) = func(name).cfg.blocks.flatMap { it.instructions }

    // ─── Pair unpacking ────────────────────────────────────

    @Test fun `pair unpack produces GetIter and NextIter`() {
        assertTrue(insts("ftu_pair_unpack").any { it is PIRGetIter })
        assertTrue(insts("ftu_pair_unpack").any { it is PIRNextIter })
    }

    @Test fun `pair unpack produces Unpack`() {
        val unpacks = insts("ftu_pair_unpack").filterIsInstance<PIRUnpack>()
        assertTrue(unpacks.isNotEmpty(),
            "Expected PIRUnpack for 'for a, b in pairs'")
    }

    @Test fun `pair unpack has binop for addition`() {
        val binOps = insts("ftu_pair_unpack").filterIsInstance<PIRBinOp>()
        assertTrue(binOps.isNotEmpty(), "Expected PIRBinOp for a + b")
    }

    // ─── Triple unpacking ──────────────────────────────────

    @Test fun `triple unpack produces Unpack`() {
        val unpacks = insts("ftu_triple_unpack").filterIsInstance<PIRUnpack>()
        assertTrue(unpacks.isNotEmpty(),
            "Expected PIRUnpack for 'for a, b, c in triples'")
    }

    // ─── Nested unpacking ──────────────────────────────────

    @Test fun `nested unpack has Unpack`() {
        val unpacks = insts("ftu_nested_unpack").filterIsInstance<PIRUnpack>()
        assertTrue(unpacks.isNotEmpty(),
            "Expected PIRUnpack for nested '(a,b), c'")
    }

    // ─── Dict items pattern ────────────────────────────────

    @Test fun `dict items has GetIter and Unpack`() {
        val allInsts = insts("ftu_dict_items")
        assertTrue(allInsts.any { it is PIRGetIter })
        assertTrue(allInsts.any { it is PIRUnpack },
            "Expected PIRUnpack for 'for k, v in d.items()'")
    }

    @Test fun `dict items has method call`() {
        val calls = insts("ftu_dict_items").filterIsInstance<PIRCall>()
        assertTrue(calls.isNotEmpty(), "Expected PIRCall for d.items()")
    }

    // ─── Enumerate pattern ─────────────────────────────────

    @Test fun `enumerate has call and Unpack`() {
        val allInsts = insts("ftu_enumerate")
        assertTrue(allInsts.any { it is PIRCall }, "Expected call to enumerate()")
        assertTrue(allInsts.any { it is PIRUnpack }, "Expected Unpack for i, x")
    }

    // ─── Zip pattern ───────────────────────────────────────

    @Test fun `zip has call and Unpack`() {
        val allInsts = insts("ftu_zip")
        assertTrue(allInsts.any { it is PIRCall }, "Expected call to zip()")
        assertTrue(allInsts.any { it is PIRUnpack }, "Expected Unpack for x, y")
    }

    // ─── Single target (baseline) ──────────────────────────

    @Test fun `single target has no Unpack`() {
        val unpacks = insts("ftu_single_target").filterIsInstance<PIRUnpack>()
        assertTrue(unpacks.isEmpty(),
            "Expected no PIRUnpack for single target 'for x in items'")
    }

    @Test fun `single target has GetIter and NextIter`() {
        assertTrue(insts("ftu_single_target").any { it is PIRGetIter })
        assertTrue(insts("ftu_single_target").any { it is PIRNextIter })
    }

    // ─── Four targets ──────────────────────────────────────

    @Test fun `four target unpack produces Unpack`() {
        val unpacks = insts("ftu_four_targets").filterIsInstance<PIRUnpack>()
        assertTrue(unpacks.isNotEmpty(),
            "Expected PIRUnpack for 'for a, b, c, d in items'")
    }

    // ─── Ignore with underscore ────────────────────────────

    @Test fun `underscore target still produces Unpack`() {
        val unpacks = insts("ftu_ignore_second").filterIsInstance<PIRUnpack>()
        assertTrue(unpacks.isNotEmpty(),
            "Expected PIRUnpack even when using _ as target")
    }

    // ─── Structural validity ───────────────────────────────

    @Test fun `all for-unpack functions have valid CFGs`() {
        val funcNames = listOf(
            "ftu_pair_unpack", "ftu_triple_unpack", "ftu_nested_unpack",
            "ftu_dict_items", "ftu_enumerate", "ftu_zip",
            "ftu_single_target", "ftu_starred_unpack",
            "ftu_four_targets", "ftu_ignore_second"
        )
        for (name in funcNames) {
            val f = func(name)
            assertTrue(f.cfg.blocks.isNotEmpty(),
                "Function $name should have non-empty CFG")
        }
    }
}
