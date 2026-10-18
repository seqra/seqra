# Caller subscriptions: exact difference from ordinary propagation

Date: 18 October 2026. Status: design comparison; analyzer unchanged.
Latest measured conductor and ThingsBoard: **run-1 / completed none /
accepted none**, on f18b1c5bb. No new project measurement.

This is the cost comparison for the
[caller-owned proposal](bidirectional-caller-owned-request-subscriptions-20261018.md).
The [detailed spec](../../spec/run1-caller-owned-subscriptions.md) fixes the
selected boundary: canonical static descriptors pass directly; existing
broad/class normalization stays before it. References to projection below
include that existing preparation and ordinary nonstatic admission. They do
not propose another static geometry projection in the subscription.

## 1. Fix the baseline

Default means ordinary incremental analysis:

~~~text
caller retains full bound inputs
callee projects arrived inputs into canonical initials
callee deduplicates initials and evaluates its own body
caller applies existing/future callee summaries
each method merges outgoing requests before notifying its callers
fresh requests join old inputs; fresh inputs join old requests
~~~

There is no all-source replay in this baseline. Removing the regressed
StaticNeedRouter waves is not a saving over default.

For the algorithmic comparison, hold input suppliers, initial/admission policy,
layers, full call bindings, local operators and summary semantics fixed.
Replace only ordinary request routing and the input-handling protocol.
Historical 27e22/ab6 also differ in frozen coverage and static admission/layer
policy. Those differences are separate; this document does not claim that
moving input handling alone recovers their measured project performance.

## 2. What already exists

| Mechanism | Default | Caller proposal | Saving over default |
| --- | --- | --- | --- |
| Full input history needed for summary/ND joins | Caller-owned bound relation | Same relation | None |
| Canonical initial deduplication | Callee InitialFactStore | Same store | None in body evaluations |
| Summary caching | Callee SummaryStorage | Same store | None |
| Summary application | Caller subscriptions | Same full-port match | None merely from ownership |
| Mark-set request merging | Per-method R/P | Source R/P, shared demand views and callsite P/A | No new dedup principle |
| Sharing before original fan-out | Method request union | Shared demand view plus callsite union | Preserves default sharing |
| Local fact transfer and observations | Ordinary method analysis | Retained | No local fact-work elimination specified here |

The r+p bound for r readers and p originals at a shared entry is already a
default property. Restoring it repairs the previous source/original cross
product; it is not an improvement over default.

## 3. Difference A: admit input before dispatching starts

~~~kotlin
// Default protocol shape:
fun onCallInput(input: BoundInput) {
    subscriptions.subscribe(input) // admission may reject this as duplicate
    callee.link(input)             // independent input-projection path
}
fun Callee.onLink(input: BoundInput) {
    for (initial in abstraction.project(input))
        addInitial(initial) // global dedup prevents repeated body evaluation
}

// Proposed protocol:
fun onCallInput(input: BoundInput) {
    val fresh = subscriptions.admit(input)
    if (fresh.isEmpty) return
    batchInitials(preparedStaticEntriesAndOrdinaryNonstaticStarts(fresh))
    replaySummariesAndKnownRequests(fresh)
}
~~~

The actual current source has independent subscribe/link calls in
[RunMethodCallServices](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/RunMethodCallServices.kt).
The ordinary caller admission already removes duplicate N2 input growth.
Coupling that admission to projection can also skip duplicate supplier
projection and delivery. It does not skip additional callee body evaluations:
the callee already deduplicates those.
For a partially fresh offered input, the proposal also projects its admitted
semantic delta rather than forwarding the entire offered value again.
Retain required header growth and input-presence semantics in that delta.

~~~text
Saving if an input was already admitted:
  default: possible raw delivery + projection + initial dedup attempts
  proposed: one caller admission check, then stop

For a fresh input:
  existing canonical preparation is retained; static descriptors pass as is
  ordinary nonstatic request-dependent admission occurs at the caller

For an overlapping input:
  old offered marks {T,U}, with T already admitted
  only fresh U needs projection, if all other headers/kinds are unchanged
~~~

This relies on both late-request and late-summary replay against retained
inputs. Repeating a raw input must not be necessary to discover a later answer.
Presence metadata still has to be delivered on the first real admission.

## 4. Difference B: deliver canonical start batches

~~~text
p actual inputs at one caller statement:
  original p1 -> current C.g
  original p2 -> current C.g
  ...
  original pp -> current C.g

default:
  raw inputs reach callee; callee projects all p and rejects repeated starts

proposed, if these inputs share one batch:
  caller projects p inputs
  caller unions projected starts into one C.g abstract start
  callee receives one start membership plus required presence metadata
~~~

The full p input rows remain in the caller in both procedures. The new
saving is possible in queued payloads, callee dispatch and duplicate initial
admission attempts. Projection itself still occurs unless a separate
geometry-sharing optimization is designed.

Let I be raw input arrivals in a batch, S their total projected start
memberships, and U the distinct projected starts. U <= S. U need not be
smaller than I: one broad input can project to many field/mark initials.
If each batch contains one input, batching may give no saving.

Also, same-unit links already execute inline in
[UnitRunner.link](../../core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/bidi/engine/UnitRunner.kt).
Do not count every removed raw input as a removed queued message.
Existing packed lanes already batch fields; do not count their saving again.

## 5. Difference C: shortcut uncovered static request relays

Use a cohort with actual supplied C.g, one productive caller statement,
h transparent relays and k requested marks. B changes the origin's current
field before checking it:

~~~java
A(p) { C.g = p; H1(); }
H1() { H2(); } // ... through Hh
B() {
    C.f = C.g;
    check ContainsMark(C.f, T);
}
~~~

Assume no frozen C.g/T or broader covering template.

~~~text
Default dynamic request path:
  B/original-g -> Hh/g -> ... -> H1/g -> A/p
  each step joins a request to an actual bound input and maps to its original

Proposed:
  B/original-g -> demandView(H1,g) -> A's subscription at H1()
  subscription joins its actual input -> A/p
  prescan Reachable(H1,B) replaces the intermediate request joins
~~~

This is the main algorithmic saving. The h intermediate input/original
remapping joins disappear. The fact and call-stage relations needed for
ordinary analysis and summary application remain.

### Exact component accounting for that cohort

At quiescence, with separate caches as currently proposed:

| Component | Default | Caller refinement |
| --- | ---: | ---: |
| Request-mark joins against bound inputs/originals | (h+1)k | k |
| Source request memberships | k | k |
| Intermediate method request memberships | hk | 0 |
| Shared demand-view memberships | 0 | k |
| Callsite processed-request memberships | 0 | k |
| Total retained logical request memberships, excluding common Base destination | (h+1)k | 3k |

Thus the remapping component saves hk joins. The literal request-state design
saves (h-2)k logical memberships, strict only for h>2 and k>0. The earlier
source-only k-membership accounting does not apply to this refinement.
Physical mark-set arrays may be shared, so these are not retained-byte claims.
Batching already shares geometry checks across marks. These pair counts
describe mark-member work, not k full AP evaluations for every input.

The proposed procedure additionally pays for pattern matching, reachability
search/cache, source-to-view unions, view notifications, callsite P/A and
initial batching. Those costs are outside the remapping count.
Ordinary has request notifications as well as upward request deliveries.
Do not present the diagram's arrows as exact Kotlin message totals.
A cold reachability query can itself traverse the h-method chain. With one
mark both procedures can therefore have linear cold work in h; subsequent
marks can reuse the reachability result.

## 6. Cases with no established advantage

~~~text
ordinary frozen coverage already handles the check:
  no dynamic relay joins to remove

h <= 2:
  the proposed separate request caches do not save logical memberships

shared entry with many readers:
  both default and the repaired proposal merge before original fan-out

many productive callee views with overlapping reachable readers:
  global view matching can still exceed default's shared local propagation

fresh inputs with no batch collapse:
  projection has moved; no projection-work saving follows
~~~

Historical ab6 suppresses canonical same-field checks covered by its frozen
plan. The changed-origin C.g -> C.f example matters because the literal
C.f/T template need not cover the actual original-g request. A real workload
must satisfy this uncovered condition before claiming chain savings.

The broader static refactor also replaced inherited frozen mark plans with
reached outgoing needs and active request views, and changed static actual-T
admission to be eager. These are separate from caller ownership. Laziness can
avoid unused plan entries, but shared frozen values and replicated active
caches must be counted before claiming a byte saving. Eager admission can
increase concrete work; moving projection does not remove that expansion.

Consequently this proposal is not a strictly better algorithm for all
projects. It trades intermediate dynamic request joins for a global indexed
reachability join and local caller caches. The useful workload has many
uncovered transparent request relays, repeated supplier submissions, or
canonical starts that collapse well before delivery.

## 7. Validation needed to attribute a measured gain

~~~text
raw supplier arrivals / duplicate caller admissions / projector invocations
projected start memberships before batching / distinct starts after batching
cross-unit raw and canonical deliveries, separated from same-unit inline calls
bound-input/original request joins, split into covered and uncovered cases
source / demand-view / callsite P+A memberships
pattern candidates / reachability misses / fresh view marks / notifications
ordinary summary applications and actual local fact work
~~~

The [new constructive proofs](../../spec/run1-caller-owned-subscriptions-proofs.md)
cover finite routing, view/callsite composition and default-summary inclusion
in the selected calculus. They do not give a total runtime/heap bound or prove
the Kotlin adapters. Compare the implemented adapters with ordinary incremental
propagation, including frozen coverage, before claiming a measured gain.
