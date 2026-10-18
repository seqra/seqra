# Run-1 statics: forward edges and direct mark refinement

Date: 18 October 2026. Status: **revised proposal; analyzer unchanged**.
Language: ASD-STE100 Simplified Technical English. Defined technical terms,
code names and mathematical notation keep their stated meanings.

This proposal follows [proof-guide.md](proof-guide.md). Its
[proof dossier](run1-group-static-pool-proofs.md) states the checked scope.
Use the selected [mark representation](rule-group-marks.md).
This revision replaces the backward provider graph with ordinary forward
path edges. It also replaces the empty-group output-provider mechanism.
There is no second backward analysis inside run-1.

The [immediate static-summary revision](run1-immediate-static-summaries.md)
refines write publication: publish a static effect immediately, then lift
it through caller summaries without local static propagation. That revision
also proves that sink-origin wrapper paths use the existing backward ZERO
exception. They do not require the extra carry guides described below for
a nonzero static query.

The selected static order/caller-context approximation stays in effect.
A static read emits its conditional edge only after a compatible FLOW or
group-valued write has been published. A late write wakes waiting reads.
Every supplied group needs a finite source/writer derivation. Write presence,
a conditional FLOW edge, ZERO and a request cannot create group supply.
The handoff must also contain the required GROUP demands in each method.
A marked edge at one sink does not supply the group demand of its callees.

Measured runtime remains `63d5481e4e4b69ecf042c1149bb574ad40fd1624`.
Latest reached / completed engine / accepted forward report: OWASP
**run-7 / run-6 / run-5**; conductor **run-1 / none / none**;
ThingsBoard **run-1 / none / none**; Stirling-PDF **run-2 / run-1 / run-1**.
These results do not measure this revision. Conductor stops with run-1 OOM.
The target remains run-1 engine time within 60 seconds. IR storage and
reference/cache-manager changes are outside scope.

## 1. Current design versus the new design

| Area | Rejected provider-graph proposal | New proposal |
| --- | --- | --- |
| Static read | Register a local provider node linked to a shared static node. | Wait for a compatible published write, then emit `C.f.* FLOW -> lhs.* FLOW`, DEMAND. |
| Local propagation | Maintain provider links as well as group values. | Propagate the edge with the existing forward engine. Keep its static initial. |
| Return | Publish a structural output provider. | Publish the ordinary conditional return summary, even before a group arrives. |
| Caller | Bind provider nodes across the return. | ZERO can activate a static-premised summary. Preserve that static premise in the caller edge. |
| Refinement | Walk local operations and call bindings backward. | Read the static initial from the current edge and subscribe directly to writers of that field. |
| Answer | Send answers through the provider graph. | Enqueue the refined edge at the subscribed statement/stage. |
| Static copy | Use a provider dependency and reverse needs. | Forward actual group deltas from the original static field to the written field. |
| Stored state | Local provider graph, needs and answers. | Ordinary edges/summaries, writer supply and direct continuation subscriptions. |

The measured runtime still carries static inputs through ordinary call ports.
The new proposal removes pure static availability relays. It does not remove
the static initial coordinate from productive local edges and summaries.
That coordinate is required for direct refinement.

## 2. Terms, marks and legal shapes

`C.f.*` below means static base S, class accessor C, field f and STAR tail.
`FLOW` is the abstract pass-through mark. It is different from the wildcard
in group mark `(G, *)`. `T` in a rule has group `owner(T)`.

```text
conditional edge at S:  (C.f.*, FLOW) -> (x.*, FLOW) [DEMAND]
refined edge at S:      (C.f.*, Group(G)) -> (x.*, Group(G)) [DEMAND]
```

A conditional edge describes a dependency. It does not assert that C.f has
a group. Its initial read also needs a compatible published write. A
refined edge needs compatible, source-derived group supply at C.f.
All static-origin edges and their dependent results stay DEMAND, including
results whose current base is nonstatic.

Keep the legal initial families in [the mark contract](rule-group-marks.md#4-legal-marks-and-layers).
There is no S-root initial, ANY initial, abstract class initial, second field
accessor or nonstatic field path in an initial. Static FLOW initials have
field form `S.C.f.*`. Taint initial tags remain EXACT or STAR. A concrete
current ANY tail does not become an ANY initial. Group projection does not
change the initial tag or the current tail. Run-1 field exclusions are empty.

Point refinement does not call the method-entry abstraction. In particular,
it does not apply an entry STAR-to-ANY start conversion at the new point.
Use the surviving edge's ordinary AP relation and compatible supply geometry.
The simple STAR example above preserves that STAR relation. For general
scopes, use ordinary intersection and projection, not blind tail replacement.

## 3. Stores and ownership

Keep ordinary method path-edge and summary stores. Do not add a local
provider graph or reverse worklist. The extra static state is:

```kotlin
data class StaticContinuation(
    val owner: MethodKey,
    val inputStage: ProgramPoint,
    val edge: PathEdgeRef, // static initial, current AP, branch/support
)

class RefinementRow {
    var wanted = emptyGroups()
    var delivered = emptyGroups()
}

class StaticFieldState {
    val written: ScopeSet // published FLOW/group writes, independent of marks
    val reads: ScopeIndex<ReachedStaticRead, AdmittedRegions>
    var supplied = emptyGroups() // source-derived groups only
    var wanted = emptyGroups()   // union of direct group interests
    val continuations: ScopeIndex<StaticContinuation, RefinementRow>
    val staticWriters: ScopeIndex<StaticWriterContinuation>
    val ordinaryWriters: ScopeIndex<OrdinaryWriterContinuation>
}

class Run1Statics {
    val fields: StaticScopeIndex<StaticFieldState>
    val temporary: MethodBatchTable
    val work: CountedMessageQueue
}
```

`ProgramPoint` includes the operation input, call and F74/F75 action stages.
A continuation retains the exact edge branch and ordinary support needed
for its injection. It is not a list of preceding operations. Merge wanted
and delivered groups for the same continuation. Use GroupSets, not one
stored request or ordinary initial per full mark.

The index must handle broad and narrow scopes at a common AP location.
An exact class/root value does not supply an unrelated field. Intersect
writer supply with the subscribed original geometry and its current image.
Do not replace this check with group equality or field-name equality alone.
The finite model treats AP coordinates as certified overlap cells.
Each supplied-group row belongs to one normalized overlap cell. Each
refinement row also identifies its projected current AP image. In the
subscription pseudocode, `field` and `at` denote that resolved cell/image.
The scope index iterates matching cells before invoking those operations.
Do not share one delivered G bit across distinct regions: a later write
that exposes a new region for G must still deliver its new current image.
Keep broad descriptors compact; do not expand them into every declared field.

The writer index stores reached writer continuations. It does not store a
writer-by-consumer product or a source proof tree for each answer. Ordinary
forward propagation validates roots and operations. The Lean proof yields
executable witnesses; runtime need not retain those proof objects.

Prescan also retains the complete writer/call inventory for run-2, including
writers not reached in run-1. Do not build transitive method-by-field or
method-by-group sets. Published write presence, group supply and run-2
inventory are different stores. A prescan writer candidate is not a published write.

## 4. Static reads wait for a corresponding published write

Register a control-reached read as pending. Emit its conditional edge only
where a compatible write is already published. Join both arrival orders:

```kotlin
fun onStaticRead(read: ReachedStaticRead) {
    fields.reads.registerOnce(read)
    admitRead(read, fields.written) // existing writes join the new read
}

fun onPublishedStaticWrite(write: PublishedWriterEffect) {
    // Real FLOW/group output at a reached write, not a prescan candidate.
    val fresh = fields.written.unionAndGetGrowth(write.targetScope)
    if (fresh.isEmpty()) return
    queueMatchingReadAdmissions(fresh) // pending reads join the new write
}

fun admitRead(read: ReachedStaticRead, written: ScopeSet) {
    val fresh = compatibleReadRegions(read, written) - read.admittedRegions
    if (fresh.isEmpty()) return
    read.admittedRegions.union(fresh) // commit before queueing
    val initial = staticFieldFlowInitial(read.field, tail = STAR)
    for (current in read.forwardFlowImages(initial, fresh))
        enqueueAt(read.outputStage, edge(initial, current, layer = DEMAND))
}
```

A corresponding write is a published real writer effect with compatible
geometry. It can carry FLOW or a taint group. It need not contain the group
that a later rule will request. A reached `p FLOW -> C.f FLOW` output
therefore admits C.f reads before p/G is known. This lets a later check
request p/G through the existing ordinary-input protocol.

A syntactic store found during prescan does not qualify by itself. Neither
an ordinary static pass-through, a local read identity nor the synthetic
`S.* -> S.*` compatibility summary is a write. Source/rule/model/alias and
boundary effects qualify when they publish a real static write/supply.
Group supply commits corresponding write presence first; presence alone
never creates a group.

With no published compatible write, `x = C.f` emits **no** `C.f.* -> x.*`
edge and no conditional return summary can derive from that read. Retain
only the pending read descriptor. A later write schedules the fresh read
output directly; it does not replay the method entry or traverse callers.
For broad scopes, retain admitted regions and emit new compatible regions
as later writes arrive. A single boolean for the entire read is insufficient.
Use legal canonical initial shapes and ordinary AP projection for each image.

This gate withholds the conditional taint dependency only. ZERO/control,
normal-return reachability and independent source/model actions still run.
For example, a source action on a getter result must not wait for an old
marked value in C.f when that source action does not depend on such a value.
Eligible non-mark and negative rule handling keeps its ordinary contract.

An admitted read produces `C.f.* -> x.*`, not `ZERO -> x` or an unconditional
group at x. No incoming C.f entry fact or field request is needed. Static
reads include code loads and static operands of reached source, sink,
cleaner and model conditions. A direct `ContainsMark(C.f,T)` also waits for
a compatible write before its local conditional `C.f FLOW -> C.f FLOW`
branch can request owner(T). This local edge is not an identity summary.
No static field-request store is introduced.

Assignments, actual argument/formal bindings, returns and ordinary summary
composition preserve the static initial when they propagate the admitted
edge. Definite kills remove the affected current branch. Disjoint branches
keep their geometry. If preservation needs an E update, keep empty E and
DEMAND under the selected run-1 field-write rule.

A method that only passes static availability through a call chain receives
no per-field identity seed. A productive nonstatic transfer can carry an
admitted static-origin edge as usual. Pending reads with no matching write
remain dormant and do not block a complete barrier.

## 5. ZERO activates static-premised summaries

A reached normal return publishes the conditional summary after its read
has been admitted. A FLOW write is sufficient; no actual group is required:

```text
static_get: return C.f
summary:   C.f.* FLOW -> return.* FLOW [DEMAND]
```

At a reached call, ZERO can trigger application of a compatible summary
with a static premise. ZERO supplies control activation only. It does not
replace or discharge the static premise:

```kotlin
fun applyStaticSummary(call: ReachedCall, summary: StaticSummary) {
    // ZERO at this call activates both old and newly published summaries.
    val current = call.bindOutput(summary.current)
    val result = edge(initial = summary.staticInitial,
                      current = current,
                      layer = DEMAND,
                      support = summary.support)
    enqueueAt(call.afterCalleeStage, result)
}
```

```text
callee summary:       C.f.* -> return.*
caller has ZERO:      call static_get()
caller result:        C.f.* -> t.*       // not ZERO -> t.*
```

Register the call's summary interest once. Join old summaries at call
registration and new summaries at publication. If the read is still pending,
the ordinary control return can proceed without this conditional summary.
Late read admission then publishes the summary to the existing call interest. Use the real target,
normal/exceptional output binding and action stage. A normal output needs
a reached normal exit or an applicable ordinary normal-return model. An
internal check in a nonreturning method stays eligible; it does not enable
the caller's later statements.

This exception applies to the static premise only. ZERO cannot satisfy an
ordinary parameter, an independent mark guard or another ND premise.
Retain those ordinary conditions/support. Applying a group-bearing static
summary retains its already grounded group and its static initial too.
No application is reclassified as a ZERO-source hit.

Create no `C.f -> C.f` summary merely to transport field availability.
Keep the existing single `S.* -> S.* [DEMAND]` compatibility summary if its
ordinary lane needs it. This lane emits no S.* initial and uses no such
summary to transport field groups.

## 6. Direct refinement at the current point

When a rule needs group G for a surviving static-origin FLOW edge, register
a continuation on the edge's initial static field. Do not visit providers,
method entries or preceding statements:

```kotlin
fun refineAt(point: ProgramPoint, input: MarkPreservingFlowEdge,
             groups: GroupSet) {
    when (val original = input.initial) {
        is StaticFlowInitial -> {
            val continuation = StaticContinuation(owner, point, retain(input))
            statics.subscribe(original.fieldScope, continuation, groups)
        }
        is OrdinaryFlowInitial -> ordinaryRequests.offer(original.slot, groups)
        else -> keepExistingConcreteOrZeroRule(input, groups)
    }
}

fun subscribe(field: StaticScope, at: StaticContinuation, offered: GroupSet) {
    val row = fields[field].continuations.getOrPut(at)
    row.wanted.union(offered)
    deliverAvailable(field, at, row) // old writer supply joins new interest
    activateOrdinaryWriters(field, offered)
}

fun deliverAvailable(field: StaticScope, at: StaticContinuation,
                     row: RefinementRow) {
    val matching = compatibleSupply(field, at.edge)
    val fresh = (row.wanted intersect matching.groups) - row.delivered
    if (fresh.isEmpty()) return
    row.delivered.union(fresh) // commit before queueing
    for (reply in instantiateAtPoint(at.edge, matching, fresh)) {
        enqueueAt(at.inputStage, reply.withLayer(DEMAND))
    }
}
```

For the identity FLOW example, instantiate only the group payload:

```text
stored continuation at S:  C.f.* FLOW -> x.* FLOW
wanted:                   G
source-derived supply:    C.f/G
new work at S:            C.f.* Group(G) -> x.* Group(G) [DEMAND]
```

Use the **operation input stage** that raised the need. Re-run that operation
normally. Do not inject its output or skip its guards, cleaner or call stage.
No method-entry replay is part of this answer.
Intern the refined initial descriptor if the worklist needs its key. This
does not start an entry analysis or mark that initial's entry analysis as
complete. Point admission and entry seeding are different operations.

A new supply joins old subscriptions. A new subscription joins old supply.
An unanswered subscription stays pending without queued work. A request
cannot add a group to the field pool or satisfy itself. Repeated answers
merge with delivered groups and ordinary path-edge deduplication.

This substitution rule applies to mark-preserving FLOW. It is not a rule
that maps an arbitrary output group to the same original group. Section 8
specifies mark-changing actions without a reverse dependency traversal.
`MarkPreservingFlowEdge` is a checked view of an ordinary edge, not a new
graph node. A group-bearing current branch uses ordinary taint handling,
even when its original is FLOW. Do not instantiate that transformed branch
from every group supplied at its original field.

## 7. Writer publication and static copies

Publish only groups justified at a reached writer. Keep the distinction
between conditional FLOW and actual group supply:

```kotlin
fun onStaticGroupWrite(write: ReachedWrite, value: GroundedGroupValue) {
    onPublishedStaticWrite(write.effect(value.geometry)) // presence first
    val fresh = fields[write.target].supplied.unionAndGetGrowth(value.groups)
    if (fresh.isEmpty()) return
    queueMatchingContinuations(write.target, fresh)
    queueStaticWriterContinuations(write.target, fresh)
}
```

A source/rule/model/boundary value needs its normal action permission and
all required group guards. A hypothetical taint initial, catalog member,
FLOW edge or request is not `GroundedGroupValue`. The pool is a monotone
union under the selected static order/context approximation. A local kill
does not erase historical field supply from that pool.

### 7.1. Static-origin writer

A conditional edge `C.g.* FLOW -> C.f.* FLOW` registers a **bulk forward**
writer continuation at the store input. It listens for all grounded groups
of its original C.g, not only groups requested by a consumer of C.f:

```kotlin
fun onStaticOriginWriter(point: ProgramPoint, input: MarkPreservingFlowEdge,
                         write: Write) {
    subscribeAllSuppliedGroups(input.initial.staticScope,
                              WriterContinuation(point, retain(input), write))
    // Join old supply and future fresh deltas. No target-to-source request.
}
```

For a new C.g/G, instantiate at this writer's input and run the real write
forward. It can then publish C.f/G. Forward copies can form cycles. With
no source/boundary seed, they supply no group. A later seed spreads through
fresh field-group deltas. No consumer request walks the copy chain backward.

The conditional FLOW result of a real store also publishes write presence
for its target. That result exists only after its RHS conditional read was
admitted and ordinary forward transfer reached this write. A source-less
copy cycle with no independently published write cannot admit its own reads.

The same rule covers static-origin rule/model/alias effects. Preserve the
real input stage, group operation, geometry and guard. Do not treat every
writer as a mark-preserving copy.

### 7.2. Ordinary-origin writer

For an abstract ordinary edge `p FLOW -> C.f FLOW`, retain a writer
continuation keyed by the ordinary initial p. A C.f/G interest can ask this
reached writer for p/G using the existing original-slot P/R mechanism:

```kotlin
fun requestOrdinaryWriter(writer: OrdinaryWriterContinuation, groups: GroupSet) {
    ordinaryRequests.offer(writer.input.initial.slot, groups)
}

summaryStorage.commit(ordinaryRequests)
ordinaryRequests = new RequestStorage()
```

Publish ordinary-origin FLOW write presence before waiting for a group
request. A new writer joins old field interests. A new field interest joins
old writers. Merge their group requests. This is a direct lookup of the known
initial p, not a walk from the store to assignments or callers. Ordinary
nonstatic input requests retain their existing protocol. No new backward
static solver or local predecessor graph is permitted.

Only actual matching caller/boundary group supply can ground p/G. Analyze
`p/G -> C.f/G` conditionally if needed, but do not publish C.f/G until that
input premise and the writer's guards are grounded. Ordinary marked writes
publish eagerly once grounded, even when no field interest exists.

## 8. Mark operations, conditions and kills

Keep the existing forward rule semantics. A group-sensitive operation asks
for the input groups needed by **that operation at its input stage**. Resolve
that interest from the incoming edge's initial, as in section 6. Then execute
the operation forward on each supplied group delta.

```text
before rewrite:  C.f FLOW -> x FLOW
rewrite needs g: subscribe to C.f/g at this operation input
answer:          C.f/g -> x/g at this operation input
forward rewrite: C.f/g -> y/h, if the declared operation maps g to h
```

A later h check sees the forward h result. It must not request C.f/h merely
because h is the current mark. The transformed branch is an ordinary group
branch, not a mark-preserving FLOW template. If an operation can inspect or
transform any input group, its local declaration must cover all eligible
input groups. Retain its forward image and original full-mark obligations.
No output request recursively computes preimages through earlier operations.

Keep signed full-literal NNF and fold contradictions before group projection.
Do not expand cubes. Conditions on sources and sinks keep bound positions,
original full identities, eligible negatives and ND support. A required
positive leaf registers its own direct initial-slot interest. Same-group
leaves at different positions stay different antecedents. Late supply runs
the ordinary rule reevaluation for the retained input and guard state.
A guarded source cannot bootstrap itself without an independent group root.

Selected-mark cleaners cannot remove a group in run-1. They register the
required interest at the cleaner input and retain the DEMAND group branch.
FLOW mark exclusions do not filter that interest. A NORMAL group-excluded
complement does not itself provide group supply or replace the DEMAND repair.
A surviving selected-cleaner branch can retain its group-preserving DEMAND
relation even when its NORMAL FLOW alternative excludes that group.

RemoveAll retains its ordinary true-condition admission and whole-hit kill.
A genuine kill leaves no surviving template through which to inject the old
group later. Definite field kills and disjoint preservation stay as specified.
Do not merge before-kill and after-kill stages. Partial hits use their real
AP relation. Unknown/native models must declare their actual transfers or
explicit source groups; an unknown effect is not an all-groups source.

## 9. Batching, duplicates and completion

Accumulate read/write-presence registrations, group interests and supply
deltas in temporary per-method batches. Commit at method-queue-empty. A
committed batch schedules counted cross-method work; it does not wait for
global emptiness.

```text
method queue empty
  -> commit ordinary requests, reads, write presence and static deltas
  -> replace temporary stores
  -> admit read regions from old/new writes, then join supply/subscriptions
  -> queue fresh current-point replies and forward writer work
```

Commit each store before queueing its callbacks. Late call subscriptions
join existing conditional summaries too. Process growth, not whole-graph
replay. There is no graphChanged flag, reverse provider traversal or per-edge
history of backward visits.

A complete barrier includes ordinary work, conditional summary publication,
P/R commits, pending-read admissions, write presence, static supply, direct
subscriptions, point replies, forward writer callbacks and group-guide
closure. Preserve
cancellation and incomplete-run guards. An
unanswered subscription or read without a write is stored interest, not
unfinished queued work.
No arbitrary pending-size threshold is part of this proposal.

For finite scopes and groups, a field/group supply bit grows once. A
continuation/group delivered bit also grows once. A bulk writer handles
fresh input groups through its ordinary forward operation. These limits
exclude repeated semantic answers; they do not bound the number of
distinct ordinary static-origin edges or compatible AP intersections.

## 10. Run-2 handoff and later confirmation

Run-1 group edges remain DEMAND. Record derived check/source/cleaner/output
demands at the actual operation point, with bound occurrence, full NNF,
geometry, context and support. A conditional FLOW edge or unanswered
subscription alone is not a positive group hit or confirmed finding.

### 10.1. Generate group demands before freezing the handoff

Run-2 emits a full-mark initial only when an explicit matching demand
contains that mark's **group**. It does not require the exact full mark in
run-1. FLOW guidance alone cannot provide this permission. The demand must
match the actual method/port and geometry; a group demand at a caller sink
is not automatically a group demand in its getter or ordinary helper.

Point refinement skips the grouped prefix. Therefore, include a forward
**group-guide generation** phase before the handoff. It annotates the
existing conditional interfaces; it does not replay body taint facts or
walk a local provider graph backward. Retain:

```text
conditional summary rows:       original -> method output
reached call-input rows:         caller original -> actual formal slot
internal point rows:            original -> check/source/cleaner input
writer rows:                    original -> written field at the real stage
actual source/input/field groups and their ordinary support
```

Register bound observation targets on ordinary control reachability, even
before a grouped hit is observed. The handoff cannot rely only on report
hits and normal-return summaries to find its internal query starts.

For identity FLOW, maintain a merged group mask per canonical original slot.
Static originals read the compatible grounded field mask. Reached ordinary
call-input bindings transfer masks to their actual callee slots. Decorate
summary and point rows with the mask of their original. Join late rows and
masks, drain the forward closure, then freeze the group demands.
See [the coverage specification](run1-static-demand-coverage.md) for the
pseudocode, counterexample and precise proof boundary.

```text
getter FLOW summary + C.f/G:
  demand(getter, entry=return/ANY/Group(G), exit=C.f/STAR/Group(G))

helper p FLOW -> return FLOW + a real reached input binding carrying G:
  demand(helper, entry=return/ANY/Group(G), exit=p/STAR/Group(G))
```

Full mark T can use these demands when owner(T) = G. Its actual identity
stays T. A rewriter can need different entry and exit groups; keep the
real group relation and all positive guard premises. Same-group identity
decoration is insufficient for arbitrary mark-changing actions.

Each required writer opening and its RHS/guard/call continuation also needs
an explicit group-demand path. A writer-inventory permission alone cannot
pass the mark gate. Internal starts need group demands at their real points,
including checks in methods with no normal exit. Their demands are not
fictitious normal-return summaries.

After run-1, install the [shared static GROUP demand](run1-universal-static-carry.md)
for every method. For each eligible G, its entry is root S/ANY/Group(G)
and its exit is root S/STAR/Group(G). Store this as one implicit view with
the complete group catalog. It applies to later-created method actors too;
it does not depend on run-1 field supply or `result.analyzers` membership.
Do not construct a method-by-field-by-group table or a marked S-root initial.
Match and retain the actual bounded field query and full mark T.

This DEMAND pair permits query admission and compatible static result
publication. It is not an executable identity summary, write presence or
positive group supply. Run-2 still analyzes the body and applies its actual
kills and write pivots. The earlier source-derived field carry construction
is sufficient but is no longer required for these relays.

Ordinary run-2 calls retain their real caller before/after stages, callee
entry/exit, target context and exit kind. Their actual queries and ZERO-only
subscriptions do not require marked run-1 subscriptions. The field pool
alone cannot identify a caller continuation.

At `set(p) { C.f = p; }`, a carry pair `C.f/G -> C.f/G` cannot publish
the reverse RHS p/T. Capture the writer FLOW row before intercepting its
static output. Forward input-mask closure must generate the separate
`C.f/G -> p/G` writer pair and writer/RHS point guides. This query-only pair
does not require an emitted run-1 method-exit summary. It does not assert
that the writer reaches a method exit. Keep the actual source point and
source permission for a source-generated write; its reverse source result
is unmarked ZERO. See the [read-to-write path](run1-static-demand-coverage.md#51-the-real-read-to-write-path)
for the complete guide pipeline.

### 10.2. Full-mark run-2

At a complete handoff, retain the compact group-demand ledger, required
interface/carry/point guides and complete writer/call inventory. Release
run-1 subscription state only after snapshot ownership and guide closure
are secure. Preserve independently valid NORMAL summaries under their
existing rules. A group-excluded NORMAL record cannot close a later full
query inside that group.

Run-2 is BACKWARD/full/field-limit 1. Decode every eligible full member of
each demanded group, including members not observed in run-1. Start queries
at internal check/read/guard/output points, not only method exits. Ordinary
reverse CFG/call transfer need not have a run-1 static carrier fact, but
its marked method input must match an explicit group guide. At entries,
visit compatible incoming prescan ports with their real stages and guides.

A backward static read can open every compatible writer from the complete
inventory. Preserve the original read, writer guards and all intermediate
query dependencies. Openings are discovery queries, not executable static
jumps. F1 supplied groups cannot be a negative writer-inventory filter.
Demand generation must cover the actual marked query/continuation too;
lookup completeness alone does not prove query-admission completeness.
This backward work belongs to run-2, not run-1 request handling.

### 10.3. Publication and later confirmation

A paired demand must admit the backward input and retain the relevant
result during publication. Do not admit a marked callee input, then lose
its source/RHS result against an old FLOW-only or wrong-group exit pattern.
Keep all required parent-to-predecessor dependencies even when queries merge.

Retain exact source-permission identity: method, statement, full forward
PathEdge and predicate occurrence where needed. The next full forward run
starts from actual roots and permitted source actions. Guidance can leave
AP/control scope unpruned, but this is not permission to bypass the explicit
mark-group gate. Reconstruct real call order, returns, overwrites, cleaners,
full conditions and ND support. A finding still needs the existing
confirmation certificate. Run-3 is FORWARD/full/limit 2; later limits
increase as specified.

The coverage proof must establish the whole chain: a finite reference
witness produces the group-demand seed, all intermediate method/point/carry
and writer guides, and the paired publications needed by run-2. The finite
models check their stated concept/interface scopes. Complete JVM witness
compilation, source eligibility and queue refinement remain explicit gates;
the previous writer-search theorem alone did not establish this chain.

## 11. Trace of the getter example

```text
main:           static_set(); static_consume()
static_set:     t = src(); C.f = t
static_consume: t = static_get(); sink(t)
static_get:     return C.f
```

Let the source supply G and the sink test a full mark owned by G. If the
getter is processed before a C.f write is published, record its read as
pending and emit no conditional edge. Its ordinary control return still
proceeds. When the real source/write publishes C.f/G, first commit C.f write
presence. It wakes the pending read, then ordinary forward work produces:

```text
static_get read:      C.f.* FLOW -> temporary.* FLOW
static_get summary:   C.f.* FLOW -> return.* FLOW
consume call, ZERO:   C.f.* FLOW -> t.* FLOW
at sink input S:      subscribe(C.f, G, continuation = this edge at S)
```

C.f/G is already stored when this sink subscription arrives, so the join
replies directly. If a FLOW write admitted the read first, the subscription
can precede group supply instead. Both orders give:

```text
pool C.f has G
  -> enqueue C.f.* Group(G) -> t.* Group(G) [DEMAND] at S
  -> evaluate sink at S
```

Before the handoff, the guide-generation phase also emits the getter's
`return/Group(G) -> C.f/Group(G)` demand. If the read-to-return path calls an
ordinary helper, its reached input binding supplies G to that helper's
original-slot mask and emits its paired group demand too. The final sink
edge alone would not provide either callee's demand.

There is no request from the sink to `static_get`'s return, its read or
`main`. There is no method-entry replay. The getter's forward conditional
summary already carries the information needed to address C.f directly.

This statement concerns run-1. Run-2 follows the real backward path:

```text
consume: Z -> t/T at the sink
  -> getter return/T       [getter return/G -> C.f/G guide]
  -> getter entry C.f/T    [productive getter result]
  -> consume Z -> C.f/T    [actual getter call binding]
  -> consume entry        [ordinary reverse CFG, ZERO publication]
  -> main Z -> C.f/T       [ZERO-only consume subscription]
  -> setter C.f/T input   [actual call, shared static GROUP permission]
  -> C.f = t              [ordinary reverse CFG]
  -> t/T                  [actual store pivot + productive writer guide]
  -> actual source hit    [exact source match; unmarked ZERO result]
```

The carry relation covers omitted static relays. Getter/helper and writer
pairs cover productive transfers. A call-graph edge alone cannot replace
the actual ports. A writer lookup alone cannot replace the corridor or its
group demands. Ordinary run-2 call/CFG checks preserve order and context;
the run-1 pool's accepted approximation does not confirm a flow.

For `C.f = C.g; C.g = C.f`, with no independently published write, both
reads stay pending. A real external/FLOW write can admit the cycle; a
source-derived G is still required to supply a group. For `C.f = p`, a field
need can directly request the known ordinary initial p/G; it cannot treat
that request or a hypothetical result as a source.

## 12. Proof scope, savings and implementation order

The [read-admission model](lean/ApSpec/Run1StaticReadAdmission.lean) proves
that a reached read and a corresponding published write are both required.
Late write/read registration, merged batches and fresh admissions preserve
that join. Group publication establishes write presence first. Under this
maintained invariant, the gate loses no already supplied requested output
in the finite identity-template model. It does not prove whole-engine
control/operation compilation.

The [direct-refinement model](lean/ApSpec/Run1StaticInitialRefinement.lean)
checks mark-preserving conditional templates and direct supply joins.
Refinement commutes with ordinary identity forward transport. ZERO-triggered
application preserves the static premise. Every emitted branch has a
source-rooted field certificate and DEMAND layer. Merged subscriptions,
supply and templates have the same outputs as the scalar oracle in all
arrival orders. Replies keep the subscribed point and current AP. One direct
lookup is independent of the preceding forward-template depth.

The positive group calculus still covers rooted writer operations and
static relay contraction. The full-mark handoff model still covers its
finite discovery scope. The old reverse-need protocol and backward request-
slice model are historical. They are not the algorithm of this revision.
AP/guard/operation compilation, ordinary engine composition and runtime
queue refinement remain explicit adapter obligations in the proof dossier.

Expected savings come from removing static availability relay chains,
full-mark payloads in run-1, the extra provider graph, reverse request work
and method-entry answer replay. A read with no published write also avoids
its downstream conditional edge and summary work. The new design **retains static originals**
in productive edges and summaries. Distinct field/origin/current combinations,
forward writer subscriptions and point continuations still cost memory.
No universal byte/time saving or 60-second completion is proved.

Implementation order, after adapter proofs and controls:

1. Register pending reads and published write presence; emit conditional
   read edges only for their compatible overlap.
2. Add ZERO-triggered conditional summary application with preserved premise.
3. Add merged writer supply and direct current-point subscriptions.
4. Add bulk forward static-origin writers and existing ordinary-input joins.
5. Check local rule input-group activation, guards, stages and whole-hit kills.
6. Add method-empty batches and complete-barrier lifecycle handling.
7. Generate and freeze explicit group guides, including ordinary helpers,
   point starts, static carries and writer/RHS publication; keep full-member
   run-2 queries and complete writer discovery.
8. Run core and fixed project gates. Always report the latest reached,
   completed-engine and accepted-forward-report run iterations.

Required controls include no-write read suppression, FLOW-only admission,
late writes/reads, synthetic-identity rejection, the getter trace with a FLOW
write but no group at summary time, old/new supply and subscription orders,
duplicate groups, wrong field/group,
no-seed copy cycles, grounded ordinary writer input, rewriters with g != h,
selected-cleaner repair, RemoveAll, partial AP hits, conditional normal return,
nonreturning checks, ND support, getter/nested-helper group demands, paired
publication and full-member run-2 queries.
No analyzer implementation or new project measurement is part of this task.
