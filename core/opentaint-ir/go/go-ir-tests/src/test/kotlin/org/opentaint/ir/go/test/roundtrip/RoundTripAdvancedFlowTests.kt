package org.opentaint.ir.go.test.roundtrip

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.opentaint.ir.go.test.*

@Tag("roundtrip")
@ExtendWith(GoIRTestExtension::class)
class RoundTripAdvancedFlowTests {

    @TestFactory
    fun tests(builder: GoIRTestBuilder): List<DynamicTest> {
        val cases = listOf(
            // ── Deeply nested if/else chains ────────────────────────────────

            // 1. Deeply nested if/else – 5 levels
            RoundTripTestCase.withInputs("deep-nest-5", """
func rtaf_deepNest5(a, b int) int {
    if a > 0 {
        if b > 0 {
            if a > b {
                if a > 2*b {
                    if a > 3*b {
                        return a - 3*b
                    }
                    return a - 2*b
                }
                return a - b
            }
            return b - a
        }
        return a
    }
    return b
}""",
                "rtaf_deepNest5(%s, %s)",
                listOf(listOf("100", "10"), listOf("5", "20"), listOf("0", "-3"), listOf("-5", "7"), listOf("30", "10")),
            ),

            // 2. Deeply nested if/else – 6 levels, 3 args
            RoundTripTestCase.withInputs("deep-nest-6", """
func rtaf_deepNest6(a, b, c int) int {
    if a > 0 {
        if b > 0 {
            if c > 0 {
                if a > b {
                    if b > c {
                        if a > b+c {
                            return a - b - c
                        }
                        return b + c - a
                    }
                    return a + c - b
                }
                return b - a + c
            }
            return a + b
        }
        return a
    }
    return -a + 1
}""",
                "rtaf_deepNest6(%s, %s, %s)",
                listOf(listOf("100", "50", "20"), listOf("10", "20", "30"), listOf("-5", "10", "15"), listOf("5", "-3", "10"), listOf("50", "30", "40")),
            ),

            // 3. Deep nest with arithmetic at each level
            RoundTripTestCase.withInputs("deep-nest-arith", """
func rtaf_deepNestArith(x, y int) int {
    r := x + y
    if x > 100 {
        r += x
        if y > 50 {
            r *= 2
            if x > y {
                r -= y
                if x-y > 100 {
                    r += 10
                    if r > 1000 {
                        return r % 1000
                    }
                    return r
                }
                return r + 5
            }
            return r - 3
        }
        return r + 1
    }
    return r
}""",
                "rtaf_deepNestArith(%s, %s)",
                listOf(listOf("200", "100"), listOf("50", "30"), listOf("150", "60"), listOf("300", "50"), listOf("10", "10")),
            ),

            // ── For loops with early break + flag ───────────────────────────

            // 4. Find smallest factor via loop + break + flag
            RoundTripTestCase.withInputs("loop-break-flag", """
func rtaf_loopBreakFlag(n int) int {
    if n < 0 { n = -n }
    if n < 2 { return 0 }
    if n > 200 { n = 200 }
    flag := 0
    result := 0
    for i := 2; i <= n; i++ {
        if n%i == 0 {
            flag = 1
            result = i
            break
        }
    }
    if flag == 1 {
        return result
    }
    return -1
}""",
                "rtaf_loopBreakFlag(%s)",
                listOf(listOf("12"), listOf("7"), listOf("100"), listOf("1"), listOf("0")),
            ),

            // 5. Two independent flag searches in one loop
            RoundTripTestCase.withInputs("loop-break-flag-two", """
func rtaf_loopBreakFlagTwo(a, b int) int {
    if a < 0 { a = -a }
    if b < 0 { b = -b }
    if a > 100 { a = 100 }
    if b > 100 { b = 100 }
    if b == 0 { b = 1 }
    foundA := 0
    foundB := 0
    for i := 1; i <= a; i++ {
        if i*i > a && foundA == 0 {
            foundA = i
        }
        if i*b > a && foundB == 0 {
            foundB = i
        }
        if foundA != 0 && foundB != 0 {
            break
        }
    }
    return foundA*1000 + foundB
}""",
                "rtaf_loopBreakFlagTwo(%s, %s)",
                listOf(listOf("50", "3"), listOf("100", "7"), listOf("1", "1"), listOf("25", "5"), listOf("10", "10")),
                randomRange = 1..500,
            ),

            // ── For loops with continue + accumulation ──────────────────────

            // 6. Skip multiples of 3 and 7, accumulate the rest
            RoundTripTestCase.withInputs("continue-accum", """
func rtaf_continueAccum(n int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    s := 0
    for i := 1; i <= n; i++ {
        if i%3 == 0 { continue }
        if i%7 == 0 { continue }
        s += i
    }
    return s
}""",
                "rtaf_continueAccum(%s)",
                listOf(listOf("20"), listOf("50"), listOf("100"), listOf("1")),
            ),

            // 7. Continue + accumulation with modular arithmetic
            RoundTripTestCase.withInputs("continue-accum-mod", """
func rtaf_continueAccumMod(n, m int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    if m <= 0 { m = 1 }
    s := 0
    for i := 1; i <= n; i++ {
        if i%m == 0 { continue }
        s += i * i
    }
    return s % 100000
}""",
                "rtaf_continueAccumMod(%s, %s)",
                listOf(listOf("30", "4"), listOf("50", "3"), listOf("100", "7"), listOf("10", "2")),
                randomRange = 1..500,
            ),

            // ── Nested for loops (2–3 levels) with bounded iterations ───────

            // 8. 2-level nested loop with alternating accumulation
            RoundTripTestCase.withInputs("nested-loop-2", """
func rtaf_nestedLoop2(n, m int) int {
    if n < 0 { n = -n }
    if m < 0 { m = -m }
    if n > 50 { n = 50 }
    if m > 50 { m = 50 }
    s := 0
    for i := 0; i < n; i++ {
        for j := 0; j < m; j++ {
            if (i+j)%2 == 0 {
                s += i + j
            } else {
                s += i * j
            }
        }
    }
    return s
}""",
                "rtaf_nestedLoop2(%s, %s)",
                listOf(listOf("10", "10"), listOf("5", "20"), listOf("1", "1"), listOf("15", "8")),
            ),

            // 9. 3-level nested loop with XOR accumulation
            RoundTripTestCase.withInputs("nested-loop-3", """
func rtaf_nestedLoop3(n int) int {
    if n < 0 { n = -n }
    if n > 30 { n = 30 }
    s := 0
    for i := 0; i < n; i++ {
        for j := i; j < n; j++ {
            for k := j; k < n; k++ {
                if i+j+k > 0 {
                    s += (i ^ j ^ k) + 1
                }
            }
        }
    }
    return s
}""",
                "rtaf_nestedLoop3(%s)",
                listOf(listOf("5"), listOf("10"), listOf("15"), listOf("1"), listOf("0")),
            ),

            // ── Nested loops with break (goto-like pattern) ─────────────────

            // 10. Nested loops, inner break + outer flag break
            RoundTripTestCase.withInputs("nested-break", """
func rtaf_nestedBreak(a, b int) int {
    if a < 0 { a = -a }
    if b < 0 { b = -b }
    if a > 100 { a = 100 }
    if b > 100 { b = 100 }
    result := 0
    done := 0
    for i := 1; i <= a; i++ {
        if done == 1 { break }
        for j := 1; j <= b; j++ {
            if i*j > a+b {
                result = i*1000 + j
                done = 1
                break
            }
        }
    }
    return result
}""",
                "rtaf_nestedBreak(%s, %s)",
                listOf(listOf("20", "10"), listOf("5", "5"), listOf("100", "50"), listOf("1", "1")),
            ),

            // ── Diamond control flow (if-else that merges) ──────────────────

            // 11. Simple diamond: two paths merge
            RoundTripTestCase.withInputs("diamond-flow", """
func rtaf_diamondFlow(a, b int) int {
    x := a + b
    y := a - b
    if a > b {
        x = x * 2
        y = y + 10
    } else {
        x = x + 5
        y = y * 3
    }
    return x + y
}""",
                "rtaf_diamondFlow(%s, %s)",
                listOf(listOf("10", "5"), listOf("5", "10"), listOf("7", "7"), listOf("-3", "8")),
            ),

            // 12. Triple sequential diamonds
            RoundTripTestCase.withInputs("diamond-multi", """
func rtaf_diamondMulti(a, b, c int) int {
    x := 0
    if a > 0 {
        x = a
    } else {
        x = -a + 1
    }
    y := 0
    if b > c {
        y = b - c
    } else {
        y = c - b
    }
    z := 0
    if x > y {
        z = x - y
    } else {
        z = y - x
    }
    return x + y*10 + z*100
}""",
                "rtaf_diamondMulti(%s, %s, %s)",
                listOf(listOf("5", "10", "3"), listOf("-5", "3", "10"), listOf("0", "0", "0"), listOf("100", "50", "50")),
            ),

            // ── Loop unrolling ──────────────────────────────────────────────

            // 13. Manual 4-wide unroll with scalar tail
            RoundTripTestCase.withInputs("loop-unroll", """
func rtaf_loopUnroll(n int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    s := 0
    i := 0
    for i+3 < n {
        s += (i + 1) + (i + 2) + (i + 3) + (i + 4)
        i += 4
    }
    for i < n {
        s += i + 1
        i++
    }
    return s
}""",
                "rtaf_loopUnroll(%s)",
                listOf(listOf("10"), listOf("7"), listOf("100"), listOf("1"), listOf("0")),
            ),

            // ── State machine simulation ────────────────────────────────────

            // 14. 3-state machine driven by deterministic pseudo-random
            RoundTripTestCase.withInputs("state-machine-3", """
func rtaf_stateMachine3(n, seed int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    state := 0
    count := 0
    v := seed
    for i := 0; i < n; i++ {
        v = (v*31 + 17) % 100
        if state == 0 {
            if v > 60 { state = 1 }
        } else if state == 1 {
            if v < 30 { state = 2; count++ }
            if v > 80 { state = 0 }
        } else {
            if v > 50 { state = 0 }
        }
    }
    return count*100 + state
}""",
                "rtaf_stateMachine3(%s, %s)",
                listOf(listOf("50", "7"), listOf("100", "42"), listOf("10", "1"), listOf("200", "99")),
            ),

            // 15. 5-state machine
            RoundTripTestCase.withInputs("state-machine-5", """
func rtaf_stateMachine5(n int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    state := 0
    total := 0
    for i := 0; i < n; i++ {
        v := (i*37 + 13) % 10
        if state == 0 {
            if v > 5 { state = 1 }
            total += 1
        } else if state == 1 {
            if v < 3 { state = 2 }
            if v > 7 { state = 3 }
            total += 2
        } else if state == 2 {
            if v > 4 { state = 0 }
            total += 3
        } else if state == 3 {
            if v < 5 { state = 4 }
            total += 4
        } else {
            if v > 6 { state = 0 }
            total += 5
        }
    }
    return total*10 + state
}""",
                "rtaf_stateMachine5(%s)",
                listOf(listOf("20"), listOf("50"), listOf("100"), listOf("1")),
            ),

            // ── Binary search on computed values ────────────────────────────

            // 16. Binary search for integer square root
            RoundTripTestCase.withInputs("binary-search", """
func rtaf_binarySearch(target, n int) int {
    if n < 0 { n = -n }
    if n == 0 { n = 1 }
    if n > 10000 { n = 10000 }
    if target < 0 { target = -target }
    lo := 0
    hi := n
    for lo < hi {
        mid := lo + (hi-lo)/2
        v := mid * mid
        if v == target {
            return mid
        } else if v < target {
            lo = mid + 1
        } else {
            hi = mid
        }
    }
    return lo
}""",
                "rtaf_binarySearch(%s, %s)",
                listOf(listOf("25", "100"), listOf("100", "1000"), listOf("0", "50"), listOf("1", "10"), listOf("49", "500")),
                randomRange = 1..500,
            ),

            // ── Newton's method for integer sqrt ────────────────────────────

            // 17. Iterative Newton's method
            RoundTripTestCase.withInputs("newton-sqrt", """
func rtaf_newtonSqrt(n int) int {
    if n <= 0 { return 0 }
    x := n
    for i := 0; i < 50; i++ {
        nx := (x + n/x) / 2
        if nx >= x { break }
        x = nx
    }
    return x
}""",
                "rtaf_newtonSqrt(%s)",
                listOf(listOf("100"), listOf("49"), listOf("2"), listOf("10000"), listOf("1")),
                randomRange = 1..500,
            ),

            // ── Sieve-like pattern on single values ─────────────────────────

            // 18. Count prime factors of a single value (trial division)
            RoundTripTestCase.withInputs("sieve-single", """
func rtaf_sieveSingle(n int) int {
    if n < 0 { n = -n }
    if n < 2 { return 0 }
    if n > 10000 { n = 10000 }
    count := 0
    d := 2
    for d*d <= n {
        for n%d == 0 {
            count++
            n /= d
        }
        d++
    }
    if n > 1 { count++ }
    return count
}""",
                "rtaf_sieveSingle(%s)",
                listOf(listOf("12"), listOf("100"), listOf("7"), listOf("1"), listOf("360")),
            ),

            // ── Fibonacci variations ────────────────────────────────────────

            // 19. Iterative Fibonacci, memo-less, bounded
            RoundTripTestCase.withInputs("fib-bounded", """
func rtaf_fibBounded(n int) int {
    if n < 0 { n = -n }
    if n > 45 { n = 45 }
    if n <= 1 { return n }
    a := 0
    b := 1
    for i := 2; i <= n; i++ {
        a, b = b, a+b
    }
    return b
}""",
                "rtaf_fibBounded(%s)",
                listOf(listOf("0"), listOf("1"), listOf("10"), listOf("20"), listOf("30")),
            ),

            // 20. Tribonacci variant
            RoundTripTestCase.withInputs("tribonacci", """
func rtaf_tribonacci(n int) int {
    if n < 0 { n = -n }
    if n > 30 { n = 30 }
    if n == 0 { return 0 }
    if n <= 2 { return 1 }
    a := 0
    b := 1
    c := 1
    for i := 3; i <= n; i++ {
        a, b, c = b, c, a+b+c
    }
    return c
}""",
                "rtaf_tribonacci(%s)",
                listOf(listOf("0"), listOf("1"), listOf("5"), listOf("10"), listOf("20")),
            ),

            // ── Digit extraction ────────────────────────────────────────────

            // 21. Get Nth digit from the right (0-indexed)
            RoundTripTestCase.withInputs("digit-nth", """
func rtaf_digitNth(n, pos int) int {
    if n < 0 { n = -n }
    if pos < 0 { pos = -pos }
    for i := 0; i < pos; i++ {
        n /= 10
    }
    return n % 10
}""",
                "rtaf_digitNth(%s, %s)",
                listOf(listOf("12345", "0"), listOf("12345", "2"), listOf("12345", "4"), listOf("99", "1"), listOf("7", "0")),
                randomRange = 1..500,
            ),

            // ── Number base conversion ──────────────────────────────────────

            // 22. Convert integer to its digit representation in base 2–10
            RoundTripTestCase.withInputs("base-convert", """
func rtaf_baseConvert(n, base int) int {
    if n < 0 { n = -n }
    if base < 2 { base = 2 }
    if base > 10 { base = 10 }
    result := 0
    mul := 1
    for n > 0 {
        result += (n % base) * mul
        n /= base
        mul *= 10
    }
    return result
}""",
                "rtaf_baseConvert(%s, %s)",
                listOf(listOf("10", "2"), listOf("255", "8"), listOf("100", "3"), listOf("42", "5"), listOf("0", "2")),
                randomRange = 1..500,
            ),

            // ── Palindrome checking (on numbers) ───────────────────────────

            // 23. Check whether a non-negative number is a palindrome
            RoundTripTestCase.withInputs("palindrome-check", """
func rtaf_isPalindrome(n int) int {
    if n < 0 { return 0 }
    orig := n
    rev := 0
    for n > 0 {
        rev = rev*10 + n%10
        n /= 10
    }
    if orig == rev { return 1 }
    return 0
}""",
                "rtaf_isPalindrome(%s)",
                listOf(listOf("121"), listOf("123"), listOf("0"), listOf("1221"), listOf("12321")),
            ),

            // ── Counting specific bits ──────────────────────────────────────

            // 24. Population count (number of set bits)
            RoundTripTestCase.withInputs("count-set-bits", """
func rtaf_countSetBits(n int) int {
    if n < 0 { n = -n }
    c := 0
    for n > 0 {
        c += n & 1
        n >>= 1
    }
    return c
}""",
                "rtaf_countSetBits(%s)",
                listOf(listOf("0"), listOf("7"), listOf("255"), listOf("1024"), listOf("127")),
            ),

            // ── Gray code conversion ────────────────────────────────────────

            // 25. Binary-to-Gray code
            RoundTripTestCase.withInputs("gray-code", """
func rtaf_grayCode(n int) int {
    if n < 0 { n = -n }
    return n ^ (n >> 1)
}""",
                "rtaf_grayCode(%s)",
                listOf(listOf("0"), listOf("1"), listOf("2"), listOf("7"), listOf("15")),
            ),

            // ── Hamming distance ────────────────────────────────────────────

            // 26. Hamming distance (differing-bit count)
            RoundTripTestCase.withInputs("hamming-dist", """
func rtaf_hammingDist(a, b int) int {
    if a < 0 { a = -a }
    if b < 0 { b = -b }
    x := a ^ b
    c := 0
    for x > 0 {
        c += x & 1
        x >>= 1
    }
    return c
}""",
                "rtaf_hammingDist(%s, %s)",
                listOf(listOf("1", "4"), listOf("7", "0"), listOf("255", "0"), listOf("10", "10"), listOf("15", "8")),
            ),

            // ── Leading / trailing zero counting ────────────────────────────

            // 27. Count leading zeros in a 32-bit window
            RoundTripTestCase.withInputs("leading-zeros", """
func rtaf_leadingZeros(n int) int {
    if n <= 0 { return 32 }
    c := 0
    for bit := 31; bit >= 0; bit-- {
        if n&(1<<uint(bit)) != 0 {
            return c
        }
        c++
    }
    return 32
}""",
                "rtaf_leadingZeros(%s)",
                listOf(listOf("1"), listOf("256"), listOf("65535"), listOf("1024"), listOf("0")),
            ),

            // 28. Count trailing zeros
            RoundTripTestCase.withInputs("trailing-zeros", """
func rtaf_trailingZeros(n int) int {
    if n <= 0 { return -1 }
    c := 0
    for n%2 == 0 {
        c++
        n /= 2
    }
    return c
}""",
                "rtaf_trailingZeros(%s)",
                listOf(listOf("8"), listOf("12"), listOf("1"), listOf("1024"), listOf("6")),
                randomRange = 1..500,
            ),

            // ── Multiple return values from different branches ──────────────

            // 29. Different arithmetic computed per branch, then merged
            RoundTripTestCase.withInputs("multi-branch-return", """
func rtaf_multiBranchReturn(a, b, c int) int {
    x := 0
    y := 0
    if a > b && a > c {
        x = a * 2
        y = b + c
    } else if b > a && b > c {
        x = b * 3
        y = a - c
    } else if c > a && c > b {
        x = c + a + b
        y = c - a
    } else {
        x = a + b + c
        y = 1
    }
    if x > y {
        return x - y
    }
    return y - x
}""",
                "rtaf_multiBranchReturn(%s, %s, %s)",
                listOf(listOf("10", "5", "3"), listOf("3", "10", "5"), listOf("3", "5", "10"), listOf("5", "5", "5"), listOf("-3", "7", "2")),
            ),

            // ── Complex flag management ─────────────────────────────────────

            // 30. Multiple flags + streak tracking in a single loop
            RoundTripTestCase.withInputs("flag-complex", """
func rtaf_flagComplex(n, m int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    if m < 0 { m = -m }
    if m == 0 { m = 1 }
    sawEven := 0
    sawBig := 0
    streak := 0
    maxStreak := 0
    for i := 1; i <= n; i++ {
        v := (i * m) % 100
        if v%2 == 0 {
            sawEven = 1
            streak++
        } else {
            if streak > maxStreak { maxStreak = streak }
            streak = 0
        }
        if v > 75 { sawBig = 1 }
    }
    if streak > maxStreak { maxStreak = streak }
    return sawEven*10000 + sawBig*1000 + maxStreak
}""",
                "rtaf_flagComplex(%s, %s)",
                listOf(listOf("50", "7"), listOf("100", "3"), listOf("10", "11"), listOf("200", "2")),
                randomRange = 1..500,
            ),

            // ── Nested diamond control flow ─────────────────────────────────

            // 31. Diamonds inside diamonds
            RoundTripTestCase.withInputs("nested-diamond", """
func rtaf_nestedDiamond(a, b int) int {
    p := 0
    q := 0
    if a > 0 {
        if b > 0 {
            p = a + b
        } else {
            p = a - b
        }
        q = p * 2
    } else {
        if b > 0 {
            p = b - a
        } else {
            p = -a - b
        }
        q = p + 1
    }
    r := 0
    if q > 100 {
        r = q - 100
    } else {
        r = 100 - q
    }
    return p + r
}""",
                "rtaf_nestedDiamond(%s, %s)",
                listOf(listOf("10", "20"), listOf("-5", "15"), listOf("30", "-10"), listOf("-7", "-3"), listOf("0", "0")),
            ),

            // ── Branch-dependent accumulation ───────────────────────────────

            // 32. Accumulate into two buckets depending on threshold
            RoundTripTestCase.withInputs("branch-accum", """
func rtaf_branchAccum(n, threshold int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    sumLow := 0
    sumHigh := 0
    countLow := 0
    countHigh := 0
    for i := 1; i <= n; i++ {
        v := (i*41 + 7) % 100
        if v < threshold {
            sumLow += v
            countLow++
        } else {
            sumHigh += v
            countHigh++
        }
    }
    if countLow > countHigh {
        return sumLow
    }
    return sumHigh
}""",
                "rtaf_branchAccum(%s, %s)",
                listOf(listOf("50", "50"), listOf("100", "30"), listOf("100", "70"), listOf("10", "50")),
            ),

            // ── Loops with both break and continue ──────────────────────────

            // 33. Mixed break/continue with bounded accumulation
            RoundTripTestCase.withInputs("break-continue-mix", """
func rtaf_breakContinueMix(n int) int {
    if n < 0 { n = -n }
    if n > 200 { n = 200 }
    s := 0
    for i := 1; i <= n; i++ {
        if i%5 == 0 { continue }
        if i%2 == 0 {
            s += i
        } else {
            s -= i
        }
        if s < -500 { break }
        if s > 500 { break }
    }
    return s
}""",
                "rtaf_breakContinueMix(%s)",
                listOf(listOf("10"), listOf("50"), listOf("100"), listOf("200"), listOf("1")),
            ),

            // ── Convergent iteration ────────────────────────────────────────

            // 34. Newton sqrt returning value*1000 + iteration count
            RoundTripTestCase.withInputs("convergent-iter", """
func rtaf_convergentIter(n int) int {
    if n <= 0 { return 0 }
    if n > 10000 { n = 10000 }
    x := n
    steps := 0
    for steps < 100 {
        nx := (x + n/x) / 2
        if nx == x { break }
        x = nx
        steps++
    }
    return x*1000 + steps
}""",
                "rtaf_convergentIter(%s)",
                listOf(listOf("100"), listOf("1"), listOf("9999"), listOf("4"), listOf("50")),
                randomRange = 1..500,
            ),

            // ── Bit reversal ────────────────────────────────────────────────

            // 35. Reverse the low 8 bits
            RoundTripTestCase.withInputs("bit-reverse-8", """
func rtaf_bitReverse8(n int) int {
    if n < 0 { n = -n }
    n = n & 0xFF
    r := 0
    for i := 0; i < 8; i++ {
        r = (r << 1) | (n & 1)
        n >>= 1
    }
    return r
}""",
                "rtaf_bitReverse8(%s)",
                listOf(listOf("0"), listOf("1"), listOf("128"), listOf("255"), listOf("170")),
            ),

            // ── Digital root ────────────────────────────────────────────────

            // 36. Repeated digit-sum until single digit
            RoundTripTestCase.withInputs("digital-root", """
func rtaf_digitalRoot(n int) int {
    if n < 0 { n = -n }
    for n >= 10 {
        s := 0
        for n > 0 {
            s += n % 10
            n /= 10
        }
        n = s
    }
    return n
}""",
                "rtaf_digitalRoot(%s)",
                listOf(listOf("0"), listOf("9"), listOf("123"), listOf("9999"), listOf("38")),
            ),

            // ── Counting divisors ───────────────────────────────────────────

            // 37. Bounded divisor counting via trial-to-sqrt
            RoundTripTestCase.withInputs("count-divisors", """
func rtaf_countDivisors(n int) int {
    if n < 0 { n = -n }
    if n == 0 { return 0 }
    if n > 10000 { n = 10000 }
    c := 0
    for i := 1; i*i <= n; i++ {
        if n%i == 0 {
            c += 2
            if i*i == n { c-- }
        }
    }
    return c
}""",
                "rtaf_countDivisors(%s)",
                listOf(listOf("1"), listOf("12"), listOf("100"), listOf("7"), listOf("360")),
            ),

            // ── Collatz variant ─────────────────────────────────────────────

            // 38. Collatz step-count with iteration bound
            RoundTripTestCase.withInputs("collatz-variant", """
func rtaf_collatzVariant(n int) int {
    if n <= 0 { return 0 }
    if n > 10000 { n = 10000 }
    steps := 0
    for n != 1 && steps < 500 {
        if n%2 == 0 {
            n /= 2
        } else {
            n = 3*n + 1
        }
        steps++
    }
    return steps
}""",
                "rtaf_collatzVariant(%s)",
                listOf(listOf("1"), listOf("6"), listOf("27"), listOf("100"), listOf("7")),
                randomRange = 1..500,
            ),

            // ── Complex step function ───────────────────────────────────────

            // 39. Many thresholds with conditional multipliers
            RoundTripTestCase.withInputs("step-function", """
func rtaf_stepFunction(n int) int {
    if n < 0 { n = -n }
    r := 0
    if n > 1000 { r += 100 }
    if n > 500 { r += 50 }
    if n > 200 { r += 20 }
    if n > 100 { r += 10 }
    if n > 50 { r += 5 }
    if n > 20 { r += 2 }
    if n > 10 { r += 1 }
    if n%2 == 0 { r *= 2 }
    if n%3 == 0 { r += 7 }
    return r
}""",
                "rtaf_stepFunction(%s)",
                listOf(listOf("5"), listOf("50"), listOf("200"), listOf("1000"), listOf("0")),
            ),

            // ── Interleaved sequential loops ────────────────────────────────

            // 40. Three dependent sequential loops
            RoundTripTestCase.withInputs("interleaved-loops", """
func rtaf_interleavedLoops(n int) int {
    if n < 0 { n = -n }
    if n > 100 { n = 100 }
    s := 0
    for i := 0; i < n; i++ {
        s += i * i
    }
    t := 0
    for i := 0; i < n; i++ {
        t += s - i
    }
    u := 0
    for i := 0; i < n; i++ {
        if t > s {
            u += i
        } else {
            u -= i
        }
    }
    return (s % 10000) + (t % 10000) + (u % 10000)
}""",
                "rtaf_interleavedLoops(%s)",
                listOf(listOf("5"), listOf("10"), listOf("50"), listOf("1"), listOf("0")),
            ),
        )
        return BatchRoundTripRunner.runBatchAndCreateTests(cases, builder)
    }
}
