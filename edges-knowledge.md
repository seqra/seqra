# Edges, Abstraction, and Exclusion Sets — Knowledge Base

> **This document must be updated whenever we learn something new about the edge/abstraction system.**

## 1. Edge Types

The dataflow framework uses three edge types to represent taint propagation within and across methods:

| Edge Type | Meaning | Initial Fact | Final Fact | Exclusion Set |
|-----------|---------|-------------|------------|---------------|
| `ZeroToZero` | Simple reachability | Zero (∅) | Zero (∅) | N/A |
| `ZeroToFact` | Source edge — taint originates here | Zero (∅) | A tainted access path | `Universe` |
| `FactToFact` | Pass edge — taint propagates from initial to final | Non-zero (some access path) | Another access path | Uses abstractions |

### ZeroToZero
Reachability edge. Mainly used to trigger taint source rules at call sites (if a method containing `source()` is reached, zero-to-zero enters, then zero-to-fact source edges are produced).

### ZeroToFact (Source Edge)
Created when a taint source is detected. The fact has **no abstractions** — it uses `ExclusionSet.Universe`, meaning all accessors are excluded (the fact is fully concrete, representing exactly what the source produces).

Example: `source()` returns tainted data →
```
var(0).[taint].* with ExclusionSet.Universe
```

### FactToFact (Pass Edge)
Represents taint propagation. Both initial and final facts are access paths.

Example: "if `arg(0)` is tainted, then `ret` is tainted" →
```
initial: arg(0).* {}    final: ret.* {}
```

**Key rule**: To apply a summary `initial → final` to a concrete fact at a call site, the concrete fact's access path must be compatible with the summary's `initial`. Any suffix beyond the abstraction point `*` in the initial is appended to the final.

## 2. Access Paths

An access path is: `base.accessor1.accessor2...` with an exclusion set.

### Bases (`AccessPathBase`)
- `Argument(i)` — the i-th method parameter
- `LocalVar(i)` — local variable by index
- `Return` — method return value
- `This` — receiver object (JVM; not used in Python)
- `ClassStatic` — static field access
- `Constant` — constant value

### Accessors (`Accessor`)
- `FieldAccessor(className, fieldName, fieldType)` — field dereference (`.field`)
- `ElementAccessor` — collection element access (`[*]`)
- `TaintMarkAccessor(mark)` — taint mark label (e.g., `![taint]`)
- `FinalAccessor` — terminal marker (`$`) indicating end of concrete path
- `AnyAccessor` — wildcard matching field/element accessors

### Access Path Notation
```
var(0)![taint]/*    = LocalVar(0), accessor=TaintMarkAccessor("taint"), abstract, Universe exclusions
arg(0).*  {}        = Argument(0), abstract, Empty exclusions
ret.*  {}           = Return, abstract, Empty exclusions
var(3).field.$ {}   = LocalVar(3), accessors=[FieldAccessor, FinalAccessor], concrete
```

## 3. Abstraction

### What is Abstraction?

When analyzing a callee interprocedurally, we don't propagate the exact caller fact. Instead, we **abstract** it: strip the specific suffix and replace it with `*` (an abstraction point).

Example: Caller has `arg(0).field1.field2.![taint].$`. When entering callee `foo(arg)`, the framework abstracts this to `arg(0).*` with `ExclusionSet.Empty`. The callee is analyzed with this abstract fact.

### Why Abstract?

Efficiency. A method like `def identity(x): return x` has summary `arg(0).* → ret.*`. This single summary applies to **any** concrete fact passed in, regardless of the specific access path suffix.

### How `addAbstractedInitialFact` Works

When a fact enters a callee via `addInitialFact(factAp)`:
1. The start flow function may transform the fact
2. `InitialFactAbstraction.addAbstractedInitialFact(fact)` creates abstracted initial/final fact pairs
3. A `FactToFact(initialAbstract, entryStatement, finalAbstract)` edge is created
4. This edge is processed by the callee's flow functions

The abstraction process strips all accessors beyond the base and creates `base.* {}` (abstract with empty exclusions). The original taint mark, field accessors, etc. are all behind the `*`.

### Consequence for Taint Mark Checking

Inside a callee, the fact at a statement may be `var(0).*` (abstract). The taint mark `![taint]` is **behind** the abstraction point. `startsWithAccessor(TaintMarkAccessor("taint"))` returns **false** because the access path tree starts with `*`, not `![taint]`.

**Solution (Python engine)**: `factHasMark()` checks both:
1. Concrete: `fact.startsWithAccessor(TaintMarkAccessor(mark))` — mark is explicitly present
2. Abstract: `fact.isAbstract() && TaintMarkAccessor(mark) !in fact.exclusions` — mark is not excluded, so it **could** be behind `*`

## 4. Exclusion Sets

### Purpose

Exclusion sets track which accessors are known to NOT be behind an abstraction point `*`. They drive the framework's refinement mechanism.

### Types

| Type | Meaning | `contains(x)` |
|------|---------|---------------|
| `Empty` | No exclusions — nothing is known | Always `false` |
| `Universe` | All excluded — fact is fully concrete | Always `true` |
| `Concrete({a, b})` | Specific accessors excluded | `true` if `x ∈ {a, b}` |

### Creation Rules

- **Source facts** use `ExclusionSet.Universe` — the fact is concrete, no abstraction
- **Abstracted facts** (inside callees) use `ExclusionSet.Empty` — nothing excluded yet
- **After refinement** — exclusions grow as the framework discovers what's behind `*`

### Refinement Mechanism

The `FinalFactReader.containsPosition()` drives refinement:

```kotlin
readPosition(
    ap = factAp,
    position = position,
    onMismatch = { node, accessor ->
        if (accessor != null && node.isAbstract()) {
            refinement = refinement.add(accessor)  // Record what we tried to read
        }
        false
    },
    matchedNode = { true }
)
```

When checking if an abstract fact contains a position (e.g., checking for taint mark):
1. Walk the access path tree
2. If we hit an abstract node (`*`) but expected a specific accessor → **mismatch**
3. The accessor is added to the `refinement` exclusion set
4. `refineFact()` then unions these refinements into the fact's exclusions
5. The framework detects the new exclusions and re-analyzes with more specific facts

Example flow:
1. Callee receives `arg(0).*  {}` (abstract, empty exclusions)
2. Sink check looks for `TaintMarkAccessor("taint")` → hits `*` → refinement = `{![taint]}`
3. `refineFact` produces `arg(0).*  {![taint]}` (now "taint" is excluded from `*`)
4. Framework sees new exclusions → triggers re-analysis with `arg(0).![taint].*  {}` (taint mark is now concrete)
5. On second analysis, `startsWithAccessor(TaintMarkAccessor("taint"))` returns **true**

This is the JVM engine's approach. The Python engine currently uses a simpler approximation (check abstract + not-excluded), which is sound but may over-approximate.

## 5. Interprocedural Edge Flow

### Caller → Callee (CallToStart)

1. Call flow function creates `CallToStartZFact(callerFactAp, startFactBase)`
   - `callerFactAp` has the **caller's** base (e.g., `LocalVar(0)`)
   - `startFactBase` is the **callee-side** base (e.g., `Argument(0)`)
2. Framework rebases: `callerFactAp.rebase(startFactBase)` → `Argument(0).![taint]/*`
3. Callee's `addInitialFact()` abstracts it to `Argument(0).*  {}`
4. Start flow function receives the fact (NOT remapped — kept as `Argument(i)`)
5. Callee's `FactToFact` edges begin from this abstracted initial fact

### Callee → Caller (Summary Return)

1. Callee produces summary edge: `FactToFact(initial=Argument(0).*, stmt, final=Return.*)`
2. Framework delivers summary to subscribed caller via `SummaryEdgeSubscription`
3. `PIRMethodCallSummaryHandler.mapMethodExitToReturnFlowFact()` maps:
   - `Return` → caller's call target variable
   - `Argument(i)` → caller's i-th argument expression
4. New edges created in caller with the mapped facts

### Critical: Parameter Naming in PIR

PIR represents function parameters as `PIRLocal` in the body (NOT `PIRParameterRef`). If `accessPathBase()` mapped parameter-named locals to `LocalVar(i)`, the callee's summary edges would have `LocalVar(i)` initial bases — but the caller subscriptions expect `Argument(i)`.

**Fix**: `PIRFlowFunctionUtils.accessPathBase()` checks if a `PIRLocal` name matches a method parameter and maps it to `Argument(i)`. This ensures summary initial bases match subscription expectations.

## 6. Lessons Learned

### Lesson 1: Source facts require `ExclusionSet.Universe`
Use `apManager.createAbstractAp(base, ExclusionSet.Universe).prependAccessor(TaintMarkAccessor(mark))`. Using `mostAbstractFinalAp(base)` creates with `Empty` exclusions, which would make the fact appear abstracted when it should be concrete.

### Lesson 2: Abstract facts hide taint marks
Inside callees, facts are abstracted. The taint mark is behind `*`. Simple `startsWithAccessor()` returns false. Must check `isAbstract() && mark !in exclusions` for sound sink detection.

### Lesson 3: Don't remap bases in start flow function
The start flow function should NOT change `Argument(i)` to `LocalVar(j)`. The framework's summary matching depends on `Argument(i)` bases being consistent between subscription setup and summary delivery. Instead, make `accessPathBase()` return `Argument(i)` for parameter-named locals.

### Lesson 4: `FinalFactReader` refinement is the proper JVM approach
The JVM uses `FinalFactReader.containsPosition()` with `onMismatch` to accumulate refinements, then `refineFact()` to produce more specific facts. This triggers the framework's re-analysis mechanism for progressively more precise results. The Python engine should adopt this pattern when moving beyond the prototype.

### Lesson 5: `rebase()` preserves access paths and exclusions
`fact.rebase(newBase)` only changes the base, keeping accessors and exclusions intact. Safe to use for remapping between caller/callee frames.
