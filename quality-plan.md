# Quality Plan

## Status: ALL PASSING (1103 tests) — All runnable via single `gradle test -PallTiers`

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

### New Library Benchmarks (Session 12)
- [x] flask (24 files)
- [x] django (899 files, recursive)
- [x] fastapi (48 files, recursive)
- [x] starlette (34 files, recursive)
- [x] werkzeug (52 files, recursive)
- [x] jinja2 (25 files, recursive)
- [x] tornado (73 files, recursive)
- [x] falcon (102 files, recursive)
- [x] bottle (1 file, single-file framework)
- [x] pyramid (61 files, recursive)
- [x] sanic (132 files, recursive)
- [x] aiohttp (55 files, recursive)
- [x] celery (161 files, recursive)
- [x] pydantic (105 files, recursive)
- [x] sqlalchemy (256 files, recursive)
- [x] marshmallow (14 files, recursive)
- [x] httpx (23 files, recursive)
- [x] httpcore (31 files, recursive)
- [x] yaml/pyyaml (17 files, recursive)
- [x] mako (33 files, recursive)
- [x] markdown (33 files, recursive)
- [x] tqdm (31 files, recursive)
- [x] pycparser (17 files, recursive)
- [x] pyparsing (17 files, recursive)
- [x] pathspec (31 files, recursive)
- [x] typeguard (12 files, recursive)
- [x] anyio (42 files, recursive)
- [x] filelock (10 files, recursive)
- [x] lxml (25 files, recursive)

Total: 48 library benchmark packages (19 original + 29 new), all passing

### Web Application Benchmarks (Session 12)
Django web apps:
- [x] saleor (e-commerce platform)
- [x] netbox (network automation)
- [x] wagtail (CMS)
- [x] taiga-back (project management)
- [x] django-oscar (e-commerce)
- [x] django-rest-framework
- [x] healthchecks (monitoring)
- [x] zulip (chat server)
- [x] label-studio (data labeling)
- [x] paperless-ngx (document management)
- [x] flagsmith (feature flags)
- [x] hyperkitty (mailing list archiver)
- [x] ralph (asset management)
- [x] plane (project tracker)
- [x] posthog (analytics)
- [x] sentry (error tracking)

Flask web apps:
- [x] superset (BI platform)
- [x] redash (dashboarding)
- [x] CTFd (CTF platform)
- [x] flaskbb (forum)
- [x] lemur (certificate manager)

FastAPI web apps:
- [x] mealie (recipe manager)
- [x] dispatch (incident management)
- [x] polar (billing platform)
- [x] full-stack-fastapi-template

Total: 25 web application benchmarks, all passing

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
- [x] Fix exception handler labels on try-body blocks (blocks inside try had empty exceptionHandlers)
- [x] Fix mid-block terminators: dead code after return/raise emitted in same basic block
- [x] Fix for/else and while/else: break target was same block as else body, causing dead code
- [x] Fix with-stmt `__exit__` dead code: skip `__exit__` when body terminates with return/raise
- [ ] Fix chained comparison lowering (uses BIT_AND instead of short-circuit and) — deferred, works for correctness

## Q3: Add More Complex CFG Tests
- [x] WithStatementTest (11 tests) — __enter__/__exit__, no target, multiple, nested
- [x] ComplexControlFlowTest (22 tests) — try-in-loop, loop-in-try, triple-nested, while-in-for, multi-break, complex boolean conditions, for/else, while/else
- [x] AssignmentPatternsTest (21 tests) — simple, multi-target, augmented, tuple unpack, swap, starred, attr, subscript, chained
- [x] ExceptionTypeResolutionTest (23 tests) — ValueError, TypeError, RuntimeError, bare except, tuple except, base class Exception
- [x] Comprehension round-trip tests (39 tests in RoundTripComprehensionTest)
- [x] Lambda round-trip tests (30 tests in RoundTripLambdaTest)
- [x] WalrusOperatorTest (17 tests) — walrus in if, while, comp, ternary, assert, nested, condition chain
- [x] ChainedComparisonTest (18 tests) — 2/3/4-operand chains, mixed ops, in control flow
- [x] ConstantTypesTest (24 tests) — complex, bytes, ellipsis, large int, unicode, collection literals
- [x] ForTupleUnpackTest (14 tests) — pair/triple/nested unpack, dict.items, enumerate, zip, starred
- [x] ExceptionControlFlowComboTest (25 tests) — break/continue/return in try/except, nested try, while+try, multi-handler
- [x] LambdaEdgeCasesTest (18 tests) — nested lambda, lambda+comp, default args, immediate invocation, chain
- [x] AdvancedFeaturesTest (25 tests) — super(), slice+step, decorators, dict splat, augmented ops, isinstance

## Q4: Additional Test Approaches
- [x] CFG quality assertions in benchmarks (instructions >= functions, <=5% empty CFGs)
- [x] CFG build failure logging (warnings printed to stderr)
- [x] CfgIntegrityTest (33 tests) — structural CFG validation (reachability, successor/predecessor consistency, terminator checks, dangling edges, block label uniqueness)
- [x] EdgeCasesTest (40 tests) — edge-case coverage (empty functions, deep nesting, all comparison operators, delete stmts, parameter kinds, assert patterns, try/except variants)
- [x] 12 new Tier-3 round-trip tests (while-break, for-continue, augmented assign, nested if, chained if, power, find-max, count-chars, nested loops, early return, build dict)
- [x] 360 new Tier-3 round-trip tests across 8 new test classes (RoundTripArithmeticTest, RoundTripStringTest, RoundTripConditionalTest, RoundTripLoopTest, RoundTripCollectionTest, RoundTripMixedTest, RoundTripComprehensionTest, RoundTripLambdaTest)
- [x] Stronger benchmark assertions (instruction diversity, 0 dangling edges, <10% unreachable blocks)
- [x] Refactored 4 slow test classes to PER_CLASS lifecycle (BasicInstructionsTest, ClassesTest, ExceptionHandlingTest, TypeMappingTest)
- [x] RoundTripExceptionTest (53 tests) — try/except/else/finally, nested try, try-in-loop, break/continue in except, multi-handler
- [x] RoundTripWalrusTest (31 tests) — walrus in if/while/ternary, nested walrus, arithmetic patterns
- [x] RoundTripAdvancedTest (40 tests) — chained comparisons, augmented chains, ternary chains, complex boolean, collection building
- [x] Enhanced PIRReconstructor to wrap exception-handler blocks in try/except for correct round-trip behavior

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

### Session 4 (Quality Phase continued)
- Fixed bug: exception handler labels not set on try-body blocks (DC-9)
  - Blocks inside `try` body now get correct `exceptionHandlers` pointing to except handler blocks
  - Root cause: block finalization happened after handler stack was restored
  - Fix: explicitly finalize try-body block before restoring old handler stack
- Added CfgIntegrityTest (33 tests): structural CFG validation
  - All blocks reachable from entry (dead merge blocks allowed)
  - Successor/predecessor consistency (bidirectional)
  - Exit blocks end with return/raise/unreachable
  - No dangling edges (all goto/branch/nextiter targets resolve)
  - Block labels are unique, entry exists in blocks list
  - Exception handler labels all resolve to existing blocks
- Added EdgeCasesTest (40 tests): edge-case coverage
  - Empty/minimal functions, deep nesting (5-level if, 4-level loops)
  - Multi-target assignment, tuple swap
  - Try-in-try, try/except/else/finally, multi-except, raise-from
  - while-True-break, nested-break-continue, for-in-while
  - All comparison operators (is, is not, in, not in)
  - f-strings, class methods (static/class/instance), globals
  - Assert with/without message, delete (local/attr/subscript)
  - Parameter kinds (VAR_POSITIONAL, VAR_KEYWORD, KEYWORD_ONLY)
- Added 12 new Tier-3 round-trip tests: while-break, for-continue, augmented assign, nested-if, multi-assign, chained-if, power, find-max, count-chars, nested-while-loops, early-return, build-dict
- Strengthened Tier-1 benchmark assertions: instruction diversity (>=3 types), 0 dangling edges, <10% unreachable blocks
- Refactored 4 slow test classes to PER_CLASS lifecycle (12x speedup per class)
- All 317 tests pass (19 Tier-1, 276 Tier-2, 22 Tier-3)

### Session 5 (Quality Phase continued)
- Found and fixed 3 bugs via systematic scanning of 18 benchmark packages:
  - DC-10: Mid-block terminators — dead code after return/raise in basic blocks
    - Root cause: `_visit_block` didn't stop emitting after a terminator
    - Also: `_visit_with` emitted `__exit__` after a return in the body
    - Scanned all 18 packages: 5 affected functions reduced to 0
  - DC-11: for/else and while/else break target
    - Break went to same block as else body, causing dead code when else returned
    - Fix: separate `break_block` (after else) from `exit_block` (else body)
  - DC-12: with-stmt `__exit__` dead code
    - `__exit__` was emitted after return in with body, creating mid-block terminator
    - Fix: skip `__exit__` emission when body is already terminated
- Added 4 new tests to CfgIntegrityTest for mid-block terminator detection
- Updated existing tests to use non-returning with bodies for `__exit__` checks
- All 321 tests pass (19 Tier-1, 280 Tier-2, 22 Tier-3)

### Session 6
- Propagated CFG build failures via protobuf diagnostics (replaces stderr logging)
  - Added PIRDiagnosticProto to proto schema with severity, message, function_name, exception_type
  - Added diagnostics field to PIRModuleProto, PIRModule interface, PIRModuleImpl
  - Module builder populates diagnostics instead of printing to stderr
- Implemented lambda lowering
  - LambdaExpr builds CFG using same FuncItem infrastructure as FuncDef
  - Generates synthetic <lambda>$N functions registered in module
  - Verified: click 5 lambdas, requests 1, rich 10, mypy 29
- Implemented comprehension lowering
  - List: [expr for x in iter if cond] -> loop with list.append
  - Set: {expr ...} -> loop with set.add
  - Dict: {k:v ...} -> loop with dict[k] = v
  - Supports nested loops and multiple filter conditions
  - Generator expressions materialized as lists
- Verified class hierarchies and MRO across real packages
  - Single/multiple inheritance, diamond patterns, C3 linearization all correct
  - Enum detection, dataclass detection working
  - Base classes fully qualified
- Fixed 3 additional bugs (DC-10/11/12 from session 5):
  - Mid-block terminators from dead code
  - for/else and while/else break targets
  - with-stmt __exit__ dead code
- All 302 tests pass (19 Tier-1, 258 Tier-2, 22 Tier-3 + 3 untagged)

### Session 7
- Massively expanded Tier-3 round-trip tests from 22 to 313 (291 new tests)
- Created RoundTripTestBase.kt: shared infrastructure for round-trip test classes
- Created RoundTripTestBase.kt: shared infrastructure for all round-trip test classes
- Created 7 new round-trip test classes:
  - RoundTripArithmeticTest (57 tests): math, number algorithms, bitwise ops
  - RoundTripStringTest (47 tests): string manipulation, searching, encoding
  - RoundTripConditionalTest (46 tests): if/elif/else, boolean logic, ternary
  - RoundTripLoopTest (49 tests): while/for, break/continue, nested loops, sorting
  - RoundTripCollectionTest (47 tests): lists, dicts, tuples, matrix ops
  - RoundTripMixedTest (45 tests): algorithms (binary search, kadane, sieve, RPN, etc.)
  - RoundTripComprehensionTest (39 tests): list/set/dict comprehensions, generator exprs, conditional exprs
- All 8 round-trip test classes pass individually (0 failures)
- Total: 632 tests (19 Tier-1, 258 Tier-2, 352 Tier-3 + 3 untagged)

### Session 8
- Added lambda round-trip support to PIRReconstructor:
  - New `reconstructWithLambdas()` method resolves synthetic `<lambda>$N` functions from classpath
  - Lambda functions are emitted as regular `def` blocks with sanitized names
  - `sanitizeFuncName()` converts `<lambda>$0` -> `__lambda___0` for valid Python identifiers
  - `PIRGlobalRef` names are sanitized in `val_()` to handle lambda references
- Created RoundTripLambdaTest (30 tests): lambda expressions, sorted/map/filter with key, compose, conditional lambda, multi-arg
- Added `roundTripWithLambdas()` helper to RoundTripTestBase
- Total: 662 tests (19 Tier-1, 258 Tier-2, 382 Tier-3 + 3 untagged)

### Session 10 (Massive Testing Phase)
- **Made all 681 tests runnable via single `gradle test -PallTiers` (75s, 0 failures)**
- **Made `gradle test` (tier2+tier3, 662 tests) run in 44s with 0 failures**
- Infrastructure improvements:
  - Increased JVM max heap from 1G to 8G
  - Added test logging (started/passed/failed/skipped)
  - Fixed IPv4/IPv6 mismatch: changed Python gRPC server to bind `127.0.0.1` instead of `localhost` (was causing intermittent connection failures when IPv6 `::1` was resolved)
  - Added ping retry with shorter deadline (5 retries × 10s, backoff)
  - Added `shutdownNow()` fallback for channel cleanup
- Bug fixes:
  - DC-13: Decorated method qualified names wrong — `buildFunction` used proto fullname (wrong for Decorator nodes) instead of enclosingClass-based name
  - DC-14: `enclosingClass` back-reference not set on PIRFunctionImpl for class methods
  - DC-15: AST serializer RecursionError on deeply nested expressions (idna package) — added depth limit (MAX_EXPR_DEPTH=40) with try/catch per definition
  - DC-16: Python server orphan process issue — added stdin watchdog thread + stdin pipe close on process manager shutdown
  - DC-17: Thread pool too small (was 1 worker) — increased to 4 workers
- Total: 681 tests (19 Tier-1, 262 Tier-2, 382 Tier-3 + 3 untagged + 15 new benchmark assertions)

### Session 11 (Coverage Expansion)
- **Systematic gap analysis**: identified all supported-but-untested features
- **Added 10 new test classes (255 new tests)**:
  - 7 new Tier-2 test classes (141 tests):
    - WalrusOperatorTest (17 tests): walrus in if, while, comprehension, ternary, assert, nested, condition chain
    - ChainedComparisonTest (18 tests): 2/3/4-operand chains, mixed operators, in if/while control flow
    - ConstantTypesTest (24 tests): complex numbers, bytes, ellipsis, large ints, unicode strings, collection literals
    - ForTupleUnpackTest (14 tests): pair/triple/nested unpack, dict.items(), enumerate, zip, starred
    - ExceptionControlFlowComboTest (25 tests): break/continue/return in try/except, nested try, while+try, multi-handler
    - LambdaEdgeCasesTest (18 tests): nested lambda, lambda+comprehension combo, default args, immediate invocation, chain
    - AdvancedFeaturesTest (25 tests): super(), slice with step, multiple decorators, dict splat, augmented ops, isinstance
  - 3 new Tier-3 round-trip test classes (124 tests):
    - RoundTripExceptionTest (53 tests): try/except/else/finally round-trips, nested try, try-in-loop, break/continue in except
    - RoundTripWalrusTest (31 tests): walrus in if/while/ternary, nested walrus, arithmetic patterns
    - RoundTripAdvancedTest (40 tests): chained comparisons, augmented chains, ternary chains, complex boolean, collection building
- **Enhanced PIRReconstructor**: blocks with `exceptionHandlers` now wrapped in `try:/except:` for correct exception round-tripping
- **All 936 tests pass** via `gradle test -PallTiers` (73s, 0 failures)
- Total: 936 tests (19 Tier-1, 403 Tier-2, 496 Tier-3 + 3 untagged + 15 benchmark assertions)

### Session 12 (Massive Benchmark Expansion: 936 → 990 tests)
- **Installed 15 web framework/utility packages**: flask, django, fastapi, starlette, werkzeug, jinja2, bottle, tornado, falcon, pyramid, sanic, aiohttp, celery, pydantic, sqlalchemy, marshmallow, httpx, httpcore, anyio, gunicorn, uvicorn
- **Cloned 25 real-world web application projects** into `/home/sobol/data/python-ir/web-projects/`:
  - Django (16): saleor, netbox, wagtail, taiga-back, django-oscar, django-rest-framework, healthchecks, zulip, label-studio, paperless-ngx, flagsmith, hyperkitty, ralph, plane, posthog, sentry
  - Flask (5): superset, redash, CTFd, flaskbb, lemur
  - FastAPI (4): mealie, dispatch, polar, full-stack-fastapi-template
- **Created `WebAppBenchmarkTest.kt`**: new test class with `analyzeDir()` method for filesystem-based project analysis (vs installed package analysis)
- **Added 29 new library benchmarks** to `BenchmarkTest.kt`: web frameworks (flask, django, fastapi, starlette, werkzeug, jinja2, tornado, falcon, bottle, pyramid, sanic, aiohttp, celery), data/ORM (pydantic, sqlalchemy, marshmallow), HTTP (httpx, httpcore), utils (yaml, mako, markdown, tqdm, pycparser, pyparsing, pathspec, typeguard, anyio, filelock, lxml)
- **All 990 tests pass** via `gradle test -PallTiers` (~4 minutes, 0 failures)
- Total: 990 tests (73 Tier-1 benchmarks [48 library + 25 webapp], 403 Tier-2, 496 Tier-3 + 3 untagged + 15 benchmark assertions)

### Session 12 continued (Coverage Expansion: 990 → 1103 tests)
- **Systematic gap analysis** via code exploration: identified P0/P1/P2 test coverage gaps
- **Added 4 new Tier-2 test classes (113 new tests)**:
  - ClosureNestedFunctionTest (31 tests): nested defs, closures, nonlocal, global keyword, class-in-function, lambda-in-function, conditional nested defs, decorator-inside-function, deeply nested functions. Documents that nested defs are NOT extracted (skipped in CfgBuilder).
  - AsyncConstructsTest (20 tests): async def, await, multiple awaits, async for, async with, async generator, await in conditionals, await in try/except, nested awaits, async with no as target.
  - PropertyDecoratorTest (29 tests): property getter/setter/deleter (documents empty properties list), custom decorators, stacked decorators, decorator with args, staticmethod/classmethod flags, dataclass fields/generated __init__, enum class flags/methods/IntEnum.
  - AdvancedExpressionsTest (25 tests): matrix multiply (@), f-strings (lowered to calls, not PIRBuildString), star/double-star in calls, del on tuples/slices/nested attrs, tuple return values, comprehension tuple unpack, assert patterns, global write (treated as local), augmented subscript, yield from.
- **Key discoveries documented in tests**:
  - F-strings are lowered by mypy to regular calls/concat before IR — no PIRBuildString emitted
  - PIRProperty list on classes is empty (API defined but not wired in builder)
  - Global/nonlocal writes are treated as regular local assignments (no PIRStoreGlobal/PIRStoreClosure)
  - Nested functions/classes inside function bodies are NOT extracted as PIRFunction/PIRClass
  - Property setter/deleter from OverloadedFuncDef only getter is extracted
- **All 1103 tests pass** via `gradle test -PallTiers` (~4 min, 0 failures)
- Total: 1103 tests (73 Tier-1, 516 Tier-2, 496 Tier-3 + 3 untagged + 15 benchmark assertions)
