# Next core optimization proposal

Date: 15 October 2026. Status: proposal only; current heap and recording reviews
are complete. Expected impact is ranked below; net savings and eligible skip rates
remain unmeasured. No change below is implemented by the added-fact-removal patch.

The baseline for this proposal is runtime `27e22bb1ed871651143538783473cb0d4fcbcf3e`.
Use the [current project report](../reports/bidirectional-added-facts-removal-performance-20261015.md).
The older proposal for `85a7c0e` is historical. Its retained-byte estimates and ranking
do not establish the current opportunities. The first time target remains a completed
run-1 engine within 60 seconds. Prescan, metadata and reporting remain separate costs.

| Project | Latest reached | Last completed engine | Accepted forward report | Run-1 engine seconds |
| --- | --- | --- | --- | ---: |
| OWASP | run-5, forward; TIMEOUT | run-4, backward | run-3 | 331.657 |
| Conductor | run-2, backward; OOM | run-1, forward | run-1 | 60.210 |
| ThingsBoard | run-1, forward; OOM | None | None | 279.104; incomplete |
| Stirling-PDF | run-4, backward; OOM | run-3, forward | run-3 | 36.102 |

## What needs to change

The new emitter removes callee input history, but caller N2 and method conclusions
remain large. Conductor's run-2 heap has a 1,113,073,056-byte N2 retained union within
the 2,797,840,824-byte actor/runner/subscription union. Those unions overlap. It has
1,265,465 N2 input groups and 10,576,358 leaf-position postings. Its transport queues
contain three events and its actor worklists contain 5,315 pending deltas. This is
evidence of large standing relations, rather than millions of queued messages.

ThingsBoard's incomplete run-1 has a different work problem: 1.607 billion FLOW
climb callbacks, 1.567 billion outgoing mark memberships and 1.470 billion duplicate
admitted memberships. These counters have different scopes. They do not prove that
all three populations are the same work. Its active questions peak at 555,409 keys
and 112,528,596 mark cells; pending answers peak at 282,135 shapes and 908,638 marks.
The packed pending memo is not the same population as the active interest relation.

TAINT conclusion leaves already pack marks; a concrete InitialAp still names one
mark. Required group multiplicity also includes caller premise, call site, context,
layer and E/mx. In Conductor's backward run-2, 1,169,544 of 1,265,465 N2 groups have
a single nonzero concrete premise. ThingsBoard's run-1 instead has 2,341,509–2,512,261
such groups and 3,018,878–3,189,630 FLOW/STAR groups, with observer-adjusted bounds.
Stirling-PDF's backward run-4 has 1,094,328 nonzero concrete-premise groups out of
1,112,249 total. These cohorts do not measure how many full keys merge when only the
initial mark is removed. A mark-packing redesign across initial premises needs that exact quotient
and a proof that full caller/ND correlation survives. It is not added as an assumed
high-impact fix.

OWASP's 30-second normal window emphasizes form construction, with call-plan builds
in 65.73% of samples. The complete run-1 recording has builds in 31.32% of Java CPU
samples and cleaner chains in 25.70%; cleaner chains account for 56.44% of sampled
allocation. These inclusive groups overlap and use different sample denominators.
Current counters do not distinguish cold builds from rebuilds after soft-reference
collection. P10 addresses the newly measured cleaner-application work.

A request-interest gate and a delta join can avoid repeated work. They do not remove
the required standing input relation. Index simplification can avoid an auxiliary
collection while retaining exact inputs. A compact index can reduce representation
cost where selective paths are useful. Both algorithm and representation need
consideration; a small queue alone does not justify a queue redesign.

## Expected impact and selection order

Persistent-memory order and run-1 CPU order are separate. The current owner sizes
are scope ceilings, not predicted savings. Options overlap and are not additive.
All three owner reviews and all eight primary recording reviews are complete.

| Memory priority | Proposal | Change and expected effect | Main decision gate |
| --- | --- | --- | --- |
| 1; algorithmic option | P1: reduce N2 postings | Avoid an auxiliary relation; Conductor's complete index retains 703.6 MB | Candidate selectivity and scan/snapshot cost; preserve selective static paths where needed |
| 1; conservative alternative | P2: primitive N2 postings | Keep selectivity and order, replace boxed linked-set handles | Bucket widths and complete replacement capacity; not additive with P1 |
| 2; measured shell target | P4: lazy conclusion containers | Unused maps and REACH arrays have a 636.9 MB gross target in PDF and 347.5 MB in ThingsBoard | Preserve group layout and REACH behavior; measure net owners |
| 3; new run-1 memory target | P9: share cumulative question marks | Avoid the second cumulative immutable set at publication; ThingsBoard's shared arrays occupy 441.9 MB | Dirty keys, old readers, fallback and token overhead |
| Conditional; could rank first | P3: retire discarded bound sink caches | Release discarded rules; all normal sink-cache roots retain 1.092 GB in ThingsBoard, including selected rules | Discarded fraction and sole cache ownership; previously selected separate scope |
| CPU and temporary-state options | P5: active-interest gate; P6: two-sided delta join | Avoid repeated request enumeration, transport and old-input joins | Actual eligible duplicate work; standing relations remain |
| CPU and allocation options | P10: skip proven identity cleaner work; P7: one validation/normalization per binding operation | Avoid intermediate cleaner stages, or repeated bound-condition walks | Actual identity eligibility and binding contract; inclusive profile scopes overlap |
| Possible later cache work | P8: managed cache lifecycle and reference registry | Trade retained cache for rebuilding, or reduce registry overhead | Cold/rebuild classification, lifetime and retained owner evidence |

For run-1 time, investigate P5 first on ThingsBoard. The full OWASP run-1 recording
adds P10 ahead of P7 as a cleaner-work investigation: cleaner chains account for
56.44% of sampled allocation, including lowered field statements. This is broader
than the 30-second normal window. P7 remains a bounded build-path opportunity.
P6 is a separate join improvement. Conductor and PDF's normal captures mostly
cover later runs, so use the full recording windows for their run-1 assessment.
P1 requires a CPU tradeoff check before selecting it over P2. No measured speedup
or exact net saving is claimed.

## P1. Avoid unnecessary leaf-position postings

Current: `CalleeSubscriptions` stores exact inputs in `AddedFactAccumulator` and
adds the stable input handle to `PathTrie<Int>` at each new leaf position. Queries
collect prefix/extension candidates, then the full AP kernel checks them. A handle
is posted at the leaf itself; it is not copied to every ancestor by construction.

New: for an eligible base, retain one handle membership per exact input group and
scan that base bucket. Keep the same full matching operation for every candidate.
Summary matching still uses `ops.satisfying`; requests still use the complete
`RequestGroupCursor` query. A possible hybrid keeps positional indexing for STATIC
and uses coarse buckets for selected nonstatic bases. Every deep query on a coarse
base still scans that base and applies its full pattern; it is never omitted.

Why: this avoids a large auxiliary relation rather than discarding input facts.
Replacing all positional postings with one membership per exact group gives these
logical counts at the captured frontiers:

| Capture | Current postings | Exact groups | Memberships avoided |
| --- | ---: | ---: | ---: |
| Conductor, run-2 | 10576358 | 1265465 | 9310893 |
| ThingsBoard, run-1 | 9235116 | 5803728 | 3431388 |
| Stirling-PDF, run-4 | 2445867 | 1112249 | 1333618 |

This arithmetic does not predict bytes or CPU savings. A hybrid retains more
memberships. The smaller reduction per group in ThingsBoard also limits the run-1
opportunity relative to Conductor. Selective queries could become much more
expensive, particularly when one static base contains many unrelated fields.

Keep full callee and caller MethodKeys, entry/context, CallerRef, original premise,
call site, caller/link layer and admitted GroupKey, including E and mx. Zero-only
entries remain separate. Full ND premises must hold for the same caller before a
summary applies. The index only chooses candidates; a broader bucket cannot itself
authorize an answer or summary.

Before implementation, measure per-base fan-in, leaf counts per handle, query path
depth and candidate-to-match selectivity in run-1 and restricted runs separately.
Define the candidate-superset law against a full scan. Preserve stable current-value
handles and complete input snapshots before mutating summary callbacks. Request
visits remain finite, owner-local and nonreentrant, with all effects queued.

Base-bucket enumeration can change encounter order. That is an explicit scheduling
change requiring closure and observer-contract review, not automatic representation
parity. Test disjoint deep paths, static/class paths, E/mx, both layers, multiple
callers and entries, zero/ND cases, new geometry on old handles, both arrival orders,
cycles, cancellation and queued callbacks. Compare full matched facts, summaries,
source hits/seeds, records and witnesses under the same candidate policy. Record
partial/OOM report differences separately from completed closure equivalence.

## P2. Store existing N2 postings as primitive ordered handles

Current: each `PathTrie<Int>` leaf uses `LinkedHashSet<Int>`. Exact index-only unions
retain 703,625,352 bytes in Conductor, 849,281,176 in ThingsBoard and 521,086,640 in
Stirling-PDF. Conductor's index has 423,054,320 shallow bytes in linked hash entries
and 149,041,168 bytes in boxed integers. Those bytes are not a measured net saving.

New: replace only the N2 handle index with primitive integer postings. Keep existing
path topology, child traversal, per-bucket first-insertion order, per-position
uniqueness and candidate deduplication. Keep the generic PathTrie for other users.
Use an existing primitive ordered set or a separately designed primitive membership
and encounter-order representation. Fresh PDF has 1,858,329 trie nodes for 2,445,867
postings, so per-node overhead also matters. A minimal variant stores zero or one
handle directly in the node and promotes on the second distinct handle to a primitive
insertion-ordered set. Negative sentinels are separate from nonnegative stable handles.
Keep the node at its measured 24-byte layout if feasible. Inline buckets of larger
width are separate optional work.

Why: it keeps selective scans while avoiding one object and linked entry per boxed
posting. Required integer arrays, headers, hash capacity and order storage reduce
the gross opportunity. It keeps the same number of logical groups and postings.

Use the sole-index union and measure bucket capacities. Test empty/singleton and
wide buckets, empty-to-single and single-to-many transitions, duplicate additions,
handle growth, prefix/extension ordering,
candidate snapshots and full same-caller ND matching. Compare exact candidate
encounter sequences as well as final matched closure. Measure complete owned bytes,
slack, allocations and query time. P1 and P2 are alternatives for a given bucket;
their gross opportunities must not be added.

## P3. Stop retaining discarded bound sink rules

This was selected earlier and remains a separate implementation slice. It was not
included in the added-fact-removal patch.

Current: normal method call/entry/exit resolution caches full bound sink lists before
the current rule selection discards some entries. The full cache can keep conditions,
actions and binding metadata alive after prescan.

New: retain only the selection's dependency-closed normal sink view and retire
obsolete unfiltered normal cache roots after all prescan work joins. Keep full cold
resolution and validation. Do not push selection ahead of validation or change the
separate `allRelevant` source-rewriter view.

Why: discarded rule graphs have no later normal consumer and can be released. A
fresh query of normal call/entry/exit sink `methodItems` roots measures retained
unions of 320,730,448 bytes in Conductor run-2, 1,092,020,512 in ThingsBoard run-1,
and 151,316,032 in Stirling-PDF run-4. It excludes `allRelevant` but includes both
selected and discarded rules. These totals establish current scope, not the
discarded fraction or net savings. The broader Spring-provider unions include
other state and must not be added. The old runtime estimate is no longer used.

Preserve full RuleId and occurrence/action ordinals, deterministic warnings/errors,
null/default-ID behavior and selection dependencies. Define the joined-prescan
lifecycle transition and ensure no borrower can recreate a retired unfiltered root.
Test selected/unselected rule mixtures, cold and warm resolution, source-rewriter
dependencies, entry/call/exit consumers, failures and cancellation. Verify sole cache
ownership and selected/discarded counts in each current project before choosing scope.

## P4. Allocate conclusion-kind and layer containers only when used

Current: each `ConclusionGroup` eagerly owns a REACH array and both FLOW and TAINT
maps. Each per-base group allocates lists for both layers. MethodEdgeStore handles
REACH separately and enforces that conclusion kind follows its premise.

New: allocate a kind map and layer list on first use. A first implementation keeps
all AP merge/subsumption/exclusion algorithms and group identity unchanged. A later
singleton representation requires its own measured layout and promotion design.

Why: this removes empty shells from millions of statement/premise groups. All five
production allocation sites key groups by a fixed premise and check K6. Each group
therefore uses at most one of its two eager kind maps. A HashMap is 48 shallow bytes
in these captures; the two-element boolean array is 24 bytes. Replacing that array
with equivalent primitive flags and leaving unused maps null gives this source-based
gross target for the selected actor-union group cohorts:

| Capture | Conclusion groups | Unused map plus REACH array, 72 bytes/group |
| --- | ---: | ---: |
| Conductor, run-2 | 2310980 | 166390560 |
| ThingsBoard, run-1 | 4827033 | 347546376 |
| Stirling-PDF, run-4 | 8846449 | 636944328 |

These are gross shallow layout targets, not measured net retained savings. Keep
the group header at its current 32 bytes; a larger replacement can consume part
of the target. REACH remains valid in summary/publication/record groups and must
keep both layer bits. This does not avoid required facts or processing.

Lazy layer lists need an occupancy census. Flattening the two list references can
remove a 24-byte outer array while increasing its owning group by eight bytes in
the proposed layout: 16 bytes per group before any unused-list saving. Verify the
actual JVM layout; do not count the whole outer array as net savings.

Preserve REACH use in every owner, both layers, iteration order and NORMAL/DEMAND
subsumption. Compare actual add/delta sequences, including E changes, not only final
fact sets. Existing AP/group controls plus null-to-first-value and two-layer cases
must pass. Do not change field limits, grammar or exclusion tables.
Preserve deferred sequence behavior: creating `all()` before a first insertion must
still observe the inserted value when iteration starts. Read nullable containers
inside the deferred traversal, with the existing kind/layer order.

## P5. Suppress FLOW requests already active at the exact caller

Current: a FLOW cursor enumerates eligible requested marks, checks static coverage
and builds outgoing target batches. Delivery later admits those marks at the caller;
an already-active membership is discarded there.

New: on the caller-owning runner, borrow the caller actor's existing immutable
active DYNAMIC mark set for the full original initial. Apply the same empty-E
request normalization as `requireMarks`. Suppress only memberships already active
for that exact full MethodKey and canonical dynamic pattern. Missing actors or
unknown/queued-but-unhandled memberships still use ordinary counted delivery.
Lookup must not create an actor or introduce another index, history or cache.

Why: it can avoid cursor mark callbacks, builders and transport whose logical
admission delta is empty. ThingsBoard's 1.607 billion climbs justify investigation.
Its full run-1 request-replay frames account for 44.00% of Java CPU samples and
40.15% of sampled allocation. FLOW callback and coverage frames overlap that work.
The duplicate-admission counter includes other producers; no saved fraction follows.
The standing question relation remains needed for later concrete suppliers.

First admission must already have consumed eligible pending answers and scheduled
its upstream publication under existing counted Work. A stale immutable subset can
only forward extra work. Do not substitute narrow SOURCE/SINK/CLEANER catalog
interests for dynamic interest, or use the callee's question as caller support.
TAINT answers and source/sink/cleaner semantics remain unchanged. All remaining
effects stay queued, including local delivery. Do not promise exactly-once events.

Test active T plus new U, first delivery queued but not handled, pending publication
before Work runs, late suppliers and callers, separate contexts/entries/initials,
E/mx, cycles/diamonds, default-unknown ports, cancellation, release and fresh runs.
Compare complete logical closures with an independent history oracle. Counters must
separate marks skipped before geometry from actually eligible climbs and sent batches.

A narrow adjunct can reject nonstatic initials before method-profile lookup and
borrow the immutable static profile once per exact input visit. It must retain the
existing full coverage law, active/cancellation checks and root-cleaner exception.
The observed 9.95% inclusive coverage stack share is not a removable time estimate.

## P6. Join fresh inputs with questions using two delta arms

Current: new N2 growth replays all current questions against cumulative input;
new questions already visit current N2 inputs.

New: replay all current questions against the existing fresh input delta. Keep
new-question marks against all current inputs. Retain the original admitted header
and full caller identity; do not rekey a group with a delta whose E was normalized.
No pair-history set, second input store or whole-method snapshot is added.

Why: old-input × old-question work need not repeat after every growth event. The
benefit depends on repeated growth; ThingsBoard's many first admissions may limit
it. Required standing inputs and questions remain.

Prove both cursor union laws against scalar per-leaf matching at fixed full headers:
`J(Q, C union D) = J(Q, C) union J(Q, D)` and
`J(Q union dQ, C) = J(Q, C) union J(dQ, C)`.
The current raw accumulator performs lossless pointwise union without semantic
subsumption. Exact-only TAINT deltas can clear E; they must not revive old excluded
ANY leaves. FLOW retains fixed E and mx. Query path/tail/E/role and multiple projected
answers remain intact. E6 summary matching alone proves none of these request laws.

Registration-before-question-read and install-before-notify must cover both arrival
orders. Queued overlaps may still deliver duplicates. Test blocked ancestors with
explicit tips, exact/ANY, NORMAL/DEMAND, static queries, later geometry, delayed
replay, late callers, cycles and cancellation. Measure each delta arm separately.

## P7. Validate and normalize each bound source/sink condition once

Current: call-plan binding validates a raw rule, rewriting validates it again, and
predicate creation can repeat signed normalization already done during rewriting.

New: pass a build-local validated and normalized value between these operations.
Keep source and sink rules in scope. Keep NNF; do not expand conditions into cubes.
`P AND NOT P -> false` remains valid. This change introduces no global identity memo.

Why: it avoids repeated walks and temporary condition graphs on the measured OWASP
form-building path. Its net retained-memory effect is not established. Do not
divide cumulative build counts by resolved-call triples to claim a rebuild rate.

Validate the entire raw condition and action list before folding or negative
overapproximation. Preserve source eligibility, full occurrence identity, action
order/ordinals, negative projection, warnings/errors, cancellation and backward
rewriting. Raw rule lists are not immutable by type; an identity-only cache is unsafe.
Test invalid dead branches, contradictions, AND/OR, both rule types, empty actions,
distinct bound occurrences and cancellation/retry. Count validation/normalization
passes per build operation, plus allocations and engine time.

## P8. Possible later managed-cache and reference work

Forms already use managed soft payloads. The current logs show repeated cache clear,
recovery just below the memory threshold and re-enablement in ThingsBoard/PDF.
That is evidence of pressure, not proof that a manager rewrite saves memory.

Possible changes are a designed cache budget or hysteresis, a bounded active-form
borrow for one processing batch, or retirement of obsolete reference wrappers.
Each trades cache retention for form rebuild cost. First measure cold versus rebuilt
slots, cleared-reference registry bytes and active borrowed payload ownership. Keep
keys, payloads and borrows bounded to their intended lifetimes.

A SoftReferenceManager rewrite and WeakIdentityCache remain explicit, unselected
future alternatives. Neither is part of the other proposals. A weak identity memo needs proof of
immutable input identity, complete policy/context/version keys, concurrent ownership
and correct reclamation/rebuild behavior. It must not silently become a strong cache.

## P9. Share cumulative question marks at the existing publication seam

Current: the emitter creates immutable cumulative marks `M`, keeps fresh marks `D`
in a pending builder, then publishes `D` at flush. SummaryStorage filters that delta
and builds another immutable cumulative set. The two owners retain equal contents
in separate arrays after a normal synchronous publication.

New: at the same flush boundary, pass the immutable current `M` with `D` through a
guarded internal publication. Install `M` directly when it is the exact next shared
value, and notify subscribers with the same fresh delta. Keep the public delta-only
path for custom producers. Share no mutable map or builder, publish no earlier, and
introduce no global interner, weak cache or supplier history.

Why: ThingsBoard's local question selection contains 460,192,464 shallow array bytes;
the shared question selection contains 441,918,160. All immutable request arrays
total 910,444,952 bytes. These are overlapping scope measurements, not additive
savings. Dirty local keys and old readers can need different generations. The
proposal avoids redundant materialization and leaves logical interests unchanged.

The simple correctness baseline checks, under the receiver lock, that `M` is exactly
`oldPublished union D` and that `D` is fresh. A sorted primitive walk can check this
without building an expected array. On success, install `M`; on failure, apply the
existing filtering/merge operation to `D` only. This removes duplicate allocation,
hash insertion and sorting while retaining a linear verification walk.

An optional faster design uses an opaque base token. On the first fresh admission
for a clean question, capture `baseMarks = oldActive` in its pending publication.
Aggregate fresh marks as today. At flush, detach the pending entry and capture
immutable `M` before any callback. Under the SummaryStorage lock, use identity with
the current published base, normalizing an absent value to canonical EMPTY. When
identity matches, the trusted producer law gives `M = baseMarks union D`, so install
`M` and send `D`. Otherwise use the existing delta-only fallback.

The production proof uses one actor per full MethodKey, one run-owned SummaryStorage
per key and the synchronous RunMethodAnalyzer publication lambda. Fresh local
admission excludes old local marks, so its pending delta is disjoint from the captured
base and its cumulative value is exactly their union. Source guarantees, rather than
matching hashes or sizes, establish the internal token's equation.

Do not install `M2` when custom delivery sends `D2` before `D1`: its base identity
fails and fallback must expose only `D2`. A repeated token falls back to an empty
fresh delta. A custom callback that admits more marks after capture creates the next
pending entry. A fallback can leave local and shared identities different; a safe
miss sacrifices sharing. A pending token can retain an additional old local array
under custom deferral, so its bounded cost is not universally zero.

Keep full question path, E, role and original initial. Preserve registration-before-
read, install-before-notify, all queued counted effects, active cancellation checks,
old immutable readers and release after joined work. Test every installed view and
notification delta against a scalar receiver, including first/multiple/duplicate
publications, out-of-order custom delivery, foreign delta-only updates, reentry,
cancellation between detach/install/notify, late callers, cycles and fresh runs.

Measure fast installs, fallback reasons, verification members, cumulative array
builds, token lifetimes and the combined local/shared/in-flight owner union. Once
arrays are shared, an individual retained union can shrink merely because the other
owner also reaches it. Compare whole and combined owners before claiming net savings.

An optional later representation can choose between sorted integer arrays and a
bounded dense-window bitmap. Select by actual per-set span and total bytes; compute
signed-ID span in Long and keep sparse fallback. There is no general compact-ID
contract. Preserve ascending cursor/idAt order, membership, minus, size and value
equality/hash across representations. Test negative IDs, both Int extremes, sparse
gaps, tiny sets and immutable snapshots. Include headers, bitmap/rank metadata,
builder peaks and CPU in measurements. Current cardinality and bytes do not prove
span/density or a bitmap saving. This variant is separate from cumulative-set sharing.

## P10. Avoid proven identity cleaner work and its intermediate objects

Current: `cleanChain` builds a new list for every step and new PlanFact wrappers
for callback results. A lowered field cleaner executes read, clean and write
operations, with intermediate lists, even for an unrelated input base. Same-base
cleaning can also allocate a Results accumulator with two maps and a REACH array.

The full OWASP run-1 recording has 9,868 Java CPU samples and 328,036,804,944 bytes
of sampled allocation weight. Cleaner chains occur in 25.70% of CPU samples and
56.44% of allocation weight; their field-statement helper occurs in 38.40% of
allocation weight. `cleanFacts` occurs in 37.61%. These groups overlap. They include
ordinary lowered field actions as well as returned-value rewriting. They do not
measure how many actions are identities or establish a removable fraction.

New, algorithmic first slice: bypass a step only when its current implementation
is proved to emit the identical premise, fact and identityCandidate once, with no
request, source, conjunction, filter, cut or temporary-base effect. Keep the remaining
steps in their original order and retain bounded cancellation checks.
Check each current PlanFact at its actual step; an earlier operation can change its
base or split it. Preserve result order and multiplicity. A guard based only on the
chain's original input is insufficient.

Conservative candidates from the current source are:

- A plain Clean whose cleaner base differs from the input base. `cleanFacts`
  returns the input directly in this case.
- A Kill whose keepEdges summary does not touch the input base and has no sources
  or conjunctions. FormApplier uses its unchanged callback in this case.
- A Field whose read and write summaries both leave the input base untouched,
  have no sources or conjunctions, and whose cleaner base and temporary differ
  from that input base. Both statements take the unchanged path, the cleaner is
  disjoint by base, and the temporary-result filter keeps the input.

Unknown cases execute the full existing operation. In particular, an already
excluded FLOW mark is not enough to skip a selected-mark cleaner: run-1 still
requires its markRequest callback. An absent concrete mark is not enough to skip
a lowered field action: its read/write filters, strong update and statement cuts
need separate proof. Keep all-relevant rewriters and source conversion semantics.

Why: this avoids whole chains of list, wrapper and collector creation for proven
identity steps. It changes no retained fact relation. Count attempted steps by kind,
proved-identity cases, full applications, output widths and allocations before
predicting the benefit. Do not call all cleaner allocation redundant.

An optional technical slice makes the transient Results accumulator lazy or stores
one result key inline before promoting to a map. This is separate from persistent
ConclusionGroup storage in P4. OWASP run-1 allocation samples attribute
22,320,735,536 weighted bytes to allocation sites with Results construction as
the top frame; that figure is not Results object size or retained heap. The replacement must
support multiple kinds and keys, normalize E/mx identically, merge same-key roots,
keep REACH/FLOW/TAINT and per-key encounter order, and preserve mark-request timing
before flush. No reusable global buffer, cache or new fact store is proposed.

Test complete callback sequences against the unchanged chain: unrelated and touched
bases, both layers, selected/all-mark cleaners, existing FLOW mark exclusion, EXACT/
ANY/STAR, every reach mode, E/mx, lowered field filters, strong writes, temporary
removal, reversed steps, cancellation, identityCandidate and pendingDemotion. Include
observer cut callbacks and source/request effects, not only final facts. Measure
full run-1 CPU and allocation separately from retained-memory changes.

## Correctness work and implementation gates

The eight application precision failures and the project location losses are separate
acceptance failures. Do not conceal them through faster partial runs, new sink-key
normalization, relaxed expectations or NORMAL-to-CONFIRMED shortcuts. A later design
can separate field-refinement obligations from concrete candidate confirmation, but
it must retain unresolved candidates and full ND same-caller premises. It has no
established run-1 benefit.

The isolated PDF review identifies a source-target receiver cut on the observed
command route, intact source-seed transfer, and denied command-source admission in
run-3. It also proves that Simple boundary export selects later XSS return locations.
It does not prove all alternative command paths or value equivalence on the earlier
XSS catch paths. A later correctness design must address source-target rewriting,
restricted source admission and report-location provenance with explicit tests.
Do not treat these losses as proof that the removed added-fact store is required.
These repairs are separate from P1–P10 and have no established run-1 speed benefit.

Before any selection, freeze a source-bound proposal. Implement each selected
change separately. Run the relevant
independent AP/closure/scheduler/observer controls with the unchanged candidate
policy, then the same four fixed project configurations. Report strict findings,
engine and barrier times, actual reached/completed/accepted runs, queue counts and
complete retained owners. Earlier OOM or a truncated report is not an improvement.

IR storage, broad mailbox/scheduler redesign and previously deferred queue work
remain outside the next iteration unless new evidence and selection change scope.
No new strong cache, weak cache or reference manager is authorized by this document.
