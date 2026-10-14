# Analyzer core after request sets: next optimization proposal

Date: 14 October 2026. **Draft. No change in this document is selected for implementation.**
All four primary guard heaps have been reviewed. Component and root-path queries must be reviewed
before the memory ranking is final. Previous proposals are historical. This proposal uses the fresh fixed-project batch for
implementation `a23ad7fcd99553190c82557cff6076503959aec6`. Later commits change documentation only.

The current report is
[the fixed-project performance report](../reports/bidirectional-request-set-barriers-performance-20261014.md).
Evidence root:

```text
/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/request-set-barriers/20261014T105250Z
```

## Latest progress and measured problem

| Optimized project | Latest reached | Last complete engine and accepted report |
| --- | --- | --- |
| OWASP | run-3, forward, limit 3 | run-3, forward, limit 3 |
| Conductor | run-2, backward, limit 2; OOM | run-1, forward, limit 1 |
| ThingsBoard | run-1, forward, limit 1; OOM | None |
| Stirling-PDF | run-4, backward, limit 4; timeout | run-3, forward, limit 3 |

Run-1 starts at limit 1. Main has legacy phases and no bidi run-N. Control and optimized output
match at all four projects. The main comparison still has 16 absent strict sink identities. The
optimization has not improved completion. A next memory optimization must preserve the existing
strict comparison and must not hide the unresolved command-site loss.

The isolated PDF diagnostic reaches run-4/backward/limit 4 and times out after accepting
run-3/forward/limit 3. Its terminal output matches the primary optimized output exactly. Complete
report records place the target command-constructor DEMAND keys in run-1 and their removal by
run-3. Two observed DEMAND cleaner pairs prove local root-mark removal, but the earliest loss of
the main NORMAL flow remains unresolved. Ordinary run-3 event caps limit further attribution.

The completed optimized ThingsBoard guard analysis is from **run-1, forward, limit 1**, before
cancellation. It has 310,377,094 reachable objects and 11,392,920,992 shallow bytes. Separate
retained-set queries find these owners:

| Owner selection | Retained-set bytes | Meaning |
| --- | ---: | --- |
| Actors, runners and subscription managers | 5,688,245,352 | Combined owner union |
| Standing N1 request stores | 2,267,943,216 | Exact requests retained for later joins |
| N2 subscription and publication state | 1,088,543,928 | Cumulative interprocedural state |
| Raw actor added-fact stores | 988,439,488 | Incoming links and unresolved observations together |
| Raw added-fact geometry indexes | 644,160,480 | Part of the preceding store union |
| Rule provider including base provider and Spring context | 1,324,319,384 | Component attribution still needed |
| Forms, contexts and caches together | 683,958,648 | Not an isolated form-cache payload total |
| Request frontiers and active captured views | 80,281,160 | Active request processing state |
| Temporary batching state | 45,184,824 | Includes builders and snapshot cursors |
| Physical queues and channels | 2,995,000 | 562 event queues hold 2,564 entries |

These rows overlap. Do not add them or use their total as predicted savings. IR storage has a
1,621,962,240-byte retained set. IR storage and implementation remain outside this proposal.
The completed ThingsBoard control guard, also from run-1/forward/limit 1, has 11,344,297,928
reachable shallow bytes. Its queue/channel union is 1,273,255,840 bytes with 13,786,571 physical
queue entries. Its N1/N2/raw-store unions are 2,517,478,256 / 664,371,064 / 474,984,976 bytes.
The optimized guard has smaller queue state but larger N2 and raw-store selections at different
progress. Both runs fail before their first accepted report. Actor-local delta worklists still
need separate attribution; physical event queues do not represent all pending work. An earlier
OOM is not evidence of equal-work memory savings.

The completed optimized Conductor heap is from **run-2, backward, limit 2**, with run-1/forward/
limit 1 as its last complete engine and accepted report. It has 7,641,192,032 reachable shallow
bytes. Its N2 and raw-store unions retain 965,935,880 and 559,276,760 bytes. The complete class
census proves zero live N1 stores and request frontiers in this heap. Thus N1-only work does not
directly address this failure. Its 2,264,869,592-byte IR-storage selection remains outside scope.

The Conductor control run-2/backward/limit 2 heap has 7,654,410,576 reachable shallow bytes, with
884,650,200-byte N2 and 760,205,352-byte raw-store unions. Its N1 and request-frontier populations
are also zero. Both primary run-2 owner patterns therefore support examining N2 and actor state,
while their unequal progress prevents a net memory-saving claim. The audited four-heap comparison
is in `heap-review/primary-comparison-1`.

## Algorithm changes or better storage

The run-1 snapshot does not support describing all large collections as unprocessed work. Most
of the measured request state is the standing relation needed by future links. Its full origins
and members have semantic uses. Removing answered rows or merging different contexts needs a
coverage proof that the current measurements do not provide.

There is a source-proven opportunity to avoid an index that a particular consumer never queries.
This is an algorithm change with a small interface scope. For N1, compact storage is the direct
option when the same standing relation must remain. Admission cohorts could also avoid redundant
per-mark directory columns, but their lookup cost needs evaluation. These choices do not require
changing the fixed-point algorithm or the meaning of a request.

The rule-provider caches and N2 directories need their own component review. A large owner union
does not prove that two collections are duplicates or that their values can be released.

## Candidate: avoid the unresolved-observation positional index

**Current.** Each run-1 method actor has an incoming-link `AddedFactStore`. Its call services also
have an `observed` `AddedFactStore`, used to report unresolved-call TAINT observations once for
each exact group and fresh delta. The observation path calls `add`; it never queries geometry.
Both stores nevertheless build `AddedFactIndex` nodes and postings.

**New.** Use the existing index-free `AddedFactAccumulator` for the observation path. Keep the
incoming-link store and its index. Preserve the exact full `CallerRef`, added-fact group key,
caller layer, fact layer, cumulative union and fresh-delta test. Keep the callback at the same
point in the call plan. Create observation storage only when its consumer exists.

**Why.** The observation path would stop building and retaining an unused positional index. It
would also stop traversing fact geometry to update that index. This is avoidance of unnecessary
work and state; the deduplication relation remains complete.

**Memory assessment.** The 644,160,480-byte index union includes both roles. Only the observation
component is a possible saving. Its isolated retained set is pending. The 153,886 store instances
equal two stores for each of 76,943 actors, but equal instance counts do not establish equal
payload sizes.

**Implementation boundary.** Change `RunMethodCallServices.observed` and its release path. Do not
change `RunMethodAnalyzer.links`, subscriptions, publications or request joins. Compare callback
censuses for duplicate, disjoint and overlapping TAINT updates, both layers, different premises,
different caller contexts and cancellation. Verify that snapshots are not introduced as a new
copy cost. The existing accumulator must reproduce the store's fresh-delta decision exactly.

## Candidate: compact sparse N1 paths and buckets

**Current.** N1 stores full standing memberships. `RequestPathIndex.Node` creates an `ArrayList`
for child order, and allocates a fastutil map when it gets any child. Each populated
`RequestBucket` creates three `RequestIntLane` objects. A nonempty lane allocates at least two
integer cells. `RequestMembers` already stores a singleton inline; that is not a new optimization.

**New.** Add empty and singleton storage forms for path children and bucket lanes. Allocate a map,
ordered child collection or wider lane only when it is needed. Keep append order, full origin
identity, exact membership and the old query horizon. A paused cursor must retain a valid captured
view when a singleton upgrades to a larger structure.

**Why.** Many sparse paths can avoid a hash table, collection object and small backing arrays.
This improves representation while preserving the required standing relation and its lookup
algorithm. It also avoids constructing unused lane payloads.

**Memory assessment.** The N1 retained set contains 956,987,168 bytes of `int[]` storage. Pattern,
path-fact and path-list wrappers account for another 186,490,800 bytes in that selected union.
These totals are measured bytes, not savings. The empty/singleton/many distributions and added
object-field costs must determine the net estimate before selection. A larger inline owner can
offset some removed-array savings.

**Implementation boundary.** Keep the three logical lanes separate: row encounter order, member
encounter order and position memberships. Keep their current cursor and membership contracts.
Test upgrades while a query is paused, mixed position/mark order, negative mark IDs, duplicate
admissions, raw path overlap, cancellation and a late added link. Do not merge different original
`InitialAp` objects by projected geometry.

## Candidate: compact N2 subscription postings

**Current.** `CalleeSubscriptions` stores exact cumulative groups in `AddedFactAccumulator` and
indexes directory handles with `PathTrie<Int>`. Every trie node creates a `LinkedHashSet`,
including nodes with no postings. Generic integer postings use boxed values and linked hash-map
entries. A handle at two positions must remain two postings.

**New.** Give this consumer a specialized integer-posting index with empty, singleton and wider
ordered storage. Preserve node-local insertion order, full base/path geometry, duplicate tests,
prefix/extension lookup and cancellation. Keep the exact accumulator and each caller's fresh
delta progress. Capture all candidate subscriptions before callbacks, as the current code does.
Other generic `PathTrie` consumers keep their existing contracts.

**Why.** The same standing relation can use fewer collection objects and boxes. This is a
representation change. It does not remove required subscriptions or assume that actor links and
caller subscriptions have one shared admission state. It applies in both run-1 and later runs,
including Conductor's observed run-2 failure.

**Memory assessment.** In the optimized ThingsBoard N2 selection, linked hash-map entries account
for 220,489,440 bytes, linked hash maps for 99,701,000 bytes and boxed integers for 36,147,888
bytes. These classes also serve other N2 structures. The index field's retained set must isolate
the target before estimating savings. The whole N2 union is 1.089 GB in ThingsBoard run-1 and
0.966 GB in Conductor run-2; those are not wholly removable bytes.

**Implementation boundary.** Change the subscription posting index, with an independent generic
reference for prefix/extension results and duplicate behavior. Test repeated handles at different
positions, empty and singleton upgrades, both run directions, late publications, reentrant
subscription additions, full caller context/layers and cancellation. Keep the two consumers'
admission progress independent. Reusing `AddedFactIndex` unchanged is not the proposed solution:
its eager maps and hash sets can outweigh savings at sparse nodes.

## Candidate: preserve batched N1 admissions without redundant per-mark directories

**Current.** R1 merges transport requests, but N1 expands each fresh mark into standing member
columns: row handle, member key, bucket encounter ordinal and row key-to-ordinal entries. A sorted
batch can therefore create many primitive cells even though its full origin is the same.

**New.** Evaluate immutable admission cohorts. A cohort has its full row/origin, sorted fresh mark
IDs, a global start ordinal and a committed length. Its member ordinal is `start + rank` within
that cohort. Scalar admissions remain length-one cohorts. An ordered per-bucket directory includes
cohorts and position admissions, so a query can recover the exact mixed encounter order and its
captured prefix. Keep the exact mark membership test separately.

**Why.** The store can avoid repeated row handles and explicit ordinal columns for every mark.
The logical standing memberships remain present. This changes how admissions and queries handle
groups; it does not prove that the analyzer can discard logical requests.

**Memory and cost assessment.** This targets part of the measured N1 integer storage. Cohort count
and width in the standing store are not measured yet. Accepted outgoing batch widths are a
different population. A simple cohort list would replace fast mark lookup with repeated binary
searches. Keeping the old key-to-ordinal table removes part of the proposed memory benefit. The
design needs a measured lookup/retention tradeoff before selection; a transport batching result
alone is insufficient.

**Implementation boundary.** Preserve query-wide horizons and raw encounter order, including
reentrant admissions. Deduplication must use the original full origin and mark ID. No bitmap may
be sized by an assumed small global mark universe. Compare the complete standing relation and
callback sequence with an independent scalar store over mixed admissions and late joins.

## Candidates that need more attribution

Resolved rule storage keeps separate ordinary and `allRelevant` method caches for each family.
Both modes are still used by bidi forms; the ordinary cache cannot simply be cleared after
prescan. They resolve conditions differently. The provider union contains millions of exit rules
and mark conditions, but it also owns the base provider and Spring context. Component queries
must identify the large cache before proposing shared rule templates, lazy resolution or managed
soft payloads. Any such proposal must cover source and sink rules and preserve each mode's exact
resolved order. Existing managed soft `FormsCache` support is already implemented.

N2 and all raw added-fact stores together have nearly matching accumulator-entry counts. The raw
selection includes incoming links and observations; observation entries are not yet isolated.
This does not prove that their headers, values or full group keys are interchangeable. Component and strong-root
queries must establish duplication before a shared directory can be proposed.

The original `SoftReferenceManager` stays unchanged. A manager redesign and `WeakIdentityCache`
remain possible future optimizations. They need a separate design for ownership, identity,
rebuild behavior and concurrent use, plus fresh wrapper/cache attribution. No such runtime change
is part of the current iteration or this draft's implementation scope.

## Lower memory impact: request snapshot work

The deeper optimized ThingsBoard run-1 profile has 753 Java self samples in
`RequestMarkSnapshotCursor.step`, or 3.59% of that engine's Java execution samples. The accepted
outgoing batch census contains 446,327 width-2-or-3 batches. That census is not the snapshot-input
population. The current multi-member cursor performs fixed radix passes and clears 256-cell
buffers. A bounded small-set sorting path could avoid those passes for small inputs.

This is a CPU and continuation-count candidate. Temporary batching retains about 45 MB in the
guard snapshot, so this does not explain the large standing memory owners. Preserve signed mark
order, deduplication, immutable results and bounded cancellation checks if it is selected later.

## Common requirements and final selection

Keep source and sink predicates in NNF. Do not expand cubes. `P AND NOT P -> false` remains valid.
Apply the exclusion spec table, selected-mark F75 demand and the D23 `allRelevant` source-override
order. Preserve full method/context keys, premises, raw caller links, layers, witnesses and
support. Do not change strict sink locations or return matching to improve comparison counts.

The final proposal must rank candidates by expected net retained-memory impact after the
remaining heap and component reviews. Prefer avoidance of unnecessary work where the expected
impact is comparable and the exact relation is preserved. Broad N3 queue/fairness changes stay
deferred: the fresh optimized run-1 physical queues are small. Profile allocation totals are
sampled weights; they are not retained memory or equal-work savings.
