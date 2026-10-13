# O1–O3 implementation and test record

Status: core implementation verified. Fresh fixed-project scans are in progress; resource gates remain open.
The selected scope is the [implementation plan](../design/bidirectional-o1-o3-implementation-plan.md).
IR storage and O4–O12 are outside this work.

## Control and evidence

The control revision is `2d83c92694072217fec6f9136e38c5d6232bfa5a`. The existing working patch has
SHA-256 `f1343858898f2c43850dbc5cb691fc3f629affe1b8a73119c0c87bdce6811c38`.
The control patch and manifest are in `/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012`.
That directory also holds command logs and copied test XML. Existing diagnostic changes were preserved.

Remote main was checked with `git ls-remote` on 12 October 2026. Its head is
`f945f92c5ae554ff4f5fc7696ee5a269210478bb`. The fresh baseline scans will use that revision.
Previous project scan results do not prove the result of this implementation.

The control test command passed: 1,226 common tests and 247 selected JVM tests, with no failures or skipped tests.
The first complete common test run after integration found one failure in a negative-control fixture.
The fixture changed `MethodForms.call` through Kotlin delegation. The new delegated `withCall` method bypassed
that changed getter. The fixture now also changes `withCall`. Its original assertion was kept and passed.

The fresh broader control uses the same revision and diagnostic patch in an isolated checkout. Its application
suite had 949 tests: 939 passed and 10 skipped. Its query suite had 215 tests, with 10 failures and 23 skipped
tests. The failures are retained in `control-query-bidi/summary.json`; they are the comparison set for the new
implementation. An initial application run could not start the Go server. Copying the identical existing server
build artifact into the isolated checkout fixed that setup issue; no IR source was changed.

## Verified work

The focused foundation command passed 61 tests. It covered signed folding, graph compilation, support binding,
logical keys, managed cleanup, cache callbacks, compact collections and indexes. These results precede later
integration changes; the final core gate below records the complete suite results.

The focused engine integration command passed the predicate engine, predicate store, handoff and current-sequence
tests. It checks these effects through the real actor services:

- AND waits for every required leaf. Repeated leaves do not create a second premise.
- A source leaf can have both ordinary and DEMAND backward transfers.
- A form can be cleared and rebuilt between inputs without losing standing source support.
- Distinct rule occurrences keep separate sink proof scopes.
- Growing sink facts update existing support.
- A full-condition handoff handle resolves every positive OR branch within a witnessed context.
- Rebuilt end-fact triggers fire once for the logical rule occurrence.

The support store uses shared proof records and persistent binding maps. Its deep-chain test checks 32,768 nodes,
growth after an independent rebuild, and the absence of a retained compiled predicate graph.
The compiler stress test stores 193 nodes for an AND of 64 two-way OR conditions. It builds no cube inventory.

The integrated common and JVM suites passed 1,313 and 373 tests, respectively, with no failures or skipped tests.
Go and JVM SAST consumer test compilation passed. Root test compilation found two fixture API errors: the new
compressed seed needed an exhaustive snapshot branch, and a resolver needed its typed Object name. Both were
corrected without changing the assertions. The next focused command passed 57 common tests, 39 JVM tests and
root consumer test compilation. It also checks a suspended callee return after explicit form-cache cleanup,
typed atom distinctions, signed-false RETURNED rewriting and optional runtime support counters.

A source search found no `explodeToDNF`, `cubes` or Cartesian-product call in the JVM bidi production package.
The production handoff now processes sinks inside the method-form callback. Run state, witness and seed keys do
not retain a compiled predicate. Optional statistics distinguish compiled nodes from observed proof records,
support shapes, fact growth and canonical root outcomes. All counts are cumulative, not a live heap census.
Optional successful-build/reversal samples also count known form shapes, owned container reference entries,
retained primitive index cells and base/call-point directory cardinalities. They deduplicate shared storage within
each sample, skip AP/IR payloads and keep compatibility views lazy. Reversal and rebuild sample shared storage
again, so these totals measure neither new allocations, live retained sizes nor bytes. All six focused counter
tests pass. They cover empty/singleton/indexed layouts, shared directories, failure/rebuild accounting and
numeric-only ownership. The complete suites also pass after this diagnostic addition.

The storage benchmark measured 128 container graphs at 0, 1, 2, 4, 8, 9, 16 and 64 keys. It includes backing
storage, primitive indexes and retained compatibility views, and excludes shared payload objects. At 64 keys,
map storage after all views decreased from 3,272 to 1,160 bytes. Joint statement container storage decreased
from 15,912 to 6,440 bytes; call-plan container storage decreased from 1,768 to 920 bytes. Empty fields have no
exclusive backing container. These results are VM measurements of controlled layouts, not whole-project heap
savings or a lookup-speed improvement. Exact class hashes, exclusions and data are in `storage-benchmark`.

## Final core gate

The final complete common suite passed 1,331 tests. The JVM suite passed 377 tests. Both suites had no failures
or skipped tests. Go, JVM SAST and root consumer test compilation passed. The complete application suite had
950 tests in each bidi mode: 940 passed and 10 skipped with bidi enabled; 937 passed and 13 skipped with bidi
disabled. The query suite had 215 tests in each mode. With bidi enabled, it had the same 10 control failures and
23 skipped tests. With bidi disabled, it had no failures and 26 skipped tests. No tests were ignored to make
an optimization pass.

The query comparison checks the failure messages, negative sample classes, sink owners and finding-count
multisets, in addition to failing case names. All are unchanged from the fresh control. Twelve unexpected
negative samples remain in seven sample families. Witness ordering/multiplicity and run-local mark IDs differ;
the test output is not byte-identical. Exact evidence is in `query-control-comparison`.

The independent raw closure evaluators now process NNF sinks, full-condition seed handles and proof-scoped
witnesses. The new complete closure tests compare summaries, publications, requests, source hits and witnesses
across three schedules, two unit layouts and positive limits 1, 2 and 3. They also compare joined real runs and
an actual forward-to-backward handoff. Two initial fixture expectations were corrected from the AP rules:
a deeper mark-request answer keeps a root STAR, which starts as ANY/DEMAND at every selected limit; and a
canonical summary union removes redundant demand terms before handoff. The tests retain the complete independent
comparisons and separately check the original source's limit-1 cut versus unchanged paths at limits 2 and 3.

The refreshed storage evidence uses the final common classes at `b0a2448c0`. The diagnostic shape readers add
methods without adding storage fields to form containers. All 128 retained-size rows and their object counts
match the preceding layouts. The evidence has 48 copy/lookup
rows, with matching shared input hashes and all paired checksums equal. Lookup timing is a diagnostic only.
The tooling suite passed 25 tests, including cross-midnight profile windows. The benchmark runner now checks
the unique run marker before stopping an analyzer outside the Gradle wrapper's process group.

## Remaining gates

The signed folder, NNF source/sink compiler and solver, stable identities, backward scope, managed cache pins,
compact containers, specification and optional statistics are in place. The ownership review found no remaining
compiled form in actor proof state, persistent witnesses or seed handles. The implementation is committed in
separate condition, storage and cache changes. Unit and synthetic storage evidence does not establish the
project resource gates.

Run OWASP, Conductor, ThingsBoard and Stirling-PDF through `runProjectAnalyzer`. Keep the selected configuration,
heaps and time limits. For each project, record strict finding-location comparison, each iteration time,
OOM/timeout status, CPU profiles and any OOM memory snapshot. Explain each missing baseline finding and each
remaining large memory owner. The fresh [OWASP pair](bidirectional-o1-o3-projects/OWASP.md) completed within
the fixed budget and retained all 4,112 main sink identities. The fresh
[Conductor pair](bidirectional-o1-o3-projects/conductor.md) retained all seven main sink identities, but
the backward run at limit 2 stopped on the memory guard. Its 293 added sink identities are from an
incomplete refinement; they are not final false-positive counts. The guard saved an 11.7 GB heap snapshot
before cancellation. ThingsBoard and Stirling-PDF scans, per-optimization attribution and heap-owner
diagnostics remain pending. The Conductor resource gate has not passed.
