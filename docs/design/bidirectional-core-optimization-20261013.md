# Analyzer core: next memory optimizations

Status: new proposal, not selected for implementation. Date: 13 October 2026.
This proposal replaces the priorities in the [old proposal](bidirectional-core-optimization.md).
It uses the latest reviewed project heaps and profiles. IR objects, IR storage, IR tables and IR providers
remain outside the scope.

The largest measured core owners after form cleanup are standing requests, subscription state and pending
queue entries. Start with their storage. Keep NNF for both source and sink conditions; do not restore cubes.
Managed method-form references already release compiled forms in the failing Conductor and ThingsBoard
captures. Further form eviction alone does not address those remaining owners.

The measured production code is `b0a2448c0af7906ed49d90f203dd5a2a1c881523`. These measurements precede
the requested rollback of the `SoftReferenceManager` rewrite and `WeakIdentityCache`. They explain the
measured workload, but do not validate the revised code. Retest the revised core before adopting a new change.
The original manager and standard rule-validation code are the starting point for that retest. The two
removed designs are optional candidates N11 and N12 below; they are not prerequisites for the form cache.

## Latest reached iterations and evidence

An iteration is reached when its `BIDI_RUN_START` is recorded. Engine completion and acceptance of a
forward report are separate events. A failed later run does not replace the previous accepted forward
report. `BIDI_BARRIER_END` alone is insufficient evidence: it is also written from a `finally` block.

| Project | Latest reached iteration | Last complete engine iteration | Last accepted forward report | Primary result |
| --- | --- | --- | --- | --- |
| [OWASP](../reports/bidirectional-o1-o3-projects/OWASP.md) | run-3, forward, limit 3 | run-3 | run-3 | Complete; all 4,112 main sink identities retained |
| [Conductor](../reports/bidirectional-o1-o3-projects/conductor.md) | run-2, backward, limit 2 | run-1 | run-1 | OOM; 7 main identities retained, 293 additional identities in an incomplete result |
| [ThingsBoard](../reports/bidirectional-o1-o3-projects/thingsboard.md) | run-1, forward, limit 1 | None | None | OOM; empty accepted report, all 14 main identities absent |
| [Stirling-PDF](../reports/bidirectional-o1-o3-projects/Stirling-PDF.md) | run-4, backward, limit 4 | run-3 | run-3 | Timeout; 14 main identities retained, 3 missing, 42 added |

The OWASP live heap is a separate warm capture during **run-1**, followed by a replay that reaches run-3.
The Conductor primary guard heap captures **run-2**; the ThingsBoard primary guard heap captures **run-1**.
The final PDF run reaches **run-4** and has no guard heap. Its older O3/O1 checkpoint heaps capture
**run-3** OOM and must not be presented as final run-4 memory measurements. The
[checkpoint report](../reports/bidirectional-o1-o3-checkpoints.md) records those revisions and all eight
checkpoint results. The bounded PDF observer also reaches run-4 timeout and reproduces the primary findings.

Keep the original configurations: OWASP 8 GiB/1,000 seconds, Conductor 8 GiB/1,080 seconds,
ThingsBoard 12 GiB/1,080 seconds, and PDF 8 GiB/1,080 seconds. Run Gradle `runProjectAnalyzer` directly.
Run-1 starts at limit 1. No limit-0 run exists. Use the same rules, model, Temurin 17 runtime, storage
batch 2,000 and disabled multi-release loading. The OWASP input remains the disclosed cached release-8
build with the CI analysis configuration, rather than a fresh CI autobuild.

Retained unions overlap. Bytes below measure owners, not recoverable savings. Do not add the rows.
Sampled allocation weights measure allocation traffic, not retained memory or exclusive wall time.

## Priority by expected retained-memory impact

This order estimates impact after the preceding changes. It is not a promise of a byte saving. The first
three changes target measured representation overhead without eliminating logical requests or subscriptions.
Lower candidates need more evidence before implementation. CPU and allocation work has a separate order.

| Rank / ID | Proposed change | Expected retained-memory impact | Measured basis and limit |
| --- | --- | --- | --- |
| 1 / N1 | Compact standing request records and positional indexes | Very high in ThingsBoard | run-1 request-store union: 2,885,461,840 B; 22,317,268 distinct seen pairs |
| 2 / N2 | Compact subscription indexes; remove an unused accumulator index | High in Conductor | run-2 subscription union: 1,487,146,248 B; index unions: 500,992,808 B and 256,274,344 B |
| 3 / N3 | Pack pending queue entries and reduce excess queue capacity | High in ThingsBoard | run-1 backing-array-root union: 1,210,199,096 B; 16,082,582 queued entries |
| 4 / N4 | Compact and share immutable AP delta graphs | Medium to high, overlaps N2 | Conductor run-2 accepted parts: 566,535,456 B; nodes/accessors/child arrays within them: 419,634,720 B |
| 5 / N5 | Specialize small NNF descriptors and share immutable topology | Potentially high in warm forms; conditional | OWASP run-1 compiled descriptor union: 1,715,130,896 B; current singleton distribution must still be measured |
| 6 / N6 | Share immutable bound-rule condition data | Medium, conditional | Conductor run-2 rule-provider union: 372,739,560 B; removable duplication is unknown |
| 7 / N7 | Lazy remaining fact-group containers and compact reach storage | Low to medium; conditional | Conductor run-2 dense group arrays: only 14,593,136 B; older PDF group sizes require a fresh census |
| 8 / N8 | Share immutable cleaner/action payloads | Low to medium after N5/N6; conditional | Repeated action payloads are observed; exclusive remaining bytes have not been measured |
| 9 / N9 | Compact remaining residual reverse conjunctions | Low; conditional | NNF already removes compiled source/sink cube inventories; measure any remaining custom/legacy owner first |
| 10 / N10 | Reduce retained form recipes and empty slot owners | Conditional | ThingsBoard run-1 recipe/cache-shell union: 684,989,280 B, despite empty cached referents; necessary metadata and rule-provider ownership overlap |
| 11 / N11 | Redesign managed-reference bookkeeping and transitions | Unknown; optional | Rebuildable wrapper growth and registration races require a separate design; guard captures show zero tracked wrappers after cleanup |
| 12 / N12 | Introduce a weak identity cache for validated rule occurrences | Unknown; optional | No isolated retained-byte or validation-time measurement justifies a new utility yet |

N1/N2/N3 retain all logical work. Queue coalescing and logical subscription removal are separate algorithm
changes and are not included in their estimated savings. N4/N5/N6/N8 share data only after immutable ownership
is established. A global strong interner would defeat reclamation and is not proposed.

## N1. Store standing requests once

`RequestStore` keeps a seen set and a positional lookup index. In the ThingsBoard **run-1** retained set,
pair bodies use 535,614,432 B, linked-map entries use 892,690,720 B, hash-map nodes use 714,893,952 B,
and table arrays use 411,833,408 B. These are parts of the same union. The storage cost warrants a
representation change before changing the number of requests.

Use one canonical record for each complete request key. Keep its initial AP/premise, mark and position.
Use primitive record IDs in the secondary path index. Replace per-path `LinkedHashSet` and duplicate pair
wrappers with compact empty/singleton/primitive-array membership. If mutable growth needs a larger set,
introduce it only at that size and include its spare capacity in measurements.

Preserve exact deduplication, encounter order where observable, and both standing-join arrival orders:
a request must join facts already stored, and a later fact must join requests already stored. Do not weaken
the full key to just a path or mark. Keep full AP matching after any coarse positional lookup.

Validation: compare raw requests, summaries, source hits and findings against the independent closure
fixtures under different arrival orders and positive limits 1, 2 and 3. Measure owned storage per record,
then repeat ThingsBoard with its original configuration. Report the latest reached run even if it fails.

## N2. Separate exact accumulation from indexed subscription lookup

Conductor **run-2** has 2,086,288 accepted delta parts alongside 703,732 cumulative exact groups.
The delta-part index alone contains 7,264,648 linked-map entries and 6,778,966 boxed integers. Their
399,049,376 shallow bytes explain much of the 500,992,808 B index union.

First split the exact added-fact accumulator from the indexed store. `CalleeSubscriptions` calls
`AddedFactStore.add`, but does not query the positional index built by that store. This consumer can use
an exact accumulator. Other link consumers still need positional lookup; retain their indexes.

Next assign stable primitive handles to subscription records and use one positional directory over those
handles. The record must preserve the complete caller reference, exact group key and `zeroOnly` policy.
Future matching reads an immutable cumulative snapshot; notifications still replay every newly accepted
delta required by the protocol. Do not infer that old parts can be dropped merely from their count.

Preserve P1–P6: register before replay, insert before notification, linearizable snapshots and full matching.
Preserve E6: full members, full caller references, independently joined caller/link layers, zero-premise
behavior, exclusion/must changes and fact growth. Equal paths do not imply equal subscription records.
Do not subsume or remove logical subscriptions in this storage change.

Validation: run both subscription arrival orders and concurrent register/publish fixtures, with zero and
nonzero premises, exclusion changes and must growth. Measure index bytes per accepted part and per exact
group, then repeat Conductor and check strict findings and the latest reached iteration.

## N3. Pack pending queues without changing scheduling

ThingsBoard **run-1** queues contain 16,082,582 entries but allocate 45,116,783 backing cells.
Within the backing-root retained set, queue entry bodies use 643,303,080 B, request message bodies use
385,779,480 B and object arrays use 180,500,088 B. Packing entries and controlling capacity can reduce
overhead even if every logical event remains necessary.

Prototype an owned packed entry layout. Keep captured priority, steps and FIFO sequence in primitive
storage, with event payload references. Consider a FIFO lane for ordinary rank-1/step-0 events only if it
preserves the current global ordering against work events. Work requeue must capture fresh priority using
the existing rules. A primitive comparator can be implemented independently as an allocation improvement.

Preserve reservation/count before queue visibility, insertion rollback, exactly-once handler completion,
cancellation and drain behavior. A queue resizing change must not introduce a work cap or drop an event.
Check ordering under ties, mixed ordinary/work events, requeue and cancellation before project tests.

A separate feasibility study can measure pending request coalescing. The current bounded sample has
48 cells and 43 structural keys, with two repeated keys. It does not establish a global duplicate rate.
Coalescing requires proof that full method/premise/mark keys and reservation accounting remain correct.
It is not part of the initial packing change.

## N4. Compact immutable AP graphs and reduce trie work

Start with single-child trie-node storage and unchanged-subtree reuse. Preserve the complete accessor,
child and inherited exclusion/must semantics. Share only frozen nodes. Measure duplicate graph shapes
before adding interning; a duplicate-shape rate is not yet available.

The separate allocation track is especially relevant to PDF **run-4**. Its primary recording has about
2.056 TB sampled allocation weight. Subtraction appears in 25.67%, staging in 11.68% and cleaner-chain
application in 11.14% of that weight; these inclusive groups overlap. Near the limit, GC frames appear in
67.56% of asynchronous CPU samples. Conductor **run-2** also shows subtraction and merge allocation.

Use primitive scratch buffers, allocate child builders after the first changed child, and reuse unchanged
subtrees. If an operation-local memo is needed, its key includes node, stored state and inherited cover;
cache a null result explicitly. A node-only or global memo is incorrect. Keep deep traversal iterative and
keep cancellation checks. Verify T1–T4 and T2′, including whole merged deltas after exclusion/must changes,
canonical empty-exclusion lookup after the last must is removed, and separate normal/demand groups.
Do not combine this work with a FLOW/exclusion semantic change.

## N5–N10. Conditional follow-ups

**N5, small predicates.** A singleton descriptor can hold one owned pattern/base/mark with implicit root
and leaf ordinals, instead of nine topology arrays, a generic base map and per-base leaf arrays. Constants
need no leaf storage. Larger conditions keep the general NNF DAG. A FLOW fact has no mark and still visits
all applicable positive leaves on its base. Both source and sink rules use these layouts. Keep logical
occurrence IDs, ordered actions, proof scopes and public ordinal behavior separate from shared topology.

The complete singleton census is from the older O1 strong-cache ThingsBoard **run-1** checkpoint:
2,329,585 singleton, 16 constant and 142,217 multi-node predicates. Its descriptor union is
1,758,031,280 B and base-map union is 969,107,360 B, which overlap. Do not apply that older singleton
fraction to current code without measuring current forms. The OWASP warm **run-1** capture supplies
current descriptor bytes, not this distribution.

**N6, rule templates.** Freeze condition templates and bind methods/positions with small descriptors.
Conductor **run-2** retains 3,608,056 `ContainsMark` nodes and 3,639,113 condition atoms in the provider
graph. These are raw/resolved rule objects, not surviving compiled NNF forms. Preserve whole raw-rule
validation, original occurrence/serialized identity, relevant-rule policy and the complete
`allRelevant=true` mark universe. Measure equal-template frequency and exclusive bytes before implementation.

**N7, fact groups.** Use lazy empty/singleton containers and compact reach representation only where a
current census supports it. Conductor's 14.6 MB dense arrays make an array-only rewrite a low priority.
Preserve exact annotated group keys and all exclusion/must growth. Do not absorb normal exact groups
under must metadata or suppress a full delta required after a metadata change.

**N8, actions.** Share frozen cleaner/action/path payloads within bounded owners. Keep configured order,
duplicate occurrence identity, per-action filters and the cleaner normalization from
`saloed/cleaner-action-normalisation`. Do not deduplicate cleaner steps based only on equal syntax.

**N9, residual reverse blocks.** Inspect remaining custom/legacy reverse conjunction owners. Keep logical
premises and action order if a compact block is worthwhile. Source/sink NNF must not be expanded into
cubes to create a target for this optimization.

**N10, recipes.** Measure which method recipe and slot fields are needed for an independent rebuild.
Compact redundant headers or lazy empty slots without retaining builder closures or changing the required
rule lookup. Recipe bytes overlap provider and method ownership; their full union is not removable.

## N11. Optional managed-reference manager redesign

The current rollback restores the original `SoftReferenceManager`. The form cache uses its existing
`createRef`, `cleanup` and `enable` API. A lifecycle rewrite is a separate possible optimization.

Before selecting it, measure wrapper counts through repeated build/eviction cycles and test registration
against cleanup. Define a transition protocol explicitly: either cleanup owns every accepted registration,
or registration is rejected while disabled. A possible design uses a short lifecycle lock, a detachable
identity registry and a `ReferenceQueue` to prune JVM-cleared wrappers. Clear referents outside the lock
and avoid a copied O(n) cleanup batch. Never call a form builder or take a slot lock from manager cleanup.

This would trade synchronization and bookkeeping for bounded wrapper retention and a stronger lifecycle
contract. Its retained-byte impact is unknown. Guard snapshots with zero tracked references do not prove
wrapper accumulation during ordinary execution. Preserve all existing managed-cache clients and the
memory manager's thresholds, GC requests and cancellation policy. Require deterministic registration/
cleanup/re-enable races, queue-pruning tests and a separate design review before implementation.

## N12. Optional weak identity validation cache

The current rollback removes `WeakIdentityCache`. Validate raw rules directly and keep diagnostic keys
detached from rule objects. A future cache is optional if profiling proves repeated raw validation is costly.

Specify why identity rather than value is the correct key. Temporary bound rules must not be retained by
keys, values, callbacks or diagnostic records. Values must not refer back to their key. A possible utility
uses weak identity keys, a `ReferenceQueue`, bounded maintenance and an explicit owner lifetime. Define
concurrent lookup, duplicate computation, failures and warning deduplication before choosing its API.
Account for wrapper/table overhead and compare it against no cache and owner-local memoization.

Use deterministic cleared/enqueued references for tests, rather than waiting for GC. Include deep raw
conditions without recursive hashing or rendering. Keep distinct rule occurrences and validate every raw
action/literal before folding. No measured savings or performance benefit is claimed for this candidate.

## Other allocation and CPU candidates

1. **Primitive queue comparison.** ThingsBoard run-1 queue comparison accounts for about 243.58 GB,
   or 35.81%, of sampled allocation weight. Remove boxing while preserving the comparator order.
   This has little direct retained-memory effect and can be separate from N3.
2. **Trie builders and operation-local reuse.** Follow N4's semantic keys and controls. Measure bytes per
   operation and inclusive CPU separately from retained AP graphs.
3. **Alias membership and direct traversal.** Measure current repeated scans before adding an index.
   Indexes can increase retained memory; preserve exact type and alias matching.
4. **Analyzer-owned graph reuse.** Reuse a required adapter view only where lifetimes permit it. Measure
   construction and retained costs together. Keep IR providers and IR objects unchanged.

## Adoption and verification

Each selected change needs a separate detailed design, implementation plan and commit. No N candidate is
selected by this document. First finish the requested rollback and retest. Then select changes by the new
owner measurements. Do not implement a manager or weak-cache utility as an incidental prerequisite.

Keep the exclusion specification table, D23 source override, ordered cleaner effects, source admission,
full-condition backward mark scope, stable proof/occurrence IDs and NORMAL/DEMAND layers. Signed
`P AND NOT P` folds to false before the remaining negative source/sink approximation. Keep complex
source and sink conditions in NNF and evaluate contributions one incoming fact at a time.

For each implementation, run meaningful common/JVM controls and affected consumer checks. Repeat the
four fixed projects on the latest code, compare rule ID plus sink fingerprint plus full method signature
against main, and retain location details. Do not relax the known PDF differences. Capture CPU/allocation
profiles and guard heaps on OOM. Report the latest reached run-N, direction, positive limit, last complete
engine iteration and last accepted forward report, plus the snapshot's own run-N. Mark unavailable fields
explicitly. A partial scan, a shorter OOM or a retained incomplete report does not pass a resource gate.
