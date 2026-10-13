# O1–O3 implementation and test record

Status: historical pre-rollback measurements at runtime code `b0a2448c0`. All four fixed-project pairs
finished; resource gates remain open. The manager rewrite and weak identity cache are now removed at
the user's request. These results do not validate that revised code. See the
[rollback retest record](bidirectional-core-rollback-20261013.md) and
[new optimization proposal](../design/bidirectional-core-optimization-20261013.md).

| Project | Latest reached iteration in this measurement | Last complete engine iteration | Last accepted forward report |
| --- | --- | --- | --- |
| OWASP | run-3, forward, limit 3, COMPLETE | run-3 | run-3 |
| Conductor | run-2, backward, limit 2, OOM | run-1 | run-1 |
| ThingsBoard | run-1, forward, limit 1, OOM | None | None |
| Stirling-PDF | run-4, backward, limit 4, TIMEOUT | run-3 | run-3 |

Main has no bidi iteration. The OWASP warm heap is a separate run-1 capture; the PDF checkpoint heaps
are older run-3 captures. Do not assign those capture phases to the primary terminal iteration.

The selected scope is the [implementation plan](../design/bidirectional-o1-o3-implementation-plan.md).
IR storage and O4–O12 are outside this work.

## Control and evidence

The control revision is `2d83c92694072217fec6f9136e38c5d6232bfa5a`. The existing working patch has
SHA-256 `f1343858898f2c43850dbc5cb691fc3f629affe1b8a73119c0c87bdce6811c38`.
The control patch and manifest are in `/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012`.
That directory also holds command logs and copied test XML. Existing diagnostic changes were preserved.

Remote main was checked with `git ls-remote` on 12 October 2026. Its head is
`f945f92c5ae554ff4f5fc7696ee5a269210478bb`. The fresh baseline scans use that revision.
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
before cancellation. The fresh [ThingsBoard pair](bidirectional-o1-o3-projects/thingsboard.md) stopped on
the memory guard in forward run 1 at limit 1. No forward report was accepted, so all 14 main sink identities
are absent from its empty output. The guard saved an 18.7 GB heap snapshot. Conductor and ThingsBoard resource
gates have not passed. The fresh [Stirling-PDF pair](bidirectional-o1-o3-projects/Stirling-PDF.md) timed out
in backward run 4. Its retained report has 56 unique sink identities: 14 main identities remain, three are
absent and 42 are added. Two strict differences have source-backed exit-location mappings. An offline probe
reproduces command-mark loss at a later `List.add` return rewriter under the specified source override policy;
the earliest loss on a complete normal caller chain remains unproved. A separate fixed-configuration
runtime captures actual DEMAND/zero-premise root removal at that returned stage and complete F1/F3
report replacement. Fresh CPU/JFR reviews for all four projects are saved.
All eight [O3/O1 checkpoint scans](bidirectional-o1-o3-checkpoints.md) have finished. O1 completes OWASP
with all baseline identities and the same 9,179 occurrences as the final run. The other seven checkpoint
scans stop on memory. Both PDF checkpoints retain F1 output after F3 fails, while the final run accepts F3;
that output difference does not prove an O1/O2 regression. All seven checkpoint OOM heap overviews are
reviewed. They identify compiled form storage in O3 OWASP, active subscription state in Conductor,
compiled NNF descriptors plus pending requests in O1 ThingsBoard, and active edge stores in O1 PDF.
These are overlapping unions at different progress points, not isolated memory savings. The bounded
ThingsBoard follow-up verifies 2,329,585 single-leaf predicates with a 1,758,031,280-byte descriptor
union and an overlapping 969,107,360-byte base-index union. The selected PDF F1 witness and scalar
queries are complete. All eight checkpoint JFR exports and the review of their 151 overlapping windows
are complete. A bounded observer compiled and passed 48 call/report/driver controls in an isolated
checkout. Its original-configuration full run completes F1, B2 and F3, then times out in B4, with exact
primary finding/occurrence/location parity. Its complete report lane proves the two command entries
are unconfirmed F1 DEMAND and absent after the accepted F3 report update. Captured F1 batches prove
root-mark generation, returned root removal and descendant preservation under D23. Ordinary event caps
prevent a whole-run earliest-loss or complete normal caller-chain proof; contribution to the strict
loss remains an inference. The original model, heap and timeout are kept. Diagnostic timings do not
replace the primary pair. The four-project resource gate has not passed. Further deferred core or
source-policy work requires a scope decision under step 10 of the implementation plan.
