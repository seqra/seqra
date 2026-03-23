package org.opentaint.ir.test.python.tier3

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Round-trip tests for the walrus operator (:= assignment expression).
 * Tests walrus in if, while, nested, and compound expressions.
 * 30 test cases.
 */
@Tag("tier3")
class RoundTripWalrusTest : RoundTripTestBase() {

    override val allSources = """
def rtw_simple_if(x: int) -> int:
    if (n := x + 1) > 5:
        return n
    return 0

def rtw_simple_assign(x: int) -> int:
    y = (z := x * 2) + z
    return y

def rtw_reuse(x: int) -> int:
    return (n := x * 3) + n

def rtw_in_condition(x: int, y: int) -> int:
    if (s := x + y) > 10:
        return s * 2
    return s

def rtw_nested(x: int) -> int:
    a = (b := (c := x + 1) + 2) + 3
    return a + b + c

def rtw_multiple(x: int, y: int) -> int:
    a = (p := x + 1) + (q := y + 1)
    return a + p + q

def rtw_in_ternary(x: int) -> int:
    return (n := x * 2) if x > 0 else 0

def rtw_walrus_add(a: int, b: int) -> int:
    total = (s := a + b) + s
    return total

def rtw_walrus_mul(a: int, b: int) -> int:
    result = (p := a * b) * 2
    return result + p

def rtw_chain_ops(x: int) -> int:
    a = (b := x + 1) * 2
    c = b + a
    return c

def rtw_walrus_compare(x: int) -> bool:
    return (y := x + 5) > 10

def rtw_walrus_conditional_use(x: int) -> int:
    if (val := x * 2) > 20:
        return val + 100
    elif val > 10:
        return val + 50
    else:
        return val

def rtw_walrus_in_arithmetic(a: int, b: int) -> int:
    return (x := a + b) * (y := a - b) + x + y

def rtw_walrus_negative(x: int) -> int:
    return (n := -x) + n

def rtw_walrus_sequential(x: int) -> int:
    a = (b := x + 1)
    c = b + a
    return c

def rtw_walrus_with_zero(x: int) -> int:
    if (n := x) == 0:
        return -1
    return n

def rtw_walrus_double(x: int) -> int:
    y = (n := x) + n
    return y

def rtw_walrus_triple_use(x: int) -> int:
    return (n := x + 1) + n + n

def rtw_walrus_bool_result(x: int) -> int:
    if (big := x > 100):
        return 1
    return 0

def rtw_walrus_subtract(a: int, b: int) -> int:
    return (d := a - b) + d * 2

def rtw_walrus_floor_div(a: int, b: int) -> int:
    return (q := a // b) + q

def rtw_walrus_mod(a: int, b: int) -> int:
    return (r := a % b) * 2

def rtw_walrus_complex_expr(x: int, y: int) -> int:
    result = (a := x + y) * (b := x - y)
    return result + a - b

def rtw_walrus_in_loop_body(n: int) -> int:
    total = 0
    i = 0
    while i < n:
        total += (inc := i + 1)
        i = inc
    return total

def rtw_walrus_chained_if(x: int) -> int:
    if (a := x + 1) > 0:
        if (b := a + 1) > 5:
            return b
        return a
    return 0

def rtw_walrus_sum_pair(x: int, y: int) -> int:
    return (s := x + y) + s + s

def rtw_walrus_min_val(a: int, b: int) -> int:
    return (m := a if a < b else b) + m

def rtw_walrus_square(x: int) -> int:
    return (sq := x * x) + sq

def rtw_walrus_cube(x: int) -> int:
    return (cu := x * x * x) + cu

def rtw_walrus_abs(x: int) -> int:
    return (a := x if x >= 0 else -x) * 2
    """.trimIndent()

    // ─── Basic walrus ──────────────────────────────────────

    @Test fun `walrus in if - above threshold`() = roundTrip("rtw_simple_if", posArgs(listOf(10), listOf(5)))
    @Test fun `walrus in if - below threshold`() = roundTrip("rtw_simple_if", posArgs(listOf(3), listOf(0)))
    @Test fun `walrus simple assign`() = roundTrip("rtw_simple_assign", posArgs(listOf(5), listOf(10)))
    @Test fun `walrus reuse`() = roundTrip("rtw_reuse", posArgs(listOf(3), listOf(7)))
    @Test fun `walrus in condition`() = roundTrip("rtw_in_condition", posArgs(listOf(5, 6), listOf(3, 4)))
    @Test fun `walrus nested`() = roundTrip("rtw_nested", posArgs(listOf(1), listOf(5)))
    @Test fun `walrus multiple`() = roundTrip("rtw_multiple", posArgs(listOf(3, 4), listOf(10, 20)))
    @Test fun `walrus in ternary`() = roundTrip("rtw_in_ternary", posArgs(listOf(5), listOf(-3)))

    // ─── Arithmetic patterns ───────────────────────────────

    @Test fun `walrus add`() = roundTrip("rtw_walrus_add", posArgs(listOf(3, 4), listOf(10, 20)))
    @Test fun `walrus mul`() = roundTrip("rtw_walrus_mul", posArgs(listOf(3, 4)))
    @Test fun `walrus chain ops`() = roundTrip("rtw_chain_ops", posArgs(listOf(5), listOf(10)))
    @Test fun `walrus compare`() = roundTrip("rtw_walrus_compare", posArgs(listOf(10), listOf(3)))
    @Test fun `walrus conditional use`() = roundTrip("rtw_walrus_conditional_use", posArgs(listOf(20), listOf(7), listOf(2)))
    @Test fun `walrus in arithmetic`() = roundTrip("rtw_walrus_in_arithmetic", posArgs(listOf(5, 3)))
    @Test fun `walrus negative`() = roundTrip("rtw_walrus_negative", posArgs(listOf(5), listOf(-3)))

    // ─── Sequential and repeated use ───────────────────────

    @Test fun `walrus sequential`() = roundTrip("rtw_walrus_sequential", posArgs(listOf(5)))
    @Test fun `walrus with zero`() = roundTrip("rtw_walrus_with_zero", posArgs(listOf(0), listOf(5)))
    @Test fun `walrus double`() = roundTrip("rtw_walrus_double", posArgs(listOf(7)))
    @Test fun `walrus triple use`() = roundTrip("rtw_walrus_triple_use", posArgs(listOf(4)))
    @Test fun `walrus bool result`() = roundTrip("rtw_walrus_bool_result", posArgs(listOf(200), listOf(50)))

    // ─── More arithmetic ───────────────────────────────────

    @Test fun `walrus subtract`() = roundTrip("rtw_walrus_subtract", posArgs(listOf(10, 3)))
    @Test fun `walrus floor div`() = roundTrip("rtw_walrus_floor_div", posArgs(listOf(17, 3)))
    @Test fun `walrus mod`() = roundTrip("rtw_walrus_mod", posArgs(listOf(17, 5)))
    @Test fun `walrus complex expr`() = roundTrip("rtw_walrus_complex_expr", posArgs(listOf(5, 3)))

    // ─── In control flow ───────────────────────────────────

    @Test fun `walrus in loop body`() = roundTrip("rtw_walrus_in_loop_body", posArgs(listOf(5), listOf(10)))
    @Test fun `walrus chained if`() = roundTrip("rtw_walrus_chained_if", posArgs(listOf(10), listOf(2), listOf(-1)))
    @Test fun `walrus sum pair`() = roundTrip("rtw_walrus_sum_pair", posArgs(listOf(3, 4)))
    @Test fun `walrus min val`() = roundTrip("rtw_walrus_min_val", posArgs(listOf(3, 7), listOf(7, 3)))
    @Test fun `walrus square`() = roundTrip("rtw_walrus_square", posArgs(listOf(5), listOf(-3)))
    @Test fun `walrus cube`() = roundTrip("rtw_walrus_cube", posArgs(listOf(3)))
    @Test fun `walrus abs`() = roundTrip("rtw_walrus_abs", posArgs(listOf(5), listOf(-5)))
}
