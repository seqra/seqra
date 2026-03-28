# Go Dataflow Engine — Implementation Details

> This document defines concrete implementation details for the Go taint dataflow analysis engine.
> It bridges the high-level design (`go-dataflow-design.md`) and the test system plan
> (`go-dataflow-test-system.md`) into actionable per-module specifications.

---

## Sub-Documents

| # | Document | Contents |
|---|----------|----------|
| 1 | [Module Overview](impl-details/01-module-overview.md) | Module breakdown, file inventory, dependency graph, implementation order |
| 2 | [Go IR Utilities](impl-details/02-go-ir-utilities.md) | `GoFlowFunctionUtils`, access path mapping, Go IR → framework bridge |
| 3 | [Call Resolution & Application Graph](impl-details/03-call-resolution.md) | `GoCallResolver`, `GoCallExpr`, `GoMethodCallResolver`, `GoApplicationGraph` completion |
| 4 | [Flow Functions](impl-details/04-flow-functions.md) | `GoMethodStartFlowFunction`, `GoMethodSequentFlowFunction`, `GoMethodCallFlowFunction` |
| 5 | [Taint Rules & Std Library](impl-details/05-taint-rules.md) | `GoTaintRulesProvider`, source/sink/pass rule application, stdlib pass rules catalog |
| 6 | [Analysis Manager & Supporting Classes](impl-details/06-analysis-manager.md) | `GoAnalysisManager` wiring, `GoMethodAnalysisContext`, fact mapper, summary handler, preconditions |
| 7 | [Test Infrastructure Changes](impl-details/07-test-infrastructure.md) | `AnalysisTest` enhancements, `util.go` updates, multi-package support, sample organization |
| 8 | [Design ↔ Test Alignment Matrix](impl-details/08-alignment-matrix.md) | Feature-by-feature mapping: engine capabilities vs test categories, gap analysis |

---

## Key Principles

1. **Follow JVM patterns** — The JVM engine (`opentaint-jvm-dataflow`) is the reference implementation. Go classes mirror JVM class structure but are significantly simpler (no alias analysis, no lambda tracking, no type hierarchy in MVP).

2. **All new files go in `opentaint-go-dataflow`** — Under `org.opentaint.dataflow.go.*`. No changes to framework core or Go IR API.

3. **MVP first** — Phase 1 targets passing ~60 tests (basic flow, interprocedural, control flow, expressions, defer, cross-package, generics). Phase 2 adds field/collection/interface sensitivity. Phase 3 adds closures, goroutines.

4. **Abstract fact refinement via exclusion sets** — When a flow function encounters an abstract fact and needs a specific accessor (field, element, taint mark) that isn't concrete, it must add that accessor to the fact's exclusion set. This triggers the framework's refinement mechanism, which re-analyzes with the accessor materialized. Never assume an abstract fact "has" an accessor — always use `exclude()` to trigger refinement. See `04-flow-functions.md` §4.2.13.

5. **Single source/sink per test** — Each test uses exactly one `TaintRules.Source` and one `TaintRules.Sink`. Multiple source/sink rules per test are not needed.

6. **User corrections are binding** — All corrections from prior conversations are incorporated:
   - `GoIRParameterValue` → `Argument(paramIndex)`, `GoIRRegister` → `LocalVar(index)`
   - INVOKE mode supported in MVP (structural subtyping resolution)
   - Source rules ≠ entry-point rules (different application points)
   - Cross-package, generics, defer all in Phase 1
   - Collection sensitivity: element-level (`ElementAccessor`) vs container — key-insensitive, but `sink(a)` ≠ `sink(a[0])`

---

## Implementation Order (Critical Path)

```
1. GoFlowFunctionUtils          ← foundation: everything depends on access path mapping
2. GoCallExpr                   ← adapter: needed by language manager and call flow
3. GoLanguageManager (complete) ← needed by framework to extract calls from instructions
4. GoCallResolver               ← needed by application graph and call flow function
5. GoApplicationGraph (complete)← needed by TaintAnalysisUnitRunnerManager
6. GoTaintRulesProvider         ← needed by all flow functions for rule queries
7. GoMethodCallFactMapper       ← needed by call flow + summary handler
8. GoMethodAnalysisContext      ← minimal: holds entry point + taint context
9. GoMethodStartFlowFunction    ← entry point: zero propagation
10. GoMethodSequentFlowFunction ← core: assignment/store/return/phi handling
11. GoMethodCallFlowFunction    ← core: source/sink/pass at call sites
12. GoMethodCallSummaryHandler  ← applies callee summaries in caller
13. GoAnalysisManager (wire)    ← factory: creates all components
14. Dummy preconditions + side-effect handler ← stubs for trace/side-effect subsystems
15. Test infrastructure changes ← AnalysisTest enhancements, util.go updates
```

Steps 1–14 are in `opentaint-go-dataflow`. Step 15 is in `core/src/test/` and `core/samples/`.
