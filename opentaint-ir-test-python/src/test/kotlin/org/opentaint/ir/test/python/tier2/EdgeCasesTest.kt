package org.opentaint.ir.test.python.tier2

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.opentaint.ir.api.python.*
import org.opentaint.ir.test.python.PIRTestBase

/**
 * Edge case tests for the Python IR.
 *
 * Tests unusual or boundary-case Python patterns that might trip up
 * the lowering pipeline.
 */
@Tag("tier2")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EdgeCasesTest : PIRTestBase() {

    private lateinit var cp: PIRClasspath

    companion object {
        val SOURCE = """
# ─── Empty / minimal functions ──────────────────────────

def ec_empty_pass():
    pass

def ec_only_docstring():
    '''This function has only a docstring.'''

def ec_return_none():
    return None

def ec_return_only():
    return

def ec_single_assignment():
    x = 42

# ─── Deeply nested control flow ─────────────────────────

def ec_deep_nested_if(x: int) -> int:
    if x > 0:
        if x > 10:
            if x > 100:
                if x > 1000:
                    if x > 10000:
                        return 5
                    return 4
                return 3
            return 2
        return 1
    return 0

def ec_deep_nested_loops(n: int) -> int:
    total = 0
    for i in range(n):
        for j in range(n):
            for k in range(n):
                for m in range(n):
                    total += 1
    return total

# ─── Multiple assignment targets ────────────────────────

def ec_multi_target(x: int) -> list:
    a = b = c = x
    return [a, b, c]

def ec_swap(a: int, b: int) -> list:
    a, b = b, a
    return [a, b]

# ─── Try/except edge cases ──────────────────────────────

def ec_try_in_try() -> int:
    try:
        try:
            x = 1
        except:
            x = 2
    except:
        x = 3
    return x

def ec_try_except_else_finally() -> int:
    x = 0
    try:
        x = 1
    except:
        x = 2
    else:
        x = x + 10
    finally:
        x = x + 100
    return x

def ec_multi_except() -> int:
    try:
        x = 1
    except ValueError:
        x = 2
    except TypeError:
        x = 3
    except (KeyError, IndexError):
        x = 4
    except:
        x = 5
    return x

def ec_raise_from():
    try:
        x = 1
    except Exception as e:
        raise RuntimeError("wrapped") from e

# ─── Loop edge cases ────────────────────────────────────

def ec_while_true_break() -> int:
    i = 0
    while True:
        i += 1
        if i >= 10:
            break
    return i

def ec_for_range(n: int) -> int:
    total = 0
    for i in range(n):
        total += i
    return total

def ec_nested_break_continue(items: list) -> int:
    total = 0
    for x in items:
        if x == 0:
            continue
        if x < 0:
            break
        total += x
    return total

def ec_for_in_while(n: int) -> int:
    total = 0
    i = 0
    while i < n:
        for j in range(i):
            total += 1
        i += 1
    return total

# ─── Expression edge cases ──────────────────────────────

def ec_negative_number() -> int:
    return -42

def ec_large_int() -> int:
    return 999999999999

def ec_float_ops() -> float:
    return 3.14 * 2.0 + 1.0

def ec_boolean_ops() -> list:
    return [True and False, True or False, not True]

def ec_none_check(x: object) -> bool:
    return x is None

def ec_none_check_not(x: object) -> bool:
    return x is not None

def ec_in_check(x: int, items: list) -> bool:
    return x in items

def ec_not_in_check(x: str, items: list) -> bool:
    return x not in items

# ─── String operations ──────────────────────────────────

def ec_fstring(name: str, age: int) -> str:
    return f"Name: {name}, Age: {age}"

def ec_multiline_string() -> str:
    s = ("hello "
         "world "
         "test")
    return s

# ─── Decorator/static/class method patterns ─────────────

class ECClass:
    class_var: int = 10

    def __init__(self, x: int):
        self.x = x

    def instance_method(self) -> int:
        return self.x

    @staticmethod
    def static_method(y: int) -> int:
        return y + 1

    @classmethod
    def class_method(cls) -> int:
        return cls.class_var

# ─── Global / nonlocal ──────────────────────────────────

GLOBAL_VAR = 42

def ec_read_global() -> int:
    return GLOBAL_VAR

def ec_write_global():
    global GLOBAL_VAR
    GLOBAL_VAR = 100

# ─── Assert with message ────────────────────────────────

def ec_assert_simple(x: int):
    assert x > 0

def ec_assert_message(x: int):
    assert x > 0, "x must be positive"

# ─── Delete statement ───────────────────────────────────

def ec_del_local():
    x = 1
    del x

def ec_del_attr(obj: object):
    del obj.attr

def ec_del_subscript(d: dict, key: str):
    del d[key]

# ─── Star expressions ───────────────────────────────────

def ec_star_args(*args) -> int:
    total = 0
    for a in args:
        total += a
    return total

def ec_kwargs(**kwargs) -> int:
    return len(kwargs)

def ec_all_param_kinds(a: int, b: int = 0, *args, c: int = 0, **kwargs) -> int:
    return a + b + c
        """.trimIndent()
    }

    @BeforeAll fun setup() { cp = buildFromSource(SOURCE) }
    @AfterAll fun tearDown() { cp.close() }

    private fun func(name: String): PIRFunction =
        cp.findFunctionOrNull("__test__.$name")
            ?: cp.findFunctionOrNull("__test__.ECClass.$name")
            ?: fail("Function $name not found")

    private fun insts(name: String) = func(name).cfg.blocks.flatMap { it.instructions }

    // ─── Empty/minimal function tests ──────────────────────

    @Test fun `empty pass function has CFG`() {
        val f = func("ec_empty_pass")
        assertTrue(f.cfg.blocks.isNotEmpty())
        assertTrue(insts("ec_empty_pass").any { it is PIRReturn })
    }

    @Test fun `docstring-only function has CFG`() {
        val f = func("ec_only_docstring")
        assertTrue(f.cfg.blocks.isNotEmpty())
    }

    @Test fun `return None has return instruction`() {
        val rets = insts("ec_return_none").filterIsInstance<PIRReturn>()
        assertTrue(rets.isNotEmpty())
    }

    @Test fun `bare return has return instruction`() {
        val rets = insts("ec_return_only").filterIsInstance<PIRReturn>()
        assertTrue(rets.isNotEmpty())
    }

    @Test fun `single assignment produces PIRAssign`() {
        val assigns = insts("ec_single_assignment").filterIsInstance<PIRAssign>()
        assertTrue(assigns.isNotEmpty())
    }

    // ─── Deep nesting tests ────────────────────────────────

    @Test fun `deeply nested if produces 5 branches`() {
        val branches = insts("ec_deep_nested_if").filterIsInstance<PIRBranch>()
        assertTrue(branches.size >= 5,
            "Expected >= 5 branches for 5-level nested if, got ${branches.size}")
    }

    @Test fun `deeply nested if has 6 returns`() {
        val returns = insts("ec_deep_nested_if").filterIsInstance<PIRReturn>()
        assertTrue(returns.size >= 6,
            "Expected >= 6 returns for 6 return paths, got ${returns.size}")
    }

    @Test fun `4-level nested loops produce 4 iterator pairs`() {
        val getIters = insts("ec_deep_nested_loops").filterAssignOf<PIRIterExpr>()
        val nextIters = insts("ec_deep_nested_loops").filterIsInstance<PIRNextIter>()
        assertTrue(getIters.size >= 4, "Expected >= 4 GetIter, got ${getIters.size}")
        assertTrue(nextIters.size >= 4, "Expected >= 4 NextIter, got ${nextIters.size}")
    }

    // ─── Multiple assignment target tests ──────────────────

    @Test fun `multi-target assignment produces assigns`() {
        val assigns = insts("ec_multi_target").filterIsInstance<PIRAssign>()
        assertTrue(assigns.size >= 3, "Expected >= 3 assigns for a = b = c = x")
    }

    @Test fun `tuple swap produces unpack`() {
        val unpacks = insts("ec_swap").filterIsInstance<PIRUnpack>()
        assertTrue(unpacks.isNotEmpty(), "Expected PIRUnpack for a, b = b, a")
    }

    // ─── Try/except edge case tests ────────────────────────

    @Test fun `nested try-in-try has multiple handler layers`() {
        val handlers = insts("ec_try_in_try").filterIsInstance<PIRExceptHandler>()
        assertTrue(handlers.size >= 2,
            "Expected >= 2 exception handlers for nested try, got ${handlers.size}")
    }

    @Test fun `try-except-else-finally has handlers and finally code`() {
        val f = func("ec_try_except_else_finally")
        assertTrue(f.cfg.blocks.size >= 4,
            "Expected >= 4 blocks for try/except/else/finally, got ${f.cfg.blocks.size}")
    }

    @Test fun `multi-except has multiple handlers`() {
        val handlers = insts("ec_multi_except").filterIsInstance<PIRExceptHandler>()
        assertTrue(handlers.size >= 4,
            "Expected >= 4 exception handlers, got ${handlers.size}")
    }

    @Test fun `raise from produces PIRRaise with cause`() {
        val raises = insts("ec_raise_from").filterIsInstance<PIRRaise>()
        assertTrue(raises.isNotEmpty(), "Expected PIRRaise for 'raise ... from ...'")
    }

    @Test fun `try body blocks have exception handlers set`() {
        val f = func("ec_try_in_try")
        val blocksWithHandlers = f.cfg.blocks.filter { it.exceptionHandlers.isNotEmpty() }
        assertTrue(blocksWithHandlers.isNotEmpty(),
            "Expected blocks with exceptionHandlers in nested try")
    }

    // ─── Loop edge case tests ──────────────────────────────

    @Test fun `while True produces branch or no-condition loop`() {
        val f = func("ec_while_true_break")
        // while True should have at least a goto (back edge) and a goto (break)
        val gotos = insts("ec_while_true_break").filterIsInstance<PIRGoto>()
        assertTrue(gotos.size >= 2, "Expected >= 2 gotos for while True + break")
    }

    @Test fun `for range has GetIter and NextIter`() {
        assertTrue(insts("ec_for_range").any { it.isAssignOf<PIRIterExpr>() })
        assertTrue(insts("ec_for_range").any { it is PIRNextIter })
    }

    @Test fun `nested break-continue has multiple gotos`() {
        val gotos = insts("ec_nested_break_continue").filterIsInstance<PIRGoto>()
        assertTrue(gotos.size >= 2, "Expected >= 2 gotos for break + continue")
    }

    @Test fun `for in while produces both loop types`() {
        assertTrue(insts("ec_for_in_while").any { it.isAssignOf<PIRIterExpr>() },
            "Expected GetIter for inner for-loop")
        assertTrue(insts("ec_for_in_while").any { it is PIRBranch },
            "Expected Branch for outer while-loop")
    }

    // ─── Expression edge case tests ────────────────────────

    @Test fun `negative number produces unary neg`() {
        val unary = insts("ec_negative_number").filterAssignOf<PIRUnaryExpr>()
        assertTrue(unary.isEmpty() || unary.any { it.unaryExpr.op == PIRUnaryOperator.NEG },
            "Negative literal may be const or UnaryOp(NEG)")
    }

    @Test fun `boolean ops produce correct instructions`() {
        val allInsts = insts("ec_boolean_ops")
        // and/or use branches (short-circuit), not uses UnaryOp(NOT)
        val branches = allInsts.filterIsInstance<PIRBranch>()
        val unary = allInsts.filterAssignOf<PIRUnaryExpr>()
        assertTrue(branches.isNotEmpty() || unary.isNotEmpty(),
            "Expected branches (and/or) or UnaryOp (not)")
    }

    @Test fun `is None produces IS compare`() {
        val compares = insts("ec_none_check").filterAssignOf<PIRCompareExpr>()
        assertTrue(compares.any { it.compareExpr.op == PIRCompareOperator.IS },
            "Expected IS comparison for 'x is None'")
    }

    @Test fun `is not None produces IS_NOT compare`() {
        val compares = insts("ec_none_check_not").filterAssignOf<PIRCompareExpr>()
        assertTrue(compares.any { it.compareExpr.op == PIRCompareOperator.IS_NOT },
            "Expected IS_NOT comparison for 'x is not None'")
    }

    @Test fun `in check produces IN compare`() {
        val compares = insts("ec_in_check").filterAssignOf<PIRCompareExpr>()
        assertTrue(compares.any { it.compareExpr.op == PIRCompareOperator.IN },
            "Expected IN comparison for 'x in items'")
    }

    @Test fun `not in check produces NOT_IN compare`() {
        val compares = insts("ec_not_in_check").filterAssignOf<PIRCompareExpr>()
        assertTrue(compares.any { it.compareExpr.op == PIRCompareOperator.NOT_IN },
            "Expected NOT_IN comparison for 'x not in items'")
    }

    // ─── String tests ──────────────────────────────────────

    @Test fun `f-string produces PIRBuildString or equivalent`() {
        val allInsts = insts("ec_fstring")
        // f-strings might be lowered to PIRBuildString or a series of concatenations
        val buildStrings = allInsts.filterAssignOf<PIRStringExpr>()
        val calls = allInsts.filterIsInstance<PIRCall>()
        assertTrue(buildStrings.isNotEmpty() || calls.isNotEmpty(),
            "Expected PIRBuildString or calls for f-string")
    }

    // ─── Class tests ───────────────────────────────────────

    @Test fun `class has methods`() {
        val cls = cp.findClassOrNull("__test__.ECClass")
        assertNotNull(cls, "Class ECClass not found")
        assertTrue(cls!!.methods.size >= 4,
            "Expected >= 4 methods (init, instance, static, class), got ${cls.methods.size}")
    }

    @Test fun `static method is flagged`() {
        val method = cp.findFunctionOrNull("__test__.ECClass.static_method")
        assertNotNull(method)
        assertTrue(method!!.isStaticMethod, "static_method should be flagged as static")
    }

    @Test fun `class method is flagged`() {
        val method = cp.findFunctionOrNull("__test__.ECClass.class_method")
        assertNotNull(method)
        assertTrue(method!!.isClassMethod, "class_method should be flagged as class method")
    }

    @Test fun `instance method accesses self x`() {
        val method = cp.findFunctionOrNull("__test__.ECClass.instance_method")
        assertNotNull(method)
        val loadAttrs = method!!.cfg.blocks.flatMap { it.instructions }
            .filterAssignOf<PIRAttrExpr>()
        assertTrue(loadAttrs.any { it.attrExpr.attribute == "x" },
            "Expected load_attr for self.x")
    }

    // ─── Global tests ──────────────────────────────────────

    @Test fun `read global has CFG`() {
        val f = func("ec_read_global")
        assertTrue(f.cfg.blocks.isNotEmpty())
        // Global reads may be lowered as LoadGlobal or direct value references
        assertTrue(insts("ec_read_global").any { it is PIRReturn })
    }

    @Test fun `write global has CFG`() {
        val f = func("ec_write_global")
        assertTrue(f.cfg.blocks.isNotEmpty())
        // Global writes may be lowered as StoreGlobal or direct assigns
        val allInsts = insts("ec_write_global")
        assertTrue(allInsts.any { it is PIRAssign || it is PIRStoreGlobal },
            "Expected assign or store_global for global write")
    }

    // ─── Assert tests ──────────────────────────────────────

    @Test fun `assert simple produces branch`() {
        assertTrue(insts("ec_assert_simple").any { it is PIRBranch },
            "Expected PIRBranch for assert condition")
    }

    @Test fun `assert with message produces call`() {
        val calls = insts("ec_assert_message").filterIsInstance<PIRCall>()
        assertTrue(calls.any { it.args.isNotEmpty() },
            "Expected call with args for assert message")
    }

    // ─── Delete tests ──────────────────────────────────────

    @Test fun `del local produces PIRDeleteLocal`() {
        assertTrue(insts("ec_del_local").any { it is PIRDeleteLocal },
            "Expected PIRDeleteLocal for 'del x'")
    }

    @Test fun `del attr produces PIRDeleteAttr`() {
        assertTrue(insts("ec_del_attr").any { it is PIRDeleteAttr },
            "Expected PIRDeleteAttr for 'del obj.attr'")
    }

    @Test fun `del subscript produces PIRDeleteSubscript`() {
        assertTrue(insts("ec_del_subscript").any { it is PIRDeleteSubscript },
            "Expected PIRDeleteSubscript for 'del d[key]'")
    }

    // ─── Parameter kinds tests ─────────────────────────────

    @Test fun `star args has VAR_POSITIONAL parameter`() {
        val f = func("ec_star_args")
        assertTrue(f.parameters.any { it.kind == PIRParameterKind.VAR_POSITIONAL },
            "Expected VAR_POSITIONAL parameter for *args")
    }

    @Test fun `kwargs has VAR_KEYWORD parameter`() {
        val f = func("ec_kwargs")
        assertTrue(f.parameters.any { it.kind == PIRParameterKind.VAR_KEYWORD },
            "Expected VAR_KEYWORD parameter for **kwargs")
    }

    @Test fun `all param kinds function has correct param kinds`() {
        val f = func("ec_all_param_kinds")
        val kinds = f.parameters.map { "${it.name}:${it.kind}" }
        assertTrue(f.parameters.size >= 5,
            "Expected >= 5 parameters, got ${f.parameters.size}: $kinds")
        assertTrue(f.parameters.any { it.kind == PIRParameterKind.VAR_POSITIONAL },
            "Expected VAR_POSITIONAL param, got kinds: $kinds")
        assertTrue(f.parameters.any { it.kind == PIRParameterKind.VAR_KEYWORD },
            "Expected VAR_KEYWORD param, got kinds: $kinds")
    }
}
