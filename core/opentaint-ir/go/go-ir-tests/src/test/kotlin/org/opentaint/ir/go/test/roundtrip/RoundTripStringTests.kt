package org.opentaint.ir.go.test.roundtrip

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripStringTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase("concat", """func str_concat(a, b string) string { return a + b }""",
                """fmt.Println(str_concat("hello", " world")); fmt.Println(str_concat("", "x")); fmt.Println(str_concat("a", ""))"""),
            RoundTripTestCase("greet", """func str_greet(name string) string { return "Hello, " + name + "!" }""",
                """fmt.Println(str_greet("Go")); fmt.Println(str_greet("")); fmt.Println(str_greet("World"))"""),
            RoundTripTestCase("repeat", """
func str_repeat(s string, n int) string {
    r := ""
    for i := 0; i < n; i++ { r += s }
    return r
}""", """fmt.Println(str_repeat("ab", 3)); fmt.Println(str_repeat("x", 0)); fmt.Println(str_repeat("", 5))"""),
            RoundTripTestCase("len", """func str_len(s string) int { return len(s) }""",
                """fmt.Println(str_len("hello")); fmt.Println(str_len("")); fmt.Println(str_len("abc"))"""),
            RoundTripTestCase("equal", """func str_equal(a, b string) bool { return a == b }""",
                """fmt.Println(str_equal("abc", "abc")); fmt.Println(str_equal("abc", "def")); fmt.Println(str_equal("", ""))"""),
            RoundTripTestCase("not-equal", """func str_neq(a, b string) bool { return a != b }""",
                """fmt.Println(str_neq("abc", "abc")); fmt.Println(str_neq("abc", "def"))"""),
            RoundTripTestCase("less-than", """func str_lt(a, b string) bool { return a < b }""",
                """fmt.Println(str_lt("abc", "def")); fmt.Println(str_lt("def", "abc")); fmt.Println(str_lt("abc", "abc"))"""),
            RoundTripTestCase("is-empty", """func str_isEmpty(s string) bool { return len(s) == 0 }""",
                """fmt.Println(str_isEmpty("")); fmt.Println(str_isEmpty("x"))"""),
            RoundTripTestCase("char-at", """
func str_charAt(s string, i int) int {
    if i < 0 || i >= len(s) { return -1 }
    return int(s[i])
}""", """fmt.Println(str_charAt("hello", 0)); fmt.Println(str_charAt("hello", 4)); fmt.Println(str_charAt("hello", 5))"""),
            RoundTripTestCase("count-char", """
func str_countChar(s string, c byte) int {
    n := 0
    for i := 0; i < len(s); i++ { if s[i] == c { n++ } }
    return n
}""", """fmt.Println(str_countChar("hello", 'l')); fmt.Println(str_countChar("aaa", 'a')); fmt.Println(str_countChar("abc", 'z'))"""),
            RoundTripTestCase("starts-with-char", """
func str_startsWith(s string, c byte) bool {
    if len(s) == 0 { return false }
    return s[0] == c
}""", """fmt.Println(str_startsWith("hello", 'h')); fmt.Println(str_startsWith("hello", 'x')); fmt.Println(str_startsWith("", 'a'))"""),
            RoundTripTestCase("ends-with-char", """
func str_endsWith(s string, c byte) bool {
    if len(s) == 0 { return false }
    return s[len(s)-1] == c
}""", """fmt.Println(str_endsWith("hello", 'o')); fmt.Println(str_endsWith("hello", 'x')); fmt.Println(str_endsWith("", 'a'))"""),
            RoundTripTestCase("build-num", """
func str_buildNum(n int) string {
    if n == 0 { return "0" }
    neg := false
    if n < 0 { neg = true; n = -n }
    s := ""
    for n > 0 { s = string(rune('0'+n%10)) + s; n /= 10 }
    if neg { s = "-" + s }
    return s
}""", """fmt.Println(str_buildNum(0)); fmt.Println(str_buildNum(123)); fmt.Println(str_buildNum(-42))"""),
            RoundTripTestCase("pad-left", """
func str_padLeft(s string, n int) string {
    for len(s) < n { s = " " + s }
    return s
}""", """fmt.Println(str_padLeft("hi", 5)); fmt.Println(str_padLeft("hello", 3)); fmt.Println(str_padLeft("", 2))"""),
            RoundTripTestCase("has-digit", """
func str_hasDigit(s string) bool {
    for i := 0; i < len(s); i++ {
        if s[i] >= '0' && s[i] <= '9' { return true }
    }
    return false
}""", """fmt.Println(str_hasDigit("abc123")); fmt.Println(str_hasDigit("abc")); fmt.Println(str_hasDigit(""))"""),
            RoundTripTestCase("is-all-digits", """
func str_allDigits(s string) bool {
    if len(s) == 0 { return false }
    for i := 0; i < len(s); i++ {
        if s[i] < '0' || s[i] > '9' { return false }
    }
    return true
}""", """fmt.Println(str_allDigits("123")); fmt.Println(str_allDigits("12a")); fmt.Println(str_allDigits(""))"""),
            RoundTripTestCase("first-index-of", """
func str_indexOf(s string, c byte) int {
    for i := 0; i < len(s); i++ { if s[i] == c { return i } }
    return -1
}""", """fmt.Println(str_indexOf("hello", 'l')); fmt.Println(str_indexOf("hello", 'z')); fmt.Println(str_indexOf("", 'a'))"""),
            RoundTripTestCase("last-index-of", """
func str_lastIndexOf(s string, c byte) int {
    for i := len(s) - 1; i >= 0; i-- { if s[i] == c { return i } }
    return -1
}""", """fmt.Println(str_lastIndexOf("hello", 'l')); fmt.Println(str_lastIndexOf("hello", 'z'))"""),
            RoundTripTestCase("compare-len", """
func str_cmpLen(a, b string) int {
    la := len(a); lb := len(b)
    if la < lb { return -1 }
    if la > lb { return 1 }
    return 0
}""", """fmt.Println(str_cmpLen("ab", "abc")); fmt.Println(str_cmpLen("abc", "ab")); fmt.Println(str_cmpLen("ab", "cd"))"""),
            RoundTripTestCase("multi-concat", """
func str_join3(a, b, c string) string { return a + ", " + b + ", " + c }""",
                """fmt.Println(str_join3("x","y","z")); fmt.Println(str_join3("","b",""))"""),
        )
        return BatchRoundTripRunner.runBatchAndCreateTests(cases, builder)
    }
}
