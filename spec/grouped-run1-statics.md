# Mark groups and direct static handling

Date: 19 October 2026. Status: **complete feature specification; runtime
implementation has not started**. Language: ASD-STE100 Simplified Technical
English. Defined technical terms, program names and mathematical notation
keep their stated meanings.

This is the normative specification for the selected mark and static
changes. It follows [proof-guide.md](proof-guide.md). It contains the whole
feature contract; earlier proposals are not additional requirements.
The [proof dossier](grouped-run1-statics-proofs.md) maps requirements to
constructive models, validation and production adapter checks.

## 1. Pinned implementation baseline

Start implementation from:

```text
selected checkout: a80d5c580b3b4715ab39679fbf66f0f190125fa0
same measured code:28a123f0eac937e8f2feca9ee5dd84e38fa8f979
core tree:         db89e5f9dd4a142b6a093006193c22e7e416abd3
rules tree:        e2a7d29770276ca7d4d0efe974914f687ca67def
first bottom-up:  ab6c3cd006cb62e6a017c0e3c32d96573d2214e3
```

The selected checkout has no bottom-up static lane or static-need graph.
Its runtime equals the measured request-gated/selective-preservation
version. The immediate parent of the first bottom-up implementation has
the same runtime but already has future bottom-up design text. Use the
selected checkout for both implementation and specification comparison.
This task records the pin; it does not reset the current workspace.

The [baseline manifest](baselines/pre-bottom-up-a80d5c580.json) records the
exact document hashes and tree checks. Read a pinned document with:

```sh
git show a80d5c580b3b4715ab39679fbf66f0f190125fa0:spec/ap.md
```

Within that checkout, `run1-field-writes.md` takes precedence for the old
run-1 phase, followed by `run1-requests.md`. The AP, analyzer-core and
interpreter documents supply unchanged ordinary contracts. Their old
historical paragraphs do not override those selected phase documents.
This new specification explicitly replaces the phase seams in section 17.
Unchanged AP primitives and normal-call semantics keep the pinned contract.

The baseline validation had 1,360 common and 297 JVM tests passing, plus
13 sequence cases. Its application gate had 148 passes, nine failures and
one skip. It is a useful implementation base, not a claim of full project
acceptance. Runtime changes are outside this task.

## 2. Semantic domain and proof scope

A fact has base, accessor path, tail, field exclusion E, mark and layer.
An edge has an initial premise or complete premise set, a current fact,
an actual program stage and support. NORMAL means independently valid
ordinary flow. DEMAND means a candidate that later analysis must refine.

| Term | Definition |
| --- | --- |
| S | Static/global base; it is different from control ZERO |
| C.f | Class accessor C followed by field f on S |
| STAR, `*` tail | Ordinary rooted descendant tail |
| EXACT, `$` tail | The exact position |
| ANY, `[any]` | May tail; also used as a demand-entry match pattern |
| MUST, `[any-taint]` | Forward must-taint tail with its ordinary spatial exclusion |
| FLOW | Abstract pass-through mark, optionally with mark exclusions |
| Group(g) | Run-1 taint selector `(g, *)`; it is not FLOW |
| Full(g,T) | Exact later mark: group plus complete old full identity |
| Rooted supply | A finite derivation from an eligible source/boundary through actual forward operations and all required premises |
| Support | Original premises, bound rule occurrence, argument/guard slots, method context and actual call stages |
| Early static effect | A reached static conclusion published as a run-1 DEMAND summary before method exit |

Static scopes include exact points and symbolic descendant regions.
Compatibility requires an actual AP intersection and the correct projected
current image. An exact class point does not supply its fields. A broad
class region can intersect field regions. Do not expand broad regions into
every declared field. The finite proof oracle uses certified overlap cells.

The proof target is inclusion of each finite full-mark witness admitted by
the pinned reference contracts. It is not equality of run-1 precision:
run-1 can ignore static call order and caller context, but supplied groups
must have source/writer derivations. All resulting static candidates are
DEMAND. Run-2 and later confirmation use ordinary real CFG/call transfer.

Keep these construction conditions: complete ordinary micro transfers,
correct touched sets, mark-reversible full operations, legal AP shapes,
complete actual call bindings, full source eligibility, positive field
limits, no forward return binding into ZERO, and baseline exception scope.
ZERO discovery must reach each supported internal seed. The JVM adapter
already wires regions without an exit to the exceptional exit for this
purpose. The existing exceptional interprocedural limitation is not changed.

## 3. Mark ownership, forms and run schedule

Each old full mark has exactly one owner group. Use the outer top-level
rule ID before the loader flattens a rule. The entire join rule, all its
branches and generated states share that group. Internal source/sink
namespaces remain in T. A consuming rule does not reassign ownership.

Use one new-core adapter around the complete existing provider chain.
It must cover source, entry, exit, static, sink, cleaner, rewriter and
pass-through roles, including `GeneratedMark` and method specialization.
Keep the legacy provider unchanged. Imported references use the registered
owner. Plain model marks use a declared owner or the reserved tagged
`SharedModel` group. Specialization, context changes and cache rebuilding
do not create groups. Conflicting ownership is an input-contract error.

```kotlin
data class ExactMark(val group: GroupId, val full: FullMarkId)
sealed interface Selector {
    data class Group(val id: GroupId) : Selector
    data class Full(val id: ExactMark) : Selector
}
```

Group, Full, FLOW and control ZERO have distinct typed identities. Numeric
ID equality between their namespaces is not semantic equality. Maintain
one complete eligible full-mark catalog across prescan and all runs. A late
provider extension must update dependent queries before a complete barrier.

Cache full rule literals and stable occurrence/action IDs. Do not rewrite
shared full forms into group forms. The baseline method cache is keyed by
direction, and a PathEdge has one compiled payload. Use a mode-aware mark
view, or explicitly key any new compiled selector payload by mark mode.

| Run | Direction | Taint payload | Field limit |
| --- | --- | --- | ---: |
| run-1 | FORWARD | GroupIds | 1 |
| run-2 | BACKWARD | Full marks | 1 |
| run-3 | FORWARD | Full marks | 2 |
| run-4 | BACKWARD | Full marks | 3 |
| later | Alternate | Full marks | run number minus one |

Run and limit 0 are invalid. Do not stop because the first two limits are
equal. Class accessors do not consume the field limit. FULL mode retains
the baseline F72 FLOW forms; it does not turn them into taint values.

Every run-1 group-taint fact, initial, source result, ND result and summary
is DEMAND, including nonstatic outputs. A static-origin FLOW edge and its
dependent results are also DEMAND. Generic independent FLOW can remain
NORMAL. Layer demotion preserves the AP, tail and mark; it does not turn
the tail into ANY. Source/must tails retain their ordinary representation.

## 4. Initial grammar and field transfer

The exhaustive legal run-1 premise descriptors are:

Here b is an ordinary nonstatic base. It is not ZERO and is not a transient
`CleanerTemporary` base. Cleaner temporaries remain local to their lowering.

| Position | Initial tail | Mark |
| --- | --- | --- |
| ZERO | EXACT | Control |
| Nonstatic base b, empty path | STAR | FLOW |
| S.C.f | STAR | FLOW |
| Nonstatic base b, empty path | EXACT or STAR | Group(g) |
| S.C.f | EXACT or STAR | Group(g) |
| S.C | EXACT or STAR | Group(g), only for an admitted class-point/region primitive |

All initial E and FLOW mark exclusions are empty. There is no ANY/MUST
initial, abstract class initial, second field accessor, nonstatic field
path initial, or S-root initial. An exact taint at the empty S root is not
a legal interpreter atom. A legal broad static descriptor is a set of
locations; it is not that exact atom and must not be stripped as control.

The ordinary method-entry abstraction starts ZERO and nonstatic slots only.
For an ordinary group STAR initial, its normal entry current fact has ANY
tail, as in the baseline. An EXACT group initial starts exact. Both are
DEMAND. Static descriptors belong to admitted read/point effects; they do
not cause generic static method-entry seeding.

Point refinement does not call `addInitial`, `startFact` or entry replay.
It retains the surviving edge's AP relation at that point. It must not
apply the ordinary STAR-to-ANY entry conversion a second time. An exact
or broad supply selects only a legal EXACT/STAR premise descriptor. Never
emit `C.f.[any]/Group(g)` as an initial.

Keep the baseline selective run-1 field-write rule for every static and
nonstatic field write:

```text
x.f = y:
  x.*    NORMAL -> DEMAND, E stays empty
  x.f.*          -> killed by the definite overwrite
  x.g.*          -> preserved when f and g are disjoint
```

The general rule is: if a surviving result would update field exclusion,
move it to DEMAND and keep empty E. Complete hits still kill. Compute the
RHS from the pre-store state, including self-assignment. Alias writes keep
their ordinary weak-alias semantics. Later runs use ordinary strong E
transfer. Do not describe all run-1 writes as weak updates.

## 5. Conditions, cleaners and NORMAL exclusions

Keep one owned signed full-literal NNF DAG per bound occurrence. Fold exact
full literals before group projection:

```text
P(T) AND NOT P(T) -> false
P(T) AND NOT P(U), T != U -> distinct, even if owner(T) = owner(U)
```

Keep the baseline eligible residual-negative projection. Group presence
means possibility for a positive check, not exact truth or absence. Every
group-dependent condition/source/end action result is DEMAND. Preserve all
positive leaf positions, original full identities, ND premises and bound
support for later refinement. A group shared by two argument slots does
not satisfy both slots. Use the NNF DAG; do not expand cubes.

For a selected cleaner of T in group g:

```text
FLOW I -> x except X:
  request g on the actual abstract original I
  preserve FLOW except (X union {g}) in its incoming permitted layer
  retain the independent DEMAND repair for g

Group(g) -> selected cleaner(T): retain Group(g), DEMAND
Group(h), h != g:                retain Group(h), DEMAND
```

Emit the interest even when g is already excluded or the value delta is
empty. A stored group branch still needs its local cleaner/check demand
obligation. Only a singleton abstract FLOW original propagates a new
original-slot request. Group, ZERO and complete ND originals do not.

RemoveAllMarks can kill at its actual admitted locations. Keep true-condition
admission, action order and F74/F75 field-cleaner lowering. A group-possible
condition does not prove that a conditional RemoveAll may kill. No reply
can inject an old value across a genuine kill.

New run-1 FLOW exclusions select entire groups. Later exclusions select
exact full marks. Old group exclusions keep their owner-based meaning:

```text
excluded(Group(g), Full(h,T)) iff g = h
excluded(Full(g,T), Full(h,U)) iff g = h and T = U
```

Compose filters by excluded-set union. Join otherwise compatible FLOW
alternatives by intersection of the denoted excluded sets. In particular,
`Group(g) intersect Full(g,U) = Full(g,U)`. Do not use raw integer-set
intersection across these selector types. NORMAL exclusion is a valid
restriction; the DEMAND repair covers any concrete survivor omitted by it.

## 6. Ordinary nonstatic group requests

The only method request slot is `Base(b)` for a nonstatic original with
empty path. Its member is a GroupId. There is no static slot, field request,
class request, request tail, E, mx or ND conjunction.

Each actor has one temporary owner-write-only RequestStorage P. Its
SummaryStorage has one cumulative RequestStorage R. Both use the same class
with merged group sets. R has no inherited static base B in this design.

```kotlin
fun onMethodQueueEmpty() {
    summaryStorage.commit(temporary)
    temporary = RequestStorage()
}
```

Commit before replacement and before releasing counted work. A new request
joins old matching actual inputs; a new input joins old requests. Ignore
field and FLOW mark exclusions only for request matching/propagation.
Ordinary AP, summary, record and cleaner operations still use them.

Dispatch by the original, not the current base. For `p FLOW -> C.f FLOW`,
request g on Base(p). For `C.f FLOW -> x FLOW`, use section 10's direct
static join. When an ordinary helper's Base request maps back to a static
original in its caller, refine that caller edge at the actual call input
stage. Do not create a static R row or relay a static request through methods.
Concrete/group current facts do not propagate FLOW requests.

No second actor input-history store or deferred answer memo is introduced.
Keep the baseline caller N2 actual-input relation, full CallerRef and ND
matching. A conditional group initial from a request is not a rooted source.

## 7. Prescan and run-scoped storage

Prescan resolves all calls and retains the ordinary complete call graph,
legal geometry domain, stable full rule identities, and local read/write/
condition descriptors. Code reads and actual static condition operands are
consumers. An alias or write alone does not register a read or mark interest.
An actual alias/model write can later publish its reached effect.

Do not compute transitive method-by-field or method-by-group sets, F/B/W
closure, inherited static requests, or a separate run-2 writer-search graph.
The ordinary backward engine creates fresh actual subscriptions at real
call stages. It needs no frozen run-1 marked subscription or global writer
jump to follow a reference witness.

| Store | Owner and purpose |
| --- | --- |
| Full mark/owner catalog and full forms | Existing immutable provider/cache lifetime; mode-aware use |
| Method edges, initials and summaries | Existing method actor; ordinary computed edges and early static effects |
| Ordinary input/summary/request subscriptions and ND joins | Baseline caller N2 and SummaryStorage ownership |
| Published write regions | One run-scoped static service; admits local reads |
| Rooted supplied groups | Same service; one group set per normalized static region |
| Pending reads | Same service; reached local descriptor plus admitted regions |
| Refinement continuations | Same service; actual operation input, conditional edge, support, wanted/delivered groups per region/image |
| Reached writer rows | References to ordinary early summary effects and their input stage/support; no writer-consumer product |
| Grounded input/support bits | Merged group support on existing ordinary inputs/premises; no duplicate Facts or full source histories |
| Temporary static batch | Per-method writes, consumers, interests and supplied group growth before commit |
| Run-2 static default | One immutable implicit GROUP demand view, section 11 |

Pool scope is global for run-1's accepted order/context approximation.
Local kills do not erase its historical union. Every pool group still needs
a rooted derivation for that field region. An exact class point, request,
FLOW identity, catalog entry or prescan writer candidate cannot supply it.
Runtime stores merged memberships and ordinary support references, not the
Lean derivation tree for every field/group answer.

## 8. Read admission and conditional forward flow

A control-reached static read waits for a compatible published FLOW or group
write. This is a join of old/new reads with old/new actual writes:

```kotlin
fun onRead(read: ReachedRead) {
    reads.registerOnce(read)
    admitFreshRegions(read, writtenRegions)
}
fun onPublishedWrite(write: ReachedWriteEffect) {
    val fresh = writtenRegions.unionAndGetGrowth(write.scope)
    queueMatchingReadAdmissions(fresh)
}
fun admitFreshRegions(read: ReachedRead, written: ScopeSet) {
    val fresh = read.compatibleRegions(written) - read.admitted
    read.admitted.union(fresh) // commit before scheduling
    for (image in read.forwardFlowImages(fresh))
        enqueue(read.outputStage, edge(read.staticFieldFlowInitial,
                                      image, DEMAND, read.support))
}
```

For `x = C.f`, the conditional edge is `C.f.* FLOW -> x.* FLOW`, subject
to the ordinary read AP relation and cut. It is not `Z -> x`. Its static
initial survives local copies, argument/formal binding, returns and summary
composition. It carries no supplied group by itself.

A direct static mark condition registers a local conditional field input
at that condition's actual stage; it can then request its declared groups.
Bare class-point primitives have no abstract class initial. They match
the declared groups directly against compatible class-point supply and
emit only legal group descriptors. No broad current static data is stripped
as synthetic control merely because its path is short.

If no write exists, retain only the pending read descriptor. Do not emit
its conditional dependency or block completion. A late write admits fresh
regions once. Commit group write presence before group supply. A hypothetical
prescan write or `C.f -> C.f` pass identity is not a published write.
A permitted static source, model or boundary value counts as a write at its
actual eligible stage, even without an IR store. Its configured existence
alone does not satisfy its condition or create a published value.

ZERO/control, independent source actions, normal control return and eligible
non-mark or residual-negative rule handling do not wait for this taint-read
gate. A negative check is not proof of a supplied group. Retain its full
predicate and the ordinary eligibility test for later refinement. A source on a
getter's output can execute independently when its condition permits it.
Own-method reads/checks consume own-method published writes too.

## 9. Immediate static summaries and grounded group supply

At a reached actual static write, publish its static effect immediately in
the ordinary summary store as DEMAND. Do not propagate that static current
fact through the remaining run-1 CFG. At a caller, apply the effect through
the ordinary call bindings/actions; publish a resulting static conclusion
immediately as another DEMAND summary. Nonstatic results propagate normally.

```kotlin
fun onStaticEffect(effect: ReachedStaticEffect) {
    publishWritePresence(effect.scope)
    publishEarlySummary(effect.asDemand())
    publishGroupsWhenGrounded(effect)
    // No local static successor propagation.
}
```

Immediate means store insertion without waiting for method exit. Keep the
usual method-queue-empty batching of summary notifications. Early effects
do not prove normal exit, entry-to-return identity or caller continuation.
`publishEarlySummary` must use the ordinary live publication protocol:
retain the raw summary, update selected demand guidance, and enqueue its
fresh publication for subscribers. The baseline `RunSummaryStore.addDemand`
alone updates guidance; it does not perform these three operations.
Do not generate redundant `C.f -> C.f` transport summaries or executable
run-1 `S.* -> S.*` defaults. A real operation at the same field that changes
a mark/group or other semantic relation is productive and must be retained.

A source/model/boundary group starts supply only with its actual permission
and all required rooted guards. A writer with an ordinary or static initial
is a conditional row until that input and its full required support are
grounded. In particular, merely processing `p/Group(g)` after a hypothetical
request does not ground `C.f/g`. A ZERO-shaped GEN/end-fact effect also keeps
its original condition/support obligation; ZERO shape alone is not a root.

Use forward joins over existing ordinary input/summary support and reached
writer rows. Grounded input growth joins old writer rows; a new writer joins
old input/support. Rooted source/boundary facts seed the closure. A writer
fires only after all declared positive inputs/guards are supported. For a
new rooted field/group, commit write presence and its supply bit before
scheduling consumers. Retain only merged group support bits and ordinary
support references; do not build a backward local-provider graph.

For `C.f = C.g`, the reached conditional writer is a bulk forward subscriber
to all rooted groups at its original C.g scope. Apply its actual forward
group operation before publishing C.f supply. For an ordinary-origin writer,
a C.f/g interest can offer g on the known original Base(p) through P/R.
Only an actually supported input answer can ground the field result.
An available rootless copy cycle supplies no group. A late source seed can
grow its forward closure. No consumer request walks that cycle backward.

Rooted group closure is required for genuine supply. It is distinct from
the obsolete pass that annotated every helper with a group permission.
No such permission-annotation pass is required by this design.

## 10. Direct group refinement and caller use of static premises

When a group-sensitive operation sees a surviving mark-preserving FLOW
edge, route its interest from that edge's original:

```kotlin
fun needGroups(point: ProgramPoint, input: MarkPreservingFlowEdge,
               offered: GroupSet) {
    when (val initial = input.initial) {
        is StaticFieldFlow -> statics.subscribe(initial.scope,
                                continuation(point, input), offered)
        is OrdinaryBaseFlow -> temporary.offer(initial.base, offered)
    }
}
```

A static subscription stores wanted/delivered group sets for its actual
input stage, scope region, current image and support. Join old supply with
new interest and new supply with old interest. Compute fresh compatible
images, commit delivered bits, then enqueue the refined TAINT edge at that
operation input. Re-run the operation and its ordinary guards/actions.

```text
at point P: C.f FLOW -> x FLOW
interest:   group g at initial C.f
answer:     C.f/Group(g) -> x/Group(g), DEMAND, at input P
```

Replace the abstract initial selector in the refined premise. Do not put
a TAINT conclusion under an unchanged FLOW initial; preserve K6. A distinct
region/image for the same g still needs delivery. The reply does not seed
or mark complete a method-entry analysis. Requests and continuations are
not sources and cannot answer themselves.

At a reached run-1 call, ordinary ZERO can activate a static-premised
conditional summary, including a late summary. Retain the static premise:

```text
callee: C.f FLOW -> return FLOW
caller ZERO at call
result: C.f FLOW -> lhs FLOW, DEMAND
```

ZERO supplies control activation, not the static mark or a nonstatic/ND
premise. Retain every other guard. Group-valued conditional summaries still
need their actual compatible group supply/support. No application is a
new ZERO source hit.

For a mark-changing operation, request its declared input groups at that
operation's input, then execute its real forward operation. Do not request
an output group at the original slot as if the edge were an identity.
Keep its original full-mark relation for later runs. RemoveAll and definite
kills delete the branch; no continuation can revive it beyond that stage.

## 11. Complete handoff and backward run-2

At a complete run-1 barrier, keep the normal selected summary-demand handoff,
all supported internal sink occurrences/full predicates, exact source/rule
catalogs and ordinary retained records. Read an early static effect as a
DEMAND publication. A forward `Z -> C.f/Group(g)` gives a backward pair with
entry C.f/Group(g) and **explicit control-ZERO exit**, not an absent exit.

Group demands select every eligible full member of the owner group, not
one representative or only members observed in run-1. Keep geometry,
context, full literal occurrence and ND/condition support. Backward sink
seeds use the original full predicates and their complete required positive
scope. An internal sink seed is independent of a method-return summary.

Install one shared implicit static demand for every eligible method key,
including a later-created actor:

```text
for every eligible group g:
  entry pattern = S / empty path / ANY  / Group(g)
  exit pattern  = S / empty path / STAR / Group(g)
```

Store an enabled view plus the complete group catalog. Do not materialize
a method-by-field-by-group product or restrict it to run-1 actors/supplied
fields. The patterns admit an actual full C.f/T query and its computed
same-group static result while retaining the actual AP and T. A FLOW-only
root demand cannot replace this rule: the baseline abstract emitter erases T.
The default is neither an executable summary nor a root taint initial.

Preserve the pinned **actual-query admission** rule for all later runs:

```kotlin
actualCanonicalQueries = boundedQueriesFrom(actualAddedFacts)
additionalQueries = concreteIntersections(actualAddedFacts, oldGuides)
admit(actualCanonicalQueries + additionalQueries)
```

Absent old guidance does not veto an actual marked query. Each admitted
current premise authorizes its actual computed results; an old exit hint
does not discard them. Require the complete premise set for ND. This rule
also covers nonstatic, source-ZERO and cross-group productive results that
the shared static pair alone does not describe. It does not create T from
FLOW or from a group catalog entry.

Therefore, do not add a mandatory `GroupGuideBuilder`, helper prefix-mask
closure, per-point permission ledger, writer-search jump graph, or frozen
incoming-port replay service. Productive pairs can be ghost proof witnesses
and ordinary guidance. They are not a new restrictive runtime gate.

Run-2 executes its real full-mark reverse primitives, call stages, cleaners
and strong overwrites. It creates fresh ordinary subscriptions for actual
queries. Run-1's relaxed static order/context does not bypass that execution.
No special backward static solver is required.

## 12. ZERO discovery, sources and later confirmation

Only a genuine initial-ZERO/current-REACH call item starts ZERO discovery,
fires its recorded internal sink seeds, and installs zero-only subscriptions
to each resolved callee. A callee ZERO-premise summary returns at the exact
caller stage under the caller's ZERO initial, without old exit restriction.
Ordinary body transfer must still carry the requirement to the caller entry.

```text
wrap_deep sink:   Z -> t/T
getter reversal: return/T -> C.f/T
wrap_deep entry: Z -> C.f/T
wrap entry:      Z -> C.f/T       // zero-only balanced return
main beforewrap: Z -> C.f/T
pass_only query: actual C.f/T     // actual query + shared static demand
setter reversal:C.f/T -> t/T -> CONTROL_ZERO
```

`C.f/T -> current ZERO` keeps its nonzero initial. It cannot start unrelated
ZERO discovery or sink seeds. A wrapper overwrite can kill the returned
requirement; ZERO permission supplies no identity around the kill.

Record a source hit only when the exact actual reversed full source action
applies. Enumerate every eligible matching source/action occurrence; do not
select one representative when two occurrences produce the same AP/full mark.
A different full U in the same group is not T. Preserve method key,
statement, original full forward PathEdge and required occurrence. An early
static effect is not a substitute source identity. Collect hits independently
of summary exit filtering. They become exact `Seed.Source` permissions for
the next full forward run.

A backward ZERO-premise sink path is a requirement, not a forward source.
Do not persist it as a NORMAL forward record. Ordinary full forward analysis
starts from real roots and exact permitted sources. Report confirmation still
requires the baseline full path, mark/condition, call and ND certificate.
Run-1 group candidates cannot confirm a finding.

## 13. NORMAL records and the first barrier

This specification explicitly replaces the baseline's blanket run-1 record
quarantine. Persist independently valid NORMAL summaries after a complete
run-1 barrier. Every group-taint/static-dependent/early-static result is
DEMAND and is excluded. A valid NORMAL FLOW group exclusion can persist
as a restricted-domain record, with its required repair retained and drained.

Old Group exclusions and new Full exclusions keep their typed denotations
in native and reversed readers. A record excluding g cannot answer or close
an actual later Full(g,U) query. Preserve current analysis for it. Keep the
pinned must-record, exactness, reversed-view and zero-back-binding rules.
No raw record or shared static demand can override an actual body kill.

Do not persist an incomplete run or release required support/catalog state
before a complete handoff. Static supply, requests and point work are
run-scoped. Source/full rule metadata and valid records keep their ordinary
longer lifetime. Form reclamation and IR storage are outside this change.

## 14. Owner commits, batches and completion

Keep baseline actor/runner ownership, register-before-snapshot joins,
summary batching, cancellation, work reservations and complete report
barriers. Static batches commit at method-queue-empty. Record fresh write
regions, admitted read regions, grounded group bits and delivered images
before scheduling callbacks. Every cross-owner send has counted work.

A complete barrier drains ordinary work, P/R commits and joins, summary
publication/application, read admissions, grounding/support growth, static
group joins, current-point replies, rule reevaluation and provider/catalog
extensions. A waiting read or unanswered interest without available supply
is dormant state, not unfinished queued work. Do not add arbitrary pending
memory thresholds or global `graphChanged` replay waves.

For finite regions/groups, each supply bit and each continuation-region-
image/group delivery bit grows once. Grounding a previously conditional
ordinary premise joins its old reached writers/call inputs; it does not
replay all method entries or rebuild all static routes. Batch size and
duplicate attempts may change diagnostic callback counts. Semantic rule
occurrences, source/sink identities and reports keep their contracts.

## 15. Removal and retention plan

Implement on the pinned baseline. Post-baseline components are **not ported**;
this is different from deleting them in the current checkout now.

| Component | Required disposition |
| --- | --- |
| Baseline transitive F/B/W static plans and inherited B | Remove; retain only legal geometry and reached local descriptors |
| `FrozenStaticRequests`, static R/P rows, wide/class static request views | Remove; ordinary nonstatic Base requests retain P/R |
| Baseline W-dependent executable S-root default | Remove from run-1 execution; install the implicit GROUP demand after run-1 |
| Baseline inherited same-slot request suppression | Remove; selected cleaner/check interests stay active even when excluded or unchanged |
| `staticRootFact`/`withoutStaticRootFact` dispatch guards based on current geometry | Remove from rule/condition handling; no synthetic root initial does not make actual broad static data control-only |
| `Run1StaticFlow`, `Run1ConcreteStaticFlow`, `StaticIdentityEntries`, `ConcreteStaticEntries`, `ConcreteStaticSubscriptions` | Do not port packed bottom-up static lanes/codecs |
| `StaticNeedRouter`, `PendingNeedReplay`, need waves/replay statistics | Do not port the reverse static graph or graph-change traversals |
| `CallerOwnedStaticRequests`, `FixedPrescanReachability`, `Run1PreparedInitials` | Do not port static request routing/prepared static input machinery |
| `StaticSubscription`/`ConcreteStaticSubscription`, specialized static events/ports/runner hooks | Do not port; use ordinary summary/control protocols plus local static service messages |
| Post-baseline callsite pending/answered stores, exact/view request slots, `coveredStaticRelay`, shared static method-demand views | Do not port those static request caches |
| Proposed helper permission-mask closure / `GroupGuideBuilder` | Remove as mandatory runtime work; baseline actual queries already authorize starts/results |
| Proposed backward provider graph, writer-search jumps, incoming-port snapshot solver | Remove from this design; ordinary run-2 execution supplies the reference path |

Retain InitialFactStore, MethodEdgeStore, ordinary RunSummaryStore and
PublicationIndex, SummaryStorage's concurrency protocol, ordinary caller
N2/AddedFactAccumulator and candidate index, full CallerRef provenance,
whole ND/KaryJoin and predicate state, ordinary summary replay, ZERO rules,
StrongQueries, exact source hits/seeds and valid record readers.

`AddedFactAccumulator` is not a redundant callee history. It is used by
ordinary caller joins and observation deduplication. Do not blanket-remove
`AddedFacts.kt`, CalleeSubscriptions or ordinary input storage. Do not add
a second callee copy. Keep pure AP/identity laws when useful; discard only
the obsolete transport implementations.

## 16. Required validation and implementation gates

The proof dossier must contain constructive witnesses for ownership/full
projection, cleaner survivor repair and mixed exclusions, rooted static
supply/read admission, direct point refinement, merged joins, early source
effects, ZERO wrappers, shared static demand, actual-query productive replay
and the combined finite handoff. A theorem that assumes every required
guide is already present is not this coverage proof.

The production adapter must preserve each primitive's full mark relation,
AP endpoint, actual method/call stage, bound condition and all support slots.
Runtime tests must check: the complete getter/setter/wrapper/pass-only trace;
a real kill; two distinct full marks in one group; selected-cleaner repair;
conditional source/ND guard completeness; late read/write/request arrivals;
static copies with and without a source; ordinary/static original routing;
class/broad data; and exact SourceHit-to-Seed.Source identity. A callback-count
match is not semantic validation.

Use the baseline common/JVM/application gates and fixed project settings.
Report finding identities against the same main reference and every reached,
completed and accepted run. The target is run-1 engine time within 60 seconds.
No proof here establishes that time bound. Do not change the IR, cache/reference
manager or other analyzer algorithms as part of this implementation.

## 17. Explicit baseline compatibility map

| Pinned owner | Baseline behavior | Selected behavior |
| --- | --- | --- |
| `run1-field-writes.md`, selective transfer | Definite kills/disjoint preservation; E-update residual demotes without tail loss | Retain exactly |
| `run1-requests.md`, ordinary requests | Merged P/R, commit-then-replace, original-slot replay | Retain for nonstatic Base slots; group members replace full IDs |
| `Run1Abstraction`, F/B static admission | Static entry seeds and request-gated concrete starts | Replace with local reached read/write join and point refinement; no static entry lane |
| Transitive StaticMayWrite W and default | W chooses executable NORMAL/DEMAND S identity | Remove; no run-1 root execution; all early static effects DEMAND |
| Run-1 ordinary concrete taint | Full marks, some exact NORMAL starts | Group payloads; all group taint DEMAND |
| Selected cleaner/mx | Exact full-mark cleaning/exclusion | Group exclusion plus non-cleaning group repair in run-1; full rules from run-2 |
| Direction/limit policy | Positive alternating runs with increasing limits | First two limits 1, then increase; run-1 GROUP, later FULL |
| StrongQueries and current publication | Actual inputs/results authorized independently of old guides | Retain; no new strict per-method veto |
| Backward ZERO and sources | Internal seed discovery, zero-only balanced return, exact source hits | Retain exactly; no group on ZERO |
| Record barrier | Quarantine all run-1 records | Export independently valid NORMAL only; never early/group/static-demand candidates |
| Ordinary AP/NNF/F74/F75/F76/F77 | Full primitive semantics, signed folds, tail/exclusion and support rules | Retain except the explicit typed selector/mode changes above |
| IR/model/compiler caches | Existing IR; shared full forms | Retain IR and cache policy; use mode-aware mark views without mutating full forms |

This specification takes precedence at these seams over the earlier separate
mark-group, static-pool, immediate-summary and universal-carry proposals.
Those documents and their counterexamples remain historical explanation
and local proof evidence. They do not add hidden runtime phases or storage.

## 18. Measurement checkpoint

No new project runs are part of this specification. The selected baseline
has the following reached / completed engine / accepted forward runs, from
the [selective-preservation report](../docs/reports/bidirectional-run1-selective-preservation-20261016.md):

| Project | Selected baseline | Baseline run-1 engine |
| --- | --- | --- |
| OWASP | run-7 / run-6 / run-5 | 263.421 s, COMPLETE |
| Conductor | run-2 / run-1 / run-1 | 49.567 s, COMPLETE |
| ThingsBoard | run-1 / none / none | 176.913 s, OOM |
| Stirling-PDF | run-2 / run-1 / run-1 | 36.586 s, COMPLETE |

The latest measured post-refactoring runtime remains
`63d5481e4e4b69ecf042c1149bb574ad40fd1624`: OWASP **run-7 / run-6 /
run-5**; conductor **run-1 / none / none**; ThingsBoard **run-1 / none /
none**; Stirling-PDF **run-2 / run-1 / run-1**. These later measurements
come from the [latest measured report](../docs/reports/bidirectional-caller-owned-subscriptions-performance-20261018.md).
They do not measure the selected new specification. Baseline strict project
comparison had 16 missing and 342 additional identities; implementation
acceptance must report these differences rather than assume a green base.
