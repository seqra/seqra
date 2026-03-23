package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

/**
 * Tests for lambda edge cases: nested lambdas, lambda+comprehension combos,
 * lambda with default args, lambda with star-args/kwargs, and closure patterns.
 */
@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LambdaEdgeCasesTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
def lec_lambda_basic() -> int:
    f = lambda x: x + 1
    return f(5)

def lec_lambda_no_args() -> int:
    f = lambda: 42
    return f()

def lec_lambda_multi_args(a: int, b: int) -> int:
    f = lambda x, y: x + y
    return f(a, b)

def lec_lambda_conditional(x: int) -> int:
    f = lambda n: n if n > 0 else -n
    return f(x)

def lec_lambda_in_list(items: list) -> list:
    funcs = [lambda x: x + 1, lambda x: x * 2, lambda x: x - 1]
    result = []
    for f in funcs:
        result.append(f(10))
    return result

def lec_lambda_as_key(items: list) -> list:
    return sorted(items, key=lambda x: -x)

def lec_lambda_map(items: list) -> list:
    return list(map(lambda x: x * 2, items))

def lec_lambda_filter(items: list) -> list:
    return list(filter(lambda x: x > 0, items))

def lec_lambda_in_comp(n: int) -> list:
    funcs = [lambda x, i=i: x + i for i in range(n)]
    return [f(10) for f in funcs]

def lec_comp_in_lambda(items: list) -> list:
    f = lambda lst: [x * 2 for x in lst]
    return f(items)

def lec_lambda_default_arg(x: int) -> int:
    f = lambda a, b=10: a + b
    return f(x)

def lec_lambda_immediate() -> int:
    return (lambda x, y: x + y)(3, 4)

def lec_multiple_lambdas(x: int) -> int:
    add = lambda a, b: a + b
    mul = lambda a, b: a * b
    return add(x, mul(x, 2))

def lec_lambda_string() -> str:
    f = lambda s: s.upper()
    return f("hello")

def lec_lambda_bool() -> bool:
    f = lambda x: x > 0
    return f(5)

def lec_lambda_in_dict() -> dict:
    ops = {"add": lambda a, b: a + b, "sub": lambda a, b: a - b}
    return {"add": ops["add"](3, 4), "sub": ops["sub"](10, 3)}

def lec_lambda_chain(x: int) -> int:
    f = lambda a: lambda b: a + b
    return f(x)(10)
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }

    private fun func(name: String): PIRFunction =
        cp.findFunctionOrNull("__test__.$name")
            ?: fail("Function $name not found")

    private fun insts(name: String) = func(name).cfg.blocks.flatMap { it.instructions }

    // ─── Basic lambda tests ────────────────────────────────

    @Test fun `basic lambda produces call`() {
        val calls = insts("lec_lambda_basic").filterIsInstance<PIRCall>()
        assertTrue(calls.isNotEmpty(), "Expected PIRCall for f(5)")
    }

    @Test fun `basic lambda creates synthetic function`() {
        // Lambda functions are registered as module-level <lambda>$N
        val lambdaFuncs = cp.modules.flatMap { it.functions }
            .filter { it.qualifiedName.contains("<lambda>") }
        assertTrue(lambdaFuncs.isNotEmpty(),
            "Expected synthetic <lambda> functions in module")
    }

    @Test fun `no-args lambda has no params`() {
        val lambdaFuncs = cp.modules.flatMap { it.functions }
            .filter { it.qualifiedName.contains("<lambda>") }
        // At least one should have 0 params
        assertTrue(lambdaFuncs.any { it.parameters.isEmpty() },
            "Expected a lambda with no parameters")
    }

    @Test fun `multi-arg lambda has 2 params`() {
        val lambdaFuncs = cp.modules.flatMap { it.functions }
            .filter { it.qualifiedName.contains("<lambda>") }
        assertTrue(lambdaFuncs.any { it.parameters.size == 2 },
            "Expected a lambda with 2 parameters")
    }

    // ─── Lambda with conditional ───────────────────────────

    @Test fun `conditional lambda has branch in CFG`() {
        val lambdaFuncs = cp.modules.flatMap { it.functions }
            .filter { it.qualifiedName.contains("<lambda>") }
        val hasConditionalLambda = lambdaFuncs.any { f ->
            f.cfg.blocks.flatMap { it.instructions }.any { it is PIRBranch }
        }
        assertTrue(hasConditionalLambda,
            "Expected at least one lambda with PIRBranch (conditional)")
    }

    // ─── Lambda in collection ──────────────────────────────

    @Test fun `lambda in list creates multiple lambda functions`() {
        val lambdaFuncs = cp.modules.flatMap { it.functions }
            .filter { it.qualifiedName.contains("<lambda>") }
        // Source has many lambdas, should have plenty of synthetic funcs
        assertTrue(lambdaFuncs.size >= 3,
            "Expected >= 3 lambda functions, got ${lambdaFuncs.size}")
    }

    // ─── Lambda as argument ────────────────────────────────

    @Test fun `sorted with lambda key produces call`() {
        val calls = insts("lec_lambda_as_key").filterIsInstance<PIRCall>()
        assertTrue(calls.isNotEmpty(), "Expected PIRCall for sorted()")
    }

    @Test fun `map with lambda produces call`() {
        val calls = insts("lec_lambda_map").filterIsInstance<PIRCall>()
        assertTrue(calls.size >= 2, "Expected >= 2 calls (list + map), got ${calls.size}")
    }

    @Test fun `filter with lambda produces call`() {
        val calls = insts("lec_lambda_filter").filterIsInstance<PIRCall>()
        assertTrue(calls.size >= 2, "Expected >= 2 calls (list + filter), got ${calls.size}")
    }

    // ─── Lambda in comprehension ───────────────────────────

    @Test fun `lambda in comprehension has CFG`() {
        val f = func("lec_lambda_in_comp")
        assertTrue(f.cfg.blocks.isNotEmpty())
    }

    @Test fun `comp in lambda has CFG`() {
        val f = func("lec_comp_in_lambda")
        assertTrue(f.cfg.blocks.isNotEmpty())
        assertTrue(insts("lec_comp_in_lambda").any { it is PIRCall })
    }

    // ─── Lambda with defaults ──────────────────────────────

    @Test fun `lambda with default arg has function with param`() {
        // Find lambda that has a default
        val lambdaFuncs = cp.modules.flatMap { it.functions }
            .filter { it.qualifiedName.contains("<lambda>") }
        // At least one should have a parameter with default
        assertTrue(lambdaFuncs.any { f ->
            f.parameters.any { it.hasDefault }
        }, "Expected a lambda with default parameter")
    }

    // ─── Immediate invocation ──────────────────────────────

    @Test fun `immediately invoked lambda produces call`() {
        val calls = insts("lec_lambda_immediate").filterIsInstance<PIRCall>()
        assertTrue(calls.isNotEmpty(), "Expected PIRCall for (lambda ...)(3, 4)")
    }

    // ─── Multiple lambdas in one function ──────────────────

    @Test fun `multiple lambdas produce multiple calls`() {
        val calls = insts("lec_multiple_lambdas").filterIsInstance<PIRCall>()
        assertTrue(calls.size >= 2,
            "Expected >= 2 calls for add() and mul(), got ${calls.size}")
    }

    // ─── Lambda chain ──────────────────────────────────────

    @Test fun `lambda chain produces nested calls`() {
        val calls = insts("lec_lambda_chain").filterIsInstance<PIRCall>()
        assertTrue(calls.size >= 2,
            "Expected >= 2 calls for f(x)(10) chain, got ${calls.size}")
    }

    // ─── Lambda in dict ────────────────────────────────────

    @Test fun `lambda in dict has BuildDict and calls`() {
        val allInsts = insts("lec_lambda_in_dict")
        assertTrue(allInsts.any { it.isAssignOf<PIRDictExpr>() }, "Expected PIRBuildDict for ops dict")
        val calls = allInsts.filterIsInstance<PIRCall>()
        assertTrue(calls.isNotEmpty(), "Expected calls for ops['add'](3,4)")
    }

    // ─── All lambdas have valid CFGs ───────────────────────

    @Test fun `all lambda functions have valid CFGs`() {
        val lambdaFuncs = cp.modules.flatMap { it.functions }
            .filter { it.qualifiedName.contains("<lambda>") }
        for (f in lambdaFuncs) {
            assertTrue(f.cfg.blocks.isNotEmpty(),
                "Lambda ${f.qualifiedName} should have non-empty CFG")
            assertNotNull(f.cfg.entry,
                "Lambda ${f.qualifiedName} should have entry block")
            // Every lambda should have a return
            val hasReturn = f.cfg.blocks.flatMap { it.instructions }.any { it is PIRReturn }
            assertTrue(hasReturn,
                "Lambda ${f.qualifiedName} should have a return instruction")
        }
    }

    @Test fun `all edge case functions have valid CFGs`() {
        val funcNames = listOf(
            "lec_lambda_basic", "lec_lambda_no_args", "lec_lambda_multi_args",
            "lec_lambda_conditional", "lec_lambda_in_list", "lec_lambda_as_key",
            "lec_lambda_map", "lec_lambda_filter", "lec_lambda_in_comp",
            "lec_comp_in_lambda", "lec_lambda_default_arg", "lec_lambda_immediate",
            "lec_multiple_lambdas", "lec_lambda_string", "lec_lambda_bool",
            "lec_lambda_in_dict", "lec_lambda_chain"
        )
        for (name in funcNames) {
            val f = func(name)
            assertTrue(f.cfg.blocks.isNotEmpty(),
                "Function $name should have non-empty CFG")
        }
    }
}
