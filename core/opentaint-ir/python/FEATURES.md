# Python IR — Feature Support Matrix

Comprehensive list of Python language features and their support status in the IR pipeline.

**Legend:**
- **SUPPORTED + TESTED**: Feature works and has dedicated tests
- **SUPPORTED + UNTESTED**: Feature is handled in the pipeline but has no dedicated test
- **PARTIALLY SUPPORTED**: Some aspects work, others don't (details in Notes)
- **NOT SUPPORTED**: Not handled (silently ignored or would crash)

**Test counts:** 1149 passing + 5 skipped = 1154 total (73 tier-1 benchmarks, 29 tier-2 test classes, 15 tier-3 test classes)

---

## Control Flow

| Feature | Status | Notes |
|---------|--------|-------|
| `if/elif/else` | SUPPORTED + TESTED | |
| `while` / `while...else` | SUPPORTED + TESTED | |
| `for` / `for...else` | SUPPORTED + TESTED | GetIter + NextIter pattern |
| `break` / `continue` | SUPPORTED + TESTED | |
| `pass` | SUPPORTED + TESTED | No-op |
| `return` | SUPPORTED + TESTED | |
| `try/except/else/finally` | SUPPORTED + TESTED | All combinations |
| `raise` / `raise from` | SUPPORTED + TESTED | |
| `with` statement | SUPPORTED + TESTED | __enter__/__exit__ calls |
| `assert` / `assert with message` | SUPPORTED + TESTED | Branch + AssertionError raise |

## Functions

| Feature | Status | Notes |
|---------|--------|-------|
| `def` (basic) | SUPPORTED + TESTED | |
| `lambda` | SUPPORTED + TESTED | Extracted as `<lambda>$N` |
| `*args` / `**kwargs` | SUPPORTED + TESTED | |
| Default parameters | SUPPORTED + TESTED | Default value carried through for nested funcs |
| Keyword-only parameters | SUPPORTED + TESTED | |
| Positional-only parameters (`/`) | PARTIALLY SUPPORTED | Proto enum exists but Kotlin maps to POSITIONAL_OR_KEYWORD |
| Decorators | SUPPORTED + TESTED | |
| `@staticmethod` / `@classmethod` | SUPPORTED + TESTED | Flags set by mypy |
| `@property` getter/setter/deleter | SUPPORTED + TESTED | PIRProperty API |
| Generators (`yield` / `yield from`) | SUPPORTED + TESTED | |
| `async def` / `await` | SUPPORTED + TESTED | isAsync flag + PIRAwait |
| `async for` | PARTIALLY SUPPORTED | Lowered as regular `for` (no __aiter__/__anext__) |
| `async with` | PARTIALLY SUPPORTED | Always emits __enter__/__exit__, not __aenter__/__aexit__ |
| `@overload` | PARTIALLY SUPPORTED | Items serialized but overload dispatch not modeled |

## Classes

| Feature | Status | Notes |
|---------|--------|-------|
| `class` (basic) | SUPPORTED + TESTED | |
| Single / multiple inheritance | SUPPORTED + TESTED | base_classes + MRO |
| Abstract classes (`ABC`) | SUPPORTED + TESTED | is_abstract flag |
| `@dataclass` | SUPPORTED + TESTED | is_dataclass flag, generated __init__ |
| `enum.Enum` / `IntEnum` | SUPPORTED + TESTED | is_enum flag |
| Nested classes | SUPPORTED + TESTED | |
| `super()` | SUPPORTED + TESTED | |
| Metaclasses | NOT SUPPORTED | No proto field |
| `__slots__` | NOT SUPPORTED | Not serialized |
| Custom descriptors (`__get__`/`__set__`) | NOT SUPPORTED | Only @property modeled |
| Class inside function body | PARTIALLY SUPPORTED | Doesn't crash but class not extracted |

## Expressions

| Feature | Status | Notes |
|---------|--------|-------|
| Arithmetic / comparison / boolean / bitwise / unary | SUPPORTED + TESTED | All standard operators |
| Matrix multiply (`@`) | SUPPORTED + TESTED | |
| Chained comparisons (`a < b < c`) | SUPPORTED + TESTED | Short-circuit branching |
| Ternary (`x if cond else y`) | SUPPORTED + TESTED | |
| Walrus (`:=`) | SUPPORTED + TESTED | |
| F-strings | PARTIALLY SUPPORTED | Mypy desugars to format() calls; no PIRBuildString emitted |
| List / set / dict / generator comprehensions | SUPPORTED + TESTED | Generator materialized as list (lazy semantics lost) |
| Star expressions (`*x`, `**x`) | SUPPORTED + TESTED | In calls and assignments |
| Subscript / slice | SUPPORTED + TESTED | |
| Collection literals | SUPPORTED + TESTED | list, tuple, set, dict |

## Variables & Assignment

| Feature | Status | Notes |
|---------|--------|-------|
| Simple / multiple / augmented assignment | SUPPORTED + TESTED | |
| Tuple / starred / nested unpacking | SUPPORTED + TESTED | |
| Attribute / subscript assignment | SUPPORTED + TESTED | |
| `del` (local / attribute / subscript / tuple) | SUPPORTED + TESTED | |
| `global` declaration | PARTIALLY SUPPORTED | Serialized but treated as no-op; writes to locals, not PIRStoreGlobal |
| `nonlocal` declaration | PARTIALLY SUPPORTED | Tracked in closureVars but no PIRLoadClosure/PIRStoreClosure emitted |

## Local Functions & Closures

| Feature | Status | Notes |
|---------|--------|-------|
| Nested `def` inside function | SUPPORTED + TESTED | Extracted to module level |
| Closure capture (read) | SUPPORTED + TESTED | closureVars populated from free var analysis |
| Closure capture (write via `nonlocal`) | PARTIALLY SUPPORTED | closureVars tracked but no PIRStoreClosure; round-trip uses env-dict |
| Factory pattern (return inner func) | SUPPORTED + TESTED | |
| Multiple inner functions | SUPPORTED + TESTED | Unique naming via counter |
| Inner calling inner | SUPPORTED + TESTED | Sibling refs via closureVars |
| Multi-level nesting (3+ levels) | PARTIALLY SUPPORTED | IR extraction works; round-trip reconstruction fails (5 disabled tests) |
| Lambda inside function | SUPPORTED + TESTED | |

## Types & Annotations

| Feature | Status | Notes |
|---------|--------|-------|
| Basic types (`int`, `str`, `float`, `bool`) | SUPPORTED + TESTED | |
| `list[int]`, `dict[str, int]` | SUPPORTED + TESTED | |
| `Optional` / `Union` | SUPPORTED + TESTED | |
| `Any` / `None` | SUPPORTED + TESTED | |
| `Callable` / `Tuple` types | SUPPORTED + TESTED | |
| `TypeVar` | SUPPORTED + UNTESTED | Proto exists |
| `Generic[T]` | PARTIALLY SUPPORTED | Type params propagated; no explicit Generic representation |
| `Protocol` | SUPPORTED + UNTESTED | Treated as regular class |
| `Literal` | SUPPORTED + UNTESTED | Proto exists |
| `Final` / `TypeAlias` / `ParamSpec` / `TypeVarTuple` | NOT SUPPORTED | |
| `type` statement (3.12+) | NOT SUPPORTED | |

## Pattern Matching (3.10+)

| Feature | Status | Notes |
|---------|--------|-------|
| `match`/`case` | NOT SUPPORTED | No proto messages, not serialized, not lowered |

## Imports

| Feature | Status | Notes |
|---------|--------|-------|
| `import` / `from import` / `import *` | SUPPORTED + UNTESTED | Collected as flat module name list |
| Relative imports | SUPPORTED + UNTESTED | Resolved by mypy |

## Other

| Feature | Status | Notes |
|---------|--------|-------|
| `ExceptionGroup` / `except*` (3.11+) | NOT SUPPORTED | |
| `__init_subclass__` | NOT SUPPORTED | |

---

## Disabled Tests (5)

These tests exist but are disabled pending multi-level nesting support in the round-trip reconstructor:

1. `RoundTripLocalFunctionTest > recursive inner factorial` — recursive self-reference via unique name
2. `RoundTripLocalFunctionTest > nested three levels add` — 3-level nested function
3. `RoundTripLocalFunctionTest > nested three levels mul` — 3-level nested function
4. `RoundTripLocalFunctionTest > factory adder` — factory returns inner with 2-level capture
5. `RoundTripLocalFunctionTest > factory multiplier` — factory returns inner with 2-level capture

All 5 share the root cause: the env-dict closure reconstruction pattern doesn't support passing `__env__` through multiple nesting levels. The IR extraction itself works correctly for all levels.
