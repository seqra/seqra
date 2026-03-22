package org.opentaint.ir.test.python.tier3

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Round-trip tests for advanced patterns: walrus in complex contexts,
 * chained comparisons, augmented assignments, ternary chains, and
 * complex mixed patterns. Generators can't be round-tripped via
 * the state machine approach, so this focuses on non-generator
 * advanced patterns.
 * 40 test cases.
 */
@Tag("tier3")
class RoundTripAdvancedTest : RoundTripTestBase() {

    override val allSources = """
# ─── Chained comparisons ───────────────────────────────

def rta_chain_simple(x: int) -> bool:
    return 0 < x < 10

def rta_chain_triple(x: int) -> bool:
    return 0 < x < 50 < 100

def rta_chain_ge(x: int) -> bool:
    return 100 >= x >= 0

def rta_chain_eq(a: int, b: int, c: int) -> bool:
    return a == b == c

def rta_chain_in_if(x: int) -> int:
    if 0 < x < 100:
        return x
    return -1

def rta_chain_mixed(x: int, y: int) -> bool:
    return x < y <= 100

# ─── Complex arithmetic ────────────────────────────────

def rta_augmented_chain(x: int) -> int:
    result = x
    result += 10
    result *= 2
    result -= 5
    result //= 3
    return result

def rta_power_chain(x: int) -> int:
    return x ** 2 ** 1

def rta_complex_expr(a: int, b: int, c: int) -> int:
    return (a + b) * c - (a - b) // (c + 1)

def rta_bitwise_combo(x: int, y: int) -> int:
    return (x & y) | (x ^ y)

def rta_shift_combo(x: int, n: int) -> int:
    return (x << n) >> 1

# ─── Nested ternary ─────────────────────────────────────

def rta_nested_ternary(x: int) -> str:
    return "positive" if x > 0 else ("zero" if x == 0 else "negative")

def rta_ternary_in_assign(x: int) -> int:
    a = x * 2 if x > 0 else x * -2
    return a

def rta_ternary_in_return(a: int, b: int) -> int:
    return a if a > b else b

def rta_ternary_chain(x: int) -> int:
    return 3 if x > 100 else (2 if x > 10 else (1 if x > 0 else 0))

# ─── Multiple assignment patterns ──────────────────────

def rta_multi_assign(x: int) -> list:
    a = b = c = x
    a = a + 1
    b = b + 2
    c = c + 3
    return [a, b, c]

def rta_swap(a: int, b: int) -> list:
    a, b = b, a
    return [a, b]

def rta_tuple_unpack(x: int, y: int, z: int) -> int:
    a, b, c = x, y, z
    return a + b + c

# ─── Complex loop patterns ─────────────────────────────

def rta_nested_loop_sum(n: int) -> int:
    total = 0
    i = 0
    while i < n:
        j = 0
        while j < i:
            total += j
            j += 1
        i += 1
    return total

def rta_loop_with_flag(items: list) -> bool:
    found = False
    for x in items:
        if x == 0:
            found = True
    return found

def rta_accumulate_positive(items: list) -> int:
    total = 0
    for x in items:
        if x > 0:
            total += x
    return total

def rta_count_negatives(items: list) -> int:
    count = 0
    for x in items:
        if x < 0:
            count += 1
    return count

def rta_min_max(items: list) -> list:
    if len(items) == 0:
        return [0, 0]
    mn = items[0]
    mx = items[0]
    for x in items:
        if x < mn:
            mn = x
        if x > mx:
            mx = x
    return [mn, mx]

# ─── String operations ─────────────────────────────────

def rta_str_repeat(s: str, n: int) -> str:
    result = ""
    i = 0
    while i < n:
        result = result + s
        i += 1
    return result

def rta_str_contains(haystack: str, needle: str) -> bool:
    return needle in haystack

def rta_str_len(s: str) -> int:
    return len(s)

# ─── Walrus in complex contexts ────────────────────────

def rta_walrus_accumulate(items: list) -> int:
    total = 0
    for x in items:
        total += (doubled := x * 2)
    return total + doubled

def rta_walrus_max(items: list) -> int:
    best = items[0]
    for x in items:
        if (v := x * 2) > best:
            best = v
    return best

# ─── Complex boolean logic ─────────────────────────────

def rta_complex_bool(a: int, b: int, c: int) -> bool:
    return a > 0 and b > 0 or c > 0

def rta_not_chain(a: bool, b: bool) -> bool:
    return not a and not b

def rta_bool_with_compare(x: int, y: int) -> bool:
    return x > 0 and y > 0 and x + y > 10

# ─── Collection building ───────────────────────────────

def rta_build_list_loop(n: int) -> list:
    result = []
    i = 0
    while i < n:
        result = result + [i * i]
        i += 1
    return result

def rta_build_dict_loop(keys: list, values: list) -> dict:
    result = {}
    i = 0
    while i < len(keys):
        result[keys[i]] = values[i]
        i += 1
    return result

def rta_merge_lists(a: list, b: list) -> list:
    result = []
    for x in a:
        result = result + [x]
    for y in b:
        result = result + [y]
    return result

def rta_filter_and_transform(items: list) -> list:
    result = []
    for x in items:
        if x > 0:
            result = result + [x * 2]
    return result

# ─── Nested if patterns ────────────────────────────────

def rta_classify(x: int) -> str:
    if x > 100:
        return "huge"
    elif x > 50:
        return "large"
    elif x > 10:
        return "medium"
    elif x > 0:
        return "small"
    else:
        return "non-positive"

def rta_guard_clauses(x: int, y: int) -> int:
    if x <= 0:
        return 0
    if y <= 0:
        return 0
    return x * y
    """.trimIndent()

    // ─── Chained comparisons ───────────────────────────────

    @Test fun `chain simple`() = roundTrip("rta_chain_simple", posArgs(listOf(5), listOf(15), listOf(-1)))
    @Test fun `chain triple`() = roundTrip("rta_chain_triple", posArgs(listOf(25), listOf(75), listOf(-1)))
    @Test fun `chain ge`() = roundTrip("rta_chain_ge", posArgs(listOf(50), listOf(101), listOf(-1)))
    @Test fun `chain eq`() = roundTrip("rta_chain_eq", posArgs(listOf(5, 5, 5), listOf(5, 5, 6)))
    @Test fun `chain in if`() = roundTrip("rta_chain_in_if", posArgs(listOf(50), listOf(150), listOf(-1)))
    @Test fun `chain mixed`() = roundTrip("rta_chain_mixed", posArgs(listOf(3, 50), listOf(50, 50), listOf(50, 3)))

    // ─── Complex arithmetic ────────────────────────────────

    @Test fun `augmented chain`() = roundTrip("rta_augmented_chain", posArgs(listOf(10), listOf(0), listOf(100)))
    @Test fun `power chain`() = roundTrip("rta_power_chain", posArgs(listOf(3), listOf(2)))
    @Test fun `complex expr`() = roundTrip("rta_complex_expr", posArgs(listOf(5, 3, 2), listOf(10, 4, 3)))
    @Test fun `bitwise combo`() = roundTrip("rta_bitwise_combo", posArgs(listOf(0b1010, 0b1100)))
    @Test fun `shift combo`() = roundTrip("rta_shift_combo", posArgs(listOf(5, 3), listOf(1, 0)))

    // ─── Nested ternary ────────────────────────────────────

    @Test fun `nested ternary`() = roundTrip("rta_nested_ternary", posArgs(listOf(5), listOf(0), listOf(-3)))
    @Test fun `ternary in assign`() = roundTrip("rta_ternary_in_assign", posArgs(listOf(5), listOf(-3)))
    @Test fun `ternary in return`() = roundTrip("rta_ternary_in_return", posArgs(listOf(5, 3), listOf(3, 5)))
    @Test fun `ternary chain`() = roundTrip("rta_ternary_chain", posArgs(listOf(200), listOf(50), listOf(5), listOf(-1)))

    // ─── Assignment patterns ───────────────────────────────

    @Test fun `multi assign`() = roundTrip("rta_multi_assign", posArgs(listOf(10)))
    @Test fun `swap`() = roundTrip("rta_swap", posArgs(listOf(3, 7), listOf(1, 1)))
    @Test fun `tuple unpack`() = roundTrip("rta_tuple_unpack", posArgs(listOf(1, 2, 3)))

    // ─── Complex loop patterns ─────────────────────────────

    @Test fun `nested loop sum`() = roundTrip("rta_nested_loop_sum", posArgs(listOf(5), listOf(0)))
    @Test fun `loop with flag`() = roundTrip("rta_loop_with_flag", posArgs(listOf(listOf(1, 2, 0, 3)), listOf(listOf(1, 2, 3))))
    @Test fun `accumulate positive`() = roundTrip("rta_accumulate_positive", posArgs(listOf(listOf(1, -2, 3, -4, 5))))
    @Test fun `count negatives`() = roundTrip("rta_count_negatives", posArgs(listOf(listOf(1, -2, 3, -4, 5))))
    @Test fun `min max`() = roundTrip("rta_min_max", posArgs(listOf(listOf(3, 1, 4, 1, 5)), listOf(emptyList<Int>())))

    // ─── String operations ─────────────────────────────────

    @Test fun `str repeat`() = roundTrip("rta_str_repeat", posArgs(listOf("ab", 3), listOf("x", 0)))
    @Test fun `str contains`() = roundTrip("rta_str_contains", posArgs(listOf("hello world", "world"), listOf("hello", "xyz")))
    @Test fun `str len`() = roundTrip("rta_str_len", posArgs(listOf("hello"), listOf("")))

    // ─── Walrus in complex contexts ────────────────────────

    @Test fun `walrus accumulate`() = roundTrip("rta_walrus_accumulate", posArgs(listOf(listOf(1, 2, 3))))
    @Test fun `walrus max`() = roundTrip("rta_walrus_max", posArgs(listOf(listOf(3, 1, 4, 1, 5))))

    // ─── Complex boolean logic ─────────────────────────────

    @Test fun `complex bool`() = roundTrip("rta_complex_bool", posArgs(listOf(1, 1, 0), listOf(-1, -1, 1), listOf(-1, -1, -1)))
    @Test fun `not chain`() = roundTrip("rta_not_chain", posArgs(listOf(false, false), listOf(true, false), listOf(false, true)))
    @Test fun `bool with compare`() = roundTrip("rta_bool_with_compare", posArgs(listOf(5, 10), listOf(5, 3), listOf(-1, 20)))

    // ─── Collection building ───────────────────────────────

    @Test fun `build list loop`() = roundTrip("rta_build_list_loop", posArgs(listOf(5), listOf(0)))
    @Test fun `build dict loop`() = roundTrip("rta_build_dict_loop", posArgs(listOf(listOf("a", "b", "c"), listOf(1, 2, 3))))
    @Test fun `merge lists`() = roundTrip("rta_merge_lists", posArgs(listOf(listOf(1, 2), listOf(3, 4))))
    @Test fun `filter and transform`() = roundTrip("rta_filter_and_transform", posArgs(listOf(listOf(1, -2, 3, -4, 5))))

    // ─── Nested if patterns ────────────────────────────────

    @Test fun `classify`() = roundTrip("rta_classify", posArgs(listOf(200), listOf(75), listOf(25), listOf(5), listOf(-1)))
    @Test fun `guard clauses`() = roundTrip("rta_guard_clauses", posArgs(listOf(5, 3), listOf(-1, 5), listOf(5, -1)))
}
