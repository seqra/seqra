package org.opentaint.ir.test.python.tier3

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Round-trip tests for arithmetic and numeric operations.
 * 55 test cases covering basic math, integer operations, and numeric algorithms.
 */
@Tag("tier3")
class RoundTripArithmeticTest : RoundTripTestBase() {

    override val allSources = """
def rta_add(a: int, b: int) -> int:
    return a + b

def rta_sub(a: int, b: int) -> int:
    return a - b

def rta_mul(a: int, b: int) -> int:
    return a * b

def rta_floor_div(a: int, b: int) -> int:
    return a // b

def rta_mod(a: int, b: int) -> int:
    return a % b

def rta_pow(a: int, b: int) -> int:
    return a ** b

def rta_neg(x: int) -> int:
    return -x

def rta_pos(x: int) -> int:
    return +x

def rta_abs_manual(x: int) -> int:
    if x < 0:
        return -x
    return x

def rta_max_of_two(a: int, b: int) -> int:
    if a >= b:
        return a
    return b

def rta_min_of_two(a: int, b: int) -> int:
    if a <= b:
        return a
    return b

def rta_clamp(x: int, lo: int, hi: int) -> int:
    if x < lo:
        return lo
    if x > hi:
        return hi
    return x

def rta_sum_range(n: int) -> int:
    total = 0
    i = 0
    while i < n:
        total = total + i
        i = i + 1
    return total

def rta_sum_squares(n: int) -> int:
    total = 0
    i = 0
    while i < n:
        total = total + i * i
        i = i + 1
    return total

def rta_factorial(n: int) -> int:
    result = 1
    i = 2
    while i <= n:
        result = result * i
        i = i + 1
    return result

def rta_fibonacci(n: int) -> int:
    if n <= 1:
        return n
    a = 0
    b = 1
    i = 2
    while i <= n:
        c = a + b
        a = b
        b = c
        i = i + 1
    return b

def rta_gcd(a: int, b: int) -> int:
    while b != 0:
        t = b
        b = a % b
        a = t
    return a

def rta_lcm(a: int, b: int) -> int:
    g = a
    r = b
    while r != 0:
        t = r
        r = g % r
        g = t
    return a * b // g

def rta_is_even(x: int) -> int:
    if x % 2 == 0:
        return 1
    return 0

def rta_is_odd(x: int) -> int:
    if x % 2 != 0:
        return 1
    return 0

def rta_sign(x: int) -> int:
    if x > 0:
        return 1
    if x < 0:
        return -1
    return 0

def rta_collatz_steps(n: int) -> int:
    steps = 0
    while n != 1:
        if n % 2 == 0:
            n = n // 2
        else:
            n = 3 * n + 1
        steps = steps + 1
    return steps

def rta_digit_sum(n: int) -> int:
    total = 0
    if n < 0:
        n = -n
    while n > 0:
        total = total + n % 10
        n = n // 10
    return total

def rta_count_digits(n: int) -> int:
    if n == 0:
        return 1
    if n < 0:
        n = -n
    count = 0
    while n > 0:
        count = count + 1
        n = n // 10
    return count

def rta_reverse_number(n: int) -> int:
    neg = 0
    if n < 0:
        neg = 1
        n = -n
    result = 0
    while n > 0:
        result = result * 10 + n % 10
        n = n // 10
    if neg == 1:
        return -result
    return result

def rta_is_palindrome_num(n: int) -> int:
    if n < 0:
        return 0
    original = n
    rev = 0
    while n > 0:
        rev = rev * 10 + n % 10
        n = n // 10
    if rev == original:
        return 1
    return 0

def rta_power_iterative(base: int, exp: int) -> int:
    result = 1
    i = 0
    while i < exp:
        result = result * base
        i = i + 1
    return result

def rta_triangle_number(n: int) -> int:
    return n * (n + 1) // 2

def rta_square(x: int) -> int:
    return x * x

def rta_cube(x: int) -> int:
    return x * x * x

def rta_average_two(a: int, b: int) -> int:
    return (a + b) // 2

def rta_distance(a: int, b: int) -> int:
    d = a - b
    if d < 0:
        d = -d
    return d

def rta_weighted_sum(a: int, b: int, wa: int, wb: int) -> int:
    return a * wa + b * wb

def rta_divmod_manual(a: int, b: int) -> list:
    q = a // b
    r = a % b
    return [q, r]

def rta_is_power_of_two(n: int) -> int:
    if n <= 0:
        return 0
    while n > 1:
        if n % 2 != 0:
            return 0
        n = n // 2
    return 1

def rta_count_factors(n: int) -> int:
    count = 0
    i = 1
    while i <= n:
        if n % i == 0:
            count = count + 1
        i = i + 1
    return count

def rta_is_prime(n: int) -> int:
    if n < 2:
        return 0
    i = 2
    while i * i <= n:
        if n % i == 0:
            return 0
        i = i + 1
    return 1

def rta_sum_divisors(n: int) -> int:
    total = 0
    i = 1
    while i < n:
        if n % i == 0:
            total = total + i
        i = i + 1
    return total

def rta_compound_expr(a: int, b: int, c: int) -> int:
    x = a + b * c
    y = (a + b) * c
    return x + y

def rta_multi_step(x: int) -> int:
    a = x + 1
    b = a * 2
    c = b - 3
    d = c + a
    e = d * b
    return e

def rta_swap_values(a: int, b: int) -> list:
    t = a
    a = b
    b = t
    return [a, b]

def rta_three_way_max(a: int, b: int, c: int) -> int:
    m = a
    if b > m:
        m = b
    if c > m:
        m = c
    return m

def rta_three_way_min(a: int, b: int, c: int) -> int:
    m = a
    if b < m:
        m = b
    if c < m:
        m = c
    return m

def rta_accumulate(items: list) -> list:
    result = []
    total = 0
    for x in items:
        total = total + x
        result = result + [total]
    return result

def rta_product_list(items: list) -> int:
    result = 1
    for x in items:
        result = result * x
    return result

def rta_dot_product(a: list, b: list) -> int:
    total = 0
    i = 0
    while i < len(a):
        total = total + a[i] * b[i]
        i = i + 1
    return total

def rta_mean_int(items: list) -> int:
    total = 0
    for x in items:
        total = total + x
    return total // len(items)

def rta_range_val(start: int, stop: int) -> list:
    result = []
    i = start
    while i < stop:
        result = result + [i]
        i = i + 1
    return result

def rta_sum_of_evens(items: list) -> int:
    total = 0
    for x in items:
        if x % 2 == 0:
            total = total + x
    return total

def rta_sum_of_odds(items: list) -> int:
    total = 0
    for x in items:
        if x % 2 != 0:
            total = total + x
    return total

def rta_count_in_range(items: list, lo: int, hi: int) -> int:
    count = 0
    for x in items:
        if x >= lo:
            if x <= hi:
                count = count + 1
    return count

def rta_bitwise_and(a: int, b: int) -> int:
    return a & b

def rta_bitwise_or(a: int, b: int) -> int:
    return a | b

def rta_bitwise_xor(a: int, b: int) -> int:
    return a ^ b

def rta_bitwise_invert(x: int) -> int:
    return ~x

def rta_left_shift(x: int, n: int) -> int:
    return x << n

def rta_right_shift(x: int, n: int) -> int:
    return x >> n
    """.trimIndent()

    // ─── Tests ───────────────────────────────────────────────

    @Test fun `arithmetic - add`() = roundTrip("rta_add",
        posArgs(listOf(1, 2), listOf(0, 0), listOf(-3, 5), listOf(100, -100)))

    @Test fun `arithmetic - sub`() = roundTrip("rta_sub",
        posArgs(listOf(5, 3), listOf(0, 0), listOf(-3, -5), listOf(1, 100)))

    @Test fun `arithmetic - mul`() = roundTrip("rta_mul",
        posArgs(listOf(3, 4), listOf(0, 5), listOf(-2, 3), listOf(7, 7)))

    @Test fun `arithmetic - floor div`() = roundTrip("rta_floor_div",
        posArgs(listOf(7, 2), listOf(10, 3), listOf(-7, 2), listOf(100, 7)))

    @Test fun `arithmetic - mod`() = roundTrip("rta_mod",
        posArgs(listOf(7, 3), listOf(10, 5), listOf(17, 4), listOf(100, 7)))

    @Test fun `arithmetic - pow`() = roundTrip("rta_pow",
        posArgs(listOf(2, 10), listOf(3, 3), listOf(5, 0), listOf(1, 100)))

    @Test fun `arithmetic - neg`() = roundTrip("rta_neg",
        posArgs(listOf(5), listOf(-3), listOf(0)))

    @Test fun `arithmetic - pos`() = roundTrip("rta_pos",
        posArgs(listOf(5), listOf(-3), listOf(0)))

    @Test fun `arithmetic - abs manual`() = roundTrip("rta_abs_manual",
        posArgs(listOf(5), listOf(-5), listOf(0)))

    @Test fun `arithmetic - max of two`() = roundTrip("rta_max_of_two",
        posArgs(listOf(3, 7), listOf(7, 3), listOf(5, 5), listOf(-1, -5)))

    @Test fun `arithmetic - min of two`() = roundTrip("rta_min_of_two",
        posArgs(listOf(3, 7), listOf(7, 3), listOf(5, 5), listOf(-1, -5)))

    @Test fun `arithmetic - clamp`() = roundTrip("rta_clamp",
        posArgs(listOf(5, 0, 10), listOf(-3, 0, 10), listOf(15, 0, 10), listOf(0, 0, 0)))

    @Test fun `arithmetic - sum range`() = roundTrip("rta_sum_range",
        posArgs(listOf(0), listOf(1), listOf(5), listOf(10)))

    @Test fun `arithmetic - sum squares`() = roundTrip("rta_sum_squares",
        posArgs(listOf(0), listOf(1), listOf(4), listOf(5)))

    @Test fun `arithmetic - factorial`() = roundTrip("rta_factorial",
        posArgs(listOf(0), listOf(1), listOf(5), listOf(7)))

    @Test fun `arithmetic - fibonacci`() = roundTrip("rta_fibonacci",
        posArgs(listOf(0), listOf(1), listOf(5), listOf(10)))

    @Test fun `arithmetic - gcd`() = roundTrip("rta_gcd",
        posArgs(listOf(12, 8), listOf(7, 3), listOf(100, 25), listOf(17, 17)))

    @Test fun `arithmetic - lcm`() = roundTrip("rta_lcm",
        posArgs(listOf(4, 6), listOf(3, 7), listOf(12, 18), listOf(5, 5)))

    @Test fun `arithmetic - is even`() = roundTrip("rta_is_even",
        posArgs(listOf(0), listOf(1), listOf(2), listOf(7), listOf(-4)))

    @Test fun `arithmetic - is odd`() = roundTrip("rta_is_odd",
        posArgs(listOf(0), listOf(1), listOf(2), listOf(7), listOf(-3)))

    @Test fun `arithmetic - sign`() = roundTrip("rta_sign",
        posArgs(listOf(5), listOf(-3), listOf(0)))

    @Test fun `arithmetic - collatz steps`() = roundTrip("rta_collatz_steps",
        posArgs(listOf(1), listOf(2), listOf(3), listOf(6), listOf(10)))

    @Test fun `arithmetic - digit sum`() = roundTrip("rta_digit_sum",
        posArgs(listOf(0), listOf(123), listOf(999), listOf(-456)))

    @Test fun `arithmetic - count digits`() = roundTrip("rta_count_digits",
        posArgs(listOf(0), listOf(5), listOf(123), listOf(-999), listOf(10000)))

    @Test fun `arithmetic - reverse number`() = roundTrip("rta_reverse_number",
        posArgs(listOf(123), listOf(100), listOf(0), listOf(-456)))

    @Test fun `arithmetic - is palindrome num`() = roundTrip("rta_is_palindrome_num",
        posArgs(listOf(121), listOf(123), listOf(0), listOf(1), listOf(-121), listOf(1221)))

    @Test fun `arithmetic - power iterative`() = roundTrip("rta_power_iterative",
        posArgs(listOf(2, 0), listOf(2, 8), listOf(3, 4), listOf(1, 50)))

    @Test fun `arithmetic - triangle number`() = roundTrip("rta_triangle_number",
        posArgs(listOf(0), listOf(1), listOf(5), listOf(10), listOf(100)))

    @Test fun `arithmetic - square`() = roundTrip("rta_square",
        posArgs(listOf(0), listOf(3), listOf(-4), listOf(7)))

    @Test fun `arithmetic - cube`() = roundTrip("rta_cube",
        posArgs(listOf(0), listOf(2), listOf(-3), listOf(5)))

    @Test fun `arithmetic - average two`() = roundTrip("rta_average_two",
        posArgs(listOf(4, 6), listOf(0, 0), listOf(7, 8), listOf(-3, 5)))

    @Test fun `arithmetic - distance`() = roundTrip("rta_distance",
        posArgs(listOf(5, 3), listOf(3, 5), listOf(-3, -7), listOf(0, 0)))

    @Test fun `arithmetic - weighted sum`() = roundTrip("rta_weighted_sum",
        posArgs(listOf(3, 5, 2, 4), listOf(0, 0, 1, 1), listOf(10, 20, 1, 2)))

    @Test fun `arithmetic - divmod manual`() = roundTrip("rta_divmod_manual",
        posArgs(listOf(7, 3), listOf(10, 5), listOf(17, 4), listOf(100, 7)))

    @Test fun `arithmetic - is power of two`() = roundTrip("rta_is_power_of_two",
        posArgs(listOf(1), listOf(2), listOf(4), listOf(7), listOf(16), listOf(0), listOf(-4)))

    @Test fun `arithmetic - count factors`() = roundTrip("rta_count_factors",
        posArgs(listOf(1), listOf(6), listOf(12), listOf(7)))

    @Test fun `arithmetic - is prime`() = roundTrip("rta_is_prime",
        posArgs(listOf(0), listOf(1), listOf(2), listOf(3), listOf(4), listOf(7), listOf(13), listOf(15)))

    @Test fun `arithmetic - sum divisors`() = roundTrip("rta_sum_divisors",
        posArgs(listOf(1), listOf(6), listOf(12), listOf(7)))

    @Test fun `arithmetic - compound expr`() = roundTrip("rta_compound_expr",
        posArgs(listOf(1, 2, 3), listOf(0, 0, 0), listOf(5, -3, 2)))

    @Test fun `arithmetic - multi step`() = roundTrip("rta_multi_step",
        posArgs(listOf(0), listOf(5), listOf(-3), listOf(10)))

    @Test fun `arithmetic - swap values`() = roundTrip("rta_swap_values",
        posArgs(listOf(1, 2), listOf(0, 0), listOf(-3, 5)))

    @Test fun `arithmetic - three way max`() = roundTrip("rta_three_way_max",
        posArgs(listOf(1, 2, 3), listOf(3, 2, 1), listOf(5, 5, 5), listOf(-1, -2, -3)))

    @Test fun `arithmetic - three way min`() = roundTrip("rta_three_way_min",
        posArgs(listOf(1, 2, 3), listOf(3, 2, 1), listOf(5, 5, 5), listOf(-1, -2, -3)))

    @Test fun `arithmetic - accumulate`() = roundTrip("rta_accumulate",
        posArgs(listOf(listOf(1, 2, 3)), listOf(listOf(5)), listOf(emptyList<Int>())))

    @Test fun `arithmetic - product list`() = roundTrip("rta_product_list",
        posArgs(listOf(listOf(1, 2, 3, 4)), listOf(listOf(5)), listOf(listOf(2, 3, 5))))

    @Test fun `arithmetic - dot product`() = roundTrip("rta_dot_product",
        posArgs(listOf(listOf(1, 2, 3), listOf(4, 5, 6)), listOf(listOf(0, 0), listOf(1, 1))))

    @Test fun `arithmetic - mean int`() = roundTrip("rta_mean_int",
        posArgs(listOf(listOf(2, 4, 6)), listOf(listOf(1, 2, 3, 4)), listOf(listOf(10))))

    @Test fun `arithmetic - range val`() = roundTrip("rta_range_val",
        posArgs(listOf(0, 5), listOf(3, 3), listOf(-2, 2)))

    @Test fun `arithmetic - sum of evens`() = roundTrip("rta_sum_of_evens",
        posArgs(listOf(listOf(1, 2, 3, 4, 5, 6)), listOf(listOf(1, 3, 5)), listOf(emptyList<Int>())))

    @Test fun `arithmetic - sum of odds`() = roundTrip("rta_sum_of_odds",
        posArgs(listOf(listOf(1, 2, 3, 4, 5, 6)), listOf(listOf(2, 4, 6)), listOf(emptyList<Int>())))

    @Test fun `arithmetic - count in range`() = roundTrip("rta_count_in_range",
        posArgs(listOf(listOf(1, 5, 10, 15, 20), 5, 15), listOf(listOf(1, 2, 3), 0, 0)))

    @Test fun `arithmetic - bitwise and`() = roundTrip("rta_bitwise_and",
        posArgs(listOf(0b1100, 0b1010), listOf(0xFF, 0x0F), listOf(0, 255)))

    @Test fun `arithmetic - bitwise or`() = roundTrip("rta_bitwise_or",
        posArgs(listOf(0b1100, 0b1010), listOf(0, 0), listOf(255, 0)))

    @Test fun `arithmetic - bitwise xor`() = roundTrip("rta_bitwise_xor",
        posArgs(listOf(0b1100, 0b1010), listOf(255, 255), listOf(0, 0)))

    @Test fun `arithmetic - bitwise invert`() = roundTrip("rta_bitwise_invert",
        posArgs(listOf(0), listOf(1), listOf(-1), listOf(255)))

    @Test fun `arithmetic - left shift`() = roundTrip("rta_left_shift",
        posArgs(listOf(1, 0), listOf(1, 4), listOf(3, 2)))

    @Test fun `arithmetic - right shift`() = roundTrip("rta_right_shift",
        posArgs(listOf(16, 2), listOf(255, 4), listOf(1, 0)))
}
