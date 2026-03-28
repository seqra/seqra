# 5. Taint Rules & Standard Library Pass Rules

---

## 5.1 `GoTaintRulesProvider`

> **File**: `org/opentaint/dataflow/go/rules/GoTaintRulesProvider.kt`

Bridges `GoTaintConfig` to rule queries used by flow functions. Filters rules by callee function name.

```kotlin
class GoTaintRulesProvider(val config: GoTaintConfig) {

    // Pre-index rules by function name for O(1) lookup
    private val sourcesByFunction: Map<String, List<TaintRules.Source>> =
        config.sources.groupBy { it.function }

    private val sinksByFunction: Map<String, List<TaintRules.Sink>> =
        config.sinks.groupBy { it.function }

    private val passByFunction: Map<String, List<TaintRules.Pass>> =
        config.propagators.groupBy { it.function }

    fun sourceRulesForCall(calleeName: String): List<TaintRules.Source> =
        sourcesByFunction[calleeName] ?: emptyList()

    fun sinkRulesForCall(calleeName: String): List<TaintRules.Sink> =
        sinksByFunction[calleeName] ?: emptyList()

    fun passRulesForCall(calleeName: String): List<TaintRules.Pass> =
        passByFunction[calleeName] ?: emptyList()

    fun hasAnyRulesForCall(calleeName: String): Boolean =
        calleeName in sourcesByFunction || calleeName in sinksByFunction || calleeName in passByFunction
}
```

### Design Notes

- **Pre-indexing** — Rules are indexed by function name at construction time. This avoids repeated `filter` calls during analysis, which would be O(n) per call site.

- **Function name format** — Go function names are fully qualified: `"test.source"`, `"fmt.Println"`, `"strings.Replace"`. Method names include the receiver type: `"(*bytes.Buffer).WriteString"` or similar. The exact format depends on `GoIRFunction.fullName`.

- **Wildcard matching** — The MVP uses exact string matching. Future enhancement: support prefix matching (e.g., `"crypto/*"` to match all crypto package functions) or regex patterns.

---

## 5.2 How Rules Are Applied

### Source Rules (in `GoMethodCallFlowFunction.propagateZeroToZero`)

| Step | Action |
|------|--------|
| 1 | Get callee name from `GoCallExpr.calleeName` |
| 2 | Query `rulesProvider.sourceRulesForCall(name)` |
| 3 | For each matching rule: resolve `rule.pos` to `AccessPathBase` |
| 4 | Map `AccessPathBase.Return` → caller's result register |
| 5 | Create `apManager.createAbstractAp(callerBase, ExclusionSet.Universe).prependAccessor(TaintMarkAccessor(rule.mark))` |
| 6 | Emit `CallToReturnZFact(factAp, traceInfo)` |

### Sink Rules (in `GoMethodCallFlowFunction.propagateFactToFact`)

| Step | Action |
|------|--------|
| 1 | Get callee name |
| 2 | Query `rulesProvider.sinkRulesForCall(name)` |
| 3 | For each matching rule: resolve `rule.pos` to caller's argument base |
| 4 | Check if `currentFactAp.base` matches that argument base |
| 5 | Check `factHasMark(currentFactAp, rule.mark)` — handles both concrete and abstract facts |
| 6 | If match: `context.taint.taintSinkTracker.addVulnerability(...)` |

### Pass Rules (in `GoMethodCallFlowFunction.propagateFactToFact`)

| Step | Action |
|------|--------|
| 1 | Get callee name |
| 2 | Query `rulesProvider.passRulesForCall(name)` |
| 3 | For each rule: resolve `rule.from` → caller's argument base |
| 4 | If `currentFactAp.base` matches `from`: resolve `rule.to` → caller's result base |
| 5 | Emit `CallToReturnFFact(initialFact, currentFactAp.rebase(toBase), traceInfo)` |

---

## 5.3 Standard Library Pass Rules — Catalog

These rules model how Go standard library functions propagate taint. They are specified as `TaintRules.Pass` entries in the test configuration (and eventually in a default rule set).

### 5.3.1 String Operations (`strings` package)

| Function | From | To | Notes |
|----------|------|----|-------|
| `strings.Replace` | `Argument(0)` | `Result` | Taint flows from input string to result |
| `strings.ToLower` | `Argument(0)` | `Result` | Case conversion preserves taint |
| `strings.ToUpper` | `Argument(0)` | `Result` | |
| `strings.TrimSpace` | `Argument(0)` | `Result` | |
| `strings.Trim` | `Argument(0)` | `Result` | |
| `strings.TrimLeft` | `Argument(0)` | `Result` | |
| `strings.TrimRight` | `Argument(0)` | `Result` | |
| `strings.TrimPrefix` | `Argument(0)` | `Result` | |
| `strings.TrimSuffix` | `Argument(0)` | `Result` | |
| `strings.Split` | `Argument(0)` | `Result` | Tainted string → tainted slice |
| `strings.SplitN` | `Argument(0)` | `Result` | |
| `strings.SplitAfter` | `Argument(0)` | `Result` | |
| `strings.Join` | `Argument(0)` | `Result` | Tainted slice elements → tainted string |
| `strings.Repeat` | `Argument(0)` | `Result` | |
| `strings.Map` | `Argument(1)` | `Result` | Second arg is the string |
| `strings.NewReader` | `Argument(0)` | `Result` | String → Reader |
| `strings.NewReplacer` | — | — | Complex: skip for MVP |
| `(*strings.Builder).WriteString` | `Argument(0)` | `This` | Taint goes to builder |
| `(*strings.Builder).String` | `This` | `Result` | Taint from builder to result |

### 5.3.2 Format Operations (`fmt` package)

| Function | From | To | Notes |
|----------|------|----|-------|
| `fmt.Sprintf` | `Argument(*)` | `Result` | Any arg taints result. MVP: `Argument(0)` + `Argument(1)` |
| `fmt.Sprint` | `Argument(0)` | `Result` | |
| `fmt.Fprintf` | `Argument(1)` | `Argument(0)` | Taint flows to writer |
| `fmt.Fprint` | `Argument(1)` | `Argument(0)` | |

### 5.3.3 Conversion Operations (`strconv` package)

| Function | From | To | Notes |
|----------|------|----|-------|
| `strconv.Itoa` | `Argument(0)` | `Result` | int→string preserves taint |
| `strconv.FormatInt` | `Argument(0)` | `Result` | |
| `strconv.FormatFloat` | `Argument(0)` | `Result` | |
| `strconv.FormatBool` | `Argument(0)` | `Result` | |
| `strconv.Atoi` | `Argument(0)` | `Result` | string→int preserves taint |
| `strconv.ParseInt` | `Argument(0)` | `Result` | |
| `strconv.ParseFloat` | `Argument(0)` | `Result` | |

### 5.3.4 I/O Operations (`io`, `bufio`, `bytes`)

| Function | From | To | Notes |
|----------|------|----|-------|
| `io.ReadAll` | `Argument(0)` | `Result` | Reader → byte slice |
| `io.Copy` | `Argument(1)` | `Argument(0)` | src → dst |
| `io.WriteString` | `Argument(1)` | `Argument(0)` | string → writer |
| `bufio.NewReader` | `Argument(0)` | `Result` | Wrap reader |
| `bufio.NewScanner` | `Argument(0)` | `Result` | Wrap reader |
| `(*bufio.Reader).ReadString` | `This` | `Result` | |
| `(*bufio.Scanner).Text` | `This` | `Result` | |
| `bytes.NewBuffer` | `Argument(0)` | `Result` | byte slice → Buffer |
| `(*bytes.Buffer).String` | `This` | `Result` | |
| `(*bytes.Buffer).Bytes` | `This` | `Result` | |
| `(*bytes.Buffer).Write` | `Argument(0)` | `This` | |
| `(*bytes.Buffer).WriteString` | `Argument(0)` | `This` | |

### 5.3.5 Built-in Functions

| Function | From | To | Notes |
|----------|------|----|-------|
| `append` | `Argument(0)` | `Result` | Slice taint flows through |
| `append` | `Argument(1)` | `Result.[*]` | Appended element taints result elements |
| `copy` | `Argument(1)` | `Argument(0)` | src → dst |
| `string(bytes)` | `Argument(0)` | `Result` | byte slice → string conversion |
| `[]byte(string)` | `Argument(0)` | `Result` | string → byte slice conversion |

> **Note on `append`**: This is a Go builtin, not a package function. Its full name in the IR is just `"append"` (a `GoIRBuiltinValue`). The call resolver won't resolve it to a `GoIRFunction`. Pass rules must match against the builtin name.

### 5.3.6 Path/URL Operations

| Function | From | To | Notes |
|----------|------|----|-------|
| `path.Join` | `Argument(0)` | `Result` | Any segment taints result |
| `filepath.Join` | `Argument(0)` | `Result` | |
| `filepath.Base` | `Argument(0)` | `Result` | |
| `filepath.Dir` | `Argument(0)` | `Result` | |
| `net/url.QueryEscape` | `Argument(0)` | `Result` | Encoding preserves taint |
| `net/url.PathEscape` | `Argument(0)` | `Result` | |
| `(*net/url.URL).String` | `This` | `Result` | |
| `(*net/url.URL).Query` | `This` | `Result` | URL → query params |

### 5.3.7 Encoding Operations

| Function | From | To | Notes |
|----------|------|----|-------|
| `encoding/base64.StdEncoding.EncodeToString` | `Argument(0)` | `Result` | |
| `encoding/base64.StdEncoding.DecodeString` | `Argument(0)` | `Result` | |
| `encoding/hex.EncodeToString` | `Argument(0)` | `Result` | |
| `encoding/hex.DecodeString` | `Argument(0)` | `Result` | |
| `encoding/json.Marshal` | `Argument(0)` | `Result` | |
| `encoding/json.Unmarshal` | `Argument(0)` | `Argument(1)` | JSON bytes → struct |

---

## 5.4 Rule Application for Builtins

Go builtins (`append`, `copy`, `len`, `cap`, `delete`, `make`, `new`, `panic`, `recover`) are represented as `GoIRBuiltinValue` in the IR. They are not resolved to `GoIRFunction` objects.

For the call flow function to apply pass rules to builtins:

```kotlin
// In GoMethodCallFlowFunction:
val calleeName: String? get() {
    // First try resolved callee
    callExpr.resolvedCallee?.fullName?.let { return it }
    // For builtins, use the builtin name directly
    val func = callInfo.function
    if (func is GoIRBuiltinValue) return func.name
    // For INVOKE, construct name from receiver type + method
    if (callInfo.mode == GoIRCallMode.INVOKE) {
        val recvType = callInfo.receiver?.type?.displayName ?: return null
        return "($recvType).${callInfo.methodName}"
    }
    return null
}
```

For `append`, the builtin name is `"append"`. The pass rule function name should be `"append"`.

---

## 5.5 MVP Rule Set

For the MVP (passing existing 2 tests + Phase 1 tests), the minimal rule set is:

```kotlin
// In test configurations:
val stdSource = TaintRules.Source("test.source", "taint", Result)
val stdSink = TaintRules.Sink("test.sink", "taint", Argument(0), "test-id")

// Common pass-through rules needed for Phase 1 tests:
val commonPassRules = listOf(
    // append: element taint flows to result slice
    TaintRules.Pass("append",
        PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(1)),
        PositionBaseWithModifiers.BaseOnly(PositionBase.Result)),
    // append: existing slice taint flows to result
    TaintRules.Pass("append",
        PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(0)),
        PositionBaseWithModifiers.BaseOnly(PositionBase.Result)),
)
```

The stdlib rules from section 5.3 are added incrementally as test categories require them.

---

## 5.6 `TaintRules.Pass` with Modifiers

The `PositionBaseWithModifiers` type supports drilling into fields/elements. Example:

```kotlin
// "append(slice, elem)" → taint flows from elem to result[*]
TaintRules.Pass("append",
    from = PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(1)),
    to = PositionBaseWithModifiers.WithModifiers(
        PositionBase.Result,
        listOf(PositionModifier.ArrayElement)
    )
)
```

This means: when `append` is called, if `Argument(1)` is tainted, then `Result.[*]` (element level) becomes tainted.

The `GoMethodCallFlowFunction.applyPassRules` must handle modifiers:

```kotlin
// When resolving "to" with modifiers:
var newFact = currentFactAp.rebase(callerToBase)
for (accessor in toAccessors) {
    newFact = newFact.prependAccessor(accessor)
}
```
