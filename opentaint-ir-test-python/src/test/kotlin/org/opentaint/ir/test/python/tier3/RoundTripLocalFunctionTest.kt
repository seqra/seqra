package org.opentaint.ir.test.python.tier3

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Round-trip tests for local (nested) function definitions.
 *
 * Local functions are lowered to module-level synthetic functions in PIR.
 * The [roundTripWithLambdas] method handles looking up these extracted
 * functions and emitting them alongside the outer function's reconstruction.
 *
 * 29 test cases covering local function patterns: simple calls, closures,
 * nonlocal writes, multiple inner functions, factory patterns, nested closures,
 * conditional definitions, default parameters, and recursive inner functions.
 */
@Tag("tier3")
class RoundTripLocalFunctionTest : RoundTripTestBase() {

    override val allSources = """
def rtlf_simple_call(x: int) -> int:
    def inner(y: int) -> int:
        return y + 1
    return inner(x)

def rtlf_simple_double(x: int) -> int:
    def double(y: int) -> int:
        return y * 2
    return double(x)

def rtlf_simple_call_twice(x: int) -> int:
    def add_one(y: int) -> int:
        return y + 1
    return add_one(add_one(x))

def rtlf_closure_param_add(x: int) -> int:
    def add_x(y: int) -> int:
        return x + y
    return add_x(10)

def rtlf_closure_param_mul(x: int) -> int:
    def mul_x(y: int) -> int:
        return x * y
    return mul_x(5)

def rtlf_closure_param_concat(prefix: str, name: str) -> str:
    def greet(n: str) -> str:
        return prefix + n
    return greet(name)

def rtlf_closure_local_var() -> int:
    y = 5
    def get_y() -> int:
        return y
    return get_y()

def rtlf_closure_local_compound() -> int:
    a = 3
    b = 7
    def add_ab() -> int:
        return a + b
    return add_ab()

def rtlf_nonlocal_counter() -> int:
    n = 0
    def inc() -> int:
        nonlocal n
        n = n + 1
        return n
    inc()
    inc()
    return inc()

def rtlf_nonlocal_accumulator(items: list) -> int:
    total = 0
    def add(x: int) -> None:
        nonlocal total
        total = total + x
    i = 0
    while i < len(items):
        add(items[i])
        i = i + 1
    return total

def rtlf_nonlocal_toggle() -> int:
    state = 0
    def toggle() -> int:
        nonlocal state
        if state == 0:
            state = 1
        else:
            state = 0
        return state
    toggle()
    toggle()
    return toggle()

def rtlf_multiple_inner_fns(x: int) -> int:
    def add(y: int) -> int:
        return x + y
    def mul(y: int) -> int:
        return x * y
    return add(3) + mul(4)

def rtlf_multiple_inner_ops(x: int) -> int:
    def square(y: int) -> int:
        return y * y
    def negate(y: int) -> int:
        return -y
    return square(x) + negate(x)

def rtlf_factory_adder(n: int, x: int) -> int:
    def make_adder(base: int):
        def adder(val: int) -> int:
            return val + base
        return adder
    f = make_adder(n)
    return f(x)

def rtlf_factory_multiplier(n: int, x: int) -> int:
    def make_mul(factor: int):
        def mul(val: int) -> int:
            return val * factor
        return mul
    f = make_mul(n)
    return f(x)

def rtlf_nested_three_levels(x: int, y: int, z: int) -> int:
    def level_b(b_val: int):
        def level_c(c_val: int) -> int:
            return x + b_val + c_val
        return level_c
    f = level_b(y)
    return f(z)

def rtlf_nested_three_mul(x: int, y: int, z: int) -> int:
    def level_b(b_val: int):
        def level_c(c_val: int) -> int:
            return x * b_val * c_val
        return level_c
    f = level_b(y)
    return f(z)

def rtlf_inner_multi_args() -> int:
    def calc(a: int, b: int, c: int) -> int:
        return a * b + c
    return calc(2, 3, 4)

def rtlf_inner_weighted(a: int, b: int) -> int:
    def weighted_sum(x: int, y: int, w1: int, w2: int) -> int:
        return x * w1 + y * w2
    return weighted_sum(a, b, 2, 3)

def rtlf_conditional_def(flag: int) -> int:
    if flag > 0:
        def f() -> int:
            return 1
    else:
        def f() -> int:
            return 2
    return f()

def rtlf_conditional_def_str(flag: int) -> str:
    if flag > 0:
        def msg() -> str:
            return "positive"
    else:
        def msg() -> str:
            return "non-positive"
    return msg()

def rtlf_inner_default_param() -> int:
    def f(x: int, n: int = 10) -> int:
        return x + n
    return f(5)

def rtlf_inner_default_override() -> int:
    def f(x: int, n: int = 10) -> int:
        return x + n
    return f(5, 20)

def rtlf_inner_multi_defaults() -> int:
    def f(a: int, b: int = 2, c: int = 3) -> int:
        return a + b * c
    return f(1)

def rtlf_accumulator_sum() -> int:
    total = 0
    def add(x: int) -> int:
        nonlocal total
        total = total + x
        return total
    add(1)
    add(2)
    add(3)
    return add(4)

def rtlf_inner_calls_inner(x: int) -> int:
    def double(y: int) -> int:
        return y * 2
    def double_plus_one(y: int) -> int:
        return double(y) + 1
    return double_plus_one(x)

def rtlf_recursive_inner(n: int) -> int:
    def factorial(x: int) -> int:
        if x <= 1:
            return 1
        return x * factorial(x - 1)
    return factorial(n)

def rtlf_inner_in_loop(n: int) -> int:
    def square(x: int) -> int:
        return x * x
    total = 0
    i = 1
    while i <= n:
        total = total + square(i)
        i = i + 1
    return total

def rtlf_wrapper_pattern(x: int) -> int:
    def compute(val_: int) -> int:
        return val_ * val_
    def with_offset(val_: int) -> int:
        return compute(val_) + 10
    return with_offset(x)
    """.trimIndent()

    // ─── Simple local function calls ─────────────────────────

    @Test fun `local fn - simple call`() = roundTripWithLambdas("rtlf_simple_call",
        posArgs(listOf(5), listOf(0), listOf(-3)))

    @Test fun `local fn - simple double`() = roundTripWithLambdas("rtlf_simple_double",
        posArgs(listOf(4), listOf(0), listOf(-2)))

    @Test fun `local fn - call twice (chained)`() = roundTripWithLambdas("rtlf_simple_call_twice",
        posArgs(listOf(5), listOf(0), listOf(-1)))

    // ─── Closure over parameter ──────────────────────────────

    @Test fun `local fn - closure over param add`() = roundTripWithLambdas("rtlf_closure_param_add",
        posArgs(listOf(3), listOf(0), listOf(-5)))

    @Test fun `local fn - closure over param mul`() = roundTripWithLambdas("rtlf_closure_param_mul",
        posArgs(listOf(4), listOf(0), listOf(-2)))

    @Test fun `local fn - closure over param concat`() = roundTripWithLambdas("rtlf_closure_param_concat",
        posArgs(listOf("Hello, ", "World"), listOf("Hi ", "there"), listOf("", "test")))

    // ─── Closure over local variable ─────────────────────────

    @Test fun `local fn - closure over local var`() = roundTripWithLambdas("rtlf_closure_local_var",
        posArgs(emptyList()))

    @Test fun `local fn - closure over local compound`() = roundTripWithLambdas("rtlf_closure_local_compound",
        posArgs(emptyList()))

    // ─── Nonlocal writes ─────────────────────────────────────

    @Test fun `local fn - nonlocal counter`() = roundTripWithLambdas("rtlf_nonlocal_counter",
        posArgs(emptyList()))

    @Test fun `local fn - nonlocal accumulator`() = roundTripWithLambdas("rtlf_nonlocal_accumulator",
        posArgs(listOf(listOf(1, 2, 3, 4)), listOf(emptyList<Int>()), listOf(listOf(10))))

    @Test fun `local fn - nonlocal toggle`() = roundTripWithLambdas("rtlf_nonlocal_toggle",
        posArgs(emptyList()))

    // ─── Multiple inner functions ────────────────────────────

    @Test fun `local fn - multiple inner fns`() = roundTripWithLambdas("rtlf_multiple_inner_fns",
        posArgs(listOf(2), listOf(0), listOf(5)))

    @Test fun `local fn - multiple inner ops`() = roundTripWithLambdas("rtlf_multiple_inner_ops",
        posArgs(listOf(3), listOf(0), listOf(-4)))

    // ─── Factory pattern ─────────────────────────────────────

    @Test fun `local fn - factory adder`() = roundTripWithLambdas("rtlf_factory_adder",
        posArgs(listOf(5, 10), listOf(0, 0), listOf(-3, 7)))

    @Test fun `local fn - factory multiplier`() = roundTripWithLambdas("rtlf_factory_multiplier",
        posArgs(listOf(3, 4), listOf(0, 5), listOf(-2, 3)))

    // ─── Nested closures — 3 levels ─────────────────────────

    @Test fun `local fn - nested three levels add`() = roundTripWithLambdas("rtlf_nested_three_levels",
        posArgs(listOf(1, 2, 3), listOf(0, 0, 0), listOf(10, 20, 30)))

    @Test fun `local fn - nested three levels mul`() = roundTripWithLambdas("rtlf_nested_three_mul",
        posArgs(listOf(2, 3, 4), listOf(1, 1, 1), listOf(0, 5, 3)))

    // ─── Local function with multiple args ───────────────────

    @Test fun `local fn - inner multi args`() = roundTripWithLambdas("rtlf_inner_multi_args",
        posArgs(emptyList()))

    @Test fun `local fn - inner weighted`() = roundTripWithLambdas("rtlf_inner_weighted",
        posArgs(listOf(3, 4), listOf(0, 0), listOf(1, 1)))

    // ─── Conditional local function ──────────────────────────

    @Test fun `local fn - conditional def int`() = roundTripWithLambdas("rtlf_conditional_def",
        posArgs(listOf(1), listOf(-1), listOf(0)))

    @Test fun `local fn - conditional def str`() = roundTripWithLambdas("rtlf_conditional_def_str",
        posArgs(listOf(1), listOf(-1), listOf(0)))

    // ─── Local function with default params ──────────────────

    @Test fun `local fn - default param`() = roundTripWithLambdas("rtlf_inner_default_param",
        posArgs(emptyList()))

    @Test fun `local fn - default param override`() = roundTripWithLambdas("rtlf_inner_default_override",
        posArgs(emptyList()))

    @Test fun `local fn - multi defaults`() = roundTripWithLambdas("rtlf_inner_multi_defaults",
        posArgs(emptyList()))

    // ─── Accumulator pattern ─────────────────────────────────

    @Test fun `local fn - accumulator sum`() = roundTripWithLambdas("rtlf_accumulator_sum",
        posArgs(emptyList()))

    // ─── Inner calls inner ───────────────────────────────────

    @Test fun `local fn - inner calls inner`() = roundTripWithLambdas("rtlf_inner_calls_inner",
        posArgs(listOf(5), listOf(0), listOf(-3)))

    // ─── Recursive inner function ────────────────────────────

    @Test fun `local fn - recursive inner factorial`() = roundTripWithLambdas("rtlf_recursive_inner",
        posArgs(listOf(5), listOf(1), listOf(0)))

    // ─── Inner function in loop ──────────────────────────────

    @Test fun `local fn - inner in loop`() = roundTripWithLambdas("rtlf_inner_in_loop",
        posArgs(listOf(4), listOf(1), listOf(0)))

    // ─── Wrapper / decorator-like pattern ────────────────────

    @Test fun `local fn - wrapper pattern`() = roundTripWithLambdas("rtlf_wrapper_pattern",
        posArgs(listOf(3), listOf(0), listOf(-2)))
}
