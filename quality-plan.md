# Quality Plan

## Status: ALL PASSING (232 tests)

## Baseline (start of quality phase)
- 140 tests passing (4 Tier-1 benchmarks, 126 Tier-2 unit tests, 10 Tier-3 round-trip)
- 4 benchmark projects: click, requests, attrs, typer

## Q1: Expand Tier-1 Benchmarks (~20 real-world projects)
- [x] click (8 files)
- [x] requests (8 files)
- [x] attrs/attr (8 files)
- [x] typer (5 files)
- [x] rich (12 files, recursive)
- [x] pygments (10 files, recursive)
- [x] urllib3 (10 files, recursive)
- [x] packaging (10 files)
- [x] cryptography (10 files, recursive)
- [x] more_itertools (3 files)
- [x] idna (8 files)
- [x] charset_normalizer (8 files)
- [x] markdown_it (10 files, recursive)
- [x] grpc (10 files, recursive)
- [x] google.protobuf (10 files, recursive)
- [x] mypy (15 files, recursive)
- [x] shellingham (7 files)
- [x] mdurl (6 files)
- [x] markupsafe (2 files)
- ~~certifi~~ (removed: data-only package, no __file__)

Total: 19 benchmark packages, all passing

## Q2: Fix Bugs Found in Analysis
- [x] Fix exception type resolution in try/except (was hardcoded to `builtins.Exception`)
- [x] Fix for-loop tuple unpacking target (was only handling NameExpr, now emits PIRUnpack for TupleExpr)
- [x] Add `__exit__` call emission in `with` statement handler (was missing entirely)
- [x] Add logging to catch-all exception handlers in module_builder.py (was silently swallowing)
- [x] Fix function qualified_name for methods (now `module.Class.method` instead of `module.method`)
- [x] Fix RecursionError in type_mapper.py for recursive types (added depth limit)
- [x] Fix int overflow for huge Python ints in protobuf (int64 can't hold 2^64+)
- [x] Add assert message support (emit call with message argument)
- [x] Fix `with` statement to use load_attr __enter__/__exit__ pattern (was using fake $__enter__ local)
- [ ] Fix chained comparison lowering (uses BIT_AND instead of short-circuit and) — deferred, works for correctness

## Q3: Add More Complex CFG Tests
- [x] WithStatementTest (11 tests) — __enter__/__exit__, no target, multiple, nested
- [x] ComplexControlFlowTest (22 tests) — try-in-loop, loop-in-try, triple-nested, while-in-for, multi-break, complex boolean conditions, for/else, while/else
- [x] AssignmentPatternsTest (21 tests) — simple, multi-target, augmented, tuple unpack, swap, starred, attr, subscript, chained
- [x] ExceptionTypeResolutionTest (23 tests) — ValueError, TypeError, RuntimeError, bare except, tuple except, base class Exception
- [ ] Comprehension tests — deferred (comprehensions currently lower to None)
- [ ] Lambda tests — deferred (lambdas currently lower to None)

## Q4: Additional Test Approaches
- [x] CFG quality assertions in benchmarks (instructions >= functions, <=5% empty CFGs)
- [x] CFG build failure logging (warnings printed to stderr)
- [ ] CFG structural integrity tests — future work
- [ ] More Tier-3 round-trip tests — future work

## Progress Log

### Session 3 (Quality Phase)
- Expanded Tier-1 benchmarks from 4 to 19 real-world packages
- Fixed 9 bugs in Python lowering code:
  - Exception type resolution (try/except)
  - For-loop tuple unpacking
  - With-statement __enter__/__exit__ protocol
  - Function qualified name for methods
  - RecursionError in type_mapper (recursive types)
  - Int overflow for huge Python ints
  - Assert message support
  - With-statement method resolution
  - Error logging in module_builder
- Added 4 new Tier-2 test classes (77 new tests)
- Added CFG quality assertions to benchmark tests
- All 232 tests pass (19 Tier-1, 203 Tier-2, 10 Tier-3)
