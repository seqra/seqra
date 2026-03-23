package org.opentaint.ir.test.python.tier3

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Round-trip tests for conditionals and boolean logic.
 * 50 test cases covering if/elif/else, guard patterns, ternary-like, boolean ops.
 */
@Tag("tier3")
class RoundTripConditionalTest : RoundTripTestBase() {

    override val allSources = """
def rtc_simple_if(x: int) -> int:
    if x > 0:
        return 1
    return 0

def rtc_if_else(x: int) -> str:
    if x > 0:
        return "positive"
    else:
        return "non-positive"

def rtc_if_elif_else(x: int) -> str:
    if x > 0:
        return "positive"
    elif x < 0:
        return "negative"
    else:
        return "zero"

def rtc_multi_elif(x: int) -> str:
    if x > 100:
        return "huge"
    elif x > 50:
        return "large"
    elif x > 10:
        return "medium"
    elif x > 0:
        return "small"
    elif x == 0:
        return "zero"
    else:
        return "negative"

def rtc_nested_if(x: int, y: int) -> str:
    if x > 0:
        if y > 0:
            return "Q1"
        else:
            return "Q4"
    else:
        if y > 0:
            return "Q2"
        else:
            return "Q3"

def rtc_deeply_nested(a: int, b: int, c: int) -> str:
    if a > 0:
        if b > 0:
            if c > 0:
                return "all positive"
            else:
                return "c not positive"
        else:
            return "b not positive"
    else:
        return "a not positive"

def rtc_guard_clause(x: int) -> str:
    if x < 0:
        return "negative"
    if x == 0:
        return "zero"
    if x > 100:
        return "big"
    return "normal"

def rtc_and_logic(a: int, b: int) -> int:
    if a > 0 and b > 0:
        return 1
    return 0

def rtc_or_logic(a: int, b: int) -> int:
    if a > 0 or b > 0:
        return 1
    return 0

def rtc_not_logic(x: int) -> int:
    if not x > 0:
        return 1
    return 0

def rtc_complex_and_or(a: int, b: int, c: int) -> int:
    if (a > 0 and b > 0) or c > 0:
        return 1
    return 0

def rtc_and_chain(a: int, b: int, c: int) -> int:
    if a > 0 and b > 0 and c > 0:
        return 1
    return 0

def rtc_or_chain(a: int, b: int, c: int) -> int:
    if a > 0 or b > 0 or c > 0:
        return 1
    return 0

def rtc_ternary_like(x: int) -> int:
    result = 1 if x > 0 else 0
    return result

def rtc_ternary_nested(x: int) -> str:
    result = "positive" if x > 0 else ("negative" if x < 0 else "zero")
    return result

def rtc_ternary_in_expr(x: int) -> int:
    bonus = 10 if x > 50 else 0
    return x + bonus

def rtc_equal(a: int, b: int) -> int:
    if a == b:
        return 1
    return 0

def rtc_not_equal(a: int, b: int) -> int:
    if a != b:
        return 1
    return 0

def rtc_less_than(a: int, b: int) -> int:
    if a < b:
        return 1
    return 0

def rtc_less_equal(a: int, b: int) -> int:
    if a <= b:
        return 1
    return 0

def rtc_greater_than(a: int, b: int) -> int:
    if a > b:
        return 1
    return 0

def rtc_greater_equal(a: int, b: int) -> int:
    if a >= b:
        return 1
    return 0

def rtc_classify_char(c: str) -> str:
    n = ord(c)
    if n >= 48:
        if n <= 57:
            return "digit"
    if n >= 65:
        if n <= 90:
            return "upper"
    if n >= 97:
        if n <= 122:
            return "lower"
    return "other"

def rtc_grade(score: int) -> str:
    if score >= 90:
        return "A"
    elif score >= 80:
        return "B"
    elif score >= 70:
        return "C"
    elif score >= 60:
        return "D"
    else:
        return "F"

def rtc_fizzbuzz(n: int) -> str:
    if n % 15 == 0:
        return "FizzBuzz"
    elif n % 3 == 0:
        return "Fizz"
    elif n % 5 == 0:
        return "Buzz"
    else:
        return "other"

def rtc_leap_year(year: int) -> int:
    if year % 400 == 0:
        return 1
    if year % 100 == 0:
        return 0
    if year % 4 == 0:
        return 1
    return 0

def rtc_absolute_diff(a: int, b: int) -> int:
    if a > b:
        return a - b
    else:
        return b - a

def rtc_median_of_three(a: int, b: int, c: int) -> int:
    if a <= b:
        if b <= c:
            return b
        elif a <= c:
            return c
        else:
            return a
    else:
        if a <= c:
            return a
        elif b <= c:
            return c
        else:
            return b

def rtc_sort_two(a: int, b: int) -> list:
    if a <= b:
        return [a, b]
    return [b, a]

def rtc_sort_three(a: int, b: int, c: int) -> list:
    x = a
    y = b
    z = c
    if x > y:
        t = x
        x = y
        y = t
    if y > z:
        t = y
        y = z
        z = t
    if x > y:
        t = x
        x = y
        y = t
    return [x, y, z]

def rtc_bmi_category(weight: int, height: int) -> str:
    bmi = weight * 10000 // (height * height)
    if bmi < 18:
        return "underweight"
    elif bmi < 25:
        return "normal"
    elif bmi < 30:
        return "overweight"
    else:
        return "obese"

def rtc_if_return_or_assign(x: int) -> int:
    if x > 10:
        result = x * 2
    else:
        result = x + 5
    return result

def rtc_multiple_conditions(a: int, b: int) -> str:
    result = ""
    if a > 0:
        result = result + "a+"
    if b > 0:
        result = result + "b+"
    if a > 0 and b > 0:
        result = result + "both"
    if a <= 0 and b <= 0:
        result = result + "neither"
    return result

def rtc_cascading_assign(x: int) -> int:
    y = 0
    if x > 100:
        y = 100
    elif x > 50:
        y = 50
    elif x > 25:
        y = 25
    elif x > 10:
        y = 10
    return y

def rtc_flag_accumulate(items: list) -> str:
    has_pos = 0
    has_neg = 0
    has_zero = 0
    for x in items:
        if x > 0:
            has_pos = 1
        elif x < 0:
            has_neg = 1
        else:
            has_zero = 1
    result = ""
    if has_pos == 1:
        result = result + "P"
    if has_neg == 1:
        result = result + "N"
    if has_zero == 1:
        result = result + "Z"
    return result

def rtc_all_positive(items: list) -> int:
    for x in items:
        if x <= 0:
            return 0
    return 1

def rtc_any_negative(items: list) -> int:
    for x in items:
        if x < 0:
            return 1
    return 0

def rtc_none_zero(items: list) -> int:
    for x in items:
        if x == 0:
            return 0
    return 1

def rtc_count_categories(items: list) -> list:
    pos = 0
    neg = 0
    zero = 0
    for x in items:
        if x > 0:
            pos = pos + 1
        elif x < 0:
            neg = neg + 1
        else:
            zero = zero + 1
    return [pos, neg, zero]

def rtc_between(x: int, lo: int, hi: int) -> int:
    if x >= lo:
        if x <= hi:
            return 1
    return 0

def rtc_outside(x: int, lo: int, hi: int) -> int:
    if x < lo or x > hi:
        return 1
    return 0

def rtc_bool_to_str(x: int) -> str:
    if x != 0:
        return "true"
    return "false"

def rtc_xor_logic(a: int, b: int) -> int:
    if a > 0 and b <= 0:
        return 1
    if a <= 0 and b > 0:
        return 1
    return 0

def rtc_implies(a: int, b: int) -> int:
    if a > 0:
        if b > 0:
            return 1
        return 0
    return 1

def rtc_day_type(day: int) -> str:
    if day == 0 or day == 6:
        return "weekend"
    if day >= 1:
        if day <= 5:
            return "weekday"
    return "invalid"

def rtc_season(month: int) -> str:
    if month >= 3:
        if month <= 5:
            return "spring"
    if month >= 6:
        if month <= 8:
            return "summer"
    if month >= 9:
        if month <= 11:
            return "fall"
    return "winter"
    """.trimIndent()

    // ─── Tests ───────────────────────────────────────────────

    @Test fun `cond - simple if`() = roundTrip("rtc_simple_if",
        posArgs(listOf(5), listOf(-1), listOf(0)))

    @Test fun `cond - if else`() = roundTrip("rtc_if_else",
        posArgs(listOf(5), listOf(-1), listOf(0)))

    @Test fun `cond - if elif else`() = roundTrip("rtc_if_elif_else",
        posArgs(listOf(5), listOf(-3), listOf(0)))

    @Test fun `cond - multi elif`() = roundTrip("rtc_multi_elif",
        posArgs(listOf(200), listOf(75), listOf(25), listOf(5), listOf(0), listOf(-10)))

    @Test fun `cond - nested if`() = roundTrip("rtc_nested_if",
        posArgs(listOf(1, 1), listOf(1, -1), listOf(-1, 1), listOf(-1, -1)))

    @Test fun `cond - deeply nested`() = roundTrip("rtc_deeply_nested",
        posArgs(listOf(1, 1, 1), listOf(1, 1, -1), listOf(1, -1, 0), listOf(-1, 0, 0)))

    @Test fun `cond - guard clause`() = roundTrip("rtc_guard_clause",
        posArgs(listOf(-5), listOf(0), listOf(200), listOf(50)))

    @Test fun `cond - and logic`() = roundTrip("rtc_and_logic",
        posArgs(listOf(1, 1), listOf(1, -1), listOf(-1, 1), listOf(-1, -1)))

    @Test fun `cond - or logic`() = roundTrip("rtc_or_logic",
        posArgs(listOf(1, 1), listOf(1, -1), listOf(-1, 1), listOf(-1, -1)))

    @Test fun `cond - not logic`() = roundTrip("rtc_not_logic",
        posArgs(listOf(5), listOf(-3), listOf(0)))

    @Test fun `cond - complex and or`() = roundTrip("rtc_complex_and_or",
        posArgs(listOf(1, 1, 1), listOf(1, 1, -1), listOf(-1, -1, 1), listOf(-1, -1, -1)))

    @Test fun `cond - and chain`() = roundTrip("rtc_and_chain",
        posArgs(listOf(1, 1, 1), listOf(1, 1, -1), listOf(1, -1, 1), listOf(-1, 1, 1)))

    @Test fun `cond - or chain`() = roundTrip("rtc_or_chain",
        posArgs(listOf(1, 1, 1), listOf(-1, -1, -1), listOf(1, -1, -1)))

    @Test fun `cond - ternary like`() = roundTrip("rtc_ternary_like",
        posArgs(listOf(5), listOf(-3), listOf(0)))

    @Test fun `cond - ternary nested`() = roundTrip("rtc_ternary_nested",
        posArgs(listOf(5), listOf(-3), listOf(0)))

    @Test fun `cond - ternary in expr`() = roundTrip("rtc_ternary_in_expr",
        posArgs(listOf(60), listOf(30), listOf(50)))

    @Test fun `cond - equal`() = roundTrip("rtc_equal",
        posArgs(listOf(1, 1), listOf(1, 2)))

    @Test fun `cond - not equal`() = roundTrip("rtc_not_equal",
        posArgs(listOf(1, 1), listOf(1, 2)))

    @Test fun `cond - less than`() = roundTrip("rtc_less_than",
        posArgs(listOf(1, 2), listOf(2, 1), listOf(1, 1)))

    @Test fun `cond - less equal`() = roundTrip("rtc_less_equal",
        posArgs(listOf(1, 2), listOf(2, 1), listOf(1, 1)))

    @Test fun `cond - greater than`() = roundTrip("rtc_greater_than",
        posArgs(listOf(2, 1), listOf(1, 2), listOf(1, 1)))

    @Test fun `cond - greater equal`() = roundTrip("rtc_greater_equal",
        posArgs(listOf(2, 1), listOf(1, 2), listOf(1, 1)))

    @Test fun `cond - classify char`() = roundTrip("rtc_classify_char",
        posArgs(listOf("5"), listOf("A"), listOf("z"), listOf(" ")))

    @Test fun `cond - grade`() = roundTrip("rtc_grade",
        posArgs(listOf(95), listOf(85), listOf(75), listOf(65), listOf(55)))

    @Test fun `cond - fizzbuzz`() = roundTrip("rtc_fizzbuzz",
        posArgs(listOf(15), listOf(9), listOf(10), listOf(7)))

    @Test fun `cond - leap year`() = roundTrip("rtc_leap_year",
        posArgs(listOf(2000), listOf(1900), listOf(2024), listOf(2023)))

    @Test fun `cond - absolute diff`() = roundTrip("rtc_absolute_diff",
        posArgs(listOf(5, 3), listOf(3, 5), listOf(0, 0)))

    @Test fun `cond - median of three`() = roundTrip("rtc_median_of_three",
        posArgs(listOf(1, 2, 3), listOf(3, 1, 2), listOf(2, 3, 1), listOf(5, 5, 5)))

    @Test fun `cond - sort two`() = roundTrip("rtc_sort_two",
        posArgs(listOf(3, 1), listOf(1, 3), listOf(2, 2)))

    @Test fun `cond - sort three`() = roundTrip("rtc_sort_three",
        posArgs(listOf(3, 1, 2), listOf(1, 2, 3), listOf(3, 2, 1), listOf(5, 5, 5)))

    @Test fun `cond - bmi category`() = roundTrip("rtc_bmi_category",
        posArgs(listOf(50, 170), listOf(70, 170), listOf(90, 170), listOf(120, 170)))

    @Test fun `cond - if return or assign`() = roundTrip("rtc_if_return_or_assign",
        posArgs(listOf(20), listOf(5), listOf(10)))

    @Test fun `cond - multiple conditions`() = roundTrip("rtc_multiple_conditions",
        posArgs(listOf(1, 1), listOf(1, -1), listOf(-1, 1), listOf(-1, -1)))

    @Test fun `cond - cascading assign`() = roundTrip("rtc_cascading_assign",
        posArgs(listOf(200), listOf(75), listOf(30), listOf(15), listOf(5)))

    @Test fun `cond - flag accumulate`() = roundTrip("rtc_flag_accumulate",
        posArgs(listOf(listOf(1, -1, 0)), listOf(listOf(1, 2, 3)), listOf(emptyList<Int>())))

    @Test fun `cond - all positive`() = roundTrip("rtc_all_positive",
        posArgs(listOf(listOf(1, 2, 3)), listOf(listOf(1, -1)), listOf(emptyList<Int>())))

    @Test fun `cond - any negative`() = roundTrip("rtc_any_negative",
        posArgs(listOf(listOf(1, -1, 3)), listOf(listOf(1, 2, 3)), listOf(emptyList<Int>())))

    @Test fun `cond - none zero`() = roundTrip("rtc_none_zero",
        posArgs(listOf(listOf(1, 2, 3)), listOf(listOf(1, 0, 3)), listOf(emptyList<Int>())))

    @Test fun `cond - count categories`() = roundTrip("rtc_count_categories",
        posArgs(listOf(listOf(1, -1, 0, 2, -3)), listOf(emptyList<Int>())))

    @Test fun `cond - between`() = roundTrip("rtc_between",
        posArgs(listOf(5, 1, 10), listOf(0, 1, 10), listOf(15, 1, 10), listOf(1, 1, 1)))

    @Test fun `cond - outside`() = roundTrip("rtc_outside",
        posArgs(listOf(5, 1, 10), listOf(0, 1, 10), listOf(15, 1, 10)))

    @Test fun `cond - bool to str`() = roundTrip("rtc_bool_to_str",
        posArgs(listOf(1), listOf(0), listOf(-1)))

    @Test fun `cond - xor logic`() = roundTrip("rtc_xor_logic",
        posArgs(listOf(1, 1), listOf(1, -1), listOf(-1, 1), listOf(-1, -1)))

    @Test fun `cond - implies`() = roundTrip("rtc_implies",
        posArgs(listOf(1, 1), listOf(1, -1), listOf(-1, 1), listOf(-1, -1)))

    @Test fun `cond - day type`() = roundTrip("rtc_day_type",
        posArgs(listOf(0), listOf(3), listOf(6), listOf(7)))

    @Test fun `cond - season`() = roundTrip("rtc_season",
        posArgs(listOf(1), listOf(4), listOf(7), listOf(10), listOf(12)))
}
