# A05 transfer, satisfaction and summary application

`ApOps` supplies the tree transfer and summary operations. `MarkGate` handles
concrete marks and abstract mark exclusions. `EdgeApplication` walks the premise
path, tests geometry before requests, then maps or folds the selected subtree.
The must-to-STAR row tests both the premise's own E and the shared edge E.
Relocated suffixes retain input E; demand results have Empty E.

`Summary.kt` selects whole annotated leaves by the direct satisfaction rules.
It applies each summary tail/mark kind once, then grafts the result at every
summary occurrence. Full concrete combinations join the input and summary
layers. `without` exposes the native record inside/applicable-only split.
Both batches retain ordinary transfer layer and E. The caller applies pending
demotion after return actions and mappings in E05.

Record read views remain transient Pattern/Conclusion pairs. Their transfer
preserves distinct input/output marks and drops suffixes at exact targets.
Generic reversal stays separate from native record reversal. `LeafViews.kt`
exposes every marked leaf and nearby leaves with their tail, layer and E.

The compiled edge owns its from-path array. Its public array is a snapshot.
The edge cache is published through a volatile field; each compiled value is
immutable. A cached to-path from another manager is compiled again. Walk state
and collectors belong to each call. Long walks check cancellation.

## Tests before implementation

The first RED failed on missing A05 APIs (`/tmp/bidi-a05-red.log`). The first
focused run passed 42 tests in five classes (`/tmp/bidi-a05-first.log`).
The complete first gate passed 443 common tests in 38 classes, with no failures,
errors or skips, and all consumer compilation in 28 seconds
(`/tmp/bidi-a05-gate.log`). XML is saved in `/tmp/bidi-a05/full-common/xml`.

| Test class | Final tests | Checks |
|---|---:|---|
| EdgeTreeEquivalenceTest | 17 | 2,160 direct concat matrix cells, multileaf transfer, both E tests before requests, static rules, REACH, malformed edges, array ownership, manager cache replacement, eight-thread cache reuse, sparse 32,768-depth transfer/views and cancellation. |
| MarkGateTest | 5 | Concrete replacement/exclusion, abstract exclusion union, requests, S7 guards and canonical mark sets. |
| SummarySelectorTest | 7 | Both selector overloads, canonical/noncanonical FLOW, concrete must guards, annotated nearby leaves, native split and subtraction keys. |
| SummaryKindsTest | 10 | All valid kind pairs, layers/E/marks, root and nested grafts, exact suffix dropping, combination layer joins and invalid parts. |
| ReversalTest | 5 | 1,536 raw reversal cells, tail table, mark exclusions, unsupported outcomes and native/generic distinction. |
| RecordReadViewsTest | 9 | Three F76 shapes, 360 view/input comparisons, mixed STAR/E readers, native batches, mode guards and malformed views. |

Expected inputs and summary leaves use local raw construction and decoding.
Expected outputs use the A02 per-path model. Fact, mark-request and
position-request sets are compared separately. No new Lean proof was needed;
the specification and reference cover these cases.

## Review corrections

The plan was updated before each correction. The static root/class check uses
the existing accessor-index extension. Nearby TAINT lookup copies a prefix
only for a present any leaf, so empty deep prefixes do not cause quadratic
allocation. The deep transfer/view test took 0.051 seconds in the first gate;
this is local gate timing, not a project benchmark.

Review found that the proposal's static shortcut omits the premise mark check.
The specification requires an abstract STAR premise. A root/class regression
failed with the missing ordinary mark request (`/tmp/bidi-a05-static-red.log`).
The shortcut now checks that condition before applying the static exception.

## Final gate

```bash
./gradlew --console=plain --continue \
  :opentaint-dataflow-core:opentaint-dataflow:cleanTest \
  :opentaint-dataflow-core:opentaint-dataflow:test \
  :opentaint-dataflow-core:opentaint-jvm-dataflow:compileTestKotlin \
  :opentaint-dataflow-core:opentaint-go-dataflow:compileTestKotlin \
  :opentaint-jvm-sast-dataflow:compileKotlin compileKotlin
```

After the static guard correction, all 444 common tests in 38 classes pass,
with no failures, errors or skips. XML is saved in
`/tmp/bidi-a05/final-common/xml`. JVM and Go production/test sources, SAST and
application compilation pass. The final gate took 28 seconds
(`/tmp/bidi-a05-final-gate.log`). `git diff --check` passes. A05 is complete.
