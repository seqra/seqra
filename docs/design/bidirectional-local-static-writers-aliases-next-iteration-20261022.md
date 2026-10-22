# Next iteration after local static writers and summary aliases

Date: 22 October 2026. Status: **measurement-based proposal; not implemented**.
Source: `a4bf789866f912ebefc54bca42d809c89beea2d4`.
Use the [current report](../reports/bidirectional-local-static-writers-aliases-performance-20261022.md)
for measurement scope, exact commands and latest iterations. All eight
project commands and profile reviews are closed. The combined live heaps
separate run-1 static consumers from later FULL store/publication state.

OWASP reaches **run-8 TIMEOUT**, after it completes and accepts run-7.
Its run-1 engine takes 172.096 seconds. Conductor reaches **run-2 OOM**,
after it completes and accepts run-1 in 18.134 seconds. Thingsboard reaches
**run-1 OOM**, with no complete engine or accepted report. Stirling-PDF
reaches **run-2 OOM**, after it completes and accepts run-1 in 25.089
seconds. All four commands are closed.

## Proposed order and semantic scope

| Order | Proposal | Expected effect | Contract and readiness |
| --- | --- | --- | --- |
| 1 | Preserve method-exit rule eligibility | Potentially avoid a large family of out-of-scope candidates, requests and later seeds | Precision correction; provider-context adapter needs a separate design/proof before implementation |
| 2 | Remove the repeated GROUP projection at immediate AP callbacks | Avoid a source-confirmed repeated trie walk at four run-1 callbacks | Same results, requests and receiving-manager identity; exact patch boundary identified |
| 3 | Publish an old summary only on its first full-query authorization | Avoid old-answer replay and temporary objects in restricted runs | Same publication/demand closure; project replay fraction not measured |
| 4 | Allocate conclusion containers only when used | Reduce the live cost of millions of small groups | Same storage/delta semantics; technical fallback |

This is an expected-impact order, with confidence and readiness kept
separate. It gives priority to run-1 before later FULL work. The first
item has a large candidate family but no measured CPU
share or complete repair. The second has direct run-1 hot-path evidence.
The third can remove a quadratic replay pattern, but its share in these
projects remains unknown. The fourth saves representation cost after
work has already been admitted. No item has a measured project speedup.
Use the second item first if a ready equivalence optimization is needed;
do not hide the unresolved semantic decision in the first item. Measure
each patch separately before combining it. For the later-run OOMs, the
third item is the first algorithmic candidate; the second targets run-1.

Keep field transfer, aliases, local writer admission, computed supplies,
mark groups, run limits, source/action identity, complete ND bindings,
request barriers, NORMAL persistence and later matching unchanged. Do not
add GroundingSupport, an input history, a mark-member census, a global
static relation table, an IR change or a reference-manager redesign.

## 1. Preserve method-exit rule eligibility

### Evidence and change

The enabled rule gate has two existing exit-scope failures at the exact
pre-optimization base and at the selected implementation. Both alias
settings fail. The provider restricts its method-exit sink to an empty
initial fact set. Form compilation resolves the rules with `initialFacts
= null` and loses this restriction. Runtime sink evaluation has no
corresponding eligibility field. This permits a nonzero hypothetical
initial to create an exit-sink candidate that the provider forbids.

Conductor's accepted run-1 report exports 1,253 added unique locations.
Of these, 986 are XSS method-exit-shaped locations, with 7,888 exported
occurrences. This identifies a large candidate family. The Simple report
does not retain the binding evidence needed to classify all 986 as false
positives. The finite negative controls prove the defect; they do not
measure its whole-project cost.

Current: cached rule compilation loses the provider's initial-analysis
scope. New: preserve that scope through forms and evaluate the rule only
where the provider permits it. The adapter from the legacy scope to the
current GROUP/static engine is unresolved. A premise-kind test alone is
not a demonstrated implementation of that adapter. A custom provider can
also permit a nonzero exit sink.

### Example and fact handling

```java
String clean(String p) { return p; } // no endpoint annotation
String endpoint() { return clean(requestInput()); }
```

Let the rule's endpoint scope exclude `clean`. Let `G` be its group. A
nonzero run-1 edge in `clean` can be:

```text
I = arg0/./G
I -> return/./G, DEMAND

current: cached anyFunction exit sink evaluates under I
         -> add a clean-exit candidate / possible later seed
new:     preserve the provider's intended endpoint scope
         -> do not evaluate this ineligible sink
```

Keep the transfer and return summary. The actual caller can still derive
`ZERO -> return/./G` in `endpoint`; eligible sink handling there remains.
The correction must remove the ineligible rule evaluation, not filter an
exported finding after the analysis has done its work.

### Implementation and proof gates

Preserve provider eligibility through `JIRMethodExitRuleProvider`,
`JIRBoundaryForms` and the bound `SinkRule`. Carry it as rule metadata,
not as a fact support label. Before choosing a representation, close this
positive static-origin obligation:

```java
void initialize() { C.f = source(); }
@EntryPoint Object endpoint() { return getter(); }
Object getter() { return C.f; } // not an endpoint
```

The current static contract can produce `C.f/G -> return/G, DEMAND`
at both returns. An actual pool reply keeps the nonzero static original;
it does not turn it into ZERO. A literal `premise.isZero` guard can block
the endpoint's legitimate FLOW group request and its later Group witness.
A blanket static exception can still admit the unannotated getter. Pool
availability proves a group exists, not that this rule belongs to this
method. The future adapter must distinguish these cases through the
provider's intended scope, without a new support/history mechanism.

The positive case is a source-derived contract obligation. It has not
been executed with its actual Semgrep lowering. Do not advertise a
zero-only guard or a static exception as a ready, safe repair. This is a
major semantic decision that needs its own selected implementation spec.

First model the legacy provider scope and its current-engine adapter in
Lean. Prove permitted observations and absence of forbidden observations.
This is equivalence to the intended provider contract, not the defective
runtime. Retain the exact-base negatives, positive ZERO controls and both
alias settings. Add actual direct and nested static endpoint positives,
unannotated getter negatives, both source/request arrival orders,
mixed-origin ND and a custom provider that permits nonzero exit sinks.
Check run-2 queries, source actions and handoff as well as run-1 reports.
Keep FULL behavior and the empty END-action policy. Measure rejected
evaluations and resulting sink/seed reduction separately.

The evidence notes are `validation/exit-scope-root-cause.md` and
`validation/exit-scope-static-origin-appendix.md` in the campaign.

## 2. Remove the repeated immediate GROUP projection

### Evidence and change

`ops.applyEdge` and `ops.clean` already apply the receiving manager's
`run1Policy` before their result callbacks. Four immediate actor
collectors then call `runtimeFact` again. An unchanged immutable GROUP
value can still require a full trie walk and new traversal scratch before
the second projection returns the same value.

Current: project at the AP boundary, then project again at its immediate
collector. New: use the existing `collectAfterRun1Policy` contract at
those audited callbacks. Retain layer forcing, cover and request routing.

The combined run-1 JFR assigns 30.00% of OWASP allocation weight to
`runtimeFact` ancestry, 18.97% for conductor and 9.10% for Thingsboard.
PDF's retained partial run-1 has 7.96%; its whole-run allocation is
unknown. These shares include required projections and overlap other
frames. They are not a promised saving. The source identifies one
redundant walk at each selected callback; measurement must establish
the project effect of removing those walks.

### Example and fact handling

```java
void copy(Box p) { Box x = p; use(x); }
```

For `I = arg0/./G`, the local copy produces `I -> x/./G, DEMAND`.

```text
current: raw result -> receiving projection -> collector projection
                    -> existing layer/cover -> same output
new:     raw result -> receiving projection
                    -> existing layer/cover -> same output
```

Keep the first projection. An equal numeric group ID or immutable root is
not proof that a foreign result uses the receiving manager's canonical
leaves. This proposal adds no stamp, cache or persistent ownership field.

### Exact patch boundary and proofs

Add the premise-only overload of the existing after-policy collector.
Use it at `EngineAlgebra.applyEdge` and `applyEdgeAt`, and the two
`ops.clean` callbacks in `RunMethodCallServices.cleanChain`.

```kotlin
ops.applyEdge(input, premise, edge, statementEdge, mode,
    collectAfterRun1Policy(premise, input, sink), may = may)

ops.clean(input, premise, cleaner, mode,
    collectAfterRun1Policy(operationInput, sink))
```

The premise-only callback still uses `raiseMark`; the point-aware callback
still uses `needGroups` with its complete saved call state. Keep the
second cover and its cancellation checkpoint. Do not migrate
`applySummary`: it can graft after its intermediate policy callback.
Keep raw, foreign, record, ND, static supply and replay boundaries intact.

Prove receiving-manager representative identity, projection idempotence
and equal ordered result/request traces at each selected boundary. Check
the existing cover fixed point without removing cover. Test a foreign
equal GROUP leaf, a distinct catalog, actual copies and cleaners, both
collector forms, saved point replies and FULL no-op behavior. Measure
projection calls/scratch and phase allocations. The detailed design is
in campaign `validation/policy-projection-proposal.md`.

## 3. Publish old summaries only on first full-query authorization

### Evidence and change

In restricted runs, every fresh current-query batch currently scans
`summaries.all()` and queues every permitted raw summary again. The
publication index deduplicates later. This does not avoid the raw scan,
temporary publications, demand-store insertion or repeated subtraction.

Current: an unrelated new query requeues old answers that were already
authorized. New: replay the complete current raw output only when a
previously blocked complete premise first becomes authorized. Fresh raw
summary growth keeps its ordinary publication path.

Conductor and PDF reach run-2 OOM after they complete and accept run-1.
The combined JFR estimates 393.143 GB and 2,322.393 GB of run-2
allocations. Generic trie subtraction has 30.10% and 36.07% allocation
ancestry. The closed heaps retain large actor and publication stores.
These costs include required work. They do not measure the fraction caused
by old-summary replay, or promise that this change avoids either OOM.
PDF's live heap has 23,584,539 Publication wrappers, with 23,584,084 in
the method-actor retained union. The publication/subscription union has
no such wrappers. This separates actor-side publication pressure from
already merged index state; it does not establish the duplicate fraction.

### Example and fact handling

```java
Object id(Object p) { return p; }
// Different callers submit separate full-mark demands T1, T2 and T3.
```

In backward run-2, let `qi = return/./Ti`. Each ordinary answer is
`qi -> arg0/./Ti` with its existing layer.

```text
current:
  q1 arrives -> derive and publish answer 1
  q2 arrives -> republish answer 1; derive and publish answer 2
  q3 arrives -> republish answers 1 and 2; derive answer 3

new:
  qi arrives -> derive and publish answer i
  no unrelated arrival republishes an already authorized answer
```

If an answer already exists before its actual query, replay it once when
that query first authorizes the complete premise. Keep late subscribers:
they read the existing cumulative publication store. They must not depend
on another unrelated input triggering an old-summary sweep.

### Storage and pipeline

Keep only blocked raw premises and their missing canonical members.
Store no Facts, old trie root, caller input or replay history.

```kotlin
class Waiting(val premise: PremiseKey, var missing: Int)
val waiting = IdentityHashMap<PremiseKey, Waiting>()
val byMissing = IdentityHashMap<InitialAp, MutableList<Waiting>>()
var forwardZeroWaiting = false

fun onFreshRawSummary(p: PremiseKey, delta: Facts) {
    if (!unrestricted(p)) rememberMissingMembersOnce(p)
    queuePublications(p, delta) // unchanged permission and demand policy
}

fun onNewQueries(batch: List<InitialAp>) {
    // StrongQueries already installed the complete batch.
    val ready = consumeMissingPostings(batch)
    for (p in ready) {
        check(unrestricted(p))
        for (current in summaries.borrow(p)) queuePublications(p, current)
    }
    batch.forEach(::addInitial)
}
```

For nonzero ND, require every member. Remove each missing-member posting
once, and remove a completed waiting row. Backward ZERO remains
unconditionally publishable. Forward ZERO uses the existing any-query
permission and one waiting flag. Run-1 does not use this mechanism.
Keep raw-before-query, query-before-raw and complete-batch arrival orders.
Borrowed summary views stay on their actor and do not cross mutation.

### Proof and work bound

Construct a replay witness for each old full-publication event. Either a
fresh raw callback already published that output, or its premise was
blocked and its first authorization replays the current covering raw rows.
Conversely, each proposed replay has a complete premise that the old
permission function accepts. Prove equal semantic publication and selected
demand closure under existing duplicate admission. Keep annotation,
report, source, record and handoff obligations explicit.

For n one-answer singleton queries whose answers follow their query, old
answer replay attempts can total `n*(n-1)/2`; the new path has zero such
replays. For n already stored blocked answers, first full replays fall
from `n*(n+1)/2` to n. Genuine new answers remain. The index uses O(W+M)
keys/postings for W blocked premises and M missing members. This bounds
one path; it does not bound propagation or prove project termination.

Tests must cover complete ND, both ZERO policies, annotation growth,
semantic replacement, all arrival orders, late subscribers, publication
failure restoration, cancellation and actual multi-caller actor schedules.
Measure first authorizations, posting visits, replayed current rows and
pending publication peak. The detailed code design is retained in
`validation/first-authorization-proposal.md` in the campaign.

## 4. Allocate small-group containers only when used

The combined conductor run-2 OOM heap has 5,168,242 TaintGroups and
6,369,247 TaintTrees globally. Some trees belong to work or subscriptions.
The owned-wrapper contract bounds the average stored alternatives by
that global ratio, about 1.2324. This supports a small-group layout
change. It does not support indexing huge alternative lists.

```java
Object id(Object p) { return p; }
```

For one stored `I -> return/./T, DEMAND` at a point:

```text
current: ConclusionGroup allocates both kind maps and reach BooleanArray;
         TaintGroup allocates both layer lists and backing arrays
new:     allocate only the used TAINT family and DEMAND list;
         store the two reach bits in the containing object
```

The combined capture's lower bound for empty scaffolding is
548,875,992 shallow bytes: empty FLOW maps, reach arrays and empty TAINT
layer containers. It is not a measured achievable saving. Thingsboard
run-1 has the same many-small-groups pattern and a **461,507,352
shallow-byte** bound. Keep this technical change separate from the
algorithmic proposals.
PDF run-2 is stronger confirmation: 8,948,150 TaintGroups and
10,509,971 TaintTrees globally, with an average bound about 1.1746 and
**966,094,632 shallow bytes** of the same disjoint unused scaffolding.
These bounds describe separate captures; do not add them across projects.

Use nullable unused family/layer containers and inline reach bits. Keep
the existing iteration order, T2 annotation rules, T4 deltas, subsumption
and interner calls. Do not add a singleton representation in the same
patch. Prove decoder equality and equal add/delta traces before tests.
Exercise transitions from empty to either layer/family, cross-layer
growth, duplicate insertion, annotation replacement and store release.

## Static consumer work: evidence still needed

Thingsboard's run-1 OOM has 304,292 pending call continuations, 226,498
point rows and 618,263 saved FLOW-image roots. Its cumulative statistics
require at least 19,657,611 PointReady replies. The normal CPU window
spends 9.38% of samples under static call supply. These are a strong
reason to investigate consumer work, but not proof that complete equal
consumer inputs can be dropped. A new caller image can need an answer
even when its field and group are already known.

The current report does not measure repeated complete call bindings or
equal scalar publication images. Before proposing consumer suppression,
measure attempted versus fresh work by complete premise, statement/call
stage, bounded input image, selected group and summary arm. Keep arrival
orders and all contributing originals. Do not use field/group equality
alone as the deduplication key. A future design must retain actual
computed supply joins and every required handoff pair.

Global sharing of equal static FLOW relations is not selected here.
The small current writer population and dominant PointReady count do not
establish that relation admission is still the main cost. None of the
four proposals above claims to eliminate all static consumer work or all
per-origin storage. That remaining gap is explicit.

## Selection and acceptance

No item is implemented by this proposal. Resolve the provider adapter
before selecting the semantic correction. Keep each equivalence
optimization in a separate proof-first implementation and measurement
step. Use the approved four workloads and configured
limits; compare strict reference sink identities and added candidate
families. Always give latest reached/completed/accepted iteration.

A successful result must report run-1 engine time, restricted-run memory,
OOM/timeout outcomes and allocation/GC changes. Fewer callback counts or
a smaller isolated container are insufficient. A failed project must
retain its complete evidence and its actual latest accepted report.

OWASP's repeated exported occurrences and PDF's earlier-return coordinate
omissions also need separate reporting designs. They are not corrected by
renaming sink keys, dropping report entries or changing source resolution
inside an equivalence optimization. Keep their strict failed gates until
the corresponding reporting contract is specified and verified.
