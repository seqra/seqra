package org.opentaint.ir.go.test.roundtrip

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripConversionTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            RoundTripTestCase("int-to-float", """
func cv_intToFloat(n int) int { f := float64(n); return int(f + 0.5) }""",
                "fmt.Println(cv_intToFloat(5)); fmt.Println(cv_intToFloat(0)); fmt.Println(cv_intToFloat(-3))"),
            RoundTripTestCase("float-trunc", """
func cv_floatTrunc(n int) int { return int(float64(n) * 0.7) }""",
                "fmt.Println(cv_floatTrunc(10)); fmt.Println(cv_floatTrunc(100)); fmt.Println(cv_floatTrunc(0))"),
            RoundTripTestCase("int32-conv", """
func cv_int32(n int) int { return int(int32(n)) }""",
                "fmt.Println(cv_int32(42)); fmt.Println(cv_int32(0)); fmt.Println(cv_int32(-100))"),
            RoundTripTestCase("int8-conv", """
func cv_int8(n int) int { return int(int8(n)) }""",
                "fmt.Println(cv_int8(42)); fmt.Println(cv_int8(127)); fmt.Println(cv_int8(128)); fmt.Println(cv_int8(-1))"),
            RoundTripTestCase("uint-conv", """
func cv_uint(n int) int { if n < 0 { return -1 }; return int(uint(n)) }""",
                "fmt.Println(cv_uint(42)); fmt.Println(cv_uint(0)); fmt.Println(cv_uint(-1))"),
            RoundTripTestCase("byte-conv", """
func cv_byte(n int) int { return int(byte(n)) }""",
                "fmt.Println(cv_byte(65)); fmt.Println(cv_byte(255)); fmt.Println(cv_byte(256)); fmt.Println(cv_byte(0))"),
            RoundTripTestCase("rune-conv", """
func cv_rune(n int) int { return int(rune(n)) }""",
                "fmt.Println(cv_rune(65)); fmt.Println(cv_rune(8364)); fmt.Println(cv_rune(0))"),
            RoundTripTestCase("int-to-string", """
func cv_itoa(n int) string {
    if n == 0 { return "0" }
    neg := n < 0
    if neg { n = -n }
    s := ""
    for n > 0 { s = string(rune('0'+n%10)) + s; n /= 10 }
    if neg { s = "-" + s }
    return s
}""", """fmt.Println(cv_itoa(0)); fmt.Println(cv_itoa(123)); fmt.Println(cv_itoa(-42))"""),
            RoundTripTestCase("string-to-int", """
func cv_atoi(s string) int {
    if len(s) == 0 { return 0 }
    neg := false; start := 0
    if s[0] == '-' { neg = true; start = 1 }
    n := 0
    for i := start; i < len(s); i++ { n = n*10 + int(s[i]-'0') }
    if neg { return -n }
    return n
}""", """fmt.Println(cv_atoi("123")); fmt.Println(cv_atoi("-42")); fmt.Println(cv_atoi("0")); fmt.Println(cv_atoi(""))"""),
            RoundTripTestCase("char-to-upper", """
func cv_toUpper(c byte) byte {
    if c >= 'a' && c <= 'z' { return c - 32 }
    return c
}""", "fmt.Println(cv_toUpper('a')); fmt.Println(cv_toUpper('z')); fmt.Println(cv_toUpper('A')); fmt.Println(cv_toUpper('5'))"),
            RoundTripTestCase("char-to-lower", """
func cv_toLower(c byte) byte {
    if c >= 'A' && c <= 'Z' { return c + 32 }
    return c
}""", "fmt.Println(cv_toLower('A')); fmt.Println(cv_toLower('Z')); fmt.Println(cv_toLower('a')); fmt.Println(cv_toLower('5'))"),
            RoundTripTestCase("bool-to-int", """
func cv_boolToInt(b bool) int { if b { return 1 }; return 0 }""",
                "fmt.Println(cv_boolToInt(true)); fmt.Println(cv_boolToInt(false))"),
            RoundTripTestCase("int-to-bool", """
func cv_intToBool(n int) bool { return n != 0 }""",
                "fmt.Println(cv_intToBool(0)); fmt.Println(cv_intToBool(1)); fmt.Println(cv_intToBool(-5))"),
            RoundTripTestCase("celsius-fahren", """
func cv_cToF(c int) int { return c*9/5 + 32 }
func cv_fToC(f int) int { return (f - 32) * 5 / 9 }""",
                "fmt.Println(cv_cToF(0)); fmt.Println(cv_cToF(100)); fmt.Println(cv_fToC(32)); fmt.Println(cv_fToC(212))"),
            RoundTripTestCase("km-miles", """
func cv_kmToMi(km int) int { return km * 621 / 1000 }
func cv_miToKm(mi int) int { return mi * 1609 / 1000 }""",
                "fmt.Println(cv_kmToMi(100)); fmt.Println(cv_miToKm(62))"),
        )
        val result = BatchRoundTripRunner.runBatch(cases, builder)
        return cases.map { c -> DynamicTest.dynamicTest(c.name) {
            assertThat(result.reconstructedOutputs[c.name]).isEqualTo(result.originalOutputs[c.name])
        }}
    }
}
