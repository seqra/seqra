package org.opentaint.ir.test.python.tier3

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Round-trip tests for collection operations.
 * 50 test cases covering lists, dicts, tuples, subscript, building.
 */
@Tag("tier3")
class RoundTripCollectionTest : RoundTripTestBase() {

    override val allSources = """
def rtco_build_list_0() -> list:
    return []

def rtco_build_list_1(x: int) -> list:
    return [x]

def rtco_build_list_3(a: int, b: int, c: int) -> list:
    return [a, b, c]

def rtco_append_loop(n: int) -> list:
    result = []
    i = 0
    while i < n:
        result = result + [i]
        i = i + 1
    return result

def rtco_prepend_loop(items: list) -> list:
    result = []
    for x in items:
        result = [x] + result
    return result

def rtco_list_len(items: list) -> int:
    return len(items)

def rtco_get_index(items: list, i: int) -> int:
    return items[i]

def rtco_set_index(items: list, i: int, v: int) -> list:
    result = []
    for x in items:
        result = result + [x]
    result[i] = v
    return result

def rtco_list_concat(a: list, b: list) -> list:
    return a + b

def rtco_list_repeat(items: list, n: int) -> list:
    result = []
    i = 0
    while i < n:
        result = result + items
        i = i + 1
    return result

def rtco_list_copy(items: list) -> list:
    result = []
    for x in items:
        result = result + [x]
    return result

def rtco_list_reverse(items: list) -> list:
    result = []
    i = len(items) - 1
    while i >= 0:
        result = result + [items[i]]
        i = i - 1
    return result

def rtco_list_sum(items: list) -> int:
    total = 0
    for x in items:
        total = total + x
    return total

def rtco_list_max(items: list) -> int:
    best = items[0]
    for x in items:
        if x > best:
            best = x
    return best

def rtco_list_min(items: list) -> int:
    best = items[0]
    for x in items:
        if x < best:
            best = x
    return best

def rtco_list_count_val(items: list, val_: int) -> int:
    count = 0
    for x in items:
        if x == val_:
            count = count + 1
    return count

def rtco_list_contains(items: list, val_: int) -> int:
    for x in items:
        if x == val_:
            return 1
    return 0

def rtco_list_remove_first(items: list, val_: int) -> list:
    result = []
    removed = 0
    for x in items:
        if x == val_ and removed == 0:
            removed = 1
        else:
            result = result + [x]
    return result

def rtco_list_filter_pos(items: list) -> list:
    result = []
    for x in items:
        if x > 0:
            result = result + [x]
    return result

def rtco_list_map_double(items: list) -> list:
    result = []
    for x in items:
        result = result + [x * 2]
    return result

def rtco_list_map_square(items: list) -> list:
    result = []
    for x in items:
        result = result + [x * x]
    return result

def rtco_list_take(items: list, n: int) -> list:
    result = []
    i = 0
    while i < n:
        if i >= len(items):
            break
        result = result + [items[i]]
        i = i + 1
    return result

def rtco_list_drop(items: list, n: int) -> list:
    result = []
    i = n
    while i < len(items):
        result = result + [items[i]]
        i = i + 1
    return result

def rtco_list_split_at(items: list, idx: int) -> list:
    left = []
    right = []
    i = 0
    while i < len(items):
        if i < idx:
            left = left + [items[i]]
        else:
            right = right + [items[i]]
        i = i + 1
    return [left, right]

def rtco_build_dict_empty() -> dict:
    return {}

def rtco_build_dict(k1: str, v1: int, k2: str, v2: int) -> dict:
    return {k1: v1, k2: v2}

def rtco_dict_get(d: dict, key: str) -> int:
    return d[key]

def rtco_dict_set(d: dict, key: str, val_: int) -> dict:
    d[key] = val_
    return d

def rtco_dict_keys_to_list(d: dict) -> list:
    result = []
    for k in d:
        result = result + [k]
    return result

def rtco_dict_values_sum(d: dict) -> int:
    total = 0
    for k in d:
        total = total + d[k]
    return total

def rtco_dict_from_lists(keys: list, vals: list) -> dict:
    result = {}
    i = 0
    while i < len(keys):
        result[keys[i]] = vals[i]
        i = i + 1
    return result

def rtco_dict_count(d: dict) -> int:
    count = 0
    for k in d:
        count = count + 1
    return count

def rtco_dict_has_key(d: dict, key: str) -> int:
    for k in d:
        if k == key:
            return 1
    return 0

def rtco_dict_merge(a: dict, b: dict) -> dict:
    result = {}
    for k in a:
        result[k] = a[k]
    for k in b:
        result[k] = b[k]
    return result

def rtco_dict_invert(d: dict) -> dict:
    result = {}
    for k in d:
        result[d[k]] = k
    return result

def rtco_build_tuple_1(x: int) -> tuple:
    return (x,)

def rtco_build_tuple_3(a: int, b: int, c: int) -> tuple:
    return (a, b, c)

def rtco_matrix_create(rows: int, cols: int, val_: int) -> list:
    result = []
    i = 0
    while i < rows:
        row = []
        j = 0
        while j < cols:
            row = row + [val_]
            j = j + 1
        result = result + [row]
        i = i + 1
    return result

def rtco_matrix_sum(matrix: list) -> int:
    total = 0
    for row in matrix:
        for x in row:
            total = total + x
    return total

def rtco_matrix_transpose(matrix: list) -> list:
    if len(matrix) == 0:
        return []
    rows = len(matrix)
    cols = len(matrix[0])
    result = []
    j = 0
    while j < cols:
        new_row = []
        i = 0
        while i < rows:
            new_row = new_row + [matrix[i][j]]
            i = i + 1
        result = result + [new_row]
        j = j + 1
    return result

def rtco_frequency_count(items: list) -> list:
    keys = []
    vals = []
    for x in items:
        found = 0
        i = 0
        while i < len(keys):
            if keys[i] == x:
                vals[i] = vals[i] + 1
                found = 1
                break
            i = i + 1
        if found == 0:
            keys = keys + [x]
            vals = vals + [1]
    return vals

def rtco_group_by_sign(items: list) -> list:
    pos = []
    neg = []
    zero = []
    for x in items:
        if x > 0:
            pos = pos + [x]
        elif x < 0:
            neg = neg + [x]
        else:
            zero = zero + [x]
    return [pos, neg, zero]

def rtco_merge_sorted(a: list, b: list) -> list:
    result = []
    i = 0
    j = 0
    while i < len(a) and j < len(b):
        if a[i] <= b[j]:
            result = result + [a[i]]
            i = i + 1
        else:
            result = result + [b[j]]
            j = j + 1
    while i < len(a):
        result = result + [a[i]]
        i = i + 1
    while j < len(b):
        result = result + [b[j]]
        j = j + 1
    return result

def rtco_list_difference(a: list, b: list) -> list:
    result = []
    for x in a:
        found = 0
        for y in b:
            if x == y:
                found = 1
                break
        if found == 0:
            result = result + [x]
    return result

def rtco_list_intersection(a: list, b: list) -> list:
    result = []
    for x in a:
        for y in b:
            if x == y:
                result = result + [x]
                break
    return result

def rtco_is_sorted(items: list) -> int:
    i = 1
    while i < len(items):
        if items[i] < items[i - 1]:
            return 0
        i = i + 1
    return 1

def rtco_second_largest(items: list) -> int:
    first = items[0]
    second = items[0]
    for x in items:
        if x > first:
            second = first
            first = x
        elif x > second:
            if x != first:
                second = x
    return second
    """.trimIndent()

    // ─── Tests ───────────────────────────────────────────────

    @Test fun `coll - build list 0`() = roundTrip("rtco_build_list_0",
        posArgs(emptyList()))

    @Test fun `coll - build list 1`() = roundTrip("rtco_build_list_1",
        posArgs(listOf(42), listOf(0), listOf(-5)))

    @Test fun `coll - build list 3`() = roundTrip("rtco_build_list_3",
        posArgs(listOf(1, 2, 3), listOf(0, 0, 0)))

    @Test fun `coll - append loop`() = roundTrip("rtco_append_loop",
        posArgs(listOf(0), listOf(3), listOf(5)))

    @Test fun `coll - prepend loop`() = roundTrip("rtco_prepend_loop",
        posArgs(listOf(listOf(1, 2, 3)), listOf(emptyList<Int>())))

    @Test fun `coll - list len`() = roundTrip("rtco_list_len",
        posArgs(listOf(listOf(1, 2, 3)), listOf(emptyList<Int>()), listOf(listOf(1))))

    @Test fun `coll - get index`() = roundTrip("rtco_get_index",
        posArgs(listOf(listOf(10, 20, 30), 0), listOf(listOf(10, 20, 30), 2)))

    @Test fun `coll - set index`() = roundTrip("rtco_set_index",
        posArgs(listOf(listOf(1, 2, 3), 1, 99)))

    @Test fun `coll - list concat`() = roundTrip("rtco_list_concat",
        posArgs(listOf(listOf(1, 2), listOf(3, 4)), listOf(emptyList<Int>(), listOf(1))))

    @Test fun `coll - list repeat`() = roundTrip("rtco_list_repeat",
        posArgs(listOf(listOf(1, 2), 3), listOf(listOf(1), 0)))

    @Test fun `coll - list copy`() = roundTrip("rtco_list_copy",
        posArgs(listOf(listOf(1, 2, 3)), listOf(emptyList<Int>())))

    @Test fun `coll - list reverse`() = roundTrip("rtco_list_reverse",
        posArgs(listOf(listOf(1, 2, 3)), listOf(listOf(1)), listOf(emptyList<Int>())))

    @Test fun `coll - list sum`() = roundTrip("rtco_list_sum",
        posArgs(listOf(listOf(1, 2, 3)), listOf(emptyList<Int>())))

    @Test fun `coll - list max`() = roundTrip("rtco_list_max",
        posArgs(listOf(listOf(3, 1, 4, 1, 5)), listOf(listOf(1))))

    @Test fun `coll - list min`() = roundTrip("rtco_list_min",
        posArgs(listOf(listOf(3, 1, 4, 1, 5)), listOf(listOf(1))))

    @Test fun `coll - list count val`() = roundTrip("rtco_list_count_val",
        posArgs(listOf(listOf(1, 2, 1, 3, 1), 1), listOf(listOf(1, 2, 3), 5)))

    @Test fun `coll - list contains`() = roundTrip("rtco_list_contains",
        posArgs(listOf(listOf(1, 2, 3), 2), listOf(listOf(1, 2, 3), 5)))

    @Test fun `coll - list remove first`() = roundTrip("rtco_list_remove_first",
        posArgs(listOf(listOf(1, 2, 3, 2), 2), listOf(listOf(1, 2, 3), 5)))

    @Test fun `coll - list filter pos`() = roundTrip("rtco_list_filter_pos",
        posArgs(listOf(listOf(1, -2, 3, -4, 5)), listOf(emptyList<Int>())))

    @Test fun `coll - list map double`() = roundTrip("rtco_list_map_double",
        posArgs(listOf(listOf(1, 2, 3)), listOf(emptyList<Int>())))

    @Test fun `coll - list map square`() = roundTrip("rtco_list_map_square",
        posArgs(listOf(listOf(1, 2, 3, 4)), listOf(emptyList<Int>())))

    @Test fun `coll - list take`() = roundTrip("rtco_list_take",
        posArgs(listOf(listOf(1, 2, 3, 4, 5), 3), listOf(listOf(1, 2), 5), listOf(emptyList<Int>(), 2)))

    @Test fun `coll - list drop`() = roundTrip("rtco_list_drop",
        posArgs(listOf(listOf(1, 2, 3, 4, 5), 2), listOf(listOf(1, 2, 3), 5)))

    @Test fun `coll - list split at`() = roundTrip("rtco_list_split_at",
        posArgs(listOf(listOf(1, 2, 3, 4, 5), 3), listOf(listOf(1, 2), 0)))

    @Test fun `coll - build dict empty`() = roundTrip("rtco_build_dict_empty",
        posArgs(emptyList()))

    @Test fun `coll - build dict`() = roundTrip("rtco_build_dict",
        posArgs(listOf("a", 1, "b", 2)))

    @Test fun `coll - dict get`() = roundTrip("rtco_dict_get",
        posArgs(listOf(mapOf("x" to 10, "y" to 20), "x")))

    @Test fun `coll - dict set`() = roundTrip("rtco_dict_set",
        posArgs(listOf(mapOf("x" to 10), "y", 20)))

    @Test fun `coll - dict keys to list`() = roundTrip("rtco_dict_keys_to_list",
        posArgs(listOf(mapOf("a" to 1, "b" to 2))))

    @Test fun `coll - dict values sum`() = roundTrip("rtco_dict_values_sum",
        posArgs(listOf(mapOf("a" to 10, "b" to 20))))

    @Test fun `coll - dict from lists`() = roundTrip("rtco_dict_from_lists",
        posArgs(listOf(listOf("a", "b"), listOf(1, 2)), listOf(emptyList<String>(), emptyList<Int>())))

    @Test fun `coll - dict count`() = roundTrip("rtco_dict_count",
        posArgs(listOf(mapOf("a" to 1, "b" to 2)), listOf(emptyMap<String, Int>())))

    @Test fun `coll - dict has key`() = roundTrip("rtco_dict_has_key",
        posArgs(listOf(mapOf("a" to 1, "b" to 2), "a"), listOf(mapOf("a" to 1), "z")))

    @Test fun `coll - dict merge`() = roundTrip("rtco_dict_merge",
        posArgs(listOf(mapOf("a" to 1), mapOf("b" to 2))))

    @Test fun `coll - dict invert`() = roundTrip("rtco_dict_invert",
        posArgs(listOf(mapOf("a" to 1, "b" to 2))))

    @Test fun `coll - build tuple 1`() = roundTrip("rtco_build_tuple_1",
        posArgs(listOf(42), listOf(0)))

    @Test fun `coll - build tuple 3`() = roundTrip("rtco_build_tuple_3",
        posArgs(listOf(1, 2, 3)))

    @Test fun `coll - matrix create`() = roundTrip("rtco_matrix_create",
        posArgs(listOf(2, 3, 0), listOf(1, 1, 5)))

    @Test fun `coll - matrix sum`() = roundTrip("rtco_matrix_sum",
        posArgs(listOf(listOf(listOf(1, 2), listOf(3, 4)))))

    @Test fun `coll - matrix transpose`() = roundTrip("rtco_matrix_transpose",
        posArgs(listOf(listOf(listOf(1, 2, 3), listOf(4, 5, 6))), listOf(emptyList<Any>())))

    @Test fun `coll - frequency count`() = roundTrip("rtco_frequency_count",
        posArgs(listOf(listOf(1, 2, 1, 3, 2, 1))))

    @Test fun `coll - group by sign`() = roundTrip("rtco_group_by_sign",
        posArgs(listOf(listOf(1, -2, 3, 0, -4, 5))))

    @Test fun `coll - merge sorted`() = roundTrip("rtco_merge_sorted",
        posArgs(listOf(listOf(1, 3, 5), listOf(2, 4, 6)), listOf(emptyList<Int>(), listOf(1, 2))))

    @Test fun `coll - list difference`() = roundTrip("rtco_list_difference",
        posArgs(listOf(listOf(1, 2, 3, 4), listOf(2, 4))))

    @Test fun `coll - list intersection`() = roundTrip("rtco_list_intersection",
        posArgs(listOf(listOf(1, 2, 3), listOf(2, 3, 4))))

    @Test fun `coll - is sorted`() = roundTrip("rtco_is_sorted",
        posArgs(listOf(listOf(1, 2, 3)), listOf(listOf(3, 1, 2)), listOf(emptyList<Int>())))

    @Test fun `coll - second largest`() = roundTrip("rtco_second_largest",
        posArgs(listOf(listOf(3, 1, 4, 1, 5)), listOf(listOf(1, 2))))
}
