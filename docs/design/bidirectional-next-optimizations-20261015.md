# Next analyzer-core optimizations

Date: 15 October 2026. Status: the added-fact-store removal redesign replaces the earlier
callee-owned join design. Discarded normal-sink cache retirement remains selected.
Implementation has not started. Use the
[new emission design and plan](bidirectional-added-facts-removal-20261015.md) for the
current storage-removal direction; the older join section below is historical design evidence.
The measured runtime is `85a7c0e367d9a2dc6360b03c2feb5e07b1583b66`.
Use these measurements to design and validate the next iteration. Earlier measurement rankings are stale. The
[current report](../reports/bidirectional-run1-abstraction-demands-performance-20261015.md)
binds all twelve terminal main/control/optimized scans, complete recording reviews and four
analyzed guard heaps. The supplementary container and initial census is complete; occupied
statement cells remain unavailable. The command-loss diagnostic and final output-parity review
are complete with the coverage limits in the report.
Historical revisions cannot supply missing evidence for this revision.

The first target is a completed **run-1, forward, limit 1** engine within 60 seconds.
Setup and report barriers remain separate. Limits start at 1.

| Optimized project | Latest reached | Last completed / accepted forward report | Run-1 engine |
| --- | --- | --- | --- |
| OWASP | run-3 / forward / limit 3 | run-3 / run-3 | 349.964 s; missed |
| Conductor | run-2 / backward / limit 2; OOM | run-1 / run-1 | 69.931 s; missed |
| ThingsBoard | run-1 / forward / limit 1; OOM | None / None | 164.488 s; incomplete |
| Stirling-PDF | run-4 / backward / limit 4; TIMEOUT | run-3 / run-3 | 30.914 s; met |

Corrected control reaches the same iterations and exports the same sink identities, spans and
occurrence counts on all four projects. Its run-1 times are 247.764, 46.420, 209.293 with OOM,
and 56.634 seconds respectively. These single instrumented pairs have different host load and
incomplete OOM frontiers; they do not isolate causal cost. Earlier OOM is not a speedup.

Finding acceptance is still failed. OWASP retains all 4,112 main sink identities. Conductor
retains seven but adds 293 unrefined candidates and has one invalid export location. ThingsBoard
exports none of main's 14 sites because run-1 stops before report acceptance. Stirling-PDF
retains 14 of 17 main identities and adds 42. Two strict XSS location losses have a source-backed
export-fallback explanation; the command route has a specified source-rewriter cut described below.
Matching control output does not prove complete analysis or equal internal witnesses.

The isolated diagnostic now identifies a specified source-rewriter cut on the missing command
route. At the later `command.add(tempOutputFile.toString())`, backward source matching emits
requirements on the appended argument, while the reversed exact cleaner removes the prior
receiver's root `$ARGS` fact. The earlier URL append cannot be reached by that receiver branch.
The measured report binds the runtime rows and unchanged compiler/kernel bodies to the
summary-rewriter contract. This is a recall/specification issue to review before acceptance;
it is not an AP primitive defect or an authorization to change source overwrite semantics.
The diagnostic has not proved that every possible route fails at this same gate. Keep the
strict loss and the need for a separate correctness design explicit in the next iteration.

## What the new measurements support

ThingsBoard's optimized guard is **run-1 / forward / limit 1**, with no completed engine or
accepted report. Its reachable heap is 11,374,504,144 bytes. The matching control phase has
11,389,516,736 reachable bytes; neither snapshot is a completed closure.

| Optimized ThingsBoard owner set | Default MAT retained union, bytes | Implication |
| --- | --- | --- |
| Actors/runners/subscriptions | 5,405,248,360 | Persistent core state is large |
| All added-fact stores | 1,523,417,616 | Call-input and observation storage merits an ownership design |
| N2 owners | 1,617,261,144 | Includes caller subscriptions and necessary publication storage |
| Provider wrapper | 1,327,132,728 | Expanded bound-rule caches merit a lifetime design |
| Entries/forms/caches | 704,069,848 | Includes alias/graph owners; only 11 compiled payloads remain |
| General queues/channels | 1,238,184 | Broad queue tuning is not the main retained-memory target |
| Shared static catalog | 4,923,696 | Logical transitive membership counts do not imply huge physical storage |

These unions overlap and cannot be added or called reclaimable savings. Default MAT reference
paths are not strong-only proofs. Control's standing N1 stores retain a 2,431,949,392-byte union;
those stores are absent in optimized. Yet optimized has 7,522,164 raw/N2 entries versus control's
4,721,493. Equal actor counts and different handled-message totals do not establish matched
semantic progress or a net memory saving. Nearly equal alias-object cohorts also fall under
different default retained selectors; the forms-union difference is not a measured alias
allocation regression.

Both Conductor heaps are **run-2 / backward / limit 2**, after accepting run-1. Optimized has
7,633,136,808 reachable bytes, a 950,228,720-byte N2 union and a 549,844,664-byte added-store
union. Its 219 physical queues contain two entries; no run-1 abstraction or catalog remains.
Thus its later OOM cannot be attributed to a live run-1 policy. These unions also overlap.

Profiles separate CPU/allocation churn from retained memory. OWASP run-1 has 300.5 GiB sampled
allocation weight; cleaner execution appears in 55.2% of that inclusive weight and call-plan
building in 30.3% of Java CPU samples. ThingsBoard pre-guard run-1 has 449.5 GiB sampled weight;
plan building appears in 32.7% of Java samples, alias computation in 21.7% and frontier work in
19.8%. Paths overlap and are not removable fractions. Final form totals span all reached runs,
not per-run first builds or rebuilds. Normal windows also differ in phase between some pairs.

The same END-counter family shows more run-1 scans/climbs in optimized Conductor and
ThingsBoard. ThingsBoard has 325,282,471 member scans, 319,832,979 climbs and 146,142,925
failed outgoing-set additions. These count different operations, not duplicate-message rates.
Broader precomputed interests can admit additional required work. Removing a request store
alone did not remove all its underlying work.

IR storage is measured context only; its optimization is out of scope. Broad N3 queue changes
remain deferred unless a future capture shows their collections dominate. The next proposals
keep the existing reference manager and introduce no weak-identity cache.

## Expected memory impact

This order uses the completed supplementary census. It ranks expected memory
effect, not implementation ease. The owner footprints are ceilings on candidate scope, not
predicted savings. Improvements overlap; removing a caller directory also removes buckets that
a representation change would otherwise optimize. Do not add the estimates.

| Order | Candidate | Expected memory effect and evidence | Main uncertainty |
| --- | --- | --- | --- |
| 1; superseded | [Move the run-1 join to the callee](#move-the-run-1-inputsummary-join-to-the-callee-owner) | Earlier way to remove duplicate inputs; now replaced by callee added-store removal | The old design's sole input history was the raw store that the new design removes |
| 2; selected | [Retire discarded bound sink lists](#stop-retaining-discarded-bound-sink-rules) | Large conditional lifetime opportunity: normal sink cache maps have a 1.092 GB retained union within the 1.327 GB provider footprint | Fraction discarded by the existing selection; a small fraction can move this below lazy buckets |
| 3 | [Allocate conclusion buckets on demand](#allocate-conclusion-buckets-only-when-needed) | Broad measured representation opportunity: 236.4 MB of empty kind-map shells, 111.2 MB of unused REACH arrays and large empty layer-list cohorts | Net owner layout, overlapping selections, live-layer capacity and promotion cost |
| Conditional later | [Share concrete-initial transfers](#share-transfers-across-concrete-initials) | Could avoid repeated intermediate work across 532,543 admitted actor memberships; scalar boundary storage remains | Global concrete-mark cohorts and common mark-neutral blocks remain unmeasured; the largest incoming store is mainly abstract FLOW |
| Conditional later | [Avoid unused alias preparation](#avoid-unused-alias-stage-preparation) and [manage alias payloads](#possible-later-referencecache-work) | Could avoid or release strong alias state | Which preparation is unnecessary, strong-only bytes and stable timed rebuild behavior |
| Smaller retained target | [FLOW interest guard](#skip-ordinary-flow-gates-when-interest-is-already-active), [catalog union](#union-catalog-marks-for-canonical-static-root-flow-climbs), [target coverage](#suppress-climbs-already-owned-by-the-exact-target-policy) | Can reduce scans or temporary outgoing work; required final policy/fact state remains | Eligible hits, placement, lookup overhead and simultaneous avoided frontier bytes |
| Mainly temporary allocation/CPU | [Direct FLOW cleaner](#emit-selected-mark-flow-cleaner-results-directly), [cleaner intervals](#batch-eligible-selected-mark-cleaner-intervals), [single normalization](#normalize-each-bound-source-or-sink-condition-once) | Can avoid intermediate objects and repeated execution on measured hot paths | Actual selected-branch/interval frequency and attributable work; no large retained saving shown |
| Future evidence only | Reference-wrapper cleanup, weak-identity memo and phase-aware form retention | No supported large run-1 retained saving; keeping forms can increase memory | Exact registry/key ownership and per-run rebuild/hit costs |

The first three candidates address measured persistent owners. This is a selection order with
explicit uncertainty, not a promise that the first change saves more bytes in every project.
The sink-cache fraction and call-route coverage can change the order. The conditional candidates need
stronger frequency or ownership evidence before selection. CPU options remain useful for the
60-second goal, but sampled allocation weight does not promote them into the top retained-memory
ranks. The user initially selected the first two candidates, then proposed removing the
callee added-fact store. That redesign replaces the first candidate's ownership protocol.
Lazy conclusion buckets and the other candidates remain outside the implementation iteration.

The selected sink change is the minimal normal call/entry/exit cache-lifetime change below.
It keeps full cold resolution and retires obsolete unfiltered cache roots after joined prescan.
Early filtering, borrowed streaming, managed caching and compact rule keys require separate
selection. The [added-fact investigation](../reports/bidirectional-run1-fact-explosion-20261015.md)
checks the existing mark packing and the growth of caller/premise/header relations before
proposing another storage or transfer change.

## How to choose the next change

Both algorithmic and technical changes are justified, with different targets. Moving the
run-1 join to the existing callee input owner can avoid a second persistent relation. Retiring
or avoiding expanded discarded rule bindings can avoid entities that do not contribute to
selected analysis. These need ownership/identity designs; neither permits dropping required
suppliers or proofs.

For required conclusion state, millions of measured container-owning buckets justify testing
lazy allocation. The census confirms many empty kind maps and unused layers, plus 4,390,104
single-base conclusion buckets. No source proof currently permits removing the logical
conclusions themselves. A small-container change can therefore complement the algorithmic
work with a smaller semantic scope. Broad queue tuning and concrete-origin sharing remain
subject to their own evidence gates; their logical counters alone do not establish a dominant
retained-memory target.

Prefer avoiding repeated work and intermediate objects to storing the same work more compactly.
Rank the candidates after checking the new engine profiles and any guard heap. Distinguish
retained memory, peak pending work, sampled allocation weight and CPU; none substitutes for
another. Inclusive profile paths overlap and are not removable fractions. The composite
`groupGeometrySteps` counter includes structural and catalog cursor work as well as AP geometry;
it cannot attribute the observed increase to one traversal. Separate catalog, dynamic, cleaner,
structural, geometry and mark-probe work in a bounded numeric census.

An ordinary local FLOW-interest guard can avoid repeated check geometry. Canonical catalog-root
union can avoid some repeated query and member traversal. Exact-target coverage at the climb
callback comes after those scans and can avoid only subsequent unions, snapshots, transport and
receiver admissions. A transport-only change is not yet evidence of a solution to the 60-second
target. Each candidate must measure the work it can actually remove.
All candidates keep abstract `*` emission. Requested concrete marks remain separate supplied
alternatives. Interest never becomes a predicate proof or a conjunction of requested marks.
Keep source and sink NNF, occurrence bindings, full MethodKeys, layers and original premises.
Finding acceptance remains a gate: preserve ThingsBoard's 14 missing sites and all three
Stirling-PDF strict location losses. Two XSS losses have an export-location fallback explanation;
the command route has a measured rule-guided overwrite cut that needs a separate correctness
contract before any recall change. Neither issue can be dismissed to claim a run-1 saving.

## Move the run-1 input/summary join to the callee owner

Superseded by [initial emission without an added-fact store](bidirectional-added-facts-removal-20261015.md).
The old protocol below made raw callee links its sole input history. Implementing it unchanged
would conflict with removing those links. The new proposal retains the existing caller N2
summary join and removes the duplicate callee store instead.

| Current | Proposed | Why it can help |
| --- | --- | --- |
| A caller accumulates and indexes a subscription, then sends the same admitted input to the callee's raw store | For eligible run-1 calls, send one counted `CallInput`; accumulate once in the callee's existing raw store | Avoid a second persistent caller-side input directory and repeated accumulation work |
| New summaries notify caller units, which search their cumulative subscription tables | The callee joins new summaries against its current owned inputs and posts immutable matched results | Keep one input relation while preserving caller-owned return application |

This is an algorithmic protocol change, not aliasing the two stores. First scope is ordinary,
successfully routed **run-1** calls only. Restricted runs, native/reversed-record replay and
backward zero-only subscriptions keep the current path. It therefore makes no direct claim
about Conductor's run-2 OOM. Use existing unit routing; do not resolve the call graph again.
Unknown or unsupported targets retain the legacy path. A rejected admission fails the run;
it must not silently change route after part of an input has been accepted.

The current 1,617,261,144-byte ThingsBoard N2 union includes `CalleeSubscriptions`,
`SummaryStorage` and `PublicationIndex`. The supplement measures 1,247,295,120 bytes for
caller directories alone and 369,966,024 for necessary summary/publication owners. Publications
remain necessary. Neither that union nor the broader raw-store union is an
exclusive saving estimate. Some fact objects already share, and new event/ownership metadata
also consumes memory.

For one eligible input, retain the complete callee MethodKey and CallerRef: caller MethodKey,
original premise, caller layer and exact call instruction. The admitted GroupKey keeps kind,
link layer, base, E and mx. Contexts and entry statements remain distinct. Feed raw admission
the original input, not another store's fresh delta: an exact TAINT delta can clear E while
the admitted group's key must not. Return raw handle/header/current value even on a covered
input; the present null-on-no-delta API is insufficient for ownership lookup.

The callee handler retains/emits the fresh raw update through the existing abstraction, then
matches it against existing publications. Each new publication matches cumulative current
owned inputs with the same AP predicate as today. Always enqueue matched results to the caller,
even locally. Only caller-owned summary application resumes call plans, return actions and
cuts. Carry the full publication, matched member and original caller support, including ND
premises. Concrete requested initials remain additive to the abstract `*` initial.

A compact actor-local set of eligible raw handles records exclusive new-protocol ownership.
New-publication scans test it before matching. Native root/Start and ZeroIn bypass raw links,
but public/direct or fallback link input is not automatically owned. A legacy-first group
cannot be upgraded merely because a later CallInput is fresh or duplicate. Use counted
register-only fallback with the original incoming facts/header, without resending LinkIn.

If foreign input arrives after a group becomes owned, either a checked stable-route contract
must exclude that case before mutation, or demote once: capture the pre-foreign owned value,
secure counted legacy registration for that value, then retire new ownership. Merely clearing
a flag loses old replay coverage; keeping it can apply unowned input. Preserve full-header
identity on fallback. No delivery may infer ownership from payload equality alone.

Use an indexed current-value visitor with primitive seen handles for the minimal join. While
it runs, the callee owner processes no other input and invokes no call-plan callback; results
only enqueue. Read current immutable group values at that operation's handle horizon. Do not
open another COW view or use an older abstraction cut: `AddedFactStore` allows one active view,
and that older cut can omit already admitted inputs. The existing abstraction view and its
before-change hook remain intact.

This first visitor is a finite atomic match with per-item cancellation checks. It does not
promise bounded fairness. A resumable join needs a separate snapshot/dispatch design and must
not introduce an unbounded version history. Avoid constructing a whole candidate snapshot
list. Release publication locks before matching, callbacks or sends.

For each input/publication pair, whichever the owner processes second joins against the other's
retained state. Establish that race-coverage proof independently; do not assume the old
registration proof transfers. Count input, matched-result and fallback events before visibility,
release each reservation exactly once, and reserve follow-up work before completing its parent.
Cancellation must not produce false COMPLETE. Raw forward suppliers still outlive runner release
through Support and report acceptance. Summary persistence and the existing publication flush
when the method worklist empties remain unchanged; there is no new summary-barrier saving.

Implementation order is caller-only ownership/fan-in census; explicit route and counted event
contracts; current-group visitor and exclusive-handle admission; both join arrival orders; then
legacy fallback and joined cleanup. Compare complete closures with the current protocol for
same/cross-unit calls, existing-summary-before-input, late publications, reentry, duplicates,
new caller origins, regular REACH, ND members, exact-delta E changes, layers/mx, active COW,
Support same-site scope, mixed ingress with no fresh delta, and failure/cancellation schedules.
Keep F1/B2/F3 summaries, reports and handoff oracles.

Matching moves from distributed caller units to the callee unit. Measure hot-callee fan-in,
publication/member fan-out, candidate selectivity, unit load, longest atomic match/cancellation
delay and matched-event volume. Lower retained memory and fewer merges do not prove lower
wall time or a 60-second run-1. The source-bound full design is
`report-preparation/call-input-dual-accumulator-review/RUN1-CALLEE-JOIN.md`. This older
protocol is superseded; do not implement it in parallel with added-store removal.

## Stop retaining discarded bound sink rules

| Current | Proposed | Why it can help |
| --- | --- | --- |
| Base configuration caches fully bound sink lists; Semgrep selection filters afterward | Create a selected normal-sink view and retire obsolete joined-prescan list roots | Avoid retaining rules that the existing selection discards |
| Cold binding expands complete candidate lists, conditions and metadata | First retain the old resolver, then consider borrowed ordered binding/compilation | Separate a low-risk lifetime change from avoiding binding work |
| All completed bound lists stay strongly cached | Consider an independent existing-managed-reference cache experiment | Trade cache memory for rebuild work without changing the reference manager |

The measured provider root retains 1,327,132,728 bytes in ThingsBoard run-1. It includes
4,278,212 bound sinks and the same number of sink metadata objects. Its `ContainsMark` and
condition-atom objects occupy 501,585,552 shallow bytes. The root includes other families and
shared models, so it is a footprint ceiling, not an eligible selected-cache size or predicted
saving. The supplement narrows the normal call/entry/exit sink-cache union to 1,092,011,616
bytes. The allRelevant sink maps occupy 144 bytes. Normal and allRelevant source/cleaner cache
unions are 65,067,128 and 42,577,144 bytes; preserve their separate selection rules. The eight
store rows total 365,353 normal and 79,610 allRelevant cached-method entries across families.
These are not binding counts. Selected/discarded binding density is still needed before a
net saving estimate or a fixed order relative to lazy buckets.

This issue first needs a lifetime/algorithm change, rather than smaller collection cells:
fully bound candidates rejected by the existing selection need not stay cached. The minimal
slice covers only normal call, entry and exit sinks. Keep the concrete rule classes, full
structural equality, ordered selected candidates and multiplicity. Publish a provider-owned
immutable selection generation only after prescan joins. Derive selected views from existing
cached lists, then retire their unfiltered list roots. Neutral templates remain available.
An explicitly unfiltered query can resolve again; it must not read a previously filtered view
as if that view contained every rule.

For cold selected queries, run the current full resolver unchanged and filter its result with
the exact dependency-closed selection, including null-ID defaults. Cache only the selected list;
release the temporary unselected list. Preserve synchronized complete publication. A later wider
selection needs a new generation built from neutral storage, or enforce the existing single
joined transition. Keep normal and allRelevant caches distinct. Normal cleaner/pass-through
queries remain broad as today; D23 also retains its broader source/cleaner rewriter view.

Early filter pushdown is a separate algorithmic extension. Today an unselected candidate can
still throw during resolution. Anonymous sink IDs also depend on the ordinal of successful
bindings before selection. Skipping an earlier candidate can hide an error or renumber a kept
sink. Retain full cold resolution until a scoped validation/identity permit proves that
materialization can be skipped, with the old resolver as fallback. Do not change that diagnostic
policy or use a raw observed-ID set in place of the dependency-closed selection.

Full sink identity remains required: `RuleId` is the bound sink value, and handoff reacquires
a form and compares that rule. Equal public IDs do not merge different bindings, conditions,
end actions, metadata or support. Keep candidate ordinals and occurrence/proof scope stable
through clear/rebuild. Raw validation still precedes signed/static folding; a selection permit
is not a validation permit. Source and sink NNF, repeated leaves, AND support, F75 and D23 stay
unchanged. Native copies already use mark-parametric operations; arbitrary conditional sources
and sinks cannot be replaced by `CopyAllMarks`.

A later borrowed candidate cursor can materialize the existing rule schema one binding at a
time and compile it inside the borrow. Do not retain the iterator, resolver, whole old list or
mutable binding environment. Direct template-to-NNF compilation or a compact sink key needs a
separate identity design; it is not hidden inside cache retirement. Any compact key must retain
complete logical equality, not just a serialized ID.

The managed-cache alternative uses the existing analysis `RefManager` and a stable slot/recipe
per method, family and selection generation. A scoped strong borrow pins one complete payload
through validation and compilation. Failure/cancellation publishes nothing; disabled caching
keeps only the callback's local value. Preserve equal full rules, order, IDs and proofs after
rebuild. No strong fallback, reference-manager rewrite or weak-identity cache is introduced.
This alternative can increase repeated binding and normalization under pressure.

Implementation order is census and independent resolver oracle; joined selected-sink view and
old-root retirement; proved-safe pushdown with fallback; then optional borrowed streaming.
Treat managed caching and direct template/compact-key work as separate selections. Test an
unselected malformed rule before a retained anonymous-ID rule, equal IDs with different
bindings, AnyArg/array/root/Element cases, raw-invalid false branches, allRelevant D23 behavior,
concurrent readers, cancellation and exact cache-rebuild/handoff equality. Then repeat the fixed
project gates with unchanged settings. Measure owner bytes, selected density, avoided binding
objects, setup/engine time and attributable rebuilds separately.

The full source/heap review is `report-preparation/spring-bound-rule-expansion-source-review`.
The minimal selected-view and old-root retirement change is selected. It has no claimed byte
saving or run-1 time improvement yet. The optional extensions remain unselected.

## Allocate conclusion buckets only when needed

| Current | Proposed | Why it can help |
| --- | --- | --- |
| Every conclusion bucket allocates a two-element REACH array and two map shells | Use two REACH bits and allocate each kind map on first use | Avoid empty arrays and maps in millions of buckets |
| Every FLOW/TAINT group allocates both layer lists, their backing arrays and an outer array | Keep nullable NORMAL/DEMAND list fields and allocate a list on its first write | Avoid storage for an unused layer |
| Small buckets use general maps and lists immediately | Consider singleton-first promotion after a net layout prototype | Avoid further container overhead where measured density supports it |

This is a technical representation optimization. It keeps the logical facts and work. The
captured ThingsBoard run-1 heap has 4,779,426 `ConclusionGroup`, 4,471,295 `FlowGroup` and
497,514 `TaintGroup` objects. Dense statement arrays account for only 45,913,664 shallow bytes;
the target here is the nested container population. The completed census finds 3,964,847
FLOW-only single-base buckets and 425,257 TAINT-only single-base buckets. Another 243,085 have
one kind with multiple bases, and 146,237 have no FLOW/TAINT entry but can still carry REACH.
These four successful cohorts sum to the complete 4,779,426-object histogram. The both-kind
query returns no result and remains unavailable; the sum does not rewrite its query status.

Empty FLOW/TAINT map shells occupy 28,776,960 and 207,654,864 shallow bytes. Unused REACH arrays
occupy 111,176,136. FLOW/TAINT outer layer arrays occupy 107,311,080 and 11,940,336 shallow bytes.
Empty FLOW NORMAL/DEMAND list unions occupy 15,424,752 and 192,392,736 bytes; the TAINT unions
occupy 14,446,080 and 9,004,896. Retained list unions include their backing arrays and overlap
broader owners. For example, the FLOW DEMAND union's 8,016,364 objects include 4,008,182 lists
and the same number of backing arrays. Gross sizes are not net savings. A single-base map
also does not prove that its tree list is singleton.

The minimal design keeps the current `add`/`all` interfaces and existing merge/subtraction
kernels. Encode REACH with one bit per layer. Make the FLOW and TAINT maps nullable; first use
creates the same map type and capacity as today. Replace each two-list outer array with two
nullable list fields; first use creates the same list type/capacity. Pass only a real list to
the current T2/T4 code. No trie, AP, IR, scheduling, interning or worklist change is included.

Keep the general bucket contract. Method edges handle REACH separately and check conclusion
kind against the premise, but summary, publication and record owners also use this class.
`ConjunctionStore` owns some TAINT groups directly. Both kinds, all bases, both layers, exact
and must TAINT, full E and FLOW mark exclusions remain available. Keep correlated E/mx folding,
subsumption, delta identity and cancellation-before-mutation behavior unchanged.

`all()` is lazy today. A sequence retained before the first insert must see the inserted bucket
when it is later consumed. Defer owner-field lookup until traversal; do not capture an absent
map/list when constructing the sequence. Preserve family/layer order, repeated traversal and
the current map/list behavior. No mutable list escapes its single writer.

A later singleton-first map holds one base/group pair, then promotes on a second distinct base.
A singleton-first layer holds one tree, then promotes when another stored annotation group is
needed. This phase must handle removal and folding back to one value. It is not part of the
minimal lazy-container change unless the census and a layout prototype justify it. Owner fields,
tagged holders, promotions and wide-bucket costs count against any saving. For example, moving
the two layer references inline can grow an owner while removing its outer array.

Implementation order:

1. Use the completed census and prototype full owner layouts. Preserve its two unavailable
   queries and field limits; keep global owner scopes and overlapping unions explicit.
2. Implement REACH bits and lazy kind maps, preserving the existing kernels.
3. Implement lazy layer lists. Compare complete stored values and returned deltas with the
   current implementation for small exhaustive cases and existing closure/reference controls.
4. Add singleton promotion only as a separate measured extension. Compare full owner layout,
   allocation, lookup cost and wide-case behavior before selection.

Tests must cover retained sequences before first add, repeated traversal, empty/singleton/wide
promotion, both layers/kinds, REACH, FLOW T2/T4 E/mx folding, TAINT normal must plus exact,
exclusions, cancellation and reentry. Keep native records, summaries and F1/B2/F3 parity tests.
The source/census design is `analysis-preparation/group-bucket-census` in the measurement batch.
This option can reduce retained representation cost; it does not by itself prove a 60-second
run-1 engine or reduce required call-input cardinality.

## Normalize each bound source or sink condition once

| Current | Proposed | Why it can help |
| --- | --- | --- |
| On projected call/exit paths, `rewriteSourceOrSink` produces a positive effective NNF; `predicate` converts and normalizes it again | Pass a private validated positive-NNF value directly to predicate compilation | Avoid a repeated expression traversal, node construction and temporary collections |
| Related paths receive generic `ExprOrConstant` values with different semantic stages | Make signed rewritten and positive projected values distinct internal types | Prevent cost reduction from accidentally removing required signed folding or eligibility checks |

Keep both required semantic folds: normalize the raw signed condition before static/alias
rewriting, then fold the rewritten signed condition before approximating remaining negative
mark literals. `P AND NOT P` still becomes false. Remove only the repeated conversion/fold of
an already prepared positive NNF at the predicate compiler boundary.

Introduce a private positive-NNF token whose constructor is available only to validated
preparation. The token proves the phase of the expression, not that a runtime condition holds.
The predicate compiler still binds the complete literal positions and the appropriate source
or sink array role, and creates the same logical occurrence/proof structure.

Validate all raw positions, actions and end-fact positions before folding, including fields
that a false condition or empty action list would later discard. Collect and preserve the live
exit footprint only when the folded output is not false. A proved-false output has no read,
drop, seed or request effect. A validation permit belongs to one actual bound input. Do not reuse it by serialized
rule ID. Normal providers and `allRelevant` providers can bind different conditions and actions.

Signed consumers, including `projectNegatives=false`, retain signed expressions. Entry eligibility consumes signed truth before negative approximation. Source return cleanup
consumes signed not-false eligibility. Neither path may use projected truth in its place.
Constants, action-empty sources, repeated leaves, multiple actions and signed false branches
retain their current behavior. Do not expand cubes or memoize runtime proof/support results.

The first implementation needs no map. Optional later sharing inside one build requires the
same bound object and complete rewrite/alias context; it is a separate extension. Do not add a
global rule cache or a weak-identity cache as part of this candidate.

Validation must compare complete predicate and action results for positive/negative AND/OR,
contradictions, static constants, alias-dependent leaves, entry rules, return cleanup, the
existing shared-resolver source-array specialization and sink drops. The extra root/Element
OR choices in `JIRRulePredicates` apply only to call-sink arguments; do not add them to source,
receiver, result, entry or exit positions. Include normal and `allRelevant` copies with equal IDs but
different bindings. Check real F1/B2/F3 findings, witnesses and selected demand after cache
rebuilds. Count raw, post-static and redundant normalization passes separately at build boundaries.

The expected benefit is less allocation and compilation work. The final stored predicate graph
need not get smaller. Rank this candidate from the new build/setup and engine measurements.

## Avoid unused alias-stage preparation

| Current | Possible change | Why it can help |
| --- | --- | --- |
| A full call plan prepares ALIASES even when its later `MemoryEffect` guard rejects an input | Keep a minimal immutable stage descriptor; prepare the exact stage when its guard first admits an input | Avoid unused stage construction and, when no other consumer has forced it, shared alias-state computation |
| Bind-back edges can repeat persisted-alias queries for one local target within a build | Reuse that query result within the builder, while emitting every original source edge in order | Avoid repeated list/intersection work; the temporary map ends with the build |
| Forward dispatch builds a cold plan before checking an unrelated input base | Perform the existing exact syntactic touched-base test before full construction | Avoid a build whose only result is the existing unchanged-input branch |

These are independent options. The guarded-stage option has the broadest design scope. Keep
raw rule validation, source actions, sink footprints, zero sources, F75 and signed not-false
RETURNED cleaners. Force the complete alias stage before backward reversal and alias pass-over
base computation. Later DEMAND or nonidentity inputs must still obtain every original edge.
Keep each native identity/effect path and pending demotion frame. Do not retain the whole
builder in a lazy closure. Specify concurrent first use, cancellation, reclaim/rebuild and
stable occurrence identities before implementation.

Completed alias states are already strongly shared per physical method entry. Rebuilding a
form does not by itself recompute those states. Build-local query reuse therefore targets
persisted-alias list/intersection preparation, not every sample inside alias computation.
Resolver, static-condition and statement consumers can already have forced the same result;
stage deferral cannot save that computation again.
Stage deferral may move work to a later run; it does not guarantee lower total memory or time.

The touched-base precheck applies only to forward non-Reach input with the exact current touched
set, including STATIC. Preserve `emitUnchanged` and its original premise. Backward touched bases
include alias pass-over and cannot use that test. The same bound call must already have passed
raw rule validation and mandatory structural, resolution and catalog-target checks; otherwise
retain those checks before the early branch or build the plan as today. Do not hide an invalid
rule, action or missing prescan target. A zero input may already build the same plan,
so the actual cold-build saving can be small.

Measure prepared versus admitted alias stages, first alias computations, repeated local targets
and cold unrelated-base calls before ranking these options. Compare exact scalar stage edges,
call targets/contexts, source/sink proofs, summaries, records and F1/B2/F3 seeds. An absent current
mark is not a safe general pruning rule: FLOW interest and delayed AND support must remain live.
No reference-manager or weak-identity-cache change is part of these options.

## Emit selected-mark FLOW cleaner results directly

| Current | Proposed | Why it can help |
| --- | --- | --- |
| A selected-mark FLOW clean constructs `FlowClean` and a general `Results` collector, inserts one key/root, requests the mark and flushes one result | Dispatch this case before those objects are built and emit its one canonical result directly | Avoid temporary accumulator maps, array, key and entry machinery without changing cleaner order |

This narrow allocation/CPU option does not need a wide cleaner interval. It leaves all existing
steps and barriers in place. It does not reduce required request cardinality or retained actor,
N2, policy or AP storage.

After the existing entry cancellation check and same-base guard, prepare the same canonical
`mx + T` and E. Then issue the unconditional run-1 F75 request on the original premise, perform
the existing pre-result cancellation checkpoint, and construct/emit one result through
`ApManager.flowTree` with the original root, base and layer. Legal FLOW roots are nonempty, so
the old collector cannot discard this branch's result. Keep Reach, another-base, all-mark FLOW
and TAINT paths unchanged.

Order matters. Canonical header preparation remains before the request. Result construction
remains after the request and its following checkpoint. A throwing request callback emits no
result; a callback that cancels and returns reaches that checkpoint and emits no result. Do not
add a geometry check or skip already excluded marks. Do not return the original fact object as
a further no-op specialization. Existing call wrappers retain original premise, identity
eligibility, pending demotion and Field read/clean/write behavior.

The completed OWASP run-1 profile attributes 19.818 decimal GB of allocation weight to the
`Results` constructor and 32.658 GB inclusively to `FlowClean.run`. These overlap other work:
`Results` serves other AP operations and `FlowClean.run` includes all-mark and downstream work.
Neither is a removable selected-branch fraction. The selected operation frequency is unmeasured;
no retained saving, CPU fraction or 60-second result is claimed.

Implement only this dispatch/emit specialization if selected. Compare the independent scalar
cleaner relation and ordered callbacks for all layers, exclusions, paths/reaches and selected
marks, including already excluded/disjoint cases. Test throwing/cancelling/reentrant callbacks,
restricted modes, huge shared roots and deep paths, original caller scopes and native identity/
demotion controls. Measure actual branch allocation with equal escaping outputs, then repeat
fixed-project run-1 timing. Default-off scalar counters can distinguish selected FLOW from
other cleaners without retaining fact keys. The source-bound design and compact evidence are
`report-preparation/selected-flow-cleaner-fastpath-review`.

## Batch eligible selected-mark cleaner intervals

| Current | Proposed | Why it can help |
| --- | --- | --- |
| An ordered cleaner program applies one selected mark at a time, creating intermediate results | Process an eligible interval with its actual mark set | Walk geometry and construct residual headers fewer times |
| FLOW selected cleaners each union one mark into the exclusion and register one interest | Union the interval's marks once and deliver a mark-set interest on the same original premise | Avoid repeated exclusion copies and local registration calls |

The first common kernel is limited to consecutive plain `CleanStep.Clean` steps with selected
marks and identical base, full path and reach. Represent the interval with an immutable actual
mark set, not a dense global mark universe. The scalar cleaner remains the independent reference.

For TAINT, replace singleton selection with intersection against that set. Keep every existing
EXACT/ANY/must, NORMAL/DEMAND, exclusion and partial-cleaning result. A mark not selected by the
interval is unchanged. Results can coalesce equal fact headers as they do now; they must not
combine different premises or predicate proof slots. Process each original `PlanFact`
separately and preserve its `identityCandidate` and enclosing `pendingDemotion` frame. Equal
AP headers cannot justify merging native identity and effect paths.

For same-base FLOW, preserve its root, layer and tail exclusion and union the whole selected set
into its mark exclusion. In run-1, request **all selected marks** on the original initial,
including already-excluded marks. F75 does not test cleaner geometry before this request.
Subtracting the current mark exclusion from the request set would change the rule.

Use an internal grouped clean operation and an additive mark-set output path. Existing scalar
consumers can retain a scalar callback adapter. The engine can deliver the set directly to the
existing abstraction policy. Keep cancellation bounded during mark scans, supplier joins and
result construction. Duplicate registration diagnostics may change; factual closure may not.

A separate FLOW-only specialization can cover consecutive plain selected cleaners with the
same base but different paths/reaches. FLOW ignores those paths in its selected-mark branch.
Keep the exact ordered scalar program as the TAINT fallback, including the existing reverse
conversion's order. This specialization alone does not remove cached scalar cleaner objects.

Terminate intervals at all-mark cleaners, `Field`, `Kill`, read/write programs, filters, cuts,
observers and stage boundaries. Do not reorder through them. String-byte lowering can interleave
root and field steps for each mark, leaving interval width one. A large relevant-mark universe
therefore does not establish a large groupable interval. Do not merge typed field temporaries
or reuse field filters without a separate proof.

Metadata collection must still see every selected static cleaner mark as base-wide F75 interest.
Grouped compilation does not change rule eligibility or invent a position-by-mark product.
Reverse plans and restricted runs must retain their current behavior.

Validate the grouped kernel against ordered scalar composition for all small mark sets, leaf
tails, exclusions, reaches and layers. Include partial outputs, disjoint paths, already-excluded
FLOW marks, duplicate marks, wrong bases, late suppliers and independent requested initials.
Add explicit non-groupable string/field/filter/Kill boundaries and full source/sink closure tests.
Include native and record resume controls that distinguish identity and effect paths.
Measure interval widths and actual executed grouped work before estimating benefit. Separate
runtime intermediate savings from any later physical compression of cached forms.

## Skip ordinary FLOW gates when interest is already active

| Current | Proposed | Why it can help |
| --- | --- | --- |
| An ordinary concrete-mark edge or check walks FLOW geometry, then deduplicates its request | Check the original initial's active policy before that geometry | Avoid repeated work whose only possible effect is an already registered interest |

Keep the AP reference kernel unchanged. Add an actor-owned, read-only interest query and a narrow
engine guard for run-1 FLOW plus a concrete premise/literal mark. Read current policies, including
fresh pending interest, and conservative installed whole-chain catalog coverage. A committed
snapshot alone is insufficient. Equal-looking deep/EXACT catalog queries do not prove coverage.

Apply the guard after operand/type filters, complete form validation and required source/action
bookkeeping. Cover ordinary edges, unary and multi-input source checks, NNF sinks and legacy
conjunction checks separately. Never skip a whole source, sink, statement, call plan or FLOW
entity. Earlier TAINT inputs and later concrete joins remain live. A skipped FLOW gate yields
no fact and no proof; it never becomes Holds.

Exclude F75 and other cleaner/residual operations, TAINT, REACH, abstract-mark transfers,
structural emission and supplier cursors. Keep owner/cancellation checks and counted pending
work. Existing interest must continue to process current and future suppliers/new callers.

Compare complete facts, actions, witnesses and demand, including interest admitted before its
first barrier, reentrant/late suppliers, fresh runs, mark exclusions and transformed static
origins. Count skips separately from admission operations and mark memberships. A skipped
geometry check does not prove that its old path would have overlapped or sent a request.
The added lookup can cost more than a shallow rejected check; selection needs fresh evidence.

## Union catalog marks for canonical static-root FLOW climbs

| Current | Proposed | Why it can help |
| --- | --- | --- |
| A canonical root FLOW supplier visits each catalog query's marks and merges duplicate climbs later | Visit one shared union of the qualifying catalog marks | Avoid repeated member scans and duplicate target unions |

Limit this path to STATIC FLOW with one nonempty root leaf, no children and an empty tail
exclusion, within one full raw-link reference/group. Every static query overlaps this supplier;
the climb target remains that reference's original caller initial and complete MethodKey.
The mark-exclusion law is `union(M_i minus mx) = union(M_i) minus mx`. Include the existing
base-wide cleaner marks. Keep the current per-mark coverage decision before sending.

Build a sparse shared actual-mark union per component. Charge its nodes and retained lifetime
to the catalog. Do not flatten it into actors or introduce a Cartesian product. All noncanonical
FLOW shapes and every TAINT answer retain the complete geometry/role kernel. This does not
justify suppressing a climb merely because initial and final static positions look equal.

Preserve the wide marked-ancestor/excluded-field route through a different read position and
subsequent copy. Exact/deep catalog interests alone cannot replace that route. Test distinct
original initials, layers, mx, exclusions, late links, source/sink checks and complete joins.
Measure qualifying single-root/empty-E suppliers and query-member count versus unique union
members per full raw reference. Separate catalog, dynamic and cleaner contributions. All climb
actions are not qualifying canonical suppliers. The union itself consumes memory, so do not
infer a retained-memory reduction from fewer scans.

## Suppress climbs already owned by the exact target policy

| Current | Proposed | Why it can help |
| --- | --- | --- |
| Outgoing climbs check static catalog coverage, then send sets that the target can reject as already owned dynamic interest | Also test the exact target's current admitted policy before constructing or sending duplicate work | Avoid some outgoing unions, snapshots, events and receiver duplicate admissions |

The key is the full target MethodKey, original interned InitialAp and concrete mark. An admitted
policy stays responsible for old and late raw suppliers. Repeating its membership admission has
no fact or proof effect. Preserve raw caller links, layers and original premises. Never infer
coverage from another context, a descendant, geometric overlap or an already emitted fact.

Read current policies, including admitted interest awaiting supplier processing. A committed
phase root can be older. Unsent builders and queued events are not admitted ownership and
cannot justify suppression. A positive check returns only coverage; it cannot satisfy a source
or sink condition, create a concrete initial or complete pending work.

Start with a noncreating same-unit owner peek. Missing actors return false and retain the
original route. Do not use an actor-creating accessor. Cross-unit worker maps are mutable and
cannot be read remotely. A later extension needs a safely published run-local method-to-read-
handle directory and a latest immutable policy root. This is routing metadata over the existing
policy, not a second request-membership index. Publish only admitted policy with secured pending
work, and invalidate after joined release on success or failure. Never reuse handles across runs.

The check placement determines the saving. A per-climb check can avoid source unions but follows
geometry and member traversal. A send-time check can avoid some snapshots/events but leaves
earlier union work. Neither removes the preceding member scans. Resolving or locking a global
directory on each of ThingsBoard's 319,832,979 climbs can cost more than it saves. Resolve a
target once per bounded group/chunk, preserve cancellation and drop borrowed roots promptly.
Do not retain old target policy snapshots in source phases or callbacks.

The fresh duplicate-union and receiver duplicate-member counters have different units and
lifetimes. They cannot predict an exact saving. Measure same-unit/cross-unit opportunities,
dynamic coverage hits, skipped members, avoided batches, lookup cost, builder/snapshot work and
remaining scans separately. Keep static-profile hits distinct. Two concurrent senders may both
see a miss and send; target deduplication remains required.

Validate fresh-but-uncommitted interest, existing and late links, transformed static origins,
F75, complete MethodKey/context separation, E/mx and NORMAL/DEMAND. Include quantum 1,
reentrant callbacks, delayed messages, cancellation, timeout, release races and missing actors.
Compare full scalar facts, scoped proofs/witnesses, publications, summaries, handoff seeds and
reports. No positive read may suppress unowned work or serve as an accepted-send reservation.
The source audit is `report-preparation/dynamic-target-coverage-source-review` in the current
measurement batch. This candidate remains unselected; its retained-memory benefit is unmeasured.

## Share transfers across concrete initials

| Current | Possible change | Why it can help |
| --- | --- | --- |
| Distinct concrete marks at one initial geometry start separate premise-indexed propagation | Process a group of independent origins through a transfer block that does not inspect or change marks | Share geometry walks, dispatch and intermediate tree work across those origins |
| Packed `Facts` shares output marks within one premise; method actors, graphs and forms are already shared | Keep an explicit original-initial-to-result relation across the grouped block | Share execution without inventing crossed outputs or merging independent premises |

This is a broader algorithmic option, not a further request-queue change. The current
`Run1Abstraction` batches interest and supplier selection but emits scalar concrete initials.
Each newly admitted initial starts its own edge propagation. Shared actors and forms must not
be counted as a new saving.

The supplementary run-1 guard census finds 532,543 admitted initial memberships across 73,669
actors. The median is one, nearest-rank p95 is 53 and maximum is 437; 39,179 actors have one.
It does not classify their concrete marks or equal geometry. Initial-store memberships
and method-edge premise rows also have different key domains: edge rows can include a
`PremiseSet`. Do not derive concrete-initial count by subtracting REACH rows or compare those
domains as if they were equal.

The later [added-fact census](../reports/bidirectional-run1-fact-explosion-20261015.md)
classifies the original caller premises of the largest incoming store, a different domain.
It finds 86.30% FLOW rows with abstract premises. Its concrete static premises have at most
five marks per equal path/tail/E geometry. This does not establish a dominant concrete-mark
repetition problem there, or classify all actor-initial cohorts. Keep this candidate
unselected; equal payload sharing and static origin fan-in require their own designs.

Within one premise, method storage already subtracts known content and returns a new-leaf
delta when its annotations are unchanged. Annotation changes can require the merged value
under the existing T4 rule. The method worklist then keeps those deltas separately; unchanged
paths use a burst-scoped repeat set. Thus a newly arriving mark does not always replay the
entire accumulated fact. The candidate targets repeated work across distinct concrete
initials, which existing within-premise delta storage cannot combine. A broad worklist rewrite
remains deferred. The current source binding is
`report-preparation/intramethod-delta-source-review.json` in the measurement batch.

A first design can limit grouping to proven mark-neutral transfer blocks. Group only equal full
MethodKeys and compatible entry/conclusion geometry, kind, layer, exclusions and stage state.
Preserve each original `InitialAp`, its identity eligibility and pending demotion. Project the
exact scalar identities back to the existing rule, call, summary and exit interfaces.

Keep the relation explicit. If `iT` supports `A` and `iU` supports `B`, a union tree attached to
both origins would invent `iT -> B` and `iU -> A`. An AND `PremiseSet` would change the two
independent edges into a joint requirement. Neither representation is sufficient. Equal whole
results can share one value only while every listed origin actually has that whole result.

Keep source and sink NNF proof choices, repeated-leaf bindings and valid AND products at scalar
boundaries. Preserve same-call support, complete caller references, mark-specific type policies,
F75, NORMAL/DEMAND, E/mx and native RETURNED behavior. Late suppliers add fresh origin/result
relations even when an output mark was already seen. Project scalar summaries under each
original premise before F1/B2/F3 handoffs and record eligibility checks. Preserve the full
`(MethodKey, statement, original forward PathEdge)` identity for source hits and `Seed.Source`
admission.

Before selection, measure admitted concrete initials per actor and complete geometry excluding
only mark, relation density and the length of common transfer blocks. Global canonical initial
counts and output-tree mark width do not measure repeated entry propagation. Compare full scalar
closures, summaries, witnesses, callers, records and seeds on small independent oracles.
If groups usually contain one origin or split early, the relation overhead can exceed the gain.
The bounded first design still stores scalar boundary identities; it does not claim to remove
their retained memory. Source shows where repetition can occur, not its measured frequency or
savings. This option remains unselected until fresh evidence supports its greater design cost.

## Possible later reference/cache work

Managed soft references are already used for method-form payloads. Further reference-manager
or weak-identity-cache changes are separate candidates, not hidden parts of the options above.

| Current or possible issue | Possible change | Evidence required |
| --- | --- | --- |
| Completed alias results and their conversion memo are held by strong physical-method owners | Consider one reclaimable result-plus-memo payload under the existing managed-reference API | Actual result/DSU/memo ownership, active pins and stable rebuild behavior under the alias time limit |
| The manager's registry can keep wrappers whose referents GC has cleared until explicit cleanup | Design removal of cleared wrappers while preserving cleanup/enable/create semantics | Guard-heap wrapper/queue retained bytes and lifetime, not allocation counts alone |
| Temporary identity keys could be retained by a proposed shared memo | Consider a weak-identity memo only with explicit ownership and reference-queue rules | Actual retained key/value chains, hit rate, rebuild cost and absence of value-to-key retention |
| Iteration teardown clears managed form payloads before later runs | Consider phase-aware form retention under the existing memory-pressure policy | Attributable rebuild work, peak live cache cost, stable IDs/support after reclaim/rebuild |

The alias option does not require a reference-manager rewrite. Its payload must own both
`AnalysisResult` and `convertedAliases`: conversion keys use IDs from that result's manager.
A scoped callback pins one generation for compound before/after queries; no cached closure
may retain the borrowed payload. A separate nonforcing borrow must cover the complete prescan
metadata collection, including after its cancellation token is cancelled. Keep the method
recipe and its separately strong graph/reachability owners out of any claimed reclaimable bytes.

The alias computation has a fresh 10-second time limit today and caches a completed null.
Reclamation must preserve that sticky null and distinguish it from a reclaimed success.
A successful rebuild that times out cannot silently replace the previous successful relation
with unknown answers. Define this failure policy and prove stable observable alias, call-target
and signed-condition results before selecting this option. With caching disabled, hold the
computed result only through its callback; do not add a strong fallback. Clear/rebuild,
concurrent generation, compound query, cancellation and nonforcing-metadata controls are required.
The source-bound feasibility review is
`report-preparation/alias-managed-payload-source-review` in the current batch. No implementation,
retained saving or run-1 benefit is claimed.

Each option needs its own concurrency, cancellation, lifetime and reclaim design before
selection. Preserving cache data can increase peak memory. Reducing later-run rebuilds does
not establish a run-1 benefit. Do not rewrite the current manager or add `WeakIdentityCache`
in this iteration. Keep these possibilities visible for a later evidence-based choice.

## Final selection gate

Use the memory order above with the actual container/initial/cache/input census and its stated
limits. Keep each
result's reached, completed and accepted run, its OOM/timeout phase and its finding limits.
Keep the named command-route cut, its overwrite contract and incomplete lane limits in the
correctness review. The next iteration follows the new callee added-store removal design
and the minimal discarded normal-sink cache retirement. Other choices need a separate selection. This
document does not authorize hidden cache, reference-manager, IR or queue changes. Every chosen
implementation must retain the additive abstract/concrete emission rule and repeat the
fixed-project checks.
