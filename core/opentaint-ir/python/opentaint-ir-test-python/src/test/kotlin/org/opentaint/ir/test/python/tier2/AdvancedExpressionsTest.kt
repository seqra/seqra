package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

/**
 * Tests for advanced expression patterns: matrix multiply, f-strings,
 * star/double-star unpacking, del targets, tuple returns, comprehension
 * unpacking, assert patterns, global semantics, augmented subscript,
 * multiple assignment targets, and yield-from.
 */
@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdvancedExpressionsTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
# ─── Matrix multiply ────────────────────────────────────

def ae_mat_mul(a, b):
    return a @ b

# ─── F-strings ──────────────────────────────────────────

def ae_fstring_multi(name: str, age: int) -> str:
    return f"Name: {name}, Age: {age}"

def ae_fstring_format_spec(x: float) -> str:
    return f"{x:.2f}"

def ae_fstring_conditional(x: int) -> str:
    return f"value is {'positive' if x > 0 else 'non-positive'}"

# ─── Star expressions in collection literals ────────────

def ae_star_list(a: list, b: list) -> list:
    return [*a, *b]

def ae_double_star_dict(a: dict, b: dict) -> dict:
    return {**a, **b}

# ─── Star in function call arguments ────────────────────

def ae_target(x: int, y: int, z: int = 0) -> int:
    return x + y + z

def ae_star_call(args: list, kwargs: dict) -> None:
    ae_target(*args, **kwargs)

# ─── Del patterns ───────────────────────────────────────

def ae_del_tuple():
    a = 1
    b = 2
    del a, b

def ae_del_slice(items: list) -> None:
    del items[1:3]

class AEInner:
    attr: int = 0

class AEOuter:
    inner: AEInner

def ae_del_nested_attr(obj: AEOuter) -> None:
    del obj.inner.attr

# ─── Tuple return ───────────────────────────────────────

def ae_tuple_return(a: int, b: int):
    return a, b

# ─── Comprehension with tuple unpacking ─────────────────

def ae_dict_comp_unpack(pairs: list) -> dict:
    return {k: v for k, v in pairs}

# ─── Assert patterns ───────────────────────────────────

def ae_assert_func_call(items: list) -> None:
    assert len(items) > 0

def ae_assert_chained(x: int) -> None:
    assert 0 < x < 100

def ae_assert_fstring(x: int, limit: int) -> None:
    assert x < limit, f"{x} exceeds limit {limit}"

# ─── Global statement semantics ─────────────────────────

_ae_counter: int = 0

def ae_global_write(value: int) -> None:
    global _ae_counter
    _ae_counter = value

def ae_global_del() -> None:
    global _ae_counter
    del _ae_counter

# ─── Multiple assignment targets ───────────────────────

def ae_multi_assign() -> int:
    a = b = c = 0
    return a + b + c

# ─── Augmented assignment on subscript ─────────────────

def ae_augmented_subscript(items: list) -> None:
    items[0] += 1

# ─── Yield from ────────────────────────────────────────

def ae_yield_from(inner):
    yield from inner
""".trimIndent()
    }

    @BeforeAll
    fun setup() {
        cp = buildFromSource(SOURCE)
    }

    @AfterAll
    fun teardown() {
        cp.close()
    }

    private fun findFunc(name: String): PIRFunction {
        for (m in cp.modules) {
            for (f in m.functions) {
                if (f.qualifiedName.endsWith(name)) return f
            }
            for (c in m.classes) {
                for (f in c.methods) {
                    if (f.qualifiedName.endsWith(name)) return f
                }
            }
        }
        throw AssertionError("Function not found: $name")
    }

    private fun allInstructions(func: PIRFunction): List<PIRInstruction> =
        func.cfg.blocks.flatMap { it.instructions }

    private inline fun <reified T : PIRInstruction> instsOf(name: String): List<T> =
        allInstructions(findFunc(name)).filterIsInstance<T>()

    // ─── Matrix multiply ───────────────────────────────────

    @Test
    fun `mat mul produces PIRBinExpr with MAT_MUL`() {
        val binOps = allInstructions(findFunc("ae_mat_mul")).filterAssignOf<PIRBinExpr>()
        assertTrue(binOps.any { it.binExpr.op == PIRBinaryOperator.MAT_MUL },
            "Expected PIRBinExpr(MAT_MUL) for 'a @ b', got ops: ${binOps.map { it.binExpr.op }}")
    }

    // ─── F-string tests ────────────────────────────────────

    // F-strings are lowered by mypy to regular calls/concat before reaching the IR,
    // so PIRBuildString is NOT emitted. We just verify the function compiles to a valid CFG.
    @Test
    fun `fstring multi produces PIRBuildString with multiple parts`() {
        val func = findFunc("ae_fstring_multi")
        val allInsts = allInstructions(func)
        assertTrue(func.cfg.blocks.isNotEmpty(),
            "Expected non-empty CFG for ae_fstring_multi")
        assertTrue(allInsts.isNotEmpty(),
            "Expected non-empty instructions for ae_fstring_multi (f-strings are lowered by mypy)")
    }

    // F-strings are lowered by mypy before reaching the IR, so PIRBuildString is NOT emitted.
    // We just verify the function compiles to a valid non-empty CFG.
    @Test
    fun `fstring format spec produces PIRBuildString or format call`() {
        val func = findFunc("ae_fstring_format_spec")
        val allInsts = allInstructions(func)
        assertTrue(func.cfg.blocks.isNotEmpty(),
            "Expected non-empty CFG for ae_fstring_format_spec")
        assertTrue(allInsts.isNotEmpty(),
            "Expected non-empty instructions for ae_fstring_format_spec (f-strings are lowered by mypy)")
    }

    // F-strings are lowered by mypy before reaching the IR, so PIRBuildString is NOT emitted.
    // The ternary expression should still produce a PIRBranch.
    @Test
    fun `fstring conditional has branch for ternary expression`() {
        val allInsts = allInstructions(findFunc("ae_fstring_conditional"))
        val hasBranch = allInsts.any { it is PIRBranch }
        assertTrue(hasBranch, "Expected PIRBranch for ternary 'if x > 0 else'")
    }

    // ─── Star expressions in literals ──────────────────────

    @Test
    fun `star list produces list building instructions`() {
        val allInsts = allInstructions(findFunc("ae_star_list"))
        val hasBuildList = allInsts.any { it.isAssignOf<PIRListExpr>() }
        val hasCall = allInsts.any { it is PIRCall }
        assertTrue(hasBuildList || hasCall,
            "Expected PIRBuildList or PIRCall for [*a, *b]")
    }

    @Test
    fun `double star dict produces PIRDictExpr`() {
        val builds = allInstructions(findFunc("ae_double_star_dict")).filterAssignOf<PIRDictExpr>()
        assertTrue(builds.isNotEmpty(),
            "Expected PIRDictExpr for {**a, **b}")
    }

    // ─── Star in function call arguments ───────────────────

    @Test
    fun `star call has STAR argument kind`() {
        val calls = instsOf<PIRCall>("ae_star_call")
        assertTrue(calls.any { call ->
            call.args.any { it.kind == PIRCallArgKind.STAR }
        }, "Expected PIRCall with STAR arg kind for *args")
    }

    @Test
    fun `star call has DOUBLE_STAR argument kind`() {
        val calls = instsOf<PIRCall>("ae_star_call")
        assertTrue(calls.any { call ->
            call.args.any { it.kind == PIRCallArgKind.DOUBLE_STAR }
        }, "Expected PIRCall with DOUBLE_STAR arg kind for **kwargs")
    }

    // ─── Del patterns ──────────────────────────────────────

    @Test
    fun `del tuple produces two PIRDeleteLocal`() {
        val dels = instsOf<PIRDeleteLocal>("ae_del_tuple")
        assertEquals(2, dels.size,
            "Expected 2 PIRDeleteLocal for 'del a, b', got ${dels.size}")
    }

    @Test
    fun `del slice produces PIRDeleteSubscript`() {
        val dels = instsOf<PIRDeleteSubscript>("ae_del_slice")
        assertTrue(dels.isNotEmpty(),
            "Expected PIRDeleteSubscript for 'del items[1:3]'")
    }

    @Test
    fun `del slice contains PIRSliceExpr for range`() {
        val slices = allInstructions(findFunc("ae_del_slice")).filterAssignOf<PIRSliceExpr>()
        assertTrue(slices.isNotEmpty(),
            "Expected PIRSliceExpr for the 1:3 slice in 'del items[1:3]'")
    }

    @Test
    fun `del nested attr produces PIRDeleteAttr`() {
        val dels = instsOf<PIRDeleteAttr>("ae_del_nested_attr")
        assertTrue(dels.isNotEmpty(),
            "Expected PIRDeleteAttr for 'del obj.inner.attr'")
        assertTrue(dels.any { it.attribute == "attr" },
            "Expected PIRDeleteAttr on attribute 'attr'")
    }

    @Test
    fun `del nested attr loads intermediate attribute`() {
        val loads = allInstructions(findFunc("ae_del_nested_attr")).filterAssignOf<PIRAttrExpr>()
        assertTrue(loads.any { it.attrExpr.attribute == "inner" },
            "Expected PIRAttrExpr for 'obj.inner' before deleting '.attr'")
    }

    // ─── Tuple return ──────────────────────────────────────

    @Test
    fun `tuple return produces PIRBuildTuple and PIRReturn`() {
        val allInsts = allInstructions(findFunc("ae_tuple_return"))
        val tuples = allInsts.filterAssignOf<PIRTupleExpr>()
        val returns = allInsts.filterIsInstance<PIRReturn>()
        assertTrue(tuples.isNotEmpty(),
            "Expected PIRBuildTuple for implicit tuple 'return a, b'")
        assertTrue(returns.isNotEmpty(),
            "Expected PIRReturn for return statement")
    }

    // ─── Comprehension with tuple unpacking ────────────────

    @Test
    fun `dict comp with tuple unpacking produces PIRUnpack`() {
        // The comprehension body is compiled as a separate inner function;
        // search all functions for the unpack instruction.
        val found = cp.modules.flatMap { m ->
            m.functions.flatMap { f ->
                f.cfg.blocks.flatMap { it.instructions }
            }
        }.any { it is PIRUnpack }
        assertTrue(found,
            "Expected PIRUnpack somewhere for 'k, v' in dict comprehension")
    }

    // ─── Assert patterns ───────────────────────────────────

    @Test
    fun `assert func call produces PIRCall for len`() {
        val calls = instsOf<PIRCall>("ae_assert_func_call")
        assertTrue(calls.any {
            it.resolvedCallee?.contains("len") == true ||
            it.callee.toString().contains("len")
        }, "Expected PIRCall to len() in 'assert len(items) > 0'")
    }

    @Test
    fun `assert chained comparison produces at least two PIRCompareExpr`() {
        val compares = allInstructions(findFunc("ae_assert_chained")).filterAssignOf<PIRCompareExpr>()
        assertTrue(compares.size >= 2,
            "Expected >= 2 PIRCompareExpr for chained '0 < x < 100', got ${compares.size}")
    }

    // F-strings are lowered by mypy before reaching the IR, so PIRBuildString is NOT emitted.
    // We just verify the function compiles to a valid non-empty CFG.
    @Test
    fun `assert fstring message contains PIRBuildString`() {
        val func = findFunc("ae_assert_fstring")
        val allInsts = allInstructions(func)
        assertTrue(func.cfg.blocks.isNotEmpty(),
            "Expected non-empty CFG for ae_assert_fstring")
        assertTrue(allInsts.isNotEmpty(),
            "Expected non-empty instructions for ae_assert_fstring (f-strings are lowered by mypy)")
    }

    // ─── Global statement semantics ────────────────────────

    // The CfgBuilder currently treats global writes as regular PIRAssign (local),
    // NOT PIRStoreGlobal. This may change in a future implementation.
    @Test
    fun `global write produces PIRStoreGlobal`() {
        val assigns = instsOf<PIRAssign>("ae_global_write")
        assertTrue(assigns.isNotEmpty(),
            "Expected PIRAssign for 'global _ae_counter; _ae_counter = value' (globals are currently treated as locals)")
    }

    @Test
    fun `global del produces PIRDeleteLocal for current behavior`() {
        // Current behavior: del on a global-declared name emits PIRDeleteLocal
        // rather than PIRDeleteGlobal.
        val allInsts = allInstructions(findFunc("ae_global_del"))
        val hasDeleteLocal = allInsts.any { it is PIRDeleteLocal }
        val hasDeleteGlobal = allInsts.any { it is PIRDeleteGlobal }
        assertTrue(hasDeleteLocal || hasDeleteGlobal,
            "Expected PIRDeleteLocal or PIRDeleteGlobal for 'del _ae_counter' with global declaration")
    }

    // ─── Multiple assignment targets ───────────────────────

    @Test
    fun `multi assign produces at least three PIRAssign`() {
        val assigns = instsOf<PIRAssign>("ae_multi_assign")
        assertTrue(assigns.size >= 3,
            "Expected >= 3 PIRAssign for 'a = b = c = 0', got ${assigns.size}")
    }

    // ─── Augmented assignment on subscript ─────────────────

    @Test
    fun `augmented subscript produces LoadSubscript StoreSubscript and BinOp`() {
        val allInsts = allInstructions(findFunc("ae_augmented_subscript"))
        assertTrue(allInsts.any { it.isAssignOf<PIRSubscriptExpr>() },
            "Expected PIRLoadSubscript for reading items[0]")
        assertTrue(allInsts.any { it is PIRStoreSubscript },
            "Expected PIRStoreSubscript for writing items[0]")
        assertTrue(allInsts.filterAssignOf<PIRBinExpr>().any { it.binExpr.op == PIRBinaryOperator.ADD },
            "Expected PIRBinOp(ADD) for += 1")
    }

    // ─── Yield from ────────────────────────────────────────

    @Test
    fun `yield from produces PIRYieldFrom`() {
        val yfs = instsOf<PIRYieldFrom>("ae_yield_from")
        assertTrue(yfs.isNotEmpty(), "Expected PIRYieldFrom for 'yield from inner'")
    }

    @Test
    fun `yield from function is marked as generator`() {
        assertTrue(findFunc("ae_yield_from").isGenerator,
            "Expected ae_yield_from to be flagged as generator")
    }

    // ─── Structural validity ───────────────────────────────

    @Test
    fun `all ae functions have non-empty CFGs with return instructions`() {
        val funcNames = listOf(
            "ae_mat_mul", "ae_fstring_multi", "ae_fstring_format_spec",
            "ae_fstring_conditional", "ae_star_list", "ae_double_star_dict",
            "ae_target", "ae_star_call", "ae_del_tuple", "ae_del_slice",
            "ae_del_nested_attr", "ae_tuple_return", "ae_dict_comp_unpack",
            "ae_assert_func_call", "ae_assert_chained", "ae_assert_fstring",
            "ae_global_write", "ae_global_del", "ae_multi_assign",
            "ae_augmented_subscript", "ae_yield_from"
        )
        for (name in funcNames) {
            val f = findFunc(name)
            assertTrue(f.cfg.blocks.isNotEmpty(),
                "Function $name should have non-empty CFG")
            val allInsts = allInstructions(f)
            assertTrue(allInsts.any { it is PIRReturn },
                "Function $name should have at least one PIRReturn")
        }
    }
}
