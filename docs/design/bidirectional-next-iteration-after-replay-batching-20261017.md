# Next iteration after static need replay batching

Updated: 18 October 2026. Status: **Measurement-grounded next-iteration proposal.
Conductor and PDF tools are closed; ThingsBoard has only 15 preserved tables
after operator termination. The priority below uses this bounded evidence.
No optimization in this document is selected or implemented.**

The measured candidate is `f18b1c5bb58e5856e4b15b3a71e09e3708dff0d1`.
Fresh main is `f945f92c5ae554ff4f5fc7696ee5a269210478bb`, with the reviewed
MemoryManager capture patch. The fixed eight-invocation study used the same
rules and project inputs. Both source endpoints are stable. The after-input
audit passed all 80 checks for 41 datasets; before/after dataset manifests
are byte-identical. These bindings establish the study inputs, not project
acceptance or a completed result for an interrupted engine.

The current [project report](../reports/bidirectional-static-need-replay-batching-20261017.md)
controls finding comparisons and resource outcomes. The implemented
[batching contract](../../spec/run1-static-need-replay-batching.md) remains
the semantic reference for this proposal. The previous
[next-iteration ranking](bidirectional-next-iteration-after-concrete-statics-20261017.md)
uses another runtime; its retained-byte estimates are not current evidence.

## 1. Current result and scope

| Project | Fresh main command wall, status | Candidate command wall, status | Latest reached / completed / accepted forward | Candidate F1 engine |
| --- | --- | --- | --- | --- |
| OWASP | 119.242 s, OK | 1039.669 s, TIMEOUT | run-7 / run-6 / run-5 | 268.371835970 s, complete |
| conductor | 91.178 s, OK | 847.446 s, OOM | run-1 / none / none | 774.692972714 s, interrupted |
| ThingsBoard | 211.378 s, OK | 405.958 s, OOM | run-1 / none / none | 193.874724150 s, interrupted |
| Stirling-PDF | 47.103 s, OK | 309.591 s, OOM | run-2 / run-1 / run-1 | 98.154731720 s, complete |

Command wall includes setup, prescan, engine work and reporting. It is not
an engine-only comparison. Conductor's failed F1 includes a 16.304 s heap
dump; ThingsBoard's includes a 28.215 s dump. PDF failed in B2, engine
156.269519859 s, including an 18.135 s dump. Do not compare its later heap
population to F1 as if the owner lifetimes were the same. No project meets
the 60-second F1 target. The candidate outcomes above remain failures.

The closed evidence root is:

```text
P = /drive-testcomp/opentaint-go-rules/static-need-replay-batching-20261017
P/runtime-measurements.json
P/input-audit-after.json
P/input-audit-comparison.json
P/cpu-review/all-closed-derived/{summary.json,README.md,receipt.json}
P/cpu-review/{owasp,conductor,thingsboard,pdf}-closed-native/
P/reviews/observer-key-proposal-review.json
P/reviews/observer-disabled-packed-proof-addendum.json
```

The combined CPU receipt is
`94d4b6195db90d9623f224a86a71933ad6ca2bed435e249819c62eadb1223c1d`.
The after-input receipt is
`904f1cb14dcb6770c8a33345bfdfc35d057e6997cdc5a0acf9e89f00d4a6ab31`.
The source-bound primitive files give exact times, source hashes, sample
counts and limitations. No new recording or analyzer run was added for
this proposal.

All four nominal normal windows fall within F1. Each window requested 30 s
after full-analysis start plus 60 s. The exact async attach delay and final
sample time are unavailable. `observed_closed_utc` is the harness wait
timestamp. It is not the actual final sample time.

| Current normal CPU window | Parsed samples | GC stack union | Cleaner-chain stack union | Static replay or route stack union |
| --- | ---: | ---: | ---: | ---: |
| OWASP, complete F1 | 8,082 | 60.17% | 31.96% | 0% |
| conductor, F1 OOM prefix | 31,153 | 51.27% | 0.14% | 24.53% |
| ThingsBoard, F1 OOM prefix | 32,566 | 36.57% | 0.70% | 34.90% |
| PDF, complete F1 | 27,807 | 34.15% | 1.69% | 16.01% |

These are exact per-stack unions over the recorded name patterns, with
zero malformed lines. The families overlap. They are not independent or
removable CPU shares, and GC CPU does not identify an allocation owner.
Native stack-truncation flags are unavailable. There are no main native
windows, so this table does not measure main-versus-candidate CPU shares.
OWASP's near-limit window spans B6, its barrier and F7; it is not F1.

### 1.1 Expected run-1 memory priority

The original ThingsBoard tool was stopped by the operator after its final
Q54 instance-field collection had remained unfinished for more than ten
minutes. TERM was sent at 00:02:09 UTC; Java and wrapper closed by 00:03:26,
with Java exit 143 and wrapper exit 4. The actual processes are absent,
the capture is unchanged and source/input post-check errors are empty.
This is operator termination, not a successful tool completion. It does
not establish an analyzer or algorithmic cause for the query delay.

MAT shutdown removed the temporary report, and no final ZIP survives.
Only **15 tables copied before shutdown** remain available. The other
**38 tables are unavailable**, and **Q54 was interrupted**. In particular,
do not treat all 53 prior queries as usable or claim complete 54-query
acceptance. No rerun was made. All ThingsBoard owner sizes and physical
counts cited below are in the preserved copies. They permit this limited
comparison. The copied evidence is bound in:

```text
P/heap-tools/jobs/job-thingsboard-20261017T233522.874686Z/
  partial-owner-evidence/evidence.json
    SHA256 2a6bfc78b6834b239a26f3988f62e1ef7e88d75f80845373104914cb9b2bdce4
  partial-owner-evidence/additional-owner-evidence.json
    SHA256 4833b4a952dfe46f140aadffa7cf0e506fe88a59839729527e48d071957fe6f9
P/heap-tools/jobs/job-thingsboard-20261017T233522.874686Z/
  limited-query-outcomes.json
    SHA256 2b4d1294b23b33b06baaca130a596952b0d3d2f64d6f52093dd7d14ed2e001bd
P/reviews/thingsboard-limited-owner-root-review.json
  SHA256 95dc30360258661f6e015b8df887de4160c6e1bdc76d667e9dcfefce989aa9fc
```

The order below is expected memory opportunity for **run-1**, with explicit
confidence limits. It is not a rigid order of guaranteed savings or of
implementation steps. All sizes are decimal bytes/GB. The retained owner
unions overlap; their rows must not be added.

| Priority | Candidate | Current measured target scope | Expected effect and limit |
| --- | --- | --- | --- |
| 1 | Combine overlapping graph replays into counted cohorts | ThingsBoard Q34 wave marks/visited union **3,258,710,824 B**; Q36 static-event owner union **4,075,308,816 B** and Q39 all queues **3,554,709,312 B** overlap it. Conductor wave and queue scopes are approximately **0.403 / 0.676 GB** | Avoid repeated traversals and their child fan-out, rather than only shrinking each object. The large current population plus routing CPU gives the strongest run-1 target. Exact cross-cohort overlap, retained reduction and completed-work gain still need proof and measurement. |
| 2 | Make reproducible normal bound sink-cache values reclaimable through the existing manager | ThingsBoard Q42 **1,092,022,048 B**; conductor Q42 **320,733,824 B** | The normal CALL/ENTRY/EXIT sink-cache scope is directly measured in both run-1 captures. Only exactly rebuildable values can be reclaimed; shared roots, strong keys, enabled-manager policy and rebuild CPU can limit benefit. This is a separate representation/lifetime change, not a traversal reduction. |
| 3 | Do not allocate or scalarize for the disabled unresolved-call observer | Conductor Q48 **508,036,464 B**; ThingsBoard Q48 **49,484,624 B** | All four measured adapters have null trackers. Remove unused auxiliary history and observation-only scalarization, with the two proof parts below. This is algorithmic work avoidance and a small independent candidate to validate early. The scope is larger than the cache in conductor but much smaller in ThingsBoard; no exact future saving is promised. |
| 4 | Give Work fair service and combine delivered Base admission | High all-producer P-duplicate counts; **Base-only queued retained bytes are unmeasured** | Can reduce delivery copies and admission work and let ordinary Work advance. The full queue scopes above mostly target the same routing path and are not an independent byte estimate for this item. Local cleaner/rule offers are not removed by message combining. |

Algorithmic avoidance comes first because it removes the repeated graph
work and related owners. The larger measured cache scope remains ahead of
the smaller ThingsBoard observation scope in this cross-project memory
order. The simple disabled-observer change can still be validated as an
independent early step. Do not turn that implementation convenience into
a guaranteed saving rank.

The exported ThingsBoard reachable shallow total is **11,414,128,752 B**.
Its full histogram has **172,785** physical waves, **19,145,155** StaticNeed
event objects and **9,170,898** visit objects. These are snapshot populations,
not work-owner telemetry, exact queue occupancy or logical route counts.
The authoritative source map has only **1,349 rows / 292,016 B** in its
selected owner union; ordinary R has **5,321,696 B**. The large target is
temporary replay work, not a large retained static mark catalog.

Other current selected unions provide context: conclusion groups
301,325,256 B, summary stores 183,913,432 B, and actors/caller owners
808,527,728 B. They overlap each other and other roots. The IR owner scope
1,620,144,632 B is context only; IR storage remains outside this proposal.
No smaller owner should be promoted using another runtime's old size.

OWASP cleaner/path/type work is a separate CPU target because its complete
F1 has no static replay activity. PDF's B2 owner evidence gives a separate
later-run investigation in section 3. It is not inserted into this run-1
memory order as if its owners were still live F1 routing state.

## 2. Lead candidate: combine overlapping graph replays into counted cohorts

This is the first candidate to specify and prove. Its priority comes from
current work, CPU and run-1 owner observations, not guaranteed byte savings.
It applies only to run-1 static need routing. It does not change actual
TAINT transport, FLOW transfers, mark producers, source commits, ordinary
Base requests, summaries, normal-record eligibility or later iterations.

### 2.1 Current behavior and residual work

`UnitRunner` flushes `PendingNeedReplay` after each event or Work quantum,
before it releases or requeues that event's reservation. A dirty turn
selects all committed source rows. A source-only turn selects its fresh
source deltas. Exact equal mark sets share one fresh wave inside that
selection. Other owner turns can start further waves before those waves
finish. Each flush has new visited sets.

The current local batching works. It does not combine these overlapping
selections across owners or turns:

| F1 counter | conductor, interrupted | ThingsBoard, interrupted | PDF, complete |
| --- | ---: | ---: | ---: |
| Fresh relevant binding component notifications | 237,746 | 141,831 | 113,105 |
| Full source selections | 59,412 | 8,077 | 25,484 |
| Delta selections | 949 | 879 | 456 |
| Selected source rows; all remain selected roots | 46,785,863 | 4,857,642 | 13,277,515 |
| Created exact-mark groups / waves | 4,769,287 | 179,279 | 1,208,397 |
| Entered static nodes | 333,253,162 | 55,925,501 | 30,678,399 |
| Accepted subscriber deliveries | 407,180,714 | 139,964,426 | 33,254,234 |
| Ordinary plus packed binding scans | 1,573,274,939 | 147,322,363 | 67,439,219 |
| Maximum active work-owned waves | 236,122 | 172,776 | 7,986 |
| Grouping elapsed time, summed over workers | 10.407749766 s | 0.643370451 s | 0.927419243 s |

The complete PDF cohort has `waves == groups` and `roots == selectedRows`,
with zero active, abandoned or unbalanced work ownership at closure. Its
113,105 binding notifications produce 25,484 full selections: a 77.47%
reduction in this local selection unit. Grouping creates 90.90% fewer
waves than one wave per selected row. It still selects all 13,277,515
roots. These local comparisons do not measure the old runtime's live
schedule, time saved or bytes saved.

Conductor and ThingsBoard are interrupted prefixes. Their offer/delivery
gaps cannot be called completed duplicate suppression. The work-owned
wave maximum is not a maximum of reachable JVM objects. Joined clear
abandons 102,711 and 172,776 active ownerships respectively, then resets
the current gauge. The heap review must establish current retained owners.

The current Conductor capture now supplies a narrower memory context:
approximately **0.403 GB** in the wave-related retained owner union and
**0.676 GB** in the queue owner union. These scopes overlap and must not be
added. They are current capture populations, not peak retained memory or
promised savings. The old runtime's multi-GB wave scope must not be used as
the current target. The measured unresolved-observation owner below is a
separate candidate that also needs an owner-overlap check.

The preserved ThingsBoard Q34 gives a much larger current
wave marks/visited scope, 3,258,710,824 B. Its Q36 static-event union and
Q39 queue union overlap this scope. Section 1.1 records the limited
comparison and the operator-stopped tool's restricted 15-table scope.
This is current evidence for repeated temporary work, not a reuse of the
old runtime's multi-GB estimate or a complete report archive.

The ThingsBoard normal profile has 20.56% of samples in the intersection
of the named static route/replay union and monitor helpers. Saved stacks
include 1,231 samples ending in `RunManager.accept` and 614 ending in
`RunManager.summaryStorage`, under routing fan-out. Both source entrypoints
use the shared admission monitor. These witnesses do not assign the whole
20.56% to either lock or to the wave's visited lock. Reduce unnecessary
replay and child admission before a lock-only change.

### 2.2 Proposed behavior

Use one run-scoped replay coordinator. At most one **cohort** can own
routing work. A cohort contains the separate waves for its exact nonempty
mark-set groups; it is not one mark union for all starts. Route visits
remain parallel through the existing caller owners.

While a cohort is active, combine new graph-growth flags and committed
source deltas into the next pending batch. When its routing owners finish,
select and dispatch that batch with fresh visited state. If the graph grew,
select a current atomic snapshot of all committed source rows; otherwise
select only the pending source deltas. Source values remain immutable.

```kotlin
// Design types, not an additional binding graph or request store.
data class ReplayUpdate(
    val graphGrew: Boolean,
    val freshSources: Map<StaticNeedKey, RequestMarkSet>,
)

class ReplayCoordinator {
    private var active: Cohort? = null
    // True while a dispatch is queued or selects/creates the active cohort.
    private var ready: Boolean = false
    private var graphGrew = false
    private var sourceDeltas = LinkedHashMap<StaticNeedKey, RequestMarkSet>()
    // One real run obligation covers ready, active, and the next pending batch.
    // Exact admission/release uses the existing counted owner protocol.
}

// Existing owner turn; actual directory/source state is already visible.
val update = pendingNeedReplay.takeAndReset()
coordinator.offerCounted(update, executingParent)
releaseParentOrRequeueWork()
```

`offerCounted` must finish its ownership handoff before the executing
parent can be released. An idle coordinator secures a counted dispatch.
An active or ready coordinator merges the update into its pending batch.
Its existing run obligation then covers that pending work. The merge keeps
one exact mark union per changed source, plus one graph flag. Empty updates
do not acquire a new obligation.

```text
IDLE: no active cohort, no ready dispatch, no pending update

offer(nonempty update):
    register/transfer real counted work before the parent is released
    union update into pending
    if IDLE, queue one ready dispatch

ready dispatch:
    detach pending under coordinator ownership
    keep ready true until the active root-dispatch owner is installed
    release coordinator lock
    select full source snapshot if graphGrew, else detached deltas
    group by exact nonempty mark-set contents
    install active cohort and clear ready in one ownership transition
    keep root-dispatch ownership during fan-out; settle an empty selection too

last routing owner finishes:
    under coordinator ownership, clear the active cohort
    if pending is nonempty, keep the run obligation and queue next dispatch
    otherwise close it; a concurrent later offer secures a new obligation
```

The code must define a fixed lock order. Do not hold the coordinator lock
while snapshotting sources, comparing mark contents, calling subscribers
or posting child work. A source committed after batch detach can occur in
both the selected full snapshot and the next pending delta. That duplicate
is permitted. Dropping the pending delta is not permitted. There is no
global graph snapshot: visits read the existing live binding directories.

No permanent mark row is added at a relay. No binding graph is copied.
There is no cache of completed visited sets. The authoritative static need
map remains `StaticNeedRouter.sources`. Method-empty source commits do not
move to the replay-cohort boundary.

### 2.3 Routing completion is separate from Base method work

A cohort needs an always-enabled semantic child count. The present
optional `StaticNeedWave.workOwners` is telemetry and does not control
admission or completion. It cannot become the semantic token without a
new invariant and tests. Execution with statistics disabled must use the
same semantic ownership protocol.

Count root dispatch, deferred subscriber notification and accepted queued
static visits before their parent is released. Rejected or failed child
admission must follow the existing failure protocol. A successful post
transfers ownership even if the sender's next cancellation checkpoint fails.

At a Base endpoint, keep the existing counted `MarkRequestsIn` delivery.
The routing owner finishes after this normal run reservation is secured;
it **does not wait** for the Base actor to run, reach method-empty, commit P
or finish ordinary Work. That Base work remains counted by the run. If it
later creates a static source delta, it offers a new update to the coordinator.

```kotlin
fun routeToBase(target: BaseEndpoint, marks: RequestMarkSet) {
    val fresh = marks.minus(target.committedR())
    if (!fresh.isEmpty) {
        // Existing run admission secures the child before this visit ends.
        postCounted(MarkRequestsIn(target.fullMethodKey, target.base, fresh))
    }
    // The cohort does not await the target's P commit or method Work.
}
```

Do not count Base method processing as a cohort prerequisite. Otherwise a
cohort can wait for method-empty work whose own source commit is waiting
for the cohort. Do not wait for whole-run idle to dispatch the next batch.
The final routing child must transfer the coordinator obligation to its
next ready dispatch before releasing the last old obligation.

Cancellation does not turn pending work into a successful closure. Joined
release clears pending state and reports abandoned work ownership. It must
not trigger new replay from a `finally` block after admission is closed.

### 2.4 Required constructive proofs and runtime checks

The reference result is the logical Base endpoint/mark candidate relation
**before committed-R subtraction**. Actual deliveries can omit already-R
members; final R can contain unrelated ordinary Base interests. Do not
state equality of raw delivery counts or `final R = static candidates`.

Add a finite event model and a focused audit under
[the proof guide](../../spec/proof-guide.md). It must prove:

1. Source unions and pending same-source unions are exact; only fresh source
   deltas are required, and full-source selection covers detached deltas.
2. Each emitted candidate has a real source/mark and a finite path through
   registered actual FLOW bindings. Full method, entry/context and original
   slot remain attached to every routing edge. A request never proves T.
3. Each required endpoint has a path witness. Use the latest source/edge
   insertion on that path: its counted update is processed by a cohort that
   sees the insertion, or remains pending for a later fresh cohort. A node
   visited before a late outgoing edge does not suppress that later cohort.
4. An update racing with last-child completion is in the active selection
   or the next pending batch. There is no interval with pending work but no
   parent/coordinator/run obligation. Accepted children precede release.
5. Exact equal-mark multi-start traversal preserves the per-source candidate
   union. Different sets do not form a start-times-marks cross product.
   Fresh cohorts never share mutable visited sets.
6. Base admission preserves its own run reservation without extending the
   routing dependency to P commit or whole-method completion. Source reentry
   remains covered even if it occurs after the cohort becomes idle.
7. On a finite grow-only input trace, fair service drains the pending updates
   and yields the reference candidate closure. State fairness explicitly;
   do not prove it by assuming the target closure has already been emitted.
8. Statistics do not affect that transition system. Disabled statistics,
   deferred callbacks, rejected posts, cancellation and joined clear have
   the same semantic obligations.

Reuse checked local laws in `ApSpec.StaticNeedReplayBatch` where their
premises apply: `grouped_exact`, `same_source_union`, `fresh_delta_exact`,
`committed_filter_stutter`, and fixed-graph cost laws. Its serialized
`trace_flush_exact` does not prove the new concurrent cohort handoff.
`BottomUpStaticTransport.no_early_completion` and `enqueue_before_release`
provide local accounting laws, not a proof of the Kotlin scheduler.

Runtime tests need an independent per-source/per-mark graph oracle. Include
different equal-content set objects, hash collisions, distinct marks on
disconnected starts, cycles, several owners, late edge behind visited,
source after selected snapshot, a source/last-child race, new subscribers,
same-unit and cross-unit targets, source reentry, empty sources, deferred
notifications, failed post and cancellation. Use the actual UnitRunner path
to prove pending work prevents COMPLETE and Base Work does not deadlock it.
Retain the existing differential facts, requests, findings and ND controls.

### 2.5 Cost and no-gain scope

For a fixed route graph and unchanged selected source contents, let D be
the number of distinct nonempty mark sets. A cohort creates at most D waves.
Their visited sets count only static nodes; Base delivery attempts are not
globally deduplicated by this claim. Several overlapping graph notifications
can require the current cohort plus one next cohort instead of one new
cohort per turn. A finite shared-route example must prove a strict reduction
in wave creation and weighted static scans.

Do not claim universal work nonincrease for arbitrary live schedules.
Source content growth can change equality groups; new graph routes change
scan cost. The coordinator adds synchronization, mark-union work, grouping
and scheduling delay. Exact grouping costs include each actual content
comparison times the cells examined; collision cases must be charged.
If each replay finishes before the next update, there can be no batching
gain and the coordinator can add overhead. Source-only updates with disjoint
routes and different sets can also give little sharing. Measure these cases.

No byte saving or 60-second result follows from the wave bound. Completing
more analysis can expose new owners after the current OOM boundary.

## 3. Other candidates, with separate targets

Section 1.1 sets expected run-1 memory priority. The descriptions below
also cover separate CPU and later-run targets. Their owner scopes can
overlap the lead candidate and must not be added as savings.

### Avoid inactive unresolved-call observation; project enabled observations explicitly

**Current:** in run-1, `RunMethodCallServices.observeUnresolved` retains
TAINT deltas in an `AddedFactAccumulator` called `observed`. This is used
only to decide whether to call the unresolved observer again. Its key and
payload retain caller original premises, layers, headers and full AP facts.
The current Conductor Q48 owner union is **508,036,464 B**. It is larger
than the current Q42 bound-rule cache union, **320,733,824 B**. These are
nonadditive owner scopes; neither number is a net saving estimate.
The preserved current ThingsBoard Q48 scope is **49,484,624 B**. It is one
of the 15 surviving tables; the tool was operator-stopped and has no final
report archive.

The JVM observer consumes much less information. At
`JIRInterpreter.reached`, it ignores ClassStatic and always-ignored methods.
For other positions it passes the callee method/signature, the position,
and `position in plan.passReads` to `ExternalMethodTracker`. The tracker
stores method/signature identities, a set of positions, the OR of the
pass-rule flag, and a diagnostic `callSites` count. Full marks, AP suffixes,
original premises and fact layers do not enter these semantic records.
The user permits the diagnostic count to vary with batching.

All four actual candidate invocations omit `--track-external-methods`.
The source-bound review checks their manifests, init scripts and actual
process arguments; there is no environment or property override. The option
defaults to false. `ProjectAnalyzer` supplies a null tracker, carried unchanged
to each `JIRInterpreter`. This is a command/source proof of the configuration,
not a heap-field census.

However, `JIRInterpreter` still implements `UnresolvedCallObserver`, and
`SharedObjects` returns that nonnull observer. The accumulator and callback
path still run before `reached` returns immediately at its null-tracker
check. Thus all four measured configurations pay for an apparently active
observer that produces no external-method observation result. Conductor's
508,036,464 B Q48 union is the collective retained scope of this unused
tracking state. It includes its payload and containers, not 14.6 million
logical observations or a promised 508 MB saving.

**Primary change:** let the adapter explicitly declare observation disabled
when its tracker is null. Expose no active observer in that case, and
allocate `observed` only for run-1 with an active observer. Existing generic
observers remain enabled by default; do not infer their policy from the JVM
adapter or remove their callbacks.

```kotlin
// Sketch: an explicit capability with a legacy-enabled default.
interface ObservationCapability {
    val enabled: Boolean get() = true
}

// The JVM adapter declares enabled = (externalMethodTracker != null).
val activeObserver = configuredObserver?.takeIf {
    (it as? ObservationCapability)?.enabled != false
}
val observed = if (run1 && activeObserver != null) AddedFactAccumulator(...) else null
```

Choose the exact adapter API before implementation; the sketch is not a
second observer or an inference from a command-line string inside the core.
`SharedObjects.unresolvedObserver` must be null for the disabled capability.
Then the existing `flowConcreteStatic` nonnull-observer guard also avoids
unpacking all static entries solely for observation. Actual unresolved/model
transfer stages still run through the existing packed and ordinary paths.
This last claim needs a separate adapter proof: removing the guard at
`RunMethodCallServices.kt:106–111` changes which walker evaluates the
remaining stages. The fact that the callback does nothing does not prove
those two walkers equivalent.

The primary change has two proof parts. First, erase the callback and its
private dedup state while retaining the same transfer path. This is a local
no-op argument for the immutable null-tracker adapter. Second, prove that
avoiding observation-only scalarization preserves every remaining actual
stage under scalar decode. This adapter law is **required and not yet proved**.
The primary change needs no new enabled-observer projection contract, but
it needs both of these proof parts. Store removal with the old scalarization
can be a validation checkpoint; it is not a silently reduced final scope.

**Separate extension for enabled observation:** retain a small semantic
key at the existing actual observation point, only if the observer opts in
to a proved projection law. Require an actual nonempty TAINT carrier that
reaches the UNRESOLVED stage. The current `TaintTree` constructor enforces
nonempty roots; preserve that carrier invariant. Do not infer an observation
from a built call plan, ZERO, a mark request or an input killed before that
stage.

```kotlin
// Sketch for an observer that explicitly supports projection.
data class ObservationKey(
    val caller: MethodKey,
    val call: CommonInst,
    val position: AccessPathBase,
    val category: StableObservationCategory,
)

// JVM category must distinguish at least the passReads result.
// It must not use the identity of a rebuildable CallPlan object.
val key = observer.project(actualCall, actualPosition, currentPlan) ?: return
if (observedKeys.add(ObservationKey(fullCaller, node, actualPosition, key))) {
    observer.reached(actualCall, actualPosition, currentPlan)
}
```

This changes auxiliary observation state, not the cumulative source-interest
map, ordinary requests or actual transfer facts. Keep the full method and
call-node boundary. If a later stable plan category can change the observer
result, its key must differ. In particular, an earlier false passReads flag
must not suppress a later true flag. The projection is configuration-scoped
and must be stable when a form is reclaimed and rebuilt. Do not retain the
whole plan or bound rule list through `StableObservationCategory`.

The generic `UnresolvedCallObserver` currently has no such projection
contract. An enabled key-based extension needs an explicit opt-in policy
with a proved observation law; unsupported observers keep their existing
behavior and callback granularity. The generic policy
must not silently assume that every observer ignores AP histories, call
counts or plan contents. A declared ignored position can return no key.
For this JVM observer, ClassStatic can be declared ignored. Then
`flowConcreteStatic` need not decode every diagonal static original solely
to invoke an ignored callback. All actual unresolved/model transfers,
guards, sinks and other call stages still execute with their ordinary
semantics. A different observer that uses static positions must retain its
normal evaluation path.

Why this can help: the disabled mode removes an auxiliary fact-history
accumulator, its exact hash directory, entry list and private interners,
which cannot produce any observation in this mode.
It also avoids observation-only scalarization of packed static inputs.
The enabled extension would replace full history with the required finite
observation relation. Neither change removes real TAINT evaluation or
guarantees that all 508 MB become unreachable: shared facts, AP interners,
method/node keys and other owners can retain part of the same graph. First
validate the simpler disabled mode. Measure enabled key cardinality, bytes
and avoided decodes separately; do not multiply an object count by an
assumed AP width.

Required constructive proof and adapter controls:

1. For state/callback erasure, prove the immutable null-tracker callback
   returns without effects and the private accumulator is read only to gate
   that callback. With the same transfer path, removing the accumulator must
   not select facts, satisfaction, summaries, requests or ND support.
2. For scalarization avoidance, prove a decode/effect refinement between
   the packed walker without the observer-only ADDED branch and the old
   ordinary walker. Preserve actual facts, originals, marks, E/mx, layers,
   ND support, all model/unresolved/default/action stages, guards, triggers,
   cuts, source/sink effects and requests. Include real owner-runner tests
   against an independent ordinary reference: nontrivial static clean/kill
   and field-clean writeback; changed destinations and excluded-mark effects;
   complete and missing ND support; guarded/source/sink stages; identity and
   unknown fallback; exact, broad and class suppliers; cancellation and
   counted lifetime. Existing cofactor laws help only where their premises
   cover the affected stage. No observation can replace a real return edge.
3. For enabled projection, define the observer's semantic projection. Prove the projected set gives
   the same method/signature records, fact-position sets and OR of
   passRulesApplied as all actual eligible callbacks. Diagnostic callSites
   counts, and list order derived only from those counts, are outside this
   equality; rule/source/sink occurrence identities remain unchanged.
4. Prove that merging different facts at one projected key cannot lose a
   distinct semantic observation. Cover original marks, E/mx, layers, exact
   versus broad geometry and callers with different full keys. Do not use
   `count > 0` without an actual eligible nonempty TAINT witness.
5. Prove reached-stage preservation: prefix kill, unmet guard, empty carrier,
   no UNRESOLVED stage, ZERO and FLOW cannot create an observation. Late
   genuine TAINT still creates the first required observation.
6. Prove the static ignored-position policy permits omission of only the
   callback-related unpacking. All ordinary model/unresolved transfer and
   effect results must match the independent scalar reference.
7. Test stable plan rebuilding, false-to-true passReads categories, source
   and sink occurrences, ignored methods, tracker-disabled lifetime and a
   generic observer that does not opt in. Any mutable observer policy needs
   a configuration epoch or equivalent invalidation rule. Primary disabled
   capability tests must retain the exact enabled JVM and generic new-delta
   callback behavior; optional enabled projection is a later, separate change.

This candidate is an algorithmic reduction of auxiliary history and
scalarization. Its placement relative to replay, queues and rule caches
remains open until the remaining current heaps are reviewed.

### Give ordinary Work fair service and combine Base delivery admission

**Current:** non-Work events rank ahead of non-ZERO Work in `EventQueue`.
Repeated request deliveries and local producers can offer marks already in
temporary P. Source `RunMethodAnalyzer.collectRequests` handles StaticField
separately; the counters below concern ordinary Base admission, all producers.

| F1 ordinary Base admission | Offered mark cells | Already in temporary P | P duplicate share |
| --- | ---: | ---: | ---: |
| OWASP, complete | 61,356,273 | 61,014,095 | 99.44231% |
| conductor, interrupted | 1,243,884,931 | 1,241,699,440 | 99.82430% |
| ThingsBoard, interrupted | 75,348,303 | 74,889,211 | 99.39071% |
| PDF, complete | 1,081,566 | 879,583 | 81.32495% |

Each observed partition is exact: offered = R duplicates + P duplicates +
fresh P members. A producer may be a local rule/cleaner or a delivered need.
These percentages are **not static-message duplicate fractions**. All those
paths pay collection/admission work, but message combining targets only the
delivery portion. Fair service or message combining alone does not remove
OWASP's repeated local cleaner offers.

**New:** use a bounded service rule for already eligible ordinary Work, and
one counted pending union for delivered requests at each full destination
method/slot. Keep Base and static source-reentry slots typed separately.
Drain through the ordinary destination owner, R check, P union and method-empty
commit. Do not read another actor's mutable P, commit at a turn boundary,
assert actual T from interest, or combine different contexts/original slots.

Prove exact endpoint/mark union, both input/request arrival orders, child
admission before parent release, source reentry and fair progress under an
event stream. Preserve full finding and ND observations. Distinguish local
producer attempts, delivered memberships and effective fresh P/R changes
in new measurements. First measure the residual after cohort replay.

### Reclaim reproducible bound-rule lists through the existing manager

**Current:** `TaintConfiguration.SimpleConfig.methodItems` and
`methodAllRelevantItems` strongly retain per-method bound lists. The current
Conductor Q42 cache owner union is 320,733,824 B. Its scope can overlap other
owners, and other strong roots can limit reclaim. The preserved ThingsBoard
Q42 normal bound sink-cache scope is 1,092,022,048 B. It is one of the 15
surviving tables, not proof of successful whole-tool acceptance. PDF's
separate B2 Q42 scope is 151,305,232 B. The query covers normal CALL/ENTRY/EXIT
sink caches; it does not measure every allRelevant list, source cache,
discarded-rule fraction or bound form. Historical bytes must not be
assigned to this candidate.

**New, conditional on current owner evidence:** keep the stable rule source
and method keys, but let the existing managed soft-reference facility reclaim
exactly rebuildable bound-list values. Rebuild the same ordered effective
rules, conditions, actions and occurrence identities. Keep configuration and
classpath lifetime boundaries. Cover source/sink and ENTRY/CALL/EXIT variants;
do not treat context/alias-dependent conditions as statically false.

Require exact rebuild tests, disabled-manager behavior, concurrent readers,
reclaim/rebuild cycles, diagnostic occurrence stability and a CPU/retention
tradeoff measurement. FormsCache already uses managed references where
configured; this proposal does not redo it. It changes neither IR storage
nor the reference manager and adds no general-purpose identity cache.

### Reuse analyzer cleaner/path/type work on eligible operations

**Current:** OWASP has zero run-1 static replay activity. Its normal window
has cleaner-chain31.96%, and cleaner-chain/type-geometry intersection14.15%.
`cleanChain` executes ordered read/clean/write steps; the type filters can
reach `JIRFactTypeChecker` and IR type lookup. These source paths, not a
large static request catalog, explain its separate CPU investigation.

**New:** compute compatible path/type geometry once within an analyzer-owned
evaluation, and reuse it across an eligible mark batch. Add a proved
idempotent path for a pure selected-mark cleaner when its mark is already
excluded. Preserve its required same-origin interest. Combine duplicate
mark-request construction before admission where the producer semantics
allow it, without changing authoritative source/R/P rules.

Do not skip an entire `CleanStep.Field` read/clean/write chain merely because
T is in one FLOW header. Aliases, partial reach, filtering, temporary values,
cuts and writeback can still change facts or effects. Prove the exact
eligible primitive identity, not a blanket cleaner identity. Preserve concrete
TAINT, source/sink/ND conditions, layer-only demotion and request effects.
Keep reuse bounded to the operation/configuration lifetime; it is not an
IR storage cache or a reference-manager rewrite. Measure eligible work and
request construction separately from full wrapper CPU.

### Optional PDF investigation: premise-guided concrete summary joins

PDF's F1 normal profile includes `Run1ConcreteStaticFlow.work`25.56% and
`ConcreteStaticSubscriptions.apply`16.15% as overlapping raw inclusive rows.
`apply` decodes every retained diagonal original for a non-root publication
before full matching; deliveries also loop retained call-port relations.

A possible change selects only actual concrete diagonal entries compatible
with the publication premise before that loop, then retains the ordinary
full matcher and application. Preserve exact mark/path pairing, caller and
callee full keys, call stages, class points, full premises and ND guards.
The existing default fast path remains ordinary semantics. Measure examined
versus matched originals and geometry/mark selectivity first. CPU alone
does not establish a profitable cohort or the later B2 OOM owner.

### Separate later-run investigation: persistent group and statement state

The current PDF capture is **run-2 BACKWARD, field limit 2**, after F1
completed and its report was accepted. Its original MAT process exited 0,
the capture/source binding is stable, processes are absent and the archive
exists. However, the report reader exited 5: the 54-query inventory has
**30 available CSVs, 5 CSVs with unavailable blank fields, 12 empty selections
and 7 failed queries**. This is a closed tool with explicit unavailable
results, not an all-query pass.

The seven failures are Q25, Q27, Q32, Q34, Q51, Q53 and Q54: retained-set
queries for run-1-only objects returned no result. Their byte scopes remain
unavailable, not zero. Q48 observation roots are an empty selection, also
not an independent zero-byte proof. Physical class absence, if established
by the full histogram, is a different claim from those query failures.

```text
P/heap-tools/jobs/job-Stirling-PDF-20261018T000624.748710Z/query-outcomes.json
  SHA256 b044ab0c4ab6e3a64ccc86edc317425776c0ca177d5db84a2159b323c65b0327
```

The whole reachable histogram has **237,192,751 physical objects /
7,612,375,424 B**. The following owner selections identify where to
investigate later-run memory. They overlap and must not be added:

| PDF B2 owner selection | Retained union bytes | Scope |
| --- | ---: | --- |
| Q46 combined actor and caller owners | 4,620,261,000 | Broad control and transport roots |
| Q09 conclusion groups and kind bodies | 4,230,622,232 | All physical buckets across method and summary stores, not method-only |
| Q08 method-local statement directories | 3,448,282,576 | Non-Reach method-directory roots and retained payload |
| Q11 raw/selected summary stores | 501,071,816 | Actor summary roots |
| Q14 ordinary caller inputs and index | 325,432,440 | All ordinary per-callee inputs |
| Q43 rules, joins, records and guidance | 270,132,480 | Rule, witness, native-record and guidance roots |
| Q42 normal bound sink caches | 151,305,232 | Normal CALL/ENTRY/EXIT bound sink lists |
| Q44 strong current actual queries | 33,669,576 | Later query state |
| Q39 all queues/worklists/channels | 7,724,008 | Physical queued-work owner selection |

Thus a later-run candidate must examine **persistent method statement and
group state**, not carry the run-1 replay-wave diagnosis into B2. Q09's
4.23 GB group scope is on the scale of the broader 4.62 GB actor/caller
union and overlaps Q08's 3.45 GB method state. It does not prove that all
4.23 GB is intra-procedural, redundant or removable. The IR scope
978,093,480 B is context only; no new IR design is proposed.

**Future investigation:** identify which canonical relations and consumers
require each retained method/node/original group. First check the current
T2/T4 normalization; do not assume that its surviving groups are obsolete.
Measure shared geometry and header cohorts across real originals, occupied
bucket/layer use, and actual consumers before choosing a change. A physical
group, tree or directory cell count is not a logical fact count, a distinct
path count or evidence of duplicate execution.

Prefer an algorithm that avoids repeated durable state when its exact
consumer relation can be proved unchanged. Possible representation work,
such as lazy kind/layer bodies, must retain the existing normalization and
subsumption rules and charge the replacement layout. Neither path is
selected by this report. Do not evict state needed by a CFG revisit, late
call response, source/sink condition, ND witness, backward guidance or a
current query. Do not merge original labels into an unproved Cartesian
product. Any proposed change needs a scalar decode/consumer equality,
late-arrival and cycle proofs, full-premise/ND controls, and a separate
matched B2 measurement. It has no current net-saving or time estimate.

## 4. Validation and measurement for the next selected change

Use the available owner evidence and its limits for the expected memory
priority above. Keep the current ThingsBoard limit of 15 preserved tables
explicit; the other results remain unavailable, with no rerun selected.
For each capture, record the actual run/direction, histogram coverage,
nonadditive retained owner unions and query availability. Compare routing
waves/visited state, queues and request payloads, bound rules, concrete call
carriers, facts and summary groups. Do not treat blank/empty/failed queries
as interchangeable or add overlapping owner unions. Later PDF B2 can have
no live run-1 router while retaining other run state.

Before implementation, write the selected contract and all required
constructive finite proofs. Keep scheduler/runtime refinements explicit.
After implementation, run targeted independent oracle and accounting tests,
then the full common/JVM suites, consumers and application gates. Preserve
the known strict failures as failed targets; do not weaken finding, source,
ND or occurrence assertions to pass a scheduling optimization.

Commit the tested candidate before workload measurements. Refresh main,
pin actual source/guard patch and rules/models, and repeat the same fixed
four projects with fresh before/after input audits:

| Project | Fixed model | Memory | IFDS / outer timeout |
| --- | --- | --- | --- |
| OWASP | Existing CI model | 8g | 1000 / 1200 s |
| conductor | Existing selected model, including configured extensions | 8g | 1080 / 1200 s |
| ThingsBoard | Existing selected project model | 12g | 1080 / 1200 s |
| Stirling-PDF | Existing e2e-root project model | 8g | 1080 / 1200 s |

Use `:runProjectAnalyzer` directly with the existing generated init and
qualified Go-task exclusion. Keep Java17, all fixed JVM properties, rules,
memory and timeouts unchanged. Prebuild once. Run main4 and candidate4
serially, record exact commands/source maps, and close all eight leaves
before post-input audit and heavy readers. Keep the capture-only main patch
serialized and pinned. Do not add a diagnostic workload configuration.

Compare exact vulnerability identities, locations and available paths to
fresh main, with reached/completed/accepted scopes. Missing findings from an
unfinished run remain unproven coverage. Report every engine/barrier time,
setup and prescan timing, command wall, OOM/timeout and latest progress.
Retain the 60-second F1 target as a target, not a forecast.

Keep normal requested30s after full-analysis+60s and near-limit capture where
the workload lasts that long. Bind actual marker overlaps; do not call every
normal or near-limit sample F1. Keep GC/guard intervals separate. Open heap
tools only after all workloads and audits close, under the existing locks,
idle/source/capture gates and resource limits. Preserve compact evidence
before the authorized cleanup of stale closed raw captures.

Add aggregate cohort instrumentation without per-wave history: accepted
updates, active-cohort count, next-batch union sizes, full/delta selections,
selected rows, exact groups/comparisons/compared cells, routing attempts,
node entries, subscriber posts, binding scans, Base offers/deliveries,
semantic outstanding routing owners, pending wait and handoff delay. Report
optional work gauges separately from JVM reachability. Also retain ordinary
local/delivered Base admission counts so a lower message count is not
mistaken for removal of local producer work.

Accept a cost claim only for its measured scope. A smaller prefix can look
cheap because it made less progress. A successful change must preserve
endpoint/finding semantics and improve the relevant completed work or the
explicit resource boundary. The priority uses current measured owner scopes,
not guaranteed net savings. Byte gains and target completion require the
proofs and next matched measurements.
