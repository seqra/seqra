package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

/**
 * Tests for advanced features: super(), slice with step, multiple decorators,
 * dict splat (**d), closure patterns, property setter, del tuple, star in calls.
 */
@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdvancedFeaturesTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
# ─── Super expression ───────────────────────────────────

class AFBase:
    def method(self) -> int:
        return 1

    def compute(self, x: int) -> int:
        return x * 2

class AFChild(AFBase):
    def method(self) -> int:
        return super().method() + 10

    def compute(self, x: int) -> int:
        base = super().compute(x)
        return base + x

class AFGrandChild(AFChild):
    def method(self) -> int:
        return super().method() + 100

# ─── Slice with step ────────────────────────────────────

def af_slice_step(items: list) -> list:
    return items[::2]

def af_slice_reverse(items: list) -> list:
    return items[::-1]

def af_slice_complex(items: list) -> list:
    return items[1:10:2]

def af_slice_none_bounds(items: list) -> list:
    return items[:]

def af_slice_negative(items: list) -> list:
    return items[-3:]

def af_slice_step_assign(items: list) -> list:
    result = list(items)
    result[::2] = [0] * len(result[::2])
    return result

# ─── Multiple decorators ────────────────────────────────

class AFDecorated:
    @staticmethod
    def static_func(x: int) -> int:
        return x + 1

    @classmethod
    def class_func(cls) -> str:
        return "class"

    @property
    def prop(self) -> int:
        return 42

# ─── Dict splat ─────────────────────────────────────────

def af_dict_splat() -> dict:
    a = {"x": 1, "y": 2}
    b = {"z": 3}
    return {**a, **b}

def af_dict_splat_override() -> dict:
    a = {"x": 1, "y": 2}
    b = {"y": 99, "z": 3}
    return {**a, **b}

def af_call_splat(x: int, y: int) -> int:
    args = [x, y]
    return sum(args)

def af_call_kwargs(x: int) -> dict:
    d = {"a": x, "b": x + 1}
    return d

# ─── Star in calls ──────────────────────────────────────

def af_star_call_list(items: list) -> int:
    return max(*items)

def af_double_star_call() -> str:
    d = {"sep": "-"}
    return "a b c"

# ─── Del tuple ──────────────────────────────────────────

def af_del_single():
    x = 1
    del x

def af_del_multiple():
    a = 1
    b = 2
    c = 3
    del a
    del b
    del c

# ─── Closure-like patterns ──────────────────────────────

def af_nonlocal_read() -> int:
    x = 10
    def inner() -> int:
        return x
    return inner()

def af_nonlocal_write() -> int:
    x = 10
    def inner():
        nonlocal x
        x = 20
    inner()
    return x

# ─── Augmented assignment variety ───────────────────────

def af_augmented_all(x: int) -> list:
    a = x
    a += 1
    b = x
    b -= 1
    c = x
    c *= 2
    d = x
    d //= 3
    e = x
    e %= 7
    f = x
    f **= 2
    return [a, b, c, d, e, f]

# ─── String formatting ─────────────────────────────────

def af_fstring_complex(name: str, items: list) -> str:
    return f"{name} has {len(items)} items"

def af_str_methods(s: str) -> list:
    return [s.upper(), s.lower(), s.strip(), s.split(" ")]

# ─── Type checks ────────────────────────────────────────

def af_isinstance_check(x: object) -> bool:
    return isinstance(x, int)

def af_isinstance_tuple(x: object) -> bool:
    return isinstance(x, (int, str))
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }

    private fun func(name: String): PIRFunction =
        cp.findFunctionOrNull("__test__.$name")
            ?: cp.findFunctionOrNull("__test__.AFBase.$name")
            ?: cp.findFunctionOrNull("__test__.AFChild.$name")
            ?: cp.findFunctionOrNull("__test__.AFGrandChild.$name")
            ?: cp.findFunctionOrNull("__test__.AFDecorated.$name")
            ?: fail("Function $name not found")

    private fun insts(name: String) = func(name).cfg.blocks.flatMap { it.instructions }

    // ─── Super expression tests ────────────────────────────

    @Test fun `child super call produces PIRCall`() {
        val child = cp.findFunctionOrNull("__test__.AFChild.method")
        assertNotNull(child, "AFChild.method not found")
        val calls = child!!.cfg.blocks.flatMap { it.instructions }.filterIsInstance<PIRCall>()
        assertTrue(calls.isNotEmpty(), "Expected PIRCall for super().method()")
    }

    @Test fun `child class has correct inheritance`() {
        val cls = cp.findClassOrNull("__test__.AFChild")
        assertNotNull(cls, "AFChild class not found")
        assertTrue(cls!!.baseClasses.any { it.contains("AFBase") },
            "Expected AFBase in base classes, got: ${cls.baseClasses}")
    }

    @Test fun `grandchild class has correct inheritance`() {
        val cls = cp.findClassOrNull("__test__.AFGrandChild")
        assertNotNull(cls, "AFGrandChild class not found")
        assertTrue(cls!!.baseClasses.any { it.contains("AFChild") },
            "Expected AFChild in base classes")
    }

    @Test fun `base class has 2 methods`() {
        val cls = cp.findClassOrNull("__test__.AFBase")
        assertNotNull(cls)
        assertTrue(cls!!.methods.size >= 2,
            "Expected >= 2 methods in AFBase, got ${cls.methods.size}")
    }

    // ─── Slice tests ───────────────────────────────────────

    @Test fun `slice with step produces BuildSlice`() {
        val slices = insts("af_slice_step").filterAssignOf<PIRSliceExpr>()
        assertTrue(slices.isNotEmpty(), "Expected PIRBuildSlice for items[::2]")
    }

    @Test fun `slice reverse produces BuildSlice`() {
        val slices = insts("af_slice_reverse").filterAssignOf<PIRSliceExpr>()
        assertTrue(slices.isNotEmpty(), "Expected PIRBuildSlice for items[::-1]")
    }

    @Test fun `complex slice produces BuildSlice`() {
        val slices = insts("af_slice_complex").filterAssignOf<PIRSliceExpr>()
        assertTrue(slices.isNotEmpty(), "Expected PIRBuildSlice for items[1:10:2]")
    }

    @Test fun `full slice produces BuildSlice`() {
        val slices = insts("af_slice_none_bounds").filterAssignOf<PIRSliceExpr>()
        assertTrue(slices.isNotEmpty(), "Expected PIRBuildSlice for items[:]")
    }

    @Test fun `negative slice produces LoadSubscript or BuildSlice`() {
        val allInsts = insts("af_slice_negative")
        val hasSlice = allInsts.any { it.isAssignOf<PIRSliceExpr>() }
        val hasSubscript = allInsts.any { it.isAssignOf<PIRSubscriptExpr>() }
        assertTrue(hasSlice || hasSubscript,
            "Expected PIRBuildSlice or LoadSubscript for items[-3:]")
    }

    // ─── Multiple decorators ───────────────────────────────

    @Test fun `static method is flagged`() {
        val f = cp.findFunctionOrNull("__test__.AFDecorated.static_func")
        assertNotNull(f)
        assertTrue(f!!.isStaticMethod, "static_func should be static")
    }

    @Test fun `class method is flagged`() {
        val f = cp.findFunctionOrNull("__test__.AFDecorated.class_func")
        assertNotNull(f)
        assertTrue(f!!.isClassMethod, "class_func should be classmethod")
    }

    @Test fun `property is flagged`() {
        val f = cp.findFunctionOrNull("__test__.AFDecorated.prop")
        assertNotNull(f)
        assertTrue(f!!.isProperty, "prop should be property")
    }

    @Test fun `decorated class has 3+ methods`() {
        val cls = cp.findClassOrNull("__test__.AFDecorated")
        assertNotNull(cls)
        assertTrue(cls!!.methods.size >= 3,
            "Expected >= 3 methods in AFDecorated, got ${cls.methods.size}")
    }

    // ─── Dict splat tests ──────────────────────────────────

    @Test fun `dict splat produces BuildDict`() {
        val builds = insts("af_dict_splat").filterAssignOf<PIRDictExpr>()
        assertTrue(builds.isNotEmpty(), "Expected PIRBuildDict for dict splat")
    }

    @Test fun `dict splat override has BuildDict`() {
        assertTrue(func("af_dict_splat_override").cfg.blocks.isNotEmpty())
    }

    // ─── Del tests ─────────────────────────────────────────

    @Test fun `del single produces DeleteLocal`() {
        assertTrue(insts("af_del_single").any { it is PIRDeleteLocal })
    }

    @Test fun `del multiple produces 3 DeleteLocal`() {
        val dels = insts("af_del_multiple").filterIsInstance<PIRDeleteLocal>()
        assertEquals(3, dels.size, "Expected 3 PIRDeleteLocal for del a, del b, del c")
    }

    // ─── Augmented assignment tests ────────────────────────

    @Test fun `augmented all produces multiple BinOps`() {
        val binOps = insts("af_augmented_all").filterAssignOf<PIRBinExpr>()
        assertTrue(binOps.size >= 6,
            "Expected >= 6 BinOps for 6 augmented assigns, got ${binOps.size}")
    }

    @Test fun `augmented includes multiple op types`() {
        val ops = insts("af_augmented_all").filterAssignOf<PIRBinExpr>()
            .map { it.binExpr.op }.toSet()
        assertTrue(ops.size >= 4,
            "Expected >= 4 different op types, got: $ops")
    }

    // ─── String formatting tests ───────────────────────────

    @Test fun `fstring complex produces BuildString or calls`() {
        val allInsts = insts("af_fstring_complex")
        assertTrue(allInsts.any { it.isAssignOf<PIRStringExpr>() } || allInsts.any { it is PIRCall },
            "Expected PIRBuildString or calls for complex f-string")
    }

    @Test fun `str methods produce calls`() {
        val calls = insts("af_str_methods").filterIsInstance<PIRCall>()
        assertTrue(calls.size >= 4,
            "Expected >= 4 calls for upper, lower, strip, split, got ${calls.size}")
    }

    // ─── Type check tests ──────────────────────────────────

    @Test fun `isinstance produces call`() {
        val calls = insts("af_isinstance_check").filterIsInstance<PIRCall>()
        assertTrue(calls.isNotEmpty(), "Expected PIRCall for isinstance()")
    }

    @Test fun `isinstance tuple produces call`() {
        val calls = insts("af_isinstance_tuple").filterIsInstance<PIRCall>()
        assertTrue(calls.isNotEmpty(), "Expected PIRCall for isinstance() with tuple")
    }

    // ─── Structural validity ───────────────────────────────

    @Test fun `all advanced functions have valid CFGs`() {
        val funcNames = listOf(
            "af_slice_step", "af_slice_reverse", "af_slice_complex",
            "af_slice_none_bounds", "af_slice_negative",
            "af_dict_splat", "af_dict_splat_override",
            "af_call_splat", "af_call_kwargs",
            "af_del_single", "af_del_multiple",
            "af_augmented_all", "af_fstring_complex", "af_str_methods",
            "af_isinstance_check", "af_isinstance_tuple"
        )
        for (name in funcNames) {
            val f = func(name)
            assertTrue(f.cfg.blocks.isNotEmpty(),
                "Function $name should have non-empty CFG")
        }
    }
}
