package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

/**
 * Tests for nested functions, closures, nonlocal, classes inside functions.
 *
 * Current IR behavior: nested defs inside function bodies are NOT extracted
 * as separate PIRFunction objects. They are skipped during CFG building.
 * Only top-level functions and class methods are extracted.
 * Lambdas inside functions ARE extracted as synthetic lambda functions.
 *
 * These tests verify:
 * 1. Outer functions containing nested defs have valid CFGs
 * 2. Nested defs don't crash the pipeline
 * 3. The outer function's calls to inner functions are present
 * 4. closureVars property on PIRFunction is accessible
 * 5. nonlocal/global keywords don't cause errors
 * 6. Classes inside functions don't crash
 * 7. Lambdas inside functions are still extracted
 */
@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClosureNestedFunctionTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
def cnf_simple_nested(x):
    def inner(y):
        return y + 1
    return inner(x)

def cnf_closure_read():
    value = 10
    def reader():
        return value
    return reader()

def cnf_nonlocal_write():
    count = 0
    def increment():
        nonlocal count
        count = count + 1
    increment()
    increment()
    return count

def cnf_nonlocal_multiple():
    a = 1
    b = 2
    def swap():
        nonlocal a, b
        tmp = a
        a = b
        b = tmp
    swap()
    return a + b

def cnf_class_inside_func(val_arg):
    class LocalClass:
        def __init__(self, v):
            self.v = v
        def get(self):
            return self.v
    obj = LocalClass(val_arg)
    return obj.get()

def cnf_triple_nested(x):
    def middle(y):
        def deepest(z):
            return z * 2
        return deepest(y) + 1
    return middle(x)

def cnf_lambda_in_func(x):
    fn = lambda a: a + x
    return fn(10)

def cnf_lambda_in_nested(x):
    def wrapper(n):
        fn = lambda a: a + n
        return fn(10)
    return wrapper(x)

def cnf_closure_over_loop():
    funcs = []
    for i in range(5):
        def make(val_i=i):
            return val_i
        funcs.append(make)
    return funcs

def cnf_returns_inner():
    def factory():
        return 42
    return factory

def cnf_two_inner(x):
    def add_one(n):
        return n + 1
    def double(n):
        return n * 2
    return add_one(x) + double(x)

def cnf_inner_calls_inner(x):
    def helper(n):
        return n + 10
    def caller(n):
        return helper(n) * 2
    return caller(x)

def cnf_global_keyword():
    global MY_GLOBAL
    MY_GLOBAL = 100
    return MY_GLOBAL

MY_GLOBAL = 0

def cnf_deeply_nested():
    def level1():
        def level2():
            def level3():
                return 99
            return level3()
        return level2()
    return level1()

def cnf_decorator_inside():
    def my_decorator(func):
        def wrapper(*args):
            return func(*args)
        return wrapper
    @my_decorator
    def decorated(x):
        return x + 1
    return decorated(5)

def cnf_conditional_nested(flag):
    if flag:
        def helper():
            return 1
    else:
        def helper():
            return 2
    return helper()
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

    private fun findFuncOrNull(name: String): PIRFunction? {
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
        return null
    }

    private fun allInstructions(func: PIRFunction): List<PIRInstruction> =
        func.cfg.blocks.flatMap { it.instructions }

    // ─── 1. Outer functions with nested defs have valid CFGs ───

    @Test
    fun `simple nested - outer has valid CFG`() {
        val f = findFunc("cnf_simple_nested")
        assertTrue(f.cfg.blocks.isNotEmpty(), "cnf_simple_nested should have non-empty CFG")
    }

    @Test
    fun `simple nested - outer has call instruction`() {
        val calls = allInstructions(findFunc("cnf_simple_nested")).filterIsInstance<PIRCall>()
        assertTrue(calls.isNotEmpty(), "cnf_simple_nested should have PIRCall for inner(x)")
    }

    @Test
    fun `simple nested - outer has return instruction`() {
        val rets = allInstructions(findFunc("cnf_simple_nested")).filterIsInstance<PIRReturn>()
        assertTrue(rets.isNotEmpty(), "cnf_simple_nested should have PIRReturn")
    }

    // ─── 2. Closure read doesn't crash ────────────────────────

    @Test
    fun `closure read - outer has valid CFG`() {
        val f = findFunc("cnf_closure_read")
        assertTrue(f.cfg.blocks.isNotEmpty())
    }

    @Test
    fun `closure read - outer has call and return`() {
        val insts = allInstructions(findFunc("cnf_closure_read"))
        assertTrue(insts.any { it is PIRCall }, "should call reader()")
        assertTrue(insts.any { it is PIRReturn }, "should return")
    }

    // ─── 3. Nonlocal keyword doesn't crash ────────────────────

    @Test
    fun `nonlocal write - outer has valid CFG`() {
        val f = findFunc("cnf_nonlocal_write")
        assertTrue(f.cfg.blocks.isNotEmpty())
    }

    @Test
    fun `nonlocal write - outer has return`() {
        val rets = allInstructions(findFunc("cnf_nonlocal_write")).filterIsInstance<PIRReturn>()
        assertTrue(rets.isNotEmpty())
    }

    @Test
    fun `nonlocal write - outer has calls to increment`() {
        val calls = allInstructions(findFunc("cnf_nonlocal_write")).filterIsInstance<PIRCall>()
        assertTrue(calls.isNotEmpty(), "should call increment()")
    }

    @Test
    fun `nonlocal multiple - outer has valid CFG`() {
        val f = findFunc("cnf_nonlocal_multiple")
        assertTrue(f.cfg.blocks.isNotEmpty())
    }

    // ─── 4. Class inside function doesn't crash ───────────────

    @Test
    fun `class inside func - outer has valid CFG`() {
        val f = findFunc("cnf_class_inside_func")
        assertTrue(f.cfg.blocks.isNotEmpty())
    }

    @Test
    fun `class inside func - outer has calls`() {
        val calls = allInstructions(findFunc("cnf_class_inside_func")).filterIsInstance<PIRCall>()
        assertTrue(calls.isNotEmpty(), "should call LocalClass() and obj.get()")
    }

    @Test
    fun `class inside func - outer has return`() {
        val rets = allInstructions(findFunc("cnf_class_inside_func")).filterIsInstance<PIRReturn>()
        assertTrue(rets.isNotEmpty())
    }

    // ─── 5. Triple nesting doesn't crash ──────────────────────

    @Test
    fun `triple nested - outer has valid CFG`() {
        val f = findFunc("cnf_triple_nested")
        assertTrue(f.cfg.blocks.isNotEmpty())
    }

    @Test
    fun `triple nested - outer has call and return`() {
        val insts = allInstructions(findFunc("cnf_triple_nested"))
        assertTrue(insts.any { it is PIRCall })
        assertTrue(insts.any { it is PIRReturn })
    }

    // ─── 6. Lambda inside function IS extracted ───────────────

    @Test
    fun `lambda in func - lambda is extracted as synthetic function`() {
        val lambdas = cp.modules.flatMap { it.functions }
            .filter { it.qualifiedName.contains("<lambda>") }
        assertTrue(lambdas.isNotEmpty(), "Lambdas should be extracted at module level")
    }

    @Test
    fun `lambda in func - outer has valid CFG`() {
        val f = findFunc("cnf_lambda_in_func")
        assertTrue(f.cfg.blocks.isNotEmpty())
    }

    @Test
    fun `lambda in func - outer has call and return`() {
        val insts = allInstructions(findFunc("cnf_lambda_in_func"))
        assertTrue(insts.any { it is PIRCall })
        assertTrue(insts.any { it is PIRReturn })
    }

    // ─── 7. Lambda in nested function ─────────────────────────

    @Test
    fun `lambda in nested - outer has valid CFG`() {
        val f = findFunc("cnf_lambda_in_nested")
        assertTrue(f.cfg.blocks.isNotEmpty())
    }

    // ─── 8. Closure over loop variable ────────────────────────

    @Test
    fun `closure over loop - outer has valid CFG`() {
        val f = findFunc("cnf_closure_over_loop")
        assertTrue(f.cfg.blocks.isNotEmpty())
    }

    @Test
    fun `closure over loop - outer has iteration`() {
        val insts = allInstructions(findFunc("cnf_closure_over_loop"))
        val hasIter = insts.any { it is PIRGetIter || it is PIRNextIter }
        assertTrue(hasIter, "should have for-loop iteration")
    }

    // ─── 9. Returns inner function ────────────────────────────

    @Test
    fun `returns inner - outer has valid CFG with return`() {
        val f = findFunc("cnf_returns_inner")
        assertTrue(f.cfg.blocks.isNotEmpty())
        assertTrue(allInstructions(f).any { it is PIRReturn })
    }

    // ─── 10. Two inner functions ──────────────────────────────

    @Test
    fun `two inner - outer has valid CFG`() {
        val f = findFunc("cnf_two_inner")
        assertTrue(f.cfg.blocks.isNotEmpty())
    }

    @Test
    fun `two inner - outer has multiple calls`() {
        val calls = allInstructions(findFunc("cnf_two_inner")).filterIsInstance<PIRCall>()
        assertTrue(calls.size >= 2, "should call add_one() and double()")
    }

    // ─── 11. Inner calls inner ────────────────────────────────

    @Test
    fun `inner calls inner - outer has valid CFG`() {
        val f = findFunc("cnf_inner_calls_inner")
        assertTrue(f.cfg.blocks.isNotEmpty())
    }

    // ─── 12. Global keyword ───────────────────────────────────

    @Test
    fun `global keyword - function has valid CFG`() {
        val f = findFunc("cnf_global_keyword")
        assertTrue(f.cfg.blocks.isNotEmpty())
    }

    @Test
    fun `global keyword - function has assign and return`() {
        val insts = allInstructions(findFunc("cnf_global_keyword"))
        assertTrue(insts.any { it is PIRReturn })
        // Global write may be PIRAssign (to local) or PIRStoreGlobal
        val hasAssign = insts.any { it is PIRAssign }
        assertTrue(hasAssign, "should assign to MY_GLOBAL")
    }

    // ─── 13. Deeply nested ────────────────────────────────────

    @Test
    fun `deeply nested - outer has valid CFG`() {
        val f = findFunc("cnf_deeply_nested")
        assertTrue(f.cfg.blocks.isNotEmpty())
    }

    @Test
    fun `deeply nested - outer has call and return`() {
        val insts = allInstructions(findFunc("cnf_deeply_nested"))
        assertTrue(insts.any { it is PIRCall })
        assertTrue(insts.any { it is PIRReturn })
    }

    // ─── 14. Decorator inside function ────────────────────────

    @Test
    fun `decorator inside - outer has valid CFG`() {
        val f = findFunc("cnf_decorator_inside")
        assertTrue(f.cfg.blocks.isNotEmpty())
    }

    @Test
    fun `decorator inside - outer returns`() {
        assertTrue(allInstructions(findFunc("cnf_decorator_inside")).any { it is PIRReturn })
    }

    // ─── 15. Conditional nested def ───────────────────────────

    @Test
    fun `conditional nested - outer has valid CFG`() {
        val f = findFunc("cnf_conditional_nested")
        assertTrue(f.cfg.blocks.isNotEmpty())
    }

    @Test
    fun `conditional nested - outer has branches`() {
        val insts = allInstructions(findFunc("cnf_conditional_nested"))
        assertTrue(insts.any { it is PIRBranch }, "should have branch for if/else")
    }

    // ─── 16. closureVars property accessible ──────────────────

    @Test
    fun `closureVars property is accessible on all functions`() {
        for (m in cp.modules) {
            for (f in m.functions) {
                assertNotNull(f.closureVars, "${f.qualifiedName} closureVars should be non-null")
            }
        }
    }

    // ─── 17. All outer functions have valid CFGs ──────────────

    @Test
    fun `all outer functions have valid non-empty CFGs`() {
        val names = listOf(
            "cnf_simple_nested", "cnf_closure_read", "cnf_nonlocal_write",
            "cnf_nonlocal_multiple", "cnf_class_inside_func", "cnf_triple_nested",
            "cnf_lambda_in_func", "cnf_lambda_in_nested", "cnf_closure_over_loop",
            "cnf_returns_inner", "cnf_two_inner", "cnf_inner_calls_inner",
            "cnf_global_keyword", "cnf_deeply_nested", "cnf_decorator_inside",
            "cnf_conditional_nested"
        )
        for (name in names) {
            val f = findFunc(name)
            assertTrue(f.cfg.blocks.isNotEmpty(), "$name should have non-empty CFG")
            assertTrue(allInstructions(f).any { it is PIRReturn },
                "$name should have at least one PIRReturn")
        }
    }
}
