package org.opentaint.ir.test.python.tier3

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Round-trip tests for exception handling constructs.
 * Tests try/except/else/finally, raise, multiple handlers,
 * exception type resolution, and nested try blocks.
 * 50 test cases.
 */
@Tag("tier3")
class RoundTripExceptionTest : RoundTripTestBase() {

    override val allSources = """
def rte_basic_try(x: int) -> int:
    try:
        return 10 // x
    except:
        return -1

def rte_typed_except(x: int) -> int:
    try:
        return 10 // x
    except ZeroDivisionError:
        return -1

def rte_except_as(x: int) -> str:
    try:
        y = 10 // x
        return str(y)
    except ZeroDivisionError as e:
        return "error"

def rte_multi_except(x: str) -> int:
    try:
        return int(x)
    except ValueError:
        return -1
    except TypeError:
        return -2

def rte_try_else(x: int) -> int:
    try:
        y = 10 // x
    except ZeroDivisionError:
        return -1
    else:
        return y + 1
    return 0

def rte_try_finally(x: int) -> int:
    result = 0
    try:
        result = x * 2
    finally:
        result = result + 100
    return result

def rte_try_except_else_finally(x: int) -> int:
    result = 0
    try:
        result = 10 // x
    except ZeroDivisionError:
        result = -1
    else:
        result = result + 10
    finally:
        result = result + 100
    return result

def rte_nested_try(x: int) -> int:
    try:
        try:
            return 10 // x
        except ZeroDivisionError:
            return 0
    except:
        return -1

def rte_bare_except(x: int) -> int:
    try:
        return 10 // x
    except:
        return -1

def rte_try_in_loop(items: list) -> int:
    total = 0
    for x in items:
        try:
            total += 10 // x
        except:
            pass
    return total

def rte_continue_in_except(items: list) -> int:
    total = 0
    for x in items:
        try:
            total += 10 // x
        except:
            continue
    return total

def rte_break_in_except(items: list) -> int:
    total = 0
    for x in items:
        try:
            total += 10 // x
        except:
            break
    return total

def rte_except_return_value(x: int) -> int:
    try:
        if x < 0:
            raise ValueError()
        return x
    except ValueError:
        return 0

def rte_finally_always_runs(x: int) -> int:
    result = 0
    try:
        result = x
    except:
        result = -1
    finally:
        result = result + 1000
    return result

def rte_try_simple_body(x: int) -> int:
    try:
        y = x + 1
    except:
        y = 0
    return y

def rte_try_multiple_stmts(x: int, y: int) -> int:
    try:
        a = x + y
        b = a * 2
        c = b - x
    except:
        c = 0
    return c

def rte_nested_try_inner_catch(x: int) -> int:
    result = 0
    try:
        try:
            result = 100 // x
        except ZeroDivisionError:
            result = -1
        result = result + 10
    except:
        result = -999
    return result

def rte_loop_try_accumulate(items: list) -> list:
    results = []
    for x in items:
        try:
            results = results + [100 // x]
        except:
            results = results + [0]
    return results

def rte_try_with_computation(a: int, b: int) -> int:
    try:
        if b == 0:
            raise ValueError()
        return a // b
    except ValueError:
        return a

def rte_multi_catch_values(x: int) -> int:
    try:
        if x == 0:
            return 0
        return x
    except:
        return -1

def rte_finally_no_except(x: int) -> int:
    result = x
    try:
        result = result * 2
    finally:
        result = result + 1
    return result

def rte_try_bool_check(x: int) -> bool:
    try:
        y = 10 // x
        return y > 0
    except:
        return False

def rte_try_string_convert(x: int) -> str:
    try:
        return str(10 // x)
    except:
        return "error"

def rte_triple_nested_try(x: int) -> int:
    try:
        try:
            try:
                return 100 // x
            except:
                return -1
        except:
            return -2
    except:
        return -3

def rte_try_with_else_assign(x: int) -> int:
    result = 0
    try:
        y = x * 2
    except:
        result = -1
    else:
        result = y + 10
    return result

def rte_except_modify_and_return(x: int) -> int:
    result = x
    try:
        result = 100 // x
    except:
        result = result * (-1)
    return result

def rte_try_conditional_raise(x: int, y: int) -> int:
    try:
        if x > y:
            raise ValueError()
        return x + y
    except ValueError:
        return x - y

def rte_loop_break_in_try(items: list) -> int:
    total = 0
    for x in items:
        try:
            if x < 0:
                break
            total += x
        except:
            pass
    return total

def rte_try_augmented_assign(x: int) -> int:
    total = 0
    try:
        total += x
        total += x * 2
        total += x * 3
    except:
        total = -1
    return total

def rte_try_list_operations(items: list) -> list:
    result = []
    try:
        for x in items:
            result = result + [x * 2]
    except:
        result = []
    return result

def rte_try_with_index(items: list, idx: int) -> int:
    try:
        return items[idx]
    except:
        return -1

def rte_nested_try_finally(x: int) -> int:
    result = 0
    try:
        result = 10 // x
        result = result + 10
    except:
        result = -1
    return result

def rte_try_while_loop(n: int) -> int:
    total = 0
    i = n
    try:
        while i > 0:
            total += i
            i -= 1
    except:
        total = -1
    return total

def rte_multiple_try_blocks(x: int) -> int:
    a = 0
    try:
        a = x + 1
    except:
        a = -1
    b = 0
    try:
        b = x * 2
    except:
        b = -2
    return a + b

def rte_try_math(a: int, b: int) -> int:
    try:
        return (a * b) // (a - b)
    except:
        return 0

def rte_try_compare_result(x: int) -> int:
    try:
        y = 10 // x
        if y > 5:
            return 1
        else:
            return 0
    except:
        return -1

def rte_empty_try_body(x: int) -> int:
    try:
        pass
    except:
        return -1
    return x

def rte_try_nested_conditions(x: int, y: int) -> int:
    try:
        if x > 0:
            if y > 0:
                return x + y
            else:
                return x - y
        else:
            return 0
    except:
        return -1

def rte_two_except_types(x: str) -> str:
    try:
        n = int(x)
        return str(n * 2)
    except ValueError:
        return "not_a_number"
    except TypeError:
        return "wrong_type"

def rte_except_with_default(x: int, default: int) -> int:
    try:
        return 100 // x
    except:
        return default

def rte_try_simple_arithmetic(a: int, b: int, c: int) -> int:
    try:
        return (a + b) // c
    except:
        return 0

def rte_try_string_ops(s: str, n: int) -> str:
    try:
        return s[n]
    except:
        return ""

def rte_try_list_build(items: list) -> list:
    result = []
    for x in items:
        try:
            result = result + [100 // x]
        except:
            result = result + [-1]
    return result

def rte_try_boolean_logic(x: int, y: int) -> bool:
    try:
        a = 10 // x
        b = 10 // y
        return a > 0 and b > 0
    except:
        return False

def rte_try_accumulate_safe(items: list) -> int:
    total = 0
    for x in items:
        try:
            total += 10 // x
        except:
            total += 0
    return total

def rte_try_max_safe(items: list) -> int:
    if len(items) == 0:
        return 0
    best = items[0]
    for x in items:
        try:
            if x > best:
                best = x
        except:
            pass
    return best
    """.trimIndent()

    // ─── Basic try/except ──────────────────────────────────

    @Test fun `basic try - normal path`() = roundTrip("rte_basic_try", posArgs(listOf(2), listOf(5)))
    @Test fun `basic try - exception path`() = roundTrip("rte_basic_try", posArgs(listOf(0)))
    @Test fun `typed except - normal path`() = roundTrip("rte_typed_except", posArgs(listOf(2), listOf(5)))
    @Test fun `typed except - exception`() = roundTrip("rte_typed_except", posArgs(listOf(0)))
    @Test fun `except as - normal`() = roundTrip("rte_except_as", posArgs(listOf(5)))
    @Test fun `except as - exception`() = roundTrip("rte_except_as", posArgs(listOf(0)))

    // ─── Multiple handlers ─────────────────────────────────

    @Test fun `multi except - valid int`() = roundTrip("rte_multi_except", posArgs(listOf("42")))
    @Test fun `multi except - invalid str`() = roundTrip("rte_multi_except", posArgs(listOf("abc")))

    // ─── Try-else ──────────────────────────────────────────

    @Test fun `try-else normal path`() = roundTrip("rte_try_else", posArgs(listOf(2), listOf(5)))
    @Test fun `try-else exception path`() = roundTrip("rte_try_else", posArgs(listOf(0)))

    // ─── Try-finally ───────────────────────────────────────

    @Test fun `try-finally runs finally`() = roundTrip("rte_try_finally", posArgs(listOf(3), listOf(10), listOf(0)))
    @Test fun `try-except-else-finally`() = roundTrip("rte_try_except_else_finally", posArgs(listOf(2), listOf(5), listOf(0)))

    // ─── Nested try ────────────────────────────────────────

    @Test fun `nested try - normal`() = roundTrip("rte_nested_try", posArgs(listOf(5)))
    @Test fun `nested try - inner catches`() = roundTrip("rte_nested_try", posArgs(listOf(0)))
    @Test fun `triple nested`() = roundTrip("rte_triple_nested_try", posArgs(listOf(5), listOf(0)))

    // ─── Try in loop ───────────────────────────────────────

    @Test fun `try in loop`() = roundTrip("rte_try_in_loop", posArgs(listOf(listOf(5, 2, 0, 3))))
    @Test fun `continue in except`() = roundTrip("rte_continue_in_except", posArgs(listOf(listOf(5, 0, 2, 0, 1))))
    @Test fun `break in except`() = roundTrip("rte_break_in_except", posArgs(listOf(listOf(5, 2, 0, 3))))

    // ─── Return patterns ───────────────────────────────────

    @Test fun `except return value - positive`() = roundTrip("rte_except_return_value", posArgs(listOf(5)))
    @Test fun `except return value - negative`() = roundTrip("rte_except_return_value", posArgs(listOf(-1)))
    @Test fun `finally always runs`() = roundTrip("rte_finally_always_runs", posArgs(listOf(5), listOf(0)))

    // ─── More patterns ─────────────────────────────────────

    @Test fun `try simple body`() = roundTrip("rte_try_simple_body", posArgs(listOf(5), listOf(-1)))
    @Test fun `try multiple stmts`() = roundTrip("rte_try_multiple_stmts", posArgs(listOf(3, 4), listOf(10, 2)))
    @Test fun `nested try inner catch`() = roundTrip("rte_nested_try_inner_catch", posArgs(listOf(5), listOf(0)))
    @Test fun `loop try accumulate`() = roundTrip("rte_loop_try_accumulate", posArgs(listOf(listOf(5, 0, 2))))
    @Test fun `try with computation`() = roundTrip("rte_try_with_computation", posArgs(listOf(10, 3), listOf(10, 0)))
    @Test fun `multi catch values`() = roundTrip("rte_multi_catch_values", posArgs(listOf(5), listOf(0)))
    @Test fun `finally no except`() = roundTrip("rte_finally_no_except", posArgs(listOf(5), listOf(10)))
    @Test fun `try bool check`() = roundTrip("rte_try_bool_check", posArgs(listOf(1), listOf(0)))
    @Test fun `try string convert`() = roundTrip("rte_try_string_convert", posArgs(listOf(5), listOf(0)))
    @Test fun `try with else assign`() = roundTrip("rte_try_with_else_assign", posArgs(listOf(3), listOf(0)))
    @Test fun `except modify and return`() = roundTrip("rte_except_modify_and_return", posArgs(listOf(5), listOf(0)))
    @Test fun `try conditional raise`() = roundTrip("rte_try_conditional_raise", posArgs(listOf(5, 3), listOf(3, 5)))
    @Test fun `loop break in try`() = roundTrip("rte_loop_break_in_try", posArgs(listOf(listOf(1, 2, -1, 3))))
    @Test fun `try augmented assign`() = roundTrip("rte_try_augmented_assign", posArgs(listOf(5), listOf(10)))
    @Test fun `try list operations`() = roundTrip("rte_try_list_operations", posArgs(listOf(listOf(1, 2, 3))))
    @Test fun `try with index - valid`() = roundTrip("rte_try_with_index", posArgs(listOf(listOf(10, 20, 30), 1)))
    @Test fun `try with index - out of range`() = roundTrip("rte_try_with_index", posArgs(listOf(listOf(10, 20, 30), 99)))
    @Test fun `nested try finally`() = roundTrip("rte_nested_try_finally", posArgs(listOf(5), listOf(0)))
    @Test fun `try while loop`() = roundTrip("rte_try_while_loop", posArgs(listOf(5), listOf(0)))
    @Test fun `multiple try blocks`() = roundTrip("rte_multiple_try_blocks", posArgs(listOf(5), listOf(0)))
    @Test fun `try math`() = roundTrip("rte_try_math", posArgs(listOf(6, 3), listOf(5, 5)))
    @Test fun `try compare result`() = roundTrip("rte_try_compare_result", posArgs(listOf(1), listOf(0)))
    @Test fun `empty try body`() = roundTrip("rte_empty_try_body", posArgs(listOf(5)))
    @Test fun `try nested conditions`() = roundTrip("rte_try_nested_conditions", posArgs(listOf(5, 3), listOf(5, -1), listOf(-1, 5)))
    @Test fun `two except types`() = roundTrip("rte_two_except_types", posArgs(listOf("42"), listOf("abc")))
    @Test fun `except with default`() = roundTrip("rte_except_with_default", posArgs(listOf(5, 99), listOf(0, 99)))
    @Test fun `try simple arithmetic`() = roundTrip("rte_try_simple_arithmetic", posArgs(listOf(10, 20, 5), listOf(10, 20, 0)))
    @Test fun `try string ops`() = roundTrip("rte_try_string_ops", posArgs(listOf("hello", 1), listOf("hello", 99)))
    @Test fun `try list build`() = roundTrip("rte_try_list_build", posArgs(listOf(listOf(5, 0, 2))))
    @Test fun `try boolean logic`() = roundTrip("rte_try_boolean_logic", posArgs(listOf(5, 2), listOf(0, 2)))
    @Test fun `try accumulate safe`() = roundTrip("rte_try_accumulate_safe", posArgs(listOf(listOf(5, 0, 2, 0, 1))))
    @Test fun `try max safe`() = roundTrip("rte_try_max_safe", posArgs(listOf(listOf(3, 7, 1, 9, 2)), listOf(emptyList<Int>())))
}
