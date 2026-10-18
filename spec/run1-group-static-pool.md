# Run-1 static pool with rule-group marks

Date: 18 October 2026. Status: **proposal; analyzer unchanged**.
Language: ASD-STE100 Simplified Technical English. Defined technical terms,
code names and mathematical notation keep their stated meanings.

This proposal follows [proof-guide.md](proof-guide.md). Its
[proof dossier](run1-group-static-pool-proofs.md) gives the constructive
models, checked results and required implementation adapters. It replaces
the static-pool choices in the
[earlier sketch](../docs/design/bidirectional-run1-global-static-pool-20261018.md).
It uses the selected [mark representation](rule-group-marks.md).

This proposal changes run-1 static admission, transport and handoff.
It does not claim equality to the old run-1 summary relation. The user permits
run-1 static candidates to ignore call order and caller context. All results
that depend on this approximation are DEMAND. Later ordinary full-mark
analysis must establish the actual source-to-check path.

Measured runtime remains `63d5481e4e4b69ecf042c1149bb574ad40fd1624`.
Latest reached / completed engine / accepted forward report: OWASP
**run-7 / run-6 / run-5**; conductor **run-1 / none / none**;
ThingsBoard **run-1 / none / none**; Stirling-PDF **run-2 / run-1 / run-1**.
These results do not measure this proposal. Conductor stops with run-1 OOM.
The target remains a run-1 engine time within 60 seconds. IR storage and
reference/cache-manager changes are outside scope.

## 1. Current behavior and proposed behavior

| Area | Current measured behavior | Proposed behavior | Expected effect |
| --- | --- | --- | --- |
| Static availability | Static inputs cross call ports and intermediate methods. | Writers publish to one shared pool. Reached readers receive direct updates. | Removes relay work for static availability. |
| Stored dependencies | Static effects can retain a remote original and a concrete-mark premise. | The static candidate path has no remote original. It keeps merged groups. | Removes those dimensions from local and summary keys. |
| FLOW mark need | Requests can require caller joins and static routing. | Static needs join pool supply. Static-derived local needs join local supply. | Removes the return path along the call chain. |
| Unknown RHS | A missing concrete input can delay concretization. | A conservative FLOW envelope records possible content without full marks. | Breaks the request/supply bootstrap cycle. |
| Handoff | Ordinary demand summaries supply most backward guidance. | A point-demand ledger also starts full-mark refinement without relay guides. | Keeps internal checks and static writer discovery covered. |

The pool does not replace ordinary nonstatic relation analysis. Ordinary
input FLOW keeps its original-slot requests and valid NORMAL summaries.
The new static-derived path is called the **ambient path** below.

## 2. Mark, FLOW and geometry domains

Each full mark has one top-level owner group. An entire join rule has one
group. Run-1 taint values use `(group, *)`; every such value is DEMAND.
Run-2 uses full marks and field limit 1. Run-3 uses full marks and limit 2.
Later runs follow [the selected schedule](rule-group-marks.md#3-run-schedule).

```kotlin
data class MayPayload(
    val flow: Boolean,       // supplied unknown mark content
    val groups: GroupSet,   // supplied group possibilities
)

fun join(a: MayPayload, b: MayPayload) = MayPayload(
    flow = a.flow || b.flow,
    groups = a.groups union b.groups,
)
```

`flow` is metadata, not a new taint mark or a source. It does not mean that
every full mark exists. A group interest can select a DEMAND possibility
from supplied FLOW. There is no `AnyPossibleMark` taint value, catalog-wide
mark expansion or per-full-mark row in run-1.

Keep AP geometry separate from marks. The pool key contains a static
coordinate and a bounded geometry class. A coordinate is Field(C,f) or a
direct Class(C) point. These are different coordinates. Broad root/class
supplies use explicit scope descriptors and conservative overlap matching.
They do not create an S-root initial. No `C.f.[any]` initial is introduced.
An exact class/root point does not supply a deeper field. Exact tainted
empty S-root remains outside the legal run-1 input grammar.
The existing AP rules select EXACT/STAR initials at ordinary entry adapters.
An ambient current fact can use an ordinary current ANY tail.

Preserve known field/path information when changing the layer or mark.
Run-1 field exclusions remain empty. At the pool/ambient boundary, erase
FLOW mark exclusions from this **DEMAND** metadata. This can add candidates;
it cannot add a NORMAL relation. Selected-cleaner group interests must not
be filtered by those exclusions. Ordinary NORMAL FLOW keeps the typed
group/full exclusion rules in the mark spec.

## 3. Prescan inventories

Prescan already resolves calls. Close the following inventories before
run-1. Use the existing context and entry policy when constructing method
keys and call ports.

```kotlin
data class StaticConsumerSite(
    val method: MethodKey,
    val point: ProgramPoint,
    val occurrence: EffectOccurrence,
    val geometry: StaticGeometry,
)

data class StaticWriterSite(
    val method: MethodKey,
    val point: ProgramPoint,
    val occurrence: EffectOccurrence,
    val target: StaticScope,
    val reverseOperation: OperationRef,
    val originalGuard: ConditionRef,
)
```

Consumers include code reads and static positions in source/sink conditions,
cleaners, rewriters, rules and models. Writers include code stores, copies,
source/rule/model outputs, alias writes, unknown/native effects and boundary
suppliers. Keep the original signed NNF, action order and stable occurrence.
Unknown targets use a broad scope; they must not disappear from lookup.

The inventories are templates. A consumer subscribes only when its actual
local control point is reached. A template does not make a method reachable
or create a value. The complete writer inventory remains available to
run-2 even if a writer emits no group in run-1.

There is no static field-request relation, transitive method-by-field set,
method-by-group closure or initial notification wave. A read registration
is an actual consumer subscription, not a field request. A direct mark check
creates only the corresponding group interest.

## 4. Stores and ownership

```kotlin
class StaticPoolRow {
    var supplied = MayPayload(false, emptyGroups())
    var neededGroups = emptyGroups()
    val readers = ConsumerSet()
}

class Run1StaticPool { // one serialized owner
    val rows: GeometryIndex<StaticPoolRow>
    val pending: GeometryIndex<PoolBatch>
}

data class AmbientKey(val method: MethodKey,
                      val point: ProgramPoint,
                      val geometry: LocalGeometry)

class AmbientState {
    val values: Map<AmbientKey, MayPayload>
    val interests: Map<AmbientKey, GroupSet>
}

class StaticDemandLedger {
    val entries: Map<DemandHeader, GroupSet>
    // Header retains point, occurrence, endpoint, original NNF and support.
}
```

Pool rows belong to the run. Ambient state belongs to a method owner.
The ledger belongs to the run handoff. The closed inventories belong to the
analysis session. Ordinary input relations and request P/R stores keep their
existing owners.

`ProgramPoint` includes the operation input, call stage, rule-action index
and F74/F75 lowering stage. Pre-cleaner and post-cleaner points are different
keys. New supply or a selected group re-enters transfer at the operation's
input stage. It is never injected after that operation's kill.

No pool or ambient value key contains a remote static original, producer
ID, full mark ID or producer/reader pair. Do not copy the pool into every
method. A reached call continuation still needs its ordinary call binding;
this is not a remote static origin.

Use each accumulated ambient value as the answered set for local group
delivery. Do not add a second per-reader answered-group history. A transient
snapshot can queue fresh work. Release it after delivery; keep no version
log. The inventory can retain writer sites, but run-1 keeps no writer-to-
reader dependency graph. Run-2 builds only the dependencies it queries.

## 5. Static publication and FLOW bootstrap

Intercept **every** run-1 static value input/result before ordinary per-
premise storage. This includes code stores, actual entry supply, rule/source/
model outputs, aliases and call/return results. Convert it to pool supply;
do not also retain the old `x -> C.f` static carrier row. A result that
depends on this pool enters the ambient DEMAND path even if its current
base is nonstatic. The single compatibility default in section 8 is separate.

Every reached write must obtain a conservative RHS envelope. It must not
wait for one requested full mark. The envelope can come from ordinary FLOW,
ambient state, a rule action or a control/value abstraction.

```kotlin
fun publishWrite(site: StaticWriterSite, rhs: RhsEnvelope) {
    // The compiler must establish this envelope's may-coverage contract.
    when (rhs) {
        is ProvedClean -> Unit
        is GroupValue -> pool.offerSupply(site.target, rhs.payload)
        is UnknownValue -> pool.offerSupply(site.target,
                                           MayPayload(true, rhs.groups))
    }
}
```

RHS coverage is an adapter contract, not a runtime theorem oracle.
The implementation must establish it for each primitive operation.
For a real possible mark m on the RHS, the envelope must contain owner(m)
or supplied FLOW at matching geometry.

At a control-reached write with unresolved RHS content, publish conservative
FLOW metadata unless the local abstraction proves the result clean. Missing
taint callbacks do not prove clean. A proved whole-value kill must produce
an empty local envelope; a generic fallback must not replace that proof
with unknown. Join every surviving path before claiming a whole RHS clean.
If a contribution was only conservatively unknown, the monotone pool can
keep it after later information arrives. It remains a DEMAND possibility.

FLOW copied from an ordinary input loses its relational original at this
boundary. Publication does not request every group on that original.
This is the deliberate run-1 static approximation. A later exact query
reconstructs the writer RHS and any required input mark.

Explicit boundary suppliers must publish their modeled envelope. Do not
silently treat every field as externally tainted. A request and a catalog
member alone are not supply.

Static copies use the same path: a reached read receives supplied FLOW or
groups, the local operation rebases geometry, and the reached store publishes
that envelope. FLOW can cross such a copy before any group interest exists.
Deduplicated monotone rows handle copy cycles without a graph-replay wave.

## 6. Commit, registration and direct delivery

For each compatible row, define:

```kotlin
fun availableGroups(row: StaticPoolRow): GroupSet =
    row.supplied.groups union
        (if (row.supplied.flow) row.neededGroups else emptyGroups())

fun commit(row: StaticPoolRow, batch: PoolBatch): DeliveryBatch {
    val beforeGroups = availableGroups(row)
    val beforeFlow = row.supplied.flow
    row.supplied = join(row.supplied, batch.supplied)
    row.neededGroups = row.neededGroups union batch.neededGroups
    return DeliveryBatch(
        groups = availableGroups(row) - beforeGroups,
        flow = row.supplied.flow && !beforeFlow,
        recipients = row.readers.snapshot(),
    )
}
```

The snippet uses one normalized overlap cell. The actual GeometryIndex
must also join different keys with overlapping AP scopes. Define the oracle
at a concrete location l:

```text
explicit group g available at l iff some supply containing l offers g
FLOW available at l            iff some FLOW supply contains l
g needed at l                  iff some group-interest scope contains l
reader can receive g at l      iff reader contains l and
                                  (explicit g or (FLOW and need g))
```

A broad class/root FLOW supply can meet an older narrow field need, and
the reverse arrival order must work. A new reader snapshots all compatible
committed scopes. Meet reader, supply and interest geometry at the **same**
location; separate pairwise intersections are not a sufficient oracle.
Deliver their AP intersection or an ordinary sound AP abstraction of it.
Do not copy broad supply eagerly into every declared field. The index can
visit actual registered readers and needs. Its adapter must prove equality
to this oracle within the chosen AP abstraction.

Commit supply and interests at the pool owner. Notify recipients after the
commit. The update contains fresh group members and at most one fresh FLOW
bit. A group already enabled by FLOW plus interest is not sent again when
an explicit group supply arrives. Supply and interest remain different
semantic components even if both name the same group.

```kotlin
fun onConsumerReached(site: StaticConsumerSite) {
    // Registration and snapshot selection form one serialized operation.
    val snapshot = pool.registerOnceAndReadCommitted(site) ?: return
    postAmbient(site, snapshot, DEMAND)
}
```

A new reader sees old committed content. Pending content arrives from a
later commit. An old reader receives new content. New interests join old
supply; new supply joins old interests. All these arrival orders must work.
Unreached sites receive nothing. Known group supply does not need a request.
FLOW supplies the unknown bit to a reader even before any mark need exists.

Accumulate method offers and group interests in temporary batches. Transfer
them when the method queue is empty. Queue one pool drain when a batch is
submitted. The drain is counted work; it does not wait for global emptiness.
Later pool deliveries can refill a method queue. This is ordinary fair
work scheduling, not a graph-change replay policy.

Before a complete barrier, commit pending batches and drain their deliveries,
local work, return effects and demand-ledger writes. A committed interest
with no compatible supply can remain unanswered. It must not block the run
forever or create a taint value. Keep cancellation checks and incomplete-run
guards. No arbitrary pending-size threshold is required by this design.

## 7. Group request handling

| Origin at the operation | Stored interest | Handling |
| --- | --- | --- |
| Ordinary singleton abstract input FLOW | Existing original-slot group request in P/R | Use ordinary nonstatic propagation and initial emission. |
| Direct static position | Pool row/scope group set | Join with explicit group supply or supplied FLOW. No call-chain request. |
| Ambient local FLOW | AmbientKey group set | Join with the reached local FLOW value at that point. No upstream request. |
| Ambient group value | Local forward-demand obligation | Retain the obligation even when value union adds nothing. |
| ZERO or complete ND original | No original-slot request | Keep ordinary rule/ND obligations. |

A pool need uses a legal static slot plus GroupId. It has no AP tail, field
exclusion or mark exclusion. Geometry/scope matching selects compatible
supply and readers; it does not turn the need into a full fact pattern.

```kotlin
fun needAmbient(key: AmbientKey, group: GroupId, obligation: DemandHeader) {
    ledger.offer(obligation, group) // do this even without a value delta
    ambient.interests[key].union(group)
    val supplied = ambient.values[key]
    if (supplied.flow || group in supplied.groups)
        acceptAmbient(key, MayPayload(false, groupSetOf(group)))
}
```

The local FLOW value must be supplied at the required point and compatible
geometry. An interest alone cannot supply it. Do not route the request back
to a remote read or keep a set of remote read origins on the local value.
Run-2 finds those reads again through the code.

Commit a fresh group to accumulated ambient state **before** queueing its
transfer. Repeated interests then observe it as answered. Do not recursively
resubmit an already stored group when answering local interests. No second
answered-group store is needed. A broad-scope notification can be redundant;
the local atomic join must reject duplicate semantic work before enqueueing.

Ignore field exclusions and FLOW mark exclusions for request matching.
Record the original full literal and condition in the obligation. A selected
cleaner retains a group value and its group-demand interest. On FLOW it
also creates the ordinary group-excluded complement where applicable.
Ambient complements remain DEMAND. A new group need must run even when
the group is already excluded or an identical value was processed earlier.

Ordinary P/R still use:

```kotlin
summaryStorage.commit(temporary)
temporary = new RequestStorage()
```

Pool interests are not copied into P/R in relay methods. A write from an
ordinary abstract p does not make every pool need become a run-1 p/group
request. The FLOW envelope supplies the coarse candidate; run-2 opens p/T.

## 8. Ambient FLOW and group propagation

```kotlin
fun acceptAmbient(key: AmbientKey, incoming: MayPayload) {
    val growth = ambient.values.joinAndGetGrowth(key, incoming)
    enqueueBulkGrowth(key, growth)
    answerNewLocalInterests(key)
}
```

Keep FLOW and group sets merged through local assignment, reads, calls and
returns. Apply the ordinary AP transfer and field limit to geometry. Do not
unpack a group into full marks or convert the ambient value to one ordinary
fact per remote original.

All ambient results, source effects and observations are DEMAND. A call,
return, base change or ZERO-shaped rule output cannot promote them to NORMAL.
Independent ordinary NORMAL results keep their own proof and support.

At a call, project ambient arguments by the real call binding. Join at the
callee's local formal geometry; retain no remote static premise. Publish
ambient return/exception effects once per method/output geometry and group
set. Apply them only to reached compatible call continuations and actual
output bindings. A normal return needs a reached normal exit or applicable
ordinary return model. A check in a nonreturning method can be a candidate;
it cannot make the caller's later statement reachable.

Use a separate ambient effect carrier. Do not encode a pooled read/return
as a fabricated ZERO-premise source summary or source hit. A real rule GEN
keeps its actual source occurrence and any ambient guard dependence.

No static input fact is sent down a call chain to keep a field available.
The pool is available to each reached reader directly. Do not create
`C.f -> C.f` summaries. Keep the existing single universal DEMAND default
`S.* -> S.*` in ordinary summary storage for compatibility; the pool path
does not subscribe to it or use it as a carrier. No S.* initial is emitted.

Selected-mark cleaners cannot remove a group value. RemoveAllMarks can kill
the current local FLOW and all local groups under its ordinary geometry.
Ordinary definite field kills and disjoint preservation still apply. A
surviving result that needs a field-exclusion update moves to DEMAND and
keeps empty E. Requests do not inject across a local whole-value kill.

Do not subtract a local kill from the shared pool. A later static read can
therefore see an old pooled contribution, including after a static cleaner
or overwrite. This is an admitted run-1 DEMAND approximation. Later full-
mark analysis must apply the real kill. Killing a local value and reading
the shared field again are different operations.

## 9. Conditions and demand recording

Keep the original full-literal NNF DAG and bound occurrence for source and
sink conditions. Fold full signed contradictions before group evaluation.
Keep ordinary eligibility for residual negatives and non-mark conditions.
Groups can establish possibility; they cannot establish exact mark truth,
absence or compatible ND support.

Evaluate a condition against accumulated local state, not only its latest
packet. T and U can arrive in separate packets. Retain the complete original
condition and support obligation for later analysis. Do not enumerate group
tuples or condition cubes to store one possible check.

The ledger includes ambient sink/check candidates, source/trigger guards,
cleaner group obligations and demanded output endpoints. It is independent
of method-return summaries. A candidate inside a nonreturning method is
retained at its statement. Do not write ambient facts into the observer's
ordinary concrete-input history.

An unresolved condition stays DEMAND, including conditional source GEN.
A condition ledger entry is not a source permission or a confirmed report.
If no compatible supply exists, a standing group interest alone creates no
positive mark hit.

## 10. Run-1 to run-2: point queries and writer discovery

Handoff requires a complete run-1 barrier. Keep the compact ledger, immutable
original rule views, closed writer/call inventories and ordinary valid
records. Release pool values, subscriptions and ambient method state after
the handoff snapshot is owned. Do not keep these stores as backward evidence.

Run-2 is BACKWARD, full marks, field limit 1. Use the selected complete group
decoder. Every eligible full member of a group must remain queryable. Lazy
matching is permitted if it has equal query coverage. Do not choose one
representative or restrict the catalog to marks observed in run-1.

```kotlin
data class StaticRefinementSession(
    val original: DemandHeader,
    val fullQueries: QuerySet,
    val dependencies: QueryDependencyGraph,
)

fun beginRefinement(demand: GroupDemand) {
    for (fullQuery in decodeEligibleFullMembers(demand))
        enqueueAtActualPoint(fullQuery, newSession(demand, fullQuery))
}
```

Start at the actual check/read/guard/output point. A method-exit start is
insufficient for an internal check in a method that does not return.
The query retains the original point, endpoint, full condition, context
and whole support obligation. It is a search request, not actual taint.

The session permits ordinary reverse CFG and call transfer without an equal
run-1 relay guide. At a reached reverse call, use the real call-stage bindings
and appropriate normal/exceptional callee exits. At a method entry, open
compatible prescan incoming call ports and continue towards configured
program roots. Query-only incoming-port expansion must retain context/entry
constraints; it cannot manufacture a forward supplied fact.

```kotlin
fun onBackwardStaticRead(read: ReadSite, q: FullQuery, session: Session) {
    enqueueOrdinaryPredecessors(read, q, session)
    for (writer in inventory.mayOverlap(read, q.geometry)) {
        val opening = session.dependencies.add(read, q, writer)
        for (predecessor in reverseWriter(writer, q))
            enqueueWriterSearch(opening, predecessor, session)
    }
}
```

Lookup considers all compatible writer kinds, including unobserved groups,
unvisited run-1 writers, broad effects and explicit boundary suppliers.
It must not require a full-mark run-1 initial, summary or pool emission.
Source/model writers retain their original guard. A copy/rewriter uses its
ordinary reversed AP and mark operation. The current full mark can change
under a rewriter; the original read query remains unchanged.

Deduplicate work by session plus current full query/context/point. Union
dependency edges separately. Keep every intermediate writer/guard and
parent-to-predecessor edge through copies and cycles. Keeping only the final
writer and original read loses necessary conditions.

An inventory opening creates a **DEMAND discovery query only**. It creates
no executable writer-to-reader summary, actual source or call-return edge.
An RHS source answer does not discharge the original read. Ordinary reverse
analysis can establish genuine dependencies; an unresolved placement/order
obligation remains unresolved. Recursive search uses the ordinary fair
deduplicated queue, not a fixed fuel cap. The finite proof uses depth only
to exhibit each finite witness.

## 11. Full-mark continuation and confirmation

Run-2 records exact source permissions and ordinary full-mark demand produced
by its real query analysis. A speculative writer query can find a permitted
source without proving that the source reaches the original check.

An exact source permission uses `(MethodKey, statement, complete forward
PathEdge)`, plus stable predicate occurrence where required. The edge keeps
the full mark, geometry and forward orientation. A group or writer ID is
not an exact permission. Execute the source's original guard normally.

For a static refinement session, do not reintroduce the missing run-1-guide
gate in a relay method. Authorize the whole ordinary reverse query closure.
For the following forward run, authorize ordinary propagation from actual
configured roots and exact permitted source actions through the session's
full-mark closure. If a precise guide boundary cannot be established, use
the conservative fallback: leave the relevant full-mark root-reachable
scope unpruned by run-1 guidance. This costs later work, not run-1 carriers.
Close the session over ordinary mark rewrites as well as calls and AP
transfer. A predecessor/output mark can have a different owner group.
The original group is not a filter on these full-mark continuation steps.

```kotlin
fun allowSource(action: SourceAction, session: Session): Boolean =
    exactSourcePermissions.contains(action)

fun allowOrdinaryStep(q: FullQuery, session: Session): Boolean =
    ordinaryQueryPermission(q) || session.authorizesQuery(q)

// Session authorization removes a guidance gate. It is not a taint fact.
```

The forward run must start from real root reachability and permitted sources.
It must follow ordinary full-mark CFG, calls, normal/exceptional returns,
strong kills, cleaner actions, original conditions and ND support. Do not
inject a marked value at an arbitrary writer or read because lookup found it.
Source GEN/publication and call responses must receive the same session
authorization; bypassing only the initial query gate is insufficient.

A finding needs an ordinary full-mark witness at the original occurrence.
Neither pool content, a group, a group request, an inventory opening nor an
isolated writer RHS answer can confirm it. If refinement stops early, the
candidate remains unconfirmed. Existing AP/alias and eligible-negative
approximations remain; this proposal does not promise removal of every FP.

Keep the existing confirmation-certificate/trace-resolution contract.
Ordinary F3 sink arrival alone does not set `SinkWitness.confirmed`. The
current minimal driver leaves candidates unconfirmed without a supplied
certificate. This proposal neither implements nor proves that certificate
producer; it specifies the full-mark evidence the producer must receive.

Independent valid NORMAL records remain executable within their admitted
domains. An old NORMAL FLOW exclusion of group g cannot close a later query
for a full member of g. Ambient DEMAND effects and speculative discovery
edges never enter executable NORMAL records.

## 12. Traced examples

```java
void A(Object p) {
    C.f = p;
    B();
}
void B() { check(ContainsMark(C.f, T)); }
```

```text
F1: abstract p at the reached store publishes FLOW for f.
F1: the reached B check offers group owner(T) on f.
F1: FLOW plus need gives one group DEMAND candidate.
    No static request returns through A, and no p/T is required in F1.
B2: the full query starts at B's check, including if B loops afterwards.
B2: ordinary reverse calls can reach A; inventory also opens A's store.
B2: reversing the store requests p/T. It keeps B's original read obligation.
F3: an actual source, store and later B call must establish the full witness.
```

For `B(); C.f = p`, the pool can give the same F1 candidate. Writer discovery
can also find the same store. Neither step proves the order. The ordinary
full-mark path must place the store before the read. A mismatched caller,
strong overwrite or RemoveAll has the same validation requirement.

For `x = C.f; relay(x); check(x,T)`, pool FLOW travels through local/call
geometry in bulk. The local check selects owner(T) without a remote-origin
request. B2 starts at the check, reverses relay's real binding and reaches
the original code read. Missing F1 relay summaries do not block that query.

For `C.f = C.g; check(C.f,U)`, FLOW can copy from g to f before U is known.
B2 reverses the copy to a full g/U demand and retains both writer endpoints.
Cycles merge query work and dependency edges; they do not erase guards.

## 13. Proof and cost contracts

The concept must cover finite real static-flow witnesses under the stated
local AP, producer and condition adapters. Strong concrete writes can remove
old contents; the monotone pool keeps a superset. Reader/need delivery is
DEMAND, and full-member decoding preserves the refinement queries.

Separately compare a scalar pooled oracle with merged/indexed rows. Their
FLOW and group observations must agree for the same coarse concept, for
all offer, need and registration orders. This is the storage equivalence
claim. It is not equality to the old flow-sensitive summary relation.

For a transparent call chain of length h and g groups, the expanded carrier
reference can store h*g static group carriers. This pool needs no per-field
carrier at those relay methods. Direct consumer work remains. One rule's
m full marks can share one group slot; this is the prior scoped mark law.

Pool state costs coordinate/geometry rows, their group sets, reached readers
and writer inventory. Ambient state still costs local points, AP geometries
and groups. Neither key may regain a remote-original dimension. Productive
argument/return flow still crosses real calls. B2 can open many writers and
full marks. These costs prevent a universal speed or memory guarantee.

The [proof dossier](run1-group-static-pool-proofs.md) states the exact Lean
scope and all remaining adapters. Before runtime work, use its constructive
operations as the differential oracle for the stores and handoff compiler.

## 14. Implementation and validation order

1. Add shared rows and atomic registration/commit using the formal join.
2. Add reached RHS envelope publication and origin-free ambient FLOW/group
   processing. Keep ordinary requests and NORMAL analysis separate.
3. Add pool/local group interests, the complete barrier and compact ledger.
4. Add explicit full-mark point queries, unpruned reverse closure, complete
   writer discovery, dependency edges and source/forward authorization.
5. Add controls for both arrival orders, no supply, abstract-write bootstrap,
   same-group cleaners, RemoveAll, source guards, ND, nonreturning checks,
   write/read order, static copies/cycles, broad effects and external supply.
6. Test the finite transfer/hand-off adapters, then the required core and
   fixed project gates. Report pool rows, relay carriers, local groups,
   candidate growth, observer rows and B2 writer/query work separately.

Every report must include the latest reached/completed/accepted run. Measure
run-1 engine time separately. Reject an implementation that only moves the
old origin-by-mark product into the pool, ambient store, observer or ledger.
No analyzer implementation or new benchmark is part of this proposal.
