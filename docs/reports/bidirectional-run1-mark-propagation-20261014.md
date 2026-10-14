# Run-1: mark requests and repeated fact processing

Date: 14 October 2026. Status: investigation complete; no optimization implemented.

The measurements support a large **mark-demand workload**, especially in ThingsBoard run-1.
They do not yet establish how much repeated fact processing is caused by different marks at matching
geometry. In the fresh ThingsBoard heap, a concrete leaf has at most **11 distinct marks**. A request
row has about **375 requested marks** on average. These are different relations. Narrow leaves do not
rule out repeated processing of common geometry under different origins.

There is repeated work in the implementation. Plain edges select inputs by base and then test marks.
Several fresh deltas can also remain separate after their stored value has merged. Both can cause
unnecessary transfer calls. A direct probe confirms these mechanisms. Their contribution to project
cost still needs measurement.

## Evidence and latest progress

The project evidence is the latest N1/N2 batch, at runtime revision
`61fd97acd792f2200b2e4a5746185d740d87712b`. Current HEAD at the start of this investigation was
`38b076ae5e30d3f38f3860ed18943102fbf074ee`; it adds documentation to that runtime.
The earlier optimization measurements are historical. See the
[fresh performance report](bidirectional-n1-n2-performance-20261014.md).

| Project | Latest reached iteration | Last complete engine | Last accepted forward report | Terminal result |
| --- | --- | --- | --- | --- |
| OWASP | run-3, forward, limit 3 | run-3 | run-3 | COMPLETE |
| Conductor | run-2, backward, limit 2 | run-1 | run-1 | OOM |
| ThingsBoard | run-1, forward, limit 1 | None | None | OOM |
| Stirling-PDF | run-4, backward, limit 4 | run-3 | run-3 | TIMEOUT |

All run-1 measurements below belong to **forward, limit 1**. There is no limit-0 run.
The ThingsBoard heap was captured inside run-1, before cancellation. An incomplete run can have
unprocessed requests and facts. This report describes its captured prefix, not a completed fixed point.

Artifacts are under:

```
/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/n1-n2/20261013T223640Z
```

This investigation reuses the saved profiles and fresh guard heap. It adds read-only MAT queries using
the existing indexes, and a small external diagnostic using production classes. It does not run another
project configuration or change analyzer inputs, core code, the scheduler or IR.

## What the run-1 counters show

These are run-1 END counters, including work before termination. Counts of answers and climbs include
both mark and position requests. Answers are counted before initial admission deduplicates them.

| Project | run-1 engine seconds | Mark memberships / mark rows | Mean marks per row | Duplicate request admissions | Climb actions | Answer actions |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| OWASP | 229.43 | 299,471 / 6,063 | 49.39 | 24,379,231 | 90,535 | 28,780 |
| Conductor | 50.90 | 857,941 / 13,681 | 62.71 | 14,700,210 | 10,284,533 | 190,315 |
| ThingsBoard | 757.90, OOM | 40,219,985 / 107,231 | 375.08 | 371,564,835 | 419,063,266 | 514,630 |
| Stirling-PDF | 39.16 | 393,388 / 4,494 | 87.54 | 1,617,901 | 1,283,959 | 20,851 |

Source: `counter-review/all-N1-per-run.csv` and `all-iterations.csv`.
ThingsBoard has another 1,669,410 position memberships. Its mark rows with 256–1,023 members make up
83.14% of mark rows. The grouped request stores retain a measured union of 2,485,698,088 bytes in the
guard heap. This is a substantial demand relation even before considering its propagation cost.

A row belongs to a **method actor and full abstract premise initial**. Request production attaches
the original premise, not the current conclusion path. Requests from different statements and paths
can therefore accumulate in one row. The mean of 375 does not mean that one concrete leaf carries
375 marks, or that one statement has 375 accepted concrete origins.

The same heap gives a direct comparison with fact-processing volume:

| Captured ThingsBoard run-1 quantity | Count | Meaning |
| --- | ---: | --- |
| Climb actions | 419,036,105 | Request-join effects; slightly before END |
| Answer actions | 514,593 | Attempts to admit an initial, including position answers |
| Handled reservations | 409,346,297 | RunManager event accounting |
| Pending reservations | 11,673,428 | Outstanding event accounting |
| Processed fact deltas | 9,952,362 | Sum of `RunMethodAnalyzer.steps` over all 76,943 actors |
| Pushed fact deltas | 3,207,594 | Sum of `edgeDeltas`; excludes unchanged-work pushes |
| Pending local fact deltas | 68,946 | 534 unchanged, 0 zero, 68,412 other work items |
| Admitted initial memberships | 264,736 | Actor-local accepted initials, including zero and FLOW |

Climbs are about 99.877% of captured request-join actions. The climb count is about 42 times the
processed-fact count. These are different units: one Work reservation can process multiple facts,
and one fact can produce several requests. The comparison establishes the scale of request traffic;
it is not a CPU-time split. The complete class census contains 11,665,656 `RequestIn` objects.
Physical queue slots, live event objects and pending reservations have different boundaries.

## How many marks actually travel in facts

The additional heap queries read every captured `TaintLeaves` object and decode its primitive arrays.
They validate array lengths and calculate `exact union any`, so a mark present in both annotations
counts once.

| Captured quantity | Result | Interpretation |
| --- | ---: | --- |
| All `TaintLeaves` objects | 464 | Includes canonical leaves and cached aggregate leaves |
| Maximum exact set / any set | 11 / 11 | Different tail annotations |
| Maximum exact-plus-any memberships | 22 | The largest row repeats the same 11-mark set |
| Maximum distinct marks in a leaf | **11** | Exact/any union, decoded from IDs |
| Distinct marks across all leaf objects | 91 | A global union, not marks in one fact |
| Distinct current FLOW mark-exclusion sets | 177 | Shared headers, not positive concrete payloads |
| Maximum FLOW mark-exclusion width | **245** | Marks excluded from an abstract fact |
| Canonical `InitialAp` objects | 620 | 507 concrete and 113 abstract |
| Distinct concrete initial mark IDs | 13 | Includes zero; 12 nonzero canonical initial mark IDs |
| Distinct roots referenced by `TaintTree` objects | 113,988 | Shared by 590,054 wrappers |
| Roots with an existing cached aggregate | 20,026, or 17.57% | Remaining 93,962 roots have no cached aggregate |
| Maximum stored `root.size` | 88 | Geometry positions, not mark count |

These are complete selections from this heap, with no row cap. They are pooled object counts, not
frequency-weighted processed-delta distributions. `TaintAlgebra` strongly interns nonempty leaf
pairs without clearing that table during the run. Thus the small maximum also argues against earlier
wide concrete leaves produced through that manager before capture.

A full tree can contain marks at different paths. The leaf maximum alone is not its whole-tree union.
The global 91-ID union bounds the concrete marks in any captured tree, but does not tell us that
one tree carries all 91. Most roots have no cached union, so this report does not claim an exact
whole-tree width histogram. FLOW headers do carry larger sets: the complete distinct-header query
finds widths from 0 to 245. These are marks excluded from a symbolic STAR fact, not 245 concrete
marks that travel together. Header updates can cost work even when they share the whole trie root.
The 177-set count is not weighted by its 2,362,963 FLOW wrappers.

The initial pool does not give the number of accepted initials per method. The actor-local membership
sum above is the appropriate count for that question. The pool does show that hundreds of requested
marks have not become hundreds of distinct concrete initial mark IDs in this captured prefix.

## What the source actually propagates

| Operation | Input and output behavior |
| --- | --- |
| Mark request | Carries destination method, abstract origin initial and one requested mark. It has no Facts payload. |
| Answer to a mark request | Creates an initial with that mark. `startFact` creates a singleton-mark tree. Duplicate initials do not start work again. |
| Generic STAR-to-STAR edge | Can carry a whole multi-mark set in one application and share its subtree. |
| Concrete from-mark edge | Receives the original tree, selects only the matching mark, then applies target renaming or exclusions. It does not emit unrelated input marks. |
| Concrete source or sink condition | Tests mark-dependent literals. TAINT predicate dispatch already uses present marks; FLOW can request eligible marks. Unary source transfer retains the full input for positional semantics. |
| Stored conclusion update | Merges within the full premise, statement and conclusion group. Normally schedules fresh content. Annotation changes can require a whole merged delta under T2. |

The main source paths are [Demand.kt](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Demand.kt),
[RunEvent.kt](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunEvent.kt),
[MarkGate.kt](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/MarkGate.kt),
[RunMethodAnalyzer.kt](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodAnalyzer.kt)
and [MethodEdges.kt](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/store/MethodEdges.kt).

There are four separate places where work can repeat:

1. **Plain edge dispatch.** `StatementSummary.edgesOf` indexes only the base. `FormApplier.stage`
   supplies the entire filtered input to each same-base edge. Concrete gates reject absent marks
   later. `EdgeApplication` can walk, fold or map geometry before that rejection. A one-mark input
   can therefore make many unsuccessful calls; a wide payload is not required. `foldAll` already
   caches immutable subtree unions, so repeated calls do not always repeat every node visit.
2. **Existing-link lookup for a new request.** `RequestJoin.newA` calls `AddedFactStore.overlapping`
   separately for each mark. `leavesNear` enumerates nearby leaf marks before the caller filters the
   requested mark. That can repeat traversal and create unrelated Patterns. FLOW matches still
   generate the required climb actions.
3. **Summary application.** `Summary.leafKinds` creates a transfer for each output mark/tail.
   `applySummaryPart` reapplies the selected input and grafts each result into the positions for that
   output mark. This can repeat structural work while preserving the required output incidence.
4. **Pending deltas and call-stage results.** Storage can already contain a packed value while the
   worklist still holds several fresh deltas. Call stages also collect individual results before
   downstream storage merges them. This can repeat later dispatch. Entry/exit `RuleWorklist` already
   removes equal results; do not assume all boundaries lack deduplication.

Selected-mark TAINT cleaners also traverse their selected region even when the mark is absent. They
can return the same root. A sequential cleaner chain can still emit just one final fact; repeated
intermediate callbacks do not establish multiple outgoing copies of the whole graph. Selected-mark
FLOW cleaners update the exclusion header and share the root. In run-1, F75 requires their request
even for an already excluded mark or a disjoint cleaner path.

## Direct production-code probe

The external probe uses production AP operations, `FormApplier`, `MethodEdgeStore` and `DeltaWorklist`.
It uses **run-1 mode with limit 1**, one counted accessor in each path, and the existing unchanged
`StoreFixtures`. All **52 direct controls passed**, for mark counts 1, 2, 8 and 32. Compiler and runtime
exit codes are zero. The 32-mark inputs are synthetic mechanism checks, not observed project widths.

| 32-mark control | Transfer calls | Result callbacks | Result payload |
| --- | ---: | ---: | --- |
| Packed input, one generic edge | 1 | 1 | All 32 marks together |
| Packed input, 32 concrete edges | 32 | 32 | One selected mark per result |
| One present mark, 32 concrete edges | 32 | 1 | The present mark only |
| 32 absent same-base edges | 32 | 0 | No output |
| 32 fresh singleton deltas, one packed stored group | 1,024 | 32 | One selected mark per result |
| One abstract FLOW input, 32 concrete edges | 32 | 0 | 32 mark requests |
| 32 absent-mark cleaner calls | 32 cleaner calls | 32 | Same full payload; original root reused 32 times |

The singleton-delta control is an algorithmic amplification example: storage packing alone does not
combine pending processing. It does not show that project deltas have this arrival pattern. The probe
counts calls and outputs, not trie visits, CPU time or bytes. Controls also retain normal must/E,
DEMAND/ANY, selected marks and F75 requests. They do not replace full source/sink NNF, proof, support
or fixed-point equivalence tests.

Different concrete marks create different origin initials. A separate control preserves these scopes.
They cannot be merged into a `PremiseSet`: that means conjunction, not alternative origins. Also,
an origin P can later produce Q. Any future batching across origins must preserve the relation from
each full origin to current marks, paths, tails, exclusions and layers, plus source/sink proofs and
caller bindings. A union of origin labels and a union of current marks loses that relation.

## What the profiles support

ThingsBoard **run-1** has 1,233.552 GB of sampled allocation weight. The exact queue comparator frame
`EventQueue.heap$lambda$2` accounts for 575.457 GB, or 46.65%. Java CPU samples attribute 15.35%
inclusively to `compareBy`. `nearLeafViews` has 73.302 GB/5.94% of sampled allocation;
`RequestJoin.newA` has 33.510 GB/2.72%. These frame shares overlap and must not be added.
They show costs of the request workload and identify repeated lookup as a smaller measured path.

The 30-second ThingsBoard **run-1** async capture has 29.04% GC samples, 6.10% queue comparison,
24.07% local services and 6.44% request-related frames. Group shares overlap; this is not an exclusive
CPU attribution. The profiles cannot split concrete-origin work from abstract-origin work by reading
stack names alone.

The pattern is not uniform. OWASP **run-1** normal CPU has 39.40% call services, 32.43% stage and
0.51% request-related frames, despite its many duplicate request admissions. Its run-1 allocation
has 14.86% under `applyCompiledEdge`. Conductor's normal capture is **run-2**, and PDF's normal
capture mixes **run-2/run-3**; neither is run-1 evidence. The hypothesis that concrete mark work is
the main CPU cost in every run-1 is not established.

## Consequences for the next investigation

First measure request production and full join effects. Record producer categories for both source
and sink conditions, cleaners, ordinary edges and positions; count duplicate climbs after projection
to the exact caller origin. The existing totals do not identify the producer of the wide demand rows.
Keep the generated relevant-mark scope and F75 until a semantic proof permits a change.

The following algorithmic candidates remain proposals. No candidate is selected by this report.

| Candidate | Current behavior | Possible new behavior | Expected benefit and limit |
| --- | --- | --- | --- |
| TAINT mark-aware dispatch | Test every same-base concrete edge | Use one present-mark union to omit absent-mark edges; preserve generic edges, ordering and effects | Avoid unsuccessful transfer calls, including for narrow inputs. Measure union cost and actual miss rate. FLOW requests remain required. |
| Mark-selective existing-link visitor | Enumerate nearby marks, then filter the requested mark | Visit or materialize only the requested TAINT mark, with unchanged FLOW fallback | Reduce unrelated Patterns and walks. It does not remove the much larger required abstract climb relation. |
| Summary shape reuse | Match/graft separately for each output mark/tail | Share proven common matching within a batch, then retain the exact mark-to-output-path relation | Reduce repeated geometry work. Different tails, exclusions, layers and target positions must remain distinct. |
| Pending same-key delta combination | Process each fresh delta independently | Combine compatible pending content for the full premise, node and group before processing | Avoid repeated dispatch such as the probe's 1,024 calls. Needs a proof for annotation changes, source/sink support, filters, triggers and ordered effects. |

For measurement, separate processed FLOW, TAINT and REACH deltas; accepted and duplicate concrete
initial admissions; processed leaf widths; same-geometry origin multiplicity; same-base edge misses;
request-lookup node visits and created Patterns; summary kind counts; and pending same-key multiplicity.
Use numeric counters and bounded histograms rather than retaining individual Facts for diagnostics.

The current [optimization proposal](../design/bidirectional-core-optimization-20261014.md) remains
unselected. Its request-storage candidates target a proven large relation, and its primitive comparator
candidate targets measured allocation. This investigation does not justify an origin-vector rewrite
as the first response to ThingsBoard. N3 transport, mailbox, scheduling and queue-layout changes remain
deferred. A future request-set fixed-point algorithm would need a separate design and an explicit scope
decision if it changes those interfaces.

## Reproduction artifacts

Relative to the batch directory:

- `mark-propagation-review/thingsboard/job-20261014T092004.931487Z`: initial six-query report;
  MAT exit 0, 18.64 seconds. The primitive-array CSV renders object identities and is not an ID decode.
  The concrete-initial selector produced an empty result despite concrete objects in the complete table.
  That result is not evidence of zero concrete initials.
- `mark-propagation-review/thingsboard/job-20261014T092750.202397Z`: 26-query detail report;
  MAT exit 0, 7.19 seconds. Scalar element queries decode both mark arrays completely, map concrete
  slots to initials, decode tail ordinals, and census actor work. The original queries remain preserved.
- `mark-propagation-review/thingsboard/job-20261014T093717.577206Z`: one distinct FLOW-header query;
  MAT exit 0, 336.55 seconds. Reads current `FlowTree.markExclusion` set lengths separately from
  concrete leaf payloads.
- `mark-propagation-review/three-report-evidence.json`: combined reviewed counts, field availability
  and all 33 query statuses. Frozen query XML and launch/preview records are in the same directory.
  Report ZIP CRCs pass and the HPROF fingerprint is unchanged. No object-discard option is used.
- `multi-mark-diagnostic/run1-limit1/job-20261014T093008.202715Z`: exact source snapshots, source hashes,
  command argument vectors, compile/run logs and all 52 passing results.
- `counter-review/request-propagation-source-review/README.md`: source references, semantic constraints
  and proposed measurement points.
- `projects/*/bidi/profile-summary.json` and `normal-cpu-review`: saved whole-run JFR and bounded async
  CPU results, with their actual iteration bindings.
