package org.opentaint.ir.test.python.tier3

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Round-trip tests for lambda expressions.
 *
 * Lambda expressions are lowered to synthetic `<lambda>$N` functions.
 * The [reconstructWithLambdas] method in [RoundTripTestBase] handles
 * looking up the lambda functions and emitting them as regular `def` blocks.
 *
 * 30 test cases covering lambda usage patterns.
 */
@Tag("tier3")
class RoundTripLambdaTest : RoundTripTestBase() {

    override val allSources = """
def rtlm_lambda_add_one(items: list) -> list:
    f = lambda x: x + 1
    result = []
    for x in items:
        result = result + [f(x)]
    return result

def rtlm_lambda_square(items: list) -> list:
    sq = lambda x: x * x
    result = []
    for x in items:
        result = result + [sq(x)]
    return result

def rtlm_lambda_negate(items: list) -> list:
    neg = lambda x: -x
    result = []
    for x in items:
        result = result + [neg(x)]
    return result

def rtlm_lambda_max_two(a: int, b: int) -> int:
    bigger = lambda x, y: x if x > y else y
    return bigger(a, b)

def rtlm_lambda_min_two(a: int, b: int) -> int:
    smaller = lambda x, y: x if x < y else y
    return smaller(a, b)

def rtlm_lambda_abs(x: int) -> int:
    absolute = lambda n: n if n >= 0 else -n
    return absolute(x)

def rtlm_lambda_identity(x: int) -> int:
    ident = lambda v: v
    return ident(x)

def rtlm_lambda_constant(x: int) -> int:
    always_42 = lambda: 42
    return always_42()

def rtlm_lambda_add(a: int, b: int) -> int:
    add = lambda x, y: x + y
    return add(a, b)

def rtlm_lambda_mul(a: int, b: int) -> int:
    mul = lambda x, y: x * y
    return mul(a, b)

def rtlm_lambda_compose(x: int) -> int:
    double = lambda n: n * 2
    add_one = lambda n: n + 1
    return add_one(double(x))

def rtlm_lambda_apply_twice(x: int) -> int:
    double = lambda n: n * 2
    return double(double(x))

def rtlm_lambda_in_conditional(x: int) -> int:
    if x > 0:
        op = lambda n: n * 2
    else:
        op = lambda n: n * -1
    return op(x)

def rtlm_sorted_basic(items: list) -> list:
    return sorted(items, key=lambda x: -x)

def rtlm_sorted_abs(items: list) -> list:
    return sorted(items, key=lambda x: x if x >= 0 else -x)

def rtlm_map_basic(items: list) -> list:
    return list(map(lambda x: x * 2, items))

def rtlm_filter_basic(items: list) -> list:
    return list(filter(lambda x: x > 0, items))

def rtlm_map_str_len(items: list) -> list:
    return list(map(lambda s: len(s), items))

def rtlm_lambda_three_args(a: int, b: int, c: int) -> int:
    f = lambda x, y, z: x + y + z
    return f(a, b, c)

def rtlm_lambda_nested_call(x: int) -> int:
    f = lambda n: n + 1
    g = lambda n: n * 2
    return f(g(f(x)))

def rtlm_lambda_with_loop(items: list) -> list:
    transform = lambda x: x * 3 + 1
    result = []
    for x in items:
        result = result + [transform(x)]
    return result

def rtlm_lambda_boolean(x: int) -> int:
    is_pos = lambda n: 1 if n > 0 else 0
    return is_pos(x)

def rtlm_lambda_clamp(x: int) -> int:
    clamp = lambda n: 0 if n < 0 else (100 if n > 100 else n)
    return clamp(x)

def rtlm_lambda_string_op(s: str) -> str:
    greet = lambda name: "Hello, " + name + "!"
    return greet(s)

def rtlm_lambda_comparison(a: int, b: int) -> int:
    cmp = lambda x, y: 1 if x > y else (-1 if x < y else 0)
    return cmp(a, b)

def rtlm_map_and_sum(items: list) -> int:
    doubled = list(map(lambda x: x * 2, items))
    total = 0
    for x in doubled:
        total = total + x
    return total

def rtlm_filter_and_count(items: list) -> int:
    positives = list(filter(lambda x: x > 0, items))
    return len(positives)

def rtlm_lambda_multi_use(items: list) -> list:
    double = lambda x: x * 2
    result = []
    for x in items:
        result = result + [double(x)]
    total = 0
    for x in items:
        total = total + double(x)
    result = result + [total]
    return result

def rtlm_sorted_by_mod(items: list) -> list:
    return sorted(items, key=lambda x: x % 5)

def rtlm_min_with_key(items: list) -> int:
    return min(items, key=lambda x: x if x >= 0 else -x)
    """.trimIndent()

    // ─── Tests ───────────────────────────────────────────────

    @Test fun `lambda - add one`() = roundTripWithLambdas("rtlm_lambda_add_one",
        posArgs(listOf(listOf(1, 2, 3)), listOf(emptyList<Int>())))

    @Test fun `lambda - square`() = roundTripWithLambdas("rtlm_lambda_square",
        posArgs(listOf(listOf(1, 2, 3, 4)), listOf(emptyList<Int>())))

    @Test fun `lambda - negate`() = roundTripWithLambdas("rtlm_lambda_negate",
        posArgs(listOf(listOf(1, -2, 3))))

    @Test fun `lambda - max two`() = roundTripWithLambdas("rtlm_lambda_max_two",
        posArgs(listOf(3, 7), listOf(7, 3), listOf(5, 5)))

    @Test fun `lambda - min two`() = roundTripWithLambdas("rtlm_lambda_min_two",
        posArgs(listOf(3, 7), listOf(7, 3), listOf(5, 5)))

    @Test fun `lambda - abs`() = roundTripWithLambdas("rtlm_lambda_abs",
        posArgs(listOf(5), listOf(-3), listOf(0)))

    @Test fun `lambda - identity`() = roundTripWithLambdas("rtlm_lambda_identity",
        posArgs(listOf(42), listOf(0), listOf(-1)))

    @Test fun `lambda - constant`() = roundTripWithLambdas("rtlm_lambda_constant",
        posArgs(listOf(0), listOf(99)))

    @Test fun `lambda - add`() = roundTripWithLambdas("rtlm_lambda_add",
        posArgs(listOf(3, 5), listOf(0, 0), listOf(-1, 1)))

    @Test fun `lambda - mul`() = roundTripWithLambdas("rtlm_lambda_mul",
        posArgs(listOf(3, 4), listOf(0, 5), listOf(-2, 3)))

    @Test fun `lambda - compose`() = roundTripWithLambdas("rtlm_lambda_compose",
        posArgs(listOf(5), listOf(0), listOf(-3)))

    @Test fun `lambda - apply twice`() = roundTripWithLambdas("rtlm_lambda_apply_twice",
        posArgs(listOf(3), listOf(0), listOf(1)))

    @Test fun `lambda - in conditional`() = roundTripWithLambdas("rtlm_lambda_in_conditional",
        posArgs(listOf(5), listOf(-3), listOf(0)))

    @Test fun `lambda - sorted basic`() = roundTripWithLambdas("rtlm_sorted_basic",
        posArgs(listOf(listOf(3, 1, 4, 1, 5))))

    @Test fun `lambda - sorted abs`() = roundTripWithLambdas("rtlm_sorted_abs",
        posArgs(listOf(listOf(-3, 1, -4, 2))))

    @Test fun `lambda - map basic`() = roundTripWithLambdas("rtlm_map_basic",
        posArgs(listOf(listOf(1, 2, 3)), listOf(emptyList<Int>())))

    @Test fun `lambda - filter basic`() = roundTripWithLambdas("rtlm_filter_basic",
        posArgs(listOf(listOf(1, -2, 3, -4, 5)), listOf(emptyList<Int>())))

    @Test fun `lambda - map str len`() = roundTripWithLambdas("rtlm_map_str_len",
        posArgs(listOf(listOf("hi", "hello", "a"))))

    @Test fun `lambda - three args`() = roundTripWithLambdas("rtlm_lambda_three_args",
        posArgs(listOf(1, 2, 3), listOf(0, 0, 0)))

    @Test fun `lambda - nested call`() = roundTripWithLambdas("rtlm_lambda_nested_call",
        posArgs(listOf(0), listOf(5), listOf(-1)))

    @Test fun `lambda - with loop`() = roundTripWithLambdas("rtlm_lambda_with_loop",
        posArgs(listOf(listOf(1, 2, 3)), listOf(emptyList<Int>())))

    @Test fun `lambda - boolean`() = roundTripWithLambdas("rtlm_lambda_boolean",
        posArgs(listOf(5), listOf(-3), listOf(0)))

    @Test fun `lambda - clamp`() = roundTripWithLambdas("rtlm_lambda_clamp",
        posArgs(listOf(50), listOf(-10), listOf(200), listOf(0), listOf(100)))

    @Test fun `lambda - string op`() = roundTripWithLambdas("rtlm_lambda_string_op",
        posArgs(listOf("World"), listOf("Alice"), listOf("")))

    @Test fun `lambda - comparison`() = roundTripWithLambdas("rtlm_lambda_comparison",
        posArgs(listOf(3, 7), listOf(7, 3), listOf(5, 5)))

    @Test fun `lambda - map and sum`() = roundTripWithLambdas("rtlm_map_and_sum",
        posArgs(listOf(listOf(1, 2, 3)), listOf(emptyList<Int>())))

    @Test fun `lambda - filter and count`() = roundTripWithLambdas("rtlm_filter_and_count",
        posArgs(listOf(listOf(1, -2, 3, -4, 5)), listOf(emptyList<Int>())))

    @Test fun `lambda - multi use`() = roundTripWithLambdas("rtlm_lambda_multi_use",
        posArgs(listOf(listOf(1, 2, 3))))

    @Test fun `lambda - sorted by mod`() = roundTripWithLambdas("rtlm_sorted_by_mod",
        posArgs(listOf(listOf(7, 3, 12, 8, 1))))

    @Test fun `lambda - min with key`() = roundTripWithLambdas("rtlm_min_with_key",
        posArgs(listOf(listOf(-3, 1, -1, 2))))
}
