# 8. Design ↔ Test Alignment Matrix

> This section maps each test category to the engine components required to support it,
> identifies gaps between the initial design and the test system requirements,
> and specifies any design adjustments needed.

---

## 8.1 Feature → Component Matrix

| Test Category | GoFlowFunctionUtils | GoCallResolver | GoMethodSequentFF | GoMethodCallFF | GoTaintRulesProvider | Special Requirements |
|:---|:---:|:---:|:---:|:---:|:---:|:---|
| **A: Basic Intraprocedural** | `accessPathBase`, `exprToAccess` | DIRECT only | `handleAssign` (Simple) | Source/Sink rules | `sourceRulesForCall`, `sinkRulesForCall` | — |
| **B: Interprocedural** | Same + `extractCallInfo` | DIRECT | `handleReturn` | `mapFactToCallee`, `applyPassRules` | Same | Multi-return: `GoIRExtractExpr` |
| **C: Control Flow** | Same | — | `handlePhi`, `handleAssign` | — | — | Phi nodes for SSA merge points |
| **D: Expressions** | `exprToAccess` (conversions, casts) | — | `handleAssign` (type changes) | — | — | `GoIRConvertExpr`, `GoIRChangeTypeExpr` |
| **E: Defer** | `extractCallInfo` for `GoIRDefer` | DIRECT | — | Treat `GoIRDefer` as call | — | **Defer treated as normal call at `defer` point** |
| **F: Cross-Package** | Same | DIRECT (cross-pkg) | Same | Same | Same | `"./..."` build pattern, sub-package support |
| **G: Generics** | Same | DIRECT (instantiated) | Same | Same | Same | Go IR instantiates generics — no special handling |
| **H: Interface Dispatch** | Same | **INVOKE** | Same | Same + receiver mapping | Same | `buildInterfaceImplementorsMap`, structural subtyping |
| **I: Field Sensitivity** | `fieldAccessor`, `RefAccess` | — | `handleRefAssign`, `handleStore` | — | — | `FieldAccessor` creation, strong updates |
| **J: Collections** | `ElementAccessor` for index/lookup | — | `handleRefAssign`, `handleStore`, `handleMapUpdate` | — | — | Element vs container distinction, weak updates |
| **K: Pointers** | `accessForAddr`, `DEREF` | — | `handleStore` (pointer store) | — | — | Address-of tracking via `GoIRFieldAddrExpr` |
| **L: Closures** | `GoIRFreeVarValue` → `Argument(n+idx)` | DYNAMIC (future) | — | Closure bindings | — | `GoIRMakeClosureExpr` bindings |
| **M: Higher-Order** | Same as closures | DYNAMIC | — | — | — | Function value tracking |
| **N: Goroutines** | `extractCallInfo` for `GoIRGo` | — | — | `GoIRGo` as call | — | Channel send/receive taint |

---

## 8.2 Phase 1 Alignment — Gap Analysis

### What the design already covers for Phase 1:

| Requirement | Status | Notes |
|-------------|--------|-------|
| Basic assignment propagation | ✅ Covered | `handleSimpleAssign` in sequent FF |
| Source rule at call site | ✅ Covered | `applySourceRules` in call FF |
| Sink rule at call site | ✅ Covered | `applySinkRules` in call FF |
| DIRECT call resolution | ✅ Covered | `GoCallResolver.resolveDirect` |
| Return value mapping | ✅ Covered | `handleReturn` + `GoMethodCallFactMapper` |
| Multi-return values | ✅ Covered | `GoIRExtractExpr` handled as simple copy |
| Phi nodes (control flow merge) | ✅ Covered | `handlePhi` |
| Type conversions | ✅ Covered | `exprToAccess` returns `Simple` for conversions |
| String concatenation | ✅ Covered | Special case in `handleAssign` for `BinOpExpr(ADD, string)` |
| Defer | ✅ Covered | `GoIRDefer` routes through call FF (immediate arg eval) |
| Cross-package | ✅ Covered | `"./..."` pattern, `findFunctionByFullName` across packages |
| Generics | ✅ Covered | Go IR instantiates generics, handled transparently |

### Gaps identified and adjustments:

#### Gap 1: Multiple return values — `GoIRExtractExpr` precision

**Problem**: `GoIRExtractExpr(tuple, idx)` extracts one value from a multi-return tuple. The design treats it as `Simple(base(tuple))`, which means if any return value is tainted, ALL extracted values are tainted. This is imprecise.

**Fix**: Use `ElementAccessor` or a custom approach. When the callee returns `(tainted, clean)`, the Return base captures the whole tuple. The caller's `GoIRExtractExpr(idx=0)` should get tainted, but `GoIRExtractExpr(idx=1)` should not.

**Approach for MVP**: Treat multi-return as a single Return base. The `GoIRExtractExpr` propagates taint from the tuple to the register. This over-approximates — both `first` and `second` get tainted if any return value is tainted. This is acceptable for Phase 1.

**Approach for Phase 2**: Model each return value as `Return.[idx]` with `ElementAccessor` or a custom `ReturnIndexAccessor`. The callee's `handleReturn` writes taint to `Return.[idx]`, and the caller's `GoIRExtractExpr` reads `Return.[idx]`. This requires extending the `Return` base or using field accessors.

#### Gap 2: Pass-through rules for unresolved calls

**Problem**: When a call is unresolved (DYNAMIC mode or no matching callee), the call flow function must decide what happens to facts. The design emits `CallToReturnFFact` (fact survives call). But for functions with no pass rules and no body (like `sanitize()`), the taint should NOT survive.

**Fix**: For unresolved calls with no pass rules and no body:
- If the callee HAS a body (can be analyzed interprocedurally) → `CallToStartFFact` (enter callee)
- If the callee has NO body and NO pass rule → `CallToReturnFFact` (fact survives — over-approximation; the callee might not propagate taint, but we can't know without a body or rule)
- If there IS a pass rule → apply it and skip interprocedural analysis

For `sanitize()` specifically: it HAS a body (returns "clean"). The interprocedural analysis will produce a summary with NO `Argument(0) → Return` edge (since the body returns a constant). So the fact does NOT survive. No special handling needed — the framework handles this correctly via interprocedural analysis.

#### Gap 3: GoIRCall is both DefInst and call

**Problem**: `GoIRCall` is a `GoIRDefInst` (it defines a register) AND contains a `GoIRCallInfo`. The framework routes it to `MethodCallFlowFunction` (because `getCallExpr` returns non-null). But the call's result register assignment is handled WHERE?

**Answer**: In `GoMethodCallFlowFunction.applySourceRules`. The source rule maps `Result` → the call's result register. For interprocedural flow, the summary maps callee's `Return` → caller's result register via `GoMethodCallFactMapper.mapMethodExitToReturnFlowFact`. So the result register gets tainted through EITHER source rules OR interprocedural summary application. No explicit assignment handling in the sequent FF is needed for `GoIRCall`.

#### Gap 4: `GoIRRunDefers` instruction

**Problem**: `GoIRRunDefers` is a standalone instruction that triggers execution of all deferred calls. It has no operands. The design doesn't explicitly handle it.

**Current behavior**: `GoIRRunDefers` has no call info (`extractCallInfo` returns null), so `getCallExpr` returns null, and it routes to the sequent flow function. The sequent FF's `propagate` method falls into `else → Unchanged`.

**This is correct for MVP**: Deferred function calls are already processed at their `GoIRDefer` instruction point. `GoIRRunDefers` just marks where they actually execute. Since we treat defers as normal calls at the `defer` point, the actual execution point doesn't need special handling.

---

## 8.3 Phase 2 Alignment

| Requirement | Gap? | Adjustment |
|-------------|------|------------|
| **Interface dispatch (INVOKE)** | No gap | Implemented in `GoCallResolver.resolveInvoke` |
| **Field sensitivity** | Minor: need `fieldAccessor` from struct type | Implement `resolveStructTypeName` properly |
| **Collection element vs container** | Critical: must NOT propagate element taint to container | Ensure `handleStore` writes element-level (`RefAccess` with `ElementAccessor`), and `handleRefAssign` reads element-level only with accessor match |
| **Pointer aliases** | Not in MVP | Deferred; pointer stores use `accessForAddr` which handles `GoIRFieldAddrExpr` and `GoIRIndexAddrExpr` |
| **Method receiver taint** | Via `This` base | `GoInstanceCallExpr` maps receiver → `This`; `GoMethodCallFactMapper` handles `This` ↔ receiver |

### Collection Sensitivity — Detailed Alignment

The test system defines this model:
- `a[0] = source()` → taints `a.[*]` (element-level), NOT `a` (container)
- `sink(a[0])` → reachable (reads element-level)
- `sink(a[1])` → reachable (key-insensitive)
- `sink(a)` → NOT reachable (container is not tainted)

Engine implementation must ensure:
1. **Write to element** (`GoIRStore` with `GoIRIndexAddrExpr` or `GoIRMapUpdate`):
   - Creates `RefAccess(base(a), ElementAccessor)` as destination
   - Produces `FactToFact(source.*, base(a).[*].*)` — prepends `ElementAccessor`
   - Does NOT create `FactToFact(source.*, base(a).*)` — no container-level taint

2. **Read from element** (`GoIRIndexExpr`, `GoIRLookupExpr`):
   - Creates `RefAccess(base(a), ElementAccessor)` as source
   - Produces taint only if fact starts with `ElementAccessor` on `base(a)`
   - `handleRefAssign` checks `currentFact.startsWithAccessor(ElementAccessor)`

3. **Read container** (`sink(a)` where `a` is passed directly):
   - `factIsRelevantToMethodCall` checks `factAp.base == base(a)`
   - If the fact is `base(a).[*].![taint]/*`, the base matches
   - `checkFactMark` in the call flow function checks `startsWithAccessor(TaintMarkAccessor("taint"))`
   - The fact starts with `ElementAccessor`, NOT `TaintMarkAccessor` → returns false
   - For abstract facts: `checkFactMark` adds `TaintMarkAccessor` to exclusions, triggering refinement
   - On refinement, the framework materializes the mark. But the fact has `[*]` before the mark, so the materialized fact still starts with `ElementAccessor` → `startsWithAccessor(TaintMarkAccessor)` still returns false → **sink is NOT triggered** ✓

**This works correctly** because the refinement mechanism respects the accessor ordering. A fact like `base(a).[*].![taint]/*` has `ElementAccessor` before `TaintMarkAccessor`. Refinement materializes accessors in order — `[*]` comes first. The sink check requires the taint mark at the top level, which it is not.

For `sink(a[0])`: the caller reads `a[0]` via `GoIRIndexExpr`, which strips `ElementAccessor` producing `base(reg).![taint]/*`. Now `startsWithAccessor(TaintMarkAccessor)` returns true → **sink IS triggered** ✓

---

## 8.4 Phase 3 Alignment

| Requirement | Gap | Plan |
|-------------|-----|------|
| **Closures** | `GoIRFreeVarValue` mapping to `Argument(n+idx)` | Implement in `accessPathBase`; test with `GoIRMakeClosureExpr` binding propagation |
| **Higher-order functions** | DYNAMIC call resolution | Needs function value tracking or lambda tracker (similar to JVM's `JIRLambdaTracker`) |
| **Goroutines** | `GoIRGo` treated as call | Correct for taint propagation; channel send/receive need `GoIRSend` handling in sequent FF |
| **Channels** | `GoIRSend` and `ARROW` (receive) | `GoIRSend` needs handler in sequent FF; `ARROW` in `exprToAccess` already maps to `Simple(chan)` |

---

## 8.5 Summary of Design Adjustments

| # | Adjustment | Where | Impact |
|---|-----------|-------|--------|
| 1 | Add `GoLanguageManager(cp: GoIRProgram)` constructor parameter | `GoLanguageManager.kt` | Allows access to program in language manager methods |
| 2 | Make `GoApplicationGraph.callResolver` internal | `GoApplicationGraph.kt` | Shared with `GoMethodCallResolver` |
| 3 | Add `accessPathBaseFromValue` (no method param) | `GoFlowFunctionUtils.kt` | Used by `GoMethodCallFactMapper` |
| 4 | Handle `GoIRBinOpExpr(ADD, string)` as special case | `GoMethodSequentFlowFunction` | String concatenation preserves taint from both operands |
| 5 | `calleeName` property handles builtins | `GoCallExpr.kt` or `GoMethodCallFlowFunction` | Builtin pass rules match by name |
| 6 | Abstract fact refinement via exclusion set updates | `GoMethodSequentFlowFunction`, `GoMethodCallFlowFunction` | Triggers framework re-analysis when accessor/mark is behind abstraction point |
| 7 | Multi-return over-approximation in Phase 1 | Design note | `GoIRExtractExpr` propagates all returns; precise model in Phase 2 |
