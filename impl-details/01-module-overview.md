# 1. Module Overview

## 1.1 File Inventory

All new files go under `core/opentaint-dataflow-core/opentaint-go-dataflow/src/main/kotlin/org/opentaint/dataflow/go/`.

### Existing files (to modify)

| File | Current State | Changes Needed |
|------|--------------|----------------|
| `GoLanguageManager.kt` | 4/8 methods implemented | Complete 3 stubbed methods + 1 property |
| `graph/GoApplicationGraph.kt` | `callees`/`callers` stubbed | Implement with `GoCallResolver` |
| `analysis/GoAnalysisManager.kt` | 0/14 methods implemented | Implement all 14 methods |
| `rules/TaintRules.kt` | Complete (data classes) | No changes needed |

### New files to create

| File | Package | Purpose |
|------|---------|---------|
| `GoFlowFunctionUtils.kt` | `go` | Access path mapping: Go IR values → `AccessPathBase`, `Access` sealed class |
| `GoCallExpr.kt` | `go` | `GoCallExpr` and `GoInstanceCallExpr` — `CommonCallExpr` adapters |
| `GoCallResolver.kt` | `go` | Low-level call resolution (DIRECT, INVOKE, DYNAMIC) |
| `GoMethodCallResolver.kt` | `go.analysis` | High-level `MethodCallResolver` adapter wrapping `GoCallResolver` |
| `GoMethodAnalysisContext.kt` | `go.analysis` | Per-method analysis context |
| `GoMethodStartFlowFunction.kt` | `go.analysis` | Entry flow function |
| `GoMethodSequentFlowFunction.kt` | `go.analysis` | Intraprocedural flow function |
| `GoMethodCallFlowFunction.kt` | `go.analysis` | Call-site flow function |
| `GoMethodCallSummaryHandler.kt` | `go.analysis` | Summary application |
| `GoMethodCallFactMapper.kt` | `go` | Maps facts between caller/callee namespaces |
| `GoTaintRulesProvider.kt` | `go.rules` | Rule lookup by function name |
| `GoMethodStartPrecondition.kt` | `go.trace` | Trace precondition (stub) |
| `GoMethodSequentPrecondition.kt` | `go.trace` | Trace precondition (stub) |
| `GoMethodCallPrecondition.kt` | `go.trace` | Trace precondition (stub) |
| `GoMethodSideEffectHandler.kt` | `go.analysis` | Side-effect handler (stub) |
| `DummyMethodContextSerializer.kt` | `go` | No-op serializer |

**Total: 16 new files + 3 files modified**

## 1.2 Package Layout After Implementation

```
org.opentaint.dataflow.go/
├── GoLanguageManager.kt              (modified)
├── GoFlowFunctionUtils.kt            (new)
├── GoCallExpr.kt                     (new)
├── GoCallResolver.kt                 (new)
├── GoMethodCallFactMapper.kt         (new)
├── DummyMethodContextSerializer.kt   (new)
│
├── analysis/
│   ├── GoAnalysisManager.kt          (modified — all 14 methods implemented)
│   ├── GoMethodAnalysisContext.kt     (new)
│   ├── GoMethodCallResolver.kt       (new)
│   ├── GoMethodStartFlowFunction.kt  (new)
│   ├── GoMethodSequentFlowFunction.kt(new)
│   ├── GoMethodCallFlowFunction.kt   (new)
│   ├── GoMethodCallSummaryHandler.kt (new)
│   └── GoMethodSideEffectHandler.kt  (new)
│
├── graph/
│   └── GoApplicationGraph.kt         (modified — callees/callers implemented)
│
├── rules/
│   ├── TaintRules.kt                 (unchanged)
│   └── GoTaintRulesProvider.kt       (new)
│
└── trace/
    ├── GoMethodStartPrecondition.kt  (new)
    ├── GoMethodSequentPrecondition.kt(new)
    └── GoMethodCallPrecondition.kt   (new)
```

## 1.3 Dependency Graph

```
GoFlowFunctionUtils  ← foundation (no deps on other Go dataflow classes)
       │
       ├──────────────────┐
       ▼                  ▼
GoCallExpr          GoMethodCallFactMapper
       │                  │
       ▼                  │
GoCallResolver            │
       │                  │
       ├──────────┐       │
       ▼          ▼       │
GoApplicationGraph GoMethodCallResolver    GoTaintRulesProvider
       │          │       │                       │
       ▼          ▼       ▼                       ▼
       └──────────┴───────┴───── GoAnalysisManager ◄──── GoMethodAnalysisContext
                                        │
                        ┌───────────────┼───────────────┐
                        ▼               ▼               ▼
               GoMethodStart    GoMethodSequent   GoMethodCall
               FlowFunction     FlowFunction      FlowFunction
                                                        │
                                                        ▼
                                              GoMethodCallSummaryHandler
```

## 1.4 External Dependencies (from framework)

| Framework Module | Types Used |
|-----------------|------------|
| `opentaint-dataflow` (core IFDS) | `TaintAnalysisManager`, `LanguageManager`, `ApManager`, `FinalFactAp`, `InitialFactAp`, `AccessPathBase`, `Accessor`, `FieldAccessor`, `ElementAccessor`, `TaintMarkAccessor`, `ExclusionSet`, `FactTypeChecker`, `MethodCallResolver`, `MethodAnalysisContext`, `MethodStartFlowFunction`, `MethodSequentFlowFunction`, `MethodCallFlowFunction`, `MethodCallSummaryHandler`, `MethodSideEffectSummaryHandler`, `MethodCallFactMapper`, `MethodContextSerializer`, `TaintAnalysisContext`, `TaintSinkTracker` |
| `opentaint-ir-api-common` | `CommonMethod`, `CommonInst`, `CommonCallExpr`, `CommonInstanceCallExpr`, `CommonValue`, `CommonInstLocation`, `ApplicationGraph` |
| `configuration-rules-jvm` | `PositionBase`, `PositionBaseWithModifiers`, `PositionModifier` |
| `go-ir-api` | `GoIRProgram`, `GoIRPackage`, `GoIRFunction`, `GoIRBody`, `GoIRInst`, `GoIRAssignInst`, `GoIRCall`, `GoIRReturn`, `GoIRStore`, `GoIRPhi`, `GoIRMapUpdate`, `GoIRDefer`, `GoIRGo`, `GoIRPanic`, `GoIRExpr` (all 24 variants), `GoIRValue` (all 7 variants), `GoIRType`, `GoIRCallInfo`, `GoIRCallMode`, `GoIRNamedType`, `GoIRNamedTypeKind` |

## 1.5 Lines of Code Estimates

| Component | Est. LOC | Complexity |
|-----------|---------|------------|
| `GoFlowFunctionUtils` | 120–150 | Medium — mapping logic for 24 expression types |
| `GoCallExpr` | 30–40 | Small |
| `GoCallResolver` | 100–130 | Medium — INVOKE requires interface→implementors map |
| `GoMethodCallResolver` | 40–50 | Small — adapter |
| `GoLanguageManager` (additions) | 30–40 | Small |
| `GoApplicationGraph` (additions) | 40–50 | Small |
| `GoTaintRulesProvider` | 80–100 | Medium — position resolution, rule matching |
| `GoMethodCallFactMapper` | 100–120 | Medium — exit-to-return + call-to-start mapping |
| `GoMethodAnalysisContext` | 20–25 | Small |
| `GoMethodStartFlowFunction` | 40–50 | Small |
| `GoMethodSequentFlowFunction` | 250–350 | **Large** — handles all intraprocedural statements |
| `GoMethodCallFlowFunction` | 200–280 | **Large** — source/sink/pass rule application at calls |
| `GoMethodCallSummaryHandler` | 40–50 | Small — mostly default impls |
| `GoAnalysisManager` (additions) | 80–100 | Medium — wiring all components |
| Preconditions + side-effect stubs | 60–80 | Small — empty implementations |
| `DummyMethodContextSerializer` | 15–20 | Trivial |
| **Total** | **~1250–1600** | |
