# Rule selection and mark reduction: implementation validation

Date: 24 October 2026.
Runtime: `40e20d16f03ab046df75a53a489bb331b065a9c1`.
Pre-change control: `279ac7a6f500a6c431d5898512a1422afb1b40dd`.
The runtime source tree is `c986cba8f1ea0531f7db2a4c91a10c90c2743fd4`.
Read the [selected specification](../../spec/semgrep-rule-selection-and-specialization.md)
and [proof dossier](../../spec/semgrep-rule-selection-and-specialization-proofs.md).

The first 12 project commands and four fresh corrected candidate commands
are closed. The final comparison imports the eight unchanged main/reference
runs and uses the four fresh corrected candidates. The Thingsboard candidate
reaches **run-1 OOM**, with no COMPLETE or accepted forward iteration.
Conductor and Stirling-PDF reach run-2 OOM after accepting run-1. The
corrected OWASP command completes at run-11 with NO_DEMAND_EDGE.
Fresh main uses the legacy engine; bidirectional iteration labels
do not apply to main.
Read the [new performance report](bidirectional-rule-selection-performance-20261024.md)
for project timing, precision gates, profiles and memory evidence.

## Change and boundary

The provider computes the executable mark set as assigned ∩ CM-used. It
folds executable CM conditions, removes unused GEN actions and removes
false rules and empty source/cleaner rules until the catalog is stable.
The catalog spans all methods. Metadata consumes the final set; it does
not contribute producers or consumers. Metadata conditions keep their
original resolution. Live reset targets keep every original position
carrier and ordinal, including a carrier whose own mark is unused.

Metadata selection follows active graph support and conservative shared
owner, full-mark and item-ID closure. Safe queries filter raw candidates
before binding. A query with fallback report-ID effects keeps the complete
original binder and its owner counter/memo behavior. The 20-by-20 fixture
counts 400 actual binding contexts on the reference path and zero for the
rejected candidate on the early path.

Known providers use owned caches for each selected view. The public JVM
base and direct configuration queries remain unfiltered. Ordinary and
no-END views remain separate. Original alias models use the original pass
rules. A changed body or external scope cannot use an obsolete selected
cache. An identical complete selection retains the cached rule values.

Original provider-combination admission occurs before compaction. Removing
an extra rule cannot enable a base rule that the extra originally overrode.
Changing an extra condition cannot make it override an unrelated base
condition. Original query presence and condition labels carry that decision;
there is no new per-method wrapper-result cache.

Opaque providers use the original normalization path. Coordinators call
`selectRulesWithScope`; an opaque outer wrapper makes the synchronous
selection conservative. A transient Boolean scope is restored in `finally`.
It contains no provider, IR, rule or fact reference, and propagation does
not read it. A complete boundary requires the explicit capability and an
available raw catalog; a known wrapper with an opaque child remains opaque. Direct opaque clients use the same scoped selector helper.
Both public Semgrep selector entry points publish their owned resolver view.

Field transfer, exclusions, AP grammar, layers, static-service transitions,
summary application, contributing origins, controller/exit behavior and
later iteration limits are unchanged. Lowering changes carry original
rule/action/reset ordinals through compaction and known wrapper expansion.
There is no IR-storage or reference-manager change. FULL remains the default.

## Local checks

All commands use Java 17 and the root Gradle composite build. The first
reference test command failed to build the Go server because system protoc
3.6.1 rejects proto3 optional fields. A saved init script selects the cached
protoc 3.25.1 for that build task. No analyzer source or project parameter
changes are part of this toolchain fix.

| Suite | Fresh reference | Candidate |
| --- | --- | --- |
| Common dataflow | 1,793 pass | 1,793 pass |
| JVM dataflow | 438 pass | 440 pass |
| Go dataflow | 1,313 pass | 1,323 pass |
| JVM configuration equality | No added cases | 5 pass |
| Application | 962 tests; 11 failures; 13 skips | 1,034 tests; 11 failures; 13 skips |

The corrected JVM suite has 440 passing cases: the 438 original cases and
two new vulnerability-identity controls. The earlier common and Go results
are reused for unchanged modules. The corrected application run uses the
repository's default 4 GiB test heap and has no new failing cases.
All 11 expected/actual failure
assertions match the fresh reference. Set-valued assertions are compared
as sets; scalar assertions are compared exactly. This comparison does not
equate complete exception stacks or establish whole-project acceptance.
The remaining cases are five cleaner matrix cases, three cleaner control-flow
cases, one field cleaner case and two Spring dispatcher setup cases. Their
expectations remain unchanged.

An earlier focused run passes 84 checks: 74 application checks and 10 Go
view checks. The final full suite also includes the public-base compatibility
check and two nested opaque-scope controls. All 87 checks pass in that suite. The focused checks cover global
and cascading mark reduction, metadata guard/reset carriers, query variants,
original ordinals, stable/fallback IDs, real binding work, cache/view order,
foreign owners, catalog/scope changes, original combination admission,
opaque wrappers and both public selector entry points.

The first focused run had six empty-native-statement fixture errors and one
fallback-ID fixture-order error. The statements now use a mock query input;
the base resolver ignores that input. The fallback control selects the
second candidate in the actual original broad query. It requires the exact
original retained ID and both original binder/context visits. It does not
assume serialization order for a HashSet-backed candidate collection.

A further review found that a known wrapper could forward an unknown raw
catalog from its opaque child. The marker-only boundary then incorrectly
allowed closed-world normalization. Two added controls fail before the fix
and pass after it. The fix is committed before any candidate project command.
The first reference command remains unchanged and sealed.

### Configuration equality correction

The first project campaign uses `abdbe345e`. Its new specialization field
unintentionally changes data-class equality and hash codes. Two otherwise
equal sink rows with ordinal 0 and 1 form two VulnerabilityKeys. Their
reference counterparts form one. This is an exported-occurrence regression,
not permitted callback diagnostic variation.

Commit `40e20d16f` gives all nine JVM configuration row classes explicit
equality/hash functions. They include every original semantic field in its
original hash order and exclude only specialization labels. Ordered lists,
original action/rule ordinals, constructors and internal occurrence IDs stay
available. No analyzer core identity or field rule changes.

Five configuration tests cover all nine row classes. The RED run fails two
cases; GREEN passes all five. Two JVM dataflow controls use actual
VulnerabilityKey and RuleOccurrenceId values. Six existing output tests
also pass, for 13 focused checks. An immutable production-jar control
reproduces set cardinality 1 → 2 → 1 on reference, first candidate and
correction. Bytecode inspection confirms all nine equality/hash functions
exclude only specialization and contain no allocation opcode. Calls to
existing field equality/hash methods keep their original contracts.

An isolated full test attempt used a 1 GiB test cap to share the host with
MAT. That setup is invalid for comparison with the default 4 GiB reference:
it records 16 Java-heap OOM signals and four extra Kotlin collection failures.
Those raw results are preserved. At the unchanged root default, all four
cases pass. The complete repeated application suite has 1,034 cases,
exactly the original 11 failed assertions and 13 skips, with no heap OOM.
Model YAML, compiled fixture and approximation-jar payload controls also
match. The project benchmark heap/timeout configurations are independent
and keep their fixed values.

The constructive proof build passes 184 jobs. The owned audit passes
3,506 declarations and 174 executable controls. It permits only `propext`
and `Quot.sound`. These models establish their stated local contracts;
they do not prove the complete Kotlin engine or arbitrary-project timing.

The evidence root is
`/drive-testcomp/opentaint-go-rules/run1-rule-selection-implementation-20261024`.
It retains exact commands, XML and failure controls under
`validation/reference-tests/` and `validation/current-tests/`. The proof
receipt is [semgrep-rule-selection-checks.json](../../spec/lean/semgrep-rule-selection-checks.json).
Equality correction results are under `validation/equality-correction/`,
including `default-4g-app/reference-failure-comparison.json` and the preserved
`constrained-1g/negative-control.json`.
The project campaign measures the final committed candidate. OWASP retains
all 4,112 main sink locations. Conductor retains all seven. PDF retains
15 of 17 strict main locations; the same two keys are absent on the fresh
pre-change control. Thingsboard has no accepted result. These failures
remain explicit. Conductor/PDF/OWASP reference/candidate export multisets
match exactly. OWASP still exports 9,179 entries versus CI's expected total
4,112, so its exact trace-count gate fails. No speedup is inferred from the
local binding fixture.

All final-comparison profiles and six OOM owner scopes are validated.
The corrected campaign evidence is
`/drive-testcomp/opentaint-go-rules/run1-rule-selection-equality-fix-20261024`.
The first campaign remains source-bound history. Old abd Conductor/PDF
heaps are explicitly superseded and unanalyzed; their corrected heaps are
the current measurements.
