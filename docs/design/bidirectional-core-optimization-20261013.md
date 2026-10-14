# Analyzer core: next memory optimizations

Status: historical pre-iteration proposal. N1/N2 are now implemented. N3 remains deferred.
The [14 October proposal](bidirectional-core-optimization-20261014.md) replaces these priorities using
the [fresh N1/N2 report](../reports/bidirectional-n1-n2-performance-20261014.md).
All runtime, owner and selection statements below refer to the earlier revision; they are not current measurements.
Date: 13 October 2026.
This proposal replaces the priorities in the [old proposal](bidirectional-core-optimization.md).
It uses the latest reviewed project heaps and profiles. IR objects, IR storage, IR tables and IR providers
remain outside the scope.

The largest measured core owners after form cleanup are standing requests, subscription state and pending
queue entries. The [N1–N3 analysis](bidirectional-n1-n3-analysis.md) distinguishes the standing relation,
accepted delta history and pending transport. The [N1/N2 proposal](bidirectional-n1-n3-implementation-proposal.md)
first removes redundant subscription history and unsuccessful join scans, then compacts necessary state. It replaces this
document's initial storage-first recommendation. Keep NNF for both source and sink conditions; do not restore cubes.
See the [short current-versus-new comparison](bidirectional-n1-n3-implementation-proposal.md#behavior-changes-at-a-glance)
for each selected proposal's behavior change and expected benefit.
Managed method-form references already release compiled forms in the failing Conductor and ThingsBoard
captures. Further form eviction alone does not address those remaining owners.

N3 is excluded from the next iteration. Its [deferred design](bidirectional-n3-deferred-proposal.md) can be
reconsidered if fresh measurements after N1/N2 show the same substantial queue/backlog pattern. The current
queue size does not establish a material benefit from changing transport or scheduling.

The latest measurements use runtime commit `38a400c0d` and manifest revision `bd6810823`, after the
requested rollback of the manager rewrite and weak identity cache. The
[fresh retest](../reports/bidirectional-core-rollback-20261013.md) passes 1,320 common/377 JVM tests and
reproduces the four primary output sets and terminal iterations from `b0a2448c0`. Fresh Conductor run-2
and ThingsBoard run-1 heap owners are verified and used below. OWASP warm, old singleton, provider and
JFR allocation measurements retain their explicit older-revision attribution. Those older measurements
supplement the fresh data; they do not establish an isolated rollback saving.
The original manager and direct rule validation are now restored. The two
removed designs are optional candidates N11 and N12 below; they are not prerequisites for the form cache.

## Latest reached iterations and evidence

An iteration is reached when its `BIDI_RUN_START` is recorded. Engine completion and acceptance of a
forward report are separate events. A failed later engine does not replace the previous accepted forward
report. A barrier can fail after a report update, so use direct acceptance evidence.
`BIDI_BARRIER_END` alone is insufficient evidence: it is also written from a `finally` block.

| Project | Latest reached iteration | Last complete engine iteration | Last accepted forward report | Primary result |
| --- | --- | --- | --- | --- |
| [OWASP](../reports/bidirectional-o1-o3-projects/OWASP.md) | run-3, forward, limit 3 | run-3 | run-3 | Complete; all 4,112 main sink identities retained |
| [Conductor](../reports/bidirectional-o1-o3-projects/conductor.md) | run-2, backward, limit 2 | run-1 | run-1 | OOM; 7 main identities retained, 293 additional identities in an incomplete result |
| [ThingsBoard](../reports/bidirectional-o1-o3-projects/thingsboard.md) | run-1, forward, limit 1 | None | None | OOM; empty accepted report, all 14 main identities absent |
| [Stirling-PDF](../reports/bidirectional-o1-o3-projects/Stirling-PDF.md) | run-4, backward, limit 4 | run-3 | run-3 | Timeout; 14 main identities retained, 3 missing, 42 added |

The b0 OWASP live heap is a separate warm capture during **run-1**, followed by a replay that reaches run-3.
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

This order estimates impact after the preceding changes. It is not a promise of a byte saving. Only N1 and
N2 are selected for the next iteration. They include selective joins and cumulative subscription storage,
and preserve logical requests, accepted subscription leaves and required analysis effects. N3 is deferred;
its historical owner size is not an estimate of savings or a reason to include it in this iteration.
Lower candidates need more evidence before implementation. CPU and allocation work has a separate order.

| Rank / ID | Proposed change | Expected retained-memory impact | Measured basis and limit |
| --- | --- | --- | --- |
| 1 / N1 | Factor standing requests and use selective joins | Very high in ThingsBoard | Fresh run-1 request-store union: 3,095,473,728 B; 24,981,269 distinct seen pairs |
| 2 / N2 | Replace retained subscription delta history with cumulative entries; normalize E6 bindings | High in Conductor | Fresh run-2 subscription union: 1,538,287,888 B; index unions: 509,006,864 B and 259,069,008 B |
| Deferred / N3 | Group pending requests into counted mailboxes and give work finite service intervals | Conditional; material benefit not established | Fresh ThingsBoard run-1 backing-array-root union: 1,214,843,344 B; 15,764,679 queued entries. Reassess only if the pattern persists after N1/N2. |
| 4 / N4 | Compact and share immutable AP delta graphs | Medium to high, overlaps N2 | Fresh Conductor run-2 accepted parts: 607,697,168 B; nodes/accessors/child arrays within them: 452,468,200 B |
| 5 / N5 | Specialize small NNF descriptors and share immutable topology | Potentially high in warm forms; conditional | b0 OWASP run-1 compiled descriptor union: 1,715,130,896 B; current singleton distribution must still be measured |
| 6 / N6 | Share immutable bound-rule condition data | Medium, conditional | b0 Conductor run-2 rule-provider union: 372,739,560 B; removable duplication is unknown |
| 7 / N7 | Lazy remaining fact-group containers and compact reach storage | Low to medium; conditional | b0 Conductor run-2 dense group arrays: only 14,593,136 B; current group layouts require a fresh census |
| 8 / N8 | Share immutable cleaner/action payloads | Low to medium after N5/N6; conditional | Repeated action payloads are observed; exclusive remaining bytes have not been measured |
| 9 / N9 | Compact remaining residual reverse conjunctions | Low; conditional | NNF already removes compiled source/sink cube inventories; measure any remaining custom/legacy owner first |
| 10 / N10 | Reduce retained form recipes and empty slot owners | Conditional | Fresh ThingsBoard run-1 recipe/cache-shell union: 684,922,856 B, despite empty cached referents; necessary metadata and rule-provider ownership overlap |
| 11 / N11 | Redesign managed-reference bookkeeping and transitions | Unknown; optional | Older b0 guards counted zero tracked wrappers; fresh original-manager wrapper cardinality is unrequested. Measure ordinary wrapper growth and registration races separately. |
| 12 / N12 | Introduce a weak identity cache for validated rule occurrences | Unknown; optional | No isolated retained-byte or validation-time measurement justifies a new utility yet |

N1/N2 retain all required logical work. N2 removes historical objects through a cumulative-state refinement.
Neither reduces valid caller contexts or caps state. Their detailed equivalence and protocol gates remain
open. The next iteration preserves request transport, event accounting, queue layout and scheduling.
N4/N5/N6/N8 share data only after immutable ownership
is established. A global strong interner would defeat reclamation and is not proposed.

## N1. Factor standing requests and make joins selective

`RequestStore` keeps a seen set and a positional lookup index. In the fresh ThingsBoard **run-1** retained
set, pair bodies use 599,550,456 B, linked-map entries use 999,250,760 B, hash-map nodes use 800,078,304 B,
and hash-map table arrays use 392,402,320 B. These are parts of the same union. The set and index already
share Pair bodies. These requests have been processed and remain active for later links.

Group mark memberships by full origin initial and position memberships by full requested path. Index row
handles once and use primitive exact memberships. For a concrete added mark, probe only that requested mark;
for an abstract mark, visit requested marks outside its exclusions. Keep full overlap/action checks and
both standing-join arrival orders. Do not retire answered requests. Preserve encounter order with primitive
order data in the first version. The detailed proposal defines identities, mutation safety and tests.

Measure mark/position split and group density before selecting a dense layout. No logical request reduction
or percentage saving is established by the current heap.

## N2. Replace subscription delta history with cumulative entries

Fresh Conductor **run-2** has 2,165,026 accepted-part list entries alongside 679,558 cumulative exact
groups. These directory counts differ from distinct object counts. The delta-part index contains
7,397,171 linked-map entries and 6,918,460 boxed integers. Their 406,582,200 shallow bytes explain much
of the 509,006,864 B index union. The capture has 2,723,788 handled events and five pending reservations;
different progress from b0 prevents an isolated rollback memory-savings claim.

First split the exact added-fact accumulator from the indexed store. `CalleeSubscriptions` calls
`AddedFactStore.add`, but does not query the positional index built by that store. This consumer can use
an exact accumulator. Other link consumers still need positional lookup; retain their indexes.

Next use stable handles for cumulative ordinary subscription groups and one positional directory. Replay
each accepted immutable delta transiently. Later delivery captures cumulative candidate snapshots before
callbacks. Key groups from admitted inputs, not delta headers. Preserve the separate first-wins zero-only
directory keyed by full caller reference.

Normalize a matched forward E6 member to its full caller reference and link layer before changing delivery.
Otherwise E6 can retain every cumulative snapshot. Keep member slots, full distinct combinations and growing
conclusions. The detailed proposal sets the cumulative-transfer equivalence gate and P5 representation
refinement; current part-list proofs do not prove the new encoding. Keep P1–P4/P6 ordering and full AP matching.

## N3. Deferred request transport and scheduling

This candidate is excluded from the next iteration. Retain it for possible later selection only if the
same queue pattern remains in fresh post-N1/N2 measurements. The following describes that future option.

Fresh ThingsBoard **run-1** queues contain 15,764,679 entries but allocate 51,339,702 backing cells.
Within the backing-root retained set, queue entry bodies use 630,587,160 B, request message bodies use
378,212,496 B and object arrays use 205,411,552 B. Packing entries and controlling capacity can reduce
overhead even if every logical event remains necessary.

The same capture has 197,740,423 handled events and 15,810,688 pending reservations. Its progress differs
from b0, so byte differences do not isolate the rollback's effect.

Use a grouped pending/in-service request mailbox per full destination method key, with one live counted wake.
Suppress exact transport duplicates and process finite batches. Keep synchronous local raises and actor-owned
standing requests. New mailbox admission and final-empty transitions must serialize with completion; the
existing one-count-per-event `accept` cannot directly implement coalesced admission.

Bound channel intake and serve zero Work, ordinary events and nonzero Work in finite phases. This changes
strict rank scheduling and needs a specification amendment and complete-closure tests. Then compact the
remaining FIFO/Work lanes and capacity. No blocking producer channel, request cap or overflow drop is proposed.

The older b0 sample of 48 cells and 43 structural keys does not establish duplicate frequency. Grouping
removes per-event wrappers even if all pending keys differ. Logical pending state can still be large.
The [deferred N3 proposal](bidirectional-n3-deferred-proposal.md) retains accounting, cancellation, race
and scheduling gates for that future option.
No mailbox, counted-wake, scheduler or queue-layout stage remains in the active N1/N2 sequence.

## N4. Compact immutable AP graphs and reduce trie work

Start with single-child trie-node storage and unchanged-subtree reuse. Preserve the complete accessor,
child and inherited exclusion/must semantics. Share only frozen nodes. Measure duplicate graph shapes
before adding interning; a duplicate-shape rate is not yet available.

The separate allocation track is especially relevant to PDF **run-4**. The older b0 primary recording has about
2.056 TB sampled allocation weight. Subtraction appears in 25.67%, staging in 11.68% and cleaner-chain
application in 11.14% of that weight; these inclusive groups overlap. Near the limit, GC frames appear in
67.56% of asynchronous CPU samples. Conductor **run-2** also shows subtraction and merge allocation.
The fresh rollback captures verify PDF's normal **run-3** and near-limit **run-4** phases. The late
run-4 capture has 77.02% GC, 8.00% trie-subtraction and 10.14% staging sample weights. Its 12,582
samples are a separate CPU measurement; do not combine these shares with the older allocation weights.

Use primitive scratch buffers, allocate child builders after the first changed child, and reuse unchanged
subtrees. A subtraction memo must include node, stored state and inherited cover; cache a null result
explicitly. A node-only subtraction key is incorrect. Other operation-local memos use their complete
semantic inputs: a fixed leaf transform can use node identity, merge uses a node pair, and a filter must
include its filter identity. Do not add a global memo. Keep deep traversal iterative and
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
fraction to current code without measuring current forms. The b0 OWASP warm **run-1** capture supplies
descriptor bytes for that revision, not this distribution.

**N6, rule templates.** Freeze condition templates and bind methods/positions with small descriptors.
The b0 Conductor **run-2** heap retains 3,608,056 `ContainsMark` nodes and 3,639,113 condition atoms in the provider
graph. These are raw/resolved rule objects, not surviving compiled NNF forms. Preserve whole raw-rule
validation, original occurrence/serialized identity, relevant-rule policy and the complete
`allRelevant=true` mark universe. Measure equal-template frequency and exclusive bytes before implementation.
Static matching can also use an owner-local memo if profiling shows repeated work. Its complete key must
include the template, method binding, resolution policy and normal/relevant lookup mode. Keep raw-rule
validation separate from a condition that resolves to false. Validation caching remains optional N12;
template sharing does not require that new utility.

**N7, fact groups.** Use lazy empty/singleton containers and compact reach representation only where a
current census supports it. The b0 Conductor 14.6 MB dense arrays make an array-only rewrite a low priority.
Preserve exact annotated group keys and all exclusion/must growth. Do not absorb normal exact groups
under must metadata or suppress a full delta required after a metadata change.

**N8, actions.** Share frozen cleaner/action/path payloads within bounded owners. Keep configured order,
duplicate occurrence identity, per-action filters and the cleaner normalization from
`saloed/cleaner-action-normalisation`. Do not deduplicate cleaner steps based only on equal syntax.
Action reduction is a separate conditional follow-up: measure repeated executed steps, then prove equal
facts, requests, read/drop footprint, filters and source-override behavior for the particular action kind.
Keep the full ordered chain until those effects are established. No reduction is selected here.

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
contract. Its retained-byte impact is unknown. Older b0 guard snapshots counted zero tracked wrappers;
fresh original-manager queue cardinality is unrequested. Neither establishes wrapper growth during
ordinary execution. Preserve all existing managed-cache clients and the
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

1. **Primitive queue comparison.** The b0 ThingsBoard run-1 recording attributes about 243.58 GB,
   or 35.81%, of sampled allocation weight. Remove boxing while preserving the comparator order.
   This has little direct retained-memory effect and can be separate from N3.
2. **Trie builders and operation-local reuse.** Follow N4's semantic keys and controls. Measure bytes per
   operation and inclusive CPU separately from retained AP graphs.
3. **Alias membership and direct traversal.** Measure current repeated scans before adding an index.
   Indexes can increase retained memory; preserve exact type and alias matching.
4. **Analyzer-owned graph reuse.** Reuse a required adapter view only where lifetimes permit it. Measure
   construction and retained costs together. Keep IR providers and IR objects unchanged.

## Adoption and verification

N1 and N2 have a [detailed design and staged implementation sequence](bidirectional-n1-n3-implementation-proposal.md).
Their representation, algorithm and protocol gates remain open; no runtime change is made by these documents.
N3 is deferred to its separate future proposal; other N candidates remain unselected.
The requested rollback, core/project retests and fresh heap-owner
reviews are complete. Select further changes by these owner measurements. Do not implement a manager or
weak-cache utility as an incidental prerequisite.

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
