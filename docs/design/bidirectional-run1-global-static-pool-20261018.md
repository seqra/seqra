# Run-1 statics: a shared pool and symbolic marks

Date: 18 October 2026. Status: **design proposal; analyzer unchanged**.
The selected [mark specification](../../spec/rule-group-marks.md) supersedes
the mark alternatives in this sketch. Run-1 taint values use `(group, *)`;
unknown-group supply remains FLOW or pool metadata, not an `AnyPossibleMark`
taint value. Selected-mark cleaners retain group values. RemoveAllMarks can
still kill under its ordinary local geometry. Run-2 is BACKWARD/full/limit 1.
The pool and its writer-discovery adapter remain a separate design proposal.
The user selected a run-1 over-approximation that can ignore static call
order and caller context. Later analysis must recover concrete demands and
valid support. This changes the analysis concept, not just its transport.

Current measured runtime: `63d5481e4e4b69ecf042c1149bb574ad40fd1624`.
Latest reached / completed engine / accepted forward report: OWASP
**run-7 / run-6 / run-5**; conductor **run-1 / none / none**;
ThingsBoard **run-1 / none / none**; Stirling-PDF **run-2 / run-1 / run-1**.
Conductor ends with run-1 OOM. None of the projects meets the 60-second
run-1 engine target. These measurements do not measure this proposal.
See the [current report](../reports/bidirectional-caller-owned-subscriptions-performance-20261018.md).
IR storage and reference/cache-manager changes remain outside scope.

## 1. What must change

The current implementation packs static carriers, but retains their original
premises and full call ports. Effects can unpack those carriers into
per-original statement, observer and summary records. A smaller request
store therefore does not remove the large product of owners and origins.

Conductor's snapshot has 445 interned initial descriptors, but 2,045,888
summary conclusion groups and 1,760,022 observer input entries. The summary
and observer scopes retain 838 MB and 664 MB. These scopes overlap, and their
static fractions are not measured. No byte-saving estimate follows.

| Current | Proposed run-1 behavior | Source of possible saving |
| --- | --- | --- |
| Static inputs cross call ports and method bodies. | Writers publish to a shared field pool; reached consumers read that pool directly. | Removes identity work along the entire call chain. |
| An original concrete mark can become a separate premise. | Symbolic mark payloads stay merged through reads, local propagation, calls and outputs. | Removes the original-concrete-mark dimension from this lane. |
| Static-related summaries retain remote input provenance. | This lane has no remote static original; it produces local DEMAND effects and candidate seeds. | Avoids per-origin statement and summary rows. |
| Static requests bootstrap actual concrete admission. | An abstract value written to a static publishes possible marks directly. | No static mark-request propagation or answer rendezvous. |
| Run-2 consumes ordinary run-1 summary guidance. | A new handoff also opens static writers from exact demands. | Removes the need to retain run-1 static relay provenance. |

This does **not** preserve equality of the old run-1 summary relation. The
required result is coverage of real witnesses and their refinement demands.
Unrelated ordinary analysis keeps its existing semantics.

## 2. Recommended concept

Use one monotone pool for the run. Ignore static writes as kills **in this
pool**. A later write adds possible content; it cannot remove another
execution's contribution. Keep ordinary local nonstatic transfer and its
overwrite rules. Static-dependent results always stay DEMAND.

```text
writer / external static supply
            |
            v
      pool[C.f] -- direct delta --> reached read/check of C.f
                                         |
                                         v
                            local symbolic DEMAND analysis
                                         |
                          +--------------+--------------+
                          |                             |
                     another write                candidate sink
                          |                             |
                       pool[C.g]                 exact run-2 seed
```

There is no static entry fact in each relay, static subscription at each
call, static identity at each statement, or per-field pass summary. A root
`S -> S [DEMAND]` row can remain for an existing compatibility boundary,
but this channel does not subscribe to it or use it to carry values.

## 3. Two separate precision choices

The [rule-owned mark-pair proposal](bidirectional-rule-group-mark-abstraction-20261018.md)
refines the group option below: exact `(top-level rule ID, full mark)`
pairs, with an explicit run-1 projection to a group wildcard. It assigns
each full mark one owner and puts an entire join rule in one group. Internal
GeneratedMark join namespaces remain in the full mark. The general
overlapping-group option below is not required for that refinement.

Ignoring order/context and abstracting marks are independent choices.
Even exact mark sets in the pool already give the selected order/context
over-approximation.

| Mark payload | Meaning | Benefit and cost |
| --- | --- | --- |
| Packed exact set | These marks are possible at this coordinate. | Bulk delivery without losing mark identities; set size can still be large. |
| Group set | Any member of each represented group is possible. | Fewer mark categories; more source/sink alternatives can become possible. |
| `AnyPossibleMark` | Every ordinary taint mark is possible. | One category; can activate many extra alternatives. |

Earlier option: `AnyPossibleMark` for unknown static values, with
known mark sets or groups where they are cheap to keep. This value-domain
option is superseded by the selected mark specification. Also define a
single-token mode to measure the smallest mark-state bound. Exact packed
sets supply a useful precision/cost control. These are proposed comparison
modes, not new project configuration changes or selected runtime patches.

`AnyPossibleMark` is a new **candidate-domain** value. It is neither existing
FLOW `*` nor proof of an actual concrete T. Do not encode it as all concrete
initials. No exact tainted empty static-root initial is introduced.

```kotlin
sealed interface PossibleMarks {
    data object Empty : PossibleMarks
    data class Known(val marks: MarkSet) : PossibleMarks
    data class Groups(val ids: GroupSet) : PossibleMarks
    data object AnyPossibleMark : PossibleMarks
}

// Meaning, not an implementation that enumerates all marks:
possible(Known(ts), t) = t in ts
possible(Groups(gs), t) = gs.any { t in meaning(it) }
possible(AnyPossibleMark, t) = true
```

A group needs an explicit `meaning(group): Set<Mark>`. A rule ID can name
one group, but equal group IDs must never replace equal concrete literals.
Rules can mention overlapping sets; a mark can occur in several rules.
Source-rule and sink-rule IDs need not match. Test membership/intersection
of group meanings, not ID equality. If grouping requires more references
than exact marks, it offers no saving for that cohort. Stable bound rule
occurrences remain separate from group IDs and displayed rule IDs.

Choose a mark abstraction for a run and define its join explicitly. Known
sets use set union; group sets use group union; Any absorbs both. A hybrid
must map known marks into its chosen groups before joining them. Do not
alternate unrelated encodings for equal possible content.

Mark widening does not widen AP geometry. Preserve field/class coordinates
and the applicable EXACT, STAR and ANY distinctions. Existing field-limit
normalization owns spatial approximation. Do not introduce `C.f.[any]` as
an ordinary initial or change a tail merely because the mark is symbolic.

## 4. Stores

```kotlin
class Run1StaticPool {
    val values: Map<StaticCoordinate, PossibleValue>
    val pending: Map<StaticCoordinate, PossibleValue>
    val readers: CoordinateIndex<ConsumerRef>
    val dirtyCoordinates: Set<StaticCoordinate>
}

// Field(C,f) and a direct Class(C) point are distinct coordinates.
// Root/class-wide effects use symbolic scopes matched lazily.
data class ConsumerRef(val method: MethodKey, val point: ProgramPoint,
                       val occurrence: EffectOccurrence)

class StaticWriterInventory { // closed after prescan
    val writers: CoordinateIndex<WriterSite>
    val broadWriters: ScopeIndex<WriterSite>
}

class StaticCandidateSeeds {
    val obligations: Set<CandidateKey> // real occurrence + concrete condition
}
```

The consumer inventory includes code reads, source/sink conditions,
cleaner read/write actions and modeled/alias effects. An occurrence runs
only after its local control point is reached. Registration does not make
the consumer method reachable.

The writer inventory must cover code stores, static copies, source/rule/model
outputs, alias writes and unknown/native effects. It contains possible
writers even if run-1 did not produce concrete T there. External or initial
static suppliers are explicit boundary producers. A static with no known
program writer must not disappear if a boundary can supply it.

Do not build a transitive method-by-field-by-mark table. Do not store a
delivered mark set for every producer/reader pair. Readers retain a version
cursor or current-pool reference. Release one-shot replay snapshots after
delivery; keeping old prefix snapshots can pin a reader-by-mark product.

## 5. Forward pipeline and barriers

```kotlin
fun onStaticWrite(site: WriterSite, input: Value) {
    val possible = when {
        input.isProvedUntainted -> Empty
        input.hasCompleteMayAbstraction -> abstractWholeValue(input.possibleMarks)
        input.isAbstractFlow || input.hasUnknownRemainder -> AnyPossibleMark
        input.allMarksKnown -> abstractKnownMarks(input.marks)
        else -> conservativePossibleValue(site, input)
    }
    pool.offer(site.coordinate, preserveGeometry(input, possible))
}

fun offer(c: StaticCoordinate, value: PossibleValue) {
    val before = join(values[c], pending[c])
    val next = join(before, value)
    if (next == before) return
    pending[c] = join(pending[c], value)
    dirtyCoordinates.add(c)
    schedulePoolDrainOnce()
}

fun drainPool() {
    val batch = detachPending()
    for ((coordinate, offered) in batch) {
        // Commit and capture recipients in the same serialized step.
        val delivery = pool.commitAndSnapshotReaders(coordinate, offered)
        for (reader in delivery.recipients)
            postBulk(reader, delivery.growth)
    }
}

fun onConsumerReached(consumer: ConsumerRef) {
    // Idempotent registration; null means already registered.
    val snapshot = pool.registerOnceAndReadCommitted(consumer) ?: return
    postBulk(consumer, snapshot)
    // Pending values arrive from a subsequent drain.
}
```

Known marks must describe the whole value before using the Known-only
branch. A known T plus an unknown remainder is not just Known(T).
`abstractWholeValue` joins all components under the chosen mark domain.

Every reached possible-value write must invoke publication, even if no
ordinary concrete/FLOW RHS event arrived. In that case use an unknown RHS
abstraction and publish Any, unless the store is proved untainted. The
closed inventory alone does not publish anything. This control-reached
write rule closes the general bootstrap, beyond the example in section 8.

Use the existing scheduler's work accounting. One pool-owner work item can
drain a batch at an owner-turn or method-empty barrier. Final quiescence
includes pending pool updates and reader deliveries. No graph-change replay
and no global-empty-only policy are needed.

Registration and publication must cover both arrival orders. Post callbacks
outside the pool's serialization boundary. A drain publishes a fresh atom
before notifying consumers. A newly registered consumer reads old atoms;
future atoms arrive as deltas. No per-producer replay wave is necessary.

`joinAndGetGrowth` sends new set members for ordinary set growth. A transition
from Known/Groups to Any sends one Any event; it need not represent
`Any minus previouslyKnown` as a new mark-exclusion set. Local joins absorb
that event and replace subsumed payloads. Never retain a log of every old
immutable version merely to support a reader cursor.

An unknown write publishes a symbolic broad scope when its coordinate is
unknown. Match that scope against actual registered consumers; do not eagerly
expand it into all fields and all marks.

## 6. Keep symbolic local work symbolic

Direct delivery alone is insufficient. The existing per-T fallback would
recreate the measured storage product after the first read.

```kotlin
data class MayFact(val ap: Geometry, val marks: PossibleMarks)
// Every MayFact and its effects are DEMAND.

localState[method, point, apHeader].join(mayFact.marks)
localWork.offerFreshBulkDelta(method, point, mayFact)
```

Join into accumulated local state before evaluating conditions and ND.
Packets containing T and then U can make `Contains(T) AND Contains(U)`
possible even when neither packet alone does. Do not evaluate only the
fresh packet or drop earlier candidate alternatives on later growth.

This lane has no remote static-field/mark original in its storage key.
Read-derived local values still follow CFG edges and actual local effects.
Calls transport symbolic argument/return values in bulk. Their input state
is merged by local slot, rather than by remote static source and concrete T.
For this lane, results may be shared across callers, as the user selected.
Ordinary independent inputs retain their existing exact relation.

An ambient-static return effect can be stored once per method/output shape
and delivered to reached call continuations. It is a DEMAND effect, not an
unconditional concrete ZERO source. Productive return/local propagation
can still cross a call chain; pure static availability does not.

Such a return requires a reached normal exit in this local run-1 analysis
or an ordinary applicable return model. Pool availability alone does not
make a callee return. A check inside a nonreturning callee remains a local
candidate; it must not silently enable its caller's later statements.

Static writes return to the pool. Static identity at a return needs no
per-field STAR-to-ANY summary: this lane never abstracted a caller's static
input into that per-origin initial. The candidate-domain coverage proof
replaces the old per-summary equality requirement.

Never put MayFacts in the unresolved observer's full concrete-input history.
If enabled diagnostics need information, retain the actual diagnostic key
and classify symbolic observations separately from actual concrete evidence.

## 7. Conditions, sources, cleaners and ND

```kotlin
fun possibleCondition(bound: BoundNNF, state: MayState): CandidateResult {
    val concreteNNF = signedNormalizeUsingConcreteLiteralIdentity(bound)
    // Preserve current eligible residual-negative projection.
    return evaluatePossibility(concreteNNF, state)
}
```

`ContainsMark(x,T)` is possible when the symbolic value can include T.
It does not hold as concrete evidence. Absence from a pooled union cannot
prove a negative literal. Keep exact signed folding before mark grouping:
`P(T) AND NOT P(T)` is false, while `P(T) AND NOT P(U)` for different T/U
must not become that contradiction because T/U share a group.

A possible source condition produces a DEMAND output and preserves its
original bound condition as a later obligation. This includes conditional
ZERO-to-value generation. Changing the output's base or apparent origin
must not erase the symbolic dependency.

Do not delete content from the global pool on a local cleaner. A selected-
mark cleaner retains each local group value and records the group-demand
interest, including when the catalog has only one member in that group.
A cleaner for T alone cannot kill `AnyPossibleMark`. A proved whole-value
kill can kill the local value. Exact known sets can use ordinary subtraction.
Tracking `Top minus X` is optional; if added, its union joins exclusion sets
by intersection. It is not needed for the first coarse concept.

Pooled marks from incompatible callers or branches may make an ND condition
possible. Record a DEMAND candidate with its exact occurrence and full
condition. They cannot confirm a compatible ND witness. Do not enumerate
Cartesian tuples of grouped marks just to produce that candidate.

## 8. Bootstrap example

```java
void A(Object p) {
    C.f = p;
    B();
}
void B() { check(ContainsMark(C.f, T)); }
```

```text
F1 abstract p reaches store -> pool[f] = AnyPossibleMark
F1 reached check in B       -> DEMAND candidate for the original check/T
B2 demand f/T              -> open every possible writer, including A's store
B2 pre-store demand        -> demand p/T and discover ordinary source permissions
F3 / strong validation     -> check actual p/T, store, call and check support
```

No actual p/T and no static request is required to get past the F1 bootstrap.
No summary return from B is required to retain its local check obligation.

`B(); C.f=p` deliberately gives the same pooled candidate. Later strong
analysis must distinguish its order from the example above. The same applies
to mutually exclusive callers and a clean between write and read.

## 9. Run-2 is part of this design

The current run-2 adapter cannot simply be assumed to resolve a new symbol.
Remove the symbolic domain at the boundary; retain concrete query identities.

```kotlin
fun handoff(candidate: CandidateKey) {
    keepOriginalSinkAndSourceConditions(candidate)
    seedOrdinaryConcreteBackwardQueries(candidate)
}

// Queries from this lane must not require a matching F1 guide/summary.
fun onCandidateBackwardQuery(point: ProgramPoint, query: ConcreteQuery) {
    visitOrdinaryBackwardPredecessorsAndCallBindings(point, query)
    // Newly encountered static reads invoke onBackwardStaticDemand.
}

fun onBackwardStaticDemand(read: ReadSite, query: ConcreteQuery) {
    for (producer in writerInventory.compatibleWith(read.coordinate, query)) {
        when (producer) {
            is Store -> seedBeforeStore(producer, reverseStore(producer, query))
            is Copy -> for (inputQuery in reverseCopy(producer, query))
                openStaticDemand(producer.sourceSite, inputQuery)
            is Source -> seedSourceWithOriginalCondition(producer, query)
            is Boundary -> seedBoundary(producer, query)
            is Unknown -> useConservativeOrdinaryBoundary(producer, query)
        }
    }
    retainReadEndpointAndValidationObligation(read, query)
}
```

This lookup also handles static dependencies first discovered during B2.
Start at the real sink/source occurrence and follow local variables and
calls even when F1 retained no corresponding per-origin summary. For
`x = C.f; relay(x); sink(x)`, the query through `relay` must reach the read
without a matching old static guide. This is an explicit change to the
later demand-admission boundary, not an assumed property of current B2.
Reverse each producer's full AP/mark operation: `C.f = C.g.h` rebases a
demand on f to g.h with the ordinary tail/exclusion semantics. Retain that
write's endpoint obligation; do not jump between coordinates unchanged.

Deduplicate concrete queries to terminate writer/copy cycles. Opening writers
is conservative discovery, not proof of a real path. Ordinary later CFG,
call/return, cleaner, layer and joint-condition validation must connect the
writer to the retained read endpoint under compatible execution support.
Run-2 may collect demands; a following forward run can be needed to reject
the false F1 candidates. Do not promise that one backward pass decides all
feasibility or removes existing alias/AP false positives.

Seed all concrete positive alternatives required by the original NNF, not
one arbitrary representative per group. Preserve negative constraints and
ND correlation for validation. Stored seeds can reference the original NNF
DAG; they need not expand its cubes. Conditional sources and trigger actions
remain conditional in later analysis.

Current ordinary rule semantics project eligible residual negative literals
to true. This proposal does not add a precise negative-condition validator.
Later analysis retains that existing approximation; keeping a condition
does not prove that every negative constraint will be discharged.

Every symbolic-static value, guard and generated effect stays DEMAND and is
excluded from executable NORMAL records. Independent NORMAL summaries may
still be persisted, provided a dependency proof covers mixed ordinary and
symbolic operations. A relative nonstatic callee summary can remain NORMAL
if it is independently valid; applying it to a MayFact remains DEMAND.

Do not confirm a candidate from the pool, its mark groups or its global
writer lookup. An unfinished refinement leaves it unconfirmed.

## 10. Where savings come from

Let h be a transparent call-chain length, m marks, r consuming sites and g
represented groups. For one field supply:

```text
scalar chain carriers:     work/state can include h * m before real consumers
shared pool, exact bulk:   field payload m + direct consumer delivery
shared pool, groups:       field payload g + direct consumer delivery
shared pool, single Any:   one field atom + one activation per consumer
```

The single-Any row assumes the pure single-token mode. A hybrid pays for
earlier Known/Groups growth as well as its eventual transition to Any.

For a finite field/group atom universe, fresh deliveries are bounded by
`sum_f consumers(f) * atoms(f)`, plus registration and local analysis.
The carrier bound has no h factor. A new unrelated call binding cannot
restart the field's delivery closure. Broader geometry can add its own
finite atoms and must be included in the bound.

Pool state is `O(field/group atoms + readers + writer sites)`, plus pending
deltas and local symbolic dataflow/candidates. Local state still costs
program points times AP shapes and represented groups. It must not regain
the remote-static-origin times concrete-mark factor. This is the important
memory acceptance check, not just a smaller static request cache.

These bounds do not prove a whole-project speedup. Any/group widening can
activate many more rules and local paths, and B2 can discover many writers.
Measure these costs before selecting a precision mode. There is no general
60-second guarantee for arbitrary projects.

## 11. Proof and implementation sequence

Follow [proof-guide.md](../../spec/proof-guide.md). This document has not
completed these constructive proofs and does not replace the current spec.

1. Define a finite executable concrete witness model with code/rule/model
   static producers, readers, calls, cleaners and signed conditions.
2. Define the new pooled candidate reference. Prove forward witness coverage,
   including abstract-write bootstrap and direct class/broad suppliers.
3. Define mark abstraction with an explicit concretization. Prove transfer
   coverage for known sets, groups and Any; include signed-fold and partial-
   cleaner counterexamples. Prove that symbolic effects cannot enter NORMAL
   records through source guards, ND or local/call transformations.
4. Prove candidate-to-concrete-seed coverage and closed writer discovery.
   Produce executable witnesses for every needed backward seed. State the
   strong later validator's scope; do not assume it rejects all extra paths.
5. Separately prove the batched pool equivalent to the new pooled reference
   and prove the chain/mark cost laws under their finite assumptions.
6. Design the Kotlin boundary only after those models pass. Use small
   differential controls before implementing the domain and handoff adapters.

Required controls include write-before/read-before order; unreachable readers;
nonreturning consumers; initial external statics; static copies/cycles; two
callers with incompatible inputs; Any versus exact bulk; same-group distinct
marks; contradictory NNF; partial cleaners; conditional source ZERO-GEN;
ND; unknown/alias effects; late registration and late pool growth.

Measure pool atoms, direct deliveries, symbolic local facts, source/sink
candidate growth, observer records, scalar fallback, summary ownership and
backward writer expansion. Reject a design that merely moves the old
field-by-origin-by-mark relation into another store. After a selected runtime
change, use only the fixed project settings and report the latest reached,
completed and accepted run iterations.
