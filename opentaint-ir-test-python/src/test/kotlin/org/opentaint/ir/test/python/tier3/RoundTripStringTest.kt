package org.opentaint.ir.test.python.tier3

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Round-trip tests for string operations.
 * 50 test cases covering string manipulation, searching, and building.
 */
@Tag("tier3")
class RoundTripStringTest : RoundTripTestBase() {

    override val allSources = """
def rts_concat(a: str, b: str) -> str:
    return a + b

def rts_repeat(s: str, n: int) -> str:
    result = ""
    i = 0
    while i < n:
        result = result + s
        i = i + 1
    return result

def rts_length(s: str) -> int:
    return len(s)

def rts_first_char(s: str) -> str:
    return s[0]

def rts_last_char(s: str) -> str:
    return s[len(s) - 1]

def rts_char_at(s: str, i: int) -> str:
    return s[i]

def rts_contains_char(s: str, c: str) -> int:
    for ch in s:
        if ch == c:
            return 1
    return 0

def rts_count_char(s: str, c: str) -> int:
    count = 0
    for ch in s:
        if ch == c:
            count = count + 1
    return count

def rts_find_first(s: str, c: str) -> int:
    i = 0
    while i < len(s):
        if s[i] == c:
            return i
        i = i + 1
    return -1

def rts_find_last(s: str, c: str) -> int:
    result = -1
    i = 0
    while i < len(s):
        if s[i] == c:
            result = i
        i = i + 1
    return result

def rts_starts_with_char(s: str, c: str) -> int:
    if len(s) == 0:
        return 0
    if s[0] == c:
        return 1
    return 0

def rts_ends_with_char(s: str, c: str) -> int:
    if len(s) == 0:
        return 0
    if s[len(s) - 1] == c:
        return 1
    return 0

def rts_reverse(s: str) -> str:
    result = ""
    i = len(s) - 1
    while i >= 0:
        result = result + s[i]
        i = i - 1
    return result

def rts_is_palindrome(s: str) -> int:
    i = 0
    j = len(s) - 1
    while i < j:
        if s[i] != s[j]:
            return 0
        i = i + 1
        j = j - 1
    return 1

def rts_to_upper_simple(s: str) -> str:
    result = ""
    for c in s:
        n = ord(c)
        if n >= 97:
            if n <= 122:
                result = result + chr(n - 32)
                continue
        result = result + c
    return result

def rts_to_lower_simple(s: str) -> str:
    result = ""
    for c in s:
        n = ord(c)
        if n >= 65:
            if n <= 90:
                result = result + chr(n + 32)
                continue
        result = result + c
    return result

def rts_count_vowels(s: str) -> int:
    count = 0
    for c in s:
        if c == "a" or c == "e" or c == "i" or c == "o" or c == "u":
            count = count + 1
        elif c == "A" or c == "E" or c == "I" or c == "O" or c == "U":
            count = count + 1
    return count

def rts_count_consonants(s: str) -> int:
    count = 0
    for c in s:
        n = ord(c)
        is_letter = 0
        if n >= 65:
            if n <= 90:
                is_letter = 1
        if n >= 97:
            if n <= 122:
                is_letter = 1
        if is_letter == 1:
            if c != "a" and c != "e" and c != "i" and c != "o" and c != "u":
                if c != "A" and c != "E" and c != "I" and c != "O" and c != "U":
                    count = count + 1
    return count

def rts_is_alpha(c: str) -> int:
    n = ord(c)
    if n >= 65:
        if n <= 90:
            return 1
    if n >= 97:
        if n <= 122:
            return 1
    return 0

def rts_is_digit_char(c: str) -> int:
    n = ord(c)
    if n >= 48:
        if n <= 57:
            return 1
    return 0

def rts_count_words(s: str) -> int:
    count = 0
    in_word = 0
    for c in s:
        if c == " ":
            if in_word == 1:
                count = count + 1
                in_word = 0
        else:
            in_word = 1
    if in_word == 1:
        count = count + 1
    return count

def rts_truncate(s: str, max_len: int) -> str:
    if len(s) <= max_len:
        return s
    result = ""
    i = 0
    while i < max_len:
        result = result + s[i]
        i = i + 1
    return result

def rts_pad_left(s: str, width: int, pad: str) -> str:
    result = s
    while len(result) < width:
        result = pad + result
    return result

def rts_pad_right(s: str, width: int, pad: str) -> str:
    result = s
    while len(result) < width:
        result = result + pad
    return result

def rts_remove_char(s: str, c: str) -> str:
    result = ""
    for ch in s:
        if ch != c:
            result = result + ch
    return result

def rts_replace_char(s: str, old: str, new: str) -> str:
    result = ""
    for c in s:
        if c == old:
            result = result + new
        else:
            result = result + c
    return result

def rts_squeeze_spaces(s: str) -> str:
    result = ""
    prev_space = 0
    for c in s:
        if c == " ":
            if prev_space == 0:
                result = result + c
                prev_space = 1
        else:
            result = result + c
            prev_space = 0
    return result

def rts_trim_left(s: str) -> str:
    i = 0
    while i < len(s):
        if s[i] != " ":
            break
        i = i + 1
    result = ""
    while i < len(s):
        result = result + s[i]
        i = i + 1
    return result

def rts_trim_right(s: str) -> str:
    j = len(s) - 1
    while j >= 0:
        if s[j] != " ":
            break
        j = j - 1
    result = ""
    i = 0
    while i <= j:
        result = result + s[i]
        i = i + 1
    return result

def rts_caesar_cipher(s: str, shift: int) -> str:
    result = ""
    for c in s:
        n = ord(c)
        if n >= 65:
            if n <= 90:
                result = result + chr((n - 65 + shift) % 26 + 65)
                continue
        if n >= 97:
            if n <= 122:
                result = result + chr((n - 97 + shift) % 26 + 97)
                continue
        result = result + c
    return result

def rts_is_all_same(s: str) -> int:
    if len(s) == 0:
        return 1
    first = s[0]
    for c in s:
        if c != first:
            return 0
    return 1

def rts_common_prefix(a: str, b: str) -> str:
    result = ""
    i = 0
    while i < len(a):
        if i >= len(b):
            break
        if a[i] != b[i]:
            break
        result = result + a[i]
        i = i + 1
    return result

def rts_hamming_distance(a: str, b: str) -> int:
    count = 0
    i = 0
    while i < len(a):
        if a[i] != b[i]:
            count = count + 1
        i = i + 1
    return count

def rts_char_frequency(s: str) -> list:
    chars = []
    counts = []
    for c in s:
        found = 0
        i = 0
        while i < len(chars):
            if chars[i] == c:
                counts[i] = counts[i] + 1
                found = 1
                break
            i = i + 1
        if found == 0:
            chars = chars + [c]
            counts = counts + [1]
    return counts

def rts_join_with(items: list, sep: str) -> str:
    if len(items) == 0:
        return ""
    result = items[0]
    i = 1
    while i < len(items):
        result = result + sep + items[i]
        i = i + 1
    return result

def rts_repeat_pattern(pattern: str, n: int) -> str:
    result = ""
    i = 0
    while i < n:
        result = result + pattern
        i = i + 1
    return result

def rts_interleave(a: str, b: str) -> str:
    result = ""
    i = 0
    while i < len(a):
        result = result + a[i]
        if i < len(b):
            result = result + b[i]
        i = i + 1
    while i < len(b):
        result = result + b[i]
        i = i + 1
    return result

def rts_remove_duplicates(s: str) -> str:
    seen = ""
    result = ""
    for c in s:
        found = 0
        for sc in seen:
            if sc == c:
                found = 1
                break
        if found == 0:
            result = result + c
            seen = seen + c
    return result

def rts_count_substring(s: str, sub: str) -> int:
    count = 0
    i = 0
    sub_len = len(sub)
    while i <= len(s) - sub_len:
        match = 1
        j = 0
        while j < sub_len:
            if s[i + j] != sub[j]:
                match = 0
                break
            j = j + 1
        if match == 1:
            count = count + 1
        i = i + 1
    return count

def rts_run_length_count(s: str) -> int:
    if len(s) == 0:
        return 0
    runs = 1
    i = 1
    while i < len(s):
        if s[i] != s[i - 1]:
            runs = runs + 1
        i = i + 1
    return runs

def rts_max_char(s: str) -> str:
    best = s[0]
    for c in s:
        if c > best:
            best = c
    return best

def rts_min_char(s: str) -> str:
    best = s[0]
    for c in s:
        if c < best:
            best = c
    return best

def rts_is_sorted(s: str) -> int:
    i = 1
    while i < len(s):
        if s[i] < s[i - 1]:
            return 0
        i = i + 1
    return 1

def rts_count_upper(s: str) -> int:
    count = 0
    for c in s:
        n = ord(c)
        if n >= 65:
            if n <= 90:
                count = count + 1
    return count

def rts_count_lower(s: str) -> int:
    count = 0
    for c in s:
        n = ord(c)
        if n >= 97:
            if n <= 122:
                count = count + 1
    return count

def rts_swap_case(s: str) -> str:
    result = ""
    for c in s:
        n = ord(c)
        if n >= 65:
            if n <= 90:
                result = result + chr(n + 32)
                continue
        if n >= 97:
            if n <= 122:
                result = result + chr(n - 32)
                continue
        result = result + c
    return result

def rts_center_pad(s: str, width: int) -> str:
    if len(s) >= width:
        return s
    left = (width - len(s)) // 2
    result = ""
    i = 0
    while i < left:
        result = result + " "
        i = i + 1
    result = result + s
    while len(result) < width:
        result = result + " "
    return result
    """.trimIndent()

    // ─── Tests ───────────────────────────────────────────────

    @Test fun `string - concat`() = roundTrip("rts_concat",
        posArgs(listOf("hello", " world"), listOf("", "x"), listOf("a", "")))

    @Test fun `string - repeat`() = roundTrip("rts_repeat",
        posArgs(listOf("ab", 3), listOf("x", 0), listOf("hi", 1)))

    @Test fun `string - length`() = roundTrip("rts_length",
        posArgs(listOf("hello"), listOf(""), listOf("a")))

    @Test fun `string - first char`() = roundTrip("rts_first_char",
        posArgs(listOf("hello"), listOf("a"), listOf("xyz")))

    @Test fun `string - last char`() = roundTrip("rts_last_char",
        posArgs(listOf("hello"), listOf("a"), listOf("xyz")))

    @Test fun `string - char at`() = roundTrip("rts_char_at",
        posArgs(listOf("hello", 0), listOf("hello", 2), listOf("hello", 4)))

    @Test fun `string - contains char`() = roundTrip("rts_contains_char",
        posArgs(listOf("hello", "l"), listOf("hello", "z"), listOf("", "a")))

    @Test fun `string - count char`() = roundTrip("rts_count_char",
        posArgs(listOf("hello", "l"), listOf("aaa", "a"), listOf("abc", "z")))

    @Test fun `string - find first`() = roundTrip("rts_find_first",
        posArgs(listOf("hello", "l"), listOf("hello", "z"), listOf("abcabc", "b")))

    @Test fun `string - find last`() = roundTrip("rts_find_last",
        posArgs(listOf("hello", "l"), listOf("hello", "z"), listOf("abcabc", "c")))

    @Test fun `string - starts with char`() = roundTrip("rts_starts_with_char",
        posArgs(listOf("hello", "h"), listOf("hello", "z"), listOf("", "a")))

    @Test fun `string - ends with char`() = roundTrip("rts_ends_with_char",
        posArgs(listOf("hello", "o"), listOf("hello", "z"), listOf("", "a")))

    @Test fun `string - reverse`() = roundTrip("rts_reverse",
        posArgs(listOf("hello"), listOf(""), listOf("a"), listOf("abcdef")))

    @Test fun `string - is palindrome`() = roundTrip("rts_is_palindrome",
        posArgs(listOf("racecar"), listOf("hello"), listOf(""), listOf("a"), listOf("abba")))

    @Test fun `string - to upper simple`() = roundTrip("rts_to_upper_simple",
        posArgs(listOf("hello"), listOf("Hello"), listOf("123"), listOf("")))

    @Test fun `string - to lower simple`() = roundTrip("rts_to_lower_simple",
        posArgs(listOf("HELLO"), listOf("Hello"), listOf("123"), listOf("")))

    @Test fun `string - count vowels`() = roundTrip("rts_count_vowels",
        posArgs(listOf("hello"), listOf("xyz"), listOf("aeiou"), listOf("")))

    @Test fun `string - count consonants`() = roundTrip("rts_count_consonants",
        posArgs(listOf("hello"), listOf("aeiou"), listOf("bcdfg"), listOf("")))

    @Test fun `string - is alpha`() = roundTrip("rts_is_alpha",
        posArgs(listOf("a"), listOf("Z"), listOf("5"), listOf(" ")))

    @Test fun `string - is digit char`() = roundTrip("rts_is_digit_char",
        posArgs(listOf("5"), listOf("0"), listOf("a"), listOf(" ")))

    @Test fun `string - count words`() = roundTrip("rts_count_words",
        posArgs(listOf("hello world"), listOf("one"), listOf("  spaces  here  "), listOf("")))

    @Test fun `string - truncate`() = roundTrip("rts_truncate",
        posArgs(listOf("hello world", 5), listOf("hi", 10), listOf("abc", 3)))

    @Test fun `string - pad left`() = roundTrip("rts_pad_left",
        posArgs(listOf("42", 5, "0"), listOf("abc", 3, " "), listOf("", 3, "x")))

    @Test fun `string - pad right`() = roundTrip("rts_pad_right",
        posArgs(listOf("42", 5, "0"), listOf("abc", 3, " "), listOf("", 3, "x")))

    @Test fun `string - remove char`() = roundTrip("rts_remove_char",
        posArgs(listOf("hello", "l"), listOf("aaa", "a"), listOf("abc", "z")))

    @Test fun `string - replace char`() = roundTrip("rts_replace_char",
        posArgs(listOf("hello", "l", "r"), listOf("abc", "a", "x"), listOf("aaa", "a", "b")))

    @Test fun `string - squeeze spaces`() = roundTrip("rts_squeeze_spaces",
        posArgs(listOf("a  b   c"), listOf("no spaces"), listOf("   leading")))

    @Test fun `string - trim left`() = roundTrip("rts_trim_left",
        posArgs(listOf("  hello"), listOf("hello"), listOf("   "), listOf("")))

    @Test fun `string - trim right`() = roundTrip("rts_trim_right",
        posArgs(listOf("hello  "), listOf("hello"), listOf("   "), listOf("")))

    @Test fun `string - caesar cipher`() = roundTrip("rts_caesar_cipher",
        posArgs(listOf("abc", 3), listOf("xyz", 3), listOf("Hello", 1), listOf("abc", 0)))

    @Test fun `string - is all same`() = roundTrip("rts_is_all_same",
        posArgs(listOf("aaa"), listOf("abc"), listOf(""), listOf("a")))

    @Test fun `string - common prefix`() = roundTrip("rts_common_prefix",
        posArgs(listOf("abc", "abd"), listOf("hello", "hello"), listOf("abc", "xyz"), listOf("", "abc")))

    @Test fun `string - hamming distance`() = roundTrip("rts_hamming_distance",
        posArgs(listOf("abc", "axc"), listOf("abc", "abc"), listOf("aaa", "bbb")))

    @Test fun `string - char frequency`() = roundTrip("rts_char_frequency",
        posArgs(listOf("aabbc"), listOf("abc"), listOf("aaaa")))

    @Test fun `string - join with`() = roundTrip("rts_join_with",
        posArgs(listOf(listOf("a", "b", "c"), ","), listOf(listOf("x"), "-"), listOf(emptyList<String>(), ",")))

    @Test fun `string - repeat pattern`() = roundTrip("rts_repeat_pattern",
        posArgs(listOf("ab", 3), listOf("x", 5), listOf("", 3)))

    @Test fun `string - interleave`() = roundTrip("rts_interleave",
        posArgs(listOf("abc", "xyz"), listOf("ab", "x"), listOf("a", "xyz")))

    @Test fun `string - remove duplicates`() = roundTrip("rts_remove_duplicates",
        posArgs(listOf("hello"), listOf("abcabc"), listOf("aaa"), listOf("")))

    @Test fun `string - count substring`() = roundTrip("rts_count_substring",
        posArgs(listOf("aaaa", "aa"), listOf("hello", "ll"), listOf("abc", "xyz")))

    @Test fun `string - run length count`() = roundTrip("rts_run_length_count",
        posArgs(listOf("aabbbcc"), listOf("abc"), listOf("aaaa"), listOf("")))

    @Test fun `string - max char`() = roundTrip("rts_max_char",
        posArgs(listOf("abc"), listOf("zab"), listOf("a")))

    @Test fun `string - min char`() = roundTrip("rts_min_char",
        posArgs(listOf("abc"), listOf("zab"), listOf("z")))

    @Test fun `string - is sorted`() = roundTrip("rts_is_sorted",
        posArgs(listOf("abc"), listOf("cba"), listOf("aab"), listOf("a")))

    @Test fun `string - count upper`() = roundTrip("rts_count_upper",
        posArgs(listOf("Hello World"), listOf("abc"), listOf("ABC"), listOf("")))

    @Test fun `string - count lower`() = roundTrip("rts_count_lower",
        posArgs(listOf("Hello World"), listOf("ABC"), listOf("abc"), listOf("")))

    @Test fun `string - swap case`() = roundTrip("rts_swap_case",
        posArgs(listOf("Hello"), listOf("abc"), listOf("ABC"), listOf("a1B2")))

    @Test fun `string - center pad`() = roundTrip("rts_center_pad",
        posArgs(listOf("hi", 6), listOf("hello", 3), listOf("x", 5)))
}
