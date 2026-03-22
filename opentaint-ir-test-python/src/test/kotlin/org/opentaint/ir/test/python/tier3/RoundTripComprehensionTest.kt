package org.opentaint.ir.test.python.tier3

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Round-trip tests for comprehensions, conditional expressions, and advanced features.
 * Comprehensions are lowered to explicit loops in the IR; this verifies the lowered
 * form produces the same results as the original comprehension syntax.
 * 40 test cases.
 */
@Tag("tier3")
class RoundTripComprehensionTest : RoundTripTestBase() {

    override val allSources = """
def rtcp_list_comp_double(items: list) -> list:
    result = [x * 2 for x in items]
    return result

def rtcp_list_comp_filter(items: list) -> list:
    result = [x for x in items if x > 0]
    return result

def rtcp_list_comp_transform_filter(items: list) -> list:
    result = [x * x for x in items if x % 2 == 0]
    return result

def rtcp_list_comp_str(items: list) -> list:
    result = [x + "!" for x in items]
    return result

def rtcp_list_comp_nested(matrix: list) -> list:
    result = [x for row in matrix for x in row]
    return result

def rtcp_list_comp_nested_filter(matrix: list) -> list:
    result = [x for row in matrix for x in row if x > 0]
    return result

def rtcp_list_comp_range_like(n: int) -> list:
    items = []
    i = 0
    while i < n:
        items = items + [i]
        i = i + 1
    result = [x * x for x in items]
    return result

def rtcp_list_comp_empty(items: list) -> list:
    result = [x for x in items if x > 1000]
    return result

def rtcp_list_comp_identity(items: list) -> list:
    result = [x for x in items]
    return result

def rtcp_list_comp_add_one(items: list) -> list:
    result = [x + 1 for x in items]
    return result

def rtcp_dict_comp_square(items: list) -> dict:
    result = {x: x * x for x in items}
    return result

def rtcp_dict_comp_filter(items: list) -> dict:
    result = {x: x * 2 for x in items if x > 0}
    return result

def rtcp_dict_comp_from_pairs(keys: list, vals: list) -> dict:
    pairs = []
    i = 0
    while i < len(keys):
        pairs = pairs + [[keys[i], vals[i]]]
        i = i + 1
    result = {p[0]: p[1] for p in pairs}
    return result

def rtcp_set_comp_basic(items: list) -> int:
    result = {x for x in items}
    return len(result)

def rtcp_set_comp_filter(items: list) -> int:
    result = {x for x in items if x > 0}
    return len(result)

def rtcp_set_comp_transform(items: list) -> int:
    result = {x % 3 for x in items}
    return len(result)

def rtcp_gen_expr_sum(items: list) -> int:
    total = sum(x * 2 for x in items)
    return total

def rtcp_gen_expr_any(items: list) -> int:
    if any(x < 0 for x in items):
        return 1
    return 0

def rtcp_gen_expr_all(items: list) -> int:
    if all(x > 0 for x in items):
        return 1
    return 0

def rtcp_cond_expr_simple(x: int) -> str:
    return "pos" if x > 0 else "non-pos"

def rtcp_cond_expr_chain(x: int) -> str:
    return "big" if x > 100 else ("medium" if x > 10 else "small")

def rtcp_cond_expr_in_list(a: int, b: int) -> list:
    m = a if a > b else b
    n = a if a < b else b
    return [m, n]

def rtcp_cond_expr_in_loop(items: list) -> list:
    result = []
    for x in items:
        result = result + [x if x > 0 else -x]
    return result

def rtcp_cond_expr_assign(x: int) -> int:
    bonus = 10 if x > 50 else (5 if x > 25 else 0)
    return x + bonus

def rtcp_comp_with_method(items: list) -> list:
    result = [len(s) for s in items]
    return result

def rtcp_nested_comp_sum(matrix: list) -> int:
    totals = [sum(row) for row in matrix]
    total = 0
    for t in totals:
        total = total + t
    return total

def rtcp_comp_multiple_filters(items: list) -> list:
    result = [x for x in items if x > 0 if x < 100]
    return result

def rtcp_comp_complex_expr(items: list) -> list:
    result = [x * 2 + 1 for x in items if x % 2 == 0]
    return result

def rtcp_enumerate_with_comp(items: list) -> list:
    indices = []
    i = 0
    for x in items:
        if x > 0:
            indices = indices + [i]
        i = i + 1
    return indices

def rtcp_comp_negate(items: list) -> list:
    result = [-x for x in items]
    return result

def rtcp_comp_bool_to_int(items: list) -> list:
    result = [1 if x > 0 else 0 for x in items]
    return result

def rtcp_comp_clamp(items: list) -> list:
    result = []
    for x in items:
        if x < 0:
            result = result + [0]
        elif x > 100:
            result = result + [100]
        else:
            result = result + [x]
    return result

def rtcp_comp_chain_ops(items: list) -> list:
    step1 = [x + 1 for x in items]
    step2 = [x * 2 for x in step1]
    return step2

def rtcp_comp_sum_and_len(items: list) -> list:
    total = 0
    count = 0
    for x in items:
        total = total + x
        count = count + 1
    return [total, count]

def rtcp_comp_with_cond_body(items: list) -> list:
    result = []
    for x in items:
        val = x * 2 if x > 0 else x * -1
        result = result + [val]
    return result

def rtcp_comp_flatten_and_filter(matrix: list) -> list:
    result = [x for row in matrix for x in row if x != 0]
    return result

def rtcp_comp_string_filter(s: str) -> str:
    result = ""
    for c in s:
        n = ord(c)
        if n >= 97:
            if n <= 122:
                result = result + c
    return result

def rtcp_map_and_filter(items: list) -> list:
    doubled = [x * 2 for x in items]
    filtered = [x for x in doubled if x > 5]
    return filtered

def rtcp_comp_index_values(items: list) -> list:
    result = []
    i = 0
    while i < len(items):
        result = result + [[i, items[i]]]
        i = i + 1
    return result
    """.trimIndent()

    // ─── Tests ───────────────────────────────────────────────

    @Test fun `comp - list comp double`() = roundTrip("rtcp_list_comp_double",
        posArgs(listOf(listOf(1, 2, 3)), listOf(emptyList<Int>())))

    @Test fun `comp - list comp filter`() = roundTrip("rtcp_list_comp_filter",
        posArgs(listOf(listOf(1, -2, 3, -4, 5)), listOf(listOf(-1, -2))))

    @Test fun `comp - list comp transform filter`() = roundTrip("rtcp_list_comp_transform_filter",
        posArgs(listOf(listOf(1, 2, 3, 4, 5))))

    @Test fun `comp - list comp str`() = roundTrip("rtcp_list_comp_str",
        posArgs(listOf(listOf("a", "b", "c")), listOf(emptyList<String>())))

    @Test fun `comp - list comp nested`() = roundTrip("rtcp_list_comp_nested",
        posArgs(listOf(listOf(listOf(1, 2), listOf(3, 4)))))

    @Test fun `comp - list comp nested filter`() = roundTrip("rtcp_list_comp_nested_filter",
        posArgs(listOf(listOf(listOf(1, -2), listOf(-3, 4)))))

    @Test fun `comp - list comp range like`() = roundTrip("rtcp_list_comp_range_like",
        posArgs(listOf(0), listOf(3), listOf(5)))

    @Test fun `comp - list comp empty result`() = roundTrip("rtcp_list_comp_empty",
        posArgs(listOf(listOf(1, 2, 3))))

    @Test fun `comp - list comp identity`() = roundTrip("rtcp_list_comp_identity",
        posArgs(listOf(listOf(1, 2, 3)), listOf(emptyList<Int>())))

    @Test fun `comp - list comp add one`() = roundTrip("rtcp_list_comp_add_one",
        posArgs(listOf(listOf(0, 1, 2))))

    @Test fun `comp - dict comp square`() = roundTrip("rtcp_dict_comp_square",
        posArgs(listOf(listOf(1, 2, 3))))

    @Test fun `comp - dict comp filter`() = roundTrip("rtcp_dict_comp_filter",
        posArgs(listOf(listOf(-1, 0, 1, 2))))

    @Test fun `comp - dict comp from pairs`() = roundTrip("rtcp_dict_comp_from_pairs",
        posArgs(listOf(listOf("a", "b"), listOf(1, 2))))

    @Test fun `comp - set comp basic`() = roundTrip("rtcp_set_comp_basic",
        posArgs(listOf(listOf(1, 2, 2, 3, 3, 3))))

    @Test fun `comp - set comp filter`() = roundTrip("rtcp_set_comp_filter",
        posArgs(listOf(listOf(-1, 0, 1, 2, 2))))

    @Test fun `comp - set comp transform`() = roundTrip("rtcp_set_comp_transform",
        posArgs(listOf(listOf(1, 2, 3, 4, 5, 6))))

    @Test fun `comp - gen expr sum`() = roundTrip("rtcp_gen_expr_sum",
        posArgs(listOf(listOf(1, 2, 3)), listOf(emptyList<Int>())))

    @Test fun `comp - gen expr any`() = roundTrip("rtcp_gen_expr_any",
        posArgs(listOf(listOf(1, -1, 3)), listOf(listOf(1, 2, 3))))

    @Test fun `comp - gen expr all`() = roundTrip("rtcp_gen_expr_all",
        posArgs(listOf(listOf(1, 2, 3)), listOf(listOf(1, -1, 3))))

    @Test fun `comp - cond expr simple`() = roundTrip("rtcp_cond_expr_simple",
        posArgs(listOf(5), listOf(-3), listOf(0)))

    @Test fun `comp - cond expr chain`() = roundTrip("rtcp_cond_expr_chain",
        posArgs(listOf(200), listOf(50), listOf(5)))

    @Test fun `comp - cond expr in list`() = roundTrip("rtcp_cond_expr_in_list",
        posArgs(listOf(3, 7), listOf(7, 3), listOf(5, 5)))

    @Test fun `comp - cond expr in loop`() = roundTrip("rtcp_cond_expr_in_loop",
        posArgs(listOf(listOf(1, -2, 3, -4))))

    @Test fun `comp - cond expr assign`() = roundTrip("rtcp_cond_expr_assign",
        posArgs(listOf(60), listOf(30), listOf(10)))

    @Test fun `comp - comp with method`() = roundTrip("rtcp_comp_with_method",
        posArgs(listOf(listOf("hi", "hello", "a"))))

    @Test fun `comp - nested comp sum`() = roundTrip("rtcp_nested_comp_sum",
        posArgs(listOf(listOf(listOf(1, 2), listOf(3, 4)))))

    @Test fun `comp - multiple filters`() = roundTrip("rtcp_comp_multiple_filters",
        posArgs(listOf(listOf(-5, 10, 50, 150, 0))))

    @Test fun `comp - complex expr`() = roundTrip("rtcp_comp_complex_expr",
        posArgs(listOf(listOf(1, 2, 3, 4, 5))))

    @Test fun `comp - enumerate with comp`() = roundTrip("rtcp_enumerate_with_comp",
        posArgs(listOf(listOf(-1, 2, -3, 4, 5))))

    @Test fun `comp - negate`() = roundTrip("rtcp_comp_negate",
        posArgs(listOf(listOf(1, -2, 3))))

    @Test fun `comp - bool to int`() = roundTrip("rtcp_comp_bool_to_int",
        posArgs(listOf(listOf(1, -1, 0, 5, -3))))

    @Test fun `comp - clamp`() = roundTrip("rtcp_comp_clamp",
        posArgs(listOf(listOf(-5, 50, 150, 0, 100))))

    @Test fun `comp - chain ops`() = roundTrip("rtcp_comp_chain_ops",
        posArgs(listOf(listOf(1, 2, 3))))

    @Test fun `comp - sum and len`() = roundTrip("rtcp_comp_sum_and_len",
        posArgs(listOf(listOf(1, 2, 3)), listOf(emptyList<Int>())))

    @Test fun `comp - cond body`() = roundTrip("rtcp_comp_with_cond_body",
        posArgs(listOf(listOf(3, -2, 1, -4))))

    @Test fun `comp - flatten and filter`() = roundTrip("rtcp_comp_flatten_and_filter",
        posArgs(listOf(listOf(listOf(1, 0), listOf(2, 0, 3)))))

    @Test fun `comp - string filter`() = roundTrip("rtcp_comp_string_filter",
        posArgs(listOf("Hello World 123"), listOf("abc"), listOf("")))

    @Test fun `comp - map and filter`() = roundTrip("rtcp_map_and_filter",
        posArgs(listOf(listOf(1, 2, 3, 4, 5))))

    @Test fun `comp - index values`() = roundTrip("rtcp_comp_index_values",
        posArgs(listOf(listOf(10, 20, 30)), listOf(emptyList<Int>())))
}
