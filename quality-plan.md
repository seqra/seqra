# Go IR Quality Plan

## Goals
1. Test on 20+ benchmark projects — load full projects, verify no exceptions, measure time
2. Expand round-trip tests to 300+ (target met: **302 tests**)
3. Track all quality issues and fixes

## Q1 — Benchmark Project Testing
- [ ] Run all 20 configured projects through IR loading
- [ ] Record per-project: load success/failure, time, function count, error details
- [ ] Fix any serialization/deserialization bugs found
- [ ] Track results in table below

### Benchmark Results

| # | Project | Status | Functions | Time | Notes |
|---|---------|--------|-----------|------|-------|
| 1 | logrus | pending | | | |
| 2 | cobra | pending | | | |
| 3 | websocket | pending | | | |
| 4 | lo | pending | | | |
| 5 | zap | pending | | | |
| 6 | testify | pending | | | |
| 7 | viper | pending | | | |
| 8 | validator | pending | | | |
| 9 | golang-migrate | pending | | | |
| 10 | gin | pending | | | |
| 11 | fiber | pending | | | |
| 12 | go-kit | pending | | | |
| 13 | redis | pending | | | |
| 14 | pgx | pending | | | |
| 15 | consul | pending | | | |
| 16 | prometheus | pending | | | |
| 17 | etcd | pending | | | |
| 18 | docker-cli | pending | | | |
| 19 | k8s client-go | pending | | | |
| 20 | caddy | pending | | | |

## Q2 — Round-Trip Test Expansion (target: 300+) ✅ DONE

### Final count: 302 tests (25s execution time)

| Category | File | Tests | Status |
|----------|------|-------|--------|
| Basic (original) | RoundTripTests.kt | 6 | ✅ |
| Extended (original) | RoundTripExtendedTests.kt | 13 | ✅ |
| Multi-input (fuzz replacement) | FuzzRoundTripTests.kt | 3 | ✅ |
| Arithmetic & math | RoundTripArithmeticTests.kt | 20 | ✅ |
| Comparisons & boolean | RoundTripComparisonTests.kt | 20 | ✅ |
| Control flow | RoundTripControlFlowTests.kt | 24 | ✅ |
| Loops | RoundTripLoopTests.kt | 25 | ✅ |
| Recursion | RoundTripRecursionTests.kt | 20 | ✅ |
| Strings | RoundTripStringTests.kt | 20 | ✅ |
| Bitwise | RoundTripBitwiseTests.kt | 15 | ✅ |
| Algorithms | RoundTripAlgorithmTests.kt | 25 | ✅ |
| Conversions | RoundTripConversionTests.kt | 15 | ✅ |
| Edge cases | RoundTripEdgeCaseTests.kt | 22 | ✅ |
| Multi-input stress | RoundTripMultiInputTests.kt | 15 | ✅ |
| Math & number theory | RoundTripMathTests.kt | 20 | ✅ |
| Patterns | RoundTripPatternTests.kt | 20 | ✅ |
| Mixed features | RoundTripMixedTests.kt | 19 | ✅ |
| **TOTAL** | | **302** | ✅ |

### Performance: Batched execution
- Tests use `BatchRoundTripRunner` — merges all test functions in a class into one Go program
- Only 2 `go run` invocations per test class (original + reconstructed) instead of 2 per test
- Parallel class execution via JUnit `concurrent` mode
- **302 tests in ~25 seconds** (vs ~10+ min unbatched)

## Q3 — Bugs Found During Quality Phase

| # | Bug | Fix | Found In |
|---|-----|-----|----------|
| 1 | `goto` jumps over `_alloc_` variable declarations | Pre-declare `_alloc_` locals at function top alongside other variables | RoundTripMultiInputTests |

## Progress Log

| Date | Task | Status |
|------|------|--------|
| 2026-03-26 | Created quality-plan.md and design-changes.md | DONE |
| 2026-03-26 | Built BatchRoundTripRunner for fast batched test execution | DONE |
| 2026-03-26 | Replaced fuzz tests with deterministic multi-input tests | DONE |
| 2026-03-26 | Enabled parallel test execution | DONE |
| 2026-03-26 | Fixed codegen bug: goto over _alloc_ declarations | DONE |
| 2026-03-26 | Expanded round-trip tests from 19 to 302 | DONE |
