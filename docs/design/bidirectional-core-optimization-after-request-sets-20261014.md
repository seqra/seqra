# Analyzer core after request sets: next optimization proposal

Date: 14 October 2026. **Proposal for review. No change is selected for implementation.**
All four primary guard heaps and the run-1 component/root-path results have been reviewed.
The independent component audit passes. The ranking below uses measured target sizes and explicit layout
models. Previous proposals are historical. This proposal uses the fresh fixed-project batch for
implementation `a23ad7fcd99553190c82557cff6076503959aec6`. Later commits change documentation only.

No new optimization is implemented by this proposal. The current report is
[the fixed-project performance report](../reports/bidirectional-request-set-barriers-performance-20261014.md).
Evidence root:

```text
/drive-testcomp/opentaint-go-rules/bidi-o1-o3-20261012/request-set-barriers/20261014T105250Z
```

## Latest progress and measured problem

The primary target is to complete **run-1, forward, limit 1, within 60 seconds of engine time**
on each project. The user confirmed that prescan and the first report barrier are separate.
The proposal must first address run-1 workload and the memory needed to finish it. Later-run
optimizations remain lower priority. The prepared Conductor run-2 component analysis is deferred;
its completed primary heap still gives useful context.

| Optimized project | Run-1 engine status | Engine seconds | Target met |
| --- | --- | ---: | --- |
| OWASP | Complete | 336.977 | No |
| Conductor | Complete | 75.939 | No |
| ThingsBoard | OOM | 226.938, including guard capture | No |
| Stirling-PDF | Complete | 47.389 | Yes, in this measurement |

OWASP requires at least 82.19% less engine time, and Conductor at least 20.99%, from the current
completed measurements. ThingsBoard reaches its guard after 170.673 seconds and does not finish
run-1. Its complete-run cost is unknown. A storage change alone has no measured basis for the
required time reductions. The next proposal must include algorithms that avoid repeated work.

| Optimized project | Latest reached | Last complete engine and accepted report |
| --- | --- | --- |
| OWASP | run-3, forward, limit 3 | run-3, forward, limit 3 |
| Conductor | run-2, backward, limit 2; OOM | run-1, forward, limit 1 |
| ThingsBoard | run-1, forward, limit 1; OOM | None |
| Stirling-PDF | run-4, backward, limit 4; timeout | run-3, forward, limit 3 |

Run-1 starts at limit 1. Main has legacy phases and no bidi run-N. Control and optimized output
match at all four projects. The main comparison still has 16 absent strict sink identities. The
optimization has not improved completion. A next memory optimization must preserve the existing
strict comparison and must not hide the command-site loss.

Both isolated PDF diagnostics reach run-4/backward/limit 4 and time out after accepting
run-3/forward/limit 3. Their terminal outputs match the primary optimized output exactly. The first
shows local DEMAND root-mark removal and missing run-3 TAINT input. The second names the NORMAL
supplier at the one-argument executor's call 0: its fact matches, but its caller premise is
unsupported. It also identifies the controller's missing command source seed at the completed
run-2 hand-off. Protected support and hand-off coverage is complete. The report retains the
limits: no whole-chain NORMAL erasure or global first-loss proof, and conditional source
predicates remain enabled. These diagnostic times do not replace the primary measurements.

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
| Rule provider including base provider and Spring context | 1,324,319,384 | Base provider dominates; normal exit cache alone is 544.73 MB |
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
progress. Both runs fail before their first accepted report. Actor-local attribution is now
complete: 50,838 queued deltas and 15,568 pending publications
are present. The worklist retained set is 43,556,384 bytes, compared with much larger cumulative
state. Physical event queues alone do not represent all pending work. An earlier
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

The current grouped request join already scans each near TAINT subtree once and tests its leaf
marks against the standing request set. It does not repeat that subtree scan for every requested
mark. FLOW joins still enumerate eligible requested marks and attempt separate climbs before
the phase merges outgoing requests. In the failed ThingsBoard run-1 END census, 113,166,651 climb attempts
produce 62,610,235 outgoing mark members, with 48,463,981 unions into existing phase targets.
These counters do not measure a corresponding number of full TAINT propagations. Only 2,031
deferred mark answers are recorded, compared with 887,465 position answers. Source and profile
review must distinguish request routing from concrete-premise analysis before selecting a change.

Form building also appears in 47.53% of ThingsBoard run-1 Java samples and 34.83% of OWASP run-1
Java samples. These are inclusive sampled shares, not predicted wall-time savings. The run-1
proposal must consider form and transfer work as well as request traffic. Any grouping of
concrete premises must preserve them as separate alternatives: `PremiseSet` means conjunction
and cannot represent that grouping.

The rule-provider caches and N2 directories now have separate component measurements. A large owner union
does not prove that two collections are duplicates or that their values can be released.

## Memory ranking for run-1

This order compares expected retained-memory impact. Models are estimates at the captured
ThingsBoard run-1 state; they are not measured savings or equal-progress comparisons. The
60-second goal also needs the work-avoidance proposals below.

| Order | Proposal | Memory assessment | Main tradeoff |
| --- | --- | --- | --- |
| 1 | Reclaim resolved rule-cache payloads | 544.73 MB isolated target, less wrappers and active users | More resolver work after cleanup |
| 2 | Compact sparse N1 paths and bucket lanes | About 533.30 MB in the stated layout model | Cursor and promotion contracts |
| 3 | Compact N2 integer postings | Hundreds of MB within a 474.55 MB isolated index | Ordered membership storage remains |
| 4 | Allocate empty group containers only when used | 216.06 MB gross empty-shell target | Exact delta and layer contracts |
| 5 | Remove the unused observation index | 7.12 MB isolated index target | Preserve observation deduplication |

The N2 and empty-container estimates can overlap in final net impact; validate their concrete
layouts before choosing between them. Alias avoidance can reduce part of a 582.08 MB retained
set, but the fraction that is unnecessary is not measured. N1 admission cohorts also lack a
measured net estimate. These are separate work-avoidance or exploratory candidates below; their
whole owner unions are not ranked as removable memory.

## Candidate: reclaim resolved rule-cache payloads with the existing manager

**Current.** The base rule provider retains resolved lists for each method and rule family. It
has separate ordinary and `allRelevant` caches. In the run-1 guard, the ordinary method-exit sink
cache alone retains **544,729,744 bytes**. Its `allRelevant` counterpart is an empty 48-byte map.
The Spring context retains about 2 MB; most of the 1.324 GB provider selection belongs to its
base provider. This is raw resolved rule storage, not expanded NNF cubes or live form payloads.

**New.** Store resolved list payloads through the existing `SoftReferenceManager`. Keep full
method keys, rule-family ownership and separate mode keys. Pin a returned list while it is used.
Rebuild a cleared payload from the same stable rule definitions and resolver. If the manager
returns no reference after cleanup, return the built value without caching it.

**Why.** Memory pressure can release large resolved lists that have no active user. Method keys
and rule definitions remain. The change trades cache retention for resolver work. Use one cache
contract for source and sink families, with the measured exit-rule family as the first target.
This does not repeat the managed soft FormsCache optimization, which is already implemented.

**Memory and time.** The measured 544.73 MB is a target bound, not guaranteed savings. Reference
wrappers and active lists reduce the benefit. Rebuilds can increase the form-building cost that
already makes run-1 slow. This has high memory priority but needs a rebuild-cost test before it
is chosen for the 60-second target. Do not replace or rewrite SoftReferenceManager.

**Checks.** Force cleanup and disabled-cache paths during source and sink resolution. Compare
ordered rule definitions, signed conditions, mark IDs, action IDs and full witness/support output
before and after rebuild. Preserve ordinary versus `allRelevant` resolution and D23 source order.

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
These totals are measured bytes, not savings. The global path-node census has 2,419,536 leaves, 885,668 single-child
nodes and 317,646 wider nodes. Leaves already have no child map. Single-child map shells and
key/value/child-order arrays account for 375,523,232 bytes. Removing their list shells and empty
leaf lists, then allowing Node size to grow from 24 to 32 bytes, gives a **425.87 MB layout model**.

Specialized bucket lanes can remove 165.67 MB of singleton arrays. An extra eight bytes in each
of the 7,279,455 bucket lanes costs 58.24 MB, giving another **107.44 MB model**. Keep other
RequestIntLane consumers unchanged. The combined model is about **533.30 MB**; these are global
shallow-layout estimates, not measured retained-memory savings. Verify the new object layout and
all upgrade/cursor costs before selection.

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

**Memory assessment.** The isolated subscription index retains **474,552,952 bytes**. It includes
193.32 MB of linked entries, 70.14 MB of linked maps, 56.60 MB of hash backing arrays, 36.15 MB
of boxed integers and 20.04 MB of set shells. A primitive replacement still needs ordered IDs,
membership storage and node fields. A net reduction of hundreds of MB is plausible; exact net
cost needs a concrete layout and posting-cardinality census. The whole index is not removable.
The full N2 union remains 1.089 GB in ThingsBoard run-1 and 0.966 GB in Conductor run-2.

**Implementation boundary.** Change the subscription posting index, with an independent generic
reference for prefix/extension results and duplicate behavior. Test repeated handles at different
positions, empty and singleton upgrades, both run directions, late publications, reentrant
subscription additions, full caller context/layers and cancellation. Keep the two consumers'
admission progress independent. Reusing `AddedFactIndex` unchanged is not the proposed solution:
its eager maps and hash sets can outweigh savings at sparse nodes.

## Candidate: allocate cumulative group containers only when used

**Current.** Each ConclusionGroup creates both kind maps. Each FlowGroup creates lists for both
layers. The run-1 heap contains **149,049,552 bytes** of empty kind-map shells and **67,015,440
bytes** of empty layer-list shells. These belong to cumulative state, not a large pending queue.

**New.** Use an empty state until the first value of that kind or layer arrives. Promote only that
container, while preserving exact group keys, layer separation, encounter order and delta rules.
Keep the current synchronization and immutable snapshot rules.

**Why.** A group that never receives TAINT need not own a TAINT map; a NORMAL-only FLOW group
need not own a DEMAND list. The logical relations and algorithm stay the same.

**Memory and checks.** The measured empty shells provide a **216.06 MB gross target**. A layout
that keeps the same reference fields can avoid owner growth, but new synchronization or state
objects must be deducted. Verify empty-to-first-value upgrades, zero-only groups, both layers,
late publications, T2/T4 updates and release. This is a memory change with no measured basis for
an 82% reduction in run-1 engine time.

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

**Memory assessment.** The isolated observation index retains **7,121,616 bytes**, while the
incoming-link index retains 637,038,864 bytes. Thus the unused index is a small target. The
observation accumulator retains 39,763,512 bytes and remains necessary when its consumer is active. The 153,886 store instances
equal two stores for each of 76,943 actors, but equal instance counts do not establish equal
payload sizes.

**Implementation boundary.** Change `RunMethodCallServices.observed` and its release path. Do not
change `RunMethodAnalyzer.links`, subscriptions, publications or request joins. Compare callback
censuses for duplicate, disjoint and overlapping TAINT updates, both layers, different premises,
different caller contexts and cancellation. Verify that snapshots are not introduced as a new
copy cost. The existing accumulator must reproduce the store's fresh-delta decision exactly.

## Work avoidance for the 60-second run-1 target

These algorithm changes address repeated execution. Their retained-memory savings are not
measured, so their full owner sets must not be used as predicted savings. Investigate alias and
form work first, then cleaner passes and FLOW climb expansion. These paths overlap in profiles.
None of the individual proposals establishes the required 60-second completion time.

### Force alias analysis only when its results are needed

**Current.** Every new call plan reads `entry.aliasAnalysis` before its builder knows whether it
needs aliases. The builder also computes persisted alias edges before `Guard.MemoryEffect`
decides whether an output will use them. Alias work appears in 28.99% of ThingsBoard run-1 Java
samples and 13.07% of OWASP run-1 samples, within their larger form-building shares. The run-1
alias-analysis retained selection is 582,083,568 bytes.

**New.** Pass a supplier for the existing method-owned alias analysis through the rule evaluator
and call-resolution context. Force it at the same semantic queries as before. Keep an ordered
recipe for persisted alias edges and materialize it when an actual output passes MemoryEffect,
or when backward use needs its reversed form. Within that materialization, query each local
bind-back target once and attach all original source edges to its complete alias paths.

**Why.** Literal-only conditions and calls whose resolution needs no local aliases can avoid
initialization. Zero or normal identity results can avoid building unused alias stages. A source
can produce a memory effect from zero input, so the input kind alone cannot justify a skip.
Delaying an alias query that is needed later does not remove its cost.

**Boundary and checks.** Keep full context-sensitive resolution, alias-derived constant/type
checks, before/after alias intersections, overwritten aliases, repeated actual variables and
source-generated effects. Preserve stage identities and every admitted edge. Test forward and
backward use, forced form-cache cleanup and concurrent readers. Count actual alias queries and
methods never forced; the whole 582 MB set is an upper target, not a saving estimate. Preserve
NNF conditions for both source and sink rules.

### Execute plain selected-mark cleaner blocks as sets

**Current.** Each plain cleaner step builds a new result list and PlanFact wrappers. Selected-mark
cleaners repeatedly update the FLOW exclusion header or walk TAINT geometry. OWASP run-1 cleaner
chains appear in 22.15% of Java samples and carry 164.65 GB of inclusive sampled allocation.
Field-action statements account for 118.93 GB within that allocation; they are outside the first
plain-cleaner change and cannot be counted as saved by it.

**New.** Compile contiguous plain selected-mark Clean steps into a mark-set program. Keep Kill,
Field, all-mark positional cleaners and each call-stage boundary as barriers. For FLOW base B,
produce one header with `markExclusion union S` and one bulk request set S on the original premise.
For TAINT, fuse only contiguous equal base/path/reach runs in the original step order, using one
spatial walk with the selected mark set. Do not collect equal-geometry steps across a different
geometry: the intervening cleaner can change a mark's layer or exclusion before the later step.
Reuse the input for off-base plain steps. Keep its identityCandidate.

**Why.** The FLOW branch shares the root and updates the header once. The TAINT branch can remove
several independent marks in one spatial walk. Both avoid per-step collector/list costs.

**Boundary and checks.** Every same-base selected-mark FLOW cleaner must still request its mark
in run-1, including an already excluded mark or a disjoint cleaner path: F75 remains exact. Keep
NORMAL exact and ANY_TAINT/E rows, DEMAND partial outputs and all unselected marks. Prove duplicate
idempotence and disjoint-mark independence against the scalar cleaner sequence, then compare
complete witnesses, support and native records. Do not combine Field read/clean/write blocks:
their intermediate positive-limit cuts can change other marks even when the selected mark is absent.

### Pass eligible FLOW climb sets through the join

**Current.** The grouped join shares geometry, then expands each eligible FLOW mark into a callback.
The phase constructs and hashes the same target for each callback and inserts each ID into a
builder. The incomplete ThingsBoard run-1 census records 113,166,651 climb attempts and 48,463,981
unions into existing target sets. The counts do not form a completed conservation equation.

**New.** After the current FLOW geometry check, emit one cohort:
`(full caller MethodKey, original caller InitialAp, requested marks minus link markExclusion)`.
Resolve the target once. Resolve the receiver origin and standing row once per admitted batch,
then commit its exact fresh members; avoid repeating the scalar entry lookup for each mark.
Share an immutable input set when exclusions are empty. Install the
first cohort directly; skip the same immutable cohort if it repeats at that target. Merge distinct
cohorts with bounded signed-ID cursors. For old standing requests, make one bounded immutable
snapshot for the row and captured horizon, reused only within that phase.

**Why.** This avoids per-mark callbacks, target allocation/hash probes and builder inserts. It can
also avoid a redundant outgoing radix sort. Arbitrary union and filtering still require work;
the 62,610,235 outgoing logical members do not disappear.

**Boundary and checks.** Keep full raw caller/group metadata for geometry and exclusion checks
before projecting the final target. Keep independent original premises, contexts and call sites,
old horizons, next-frontier admissions, late links and Work completion. Keep TAINT answers and
position requests on their existing paths. Do not filter F75 request admission using cleaner
geometry or exclusions. Compare scalar expansion, closure, support and report under request-first,
link-first, reentrant growth and cancellation. Use phase-local snapshots, with no new run-long
sent-mark relation.

### Reduce repeated summary matching and graft work

**Current.** Summary publication already waits for the method's fact worklist to become empty.
`flushPublications` sends one batch. `PublicationIndex.addAll` merges cumulative storage, but
returns each fresh publication separately; delivery repeats candidate matching and return work.
A TAINT summary also enumerates each distinct mark/tail kind and grafts across its whole root for
that kind. Thus delivery batching is present, while some inner work remains per delta or mark.

**New.** First evaluate sharing a candidate/matching snapshot for an exact full premise/member
cohort while retaining narrow conclusion deltas. A stronger option coalesces only the already
admitted fresh outputs of PublicationIndex by full premise and canonical GroupKey, then notifies
callers. Keep original serial admissions, cumulative late replay and native/raw persistence.
For grafts, use existing cached subtree mark summaries to skip branches that cannot contain the
selected mark/tail. A missing cached summary means unknown, not empty. A separate single-kind
zero-premise/REACH shortcut can reuse the summary root with the same output header and layer.

**Why.** Shared matching can avoid repeated subscription scans and caller resumes. Pruning avoids
whole-tree work for marks that occur only in a few branches. The single-kind zero case can avoid
reconstructing a root that already has the required shape.

**Boundary and checks.** Batch density and mark-kind distributions are not measured. A wider
multi-mark union can increase the current graft cost, so fewer publications alone are not proof
of a gain. Preserve full premise alternatives, layers, E/mx headers and T2/T4 admissions. A mixed
exact/ANY_TAINT zero-summary union can change callback/support grouping even when its leaf union
matches; do not apply a general whole-root shortcut without that proof. Verify cancellation,
late subscriptions, reentrant delivery, original RETURNED actions and positive-limit cuts.

### Prepare rule data once within a form build

**Current.** Some call builders validate a rule before a rewriter validates it again. Source and
returned-source paths also normalize overlapping candidate conditions. Existing NNF support
avoids cubes, but repeated preparation still contributes to form work.

**New.** Use a build-local prepared rule with its full validation result, signed static condition
and separately projected positive condition. Reuse only the parts that have the same candidate
and binding scope. A later managed physical-call template can share context-independent positions
and recipes while keeping context-specific occurrence and temporary keys.

**Why.** This avoids duplicate validation and normalization during builds and rebuilds. It has
no measured basis for removing the whole form-building share. Keep signed complement folding
before negative approximation, all source/sink actions and branches, D23 ordering, full temporary
keys and resolver context. Do not add a weak identity cache.

## Verification and implementation order

The memory table is a separate ranking from the run-1 work priority. Start with the algorithmic
alias/form and plain-cleaner designs, plus the set-valued FLOW climb. Use sparse N1 storage to
reduce the standing memory needed for run-1 completion. The raw-rule cache has a larger measured
memory target, but its rebuild cost can work against 60-second completion.

For each selected change, first compare an independent scalar relation and full engine closure,
source hits, witnesses, support, summaries and native records. Then rerun the same fixed OWASP,
Conductor, ThingsBoard and Stirling-PDF configurations with the committed runtime. Measure engine
wall time, completion, GC, allocation and retained memory separately. Complete run-1 within 60
seconds is the acceptance target; a timeout, OOM or reduced rule coverage is a failure. Record
latest reached, complete and accepted run-N in every report.

Add only aggregate optional counters for alias forces, admitted alias stages, cleaner steps versus
spatial walks, logical climb members versus callback invocations, summary cohort sizes and graft
node visits. Do not retain per-entity histories. Keep the physical scan options and positive limit1.
No implementation is selected by this document.

## Future storage candidates

### Preserve batched N1 admissions without redundant per-mark directories

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

The original SoftReferenceManager stays unchanged. A manager redesign and WeakIdentityCache
remain possible future optimizations. They need a separate design for ownership, identity,
rebuild behavior and concurrent use. No such runtime change is part of this iteration.

N2 and actor added-fact accumulators retain different consumer progress. Similar entry counts
do not prove that their values or headers can share one admission state. Keep them independent.

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

The table ranks measured memory targets and explicit models. Verify net impact when a concrete
implementation is selected. Prefer avoidance of unnecessary work where the expected
impact is comparable and the exact relation is preserved. Broad N3 queue/fairness changes stay
deferred: the fresh optimized run-1 physical queues are small. Profile allocation totals are
sampled weights; they are not retained memory or equal-work savings.
