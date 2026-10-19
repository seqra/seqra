# Mark groups and direct static handling

Date: 19 October 2026. Revision: **2.1, source and semantic-scope clarification**.
Runtime conformance to this revision is pending. Language: ASD-STE100 Simplified Technical
English. Defined technical terms, program names and mathematical notation
keep their stated meanings.

This is the normative specification for the selected mark and static
changes. It follows [proof-guide.md](proof-guide.md). It contains the whole
feature contract. It replaces the previous revision, including its extra
writer-geometry validation and runtime grounding metadata. Earlier
proposals are not additional requirements.
The [proof dossier](grouped-run1-statics-proofs.md) maps requirements to
constructive models, validation and production adapter checks.
The [refactoring proposal](../docs/design/bidirectional-run1-rule-provider-and-grounding-removal-20261019.md)
maps the changed contract to code and lists the superseded parts of the
earlier implementation proposal. The spec is authoritative if a proposal
still describes runtime grounding state.

## 0. Selected semantic scope

This section follows [proof-guide.md](proof-guide.md). The comparison base
is **a80d5c580**, before the previous mark/static implementation. Its runtime
equals measured **28a123f0**. The intermediate implementation **ff2966455**
is not that base. Revision 2.1 clarifies the selected contract; it adds no
source-transfer, field-transfer or cleaner policy.

### Changes from the base

| Mechanism | Base behavior | Selected behavior |
| --- | --- | --- |
| Mark representation | Full marks on run-1 | Keep `(owner group, full mark)` identity; run-1 uses only the group, later runs use full marks. One whole top-level join rule has one group. |
| Conditions | Full-mark leaves | Project each mark to its registered group and simplify the GROUP condition. Preserve each position/check kind, original FULL predicate, source action and action ID. GROUP/FULL forms have separate cache keys. |
| Layers | Some exact taint starts/results can be NORMAL | All run-1 group TAINT and static-dependent results are DEMAND. Independent ordinary FLOW can remain NORMAL. Run-1 group candidates cannot confirm an exact finding. |
| Selected-mark cleaners | Can remove the exact selected mark | Request the selected position's group; retain actual group taint as DEMAND. Keep the FLOW complement and independent DEMAND repair. A group is never removed by this cleaner. |
| Static flow | Static entry/request plans and propagation through method/call paths | Use a new run-scoped write/read/group service. Reads need a reached write; supplied groups need an actual derivation. Refine at the saved operation input. Do not relay static current facts through the run-1 CFG. |
| Static precision | Ordinary static order and caller bindings constrain propagation | The global run-1 pool deliberately ignores static call order and caller context. These extra candidates are DEMAND. Later FULL runs use actual CFG/call transfer and strong writes. |
| Static summaries | Ordinary return publication and W-dependent executable S defaults | Publish reached static effects early, with complete ordinary premises and existing notification batching. Remove executable run-1 S defaults. After run-1, add one implicit shared static DEMAND for actual backward query permission. |
| ZERO at a call | Does not supply a static premise | Only in run-1, ZERO can activate a conditional static-premised summary while retaining that premise. It supplies no group. Later runs require the actual static fact. |
| Requests | Full-mark Base and static requests | Reuse Base P/R with group sets. Remove static request slots/plans; use direct static interests and actual write/supply joins. Ordinary FLOW summaries still apply directly to actual caller group inputs without a Base request. |
| Sink end-fact actions | Execute configured `trackFactsReachAnalysisEnd` actions | Treat this list as empty in every run. Keep sink conditions/seeds and ordinary source actions. This is an explicit rule-policy change beyond run-1. |
| Persistence | Quarantine run-1 records | After a complete run-1, persist independently valid NORMAL summaries only. Group/static speculative results remain DEMAND. Typed Group/Full exclusions keep their denotation in later readers. |
| Field-limit schedule | Default limits `[1,2,3,4,...]` | Limits `[1,1,2,3,...]`: run-1 GROUP forward; run-2 FULL backward at limit 1; later FULL runs increase the limit. Direction still alternates. |

### Mechanisms reused without a semantic change

- **AP and fields:** initial descriptor geometry, tails, ordinary entry
  conversion, definite overwrite kills, disjoint preservation, pre-store
  RHS evaluation and weak aliases. New static point resumes use these
  existing AP relations, without another entry conversion. An E-update
  residual still moves to DEMAND with empty E. There is no new field rule.
- **Source actions:** existing AP transfer, bound positions, original
  incoming Facts, complete premises, permissions and action order. Only
  their mark selectors and result layer have the changes listed above.
- **Ordinary calls and joins:** current caller N2 inputs, complete compatible
  ND bindings, CallerRef and ordinary summary application. No extra callee
  input history, method-wide root mask or replacement join is added.
- **Ordinary requests:** Base P/R ownership, merged storage,
  `commit(temporary)` then replacement, both arrival orders, and ignoring
  E/mx for request matching. Members become groups; static rows are removed.
- **Kills and confirmation:** actual RemoveAllMarks admission, structural
  kills, actual-query rules, ZERO seed discovery, balanced backward calls,
  exact FULL source hits/seeds and exact later report confirmation.
- **Ownership and infrastructure:** method actors, method-queue-empty
  notifications, counted child work, cancellation, IR and managed soft
  references. No new barrier policy, clipping cap or cache manager is added.

Relative to **ff2966455 only**, delete GroundingSupport, guard formulas,
fixed certificates, cofactors, support revisions and input-root masks.
Remove its extra exact-field writer validator. This restores the base's
bounded DEMAND eligibility; a widened sibling-field candidate may survive
as DEMAND. These mechanisms never existed in a80, so their deletion is
not another semantic change from that base.

Relative to **dac301f2e**, restore publication of reached static alias/model
value effects. That implementation omitted write descriptors on two JVM
alias paths. Keep their existing AP transfers and complete premises. The
[adapter correction](../docs/design/bidirectional-run1-static-alias-publication-fix-20261019.md)
adds the missing descriptors; it adds no field rule or new approximation.

If a proof or implementation needs another major semantic change, expose
it and obtain the intended behavior before implementing it, or retain this
scope. Do not add a hidden helper to change the contract.

## 1. Pinned implementation baseline

The unchanged AP/field primitive baseline is:

```text
primitive baseline:a80d5c580b3b4715ab39679fbf66f0f190125fa0
same measured code:28a123f0eac937e8f2feca9ee5dd84e38fa8f979
core tree:         db89e5f9dd4a142b6a093006193c22e7e416abd3
rules tree:        e2a7d29770276ca7d4d0efe974914f687ca67def
first bottom-up:  ab6c3cd006cb62e6a017c0e3c32d96573d2214e3
```

The selected checkout has no bottom-up static lane or static-need graph.
Its runtime equals the measured request-gated/selective-preservation
version. The immediate parent of the first bottom-up implementation has
the same runtime but already has future bottom-up design text. Use the
selected checkout for inherited primitive/specification comparison.
This task records the pin; it does not reset the current workspace.

The delivered implementation to refactor is
`ff2966455da2e5ffd0db655a1ff06c5954ed3c8a`. It already contains the new
mark/static feature and the grounding state that this revision removes.
Do not reset that implementation to a80 for this refactoring. Use a80 to
check the unchanged primitive, request, call, ZERO and record contracts.
The previous spec is retained in Git at `9132d9e22:spec/grouped-run1-statics.md`.

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
An edge has an initial premise or complete premise set, a current fact
and an actual program stage. There is no runtime guard formula, fixed-image
certificate, cofactor or grounding revision on that edge.
NORMAL means independently valid
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
| Rooted supply | A finite source-backed derivation in the ordinary bounded run-1 AP/group relation, with every required premise matched |
| Complete binding | One compatible singleton/ND application at the same actual caller/callee port, statement, stage and layer, with every required member matched |
| Early static effect | A reached static conclusion published as a run-1 DEMAND summary before method exit |

Static scopes include exact points and symbolic descendant regions.
Compatibility requires an actual AP intersection and the correct projected
current image. An exact class point does not supply its fields. A broad
class region can intersect field regions. Do not expand broad regions into
every declared field. The finite proof oracle uses certified overlap cells.
These mathematical witnesses do not require runtime support objects.

The proof target is inclusion of each finite full-mark witness admitted by
the pinned reference contracts with the effective empty end-fact policy in
section 5. For this semantic proof, apply that policy to both analyzers.
The project comparator still reports all strict main-reference differences.
Flows that require the disabled end-fact actions are outside this iteration's
reference scope.
It is not equality of run-1 precision:
run-1 can ignore static call order and caller context, but supplied groups
must have source/writer derivations. All resulting static candidates are
DEMAND. Run-2 and later confirmation use ordinary real CFG/call transfer.

The writer relation uses the earlier DEMAND field abstraction. It does not
recheck each historical guard against an unabstracted actual input:

```text
actual p.left/g -> ordinary initial p.*/g -> entry p.[any]/g
check on p.right/g can succeed under this abstraction
result C.out/g is DEMAND, not an exact field witness
```

This restores the selected field contract. It changes the extra writer
eligibility check in the previous revision, not field-transfer semantics.
Later FULL runs reevaluate the original condition. A bounded FULL run
need not reject every approximate candidate immediately.

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

The new-core provider supplies two immutable selected views:

```kotlin
enum class RuleView { RUN1_GROUP, FULL }
fun ruleView(run: Int) = if (run == 1) RUN1_GROUP else FULL
```

Keep original FULL rules and stable occurrence/action IDs. GROUP conditions
are typed NNF over `(bound position, check kind, registered owner)`.
Do not manufacture group names in the full-mark string namespace.
The action mark gate still executes the declared input/output relation.
An action target uses its own registered owner.

Resolve each imported condition leaf with its registered owner. Equal-owner
aliases at the same bound position/check kind can merge. Legal mixed-owner
conditions remain legal. Do not assign them to the consuming rule or add
a new single-owner rejection.

Select compiled rule payloads by view as well as direction. Run-1 and
run-3 are both forward; direction alone is not a cache key. FULL backward
forms reverse FULL forward forms. Do not overwrite a shared FULL payload
with GROUP forms. Keep IDs stable across eviction/rebuild. Reuse managed
soft references; do not change the reference manager or add a weak cache.

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
No additional exact-field writer validator follows these ordinary
operations. Point materialization still uses the real represented AP
relation; deletion of a validator does not permit arbitrary AP matches.

## 5. Conditions, cleaners and NORMAL exclusions

For this iteration, treat `trackFactsReachAnalysisEnd` as an empty list in
**every run**. Apply this new-core rule policy to method, entry and exit
sinks before action resolution/validation, effective mark-catalog collection
and form compilation. Ignore only that action list. Keep each sink's full
condition, occurrence, finding and backward seed.

Do not emit these end facts, collect their static effects or mark interests,
or create forward GEN/reversed END_FACTS triggers from them. Marks used only
by those disabled actions are not eligible for this iteration. A mark still
used by a condition, source or other retained rule keeps its ordinary role.
Source actions and conditional source rules remain enabled. Do not modify
the legacy provider's shared rule objects or the IR.

Keep the original signed full-literal NNF DAG and rule metadata for FULL
runs. The new-core provider constructs the executable run-1 condition in
this order:

```text
exact signed FULL folds
-> metadata/position binding
-> exact signed FULL folds again
-> existing role admission and eligible residual-negative possibility policy
-> project each remaining positive leaf to its registered group
-> normalize positive GROUP NNF
```

Fold exact full literals before group projection:

```text
P(T) AND NOT P(T) -> false
P(T) AND NOT P(U), T != U -> distinct, even if owner(T) = owner(U)
```

Keep the baseline eligible residual-negative projection. Group presence
means possibility for a positive check, not exact truth or absence.
`P(T) AND NOT P(U)` with different full marks must not become false because
their groups agree. Do not apply a signed GROUP contradiction/tautology fold.

Use constant folding, equal-child elimination, same-connective flattening,
idempotence and positive absorption. Share the NNF DAG. Do not expand cubes
or distribute AND over OR. For equal positions/check kinds and owners:

```text
P(x,T) OR  P(x,U) -> P(x,g)
P(x,T) AND P(x,U) -> P(x,g)
(P(x,T) OR P(x,U)) AND (P(y,T) OR P(y,U))
  -> P(x,g) AND P(y,g)
```

Distinct positions, paths, check kinds and owners stay distinct. A group
at one argument does not satisfy another argument. Real spatial/ND witness
choices remain. Remove only alias proof products. Preserve TRUE/FALSE
classification after the existing signed/role policy: in particular, a
remaining positive atom cannot become unconditional merely by grouping.

Every group-dependent condition/source result is DEMAND. Retain original
FULL predicates, occurrence/action IDs and complete accepted GROUP premises.
GROUP leaf/proof ordinals are local to that view; they are not FULL ordinals.
Diagnostic callback/proof counts can change. Semantic occurrence/report
identities and every configured action remain.

### Source conditions and unchanged source actions

The rule is a mark projection. For example:

```text
CM(p1, (g,T)) AND CM(p2, (g,U))
  -> CM(p1, g) AND CM(p2, g)
```

Keep distinct bound positions distinct. Use the existing condition matching, complete
ordinary binding and source action. Project the action's mark to its own
registered group and emit the result as DEMAND. Preserve its AP transfer,
tails, original bound positions, incoming Facts, action order and ActionId.
FULL runs use the original full condition and actions.

No new source-case analysis, provider API or runtime store is required by
this specification. The earlier UNCONDITIONAL/UNARY/MULTI discussion names
existing interpreter paths. It does not define new source semantics.
There is one compiler compatibility check: folding equal-position aliases
must not select a different AP transfer because the guard has fewer leaves.
In particular, a unary transfer still receives the original incoming Facts.
The detailed source-family model is proof/regression evidence only.

### Selected-mark cleaners

`RemoveMark(T)` has different run-1 behavior from its FULL behavior.
Request the removed position's group g. Never remove an actual Group(g)
fact in run-1, even if the catalog currently has only one full mark in g.
Retain that fact as DEMAND so later FULL analysis can apply the exact
cleaner. A request alone creates no taint; repair needs an actual matching
input or static supply. Keep the original position and operation stage.

For a selected cleaner of T in group g:

```text
FLOW I -> x except X:
  request g on the actual abstract original I
  preserve FLOW except (X union {g}) in its incoming permitted layer
  retain the independent DEMAND repair for g

Group(g) -> selected cleaner(T): retain Group(g), DEMAND
Group(h), h != g:                retain Group(h), DEMAND
```

The FLOW complement can remain NORMAL. It describes only the branch that
excludes g; it does not prove that this cleaner removed g from the program.
An actual g continues on the independent DEMAND repair. This is the earlier
selected complement/repair policy, not a new permission to clean a group.

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
Publish every reached static value effect from an existing alias/model
write, with its complete original premise. The lowerer must identify those
value transfers as writes. Keep their full alias paths and actual call
stages. Do not classify keep edges, strong-preservation edges or static
bindings as writes merely because their destination is static. A write
descriptor alone creates no read, interest or supplied group.

Do not compute transitive method-by-field or method-by-group sets, F/B/W
closure, inherited static requests, or a separate run-2 writer-search graph.
The ordinary backward engine creates fresh actual subscriptions at real
call stages. It needs no frozen run-1 marked subscription or global writer
jump to follow a reference witness.

| Store | Owner and purpose |
| --- | --- |
| Full mark/owner catalog, original rules and selected forms | Existing provider/cache lifetime; explicit GROUP/FULL payload selection |
| Method edges and ordinary work | Existing method actor; AP admission/deltas only; no runtime support metadata |
| Initials and summaries | Existing method actor; ordinary computed edges and early static effects, with complete premises |
| Ordinary input/summary/request subscriptions and ND joins | Baseline caller N2 and SummaryStorage ownership |
| Published write regions | One run-scoped static service; admits local reads |
| Rooted supplied groups | Same service; one group set per normalized static region |
| Pending reads | Same service; reached local descriptor plus admitted regions |
| Refinement continuations | Actor-owned actual operation input, conditional AP image, complete premise and call/cleaner phase; service indexes wanted/delivered groups per region/image |
| All-static summary subscriptions | References to standing ordinary early effects; actual pool inputs matched at the owner |
| Ordinary-origin FLOW writer interests | Early-summary references indexed by static output; fresh wanted groups route to the known Base original through P/R |
| Temporary static batch | Per-method writes, consumers, interests and supplied group growth before commit |
| Run-2 static default | One immutable implicit GROUP demand view, section 11 |

Pool scope is global for run-1's accepted order/context approximation.
Local kills do not erase its historical union. Every pool group still needs
a rooted derivation for that field region. An exact class point, request,
FLOW identity, catalog entry or prescan writer candidate cannot supply it.
Runtime stores merged memberships and ordinary summary references, not the
Lean derivation tree for every field/group answer. A service lock does not
inspect another actor's mutable stores. It returns immutable candidate IDs
and counted owner events; the owner borrows its current relation and runs
ordinary AP transfer after the service lock is released.

Keep the existing current N2 caller inputs. Do not copy them into a callee
history or a static grounding directory. No owner stores guard formulas,
fixed certificates, cofactors, method input-root masks, support revisions,
or a replacement validator/solver. A qualified N2 handle can remain only
where ordinary caller/control joins need that current input.

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
                                      image, DEMAND))
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

## 9. Immediate static summaries and ordinary group supply

At a reached actual static write, publish its static effect immediately in
the ordinary summary store as DEMAND. Do not propagate that static current
fact through the remaining run-1 CFG. At a caller, apply the effect through
the ordinary call bindings/actions; publish a resulting static conclusion
immediately as another DEMAND summary. Nonstatic results propagate normally.

```kotlin
fun onStaticEffect(effect: ReachedStaticEffect) {
    publishWritePresence(effect.scope)
    val ref = publishEarlySummary(effect.asDemand())
    registerOrdinarySupplyAndWriterInterests(ref)
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

A source/model/boundary action must have its actual rule permission and
ordinary accepted condition binding. Its result keeps that binding's
original singleton or complete ND premise. Do not reset a conditional
source to ZERO. The empty end-fact policy requires no waiting sink-GEN state.

The pool admits a computed static TAINT result only by these paths:

1. A legitimate ordinary ZERO-rooted effect supplies its result directly.
   The source/control rule must actually execute under its selected view.
2. An effect with a nonempty all-static premise matches actual pool inputs
   for every member. Apply the complete ordinary relation at its owner.

An ordinary nonstatic or mixed premise stays conditional. Compose it
outward through ordinary singleton/ND caller application. Preserve every
unresolved original and same-call/stage compatibility. Continue until the
result has a legitimate ZERO or complete all-static binding. Do not replace
this relation with method-wide input-root bits. Separate calls
`M(marked,clean)` and `M(clean,marked)` cannot complete one two-input writer.

Ordinary caller application uses the actual N2 input, before callee-entry
abstraction. A FLOW summary can therefore carry an actual group without
a request or a marked callee initial:

```text
actual caller input: p/Group(g)
callee summary:      p/FLOW -> C.f/FLOW
ordinary result:     C.f/Group(g), with the caller's complete original
```

This is an existing mark-preserving summary operation. Do not gate it on
Base requests. Its resulting actual supply can activate all-static FLOW
copies to further fields, without reverse request routing through those
copies. A conditional caller original still needs ordinary outward
composition; an analyzed group initial alone remains insufficient.

```kotlin
fun registerSupply(ref: SummaryRef, effect: Publication) {
    when {
        effect.premise.isZero && effect.conclusion is TaintTree ->
            offerComputedPoolResult(effect.conclusion)
        !effect.premise.isZero && effect.premise.allMembersAreStatic ->
            subscribeOrdinaryStaticSummary(ref)
        else -> Unit // actual ordinary caller application remains necessary
    }
}

fun applyStaticCandidates(ref: SummaryRef, values: List<Facts>) {
    val effect = borrowCurrentSummary(ref)
    require(values.size == effect.premise.size)
    val matched = effect.premise.members.zip(values).map { (initial, value) ->
        ordinaryOps.satisfying(value, initial) ?: return
    }
    ordinaryOps.applyMatchedSummary(effect, matched) { result ->
        if (result is TaintTree) offerComputedPoolResult(result.asDemand())
    }
}
```

Each candidate list is one ordered complete compatible tuple, not a flat
batch of alternatives. An index suggests candidates; it does not prove
AP matching. Check arity before `zip` and recheck every member before the
ND primitive, which expects already matched inputs. Use the actual output
AP image; do not supply the whole declared target when composition produces
a narrower image. Group identity alone is insufficient.

This rule includes FLOW effects. A reached `C.g/FLOW -> C.f/FLOW`
subscribes to all actually supplied groups at C.g. Apply its ordinary
mark-preserving operation to each fresh group set and publish the C.f
image. Do not wait for a separate marked summary; it might never be
requested. A mark-changing effect uses its declared input group and output
relation. Do not request its output group at the input as if it were identity.

For an ordinary-origin FLOW writer, join static output interests with its
standing ordinary summary reference:

```text
reached writer: p/FLOW -> C.f/FLOW
interest:      C.f/g
owner action:  offer Base(p)/g in temporary P
then:          ordinary P/R supplier answers and complete caller joins
```

Only a legal singleton mark-preserving FLOW relation takes this route.
Keep both writer-first and interest-first joins. Ignore field E and FLOW mx
for this request interest as in section 6. A concrete/ND/GEN writer must
not use the first premise or copy an output group to all input groups.
Fresh `(summary reference, wanted group)` memberships cause counted owner
events. An unanswered request is dormant, supplies no taint and does not
block completion. No consumer walks a static call chain or backward CFG.

A request or merely analyzed `p/g` initial cannot supply C.f/g. An empty
copy cycle cannot create a first group. A real late source seed can grow
the positive closure. For every fresh computed pool region/group, commit
its write presence and supply bit before waking reads or point consumers.
New summaries join old supply; new supply joins old summaries. Retain only
current ordinary relations and semantic memberships, not proof histories.

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
input stage, scope region, complete premise and current image. Join old supply with
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
need their actual compatible group supply and complete ordinary binding. No application is a
new ZERO source hit.

This exception applies **only in run-1**. In run-2 and all later runs,
ZERO cannot satisfy a C.f premise. An actual matching C.f fact is required,
with the ordinary AP, mark and complete-premise checks. The shared static
demand grants query permission; it does not supply that fact. Ordinary
ZERO-premise summaries and ZERO sink discovery keep their control rules.

For a mark-changing operation, request its declared input groups at that
operation's input, then execute its real forward operation. Do not request
an output group at the original slot as if the edge were an identity.
Keep its original full-mark relation for later runs. RemoveAll and definite
kills delete the branch; no continuation can revive it beyond that stage.

## 11. Complete handoff and backward run-2

At a complete run-1 barrier, keep the normal selected summary-demand handoff,
all reached internal sink occurrences/original FULL predicates, exact source/rule
catalogs and ordinary retained records. Read an early static effect as a
DEMAND publication. A forward `Z -> C.f/Group(g)` gives a backward pair with
entry C.f/Group(g) and **explicit control-ZERO exit**, not an absent exit.

Group demands select every eligible full member of the owner group, not
one representative or only members observed in run-1. Keep geometry,
context, full literal occurrence and complete ND/condition premises. Backward sink
seeds use the original full predicates and their complete required positive
scope. An internal sink seed is independent of a method-return summary.
Lookup FULL forms by that original occurrence, regardless of the last cache
access or the normalized GROUP leaf count. Do not decode a GROUP leaf ordinal
as a FULL leaf ordinal. Stable original candidate/action order precedes
filtering; every configured action remains selectable after rebuilding.

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

Do not persist an incomplete run or release required input/join/catalog state
before a complete handoff. Static supply, requests and point work are
run-scoped. Source/full rule metadata and valid records keep their ordinary
longer lifetime. Form reclamation and IR storage are outside this change.

## 14. Owner commits, batches and completion

Keep baseline actor/runner ownership, register-before-snapshot joins,
summary batching, cancellation, work reservations and complete report
barriers. Static batches commit at method-queue-empty. Record fresh write
regions, admitted read regions, rooted group bits and delivered images
before scheduling callbacks. Every cross-owner send has counted work.

A complete barrier drains ordinary work, P/R commits and joins, summary
publication/application, read admissions, static group/summary joins,
ordinary writer-interest requests, current-point replies, rule reevaluation and provider/catalog
extensions. A waiting read or unanswered interest without available supply
is dormant state, not unfinished queued work. Do not add arbitrary pending
memory thresholds or global `graphChanged` replay waves.

For finite regions/groups, each supply bit and each continuation-region-
image/group delivery bit grows once. A new actual input joins old summaries
and requests; a new summary/request joins old inputs. These are ordinary
owner memberships, not support-only revisions. Do not replay all method
entries or rebuild all static routes.

MethodEdgeStore admits only ordinary AP deltas. An AP duplicate must not
enqueue work because a formula, proof history or revision changed. Keep
the baseline transient unchanged-work path with a finite repeat check;
do not replace it with persistent pass-through storage. One burst visits
each ordinary repeat key once. Starting another burst requires fresh
bounded semantic work. Ordinary rule/request observations remain active
even when their value delta is empty; their own owner deduplicates them.

The progress model assumes a finite legal AP/premise/stage and dependency
universe for a finite bounded program/catalog. This universe must cover
all legal offers and transfer results. It is not a runtime cap or permission
to drop values. Under fair processing and without cancellation or unbounded
provider extension, fresh semantic admissions and finite bursts terminate.
A quantum yield can stutter; it is not a commit barrier and it must not
release pending batches or their counted work.

Count child owner work before the producer releases its reservation.
Waiting reads/interests are dormant; they do not require a future answer
for completion. Batch size and duplicate attempts may change diagnostic
callback counts. Semantic occurrences, source/sink identities and reports
keep their contracts. No proof here establishes the 60-second engine bound.

## 15. Removal and retention plan

The earlier feature removed obsolete baseline static machinery and did
not port the later bottom-up lanes listed below. Keep those removals.
This revision refactors ff2966455 and deletes its grounding subsystem.
The table distinguishes the historical removals from this new deletion;
it does not require rebuilding the feature from an old checkout.

| Component | Required disposition |
| --- | --- |
| Baseline transitive F/B/W static plans and inherited B | Remove; retain only legal geometry and reached local descriptors |
| `FrozenStaticRequests`, static R/P rows, wide/class static request views | Remove; ordinary nonstatic Base requests retain P/R |
| Baseline W-dependent executable S-root default | Remove from run-1 execution; install the implicit GROUP demand after run-1 |
| Baseline inherited same-slot request suppression | Remove; selected cleaner/check interests stay active even when excluded or unchanged |
| `trackFactsReachAnalysisEnd` lowering | Treat as empty in all runs; no sink end-fact GEN, static metadata, track-only eligible marks or reversed action triggers |
| `staticRootFact`/`withoutStaticRootFact` dispatch guards based on current geometry | Remove from rule/condition handling; no synthetic root initial does not make actual broad static data control-only |
| `Run1StaticFlow`, `Run1ConcreteStaticFlow`, `StaticIdentityEntries`, `ConcreteStaticEntries`, `ConcreteStaticSubscriptions` | Do not port packed bottom-up static lanes/codecs |
| `StaticNeedRouter`, `PendingNeedReplay`, need waves/replay statistics | Do not port the reverse static graph or graph-change traversals |
| `CallerOwnedStaticRequests`, `FixedPrescanReachability`, `Run1PreparedInitials` | Do not port static request routing/prepared static input machinery |
| `StaticSubscription`/`ConcreteStaticSubscription`, specialized static events/ports/runner hooks | Do not port; use ordinary summary/control protocols plus local static service messages |
| Post-baseline callsite pending/answered stores, exact/view request slots, `coveredStaticRelay`, shared static method-demand views | Do not port those static request caches |
| Proposed helper permission-mask closure / `GroupGuideBuilder` | Remove as mandatory runtime work; baseline actual queries already authorize starts/results |
| Proposed backward provider graph, writer-search jumps, incoming-port snapshot solver | Remove from this design; ordinary run-2 execution supplies the reference path |
| `Run1Grounding.kt`, `GroundingSupport`, `GroundingRequirement`, `GroundingArgument`, fixed certificates and guard FLOW-shadow validator | Delete completely; no renamed validator, edge label or formula solver |
| Method/point/rule work support fields, store cofactors, publication/N2 support revisions, call guard replay/rebinding and input-root masks | Delete; retain ordinary current AP inputs, complete caller/ND joins and semantic publication deltas |
| Static writer-support probes, forwarded grounded-input directories and grounding statistics | Delete; use ordinary early summaries, actual all-static joins and Base writer interests |
| GROUP dispatch over every original full alias | Replace with normalized GROUP conditions; reuse existing source AP transfers, original FULL metadata and action IDs |

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
projection, signed-to-positive bound GROUP normalization, unchanged source
action/AP semantics, cleaner repair and exclusions, complete ordinary writer
composition, rooted supply/read admission, point refinement, merged joins,
early effects, ZERO wrappers, shared static demand, actual-query productive
replay, exact source enumeration and the combined finite handoff.
It must also prove semantic progress for the finite actor model, including
unchanged bursts and counted completion. A theorem that assumes every
required guide is present, or only bounds a separate static rule solver,
is not this coverage/progress proof.

The production adapter must preserve each primitive's full mark relation,
AP endpoint, actual method/call stage, original FULL condition and all
complete ordinary premise members. Do not introduce runtime support labels
to satisfy a proof adapter. New models must not change the selected scope.
Runtime tests must check: the complete getter/setter/wrapper/pass-only trace;
a real kill; two distinct full marks in one group; selected-cleaner repair;
conditional source/ND guard completeness; late read/write/request arrivals;
static copies with and without a source; ordinary/static original routing;
class/broad data; ignored nonempty end-fact lists in all three sink roles and
both directions; and exact SourceHit-to-Seed.Source identity. A callback-count
match is not semantic validation.

Add controls for signed folds before grouping; different positions/check
kinds/owners; original candidate/action order; GROUP/FULL cache isolation;
collapsed MULTI and mixed source families with original tails/anchors;
complete tuple arity; incompatible caller pairs; late summary/input/interest
orders; FLOW copies and Base writer bootstrap; no source-free cycle; and
bounded marked-copy loop closure. The old strict sibling-field writer
rejection becomes an explicit DEMAND-candidate plus later FULL refinement
control. Keep real overwrite/disjoint-transfer tests unchanged.

Check both JVM alias value paths: a local field write and a call ALIASES
stage. Their AP edge sets must stay unchanged, and each static value edge
must have its exact write descriptor. Check ordinary aliases, conditional
versus actual group input, original-slot writer requests and no static CFG
relay. A frontend descriptor omission violates the existing complete
transfer requirement; a proof that assumes that requirement does not
verify the lowering.

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
| Sink `trackFactsReachAnalysisEnd` | Configured end actions are lowered, propagated and reversed | Treat list as empty before resolution/validation and compilation in every run; sink conditions/seeds remain |
| Direction/limit policy | Positive alternating runs with increasing limits | First two limits 1, then increase; run-1 GROUP, later FULL |
| StrongQueries and current publication | Actual inputs/results authorized independently of old guides | Retain; no new strict per-method veto |
| Backward ZERO and sources | Internal seed discovery, zero-only balanced return, exact source hits | Retain exactly; no group on ZERO |
| Record barrier | Quarantine all run-1 records | Export independently valid NORMAL only; never early/group/static-demand candidates |
| Ordinary AP/F74/F75/F76/F77 | Full primitive semantics, tails, field/exclusion and complete-premise rules | Retain, including source transfer kind and bound unary anchor; no extra exact-field writer validator |
| Signed FULL NNF / run-1 matching | Full literals and alias-specific runtime proofs | Keep FULL folds/metadata; normalize positive GROUP source/condition cases without alias products |
| IR/model/compiler caches | Existing IR; shared full forms | Retain IR/reference policy; select separate GROUP/FULL compiled views without mutating originals |

This specification takes precedence at these seams over the earlier separate
mark-group, static-pool, immediate-summary and universal-carry proposals.
Those documents and their counterexamples remain historical explanation
and local proof evidence. They do not add hidden runtime phases or storage.

## 18. Measurement checkpoint

The latest measured implementation is
`ff2966455da2e5ffd0db655a1ff06c5954ed3c8a`.
The primitive baseline in section 1 remains unchanged. These measurements
use the previous implementation contract, not this fixed revision.
The [fresh report](../docs/reports/bidirectional-grouped-statics-performance-20261019.md)
contains these reached / completed engine / accepted forward runs:

| Project | Latest runs | Run-1 engine |
| --- | --- | --- |
| OWASP | run-1 / none / none | 908.767 s, TIMEOUT |
| conductor | run-1 / none / none | 986.838 s, TIMEOUT |
| ThingsBoard | run-1 / none / none | 700.235 s, TIMEOUT |
| Stirling-PDF | run-1 / none / none | 1,026.865 s, TIMEOUT |

All four fail the 60-second run-1 engine target.
No OOM occurred and no forward report was accepted.
The strict comparison has 4,149 missing project-qualified reference sink
identities and no additions. The report explains timeout-censored export;
these differences do not prove 4,149 semantic false negatives.
Project completion and finding parity fail acceptance.
The selected 2,039-test pass and formal checks do not override that result.
Older runtime measurements are historical and do not measure this revision.
The selected [refactoring proposal](../docs/design/bidirectional-run1-rule-provider-and-grounding-removal-20261019.md)
explains this revision's code changes. They have no new project measurements.
Other candidates in the earlier next-iteration proposal remain separate work.
