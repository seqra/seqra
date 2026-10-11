# D02 hand-offs, seeds and frontier counts

Both hand-offs read only stored selected publications. They do not select,
restrict or persist records again. Forward-to-backward demand has one pattern
per selected leaf and premise member. A forward must side becomes ANY with
Empty E. Both directions normalize excluded abstract marks to STAR and preserve
other tails, concrete marks and STAR field exclusions. Raw stores remain
unchanged. Backward zero-premise publications have no exit pattern. A backward
multi-premise publication is rejected. Every method has implicit zero demand,
which does not count as an explicit builder insertion.

Forward sink seeds use only DEMAND report keys, every witness method key and
every matching cached alternative. Exact derived seeds are deduplicated. The
seed index itself retains input multiplicity. Native entry sinks are
unconditional (interpreter §4.1 and §4.9), so they add no requirement. Backward
source seeds use actual stored hits with the full method key, statement and
original forward edge. Records add no synthetic hit.

The hand-off count map and every frontier set/map own unmodifiable snapshots.
The frontier reads counters in one analyzer pass and retains only keys/counts.
It separates stored outgoing demand from demand-layer objects and processed
steps. Report counts are forward-only. Cause counts remain null because the
engine has no cause instrumentation. Incomplete results are rejected before
store scans. Analyzer, leaf, member, sink, hit and frontier scans check
cancellation. The driver supplies the guard for an empty frontier walk.

The first API gate fails because HandOff is missing
(`/tmp/bidi-d02-api-red.log`). The frontier API gate fails before Frontier is
added (`/tmp/bidi-d02-frontier-api-red.log`). END and the full CONJ tests are
written before HandOff is added; the last missing-API gate fails while Frontier
still refers to that absent API (`/tmp/bidi-d02-final-api-red.log`). An earlier
wrapper invocation from the repository root fails before Gradle starts
(`/tmp/bidi-d02-wrapper-path-error.log`); it is not feature-test evidence.

All 29 new tests pass in 3 seconds at the first implementation gate
(`/tmp/bidi-d02-focused-first.log`):

* Nine HandOff tests cover both directions, selected/raw/R1 separation, ND
  patterns, source hits, cached sink alternatives, contexts, exact seed
  deduplication, unconditional sinks and incomplete statuses. The actual CONJ
  regression keeps both reversed literal branches in DEMAND and excludes them
  from backward singleton records.
* Eight normalization tests compare independent expected patterns and counts.
  They cover both-direction excluded leaf marks, must forgetting on both sides,
  STAR exit field E, concrete marks, zero, deduplication, backward ND rejection
  and cancellation. InitialAp forbids excluded abstract premise marks; that
  unconstructible exit cell is reviewed in the shared normalization helper.
* Seven frontier tests use actual accepted links, edges, summaries and record
  applications. A bounded property checks both directions, eight exclusion
  masks and two contexts against declared exact normalized sets. It counts
  accepted patterns rather than attempted inserts.
* Four seed tests cover full source identities, direct multiplicity versus
  derived deduplication, implicit zero rejection and snapshot ownership.
* The END regression runs actual local/call services and cached reversals at
  limits 1/1/1. Run 1 confirms an early sink and reports a later demand sink.
  Backward seeds only the later sink; the reversed end fact seeds the confirmed
  trigger and recovers its real source hit. The constructed next-forward seed
  retains the later finding. A separate direct-core control without that source
  seed loses both positive sink triggers. It is not a driver schedule.

Read-only HandOff and Frontier reviews find no further defect. No new Lean
clarification is needed.

The fresh common gate passes all 1,157 tests in 122 classes, with no failure,
error or skip. JVM and Go test sources, JVM SAST and application test sources
compile. The command runs from `core`:

```bash
./gradlew --continue \
  :opentaint-dataflow-core:opentaint-dataflow:cleanTest \
  :opentaint-dataflow-core:opentaint-dataflow:test \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:compileTestKotlin \
  :opentaint-dataflow-core:opentaint-go-dataflow:compileTestKotlin \
  :opentaint-jvm-sast-dataflow:compileTestKotlin :compileTestKotlin --console=plain
```

The gate takes 36 seconds (`/tmp/bidi-d02-full-gate.log`). All 1,009 new common
annotated tests have void returns and appear in fresh XML. The audit log is
`/tmp/bidi-d02-common-discovery.txt`; XML is saved under
`/tmp/bidi-d02/opentaint-dataflow/xml`. `git diff --check` passes. D02 is complete.
