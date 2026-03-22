package org.opentaint.ir.test.python.tier3

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Round-trip tests for mixed/complex patterns.
 * 55 test cases covering algorithms, combined patterns, edge cases.
 */
@Tag("tier3")
class RoundTripMixedTest : RoundTripTestBase() {

    override val allSources = """
def rtm_binary_search(items: list, target: int) -> int:
    lo = 0
    hi = len(items) - 1
    while lo <= hi:
        mid = (lo + hi) // 2
        if items[mid] == target:
            return mid
        elif items[mid] < target:
            lo = mid + 1
        else:
            hi = mid - 1
    return -1

def rtm_two_sum_sorted(items: list, target: int) -> list:
    lo = 0
    hi = len(items) - 1
    while lo < hi:
        s = items[lo] + items[hi]
        if s == target:
            return [lo, hi]
        elif s < target:
            lo = lo + 1
        else:
            hi = hi - 1
    return [-1, -1]

def rtm_stack_operations(ops: list) -> list:
    stack = []
    for op in ops:
        if op >= 0:
            stack = stack + [op]
        else:
            if len(stack) > 0:
                stack = stack[0:len(stack) - 1]
    return stack

def rtm_matching_brackets(s: str) -> int:
    depth = 0
    for c in s:
        if c == "(":
            depth = depth + 1
        elif c == ")":
            depth = depth - 1
            if depth < 0:
                return 0
    if depth == 0:
        return 1
    return 0

def rtm_roman_value(c: str) -> int:
    if c == "I":
        return 1
    if c == "V":
        return 5
    if c == "X":
        return 10
    if c == "L":
        return 50
    if c == "C":
        return 100
    if c == "D":
        return 500
    if c == "M":
        return 1000
    return 0

def rtm_int_to_base(n: int, base: int) -> str:
    if n == 0:
        return "0"
    digits = "0123456789ABCDEF"
    result = ""
    neg = 0
    if n < 0:
        neg = 1
        n = -n
    while n > 0:
        result = digits[n % base] + result
        n = n // base
    if neg == 1:
        result = "-" + result
    return result

def rtm_atoi(s: str) -> int:
    result = 0
    neg = 0
    i = 0
    if len(s) > 0:
        if s[0] == "-":
            neg = 1
            i = 1
    while i < len(s):
        result = result * 10 + (ord(s[i]) - 48)
        i = i + 1
    if neg == 1:
        return -result
    return result

def rtm_itoa(n: int) -> str:
    if n == 0:
        return "0"
    neg = 0
    if n < 0:
        neg = 1
        n = -n
    result = ""
    while n > 0:
        result = chr(n % 10 + 48) + result
        n = n // 10
    if neg == 1:
        result = "-" + result
    return result

def rtm_run_length_encode(s: str) -> str:
    if len(s) == 0:
        return ""
    result = ""
    current = s[0]
    count = 1
    i = 1
    while i < len(s):
        if s[i] == current:
            count = count + 1
        else:
            result = result + current
            if count > 1:
                c = count
                digits = ""
                while c > 0:
                    digits = chr(c % 10 + 48) + digits
                    c = c // 10
                result = result + digits
            current = s[i]
            count = 1
        i = i + 1
    result = result + current
    if count > 1:
        c = count
        digits = ""
        while c > 0:
            digits = chr(c % 10 + 48) + digits
            c = c // 10
        result = result + digits
    return result

def rtm_pascal_row(n: int) -> list:
    row = [1]
    i = 0
    while i < n:
        new_row = [1]
        j = 1
        while j < len(row):
            new_row = new_row + [row[j - 1] + row[j]]
            j = j + 1
        new_row = new_row + [1]
        row = new_row
        i = i + 1
    return row

def rtm_sieve_count(n: int) -> int:
    if n < 2:
        return 0
    is_prime = []
    i = 0
    while i <= n:
        is_prime = is_prime + [1]
        i = i + 1
    is_prime[0] = 0
    is_prime[1] = 0
    i = 2
    while i * i <= n:
        if is_prime[i] == 1:
            j = i * i
            while j <= n:
                is_prime[j] = 0
                j = j + i
        i = i + 1
    count = 0
    for x in is_prime:
        count = count + x
    return count

def rtm_longest_run(items: list) -> int:
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

def rtm_kadane_max_subarray(items: list) -> int:
    best = items[0]
    current = items[0]
    i = 1
    while i < len(items):
        if current + items[i] > items[i]:
            current = current + items[i]
        else:
            current = items[i]
        if current > best:
            best = current
        i = i + 1
    return best

def rtm_dutch_flag(items: list) -> list:
    zeros = []
    ones = []
    twos = []
    for x in items:
        if x == 0:
            zeros = zeros + [0]
        elif x == 1:
            ones = ones + [1]
        else:
            twos = twos + [2]
    return zeros + ones + twos

def rtm_majority_element(items: list) -> int:
    candidate = items[0]
    count = 1
    i = 1
    while i < len(items):
        if count == 0:
            candidate = items[i]
            count = 1
        elif items[i] == candidate:
            count = count + 1
        else:
            count = count - 1
        i = i + 1
    return candidate

def rtm_move_zeros(items: list) -> list:
    non_zero = []
    zero_count = 0
    for x in items:
        if x != 0:
            non_zero = non_zero + [x]
        else:
            zero_count = zero_count + 1
    result = non_zero
    i = 0
    while i < zero_count:
        result = result + [0]
        i = i + 1
    return result

def rtm_spiral_sum(n: int) -> int:
    total = 0
    i = 1
    while i <= n:
        j = 1
        while j <= n:
            total = total + i * j
            j = j + 1
        i = i + 1
    return total

def rtm_state_machine(ops: list) -> str:
    state = "idle"
    for op in ops:
        if state == "idle":
            if op == 1:
                state = "running"
        elif state == "running":
            if op == 0:
                state = "idle"
            elif op == 2:
                state = "paused"
        elif state == "paused":
            if op == 1:
                state = "running"
            elif op == 0:
                state = "idle"
    return state

def rtm_evaluate_rpn(tokens: list) -> int:
    stack = []
    for t in tokens:
        if t == "+":
            b = stack[len(stack) - 1]
            stack = stack[0:len(stack) - 1]
            a = stack[len(stack) - 1]
            stack = stack[0:len(stack) - 1]
            stack = stack + [a + b]
        elif t == "-":
            b = stack[len(stack) - 1]
            stack = stack[0:len(stack) - 1]
            a = stack[len(stack) - 1]
            stack = stack[0:len(stack) - 1]
            stack = stack + [a - b]
        elif t == "*":
            b = stack[len(stack) - 1]
            stack = stack[0:len(stack) - 1]
            a = stack[len(stack) - 1]
            stack = stack[0:len(stack) - 1]
            stack = stack + [a * b]
        else:
            stack = stack + [t]
    return stack[0]

def rtm_matrix_multiply_element(a: list, b: list, row: int, col: int) -> int:
    total = 0
    k = 0
    while k < len(b):
        total = total + a[row][k] * b[k][col]
        k = k + 1
    return total

def rtm_histogram(items: list, buckets: int, lo: int, hi: int) -> list:
    counts = []
    i = 0
    while i < buckets:
        counts = counts + [0]
        i = i + 1
    width = (hi - lo) // buckets
    if width == 0:
        width = 1
    for x in items:
        if x >= lo:
            if x < hi:
                idx = (x - lo) // width
                if idx >= buckets:
                    idx = buckets - 1
                counts[idx] = counts[idx] + 1
    return counts

def rtm_count_inversions(items: list) -> int:
    count = 0
    i = 0
    while i < len(items):
        j = i + 1
        while j < len(items):
            if items[i] > items[j]:
                count = count + 1
            j = j + 1
        i = i + 1
    return count

def rtm_zigzag(items: list) -> list:
    result = []
    for x in items:
        result = result + [x]
    i = 0
    while i < len(result) - 1:
        if i % 2 == 0:
            if result[i] > result[i + 1]:
                t = result[i]
                result[i] = result[i + 1]
                result[i + 1] = t
        else:
            if result[i] < result[i + 1]:
                t = result[i]
                result[i] = result[i + 1]
                result[i + 1] = t
        i = i + 1
    return result

def rtm_max_profit(prices: list) -> int:
    if len(prices) < 2:
        return 0
    min_price = prices[0]
    max_profit = 0
    i = 1
    while i < len(prices):
        profit = prices[i] - min_price
        if profit > max_profit:
            max_profit = profit
        if prices[i] < min_price:
            min_price = prices[i]
        i = i + 1
    return max_profit

def rtm_trap_water(heights: list) -> int:
    n = len(heights)
    if n < 3:
        return 0
    left_max = []
    i = 0
    while i < n:
        left_max = left_max + [0]
        i = i + 1
    left_max[0] = heights[0]
    i = 1
    while i < n:
        if heights[i] > left_max[i - 1]:
            left_max[i] = heights[i]
        else:
            left_max[i] = left_max[i - 1]
        i = i + 1
    right_max = []
    i = 0
    while i < n:
        right_max = right_max + [0]
        i = i + 1
    right_max[n - 1] = heights[n - 1]
    i = n - 2
    while i >= 0:
        if heights[i] > right_max[i + 1]:
            right_max[i] = heights[i]
        else:
            right_max[i] = right_max[i + 1]
        i = i - 1
    total = 0
    i = 0
    while i < n:
        m = left_max[i]
        if right_max[i] < m:
            m = right_max[i]
        if m > heights[i]:
            total = total + m - heights[i]
        i = i + 1
    return total

def rtm_valid_sudoku_row(row: list) -> int:
    seen = []
    for x in row:
        if x != 0:
            for s in seen:
                if s == x:
                    return 0
            seen = seen + [x]
    return 1

def rtm_encode_decode(items: list, key: int) -> list:
    encoded = []
    for x in items:
        encoded = encoded + [x + key]
    decoded = []
    for x in encoded:
        decoded = decoded + [x - key]
    return decoded

def rtm_life_step(cells: list) -> list:
    n = len(cells)
    result = []
    i = 0
    while i < n:
        left = 0
        if i > 0:
            left = cells[i - 1]
        right = 0
        if i < n - 1:
            right = cells[i + 1]
        neighbors = left + right
        if cells[i] == 1:
            if neighbors == 1:
                result = result + [1]
            else:
                result = result + [0]
        else:
            if neighbors == 1:
                result = result + [1]
            else:
                result = result + [0]
        i = i + 1
    return result

def rtm_compress_list(items: list) -> list:
    if len(items) == 0:
        return []
    result = [items[0]]
    i = 1
    while i < len(items):
        if items[i] != items[i - 1]:
            result = result + [items[i]]
        i = i + 1
    return result

def rtm_alternating_sum(items: list) -> int:
    total = 0
    i = 0
    for x in items:
        if i % 2 == 0:
            total = total + x
        else:
            total = total - x
        i = i + 1
    return total

def rtm_equilibrium_index(items: list) -> int:
    total = 0
    for x in items:
        total = total + x
    left_sum = 0
    i = 0
    while i < len(items):
        right_sum = total - left_sum - items[i]
        if left_sum == right_sum:
            return i
        left_sum = left_sum + items[i]
        i = i + 1
    return -1

def rtm_leaders(items: list) -> list:
    n = len(items)
    if n == 0:
        return []
    result = []
    max_right = items[n - 1]
    result = result + [max_right]
    i = n - 2
    while i >= 0:
        if items[i] > max_right:
            max_right = items[i]
            result = [max_right] + result
        i = i - 1
    return result

def rtm_wave_sort(items: list) -> list:
    result = []
    for x in items:
        result = result + [x]
    i = 0
    while i < len(result) - 1:
        j = i
        while j > 0:
            if result[j - 1] > result[j]:
                t = result[j]
                result[j] = result[j - 1]
                result[j - 1] = t
            j = j - 1
        i = i + 1
    i = 0
    while i < len(result) - 1:
        if i % 2 == 0:
            t = result[i]
            result[i] = result[i + 1]
            result[i + 1] = t
        i = i + 2
    return result

def rtm_range_sum_queries(items: list, queries: list) -> list:
    prefix = [0]
    total = 0
    for x in items:
        total = total + x
        prefix = prefix + [total]
    result = []
    for q in queries:
        result = result + [prefix[q[1] + 1] - prefix[q[0]]]
    return result

def rtm_max_subarray_len(items: list, target: int) -> int:
    best = 0
    i = 0
    while i < len(items):
        current = 0
        j = i
        while j < len(items):
            current = current + items[j]
            if current == target:
                length = j - i + 1
                if length > best:
                    best = length
            j = j + 1
        i = i + 1
    return best

def rtm_count_plateaus(items: list) -> int:
    if len(items) < 2:
        return 0
    count = 0
    i = 0
    while i < len(items) - 1:
        if items[i] == items[i + 1]:
            higher_left = 0
            if i == 0:
                higher_left = 1
            elif items[i - 1] < items[i]:
                higher_left = 1
            j = i + 1
            while j < len(items) - 1:
                if items[j] != items[j + 1]:
                    break
                j = j + 1
            higher_right = 0
            if j == len(items) - 1:
                higher_right = 1
            elif items[j + 1] < items[j]:
                higher_right = 1
            if higher_left == 1 and higher_right == 1:
                count = count + 1
            i = j + 1
        else:
            i = i + 1
    return count

def rtm_simulate_queue(ops: list) -> list:
    queue = []
    output = []
    for op in ops:
        if op >= 0:
            queue = queue + [op]
        else:
            if len(queue) > 0:
                output = output + [queue[0]]
                queue = queue[1:]
    return output

def rtm_look_and_say_step(s: str) -> str:
    if len(s) == 0:
        return ""
    result = ""
    current = s[0]
    count = 1
    i = 1
    while i < len(s):
        if s[i] == current:
            count = count + 1
        else:
            c = count
            digits = ""
            while c > 0:
                digits = chr(c % 10 + 48) + digits
                c = c // 10
            result = result + digits + current
            current = s[i]
            count = 1
        i = i + 1
    c = count
    digits = ""
    while c > 0:
        digits = chr(c % 10 + 48) + digits
        c = c // 10
    result = result + digits + current
    return result

def rtm_peaks(items: list) -> list:
    result = []
    i = 1
    while i < len(items) - 1:
        if items[i] > items[i - 1]:
            if items[i] > items[i + 1]:
                result = result + [i]
        i = i + 1
    return result

def rtm_valleys(items: list) -> list:
    result = []
    i = 1
    while i < len(items) - 1:
        if items[i] < items[i - 1]:
            if items[i] < items[i + 1]:
                result = result + [i]
        i = i + 1
    return result

def rtm_pascal_triangle_flat(n: int) -> list:
    result = []
    row = [1]
    i = 0
    while i < n:
        for x in row:
            result = result + [x]
        new_row = [1]
        j = 1
        while j < len(row):
            new_row = new_row + [row[j - 1] + row[j]]
            j = j + 1
        new_row = new_row + [1]
        row = new_row
        i = i + 1
    return result

def rtm_collatz_sequence(n: int) -> list:
    result = [n]
    while n != 1:
        if n % 2 == 0:
            n = n // 2
        else:
            n = 3 * n + 1
        result = result + [n]
    return result

def rtm_digital_root(n: int) -> int:
    while n >= 10:
        total = 0
        while n > 0:
            total = total + n % 10
            n = n // 10
        n = total
    return n

def rtm_happy_number(n: int) -> int:
    seen = []
    while n != 1:
        for s in seen:
            if s == n:
                return 0
        seen = seen + [n]
        total = 0
        while n > 0:
            d = n % 10
            total = total + d * d
            n = n // 10
        n = total
    return 1

def rtm_matrix_diagonal_sum(matrix: list) -> int:
    n = len(matrix)
    total = 0
    i = 0
    while i < n:
        total = total + matrix[i][i]
        if i != n - 1 - i:
            total = total + matrix[i][n - 1 - i]
        i = i + 1
    return total
    """.trimIndent()

    // ─── Tests ───────────────────────────────────────────────

    @Test fun `mixed - binary search`() = roundTrip("rtm_binary_search",
        posArgs(listOf(listOf(1, 3, 5, 7, 9), 5), listOf(listOf(1, 3, 5, 7, 9), 4), listOf(listOf(1), 1)))

    @Test fun `mixed - two sum sorted`() = roundTrip("rtm_two_sum_sorted",
        posArgs(listOf(listOf(1, 2, 3, 4, 5), 5), listOf(listOf(1, 2, 3), 10)))

    @Test fun `mixed - stack operations`() = roundTrip("rtm_stack_operations",
        posArgs(listOf(listOf(1, 2, -1, 3, -1, -1)), listOf(listOf(-1, 1, 2))))

    @Test fun `mixed - matching brackets`() = roundTrip("rtm_matching_brackets",
        posArgs(listOf("(())"), listOf("(()"), listOf(")("), listOf("")))

    @Test fun `mixed - roman value`() = roundTrip("rtm_roman_value",
        posArgs(listOf("I"), listOf("V"), listOf("X"), listOf("M"), listOf("Z")))

    @Test fun `mixed - int to base`() = roundTrip("rtm_int_to_base",
        posArgs(listOf(255, 16), listOf(10, 2), listOf(0, 10), listOf(-42, 10)))

    @Test fun `mixed - atoi`() = roundTrip("rtm_atoi",
        posArgs(listOf("123"), listOf("-42"), listOf("0"), listOf("999")))

    @Test fun `mixed - itoa`() = roundTrip("rtm_itoa",
        posArgs(listOf(123), listOf(-42), listOf(0)))

    @Test fun `mixed - run length encode`() = roundTrip("rtm_run_length_encode",
        posArgs(listOf("aabbbcc"), listOf("abc"), listOf(""), listOf("aaaa")))

    @Test fun `mixed - pascal row`() = roundTrip("rtm_pascal_row",
        posArgs(listOf(0), listOf(1), listOf(4)))

    @Test fun `mixed - sieve count`() = roundTrip("rtm_sieve_count",
        posArgs(listOf(0), listOf(1), listOf(10), listOf(30)))

    @Test fun `mixed - longest run`() = roundTrip("rtm_longest_run",
        posArgs(listOf(listOf(1, 1, 2, 2, 2, 3)), listOf(listOf(1)), listOf(emptyList<Int>())))

    @Test fun `mixed - kadane max subarray`() = roundTrip("rtm_kadane_max_subarray",
        posArgs(listOf(listOf(-2, 1, -3, 4, -1, 2, 1, -5, 4)), listOf(listOf(1, 2, 3))))

    @Test fun `mixed - dutch flag`() = roundTrip("rtm_dutch_flag",
        posArgs(listOf(listOf(2, 0, 1, 2, 0, 1)), listOf(listOf(0, 0, 0))))

    @Test fun `mixed - majority element`() = roundTrip("rtm_majority_element",
        posArgs(listOf(listOf(1, 1, 2, 1, 3, 1)), listOf(listOf(2, 2, 1, 1, 2))))

    @Test fun `mixed - move zeros`() = roundTrip("rtm_move_zeros",
        posArgs(listOf(listOf(0, 1, 0, 3, 12)), listOf(listOf(0, 0, 0)), listOf(listOf(1, 2, 3))))

    @Test fun `mixed - spiral sum`() = roundTrip("rtm_spiral_sum",
        posArgs(listOf(1), listOf(3), listOf(4)))

    @Test fun `mixed - state machine`() = roundTrip("rtm_state_machine",
        posArgs(listOf(listOf(1, 2, 1, 0)), listOf(listOf(1, 0)), listOf(emptyList<Int>())))

    @Test fun `mixed - evaluate rpn`() = roundTrip("rtm_evaluate_rpn",
        posArgs(listOf(listOf(3, 4, "+")), listOf(listOf(2, 3, "*", 4, "+")), listOf(listOf(5, 3, "-"))))

    @Test fun `mixed - matrix multiply element`() = roundTrip("rtm_matrix_multiply_element",
        posArgs(listOf(listOf(listOf(1, 2), listOf(3, 4)), listOf(listOf(5, 6), listOf(7, 8)), 0, 0)))

    @Test fun `mixed - histogram`() = roundTrip("rtm_histogram",
        posArgs(listOf(listOf(1, 4, 7, 2, 5, 8, 3, 6, 9), 3, 0, 10)))

    @Test fun `mixed - count inversions`() = roundTrip("rtm_count_inversions",
        posArgs(listOf(listOf(2, 4, 1, 3, 5)), listOf(listOf(1, 2, 3)), listOf(listOf(3, 2, 1))))

    @Test fun `mixed - zigzag`() = roundTrip("rtm_zigzag",
        posArgs(listOf(listOf(4, 3, 7, 8, 6, 2, 1)), listOf(listOf(1, 2, 3))))

    @Test fun `mixed - max profit`() = roundTrip("rtm_max_profit",
        posArgs(listOf(listOf(7, 1, 5, 3, 6, 4)), listOf(listOf(7, 6, 4, 3, 1)), listOf(listOf(1))))

    @Test fun `mixed - trap water`() = roundTrip("rtm_trap_water",
        posArgs(listOf(listOf(0, 1, 0, 2, 1, 0, 1, 3, 2, 1, 2, 1)), listOf(listOf(3, 0, 3))))

    @Test fun `mixed - valid sudoku row`() = roundTrip("rtm_valid_sudoku_row",
        posArgs(listOf(listOf(1, 2, 3, 4, 5)), listOf(listOf(1, 2, 1)), listOf(listOf(0, 0, 1, 2))))

    @Test fun `mixed - encode decode`() = roundTrip("rtm_encode_decode",
        posArgs(listOf(listOf(1, 2, 3), 5), listOf(listOf(10, 20, 30), -3)))

    @Test fun `mixed - life step`() = roundTrip("rtm_life_step",
        posArgs(listOf(listOf(0, 1, 0, 1, 1, 0)), listOf(listOf(1, 1, 1))))

    @Test fun `mixed - compress list`() = roundTrip("rtm_compress_list",
        posArgs(listOf(listOf(1, 1, 2, 2, 3, 1)), listOf(emptyList<Int>()), listOf(listOf(1))))

    @Test fun `mixed - alternating sum`() = roundTrip("rtm_alternating_sum",
        posArgs(listOf(listOf(1, 2, 3, 4)), listOf(listOf(5)), listOf(emptyList<Int>())))

    @Test fun `mixed - equilibrium index`() = roundTrip("rtm_equilibrium_index",
        posArgs(listOf(listOf(-7, 1, 5, 2, -4, 3, 0)), listOf(listOf(1, 2, 3))))

    @Test fun `mixed - leaders`() = roundTrip("rtm_leaders",
        posArgs(listOf(listOf(16, 17, 4, 3, 5, 2)), listOf(listOf(1)), listOf(emptyList<Int>())))

    @Test fun `mixed - wave sort`() = roundTrip("rtm_wave_sort",
        posArgs(listOf(listOf(3, 1, 4, 1, 5)), listOf(listOf(1, 2))))

    @Test fun `mixed - range sum queries`() = roundTrip("rtm_range_sum_queries",
        posArgs(listOf(listOf(1, 2, 3, 4, 5), listOf(listOf(0, 2), listOf(1, 4)))))

    @Test fun `mixed - max subarray len`() = roundTrip("rtm_max_subarray_len",
        posArgs(listOf(listOf(1, -1, 5, -2, 3), 3), listOf(listOf(1, 2, 3), 6)))

    @Test fun `mixed - count plateaus`() = roundTrip("rtm_count_plateaus",
        posArgs(listOf(listOf(1, 2, 2, 1)), listOf(listOf(1, 2, 3)), listOf(listOf(1, 1, 1))))

    @Test fun `mixed - simulate queue`() = roundTrip("rtm_simulate_queue",
        posArgs(listOf(listOf(1, 2, 3, -1, -1, 4, -1))))

    @Test fun `mixed - look and say step`() = roundTrip("rtm_look_and_say_step",
        posArgs(listOf("1"), listOf("11"), listOf("21"), listOf("")))

    @Test fun `mixed - peaks`() = roundTrip("rtm_peaks",
        posArgs(listOf(listOf(1, 3, 2, 5, 1)), listOf(listOf(1, 2, 3))))

    @Test fun `mixed - valleys`() = roundTrip("rtm_valleys",
        posArgs(listOf(listOf(3, 1, 2, 0, 4)), listOf(listOf(3, 2, 1))))

    @Test fun `mixed - pascal triangle flat`() = roundTrip("rtm_pascal_triangle_flat",
        posArgs(listOf(0), listOf(1), listOf(3)))

    @Test fun `mixed - collatz sequence`() = roundTrip("rtm_collatz_sequence",
        posArgs(listOf(1), listOf(3), listOf(6)))

    @Test fun `mixed - digital root`() = roundTrip("rtm_digital_root",
        posArgs(listOf(0), listOf(5), listOf(38), listOf(493193)))

    @Test fun `mixed - happy number`() = roundTrip("rtm_happy_number",
        posArgs(listOf(1), listOf(7), listOf(19), listOf(2), listOf(4)))

    @Test fun `mixed - matrix diagonal sum`() = roundTrip("rtm_matrix_diagonal_sum",
        posArgs(listOf(listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)))))
}
