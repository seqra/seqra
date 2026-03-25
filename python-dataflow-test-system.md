# Python Dataflow Analysis — Test System Design

## 1. Current State

### What Exists

**2 test cases** in `PythonDataflowTest.kt`:
- `testSimpleSample` — taint flows through two assignments to sink (positive)
- `testSimpleNonReachableSample` — taint killed by string literal overwrite (negative)

**1 Python sample** (`core/samples/src/main/python/simple/Sample.py`):
```python
def source() -> str:
    pass

def sink(data: str):
    pass

def sample():
    data = source()
    other = data
    sink(other)

def sample_non_reachable():
    data = source()
    other = "safe"
    sink(other)
```

**Test base class** (`AnalysisTest.kt`):
- Sets up `PIRClasspath` from Python sources extracted from a samples JAR
- Provides `assertSinkReachable(source, sink, entryPointFunction)` and `assertSinkNotReachable(...)`
- Each test specifies `TaintRules.Source`, `TaintRules.Sink`, and an entry-point function name
- Uses `PIRTaintConfig` with simple data classes: `Source(function, mark, pos)`, `Sink(function, mark, pos, id)`, `Pass(function, from, to)`

### What the JVM Side Has (for Reference)

The JVM dataflow engine has ~70 tests across 47 sample files covering:
- Simple/interprocedural/branch/loop flows
- Collections (List, Map, Iterator)
- String methods (substring, toLowerCase, trim, concat, replace)
- Lambdas and functional interfaces
- Optional (of, get, map, orElse, flatMap, ifPresent)
- Stream (map, filter, reduce, forEach, flatMap)
- Async (Thread, Runnable, Callable, CompletableFuture, ExecutorService)
- Kotlin coroutines, scope functions, null safety
- SARIF output verification with embedded span markers

### External Benchmark Available

The **Ant Application Security Testing Benchmark** at `~/data/ant-application-security-testing-benchmark/` contains:
- **`sast-python2/`** — 651 Python 2 test files across 53 categories
- **`sast-python3/`** — 799 Python 3 test files across 61 categories

These are described in detail in Sections 4-5 below.

---

## 2. Test System Goals

1. **Validate the engine prototype** — ensure the minimal implementation passes basic flow tests
2. **Characterize engine capabilities** — systematically test each analysis dimension (flow-, context-, field-, object-, path-sensitivity)
3. **Track progress** — as the engine evolves, tests move from `@Disabled` to passing
4. **Regression protection** — prevent regressions in already-working features
5. **Benchmark comparison** — measure against the Ant benchmark as an industry standard

---

## 3. Test Infrastructure Design

### 3.1 Test Organization

```
core/
  samples/src/main/python/
    simple/                     # Existing: basic samples
      Sample.py
    intraprocedural/            # New: detailed intra-procedural flow tests
      AssignmentFlow.py
      StrongUpdate.py
      BranchFlow.py
      LoopFlow.py
      ExpressionFlow.py
    interprocedural/            # New: inter-procedural flow tests
      SimpleCall.py
      ChainedCall.py
      ArgumentPassing.py
      ReturnValue.py
    field_sensitive/            # New: field/attribute access
      ClassField.py
      DictAccess.py
      ListAccess.py
      NestedAccess.py
    context_sensitive/          # New: context sensitivity
      MultiInvoke.py
      DefaultArgs.py
    class_features/             # New: class-related features
      SimpleObject.py
      Inheritance.py
      StaticMethod.py
    python_specific/            # New: Python-specific constructs
      Comprehension.py
      Lambda.py
      Generator.py
      Decorator.py
      WithStatement.py
      FString.py
      PatternMatch.py
      AsyncAwait.py
    benchmark/                  # Adapted from Ant benchmark
      completeness/
        alias/
        control_flow/
        datatype/
        expression/
        function_call/
        variable_scope/
        cross_file/
      accuracy/
        context_sensitive/
        field_sensitive/
        flow_sensitive/
        object_sensitive/
        path_sensitive/

  src/test/kotlin/org/opentaint/python/sast/dataflow/
    PythonDataflowTest.kt              # Existing: simple tests
    IntraproceduralFlowTest.kt         # New
    InterproceduralFlowTest.kt         # New
    FieldSensitiveFlowTest.kt          # New
    ContextSensitiveFlowTest.kt        # New
    ClassFeatureFlowTest.kt            # New
    PythonSpecificFlowTest.kt          # New
    BenchmarkCompleteness Test.kt      # New: adapted from Ant benchmark
    BenchmarkAccuracyTest.kt           # New: adapted from Ant benchmark
    AnalysisTest.kt                    # Existing base class
```

### 3.2 Test Pattern

Each test follows the established pattern from `PythonDataflowTest`:

```kotlin
@Test
fun testFeatureName() = assertSinkReachable(
    source = Source("ModuleName.source", "taint", PositionBase.Result),
    sink = Sink("ModuleName.sink", "taint", PositionBase.Argument(0), "rule-id"),
    entryPointFunction = "ModuleName.test_function_name"
)
```

Each Python sample file follows the same template:
```python
def source() -> str:
    pass

def sink(data: str):
    pass

def test_positive_case():
    """Taint should reach the sink."""
    x = source()
    # ... feature-specific code that propagates taint ...
    sink(x)

def test_negative_case():
    """Taint should NOT reach the sink."""
    x = source()
    # ... feature-specific code that kills taint ...
    sink(x)
```

### 3.3 Source/Sink Convention

All test samples share a single source/sink convention to keep rules simple:

| Role | Function | Position | Mark |
|------|----------|----------|------|
| Source | `<Module>.source` | `Result` | `"taint"` |
| Sink | `<Module>.sink` | `Argument(0)` | `"taint"` |

This mirrors the Ant benchmark approach where the vulnerability type is irrelevant — what matters is whether taint flows from source to sink through the specific construct being tested.

### 3.4 Multi-File Test Support

For cross-file flow tests, we need multiple Python files where taint flows across imports. The current infrastructure already supports multiple `.py` files in the samples directory. The test just needs to specify the correct entry point:

```kotlin
@Test
fun testCrossFileImport() = assertSinkReachable(
    source = Source("module_a.source", "taint", PositionBase.Result),
    sink = Sink("module_b.sink", "taint", PositionBase.Argument(0), "cross-file"),
    entryPointFunction = "module_main.test_cross_file"
)
```

### 3.5 Pass-Through Rules for Builtins

Some tests will need `TaintRules.Pass` rules for Python builtins (e.g., `str.upper()`, `list.append()`, `dict.get()`). The `AnalysisTest.commonPathRules` list is already set up (currently empty) for this purpose. Tests that need pass-through rules should define them in the test class:

```kotlin
val listPassRules = listOf(
    TaintRules.Pass(
        function = "builtins.list.append",
        from = PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(0)),
        to = PositionBaseWithModifiers.BaseOnly(PositionBase.This)
    ),
)
```

### 3.6 `@Disabled` Convention for Incremental Progress

Tests for features not yet implemented should be annotated with `@Disabled("Reason")` and grouped at the bottom of each test class. As engine capabilities grow, tests are un-disabled:

```kotlin
@Test
@Disabled("Field sensitivity not yet implemented")
fun testClassFieldAccess() = assertSinkReachable(...)
```

---

## 4. Ant Benchmark Analysis

### 4.1 Overview

The Ant Application Security Testing Benchmark (by Ant Group + Zhejiang University) evaluates SAST **engine capabilities**, not vulnerability types. All test cases use a single pattern:
- **Source**: Function parameter `taint_src`
- **Sink**: `os.system(o)` via `taint_sink()`
- **Focus**: Can the engine track taint through the specific language construct?

Test cases are organized into two dimensions:

| Dimension | Sub-categories | What it measures |
|-----------|---------------|------------------|
| **Accuracy** | Context-sensitive, Field-sensitive, Flow-sensitive, Object-sensitive, Path-sensitive | Precision — can the tool avoid false positives? |
| **Completeness** | Single-app tracing (alias, class, control flow, cross-file, datatype, exception, expression, function call, variable scope), Dynamic tracing, Other | Recall — can the tool handle this construct? |

### 4.2 sast-python3 Categories (Python 3 — our target)

| Category | Sub-category | # Files | Difficulty | Key Constructs |
|----------|-------------|---------|------------|----------------|
| **Accuracy: Context-sensitive** | Argument/return passing | ~14 | 2-3 | Default args, keyword args, variadic, nested calls |
| | Multi-invoke | ~4 | 2+ | Same function with different args |
| | Polymorphism | ~6 | 3 | Subclass dispatch, metaclasses |
| **Accuracy: Field-sensitive** | Class fields | ~14 | 2-3 | `obj.data` vs `obj.sani`, inheritance, depth 1-3 |
| | 1D collection (no solver) | ~22 | 3 | `list[0]` vs `list[1]`, map keys, destructuring |
| | 1D collection (solver) | ~12 | 4 | Computed indices |
| | Multidimensional | ~10 | 4 | 2D/3D indexing, nested dicts |
| **Accuracy: Flow-sensitive** | Normal statements | ~2 | 2 | Assignment ordering |
| | Loop statements | ~4 | 2+ | for/zip, for/enumerate |
| | Async | ~4 | 3 | async/await ordering |
| **Accuracy: Object-sensitive** | Class instances | ~4 | 3 | Two instances, one tainted |
| | Collections | ~18 | 3 | Different list/dict/set instances |
| **Accuracy: Path-sensitive** | Exception | ~5 | 2+ | try/except/finally paths |
| | Jump control | ~9 | 2+-4+ | break, continue, return |
| | Conditionals (no solver) | ~4 | 2+ | if/else branch tracking |
| | Conditionals (solver) | ~7 | 4 | Deterministic conditions (1+1==2) |
| **Completeness: Alias** | | ~10 | 2-3 | Variable alias, list element alias, multi-level |
| **Completeness: Async** | async_for, promise/callback/await | ~12 | 3 | asyncio, async generators |
| **Completeness: Class** | Simple/complex objects | ~16 | 2-3 | Constructor, inheritance, cross-class |
| **Completeness: Control flow** | assert, conditional, loop | ~26 | 2-3 | if/else, for, while, nested, all/any |
| **Completeness: Cross-file** | cross_file, cross_module | ~50 dirs | 2-3 | import, from...import, packages |
| **Completeness: Datatype** | primitives, list, tuple, map, etc. | ~50+ | 2-3 | Type-specific operations |
| **Completeness: Exception** | try/except/finally/else | ~10 | 2-3 | Exception flow, except* |
| **Completeness: Expression** | basic ops, conditional, lambda, special | ~60+ | 2-3 | Binary/unary, walrus, comprehension |
| **Completeness: Function call** | 10 sub-categories | ~80+ | 2-3 | Closures, decorators, generators, higher-order |
| **Completeness: Variable scope** | global, nonlocal, private, static | ~16 | 2-3 | Scope rules |
| **Completeness: Dynamic** | dynamic_call | ~6 | 3 | `getattr()` |
| **Completeness: Other** | ellipsis, type:ignore, with/as | ~6 | 2-3 | Python-specific |

### 4.3 Key Differences Between sast-python2 and sast-python3

Python 3-only features in the benchmark:
- `async`/`await` and `asyncio`
- `match`/`case` structural pattern matching (3.10+)
- `except*` ExceptionGroup (3.11+)
- `nonlocal` keyword
- Type annotations (`Any`, `NewType`)
- f-strings, walrus operator (`:=`), `yield from`

**Recommendation**: Use **sast-python3** exclusively — Python 2 is EOL and our engine targets Python 3.

### 4.4 Benchmark Metadata Format

Each test file has a structured comment header:
```python
# evaluation information start
# real case = true              # Ground truth (true = vulnerable, false = safe)
# evaluation item = ...         # Category path (Chinese)
# scene introduction = ...     # Scenario description (Chinese)
# level = 2                    # Difficulty (2, 2+, 3, 3+, 4, 4+)
# bind_url = ...               # Relative path
# evaluation information end
```

Each leaf directory has a `config.json` with evaluation scene definitions:
```json
{
  "compose": "case_001_T.py && !case_002_F.py",
  "scene": "description"
}
```

The `compose` field defines pass criteria: `filename_T.py` (tool must detect) `&&` `!filename_F.py` (tool must not flag).

---

## 5. Benchmark Adaptation Strategy

### 5.1 Adaptation Approach

The Ant benchmark cases cannot be used directly because:
1. They use `taint_src` (function parameter) as source; our engine uses `source()` (function call with `Result` position)
2. They use `os.system()` as sink; we use a dedicated `sink()` function
3. They have Python 2/3 boilerplate (`if __name__ == "__main__"`) we don't need
4. Some use complex imports and multi-file setups

**Adaptation pipeline** for each benchmark case:

```
1. Parse the metadata header → extract ground truth, category, difficulty
2. Transform the code:
   a. Replace `taint_src` parameter with `data = source()` call
   b. Replace `taint_sink(o)` / `os.system(o)` with `sink(o)`
   c. Add `source()`/`sink()` stub definitions
   d. Remove `if __name__ == "__main__"` block
   e. Rename the entry function to a descriptive test name
3. Generate the Kotlin test method:
   a. `assertSinkReachable(...)` if `real case = true`
   b. `assertSinkNotReachable(...)` if `real case = false`
   c. Set `@Disabled` with difficulty-based reason if above current capability
```

### 5.2 What to Adapt First (Priority Order)

**Phase 1 — Minimal prototype validation** (Level 2, completeness):
1. `completeness/single_app_tracing/alias/` — variable aliasing (10 cases)
2. `accuracy/flow_sensitive/normal_stmt/` — assignment ordering (2 cases)
3. Basic expressions from `completeness/single_app_tracing/expression/basic_expression_operation/` (subset: assignment, variable copy)

These are close to our existing 2 tests and validate the core assign/kill logic.

**Phase 2 — Interprocedural basics** (Level 2, completeness):
1. `completeness/single_app_tracing/function_call/argument_passing/` — value/keyword/variadic args
2. `completeness/single_app_tracing/function_call/return_value_passing/` — return values
3. `completeness/single_app_tracing/function_call/chained_call/` — chained calls

**Phase 3 — Control flow** (Level 2-2+):
1. `completeness/single_app_tracing/control_flow/conditional_stmt/` — if/else
2. `completeness/single_app_tracing/control_flow/loop_stmt/` — for/while
3. `accuracy/flow_sensitive/loop_stmt/` — loop sensitivity

**Phase 4 — Classes and fields** (Level 2-3):
1. `completeness/single_app_tracing/class/simple_object/` — constructor, fields
2. `accuracy/field_sensitive/class/` — field discrimination
3. `completeness/single_app_tracing/datatype/` — list, dict, tuple

**Phase 5 — Advanced features** (Level 3+):
1. `completeness/single_app_tracing/function_call/higher_order_function/` — callbacks, HOF
2. `completeness/single_app_tracing/function_call/decorator_function/` — decorators
3. `completeness/single_app_tracing/function_call/generator_function/` — generators
4. `accuracy/context_sensitive/` — multi-invoke, polymorphism
5. `accuracy/object_sensitive/` — instance discrimination

**Phase 6 — Python 3-specific** (Level 3+):
1. `completeness/single_app_tracing/asynchronous_tracing/` — async/await
2. Pattern matching (match/case)
3. `except*` ExceptionGroup
4. Walrus operator

### 5.3 Adapted Sample Structure

Adapted benchmark files will be placed under `core/samples/src/main/python/benchmark/` mirroring the original directory structure but with transformed code:

```python
# Adapted from: sast-python3/case/completeness/single_app_tracing/alias/alias_variable_001_T.py
# Original: real case = true, level = 2
# Category: Completeness > Alias > Variable aliasing

def source() -> str:
    pass

def sink(data: str):
    pass

def alias_variable_001_positive():
    """Variable alias: x aliases taint_src, taint flows through alias."""
    data = source()
    x = data      # alias
    sink(x)       # taint reaches sink via alias
```

### 5.4 Automation Script

A Python script should automate the transformation:

```
tools/adapt_benchmark.py
  --input-dir ~/data/ant-application-security-testing-benchmark/sast-python3/case/
  --output-dir core/samples/src/main/python/benchmark/
  --test-output-dir core/src/test/kotlin/org/opentaint/python/sast/dataflow/benchmark/
  --module-prefix Benchmark
```

The script:
1. Walks the benchmark directory tree
2. Parses each `.py` file's metadata header
3. Transforms the Python code (source/sink replacement)
4. Generates corresponding Kotlin test methods
5. Groups tests by category into separate test classes
6. Adds `@Disabled` annotations for Level 3+ cases (configurable threshold)

---

## 6. Custom Test Cases — Feature Coverage Matrix

Beyond the benchmark, we need custom test cases that specifically exercise our engine's IFDS/IDE mechanics. These test taint propagation through Python constructs in a way targeted to our architecture.

### 6.1 Intraprocedural Flow

| Test | Description | Positive | Negative | Priority |
|------|-------------|----------|----------|----------|
| `assign_direct` | `x = source(); sink(x)` | x | | P0 |
| `assign_chain` | `x = source(); y = x; sink(y)` | x | | P0 |
| `assign_long_chain` | `a=source(); b=a; c=b; d=c; sink(d)` | x | | P0 |
| `assign_overwrite` | `x = source(); x = "safe"; sink(x)` | | x | P0 |
| `assign_overwrite_other` | `x = source(); y = "safe"; sink(x)` | x | | P0 |
| `assign_swap` | `x = source(); y = "safe"; x, y = y, x; sink(y)` | x | | P1 |
| `branch_if_true` | `x = source(); if cond: sink(x)` | x | | P1 |
| `branch_if_else_both` | `if cond: x = source() else: x = source(); sink(x)` | x | | P1 |
| `branch_if_else_one` | `if cond: x = source() else: x = "safe"; sink(x)` | x | | P1 |
| `branch_overwrite_in_branch` | `x = source(); if cond: x = "safe"; sink(x)` | x | | P1 |
| `loop_for_body` | `x = source(); for i in range(n): sink(x)` | x | | P1 |
| `loop_while_body` | `x = source(); while cond: sink(x)` | x | | P1 |
| `loop_overwrite` | `x = source(); for i in r: x = "safe"; sink(x)` | x | | P2 |
| `string_concat` | `x = source(); y = "prefix" + x; sink(y)` | x | | P2 |
| `fstring` | `x = source(); y = f"data: {x}"; sink(y)` | x | | P2 |
| `augmented_assign` | `x = source(); x += "suffix"; sink(x)` | x | | P2 |
| `walrus` | `if (x := source()): sink(x)` | x | | P3 |
| `comprehension_list` | `x = source(); y = [x for _ in range(1)]; sink(y[0])` | x | | P3 |
| `comprehension_dict` | `x = source(); y = {"k": x}; sink(y["k"])` | x | | P3 |
| `unpack_tuple` | `x = source(); a, b = x, "safe"; sink(a)` | x | | P2 |
| `unpack_tuple_neg` | `x = source(); a, b = "safe", x; sink(a)` | | x | P2 |
| `del_variable` | `x = source(); del x; sink(x)` | | x | P3 |

### 6.2 Interprocedural Flow

| Test | Description | Positive | Negative | Priority |
|------|-------------|----------|----------|----------|
| `call_simple` | `def f(a): sink(a); f(source())` | x | | P0 |
| `call_return` | `def f(): return source(); sink(f())` | x | | P0 |
| `call_pass_through` | `def f(a): return a; sink(f(source()))` | x | | P0 |
| `call_chain_2` | `def f(a): return g(a); def g(b): return b; sink(f(source()))` | x | | P1 |
| `call_chain_3` | Three levels of pass-through | x | | P1 |
| `call_arg_kill` | `def f(a): a = "safe"; return a; sink(f(source()))` | | x | P1 |
| `call_multiple_args` | `def f(a, b): sink(b); f("safe", source())` | x | | P1 |
| `call_multiple_args_neg` | `def f(a, b): sink(a); f("safe", source())` | | x | P1 |
| `call_keyword_arg` | `def f(*, data): sink(data); f(data=source())` | x | | P2 |
| `call_default_arg` | `def f(a="safe"): sink(a); f()` | | x | P2 |
| `call_default_arg_override` | `def f(a="safe"): sink(a); f(source())` | x | | P2 |
| `call_variadic_args` | `def f(*args): sink(args[0]); f(source())` | x | | P3 |
| `call_variadic_kwargs` | `def f(**kw): sink(kw["k"]); f(k=source())` | x | | P3 |
| `call_recursive` | `def f(n, d): if n==0: sink(d); f(n-1, d); f(3, source())` | x | | P2 |
| `call_nested_def` | `def outer(): def inner(a): sink(a); inner(source())` | x | | P2 |

### 6.3 Field Sensitivity

| Test | Description | Positive | Negative | Priority |
|------|-------------|----------|----------|----------|
| `field_simple_read` | `obj.x = source(); sink(obj.x)` | x | | P1 |
| `field_different_field` | `obj.x = source(); sink(obj.y)` | | x | P2 |
| `field_constructor` | `class C: def __init__(self, d): self.d = d; c = C(source()); sink(c.d)` | x | | P2 |
| `field_two_fields` | `obj.x = source(); obj.y = "safe"; sink(obj.y)` | | x | P2 |
| `field_overwrite` | `obj.x = source(); obj.x = "safe"; sink(obj.x)` | | x | P2 |
| `field_nested` | `a.b.c = source(); sink(a.b.c)` | x | | P3 |
| `dict_literal` | `d = {"k": source()}; sink(d["k"])` | x | | P2 |
| `dict_different_key` | `d = {"k": source()}; sink(d["other"])` | | x | P3 |
| `list_append` | `l = []; l.append(source()); sink(l[0])` | x | | P2 |
| `list_index` | `l = [source(), "safe"]; sink(l[0])` | x | | P2 |
| `list_index_neg` | `l = [source(), "safe"]; sink(l[1])` | | x | P3 |
| `tuple_unpack` | `t = (source(), "safe"); a, b = t; sink(a)` | x | | P2 |

### 6.4 Context Sensitivity

| Test | Description | Positive | Negative | Priority |
|------|-------------|----------|----------|----------|
| `ctx_same_fn_diff_args` | `def f(a): return a; sink(f(source())); ok(f("safe"))` — f called with both tainted and clean | x | | P2 |
| `ctx_two_callsites` | `x = f(source()); y = f("safe"); sink(y)` — should NOT detect | | x | P3 |
| `ctx_identity_chain` | `def id(x): return x; sink(id(id(source())))` | x | | P2 |

### 6.5 Class Features

| Test | Description | Positive | Negative | Priority |
|------|-------------|----------|----------|----------|
| `class_method_call` | `class C: def m(self, d): sink(d); C().m(source())` | x | | P1 |
| `class_self_field` | `class C: def set(self, d): self.d = d; def get(self): return self.d` | x | | P2 |
| `class_inheritance` | `class B(A): pass` — taint through inherited method | x | | P3 |
| `class_static_method` | `class C: @staticmethod def f(a): sink(a); C.f(source())` | x | | P2 |
| `class_class_method` | `class C: @classmethod def f(cls, a): sink(a); C.f(source())` | x | | P2 |
| `class_property` | `class C: @property def x(self): return self._x` — taint through property | x | | P3 |

### 6.6 Python-Specific Constructs

| Test | Description | Positive | Negative | Priority |
|------|-------------|----------|----------|----------|
| `lambda_simple` | `f = lambda x: x; sink(f(source()))` | x | | P2 |
| `lambda_capture` | `x = source(); f = lambda: x; sink(f())` | x | | P3 |
| `generator_yield` | `def g(): yield source(); sink(next(g()))` | x | | P3 |
| `generator_yield_from` | `def g(): yield from [source()]; sink(next(g()))` | x | | P3 |
| `decorator_passthrough` | `def dec(f): return f; @dec def foo(): return source(); sink(foo())` | x | | P3 |
| `decorator_wrapper` | `def dec(f): def w(*a): return f(*a); return w` | x | | P3 |
| `with_as_enter` | `with open(source()) as f: sink(f.read())` | x | | P3 |
| `exception_try_except` | `try: x = source(); except: x = "safe"; sink(x)` | x | | P2 |
| `exception_finally` | `try: x = source(); finally: sink(x)` | x | | P2 |
| `global_variable` | `g = None; def set(): global g; g = source(); def get(): sink(g)` | x | | P3 |
| `nonlocal_variable` | `def outer(): x = ""; def inner(): nonlocal x; x = source(); inner(); sink(x)` | x | | P3 |
| `match_case_value` | `match source(): case str() as x: sink(x)` | x | | P3 |
| `async_await` | `async def f(): x = await async_source(); sink(x)` | x | | P3 |

### 6.7 Data Types

| Test | Description | Positive | Negative | Priority |
|------|-------------|----------|----------|----------|
| `str_methods` | `x = source(); sink(x.upper())` | x | | P2 |
| `str_join` | `x = source(); sink(",".join([x]))` | x | | P2 |
| `str_format` | `x = source(); sink("{}".format(x))` | x | | P2 |
| `str_slice` | `x = source(); sink(x[1:3])` | x | | P2 |
| `list_comprehension` | `x = source(); sink([x for _ in range(1)])` | x | | P3 |
| `dict_comprehension` | `x = source(); sink({k: x for k in ["a"]})` | x | | P3 |
| `set_add` | `s = set(); s.add(source()); sink(s.pop())` | x | | P3 |
| `bytes_type` | `x = source().encode(); sink(x.decode())` | x | | P3 |

### 6.8 Cross-File Flow

| Test | Description | Positive | Negative | Priority |
|------|-------------|----------|----------|----------|
| `import_function` | `from module_a import tainted_fn; sink(tainted_fn())` | x | | P2 |
| `import_variable` | `from module_a import tainted_var; sink(tainted_var)` | x | | P3 |
| `import_class` | `from module_a import C; sink(C().get())` | x | | P3 |
| `package_init` | `from pkg import func; sink(func())` | x | | P3 |

---

## 7. Priority Definitions

| Priority | Meaning | When |
|----------|---------|------|
| **P0** | Must pass with minimal prototype | Phase 1 — basic engine validation |
| **P1** | Should pass with basic interprocedural + control flow | Phase 2 — core capabilities |
| **P2** | Should pass with field sensitivity + builtin pass-through | Phase 3 — production baseline |
| **P3** | Advanced: context sensitivity, Python-specific, dynamic | Phase 4+ — full engine |

### Phase-to-Feature Mapping

| Phase | Engine Features Required | Test Count (est.) |
|-------|------------------------|-------------------|
| **Phase 1** | Assign propagation, strong update, source/sink rules | ~10 P0 tests |
| **Phase 2** | Call resolution, argument mapping, return mapping, basic control flow | ~25 P0+P1 tests |
| **Phase 3** | Field access, dict/list element tracking, pass-through rules for builtins, string ops | ~50 P0-P2 tests |
| **Phase 4** | Context sensitivity, closures, generators, decorators, async, pattern matching | ~100+ all priorities |

---

## 8. Benchmark Adaptation — Detailed Design

### 8.1 Transformation Rules

The Ant benchmark uses this pattern:
```python
import os

def test_case_name(taint_src):
    # ... flow logic ...
    taint_sink(result)

def taint_sink(o):
    os.system(o)

if __name__ == "__main__":
    test_case_name("taint_src_value")
```

Our adaptation transforms to:
```python
def source() -> str:
    pass

def sink(data: str):
    pass

def test_case_name():
    taint_src = source()   # <-- was: parameter
    # ... flow logic (unchanged) ...
    sink(result)            # <-- was: taint_sink(result)
```

Specific transformations:
1. Remove `import os`
2. Add `source()` and `sink()` stubs at top
3. Remove `taint_src` parameter from entry function
4. Add `taint_src = source()` as first line of entry function
5. Replace all `taint_sink(expr)` calls with `sink(expr)`
6. Remove `def taint_sink(o): os.system(o)` definition
7. Remove `if __name__ == "__main__":` block
8. For cross-file cases: apply same transforms to imported modules, adjusting import paths

### 8.2 Metadata Extraction

From each benchmark file, extract:
- `real_case` → determines `assertSinkReachable` vs `assertSinkNotReachable`
- `level` → determines `@Disabled` annotation threshold
- `evaluation_item` → maps to test class grouping
- `scene_introduction` → becomes test method documentation

### 8.3 Cross-File Case Handling

Benchmark cross-file cases use directory structures like:
```
cross_file_001_T/
  cross_file_001_T_a.py   # entry point, imports from _b
  cross_file_001_T_b.py   # imported module
```

These map to:
```
core/samples/src/main/python/benchmark/completeness/cross_file/
  cross_file_001/
    main.py    # adapted entry point
    helper.py  # adapted imported module
```

### 8.4 Expected Adapted Counts

| Category | Benchmark Files | Adaptable Now | With @Disabled | Notes |
|----------|----------------|---------------|----------------|-------|
| Flow-sensitive normal | 2 | 2 | 0 | Directly relevant to P0 |
| Alias | 10 | 10 | ~4 | Multi-level alias is P2+ |
| Basic expression | ~30 | ~25 | ~10 | Binary ops need pass-through |
| Argument passing | ~20 | ~20 | ~8 | Keyword/variadic are P2+ |
| Return value | ~10 | ~10 | ~2 | Mostly P0-P1 |
| Control flow | ~26 | ~26 | ~10 | Loops are P1, nested P2 |
| Class simple | ~4 | ~4 | ~2 | Constructor flow is P2 |
| Field sensitive | ~14 | ~14 | ~8 | Nested fields P3 |
| Context sensitive | ~24 | ~24 | ~16 | Mostly P3+ |
| **Total Phase 1** | | ~135 | ~60 | |

### 8.5 Test Generation Output

Each adapted category becomes a Kotlin test class:

```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BenchmarkAliasTest : AnalysisTest() {

    // Adapted from: sast-python3/case/completeness/single_app_tracing/alias/
    // Level 2

    @Test
    fun testAliasVariable001Positive() = assertSinkReachable(
        source = Source("alias_variable.source", "taint", PositionBase.Result),
        sink = Sink("alias_variable.sink", "taint", PositionBase.Argument(0), "alias"),
        entryPointFunction = "alias_variable.alias_variable_001_positive"
    )

    @Test
    fun testAliasVariable002Negative() = assertSinkNotReachable(
        source = Source("alias_variable.source", "taint", PositionBase.Result),
        sink = Sink("alias_variable.sink", "taint", PositionBase.Argument(0), "alias"),
        entryPointFunction = "alias_variable.alias_variable_002_negative"
    )

    // Level 3 - not yet supported
    @Test
    @Disabled("Multi-level alias chains not yet implemented")
    fun testAliasMultiLevel003Positive() = assertSinkReachable(...)
}
```

---

## 9. Test Execution Strategy

### 9.1 Test Groups and Tags

Use JUnit 5 tags for selective execution:

```kotlin
@Tag("P0") @Test fun testSimple() = ...
@Tag("P1") @Test fun testInterprocedural() = ...
@Tag("benchmark") @Test fun testBenchmarkAlias001() = ...
```

Gradle task configuration:
```kotlin
tasks.test {
    // Default: run P0 and P1 tests
    useJUnitPlatform {
        includeTags("P0", "P1")
    }
}

tasks.register<Test>("fullTest") {
    useJUnitPlatform() // all tests (excluding @Disabled)
}

tasks.register<Test>("benchmarkTest") {
    useJUnitPlatform {
        includeTags("benchmark")
    }
}
```

### 9.2 Performance Expectations

- PIR classpath creation involves spawning a mypy RPC process (expensive: ~5-10s per setup)
- Use `@TestInstance(Lifecycle.PER_CLASS)` to share classpath across tests in a class
- Group tests by Python sample file to minimize classpath rebuilds
- Estimated per-test analysis time: <1s for simple cases, 1-5s for interprocedural

### 9.3 Test Reporting

Track benchmark coverage over time:

```
Ant Benchmark Coverage Report:
  Completeness:
    Alias:           8/10  (80%)
    Control flow:    20/26 (77%)
    Function call:   45/80 (56%)
    ...
  Accuracy:
    Flow-sensitive:  4/6   (67%)
    Field-sensitive: 6/14  (43%)
    ...
  Overall:           X/799 (Y%)
```

---

## 10. Implementation Roadmap

### Step 1: Expand Existing Sample (Immediate)

Add more entry-point functions to `Sample.py` to test within the existing infrastructure without creating new files:

```python
# Add to existing Sample.py:
def sample_chain():
    a = source()
    b = a
    c = b
    sink(c)

def sample_overwrite_other_var():
    a = source()
    b = "safe"
    sink(a)  # a still tainted despite b being overwritten

def sample_branch():
    if True:
        x = source()
    else:
        x = "safe"
    sink(x)
```

### Step 2: Create Feature-Specific Sample Files

Create the organized sample files under `core/samples/src/main/python/` as described in Section 3.1, starting with P0 tests.

### Step 3: Write Adaptation Script

Create `tools/adapt_benchmark.py` to automate benchmark case transformation.

### Step 4: Adapt Phase 1 Benchmark Cases

Run the script on Level 2 completeness cases (alias, basic expression, flow-sensitive normal).

### Step 5: Iterate

As engine features are implemented:
1. Un-disable corresponding tests
2. Adapt the next batch of benchmark cases
3. Track coverage metrics

---

## 11. Open Questions

1. **Classpath sharing across test classes**: Can multiple test classes share a single `PIRClasspath` instance? The mypy RPC startup cost is significant. One option: a shared test suite that loads all Python samples once.

2. **Cross-file test support**: Does `PIRClasspathImpl.create` correctly handle multiple files with cross-imports? Need to verify that adapted cross-file benchmark cases work with the current PIR infrastructure.

3. **Pass-through rules for builtins**: How should we define pass-through rules for Python stdlib (e.g., `str.upper()`, `list.append()`, `dict.get()`)? YAML config like JVM, or programmatic like the current `commonPathRules` list? **Recommendation**: Start programmatic (in test code), migrate to YAML later.

4. **Benchmark versioning**: The Ant benchmark may evolve. Should we pin to a specific commit/version? **Recommendation**: Copy adapted files into our repo (not reference external paths at runtime).

5. **Chinese metadata**: The benchmark metadata uses Chinese text for evaluation items and scene descriptions. Should we translate these to English for test documentation? **Recommendation**: Include English  translation (for readability) in test comments.
