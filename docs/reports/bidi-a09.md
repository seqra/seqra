# A09 joins, records and witnesses

`KaryJoin` owns literal slots and exact input deduplication. It uses an
iterative product and checks cancellation. `StandingJoin` joins the two
caller-owned indexes. `ConjunctionStore` keeps each rule alternative and
statement separate, validates a fixed arity before insertion, drops zero
from a nonzero premise union and joins effective contribution layers.
`NdSummaryJoin` retains every full subscriber combination and delivers
stored conclusions or T1/T2 deltas in either arrival order. Retained tuple
lists are owned and unmodifiable.

`PersistentRecordStore` validates native normal singleton kinds and direction
guards. At the barrier it reads raw summaries, skips ineligible groups,
merges eligible values and indexes their deltas. Entry and exit queries
remain candidates for full annotated matching. Exit candidates are
record-deduplicated. Run views reject writes. Native read views are transient
and leave stored marks, layers and exclusions unchanged.

`SourceHitStore` uses the exact method-key, statement and forward-edge triple.
`ConcurrentVulnerabilityStore` uses a context-free vulnerability key and
keeps witness alternatives, contexts, runs and ordered support shapes
separate. Same-shape writes atomically union literal facts and end facts.
Witness lists and nested end-fact paths are owned and unmodifiable. The
store leaves confirmation to the completed forward-run barrier.

## Tests before implementation

Missing-API failures were captured in `/tmp/bidi-a09-joins-red.log` and
`/tmp/bidi-a09-conjunction-red.log` before the respective production slices.
The first compiled focused run executed 51 tests: 49 passed and two failed
(`/tmp/bidi-a09-focused.log`). One failure exposed concurrent construction
in a single-writer statement fixture. The plan was updated before creating
its vulnerability key before worker threads.

The other failure exposed the generic native reversal fallback: an ANY
result was assigned NORMAL. W6 requires DEMAND. A direct regression covers
both native directions, side marks and view application. The plan was
updated before fixing the reference operation and its proposal snippet.
The F76 must-side conversions are unchanged.

Read-only reviews also found public list aliases that could change retained
ND subscribers or a stored witness's support shape and seed paths. The
plan was updated before adding regressions. The witness and reverse-view
regressions failed in `/tmp/bidi-a09-review-behavior-red.log`; the ND alias
regression failed in `/tmp/bidi-a09-nd-alias-red.log`. The first reverse-view
test compile used an absent common result property; the plan records the
test typing correction to compare `EdgeOutcome.Fact` directly.

The final focused run passed all 67 tests in seven classes in six seconds
(`/tmp/bidi-a09-focused-final.log`).

| Test class | Tests | Checks |
|---|---:|---|
| KaryJoinTest | 5 | All 720 three-slot arrival orders, exact duplicates, repeated inputs, slot/arity guards, 32,768 literals and cancellation. |
| StandingJoinTest | 3 | All 720 two-sided arrival orders, full matching scans, request/link index symmetry, second callers and callback failures. |
| ConjunctionStoreTest | 15 | Literal products, repeated slots, zero-drop unions, effective layers, rules/statements/contexts, every ND key field, all 720 subscriber/conclusion orders, T1/T2 deltas, E6 matched parts, tuple ownership and cancellation. |
| RecordStoreTest | 16 | Native guards, complete entry/exit scans, read-only views, raw R1 selection, F76 independent side conversions, T1/T2 delta indexing, full method keys/directions, deep paths, cache loss and iteration cancellation. |
| SourceHitStoreTest | 3 | Complete source identity, forward-edge structural equality, compiled-cache neutrality and independent run stores. |
| VulnerabilityStoreTest | 11 | Context-free keys, ordered witness shapes, 256 concurrent inputs on eight workers, exact lossless literal/end-fact unions, immutable published collections, sink guards, support truth rows and normal must confirmation. |
| ReferenceRecordViewTest | 14 | Existing generic/native reversal cases and the new ANY-result DEMAND regression. |

No behavior question remains in A09. No new Lean proof was needed.

## Final gate

```bash
./gradlew --console=plain --continue \
  :opentaint-dataflow-core:opentaint-dataflow:cleanTest \
  :opentaint-dataflow-core:opentaint-dataflow:test \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:compileTestKotlin \
  :opentaint-dataflow-core:opentaint-go-dataflow:compileTestKotlin \
  :opentaint-jvm-sast-dataflow:compileKotlin compileKotlin
```

The final gate passed in 31 seconds (`/tmp/bidi-a09-final-gate.log`): all
635 common tests in 60 classes pass, with no failures, errors or skips.
JVM/Go test sources, SAST and application compilation pass. XML is saved
in `/tmp/bidi-a09/final-common/xml`.

All 487 new annotated bidirectional tests have JVM void returns and appear
in the final XML (`/tmp/bidi-a09-test-discovery-final.txt`). The discovery
audit and `git diff --check` pass. A09 is complete.
