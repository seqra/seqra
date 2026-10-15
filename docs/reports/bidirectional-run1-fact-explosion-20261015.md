# Run-1 added-fact growth investigation

Date: 15 October 2026. This is an investigation and selection update. No analyzer code
changes are part of this report. The measured core is
`85a7c0e367d9a2dc6360b03c2feb5e07b1583b66`; documentation HEAD
`c4e82f63677e72af72ddb2db9f5f8133dd725b9f` has the same complete `core` tree.
Earlier runtime revisions do not supply missing evidence for this revision.

The small fact-vocabulary hypothesis holds in the largest incoming store: **38,456
origin/header groups contain only 918 distinct payload values**. Concrete output marks
already pack into sets. The costs are repeated origin associations, duplicate input
directories/indexes and many separately allocated equal payloads. This store is 90.46%
static and 86.30% FLOW; all its FLOW inputs have abstract original premises. Static fan-in
is a stronger explanation for this hotspot than concrete input-mark multiplicity. The
selected maximum must not be reported as a global distribution.

This investigation initially supported these two selections from the
[current proposal](../design/bidirectional-next-optimizations-20261015.md):

1. Move the run-1 input/summary join to the callee owner.
2. Stop retaining discarded bound normal sink rules after joined prescan.

The subsequent [added-store removal redesign](../design/bidirectional-added-facts-removal-20261015.md)
replaces the first selection's raw-store-based ownership protocol. It removes the callee
copy and reuses the existing N2 summary inputs for late questions. Sink-cache retirement
remains selected. The measurements below still bind the unchanged current core.

Other candidates remain unselected. The sink change keeps full cold resolution; early
binding filters, streaming, managed rule caches and compact rule identities are separate
extensions. IR storage, reference-manager changes, weak-identity caches and broad queue
changes remain outside this iteration.

## Latest run reached

These are the latest measured executions of the current core, not new executions for this
investigation. The target remains a completed run-1 engine within 60 seconds, with limit 1.

| Project | Latest reached | Last completed engine / accepted forward report | Run-1 engine |
| --- | --- | --- | --- |
| OWASP | run-3 / forward / limit 3 | run-3 / run-3 | 349.964 s |
| Conductor | run-2 / backward / limit 2; OOM | run-1 / run-1 | 69.931 s |
| ThingsBoard | run-1 / forward / limit 1; OOM | None / None | 164.488 s; incomplete |
| Stirling-PDF | run-4 / backward / limit 4; timeout | run-3 / run-3 | 30.914 s |

The detailed [performance and correctness report](bidirectional-run1-abstraction-demands-performance-20261015.md)
retains the fixed settings, capture overhead, baseline findings and open acceptance limits.
The ThingsBoard census below reads its active run-1 guard capture. It is not a completed
closure or a matched-progress comparison with the corrected control.

## The stored values already merge output marks

`InitialAp`, scalar `Pattern` and scalar `Link` describe one mark slot: a concrete mark,
or abstract `*` with mark exclusions. They are not the stored `Facts` payload. A
`TaintTree` stores two `MarkSet` values at each path: exact and any.
`MarkSet` is a sorted, distinct integer array. Same-key additions union these sets and paths,
retain one current immutable tree, and return only new content as the delta. An exact
duplicate returns no delta. The path index also stores one group handle per path position,
not one posting per concrete output mark.

For example, two inputs with the same caller reference and admitted header that add
`x.f:T` and `x.f:U` produce one stored leaf with exact marks `{T,U}`. A later repeat of T
adds neither a group nor another index posting. `links()` and `overlapping()` expand the
packed tree into scalar views when a consumer asks for them.

Source:
[stored key and merge](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/store/AddedFacts.kt),
[TAINT leaves and union](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/Trie.kt),
[delta merge and position visitor](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/TrieOps.kt),
[scalar views](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/LeafViews.kt).
Existing `AddedFactStoreTest` and `TrieMergeDeltaTest` cover multi-mark union and duplicate
suppression. This documentation change does not claim a new test execution.

Thus a missing output mark-set representation does not explain the reported footprint.
The remaining questions concern the number of origin/header associations and the payload
and index costs behind those associations.

## A short path does not bound the stored relation

One callee store has this logical key:

```text
(full caller MethodKey, original caller premise, caller layer, call instruction,
 conclusion kind, link layer, output base, admitted field exclusion E,
 admitted FLOW mark exclusion mx)
```

The callee actor/full MethodKey is the enclosing owner. Output paths and concrete output
marks are packed content, not TAINT key dimensions. The caller MethodKey includes its
context and entry statement. A concrete input premise T differs from input premise U.
A joint `PremiseSet` differs from either independent premise. E and mx distinguish
admitted raw groups even where some scalar results overlap.

The small run-1 path grammar limits one part of this product. It does not limit the
number of callees, incoming call sites, caller contexts or original obligations. The
global 623 canonical `InitialAp` objects are reused across 532,543 admitted actor
memberships in 73,669 actors; neither count is the input relation's key cardinality.
`RunMethodAnalyzer.addInitial` deduplicates an initial per actor before it starts method
work. Many retained caller origins therefore do not imply that the callee starts the
same initial once for every origin.

Stored method FLOW conclusions require a simple abstract-mark initial. Requested concrete
initials start TAINT, not FLOW. FLOW groups can still differ because selected cleaners and
summary composition change mx, and strong writes change E. Limit 1 bounds counted path
depth; class accessors do not consume this limit. It does not bound either set's width.
A census that finds mostly FLOW groups would therefore
need an explanation based on abstract origins, routing and headers before concrete-mark
premise packing.

An unlabelled union across input marks would lose necessary dependencies. If input T
supports output A and input U supports output B, attaching `{A,B}` to both origins
invents T-to-B and U-to-A results. Making `{T,U}` an AND premise changes two alternative
origins into a joint requirement. A future compact encoding must preserve the exact
origin-to-result relation. Equal immutable payloads can share storage while retaining
that relation, but the current census must first measure equal payloads.

Raw storage also intentionally differs from method conclusion storage. It retains
covered descendants and separate E/mx annotations for later supplier, summary and Support
matching. `AddedFactStoreTest` covers these boundaries. Applying conclusion subsumption
or using a normalized fresh delta as the admitted header would change this contract.

## What the 1,523,417,616-byte figure contains

The original MAT selector selects **all** `AddedFactStore` objects. Its title cannot
restrict it to incoming actor stores. Address joins between the saved actor lifecycle
and store census establish the following exact split:

| Role | Stores | Current groups | Empty stores |
| --- | --- | --- | --- |
| Callee incoming raw links | 73,669 | 3,723,565 | 12,554 |
| Unresolved-call observation memo | 73,669 | 75,026 | 69,288 |
| All added-fact stores | 147,338 | 3,798,591 | 81,842 |
| Separate caller-side N2 directories | 87,496 | 3,723,573 | Separate relation |

The 234,834 accumulators contain 7,522,164 entries in total. Raw incoming and N2 group
totals differ by eight at this incomplete frontier. Their near equality is consistent
with the duplicated input protocol, but is not proof of identical rows or shared bytes.
The observation memo is separate from this duplication. Its callback records unresolved
calls; it is not the callee's input relation.

Incoming growth is concentrated. Across all 73,669 actor stores, the median is two
groups, nearest-rank p95 is 140, p99 is 895, and the maximum is 38,456. The largest
737 stores, the top 1% rounded up, contain 1,691,638 groups: **45.43%** of all incoming
groups. A workload-wide average would hide this fan-in distribution. This does not yet
identify which key dimensions dominate those large stores.

Within the all-store **default retained union**, disjoint class categories have these
shallow totals:

| Objects inside the selected retained set | Bytes |
| --- | --- |
| Integer arrays | 572,391,752 |
| Object arrays | 299,156,472 |
| Accumulator keys and entries | 182,332,368 |
| Group headers | 115,739,296 |
| Index nodes, integer map/set shells and handle lanes | 234,056,424 |
| Exclusively retained FLOW/TAINT wrappers | 5,145,408 |
| Exclusively retained trie nodes and child arrays | 20,747,752 |

Array totals do not by themselves attribute every array to an index or payload.
The selected set contains 3,798,591 entries and keys, 3,616,853 group headers and 1,393,193
index node/map/set/lane objects of each corresponding class. Each path-index node owns
both a membership set and an append-only handle lane, plus child lookup and traversal
containers. This preserves deduplication and paused cursor traversal, but adds physical
cost to each required association.

Only 160,794 FLOW/TAINT wrappers are exclusive to this retained selection. This is **not**
the number of payloads referenced by the stores: shared owners outside the selection
keep many payloads alive. Default retained sets can follow reference referents and do not
prove strong-only paths. The 1.523 GB is neither all payload bytes nor a reclaimable saving.
It does establish a large directory/index owner and does not establish millions of
unmerged one-mark payloads.

## Exact census of the largest incoming store

The largest store is `0x618e636f0`, owned by actor `0x618e63038` for
`org.thingsboard.server.common.data.page.BasePageDataIterable.next(): Object`.
The full actor key still includes its context and entry statement. A bounded reader uses
the existing MAT address/HPROF-offset indexes and class metadata to inspect exactly its
38,456 current entries. It does not open a full snapshot, rebuild indexes or scan a new
project. Indexed addresses, record tags, complete instance field layouts, array bounds,
actor/store ownership and saved CSV counts agree. Capture, sidecar and source metadata
are unchanged. This is a selected maximum, not a representative random sample.

| Current groups | Static S | This | Zero | Total |
| --- | --- | --- | --- | --- |
| FLOW | 31,651 | 1,537 | 0 | 33,188 |
| TAINT | 3,136 | 327 | 0 | 3,463 |
| REACH | 0 | 0 | 1,805 | 1,805 |
| Total | 34,787 | 1,864 | 1,805 | 38,456 |

All FLOW rows have a simple abstract original premise. They reference 112 distinct
canonical abstract initials: 105 static positions, six argument initials and one This
initial. TAINT has 3,441 rows with concrete nonzero initials and 22 with canonical zero,
for source-generated results. REACH has 1,805 rows with canonical zero. The whole store
references 271 concrete nonzero initials and no `PremiseSet`. Thus concrete
input marks contribute rows, but do not explain this hotspot's dominant FLOW population.
Its 236 concrete static initials cover 88 canonical path/tail/E geometries. Their distinct
input-mark widths are 1 for 34 geometries, 2 for eight, 4 for 44 and 5 for two. This
measures original input premises, not output TAINT leaf widths. Dropping only the mark
from those static premise keys in a descriptive projection reduces 3,202 corresponding
rows to 2,281 groups; it is not a sound merge rule or a byte-saving estimate.

There are 152 distinct kind/base/layer/E/mx headers after normalizing set contents.
The store references 1,590 caller MethodKey objects, 1,151 context objects and 607 call
instruction objects. Its 38,456 physical CallerRef objects reduce to 33,497 distinct
tuples of their physical context/entry/premise/layer/call fields. Of those tuples,
31,947 have exactly one header. These are physical field cohorts, not a proof of distinct
logical MethodKey/context/instruction values. They still expose the large origin
directory and modest header multiplicity in this store.

Only one group has nonempty E. FLOW has 6,030 groups with nonempty mx, with maximum width
208, and 27,158 with empty mx. Header widths can grow, but the measured row count here is
primarily incoming origins, not thousands of headers per origin.

Each of the 36,651 non-REACH entries references a different Facts wrapper. They reference
32,093 distinct root objects. REACH shares one value across 1,805 entries. A second bounded
pass compares every referenced trie by exact leaf values, full mark arrays, sorted accessor
arrays and canonical child values. Hash equality alone is never sufficient. Joining these
canonical roots to full kind/base/layer/E/mx headers gives:

| Quantity in this store | Physical objects or relation rows | Exact distinct values |
| --- | --- | --- |
| Complete input payloads, including REACH | 38,456 relation rows; 36,652 Facts objects | 918: 631 FLOW, 286 TAINT, 1 REACH |
| Non-REACH trie roots | 32,093 objects | 580 |
| Referenced trie nodes | 66,233 objects | 1,064 |

The 37,538 repeated-value extra rows retain their original caller associations. They cannot
simply be deleted. Equal immutable payloads can, in principle, share one value while those
associations remain separate. Keeping full header contents and the same root identity
already identifies 4,082 extra equal wrappers; the deeper comparison establishes much more
root duplication. Neither count measures reclaimable bytes, because other owners can hold
the same physical objects.

TAINT payloads contain one distinct output mark in 3,441 entries, two in one, three in one,
four in 13 and five in seven. Nonempty leaf widths also reach five. This directly confirms
working mark-set packing; it does not show hundreds of output marks per AP in this hotspot.

The existing per-store trie interner normally skips tries smaller than 100 nodes. Here
31,838 of the 32,093 roots are below 100 tree positions. Short equal trees can therefore
retain separate non-leaf objects. In addition,
[Results.flush](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/ap/ApOut.kt)
constructs a fresh Facts wrapper even when an identity operation preserves the root.
Prefix identity application and summary matching/grafting can also rebuild equal short
tries. These source paths explain how equal payload objects can accumulate despite correct
mark union. Their allocation frequency and avoidable global bytes remain separate measures.

The scalar census took 63.387 seconds with 905,928,704-byte peak RSS. It decoded 200,957
objects and 11,090,053 bytes of targeted HPROF object records, plus the file header and
separate metadata/index-page reads. Its 22,042,266-byte output and terminal receipt are
in `provenance-review/low-memory-prototype/job-top-census-20261015T073818.792364Z` under
the investigation packet. It is analysis-tool time, not run-1 engine time.
The exact-value pass is
`provenance-review/low-memory-prototype/deep-top-v1/job-deep-20261015T075052.583555Z`.
It completes in 7.540 seconds, decodes 198,726 records and retains its source, synthetic
collision controls, complete root mapping, value join and unchanged-input receipt.

## An algorithmic source to measure: static fan-out

Every JVM call plan includes the single `ClassStatic` base S in its touched set. Input and
return bindings preserve S. The runtime's early bypass checks the base, not an individual
static field, and forwards a surviving static input to every resolved callee. There is no
per-callee static-footprint guard on this route. The callee admits the full origin/header
to its raw store before abstract initial emission and catalog-backed request handling.

Source:
[JVM call binding](../../core/opentaint-dataflow-core/opentaint-jvm-dataflow/src/main/kotlin/org/opentaint/dataflow/jvm/bidi/interp/JIRCallPlanBuilder.kt),
[call dispatch](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodCallServices.kt),
[raw admission](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodAnalyzer.kt).

This routing behavior explains a way to produce the static-heavy fan-in measured in the
largest store. It can multiply rows even with few distinct path shapes and does not depend
on a missing TAINT mark union. Its whole-heap contribution still needs a global census;
the selected maximum alone does not prove that it dominates every actor.

The transitive static catalog identifies accesses and rule interests. It does not currently
prevent raw static call inputs from being retained. A future bypass must distinguish an
unaffected input that can pass unchanged from an input a callee can read, transform, clean
or use in a condition. The abstract `(S, ., *, ...)` initial and summary remain necessary.
Requested concrete marks remain additive, and a static transfer such as `C.g -> C.f` can
still require a dynamic request. Static bypass remains a future design, outside the two
selected changes.

## Directions for a later optimization design

The evidence supports two different targets: avoid unnecessary origin associations, and
avoid duplicate representations of required payloads. The following candidates remain
outside the two selected changes. Their conditional order is by expected retained-memory
scope; it is not a measured byte-saving ranking.

| Candidate | Current behavior | Possible new behavior | Why it can help; evidence limit |
| --- | --- | --- | --- |
| Avoid unaffected static call inputs | Surviving S inputs enter every resolved callee and create raw provenance rows | Prove a static partition unaffected by the complete callee code/rule effects and pass that partition unchanged at the caller's callee stage | Can avoid whole admissions, indexes and joins; requires an effect/identity proof and unknown-target fallback, not just a read-access check |
| Share equal immutable payload values | Different origins often retain equal short tries and wrappers | Share an equal complete payload or canonical short root while retaining every original relation key | Directly supported by 38,456 rows versus 918 values in the largest store; index/key memory remains, and co-owners/cache cost prevent a global byte estimate |
| Avoid identity reconstruction | Full identity application still builds result wrappers and can rebuild paths | Reuse the input for a proved whole-root identity while preserving filters, callbacks and cuts | Can avoid allocation and repeated AP work; does not remove required origins and is primarily a CPU/allocation candidate until measured |

Static bypass must keep abstract `(S, ., *, ...)` emission and correct summarization,
source/cleaner/condition effects, changed-static transfers and dynamic concrete requests.
Nonempty-prefix identity restricts to matching paths; it is not a whole-input identity.
TAINT with nonempty E can split exact and must results even under a root identity.
Both need the current path unless a separate proof covers them.

Payload sharing must compare full values, preserve admitted headers and retain the exact
origin-to-output relation. Requested marks are alternatives, not a new AND premise. A
larger cache can consume more memory and add lookup work. Any future managed cache must
use the existing reference-manager contract; these findings do not require a manager
rewrite or weak-identity cache.

## Earlier selection and the new redesign

| Selected change | Current behavior | New behavior | Expected benefit and limit |
| --- | --- | --- | --- |
| Added-store removal; replaces old callee-owned join | Caller and callee independently accumulate/index the admitted input relation | Drop callee input history; keep the current N2 summary join and reuse it for late questions | Avoid the callee directory/index and its raw-link lifetime; N2 remains a measured large owner |
| Discarded normal sink cache retirement | Normal sink caches keep full bound lists after selection rejects entries | Build the selected view after joined prescan, release obsolete unfiltered roots, retain only selected cold results | Avoid retention of discarded bindings; actual saving depends on selected/discarded density |

The older join design targeted the 1,247,295,120-byte caller-directory retained union.
The new redesign targets the callee added-store copy instead; the all-added-store union
also contains the observation memo and cannot be called a raw-only saving. The second targets a measured
1,092,011,616-byte normal-sink-cache union, not an equal predicted saving. These unions
overlap other owners and cannot be added. Neither change alone proves a 60-second run-1.

The [new design](../design/bidirectional-added-facts-removal-20261015.md) specifies
direct emission, deferred concrete answers, late-request replay, caller-owned return
application, ND candidate handling, lifecycle and cancellation. Summary publication still batches
until the method queue empties. Sink retirement preserves full bound identities, order,
anonymous ordinals, validation, and separate normal/allRelevant source/cleaner behavior.

## Evidence and remaining measurements

The current batch is
`/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/run1-abstraction-demands-v2/20261015T004517.956676Z`.
The existing optimized ThingsBoard heap report is
`heap-review/optimized/thingsboard/job-20261015T040356.874180Z-8fd2f16922`.
The new read-only review packets are under
`report-preparation/run1-fact-explosion-20261015`:

- `source-merge-review`: exact source/test hashes, merge boundary and existing controls.
- `provenance-review`: routing, initial deduplication, Support and origin boundaries.
- `heap-census-design/existing-evidence.json`: exact saved-CSV owner joins and histogram
  categories for optimized and corrected-control run-1 captures.
- `heap-census-design`: bounded new query recipe, parser checks and execution gates.
- `provenance-review/low-memory-prototype/final-targeted-review`: complete targeted
  reader/equality receipts and the readable largest-callee identity.

The index-only preflight checks all 38,456 top entries against the frozen kind/base/layer
projection and passes. Its conservative projected global cost exceeds the execution gate;
the global census is **not run**. `heap-census-design/measurement-closed.json` closes that
work. There is no global kind/base distribution to infer from the top-store result.

The saved owner/tail census is complete, and the top-store kind, premise, geometry, mark
width and exact-value measurements are complete. Whole-population kind/base, equal-value
sharing and concrete-initial cohorts remain unmeasured. Preserve the distinction between
relation rows, payload values, mark memberships, postings, updates and method starts in the
next fixed-project retest. The new store-removal proposal uses these measurements without
claiming a global duplicate-payload saving.
