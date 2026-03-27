package org.opentaint.ir.go.test.roundtrip

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripTrickyTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            // ── 1. Many phi nodes: multiple variables modified in if/else ────
            RoundTripTestCase.withInputs("phi-multi-var", """
func rtt_phiMultiVar(a, b, c int) int {
    x := 0
    y := 0
    z := 0
    if a > 0 {
        x = a + 1
        y = b * 2
        z = c - 3
    } else {
        x = a - 1
        y = b + 5
        z = c * 2
    }
    return x + y + z
}""",
                "rtt_phiMultiVar(%s, %s, %s)",
                listOf(listOf("10", "5", "3"), listOf("-7", "4", "8"), listOf("0", "0", "0"), listOf("1", "-1", "1")),
            ),

            // ── 2. Phi nodes with 4 variables across if/else ────────────────
            RoundTripTestCase.withInputs("phi-four-vars", """
func rtt_phiFourVars(a, b int) int {
    p := 0
    q := 0
    r := 0
    s := 0
    if a > b {
        p = a - b
        q = a * 2
        r = b + 1
        s = a + b
    } else {
        p = b - a
        q = b * 2
        r = a + 1
        s = b - a
    }
    return p*1000 + q*100 + r*10 + s
}""",
                "rtt_phiFourVars(%s, %s)",
                listOf(listOf("10", "3"), listOf("3", "10"), listOf("5", "5"), listOf("-2", "7"), listOf("0", "-1")),
            ),

            // ── 3. Deeply nested ifs – 5 levels ────────────────────────────
            RoundTripTestCase.withInputs("deep-nest-5", """
func rtt_deepNest5(a, b, c, d, e int) int {
    if a > 0 {
        if b > 0 {
            if c > 0 {
                if d > 0 {
                    if e > 0 {
                        return a + b + c + d + e
                    }
                    return a + b + c + d
                }
                return a + b + c
            }
            return a + b
        }
        return a
    }
    return -1
}""",
                "rtt_deepNest5(%s, %s, %s, %s, %s)",
                listOf(
                    listOf("1", "2", "3", "4", "5"),
                    listOf("1", "2", "3", "4", "-1"),
                    listOf("1", "2", "3", "-1", "5"),
                    listOf("1", "2", "-1", "4", "5"),
                    listOf("-1", "2", "3", "4", "5"),
                ),
            ),

            // ── 4. Deeply nested ifs – 6 levels with arithmetic ────────────
            RoundTripTestCase.withInputs("deep-nest-6-arith", """
func rtt_deepNest6(a, b, c int) int {
    r := 0
    if a > 10 {
        r += 1
        if b > 10 {
            r += 2
            if c > 10 {
                r += 4
                if a > b {
                    r += 8
                    if b > c {
                        r += 16
                        if a - b > c {
                            r += 32
                        }
                    }
                }
            }
        }
    }
    return r
}""",
                "rtt_deepNest6(%s, %s, %s)",
                listOf(listOf("20", "15", "12"), listOf("5", "20", "30"), listOf("30", "20", "10"), listOf("100", "50", "25"), listOf("1", "1", "1")),
            ),

            // ── 5. Same variable assigned in many branches ─────────────────
            RoundTripTestCase.withInputs("same-var-many-branches", """
func rtt_sameVarBranch(x int) int {
    r := 0
    if x < -100 {
        r = -5
    } else if x < -50 {
        r = -4
    } else if x < -10 {
        r = -3
    } else if x < 0 {
        r = -2
    } else if x == 0 {
        r = 0
    } else if x < 10 {
        r = 2
    } else if x < 50 {
        r = 3
    } else if x < 100 {
        r = 4
    } else {
        r = 5
    }
    return r
}""",
                "rtt_sameVarBranch(%s)",
                listOf(listOf("-200"), listOf("-75"), listOf("-30"), listOf("-5"), listOf("0")),
            ),

            // ── 6. Loop with index + accumulator + flag all modified ────────
            RoundTripTestCase.withInputs("loop-idx-acc-flag", """
func rtt_loopIdxAccFlag(n int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    acc := 0
    flag := 0
    for i := 0; i < n; i++ {
        acc += i * (1 - 2*flag)
        if i%7 == 0 {
            flag = 1 - flag
        }
    }
    return acc*10 + flag
}""",
                "rtt_loopIdxAccFlag(%s)",
                listOf(listOf("20"), listOf("50"), listOf("100"), listOf("7"), listOf("1")),
            ),

            // ── 7. Function with 10+ basic blocks ──────────────────────────
            RoundTripTestCase.withInputs("many-basic-blocks", """
func rtt_manyBlocks(a, b int) int {
    r := 0
    if a > 0 { r += 1 } else { r -= 1 }
    if b > 0 { r += 2 } else { r -= 2 }
    if a > b { r += 4 } else { r -= 4 }
    if a+b > 10 { r += 8 } else { r -= 8 }
    if a*b > 0 { r += 16 } else { r -= 16 }
    if a-b > 0 { r += 32 } else { r -= 32 }
    return r
}""",
                "rtt_manyBlocks(%s, %s)",
                listOf(listOf("10", "5"), listOf("-3", "7"), listOf("0", "0"), listOf("100", "-100"), listOf("3", "3")),
            ),

            // ── 8. Function with 5 parameters ──────────────────────────────
            RoundTripTestCase.withInputs("five-params", """
func rtt_fiveParams(a, b, c, d, e int) int {
    if a > b {
        if c > d {
            return a + c + e
        }
        return a + d + e
    }
    if c > d {
        return b + c + e
    }
    return b + d + e
}""",
                "rtt_fiveParams(%s, %s, %s, %s, %s)",
                listOf(
                    listOf("10", "5", "8", "3", "1"),
                    listOf("1", "10", "3", "8", "2"),
                    listOf("5", "5", "5", "5", "5"),
                    listOf("-1", "-2", "-3", "-4", "-5"),
                ),
            ),

            // ── 9. Complex boolean expression ──────────────────────────────
            RoundTripTestCase.withInputs("complex-bool", """
func rtt_complexBool(a, b, c, d int) int {
    if (a > 0 && b > 0) || (c < 0 && d < 0) {
        return a + b - c - d
    }
    if (a < 0 && b < 0) || (c > 0 && d > 0) {
        return c + d - a - b
    }
    return a - b + c - d
}""",
                "rtt_complexBool(%s, %s, %s, %s)",
                listOf(
                    listOf("5", "3", "-2", "-4"),
                    listOf("-1", "-2", "3", "4"),
                    listOf("1", "-1", "1", "-1"),
                    listOf("0", "0", "0", "0"),
                    listOf("10", "10", "-10", "-10"),
                ),
            ),

            // ── 10. Multiple accumulators in one loop ──────────────────────
            RoundTripTestCase.withInputs("multi-accum", """
func rtt_multiAccum(n int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    sumEven := 0
    sumOdd := 0
    count := 0
    for i := 1; i <= n; i++ {
        if i%2 == 0 {
            sumEven += i
        } else {
            sumOdd += i
        }
        count++
    }
    return sumEven*100 + sumOdd*10 + count
}""",
                "rtt_multiAccum(%s)",
                listOf(listOf("10"), listOf("25"), listOf("1"), listOf("0"), listOf("50")),
            ),

            // ── 11. Interleaved even/odd accumulation ──────────────────────
            RoundTripTestCase.withInputs("interleaved-even-odd", """
func rtt_interleavedAccum(n, m int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    if m < 0 { m = -m }
    if m == 0 { m = 1 }
    accA := 0
    accB := 0
    for i := 0; i < n; i++ {
        v := (i * m) % 100
        if i%2 == 0 {
            accA += v
            accB -= v / 2
        } else {
            accA -= v / 3
            accB += v
        }
    }
    if accA < 0 { accA = -accA }
    if accB < 0 { accB = -accB }
    return accA*1000 + accB
}""",
                "rtt_interleavedAccum(%s, %s)",
                listOf(listOf("20", "7"), listOf("50", "3"), listOf("100", "11"), listOf("1", "1"), listOf("10", "13")),
                randomRange = 1..500,
            ),

            // ── 12. Zigzag pattern (alternating +/-) ───────────────────────
            RoundTripTestCase.withInputs("zigzag", """
func rtt_zigzag(n, step int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    if step < 0 { step = -step }
    if step == 0 { step = 1 }
    acc := 0
    sign := 1
    for i := 0; i < n; i++ {
        acc += sign * (i * step % 100)
        sign = -sign
    }
    return acc
}""",
                "rtt_zigzag(%s, %s)",
                listOf(listOf("10", "3"), listOf("20", "7"), listOf("50", "1"), listOf("1", "5"), listOf("100", "11")),
            ),

            // ── 13. Return different constants from 5+ branches ────────────
            RoundTripTestCase.withInputs("multi-const-return", """
func rtt_multiConstReturn(x, y int) int {
    d := x - y
    if d > 100 { return 100 }
    if d > 50 { return 75 }
    if d > 10 { return 50 }
    if d > 0 { return 25 }
    if d == 0 { return 0 }
    if d > -10 { return -25 }
    if d > -50 { return -50 }
    if d > -100 { return -75 }
    return -100
}""",
                "rtt_multiConstReturn(%s, %s)",
                listOf(listOf("200", "50"), listOf("60", "5"), listOf("15", "3"), listOf("5", "5"), listOf("0", "200")),
            ),

            // ── 14. For-loop with multiple break conditions ────────────────
            RoundTripTestCase.withInputs("multi-break", """
func rtt_multiBreak(n, limit int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    if limit < 0 { limit = -limit }
    if limit == 0 { limit = 1 }
    acc := 0
    for i := 1; i <= n; i++ {
        acc += i
        if acc > limit {
            return acc * 10 + 1
        }
        if i * i > n {
            return acc * 10 + 2
        }
        if acc % 37 == 0 && i > 5 {
            return acc * 10 + 3
        }
    }
    return acc * 10
}""",
                "rtt_multiBreak(%s, %s)",
                listOf(listOf("100", "50"), listOf("20", "1000"), listOf("50", "30"), listOf("10", "10"), listOf("200", "200")),
                randomRange = 1..500,
            ),

            // ── 15. Cascading if-else-if-else chain ────────────────────────
            RoundTripTestCase.withInputs("cascade-chain", """
func rtt_cascadeChain(a, b, c int) int {
    r := 0
    if a > b && b > c {
        r = a - c
    } else if a > c && c > b {
        r = a - b
    } else if b > a && a > c {
        r = b - c
    } else if b > c && c > a {
        r = b - a
    } else if c > a && a > b {
        r = c - b
    } else if c > b && b > a {
        r = c - a
    } else {
        r = a + b + c
    }
    return r
}""",
                "rtt_cascadeChain(%s, %s, %s)",
                listOf(listOf("10", "5", "1"), listOf("10", "1", "5"), listOf("5", "10", "1"), listOf("1", "5", "10"), listOf("3", "3", "3")),
            ),

            // ── 16. Variable shadow-like (recomputed in inner scope) ───────
            RoundTripTestCase.withInputs("shadow-recompute", """
func rtt_shadowRecompute(a, b int) int {
    x := a + b
    if a > 0 {
        x := a * 2
        if b > 0 {
            x := x + b*3
            a = x - 1
        } else {
            a = x + 1
        }
    }
    return x + a
}""",
                "rtt_shadowRecompute(%s, %s)",
                listOf(listOf("5", "3"), listOf("-2", "7"), listOf("10", "-5"), listOf("0", "0"), listOf("100", "50")),
            ),

            // ── 17. Loop-invariant variable used after loop ────────────────
            RoundTripTestCase.withInputs("loop-invariant-after", """
func rtt_loopInvariantAfter(n, k int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    if k == 0 { k = 1 }
    factor := k * 3 + 7
    acc := 0
    for i := 0; i < n; i++ {
        acc += i * factor
    }
    result := acc + factor * n
    return result
}""",
                "rtt_loopInvariantAfter(%s, %s)",
                listOf(listOf("10", "5"), listOf("50", "2"), listOf("100", "1"), listOf("0", "10"), listOf("20", "-3")),
            ),

            // ── 18. Rolling computation (hash-like) ────────────────────────
            RoundTripTestCase.withInputs("rolling-hash", """
func rtt_rollingHash(a, b, c int) int {
    if a < 0 { a = -a }
    if b < 0 { b = -b }
    if c < 0 { c = -c }
    h := 0
    h = h*31 + a
    h = h ^ (h >> 5)
    h = h*31 + b
    h = h ^ (h >> 5)
    h = h*31 + c
    h = h ^ (h >> 5)
    if h < 0 { h = -h }
    return h % 1000000
}""",
                "rtt_rollingHash(%s, %s, %s)",
                listOf(listOf("1", "2", "3"), listOf("100", "200", "300"), listOf("0", "0", "0"), listOf("7", "13", "42"), listOf("999", "888", "777")),
            ),

            // ── 19. Rolling hash in a loop ─────────────────────────────────
            RoundTripTestCase.withInputs("rolling-hash-loop", """
func rtt_rollingHashLoop(seed, n int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    h := seed
    for i := 0; i < n; i++ {
        h = h*37 + i*13
        h = h ^ (h >> 7)
        h = h & 0x7FFFFFFF
    }
    return h % 1000000
}""",
                "rtt_rollingHashLoop(%s, %s)",
                listOf(listOf("42", "10"), listOf("0", "50"), listOf("123", "100"), listOf("999", "1"), listOf("1", "200")),
            ),

            // ── 20. XOR bit manipulation pattern ───────────────────────────
            RoundTripTestCase.withInputs("xor-pattern", """
func rtt_xorPattern(a, b, c int) int {
    if a < 0 { a = -a }
    if b < 0 { b = -b }
    if c < 0 { c = -c }
    x := a ^ b
    y := b ^ c
    z := a ^ c
    r := (x & y) | (y & z) | (z & x)
    return r ^ (x + y + z)
}""",
                "rtt_xorPattern(%s, %s, %s)",
                listOf(listOf("255", "170", "85"), listOf("0", "0", "0"), listOf("1023", "512", "256"), listOf("7", "14", "21"), listOf("100", "50", "25")),
            ),

            // ── 21. XOR accumulation loop ──────────────────────────────────
            RoundTripTestCase.withInputs("xor-accum-loop", """
func rtt_xorAccumLoop(n, seed int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    acc := seed
    for i := 1; i <= n; i++ {
        acc ^= i * 31
        acc = (acc << 1) | (acc >> 30)
        acc &= 0x7FFFFFFF
    }
    return acc % 100000
}""",
                "rtt_xorAccumLoop(%s, %s)",
                listOf(listOf("10", "0"), listOf("50", "42"), listOf("100", "255"), listOf("1", "1"), listOf("200", "999")),
            ),

            // ── 22. Reduction pattern (reduce to single value) ─────────────
            RoundTripTestCase.withInputs("reduce-minmax", """
func rtt_reduceMinMax(a, b, c, d, e int) int {
    mn := a
    mx := a
    if b < mn { mn = b }
    if b > mx { mx = b }
    if c < mn { mn = c }
    if c > mx { mx = c }
    if d < mn { mn = d }
    if d > mx { mx = d }
    if e < mn { mn = e }
    if e > mx { mx = e }
    return mx - mn
}""",
                "rtt_reduceMinMax(%s, %s, %s, %s, %s)",
                listOf(
                    listOf("10", "3", "7", "1", "9"),
                    listOf("5", "5", "5", "5", "5"),
                    listOf("-10", "20", "-30", "40", "-50"),
                    listOf("0", "0", "0", "0", "0"),
                ),
            ),

            // ── 23. Reduction via loop with conditional ────────────────────
            RoundTripTestCase.withInputs("reduce-loop-cond", """
func rtt_reduceLoopCond(n, seed int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    best := 0
    bestIdx := 0
    v := seed
    for i := 0; i < n; i++ {
        v = (v*41 + 17) % 1000
        score := v - 500
        if score < 0 { score = -score }
        if score > best {
            best = score
            bestIdx = i
        }
    }
    return best*1000 + bestIdx
}""",
                "rtt_reduceLoopCond(%s, %s)",
                listOf(listOf("50", "7"), listOf("100", "42"), listOf("10", "0"), listOf("200", "999"), listOf("1", "1")),
            ),

            // ── 24. State transition function ──────────────────────────────
            RoundTripTestCase.withInputs("state-transition", """
func rtt_stateTransition(n, seed int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    state := 0
    transitions := 0
    v := seed
    for i := 0; i < n; i++ {
        v = (v*29 + 11) % 100
        prev := state
        if state == 0 {
            if v > 70 { state = 1 }
            if v < 20 { state = 2 }
        } else if state == 1 {
            if v < 40 { state = 0 }
            if v > 90 { state = 3 }
        } else if state == 2 {
            if v > 50 { state = 0 }
            if v < 10 { state = 3 }
        } else {
            if v > 60 { state = 0 }
        }
        if state != prev { transitions++ }
    }
    return transitions*100 + state
}""",
                "rtt_stateTransition(%s, %s)",
                listOf(listOf("50", "7"), listOf("100", "42"), listOf("200", "1"), listOf("10", "99"), listOf("30", "0")),
            ),

            // ── 25. Diamond merge in control flow ──────────────────────────
            RoundTripTestCase.withInputs("diamond-merge", """
func rtt_diamondMerge(a, b, c int) int {
    x := 0
    y := 0
    if a > 0 {
        x = a * 2
        y = b + 1
    } else {
        x = b * 2
        y = a + 1
    }
    z := x + y
    if c > 0 {
        z = z * 2
    } else {
        z = z + 10
    }
    return z
}""",
                "rtt_diamondMerge(%s, %s, %s)",
                listOf(listOf("5", "3", "1"), listOf("-5", "3", "1"), listOf("5", "3", "-1"), listOf("-5", "3", "-1"), listOf("0", "0", "0")),
            ),

            // ── 26. Double diamond merge ───────────────────────────────────
            RoundTripTestCase.withInputs("double-diamond", """
func rtt_doubleDiamond(a, b, c, d int) int {
    x := 0
    if a > b {
        x = a - b
    } else {
        x = b - a
    }
    y := 0
    if c > d {
        y = c - d
    } else {
        y = d - c
    }
    r := 0
    if x > y {
        r = x * 2 + y
    } else {
        r = y * 2 + x
    }
    return r
}""",
                "rtt_doubleDiamond(%s, %s, %s, %s)",
                listOf(listOf("10", "3", "7", "2"), listOf("3", "10", "2", "7"), listOf("5", "5", "5", "5"), listOf("-1", "1", "-1", "1"), listOf("100", "0", "0", "100")),
            ),

            // ── 27. Phi with loop and branch ───────────────────────────────
            RoundTripTestCase.withInputs("phi-loop-branch", """
func rtt_phiLoopBranch(n int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    a := 0
    b := 1
    c := 0
    for i := 0; i < n; i++ {
        if i%3 == 0 {
            a += i
            b *= 2
            if b > 10000 { b = b % 10000 }
        } else if i%3 == 1 {
            b += i
            c += a
        } else {
            c += b
            a -= 1
        }
    }
    return (a%1000)*1000000 + (b%1000)*1000 + (c%1000)
}""",
                "rtt_phiLoopBranch(%s)",
                listOf(listOf("10"), listOf("30"), listOf("50"), listOf("1"), listOf("0")),
            ),

            // ── 28. Three accumulators with different update rules ──────────
            RoundTripTestCase.withInputs("three-accum-rules", """
func rtt_threeAccumRules(n, m int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    if m == 0 { m = 1 }
    sum := 0
    prod := 1
    xorAcc := 0
    for i := 1; i <= n; i++ {
        v := (i * m) % 50
        sum += v
        prod = (prod * (v + 1)) % 100000
        xorAcc ^= v
    }
    return (sum%10000)*100 + (prod%100) + xorAcc%10
}""",
                "rtt_threeAccumRules(%s, %s)",
                listOf(listOf("10", "3"), listOf("50", "7"), listOf("100", "11"), listOf("1", "1"), listOf("20", "13")),
                randomRange = 1..500,
            ),

            // ── 29. Zigzag with amplitude decay ───────────────────────────
            RoundTripTestCase.withInputs("zigzag-decay", """
func rtt_zigzagDecay(n, amp int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    if amp < 0 { amp = -amp }
    if amp == 0 { amp = 1 }
    acc := 0
    sign := 1
    for i := 0; i < n; i++ {
        acc += sign * amp
        sign = -sign
        amp = amp * 9 / 10
        if amp == 0 { amp = 1 }
    }
    return acc
}""",
                "rtt_zigzagDecay(%s, %s)",
                listOf(listOf("10", "100"), listOf("20", "50"), listOf("50", "200"), listOf("1", "10"), listOf("100", "1000")),
            ),

            // ── 30. Function with 5 params and complex boolean ─────────────
            RoundTripTestCase.withInputs("five-param-bool", """
func rtt_fiveParamBool(a, b, c, d, e int) int {
    if (a > 0 && b > 0) || (c < 0 && d < 0) {
        if e > a + b {
            return e - a - b
        }
        return a + b + e
    }
    if (a < 0 || b < 0) && (c > 0 || d > 0) {
        if e < c + d {
            return c + d - e
        }
        return e - c - d
    }
    return a + b + c + d + e
}""",
                "rtt_fiveParamBool(%s, %s, %s, %s, %s)",
                listOf(
                    listOf("5", "3", "-2", "-4", "20"),
                    listOf("-1", "-2", "3", "4", "1"),
                    listOf("1", "-1", "1", "-1", "0"),
                    listOf("0", "0", "0", "0", "0"),
                ),
            ),

            // ── 31. Multi-break loop with accumulator ──────────────────────
            RoundTripTestCase.withInputs("multi-break-accum", """
func rtt_multiBreakAccum(n int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    acc := 0
    exitCode := 0
    for i := 1; i <= n; i++ {
        acc += i
        if acc > 500 {
            exitCode = 1
            break
        }
        if acc%100 == 0 && i > 3 {
            exitCode = 2
            break
        }
        if i*i*i > 10000 {
            exitCode = 3
            break
        }
    }
    return acc*10 + exitCode
}""",
                "rtt_multiBreakAccum(%s)",
                listOf(listOf("50"), listOf("100"), listOf("10"), listOf("200"), listOf("5")),
            ),

            // ── 32. Cascading assignments in if-else-if ────────────────────
            RoundTripTestCase.withInputs("cascade-assign", """
func rtt_cascadeAssign(a, b, c int) int {
    x := 0
    y := 0
    z := 0
    if a > b && b > c {
        x = a; y = b; z = c
    } else if a > c && c > b {
        x = a; y = c; z = b
    } else if b > a && a > c {
        x = b; y = a; z = c
    } else if b > c && c > a {
        x = b; y = c; z = a
    } else if c > a && a > b {
        x = c; y = a; z = b
    } else if c > b && b > a {
        x = c; y = b; z = a
    } else {
        x = a; y = a; z = a
    }
    return x*10000 + y*100 + z
}""",
                "rtt_cascadeAssign(%s, %s, %s)",
                listOf(listOf("10", "5", "1"), listOf("1", "5", "10"), listOf("5", "1", "10"), listOf("3", "3", "3"), listOf("7", "3", "5")),
            ),

            // ── 33. Shadow-like with nested if recomputation ───────────────
            RoundTripTestCase.withInputs("shadow-nested-if", """
func rtt_shadowNestedIf(a, b int) int {
    result := a * b
    if a > 0 {
        tmp := a + 10
        if b > 0 {
            tmp := tmp * b
            result = tmp - a
        } else {
            result = tmp + b
        }
    } else {
        tmp := b + 10
        if a < -10 {
            tmp := tmp - a
            result = tmp * 2
        } else {
            result = tmp + a
        }
    }
    return result
}""",
                "rtt_shadowNestedIf(%s, %s)",
                listOf(listOf("5", "3"), listOf("-20", "7"), listOf("-5", "-3"), listOf("0", "0"), listOf("100", "-50")),
            ),

            // ── 34. Loop-invariant with post-loop branch ───────────────────
            RoundTripTestCase.withInputs("loop-inv-post-branch", """
func rtt_loopInvPostBranch(n, k int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    base := k * k + 3
    acc := 0
    maxVal := 0
    for i := 1; i <= n; i++ {
        v := (i * base) % 100
        acc += v
        if v > maxVal { maxVal = v }
    }
    if acc > base * n {
        return acc - base
    }
    return acc + maxVal
}""",
                "rtt_loopInvPostBranch(%s, %s)",
                listOf(listOf("20", "3"), listOf("50", "7"), listOf("100", "2"), listOf("1", "10"), listOf("10", "0")),
            ),

            // ── 35. Bit manipulation reduction ─────────────────────────────
            RoundTripTestCase.withInputs("bit-reduce", """
func rtt_bitReduce(a, b, c, d int) int {
    if a < 0 { a = -a }
    if b < 0 { b = -b }
    if c < 0 { c = -c }
    if d < 0 { d = -d }
    r := a
    r = r ^ (b << 3)
    r = r & 0xFFFF
    r = r | (c >> 1)
    r = r ^ (d * 7)
    r = r & 0xFFFF
    bits := 0
    tmp := r
    for tmp > 0 {
        bits += tmp & 1
        tmp >>= 1
    }
    return r*100 + bits
}""",
                "rtt_bitReduce(%s, %s, %s, %s)",
                listOf(listOf("255", "128", "64", "32"), listOf("0", "0", "0", "0"), listOf("1000", "500", "250", "125"), listOf("7", "14", "28", "56"), listOf("1", "1", "1", "1")),
            ),

            // ── 36. State transition with output accumulation ──────────────
            RoundTripTestCase.withInputs("state-accum", """
func rtt_stateAccum(n, seed int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    state := 0
    output := 0
    v := seed
    for i := 0; i < n; i++ {
        v = (v*23 + 7) % 100
        if state == 0 {
            output += v
            if v > 60 { state = 1 }
        } else if state == 1 {
            output += v * 2
            if v < 30 { state = 2 }
        } else {
            output += v * 3
            if v > 50 { state = 0 }
        }
    }
    return output%100000*10 + state
}""",
                "rtt_stateAccum(%s, %s)",
                listOf(listOf("30", "7"), listOf("100", "42"), listOf("50", "0"), listOf("200", "99"), listOf("10", "1")),
            ),

            // ── 37. Diamond with phi + loop after ──────────────────────────
            RoundTripTestCase.withInputs("diamond-phi-loop", """
func rtt_diamondPhiLoop(a, b, n int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    x := 0
    y := 0
    if a > b {
        x = a - b
        y = a + b
    } else {
        x = b - a
        y = b + a + 1
    }
    for i := 0; i < n; i++ {
        x += y % 10
        y = y/10 + x
    }
    return x*1000 + y%1000
}""",
                "rtt_diamondPhiLoop(%s, %s, %s)",
                listOf(listOf("10", "3", "5"), listOf("3", "10", "10"), listOf("0", "0", "0"), listOf("100", "50", "20"), listOf("7", "7", "15")),
            ),

            // ── 38. Nested if with many phi merges at exit ─────────────────
            RoundTripTestCase.withInputs("nested-phi-exit", """
func rtt_nestedPhiExit(a, b, c int) int {
    x := a
    y := b
    z := c
    w := 0
    if a > 0 {
        x = x + 10
        if b > 0 {
            y = y + 20
            w = 1
        } else {
            y = y - 20
            w = 2
        }
    } else {
        x = x - 10
        if c > 0 {
            z = z + 30
            w = 3
        } else {
            z = z - 30
            w = 4
        }
    }
    return x*10000 + y*100 + z + w
}""",
                "rtt_nestedPhiExit(%s, %s, %s)",
                listOf(listOf("5", "3", "1"), listOf("5", "-3", "1"), listOf("-5", "3", "1"), listOf("-5", "3", "-1"), listOf("0", "0", "0")),
            ),

            // ── 39. Interleaved loops with flag carry ──────────────────────
            RoundTripTestCase.withInputs("interleaved-loop-flag", """
func rtt_interleavedLoopFlag(n int) int {
    if n < 0 { n = -n }
    if n > 100 { n = 100 }
    flag := 0
    acc1 := 0
    for i := 0; i < n; i++ {
        if i%2 == 0 { acc1 += i } else { acc1 -= i }
        if acc1 > 50 { flag = 1 }
    }
    acc2 := 0
    for i := 0; i < n; i++ {
        if flag == 1 {
            acc2 += i * 2
        } else {
            acc2 += i
        }
    }
    return acc1*1000 + acc2 + flag
}""",
                "rtt_interleavedLoopFlag(%s)",
                listOf(listOf("10"), listOf("30"), listOf("50"), listOf("100"), listOf("1")),
            ),

            // ── 40. Complex: diamond + loop + multi-break + phi ─────────────
            RoundTripTestCase.withInputs("complex-flow", """
func rtt_complexFlow(a, b, c int) int {
    if c < 0 { c = -c }
    if c > 100 { c = 100 }
    x := 0
    y := 0
    if a > b {
        x = a * 2
        y = b + 1
    } else {
        x = b * 2
        y = a + 1
    }
    acc := x + y
    exitKind := 0
    for i := 0; i < c; i++ {
        acc += (i * x) % 37
        if acc > 5000 {
            exitKind = 1
            break
        }
        if i > 0 && acc%100 == 0 {
            exitKind = 2
            break
        }
        if acc < 0 {
            acc = -acc
        }
    }
    return acc*10 + exitKind
}""",
                "rtt_complexFlow(%s, %s, %s)",
                listOf(listOf("10", "5", "20"), listOf("5", "10", "50"), listOf("0", "0", "10"), listOf("100", "1", "100"), listOf("-7", "3", "30")),
            ),
        )
        return BatchRoundTripRunner.runBatchAndCreateTests(cases, builder)
    }
}
