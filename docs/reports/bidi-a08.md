# A08 path indexes and run stores

`PathTrie` implements positional insertion, prefixes, extensions, around and
all queries. Values are deduplicated at one position; equal values at two
positions remain two entries. Long walks use loops and check cancellation.
`InitialFactStore` deduplicates canonical initial identity and returns an
owned collection.

`MethodEdgeStore` uses REACH bits per statement, premise and layer, and
conclusion groups for FLOW and TAINT. It enforces K6, the multi-premise TAINT
guard, positive limits and the W3 counted-depth assertion. Queries test full
annotated overlap and return whole stored values. `RunSummaryStore` keeps
raw exit summaries and selected publications in separate collections. Both
collections enforce the kind guards and use the specified group deltas.

`AddedFactStore` merges exact group keys per caller reference and link layer.
It retains every caller even when the added fact already exists. It does not
subsume or fold links. Candidate keys are deduplicated before the query reads
their overlapping leaves. `RequestStore` uses the premise position for mark
requests and the requested static position for position requests. It applies
the full overlap test after lookup.

`DemandStore` validates the typed hand-off guards, including non-STAR entries,
normalized marks, paired exact exits and valid own field exclusions. It owns
unmodifiable path lists and builds a fresh private index for each snapshot.
Later builder insertions and source or query-list changes cannot alter an
earlier snapshot. Zero demand is implicit in every method's zero-base near
query; covering uses stored entry prefixes only.

## Tests before implementation

Missing-API RED runs were saved for paths/initials, method edges, summaries,
links/requests and demands (`/tmp/bidi-a08-*-red.log`). The path/initial slice
then passed six tests in two seconds. The first combined compile found that
`fastutil-core` omits the proposed reference map. The plan was updated before
using JDK `IdentityHashMap`; no dependency changed.

The first compiled focused run passed its 48 discovered tests in five seconds
(`/tmp/bidi-a08-focused-final.log`). A sequence review found missing checks
between buffered emissions. After the plan update, both added regressions
failed (`/tmp/bidi-a08-cancellation-red.log`, 12 pass, 2 fail). Per-value and
per-link cancellation checks fix those cases.

An XML audit then found three annotated tests with inferred non-Unit returns.
JUnit had not discovered them. The plan was updated before explicit Unit
signatures and a repeated gate. All 433 new bidirectional annotated methods
now have JVM void returns and appear in the final XML. The audit is saved in
`/tmp/bidi-a08-test-discovery-final.txt`.

| Test class | Tests | Checks |
|---|---:|---|
| PathTrieTest | 6 | Complete positional scans, every path branch, duplicate positions/values, caller arrays, depth 32,768 and cancellation before and during iteration. |
| InitialFactStoreTest | 1 | 61 distinct canonical keys across bases, tails, marks and exclusions; repeated canonical identity. |
| MethodEdgeStoreTest | 11 | REACH bits, K6/ND guards, statement/full-premise keys, T1/T2 deltas, subsumption, complete query scans, positive limits, class/element depth, cache reset and cancellation. |
| RunSummaryStoreTest | 9 | Raw/publication separation in both orders, structural routing, keys/layers, T1/T2/subsumption, deep summaries, cache reset and cancellation. |
| AddedFactStoreTest | 8 | Complete annotated scans, exact new-leaf deltas, no folding/subsumption, every caller-key field, link layers, first-step E, deep paths and iterator cancellation. |
| RequestStoreTest | 8 | Full request scans, mark/static index keys, guards/deduplication, both arrival orders, second caller on old content and cancellation. |
| DemandStoreTest | 10 | Candidate payloads, 3,592 emission and 277,288 restriction comparisons with full scans, implicit zero, exact guards/deduplication, immutable snapshots, deep paths and cancellation. |

Read-only reviews found no remaining contract discrepancy. The full demand
test class took 1.466 seconds in the first full gate; no test-size reduction
was needed. No new Lean proof was needed.

## Final gate

```bash
./gradlew --console=plain --continue \
  :opentaint-dataflow-core:opentaint-dataflow:cleanTest \
  :opentaint-dataflow-core:opentaint-dataflow:test \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:compileTestKotlin \
  :opentaint-dataflow-core:opentaint-go-dataflow:compileTestKotlin \
  :opentaint-jvm-sast-dataflow:compileKotlin compileKotlin
```

The final gate passed in 30 seconds (`/tmp/bidi-a08-final-gate.log`): all 581
common tests in 54 classes pass, with no failures, errors or skips. JVM/Go
test sources, SAST and application compilation pass. XML is saved in
`/tmp/bidi-a08/final-common/xml`. The test-discovery audit and
`git diff --check` pass. A08 is complete.
