package org.opentaint.ir.test.python.tier3

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Round-trip tests for loop constructs.
 * 55 test cases covering while, for, break, continue, nested loops.
 */
@Tag("tier3")
class RoundTripLoopTest : RoundTripTestBase() {

    override val allSources = """
def rtl_while_count(n: int) -> int:
    i = 0
    while i < n:
        i = i + 1
    return i

def rtl_while_sum(n: int) -> int:
    total = 0
    i = 0
    while i < n:
        total = total + i
        i = i + 1
    return total

def rtl_while_product(n: int) -> int:
    result = 1
    i = 1
    while i <= n:
        result = result * i
        i = i + 1
    return result

def rtl_while_countdown(n: int) -> list:
    result = []
    while n > 0:
        result = result + [n]
        n = n - 1
    return result

def rtl_while_break_first_neg(items: list) -> int:
    i = 0
    while i < len(items):
        if items[i] < 0:
            break
        i = i + 1
    return i

def rtl_while_break_found(items: list, target: int) -> int:
    i = 0
    while i < len(items):
        if items[i] == target:
            return i
        i = i + 1
    return -1

def rtl_while_skip_zeros(items: list) -> int:
    total = 0
    i = 0
    while i < len(items):
        if items[i] == 0:
            i = i + 1
            continue
        total = total + items[i]
        i = i + 1
    return total

def rtl_while_double_break(items: list) -> int:
    i = 0
    while i < len(items):
        if items[i] > 100:
            break
        if items[i] < -100:
            break
        i = i + 1
    return i

def rtl_for_sum(items: list) -> int:
    total = 0
    for x in items:
        total = total + x
    return total

def rtl_for_max(items: list) -> int:
    best = items[0]
    for x in items:
        if x > best:
            best = x
    return best

def rtl_for_min(items: list) -> int:
    best = items[0]
    for x in items:
        if x < best:
            best = x
    return best

def rtl_for_count(items: list, target: int) -> int:
    count = 0
    for x in items:
        if x == target:
            count = count + 1
    return count

def rtl_for_contains(items: list, target: int) -> int:
    for x in items:
        if x == target:
            return 1
    return 0

def rtl_for_all_positive(items: list) -> int:
    for x in items:
        if x <= 0:
            return 0
    return 1

def rtl_for_index_of(items: list, target: int) -> int:
    i = 0
    for x in items:
        if x == target:
            return i
        i = i + 1
    return -1

def rtl_for_continue_skip_neg(items: list) -> list:
    result = []
    for x in items:
        if x < 0:
            continue
        result = result + [x]
    return result

def rtl_for_continue_skip_even(items: list) -> list:
    result = []
    for x in items:
        if x % 2 == 0:
            continue
        result = result + [x]
    return result

def rtl_for_break_at_zero(items: list) -> list:
    result = []
    for x in items:
        if x == 0:
            break
        result = result + [x]
    return result

def rtl_for_first_dup(items: list) -> int:
    seen = []
    for x in items:
        for s in seen:
            if s == x:
                return x
        seen = seen + [x]
    return -1

def rtl_nested_while(rows: int, cols: int) -> int:
    total = 0
    i = 0
    while i < rows:
        j = 0
        while j < cols:
            total = total + 1
            j = j + 1
        i = i + 1
    return total

def rtl_nested_for(matrix: list) -> int:
    total = 0
    for row in matrix:
        for x in row:
            total = total + x
    return total

def rtl_nested_for_flatten(matrix: list) -> list:
    result = []
    for row in matrix:
        for x in row:
            result = result + [x]
    return result

def rtl_nested_break_inner(matrix: list) -> int:
    total = 0
    for row in matrix:
        for x in row:
            if x < 0:
                break
            total = total + x
    return total

def rtl_while_in_for(items: list) -> list:
    result = []
    for x in items:
        n = x
        while n > 0:
            result = result + [n]
            n = n - 1
    return result

def rtl_for_in_while(items: list) -> int:
    total = 0
    i = 0
    while i < len(items):
        for x in items[i]:
            total = total + x
        i = i + 1
    return total

def rtl_multiplication_table(n: int) -> list:
    result = []
    i = 1
    while i <= n:
        j = 1
        while j <= n:
            result = result + [i * j]
            j = j + 1
        i = i + 1
    return result

def rtl_bubble_pass(items: list) -> list:
    result = []
    for x in items:
        result = result + [x]
    i = 0
    while i < len(result) - 1:
        if result[i] > result[i + 1]:
            t = result[i]
            result[i] = result[i + 1]
            result[i + 1] = t
        i = i + 1
    return result

def rtl_insertion_sort(items: list) -> list:
    result = []
    for x in items:
        result = result + [x]
    i = 1
    while i < len(result):
        j = i
        while j > 0:
            if result[j - 1] > result[j]:
                t = result[j]
                result[j] = result[j - 1]
                result[j - 1] = t
            j = j - 1
        i = i + 1
    return result

def rtl_selection_min_idx(items: list) -> int:
    min_idx = 0
    i = 1
    while i < len(items):
        if items[i] < items[min_idx]:
            min_idx = i
        i = i + 1
    return min_idx

def rtl_running_max(items: list) -> list:
    result = []
    best = items[0]
    for x in items:
        if x > best:
            best = x
        result = result + [best]
    return result

def rtl_enumerate_manual(items: list) -> list:
    result = []
    i = 0
    for x in items:
        result = result + [i]
        i = i + 1
    return result

def rtl_zip_manual(a: list, b: list) -> list:
    result = []
    i = 0
    while i < len(a):
        if i >= len(b):
            break
        result = result + [[a[i], b[i]]]
        i = i + 1
    return result

def rtl_repeat_until(start: int, target: int) -> int:
    x = start
    steps = 0
    while x != target:
        if x < target:
            x = x + 1
        else:
            x = x - 1
        steps = steps + 1
    return steps

def rtl_find_two_sum(items: list, target: int) -> list:
    i = 0
    while i < len(items):
        j = i + 1
        while j < len(items):
            if items[i] + items[j] == target:
                return [i, j]
            j = j + 1
        i = i + 1
    return [-1, -1]

def rtl_remove_all(items: list, target: int) -> list:
    result = []
    for x in items:
        if x != target:
            result = result + [x]
    return result

def rtl_unique(items: list) -> list:
    result = []
    for x in items:
        found = 0
        for r in result:
            if r == x:
                found = 1
                break
        if found == 0:
            result = result + [x]
    return result

def rtl_count_pairs(items: list) -> int:
    count = 0
    i = 0
    while i < len(items):
        j = i + 1
        while j < len(items):
            count = count + 1
            j = j + 1
        i = i + 1
    return count

def rtl_sliding_sum(items: list, k: int) -> list:
    result = []
    i = 0
    while i <= len(items) - k:
        total = 0
        j = 0
        while j < k:
            total = total + items[i + j]
            j = j + 1
        result = result + [total]
        i = i + 1
    return result

def rtl_converge(x: int) -> int:
    steps = 0
    while x != 1:
        if x % 2 == 0:
            x = x // 2
        else:
            x = 3 * x + 1
        steps = steps + 1
        if steps > 200:
            break
    return steps

def rtl_pairwise_diff(items: list) -> list:
    result = []
    i = 1
    while i < len(items):
        result = result + [items[i] - items[i - 1]]
        i = i + 1
    return result

def rtl_prefix_match(items: list, prefix: list) -> int:
    if len(prefix) > len(items):
        return 0
    i = 0
    while i < len(prefix):
        if items[i] != prefix[i]:
            return 0
        i = i + 1
    return 1

def rtl_rotate_left(items: list, k: int) -> list:
    n = len(items)
    if n == 0:
        return []
    k = k % n
    result = []
    i = k
    while i < n:
        result = result + [items[i]]
        i = i + 1
    i = 0
    while i < k:
        result = result + [items[i]]
        i = i + 1
    return result

def rtl_chunk(items: list, size: int) -> list:
    result = []
    i = 0
    while i < len(items):
        chunk = []
        j = 0
        while j < size:
            if i + j < len(items):
                chunk = chunk + [items[i + j]]
            j = j + 1
        result = result + [chunk]
        i = i + size
    return result

def rtl_interleave_lists(a: list, b: list) -> list:
    result = []
    i = 0
    while i < len(a) or i < len(b):
        if i < len(a):
            result = result + [a[i]]
        if i < len(b):
            result = result + [b[i]]
        i = i + 1
    return result

def rtl_count_streak(items: list) -> int:
    if len(items) == 0:
        return 0
    best = 1
    current = 1
    i = 1
    while i < len(items):
        if items[i] == items[i - 1]:
            current = current + 1
            if current > best:
                best = current
        else:
            current = 1
        i = i + 1
    return best

def rtl_cumulative_max(items: list) -> list:
    result = []
    best = items[0]
    for x in items:
        if x > best:
            best = x
        result = result + [best]
    return result

def rtl_partition(items: list, pivot: int) -> list:
    lo = []
    hi = []
    for x in items:
        if x < pivot:
            lo = lo + [x]
        else:
            hi = hi + [x]
    return lo + hi

def rtl_take_while_pos(items: list) -> list:
    result = []
    for x in items:
        if x <= 0:
            break
        result = result + [x]
    return result

def rtl_drop_while_pos(items: list) -> list:
    result = []
    dropping = 1
    for x in items:
        if dropping == 1:
            if x <= 0:
                dropping = 0
                result = result + [x]
        else:
            result = result + [x]
    return result
    """.trimIndent()

    // ─── Tests ───────────────────────────────────────────────

    @Test fun `loop - while count`() = roundTrip("rtl_while_count",
        posArgs(listOf(0), listOf(5), listOf(10)))

    @Test fun `loop - while sum`() = roundTrip("rtl_while_sum",
        posArgs(listOf(0), listOf(1), listOf(5), listOf(10)))

    @Test fun `loop - while product`() = roundTrip("rtl_while_product",
        posArgs(listOf(0), listOf(1), listOf(5)))

    @Test fun `loop - while countdown`() = roundTrip("rtl_while_countdown",
        posArgs(listOf(0), listOf(3), listOf(5)))

    @Test fun `loop - while break first neg`() = roundTrip("rtl_while_break_first_neg",
        posArgs(listOf(listOf(1, 2, -1, 3)), listOf(listOf(1, 2, 3)), listOf(listOf(-1))))

    @Test fun `loop - while break found`() = roundTrip("rtl_while_break_found",
        posArgs(listOf(listOf(1, 2, 3), 2), listOf(listOf(1, 2, 3), 5)))

    @Test fun `loop - while skip zeros`() = roundTrip("rtl_while_skip_zeros",
        posArgs(listOf(listOf(1, 0, 2, 0, 3)), listOf(listOf(0, 0, 0)), listOf(listOf(1, 2, 3))))

    @Test fun `loop - while double break`() = roundTrip("rtl_while_double_break",
        posArgs(listOf(listOf(1, 200)), listOf(listOf(1, -200)), listOf(listOf(1, 2, 3))))

    @Test fun `loop - for sum`() = roundTrip("rtl_for_sum",
        posArgs(listOf(listOf(1, 2, 3)), listOf(emptyList<Int>()), listOf(listOf(-1, 0, 1))))

    @Test fun `loop - for max`() = roundTrip("rtl_for_max",
        posArgs(listOf(listOf(3, 1, 4, 1, 5)), listOf(listOf(1)), listOf(listOf(-3, -1, -5))))

    @Test fun `loop - for min`() = roundTrip("rtl_for_min",
        posArgs(listOf(listOf(3, 1, 4, 1, 5)), listOf(listOf(1)), listOf(listOf(-3, -1, -5))))

    @Test fun `loop - for count`() = roundTrip("rtl_for_count",
        posArgs(listOf(listOf(1, 2, 1, 3, 1), 1), listOf(listOf(1, 2, 3), 5)))

    @Test fun `loop - for contains`() = roundTrip("rtl_for_contains",
        posArgs(listOf(listOf(1, 2, 3), 2), listOf(listOf(1, 2, 3), 5), listOf(emptyList<Int>(), 1)))

    @Test fun `loop - for all positive`() = roundTrip("rtl_for_all_positive",
        posArgs(listOf(listOf(1, 2, 3)), listOf(listOf(1, -1)), listOf(emptyList<Int>())))

    @Test fun `loop - for index of`() = roundTrip("rtl_for_index_of",
        posArgs(listOf(listOf(10, 20, 30), 20), listOf(listOf(10, 20, 30), 50)))

    @Test fun `loop - for continue skip neg`() = roundTrip("rtl_for_continue_skip_neg",
        posArgs(listOf(listOf(1, -2, 3, -4, 5)), listOf(emptyList<Int>())))

    @Test fun `loop - for continue skip even`() = roundTrip("rtl_for_continue_skip_even",
        posArgs(listOf(listOf(1, 2, 3, 4, 5)), listOf(listOf(2, 4, 6))))

    @Test fun `loop - for break at zero`() = roundTrip("rtl_for_break_at_zero",
        posArgs(listOf(listOf(1, 2, 0, 3)), listOf(listOf(1, 2, 3)), listOf(listOf(0, 1, 2))))

    @Test fun `loop - for first dup`() = roundTrip("rtl_for_first_dup",
        posArgs(listOf(listOf(1, 2, 3, 2)), listOf(listOf(1, 2, 3)), listOf(listOf(1, 1))))

    @Test fun `loop - nested while`() = roundTrip("rtl_nested_while",
        posArgs(listOf(3, 4), listOf(0, 5), listOf(5, 0)))

    @Test fun `loop - nested for`() = roundTrip("rtl_nested_for",
        posArgs(listOf(listOf(listOf(1, 2), listOf(3, 4))), listOf(listOf(listOf(10)))))

    @Test fun `loop - nested for flatten`() = roundTrip("rtl_nested_for_flatten",
        posArgs(listOf(listOf(listOf(1, 2), listOf(3, 4))), listOf(listOf(emptyList<Int>()))))

    @Test fun `loop - nested break inner`() = roundTrip("rtl_nested_break_inner",
        posArgs(listOf(listOf(listOf(1, 2, -1, 3), listOf(4, 5)))))

    @Test fun `loop - while in for`() = roundTrip("rtl_while_in_for",
        posArgs(listOf(listOf(3, 2, 1)), listOf(listOf(0)), listOf(emptyList<Int>())))

    @Test fun `loop - for in while`() = roundTrip("rtl_for_in_while",
        posArgs(listOf(listOf(listOf(1, 2), listOf(3, 4)))))

    @Test fun `loop - multiplication table`() = roundTrip("rtl_multiplication_table",
        posArgs(listOf(1), listOf(3)))

    @Test fun `loop - bubble pass`() = roundTrip("rtl_bubble_pass",
        posArgs(listOf(listOf(3, 1, 2)), listOf(listOf(1, 2, 3)), listOf(listOf(1))))

    @Test fun `loop - insertion sort`() = roundTrip("rtl_insertion_sort",
        posArgs(listOf(listOf(3, 1, 4, 1, 5)), listOf(listOf(1)), listOf(listOf(5, 4, 3, 2, 1))))

    @Test fun `loop - selection min idx`() = roundTrip("rtl_selection_min_idx",
        posArgs(listOf(listOf(3, 1, 4, 1, 5)), listOf(listOf(1))))

    @Test fun `loop - running max`() = roundTrip("rtl_running_max",
        posArgs(listOf(listOf(1, 3, 2, 5, 4)), listOf(listOf(5, 4, 3, 2, 1))))

    @Test fun `loop - enumerate manual`() = roundTrip("rtl_enumerate_manual",
        posArgs(listOf(listOf(10, 20, 30)), listOf(emptyList<Int>())))

    @Test fun `loop - zip manual`() = roundTrip("rtl_zip_manual",
        posArgs(listOf(listOf(1, 2, 3), listOf(4, 5, 6)), listOf(listOf(1, 2), listOf(4, 5, 6))))

    @Test fun `loop - repeat until`() = roundTrip("rtl_repeat_until",
        posArgs(listOf(0, 5), listOf(5, 0), listOf(3, 3)))

    @Test fun `loop - find two sum`() = roundTrip("rtl_find_two_sum",
        posArgs(listOf(listOf(2, 7, 11, 15), 9), listOf(listOf(1, 2, 3), 10)))

    @Test fun `loop - remove all`() = roundTrip("rtl_remove_all",
        posArgs(listOf(listOf(1, 2, 3, 2, 1), 2), listOf(listOf(1, 1, 1), 1)))

    @Test fun `loop - unique`() = roundTrip("rtl_unique",
        posArgs(listOf(listOf(1, 2, 2, 3, 1)), listOf(listOf(1, 2, 3))))

    @Test fun `loop - count pairs`() = roundTrip("rtl_count_pairs",
        posArgs(listOf(listOf(1, 2, 3)), listOf(listOf(1)), listOf(emptyList<Int>())))

    @Test fun `loop - sliding sum`() = roundTrip("rtl_sliding_sum",
        posArgs(listOf(listOf(1, 2, 3, 4, 5), 3), listOf(listOf(1, 2, 3), 1)))

    @Test fun `loop - converge`() = roundTrip("rtl_converge",
        posArgs(listOf(1), listOf(2), listOf(3), listOf(6), listOf(10)))

    @Test fun `loop - pairwise diff`() = roundTrip("rtl_pairwise_diff",
        posArgs(listOf(listOf(1, 3, 6, 10)), listOf(listOf(5))))

    @Test fun `loop - prefix match`() = roundTrip("rtl_prefix_match",
        posArgs(listOf(listOf(1, 2, 3, 4), listOf(1, 2)), listOf(listOf(1, 2, 3), listOf(1, 3))))

    @Test fun `loop - rotate left`() = roundTrip("rtl_rotate_left",
        posArgs(listOf(listOf(1, 2, 3, 4, 5), 2), listOf(listOf(1, 2, 3), 0), listOf(emptyList<Int>(), 3)))

    @Test fun `loop - chunk`() = roundTrip("rtl_chunk",
        posArgs(listOf(listOf(1, 2, 3, 4, 5), 2), listOf(listOf(1, 2, 3), 3)))

    @Test fun `loop - interleave lists`() = roundTrip("rtl_interleave_lists",
        posArgs(listOf(listOf(1, 2, 3), listOf(4, 5, 6)), listOf(listOf(1, 2), listOf(4, 5, 6))))

    @Test fun `loop - count streak`() = roundTrip("rtl_count_streak",
        posArgs(listOf(listOf(1, 1, 2, 2, 2, 3)), listOf(listOf(1)), listOf(emptyList<Int>())))

    @Test fun `loop - cumulative max`() = roundTrip("rtl_cumulative_max",
        posArgs(listOf(listOf(1, 3, 2, 5, 4)), listOf(listOf(5, 4, 3, 2, 1))))

    @Test fun `loop - partition`() = roundTrip("rtl_partition",
        posArgs(listOf(listOf(3, 1, 4, 1, 5), 3), listOf(listOf(1, 2, 3), 2)))

    @Test fun `loop - take while pos`() = roundTrip("rtl_take_while_pos",
        posArgs(listOf(listOf(1, 2, 3, -1, 4)), listOf(listOf(-1, 2, 3)), listOf(listOf(1, 2, 3))))

    @Test fun `loop - drop while pos`() = roundTrip("rtl_drop_while_pos",
        posArgs(listOf(listOf(1, 2, -1, 3, 4)), listOf(listOf(-1, 2)), listOf(listOf(1, 2, 3))))
}
