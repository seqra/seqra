# Run-1 statics with writer-backed mark groups

Date: 18 October 2026. Status: **revised proposal; analyzer unchanged**.
Language: ASD-STE100 Simplified Technical English. Defined technical terms,
code names and mathematical notation keep their stated meanings.

This proposal follows [proof-guide.md](proof-guide.md). Its
[proof dossier](run1-group-static-pool-proofs.md) separates the group-flow
concept, storage/protocol optimizations and remaining runtime adapters.
Use the selected [mark representation](rule-group-marks.md).

The user keeps the run-1 approximation of static call order and caller
context. **Group supply must be precise in that selected abstraction.**
Every delivered group must have a finite source/writer derivation.
FLOW, an interest, a catalog entry and a hypothetical grouped initial are
not such derivations. This revision removes the former FLOW-plus-interest
materialization rule, including its local ambient form.

Measured runtime remains `63d5481e4e4b69ecf042c1149bb574ad40fd1624`.
Latest reached / completed engine / accepted forward report: OWASP
**run-7 / run-6 / run-5**; conductor **run-1 / none / none**;
ThingsBoard **run-1 / none / none**; Stirling-PDF **run-2 / run-1 / run-1**.
These results do not measure this revision. Conductor stops with run-1 OOM.
The target remains run-1 engine time within 60 seconds. IR storage and
reference/cache-manager changes are outside scope.

## 1. Difference from the previous proposal

| Area | Previous proposal | Revised proposal | Reason |
| --- | --- | --- | --- |
| Group at a static read | Unknown FLOW plus a need could create the requested group. | The need contacts writers. Only a derived writer/source answer supplies the group. | Avoids groups with no source/dataflow derivation. |
| Group at a local point | Local unknown FLOW could answer any requested group. | Local FLOW identifies providers. The request follows those providers. | Avoids the same fabrication after a static read or call. |
| Abstract RHS | Unknown RHS could publish universal group potential. | The writer registers its conditional RHS transfer. | Keeps unresolved input separate from supplied groups. |
| Copy cycle | Unknown FLOW could bootstrap a requested group. | A cycle without an independent source produces no group. | Uses the least fixed point, not self-support. |
| Stored provenance | No writer dependencies in run-1. | Sparse writer/RHS and local transfer links, independent of marks. | Supplies the communication needed for group precision. |
| Static relay calls | Static availability skips the call chain. | Same direct field-to-writer/reader communication. | Keeps the main transport saving. |

The measured implementation still sends static inputs through ordinary call
ports and can retain a remote original/concrete-mark product. This revision
continues to remove that static carrier path. It does not restore a static
request at every intermediate caller.

## 2. Exactness target and mark meaning

Run-1 uses `(topLevelRuleId, *)`, always DEMAND. A whole join rule has one
group. Run-2 is BACKWARD/full/field-limit 1. Run-3 is FORWARD/full/limit 2.
Later runs follow [the selected schedule](rule-group-marks.md#3-run-schedule).
There is no run or field limit 0.

Define `GroupReference` as the least closure of:

```text
explicit reached source/model/boundary group seeds
+ legal ordinary group transfers and real nonstatic call bindings
+ reached writer transfers and direct static reader delivery
+ the selected group-condition/cleaner/field-limit operations
+ the accepted static order/context approximation
```

A source root needs its ordinary control/action permission. A conditional
source needs its group-guard dependencies. An unknown external effect is
not a root for every group. A boundary can supply only groups supported by
its explicit model or actual supplied marked data.

Every supplied group must belong to that reference. Completeness applies to
the **active requested scope**, so lazy ordinary P/R admission remains legal:

```text
for every supplied (n,g): (n,g) is finitely derivable in GroupReference
for every requested (n,g): supplied(n,g) iff derivable(n,g)
```

The active scope starts with requested node/group pairs. Close it backwards
over every compatible provider's group preimage and all positive guard
premises. For an OR, include every eligible alternative without cubes.
Explicitly known groups can also propagate forward without a request.
An unrequested group can remain parked in a lazy ordinary input store;
its absence from a local answer set is not a proof of unreachability.

At a complete barrier, service the providers in that scope. Local backward
closure activates every premise of any finite requested derivation. A finite
source witness cannot be lost because an earlier request had no answer yet.

This is not exact concrete Java feasibility. The selected model forgets
within-group full marks, static order/context and the existing AP/alias or
eligible-negative details. A selected cleaner retains its whole group by
user rule, even for a singleton catalog group. Those policies are part of
`GroupReference`; a request supplies no additional approximation.

## 3. FLOW is a dependency, not a set of possible groups

Use three distinct messages:

```kotlin
data class FlowLink(val provider: NodeId, val consumer: NodeId,
                    val operation: TransferRef)
data class GroupNeed(val node: NodeId, val groups: GroupSet)
data class GroupAnswer(val node: NodeId, val groups: GroupSet)
```

A FLOW link records a conditional operation on future supplied groups. It
can exist while both endpoint group sets are empty. It creates no group.
GroupNeed activates providers. GroupAnswer carries only derived group supply.
Do not implement `FLOW && needed(g) => supplied(g)` anywhere.

Keep AP geometry separate. Use bounded static Field(C,f) and direct Class(C)
coordinates plus ordinary AP scopes. An exact class/root point does not
supply a deeper field. Exact tainted empty S-root remains forbidden. There
is no S.* initial or `C.f.[any]` initial. Preserve the ordinary EXACT/STAR
initial tags and current-tail rules; changing mark or layer does not change
a tail. Run-1 field exclusions stay empty.

FLOW mark exclusions do not filter needs. A group-excluded NORMAL FLOW
complement still needs its independent group repair. In the new path,
that repair must obtain a derived input group through providers. Exclusions
and an abstract singleton are not positive source evidence.

## 4. Nodes, writers and stores

Prescan closes legal coordinates, operation templates, all possible writer
kinds and the resolved call graph. This includes code stores/copies,
source/rule/model effects, aliases, broad effects and explicit boundaries.
Keep the complete inventory for run-2, including writers not visited in
run-1. Do not build transitive method-by-field or method-by-group sets.

Runtime registration uses reached control/action points:

```kotlin
sealed interface NodeKey {
    data class Static(val slot: StaticSlot,
                      val geometry: StaticGeometry) : NodeKey
    data class Local(val method: MethodKey, val stage: ProgramPoint,
                     val geometry: LocalGeometry) : NodeKey
    data class Input(val method: MethodKey,
                     val initial: CanonicalInitialGeometry) : NodeKey
}

data class WriterBinding(val target: StaticScope, val rhs: NodeId,
                         val operation: TransferRef,
                         val guard: ConditionCircuitRef)

class NodeState {
    var suppliedGroups = emptyGroups() // derived answers only
    var neededGroups = emptyGroups()   // standing interests
    val providers = ProviderSet()
    val consumers = TransferSet()
}

class Run1StaticCommunication {
    val nodes: NodeTable
    val writers: GeometryIndex<WriterBinding>
    val readers: GeometryIndex<ReaderBinding>
    val temporary: MethodBatchTable
    val work: CoalescedNodeQueue
}
```

ProgramPoint identifies the **operation input stage**, including call,
rule-action and F74/F75 lowering stages. Pre-cleaner and post-cleaner keys
are different. A late group enters the input and runs that operation again.
It is never injected after a kill.

Local state has one node per method/stage/AP geometry. It does not carry a
set of remote static originals. Incoming provider links record alternatives
at that local node. A static node has writer and reader indexes; do not
store a writer-by-reader Cartesian product. Productive nonstatic calls keep
their actual call bindings and context policy.

Use GroupSets for answers, needs and temporary deltas. Do not create one
request object or local fact per full mark. Existing suppliedGroups is the
answered history. Pending interest is `neededGroups - suppliedGroups`.
It is not a queued task while no new provider/supply can service it.

Production need not store one proof tree per group. The constructive proof
shows that a witness can be reconstructed from valid roots and transfers.
Do not turn those formal witnesses into another retained per-origin store.

## 5. Registering writers and consumers

Intercept every run-1 static input/result before ordinary per-premise storage.
This includes code, rule/source/model, alias and call/return results. Register
its transfer or accept its already justified group supply. Do not also keep
the old `x -> C.f` carrier row. Any result that depends on this path stays
DEMAND when its current base becomes nonstatic.

```kotlin
fun registerWriter(binding: WriterBinding) {
    if (!writers.addOnce(binding)) return
    connectWriterToStaticNode(binding)
    replayExistingRhsGroups(binding) // derived groups, not hypotheses
    requestPredecessors(binding, oldNeedsFor(binding.target))
}

fun registerReader(binding: ReaderBinding) {
    if (!readers.addOnce(binding)) return
    connectStaticNodeToLocalInput(binding)
    deliverExistingDerivedGroups(binding)
    connectFlowDependency(binding) // no group is added here
}
```

A template alone does not register a live writer/reader or make its method
reachable. A reached read can register its FLOW dependency before any group
is available. This lets local assignment and static-copy structure form
without inventing a marked value.

A new writer sees old field needs. A new need visits old writers directly.
A new reader receives old derived groups. A new source/edge replays only its
relevant existing node frontier. All registration/answer arrival orders
must work. No whole-graph replay for each registration is required.

Static copy `C.f = C.g` links g's read node through the real copy operation
to f. An unseeded f/g cycle stays empty. A later source group enters that
cycle once and reaches each compatible node through fresh group deltas.

The GeometryIndex must meet scopes at the same location. A broad supply,
narrow need and reader can have different keys. Use their AP intersection,
not key equality or unrelated pairwise intersections. Handle all arrival
orders. Do not eagerly copy broad descriptors into every declared field.
The model's finite coordinates stand for certified overlap cells; the
scope/index compiler must preserve that oracle.

## 6. Need propagation and writer answers

Use eager forward delivery of derived groups and lazy reverse needs to
activate missing providers. A known group is delivered in bulk even when
no consumer asked for it. A need is never evidence for the answer.

```kotlin
fun need(node: NodeId, offered: GroupSet) {
    val fresh = nodes[node].neededGroups.unionAndGetGrowth(offered)
    if (fresh.isEmpty()) return
    queueProviderNeeds(node, fresh)
}

fun answer(node: NodeId, proven: DerivedGroupDelta) {
    val fresh = nodes[node].suppliedGroups.unionAndGetGrowth(proven.groups)
    if (fresh.isEmpty()) return
    queueForwardTransfers(node, fresh) // state commits before enqueue
}

fun needStatic(slot: StaticSlot, groups: GroupSet) {
    need(staticNode(slot), groups)
    // Providers are the compatible reached writers; no relay callers.
}
```

DerivedGroupDelta is an internal adapter contract. A callback with group g
is not enough. The adapter must justify the source/input premises and
operation that derived g. Repeated needs and answers merge at their node;
local supplied state suppresses repeat semantic delivery.

For `C.f = p`, the f/g request reaches the registered writer RHS. An ordinary
abstract RHS delegates to the existing original-slot P/R request mechanism:

```kotlin
fun requestInput(provider: InputProvider, groups: GroupSet) {
    temporaryRequests.offer(provider.originalSlot, groups)
}

summaryStorage.commit(temporaryRequests)
temporaryRequests = new RequestStorage()
```

Only actual matching grouped input can ground the answer. Request keys keep
no AP tail, E, mx or ND conjunction. Actual replies still retain their legal
canonical geometry. A group at an exact input point cannot answer an
unrelated descendant geometry merely because its GroupId matches.

Critically, an analyzed `p/(g,*)` initial can be a **hypothesis**. Its result
is a conditional rule:

```text
InputSlot(p,g) AND writerGuard => StaticField(f,g)
```

It is not an unconditional group at f. Satisfy InputSlot from a real caller
binding whose value is itself source-derived, or an explicit boundary input.
A conditional initial/summary never becomes an independent root. If a
provider returns a conditional result, retain its premises until grounded.
The same rule applies to source GEN that depends on an input/group guard.

Ordinary input-request propagation can cross real nonstatic argument flows.
It must not reintroduce a static request at each transparent caller. Static
field needs contact the writer index directly. Group-taint values themselves
do not carry requests backward; FLOW providers carry the dependencies.

## 7. Group operations and guards

A copy preserves its input group. A declared mark operation can change it:

```kotlin
fun forwardGroups(op: TransferRef, inputs: GroupSet): GroupSet
fun predecessorGroups(op: TransferRef, outputs: GroupSet): GroupSet
```

Define these functions from the selected group abstraction of the full-mark
operation. Reverse needs use its preimage. Asking output h can require input
g with `g != h`. Do not pass every request unchanged through a rewriter.
A replacement can also preserve the old group for other full members.
Selected-mark cleaners keep their special whole-group retention rule.

Unknown/native FLOW is not an all-groups producer. Its model must provide
legal pass-through dependencies, declared group outputs or an explicit
finite source group set. A consumer request cannot extend that set.

Conditional source/writer actions retain their original full-literal NNF.
Fold signed contradictions before grouping. Preserve ordinary residual-
negative and non-mark eligibility. Compile AND/OR as a condition circuit;
do not enumerate cubes. Every required positive leaf refers to its actual
bound point/AP and group. Same group at different positions is not one
antecedent. A conjunctive action fires only after all its required inputs
and its operation input are derived. An OR needs a justified alternative.

An output need activates both the RHS group preimage and the positive guard
leaves at their bound points/APs. Request every eligible OR alternative.
Register the action as a consumer of every required input/guard node.
Fresh guard supply must re-evaluate accumulated RHS state even when the RHS
has no new group bits. A late guard cannot leave a supported writer blocked.

A source with a satisfied ordinary non-group guard can be a root for its
declared output group. A source whose group guard depends on itself cannot
bootstrap that group. A cycle of requests, FLOW and guarded source actions
without an independent seed gives no answer.

Admitted selected cleaners preserve derived group values and record group demand.
On ordinary FLOW, retain the valid group-excluded complement and request
the group even when it was already excluded. A request must ignore mx.
It still needs a genuine group input to produce the DEMAND repair.

Keep ordinary cleaner admission: its rewritten condition must be true.
Group-possible guard truth cannot enable a conditional RemoveAll or remove
a provider link. Keep unresolved mark-conditional clean actions for later
full-mark refinement; they cannot kill an unrelated full member in run-1.

An admitted RemoveAll kills local groups and removes transfers across the whole-hit
operation stage. An interest after that kill cannot reach the old local
provider through a fabricated identity edge. Keep definite field kills and
disjoint preservation. A surviving E-update residual moves to DEMAND with
empty E. Do not erase shared static history on a local kill. A later static
read can see an old **derived** group under the accepted order approximation.

## 8. FLOW/local/call pipeline

FLOW builds conditional provider links; supplied group deltas execute them.
Local state stays merged by stage/AP. No wildcard operation may create an
unlisted group merely because the node has a FLOW link.

```text
reached source / explicit boundary
               | derived group set
               v
actual caller value -> input provider -> writer RHS -> static field
                                                        |
                                                  direct delivery
                                                        v
                                               reached reader/local
```

For ordinary local transfers, compile one operation link with its geometric
and guard dependencies. Late answers enter at the input stage and re-run the
real transfer. Do not merge pre-kill and post-kill state.

At calls, use actual argument/formal and return/output bindings. Ambient
candidate dependencies can share local nodes under the selected context
policy. They cannot promote an ordinary NORMAL relation. A normal return
needs a reached normal exit or an applicable ordinary return model. A check
inside a nonreturning method remains eligible without enabling its caller's
later statements.

Use a separate ambient effect carrier. A pooled read/return is not a ZERO-
premise source summary or source hit. A real rule GEN keeps its source
occurrence and every group guard dependency.

No static value or mark need follows a transparent call chain for field
availability. Create no `C.f -> C.f` summary. Keep the single existing
universal `S.* -> S.* [DEMAND]` compatibility summary in ordinary storage;
this path does not subscribe to it or emit an S.* initial.

## 9. Batching and completion

Accumulate fresh need/answer bits and writer/reader registrations in method
temporary batches. Transfer them at method-queue-empty. A submitted batch
schedules counted communication work; it does not wait for global emptiness.
One registered binding joins the old relevant need/answer sets before it
joins future deltas. Commit accumulated state before queueing callbacks.

The queue processes growth, not repeated whole snapshots. On edge growth,
replay that new binding only. Do not clear all visited state or replay every
source when the graph changes. No per-edge/group replay history or producer-
reader/group table is required.

For a finite node/group/transfer universe, fresh wanted/answered bits are
finite. Unseeded cycles may spread needs, but cannot add answers. After the
fresh queue drains they do not block completion. Standing unanswered needs
remain stored for future supply while this run is active.

A complete barrier includes ordinary P/R commits, fresh local group work,
writer/reader registration, communication batches, return effects and ledger
updates. Unavailable supply is not a queued task and does not require a
negative answer. Preserve cancellation and incomplete-run guards. No
arbitrary pending-size threshold is part of this proposal.

## 10. Demand ledger and full-mark run-2

Keep derived check/source/cleaner/output demands at their actual operation
points. The ledger retains the bound occurrence, original full NNF, endpoint,
geometry, context and support obligation. Requests can retain query interests;
they cannot create a positive check hit or a group-taint candidate.
Do not store local groups in the ordinary observer's per-original history.

At a complete handoff, retain the compact ledger and closed writer/call
inventories. Release run-1 graph/value state when the snapshot is owned.
It is not full-mark or execution evidence.

Run-2 is BACKWARD/full/limit 1. Decode every eligible full member of a group
against the closed catalog, including members never observed in run-1.
Group precision does not identify a representative full mark. A rewriter
can also require a predecessor in a different group.

Start explicit full queries at the actual check/read/guard/output stage.
Method-exit starts alone miss checks in nonreturning methods. The query is
a search request, not actual supplied taint. Ordinary reverse CFG/call
transfer must not require a matching F1 relay summary. At method entry,
visit compatible incoming prescan call ports with their real stage/context.

```kotlin
fun onBackwardStaticRead(read: ReadSite, q: FullQuery, session: Session) {
    enqueueOrdinaryPredecessors(read, q, session)
    for (writer in closedInventory.mayOverlap(read, q.geometry)) {
        val opening = session.dependencies.add(read, q, writer)
        for (predecessor in reverseWriter(writer, q))
            enqueueWriterSearch(opening, predecessor, session)
    }
}
```

Keep all compatible writer kinds, including unvisited F1 writers and broad
boundary effects. Do not use an incomplete or demand-sliced F1 answer set
as a negative B2 writer filter. A later full query/rewriter can expose a new
input or location. Complete inventory lookup remains the safe fallback.

Openings are DEMAND discovery queries only. They create no executable
writer-to-reader edge, actual source or call return. Preserve the original
read and every intermediate writer/guard and parent-to-predecessor edge.
Deduplicate full query work separately from these dependency edges. Static
copy cycles use the ordinary fair queue; no fixed-depth runtime cap is added.

## 11. Full-mark continuation and confirmation

Run-2 records exact source permissions from its ordinary query analysis.
Their identity is `(MethodKey, statement, complete forward PathEdge)`, plus
stable predicate occurrence where needed. Preserve full marks, geometry and
forward orientation. A writer ID or group is not a source permission.

Authorize the whole ordinary reverse query closure without F1 relay guides.
The following forward run starts from actual configured roots and exact
permitted source actions. Its source GEN/publication, call responses and
ordinary transfers need the same session authorization. If precise guide
scope is unavailable, leave the relevant full-mark root-reachable scope
unpruned by F1 guidance. Close it over ordinary mark rewrites, including
other owner groups. This can cost later work; it cannot invent a source.

Ordinary full-mark propagation must reconstruct source, real call order,
returns, overwrites, cleaners, original conditions and ND support at the
original check. An isolated writer RHS answer does not discharge that read.
A finding still needs the existing confirmation certificate/trace contract;
F3 sink arrival alone does not set `SinkWitness.confirmed`.

All run-1 group values and static-related results remain DEMAND despite
the improved group precision. Independent NORMAL FLOW/summary records keep
their ordinary proof. An old group-excluded NORMAL record cannot close a
later full query in that group. No speculative discovery edge enters an
executable NORMAL record.

## 12. Traced cases

```java
void A(Object p) {
    C.f = p;
    B();
}
void B() { check(ContainsMark(C.f, T)); }
```

```text
F1: A's reached writer registers RHS provider p; no group is published.
F1: B requests g = owner(T) on f.
F1: f's writer receives g and requests the ordinary input p/g.
    A canonical p/g analysis may describe p/g -> f/g, but is not a root.
F1: an actual source/caller input g grounds p/g; the writer now publishes g.
F1: f directly delivers g to B. The check can produce a DEMAND candidate.
    If only group h arrives at p, no g answer or g check hit is produced.
B2: all eligible full g members remain queryable at the original check.
F3: ordinary full-mark analysis must establish the real path and conditions.
```

For `B(); C.f = p`, a real later g writer can still answer B under the
accepted static order approximation. The group needs a real source/writer
derivation, but this does not prove the order. Later analysis checks it.

```text
C.f = C.g; C.g = C.f; check(C.f,T)
  no independent source -> needs can circulate; no group is supplied
  later source of g     -> g crosses each copy once as fresh node growth

x = C.f; relay(x); check(x,T)
  check need -> local provider/call links -> field -> writers
  actual group answer returns through the same productive local transfers
  no remote static-original set is attached to x
```

A rewrite from group g to group h must send an h request to the correct g
provider and retain its guard. A guarded source with two positive leaves
needs both bound-position antecedents, even if both have the same GroupId.

## 13. Proof, savings and acceptance

The new constructive models must prove source-rooted answers, completeness
for finite derivations, no-seed cycle emptiness, request non-invention,
late writer/need/source coverage, batch equivalence and finite fresh work.
The [proof dossier](run1-group-static-pool-proofs.md) states the exact scope.
The previous FLOW-plus-interest model is historical and does not prove this
revision's group precision.

Compare direct writer/consumer communication with an expanded reference
using the **same** group operations and accepted static order/context model.
Only static identity availability relays may be contracted. Productive local
transfers, source guards, ordinary calls and geometric bindings stay present.
The two procedures must derive the same group facts after that projection.

A transparent static call chain needs no field/group relay nodes. Writers
and real readers communicate directly. Group bitsets bound the mark
coordinate by the actual group count, not all full marks. However, local
provider links, ordinary input joins, condition circuits and B2 discovery
remain costs. There is no universal byte/time saving or 60-second proof.

Implementation order:

1. Add sparse node/writer/reader registration and source-backed GroupSets.
2. Compile conditional FLOW transfers and guards, including ordinary input
   hypothesis versus actual input grounding. Intercept all static carriers.
3. Add fresh reverse needs, ordinary P/R bridge and eager forward answers.
4. Add method-empty batches, late-binding replay and complete-barrier work.
5. Keep explicit full-mark point-query, closed writer lookup and continuation
   authorization from the previous handoff proposal.
6. Differentially test the constructive models, then the required core and
   fixed project gates. No runtime implementation is included in this task.

Required controls include wrong-group input, request/FLOW/hypothesis-only
supply, empty cycles, late writer/source/need, two-leaf source guards,
rewriter preimages, cleaner stages, broad scopes, nonreturning checks and
full-member B2 queries. Measure node/edge counts, requested/answered group
bits, relay carriers, actual input joins, candidate counts and later query
work. Every report includes the latest reached/completed/accepted run.
