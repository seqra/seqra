# I03 JVM statement forms and type filters

`JIRStatementForms` builds non-call JVM statements with the shared
micro-edge builder. It covers copies, casts, field/static/element reads
and writes, operators, allocation kills, return, throw and unchanged
statements. Reads keep their source, self reads replace it, field writes
are strong and element writes are weak. Static paths retain their class
and field. Only local writes ask for aliases. The read-source hook runs
after ordinary construction. I06 will wire the complete interpreter.

`JIRTypeFilters` caches each type's filter, combines root and first-field
declaring checks, and resolves complete paths for field-value types. Its
mark policy checks primitive and boxed types at every element level.
Forward forms carry operand and result filters; reversal omits them.
The checker's old prescan entry points and helper bodies remain unchanged.

## Tests before implementation

Missing-API failures were recorded before each component was added:
`/tmp/bidi-i03-filters-red.log` and
`/tmp/bidi-i03-statements-red.log`. Main filter compilation passed in
`/tmp/bidi-i03-filters-compile.log`.

The plan was updated before each correction. Three generic assertions
needed explicit shared Kotlin types. Two fixture assumptions needed
correction: JIR displays nested type names with a dot, and can lower an
ignored non-void call as an assignment. The direct call guard test now
uses that real expression in a `JIRCallInst`.

The first executable run passed 49 of 52 tests. Its remaining policy
failure showed that the shared unbox helper misses `java.lang.Character`.
Interpreter §5.1 requires all boxed types. The new policy explicitly
recognizes Character, with array-level regressions; the old helper and
prescan stay unchanged. The proposal snippet now states this correction.
No behavior question or new Lean proof was required.

All 52 focused tests then passed in 10 seconds
(`/tmp/bidi-i03-focused-final.log`).

| Test class | Tests | Checks |
|---|---:|---|
| JIRStatementFormsTest | 20 | Every JVM construction row, filter placement, self cases, static prefixes, local aliases, source-hook order, call rejection, 32,768-accessor aliases and real field-to-field lowering. |
| JIRStatementEffectTest | 21 | Independent complete conclusions compared with per-path and tree transfers; all instruction rows, primitive marks, exclusions, layers, static requests, aliases and generated-output cuts at limits 1, 2 and 3. |
| JIRTypeFiltersTest | 11 | Type-cache identity and concurrency, path checks, all primitive/boxed roots, element-level policies, Character arrays, unknown levels, annotations, field-value decoding, combined filters and retained prescan behavior. |

## Final gate

From `core`, the clean gate ran common and JVM `cleanTest`/`test`, Go
`compileTestKotlin`, JVM SAST `compileKotlin` and application
`compileKotlin`. It passed in 48 seconds
(`/tmp/bidi-i03-final-gate.log`).

| Suite | Classes | Tests | Failures / errors / skips |
|---|---:|---:|---|
| Common dataflow | 71 | 748 | 0 / 0 / 0 |
| JVM dataflow | 9 | 171 | 0 / 0 / 0 |

All 600 new common and 57 new JVM annotated tests have void JVM returns
and occur in fresh XML (`/tmp/bidi-i03-common-discovery.txt` and
`/tmp/bidi-i03-jvm-discovery.txt`). XML copies are under
`/tmp/bidi-i03/{opentaint-dataflow,opentaint-jvm-dataflow}/xml`.
Existing old-path JVM statement tests pass. `git diff --check` passes.
I03 is complete.
