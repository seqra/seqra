# Next core iteration after merged run-1 requests

Date: 15 October 2026. Status: historical broader proposal, based on runtime
`4c64c579f4c4a10fc8ffcc5bcd0bf1bcd45cf877`. Its measurements describe that
revision only. Current implementation status and validation are recorded in the
selected proposal below.

Selection on 16 October: the next iteration is limited to the exception fix and
frozen static-request factoring. Use the
[detailed selected proposal](bidirectional-static-domain-and-frozen-requests-20261016.md).
The other options below remain unselected.

Use the [fresh project report](../reports/bidirectional-run1-mark-request-performance-20261015.md).
Earlier proposals and retained-byte estimates are historical; they do not establish
this iteration's ranking. IR storage is outside this proposal.

| Project | Latest reached | Completed engine | Accepted forward report |
| --- | --- | --- | --- |
| OWASP | run-5, forward; TIMEOUT | run-4, backward | run-3 |
| Conductor | run-1, forward; FAILED | None | None |
| ThingsBoard | run-1, forward; FAILED | None | None |
| Stirling-PDF | run-1, forward; FAILED | None | None |

Only OWASP completes run-1: 245.412 seconds against a 60-second engine target.
The other projects fail the frozen static-class check. Their failed prefixes must
not establish a successful time or memory bound. No project produces an OOM heap.

## Required correctness work

Close the delivered-static-domain gap before selecting a performance patch. The
known failure is an incoming class absent from the frozen universe. The exact
class and producer are not printed, so neither source-review hypothesis is an
established project reproducer.

First make the failing diagnostic identify the class/field symbol, full callee
key, original caller premise and call site, and delivery route. Print bounded
identifiers, not the whole packed fact. Then reproduce with the same project
configuration before choosing the metadata repair.

Keep two concepts separate:

```kotlin
// Semantic sets; not a requirement for two new runtime stores.
Sigma = all static classes and fields that any selected producer can deliver
H[method] = static components that this method owns or can access transitively

require(H[method].all { it in Sigma })
proveEveryDeliveredStaticComponentIsIn(Sigma)
```

The proof must include statement aliases, call binding and bind-back, source and
sink conditions, source/end actions, cleaners, pass/unresolved branches, and native
and opposite record readers. The resolved call graph is already available after
prescan. The gap is producer/metadata coverage, not a need to resolve calls later.
An unowned supplied component is not a reason to add it to every callee's H or
to create a request for it.

For metadata predicates, represent unavailable alias knowledge explicitly:

```kotlin
val aliases = when {
    context.aliasAnalysis?.isComputed == true -> Known(context.aliasAnalysis)
    else -> Unknown // includes the fallback collect(method) path
}
collectEffectiveStaticEffects(method, aliases) // Unknown must not mean false
```

This is the intended contract, not a proven one-line fix. The metadata review
must also compare reference owners with resolved declaring owners. If all real
producers canonicalize them, prove that equality; otherwise collect the actual
emitted names through the same helper. Keep the finite universe and ownership
proofs distinct. Do not merely remove the failed check or add an identity return
for an unknown component; a whole-static cleaner can make that return unsound.

Required controls include a fallback method with no prescan alias context, a
computed and an uncomputed context, inherited static references, static aliases
at statement and call boundaries, and mark-dependent source **and** sink rules.
Compare emitted static components with the frozen domain before starting run-1.
Then rerun the three failing projects with their existing configurations. Require
a completed run-1 and an accepted report before evaluating performance gains.

## Impact and selection order

Memory and engine time are different targets. No retained-owner census is
available. The memory ranking below is a hypothesis based on current collection
counts and source behavior. It is not a byte forecast. Options overlap.

| Expected persistent-memory priority | Proposal | Current evidence and expected effect |
| --- | --- | --- |
| 1 | Factor frozen static requests | Avoid 3,000,408 eagerly expanded map rows in ThingsBoard's plan; preserve the same logical requests |
| Conditional 2 | Defer unused forward alias summaries | Avoid unused alias forms and possibly cold alias state; eligibility and owner bytes are unknown |
| Conditional 3 | Factor concrete-premise execution | Potentially avoid repeated method state across initial marks; the exact mergeable quotient is not measured |
| Conditional 4 | Remove dominated FLOW rows after growth | Remove redundant stored geometry if the observed source counterexample is frequent; frequency and bytes are unknown |
| No demonstrated persistent saving | Apply field cleaners directly | Avoid synthetic intermediate facts, lists and repeated transfers; mainly CPU and allocation |
| No demonstrated persistent saving | Prepare each bound rule once per build | Avoid repeated validation and normalization inside one form build; mainly CPU and allocation |
| No demonstrated persistent saving | Match request deltas by slot | Avoid input/row combinations; mainly CPU and temporary results |
| Small temporary-state target | Fuse pending request admission | Avoid intermediate sets on repeated offers; keep logical R and P unchanged |
| Unranked future option | Form-cache lifecycle and reference registry | Forms already use managed soft references; further retention/rebuild tradeoffs need current owner and rebuild evidence |

For the 60-second engine target, start with the cleaner path, then repeated work
inside form construction. Consider the slot-directed join where the completed
profiles show material cost. Pending admission is a small,
separate patch. Static factoring targets standing plan memory. FLOW pruning needs
a measured eligibility gate. These priorities do not claim that one patch can
reduce 245 seconds to 60 seconds.

The failed project prefixes also make forward alias deferral worth an eligibility
study. It has a larger observed CPU scope than duplicate admission, but other
consumers may already require the same alias result. Select it only after the
static-domain repair and that study.

The initial grammar limits path shape, not total execution state. The current
keys still include method, entry/context, original initial premise, statement or
call site, layer and ordinary exclusions. `TaintTree` already packs concrete marks
in its leaves. A concrete **initial premise** still identifies one mark. Those
are different dimensions. Replacing a leaf mark set again cannot merge state
whose original premises differ.

## Factor frozen static requests

**Current:** `Run1StaticProfile.freezePlan` visits every transitively selected field
slot and materializes a map entry. It unions root needs, class-wide needs and
field-specific needs for that slot. Different profile maps repeat rows even when
their mark-set values are shared. Entire unchanged profiles already share storage.

**New:** retain the same selected fields and sparse mark descriptors. Expose the
same immutable B through a lookup/iteration view. Do not expand common root or
class marks into a separate map entry for every selected field.

```kotlin
class FrozenStaticRequests(
    val selected: SelectedFields, // the existing exact transitive selection S
    val root: RequestMarkSet,
    val byClass: Map<ClassId, RequestMarkSet>,
    val byField: Map<StaticFieldSlot, RequestMarkSet>,
) {
    fun borrow(slot: StaticFieldSlot): MarkSelection {
        if (slot !in selected) return EmptyMarks
        return distinctUnionView(root, byClass[slot.cls], byField[slot])
    }
}

// Logical equality required for every slot, including empty rows:
Bnew[slot] == Bold[slot]
R[slot] = Bnew[slot] union D[slot]
D[slot] intersect Bnew[slot] == emptySet()
```

The union view yields each mark ID once. Root, class and field descriptors may
overlap. A duplicate descriptor must not cause duplicate answers or notifications.
Outside-S fields remain outside B even when root marks are nonempty. Enumerate
selected fields, or selected fields of a known class, through the existing
footprint. Do not scan the global field universe for a narrow input.

Summary storage borrows this B once. The temporary store still collects only D
candidates and commits through the existing protocol:

```kotlin
summaryStorage.commit(temporary)
temporary = RequestStorage()
```

There is no initial notification wave or second cumulative B store. Fresh FLOW
reads cumulative R. A dynamic delta contains only new D. An unchanged static slot
does not climb inherited B; a renamed slot and every required dynamic mark still
reach the original caller slot. Late wide-input activation must still create the
required D; it must not widen S by accident. Direct class-point initials remain
separate from field-slot requests and retain every actual supplied mark.

**Why:** this avoids a materialized row relation. ThingsBoard has 1,452 distinct
B map roots and 3,000,408 physical rows. Its logical 63.55 million memberships do
not represent that many allocated mark cells: payloads already share storage.
Do not count them as predicted savings. The catalog setup takes 3.667 seconds;
the separate 69.447-second metadata phase is not removed by this change.

Prove lookup and enumeration equality, immutable borrowed views, D/B disjointness,
and identical source/answer/summary closure. Test overlapping descriptor marks,
empty needs, an outside-S field, mixed `f -> {f,g}` flow, class-wide suppliers,
both input/request arrival orders, recursion, cancellation and plan release.
Measure physical map/set rows, full owner unions, query allocation and query time.
The replacement descriptor and selected-set costs must be included.

## Apply field cleaners as one typed transfer

**Current:** a named-field cleaner lowers to a typed read, a cleaner on a temporary,
and a typed write. `cleanChain` creates intermediate `PlanFact` lists and applies
general statement transfers and cuts at each stage. This path includes type and
accessor checks even when little geometry changes.

**New:** add a field-cleaner operation that computes the same composite transfer
directly on the packed input. Keep the existing chain as the reference oracle.
Begin with the effective JVM form that has no alias-path expansion. A general
aliasing form stays on the reference path until its own equivalence is proved.

```kotlin
// Compile once when the effective form is built, not on every fact.
fun applyFieldCleaner(step: CompiledFieldCleaner, fact: PlanFact, out: Sink) {
    step.forEachReadBranchAfterFiltersAndCuts(fact) { readView ->
        collectRequestsBeforeCleanerGeometry(step, readView) // abstract origins only
        step.cleanAndWrite(readView, out)
    } // logical branches; no intermediate PlanFact list
}

decode(direct(step, input)) == decode(write(clean(read(input))))
```

The equation includes the read/write filters, passthrough branches, strong update,
E, FLOW mark exclusions, layer, limits/cuts, original premise and identity flag.
Requests keep the original typed-read eligibility and branch/callback order. A
read rejected by its type filter invokes no cleaner and creates no cleaner
request. F75 precedes the cleaner's own geometry test after that accepted read.
An absent concrete mark alone does not prove the whole wrapper is identity: a
typed read/write can filter another mark, and a helper with aliases can emit another
path. An already excluded abstract mark still creates its required request. Keep
request creation before geometry/exclusion rejection. The special S-root continues
to skip rule evaluation and uses only return-gated untouched framing.

A narrow identity fast path is allowed only when the complete composed operation
is identity and has no missed request, conjunction or source side effect. Source
and sink rules can both have complex mark conditions. Preserve their NNF and
mark-wise evaluation; do not expand conditions into cubes.

**Why:** avoid repeated general transfers and short-lived containers, rather than
make the same intermediates smaller. Whole OWASP run-1 has `cleanChain` in 30.99%
of recorded Java samples and 53.67% of sampled allocation. The separate 30-second
CPU capture has the cleaner chain in 46.62% and statement/type filtering in 20.01%.
The latter capture also samples native work; the denominators differ. Inclusive
groups overlap and truncated stacks can miss callers. None of these shares is a
predicted speedup or removable retained-byte estimate.

Prove the equation with an independent scalar oracle and packed-tree controls.
Include all clean reaches, mark/all-mark removal, mixed T/U leaves, exact/STAR/ANY
input, both layers, E/mx, typed rejection of an unrelated mark, multiple cleaner
steps, temporary erasure, static/class components, cancellation and source/request
callback order. Compare actual initials, summaries, records and strict project
reports. Measure eliminated intermediate outputs and typed checks, not only wall
time. Keep normal type checks on final facts.

## Prepare each bound rule once per form build

**Current:** `JIRCallPlanBuilder` validates source and sink rules before calling
`rewriteSourceOrSink`, which validates again. The rewrite normalizes the resulting
mark expression; `predicate` normalizes that result again before compilation.
These are separate calls within one build, regardless of whether that build is
cold or follows cache eviction.

**New:** use a build-local prepared rule result. Validate the complete raw rule
once, create raw signed NNF once, rewrite static predicates once, then normalize
the rewritten signed mark condition once. Lower its already prepared predicate
without a second validation or normalization of that same stage.

```kotlin
fun prepare(rule: BoundSourceOrSink, policy: BindingPolicy): PreparedRule? {
    if (!validateRawRule(rule)) return null
    val signed = normalizeRawSignedNNF(rule.condition)
    val rewritten = rewriteStaticPredicates(signed, policy)
    val condition = normalizeSignedMarkNNF(rewritten, policy.negativeProjection)
    return PreparedRule(rule, condition, policy)
}

lowerPreparedSourceOrSink(prepare(rule, currentBindingPolicy) ?: return)
```

Raw NNF preparation and normalization after static rewriting are different
operations; both remain required. Preserve `P AND NOT P -> false` before remaining
negative literals are approximated. Keep raw-position/action validation before
folding, including dead or negated alternatives. Entry source and sink eligibility
requires signed `isTrue` with negative projection disabled. A remaining
`NOT ContainsMark(P)` must not become an unconditional entry rule. Normal call/exit
predicate lowering uses positive approximation. The separate `allRelevant` source
rewriter tests signed `!isFalse`. Give these consumers distinct typed policies and
prepared results; never reuse a final positive predicate for signed eligibility.
Cleaner eligibility retains its existing rewriter/`isTrue` path. Keep
occurrence/action ordinals and diagnostics stable.
Conditions remain NNF; no cubes are introduced.

**Why:** avoid repeated tree walks and temporary identity maps inside a costly
build. Whole OWASP run-1 has `JIRCallPlanBuilder.build` in 25.21% of recorded Java
samples and 22.47% of sampled allocation. These include other build work; they are
not the removable share. Its run-5 near-limit sample is also build-heavy, but that
separate window does not establish a run-1 gain.

Keep this result local to one binding/build. It adds no cross-build memo,
`WeakIdentityCache` or retained raw-rule registry. Existing public helper paths
that accept unprepared conditions must retain their validation contract. Prove
the prepared and reference build results equal for every supported policy.
Test source/sink/boundary roles, repeated/deep NNF, static and mark complements,
false conditions with invalid raw actions, anonymous IDs, array gates, generated
end actions and cancellation. Count actual validation/normalization traversals
and complete build allocation before predicting savings.

## Defer unused forward alias summaries

**Current:** call-plan construction eagerly builds `aliases(back)`. Forward fact
application later checks `MemoryEffect`, which admits DEMAND facts or a result
that is not an identity candidate. A plan used only by NORMAL identity results,
or with no returned result, can pay for an alias summary it never applies.
`LocalAliasAnalysis.aliasInfo` already computes once per analysis object. This is
not evidence of repeated alias computation after form-cache eviction.

**New:** retain a small per-form alias recipe and materialize it on the first
forward fact admitted by the existing guard. Keep the same immutable summary for
subsequent uses of that payload. Do not capture the whole call-plan builder.

```kotlin
fun applyForwardAliases(fact: PlanFact) {
    if (!Guard.MemoryEffect.admits(checkNotNull(fact.identityCandidate), fact.facts.layer)) return
    aliasRecipe.withSummary { summary -> applyExistingStage(summary, fact) }
}

fun reversePlan(): CallPlan {
    val aliases = aliasRecipe.materialize() // backward stage has no forward guard
    return reverseWithExistingAliasTargetsAndPassOver(aliases)
}
```

Backward reversal must force the alias summary. It removes the forward guard and
uses alias targets to derive PASS_OVER. Reusing a forward lazy guard in backward
mode would lose edges. Storage statistics must observe an unmaterialized recipe
without reading `stage.summary`; the current statistics visitor would otherwise
force it during the measured build. Report recipe and built-summary counts
separately rather than calling unknown storage zero.

**Why:** avoid constructing an unused relation and possibly avoid its cold
method-wide alias computation. Recorded alias compute accounts for 42.03%, 32.56%
and 47.86% of Java samples in the failed Conductor, ThingsBoard and PDF run-1
prefixes. Those are inclusive observed frames, not an eligible/removable share.
Call resolution, static/local rule predicates and statement/boundary forms can
already force that same alias object before this stage. Measure these first-force
reasons and actually used alias stages before selection. Keep this separate from
the build-local validation/normalization change.

Require the static-domain coverage repair first; the existing metadata visitor
does not collect this alias stage. A deferred recipe must not introduce a new
class/field outside the closed domain. Preserve complete binding keys, callback
payload pinning and the existing AA result/null semantics. AA has a 10-second
local limit under root cancellation: changing first-use time can change a bounded
run's outcome. Test those cases explicitly instead of claiming equal timeout
prefixes. Include late forward nonidentity and DEMAND results, backward/PASS_OVER,
empty summaries, recursive calls, competing first borrowers, cache collection,
statistics enabled/disabled and cancellation. Prove completed-closure equality
for fixed alias results, then measure real bounded-run behavior. There is no
new global alias cache and no IR-storage change.

## Match request deltas by slot

**Current:** a changed-request batch already has a slot-keyed immutable map. The
notification path turns it into rows, visits each candidate input once, then tests
all batch rows for that input. Concrete answering also tests all rows for each
nonempty exact/any leaf branch.

**New:** keep the batch map and use slot-directed lookup for narrow branches. Reuse
its existing keys; add no persistent index or request store. Broad concrete root
or class branches still visit all matching existing keys in their original order.
Fresh input replay must continue to borrow full current R, not only the last delta.

```kotlin
fun matchingDeltaRows(delta: RequestRows, input: Facts): MatchingRows = when {
    input.base != STATIC -> delta.borrowBase(input.base)
    input.hasOnlyExplicitStaticFields() -> delta.borrowFieldsIn(input)
    else -> delta.borrowMatchingExistingKeys(input) // broad concrete suppliers
}
```

This sketch specifies a selector, not a replacement AP matcher. It must preserve
the same valid slot/leaf pairs. Matching ignores E and FLOW mx, while concrete
answers still intersect actual marks. Static ANY/exact alternatives and their
STAR/exact initial tails remain distinct. Eligible FLOW retains the actual abstract
origin, original caller slot and full caller identity; concrete origins never
propagate requests. Inherited B skipping stays per output slot, not per whole input.

**Why:** avoid an input/row cross product. ThingsBoard's failed run-1 records
216,775,968 row attempts. The counter also covers direct concrete answering, so it
is not a pure notification-only count or a CPU share. Measure that attribution
before predicting benefit. A selector can reduce failed tests; it cannot remove
the required initial facts or caller relation.

Prove selected pairs equal the reference scan in both directions. Preserve row and
leaf encounter order, or explicitly design and test any order change. Complete the
input visit before queuing effects. Test mixed bases/classes, broad ancestors,
multiple explicit fields, exact and nonexact marks, a renamed output slot, both
arrival orders, recursive/remote delivery and cancellation. Compare outputs and
request union, not just candidate counts.

## Fuse pending request admission

**Current:** admission builds `offered - R`, then merges that result into P. A pure
P duplicate has already allocated the intermediate selection and can attempt to
enqueue Work that is already scheduled.

**New:** inside the owner-only P write, test R first and P second in one offered-ID
pass. Allocate a replacement builder only after the first fresh ID. Expose no P
read to producers or answering.

```kotlin
val fresh = temporary.mergeOffered(slot, offered, summaryStorage.borrowRequest(slot))
if (fresh == 0) return
startOldestPendingClockIfNeeded()
requestWork()
```

For each completed valid offer preserve:

```text
Rduplicate = offered intersect R
Pduplicate = (offered minus R) intersect P
fresh      = offered minus (R union P)
```

**Why:** OWASP repeats P membership on 24,396,379 of 24,742,690 offered IDs.
Avoid temporary sets on these repeats. R, P and the number of producer calls remain
unchanged. The normal CPU admission family is only 5.48% inclusive; this patch alone
does not establish the 60-second target or a persistent-memory saving.

Prove nonempty P already has counted queued/executing Work after first admission.
The owning handler cannot interleave between fresh insertion and enqueue. A
duplicate then adds no new scheduling obligation. Preserve reservations on quantum
yield, commit-before-replacement, queued effects and failed/cancelled delivery.
Keep the exact R/P/fresh counter partition, oldest wait clock and callback order.
Test request-only actors, late R growth, all arrival orders and cancellation during
construction and after mutation. There is no new scheduling flag, index or cap.

## Factor concrete-premise execution, if the quotient is large

**Current:** each concrete initial mark has its own method premise. Statement
conclusions and caller inputs retain that premise even when their geometry is
identical. ThingsBoard admits 1,734,056 method-local initials and 3,015,421 caller
input groups before its failed run-1 ends. These counts include legitimate distinct
methods, slots and callers; they are not a mergeable-mark census.

**New:** first measure the exact quotient obtained by removing only the initial
mark while keeping every other key component. If that quotient is much smaller,
design execution over marked premise alternatives. Share geometry and common
transfers while preserving which original mark produced each output.

```text
Required output meaning:
    (full original premise alternative, output position, output mark, layer, E/mx)

Unsafe replacement:
    union(all premise marks) x union(all output marks)
```

**Why:** a large quotient could permit avoiding repeated analyzer work and standing
state, rather than compressing each scalar row. No current quotient, removable
owner bytes or speedup is known. It ranks below the measured static-row opportunity
until that gate is met. If outputs need the full correlation matrix, the larger
representation may not help.

The design must project back to the same scalar premise/output relation. Preserve
full caller identity and same-caller ND premise sets; a pooled mark set cannot
satisfy another caller's missing premise. Complex source and sink conditions,
cleaners, summaries, record reversal and trace evidence must all retain the
alternative's provenance. Measure mark-independent transfers and correlation
width before choosing a representation. This is a gated follow-up investigation,
not permission to union initial marks in the current stores.

## Remove dominated FLOW rows after growth

**Current:** direct equal-root FLOW insertion can intersect compatible exclusion
headers. However, same-header root growth returns before that normalization path.
It can leave an older row dominated by a new row. Caller input storage also keys
complete FLOW headers separately.

```text
Same base, layer and field E; p and q are separate packed leaf paths:
A: paths {p,q}, mx {T}
B: paths {p},   mx {}
add paths {q}, mx {}  -> B grows to {p,q}, mx {}
A remains, although B now covers A.
```

**New:** after growth, remove only rows whose denotation is fully covered by the
remaining rows. First scope this to local FLOW groups. Caller stable handles,
postings and outstanding readers require a separate design before changing them.
Do not claim the current implementation never merges mark exclusions.

```kotlin
afterRootGrowth {
    for (old in sameBaseAndLayerRows)
        if (referenceSubtract(old, remainingRows).isEmpty) remove(old)
}
```

**Why:** this avoids redundant standing rows and future scans. The counterexample
is source-backed; its frequency in these projects is unknown. It is a conditional
memory option, not an explanation of millions of measured groups. Blindly
intersecting both E and mx across different geometry can invent facts. Preserve
the existing layer-specific coverage test and all initial/caller correlation.

Before selection, measure dominated rows and complete removable owner bytes.
Prove union-denotation equality, future-add closure and request side-effect
preservation. Test growth and duplicate orderings, both layers, different E/mx and
mixed roots. Include the existing cross-product counterexample that must not merge.
Do not add a quadratic cleanup pass without measuring its cost or restricting its
eligible cohort.

## Future cache work

Forms already use managed soft references in `FormsCache` and `JIRMethodForms`.
Introducing managed references again is not a new optimization. Possible further
work is an eviction policy with hysteresis or a scoped active-form borrow.
**Current:** memory policy can clear form payloads, which later accesses rebuild.
**New:** a separately designed lifetime policy balances active reuse against cache
retention. **Why:** avoid rebuild churn or release colder payloads sooner. Neither
effect is established by aggregate form-build counters.

Two reference changes remain explicit, unselected future alternatives:

- `SoftReferenceManager`: the current queue keeps registered soft-reference
  wrappers until cleanup, including wrappers already cleared by GC. A possible
  redesign retires cleared wrappers through a reference queue and an unlinkable
  registry. It could reduce registry retention; first measure those wrapper owners,
  cleanup frequency, synchronization and registration cost. Scope the census to
  `bidi-method-forms`; IR-cache ownership is outside this proposal.
- `WeakIdentityCache`: a possible weak memo reuses a proved immutable compilation
  result for the same input identity and complete policy/context/version key. It
  could avoid repeated normalization or binding. It needs a separate contract for
  weak keys and values, value-to-key cycles, concurrent builds and reclamation.
  A miss must recompute the same result. No current saving or useful lifetime is
  measured.

First count cold builds, rebuilds, evictions, retained form owners and rebuild
time. A sampled call-plan frame cannot distinguish them. Neither alternative is
implemented or selected by this proposal. Discarded bound-rule cache
retirement also remains unselected: no fresh heap measures its discard-only owner
fraction. Use current evidence before assigning it a memory rank.

## Implementation order and acceptance

1. Reproduce and close static-domain coverage; update the producer proof and tests.
2. Select a measured CPU option and implement it as one reviewable patch. Keep
   the reference transfer/scan for independent checks.
3. Consider frozen-plan factoring for standing memory, then FLOW cleanup only
   after its eligibility census. Keep these patches separate from CPU changes.
4. Run common/JVM checks and the existing strict application gate. Preserve any
   known failing assertion as failed; do not relabel it as an allowed result.
5. Run the same fixed four projects against fresh main. Report every engine and
   barrier, latest reached/completed/accepted iterations, exact finding losses,
   CPU/allocation coverage and any OOM heap owners.

Success requires a completed run-1 within 60 engine seconds, no missing baseline
locations, bounded extra findings and no OOM under the specified configurations.
Prescan and reports remain separately measured. No optimization above is
implemented by this document.
