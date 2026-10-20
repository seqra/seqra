# Next iteration: enum alias filter, static request state, and GROUP identities

Date: 20 October 2026. Status: final selected proposal; not implemented.
Implementation baseline: `029ee41fd6de17388c2f61d0f900cd00f6bd33f8`.
Follow [the proof guide](../../spec/proof-guide.md).

This is the selected next-iteration proposal. It replaces the selection in
the [earlier optimization list](bidirectional-run1-redundant-work-next-iteration-20261020.md)
and consolidates the [three-set and enum-filter note](bidirectional-run1-three-set-static-requests-20261020.md).
Only these three changes are selected, in expected run-1 memory impact order:

1. Exclude enum-constant static alias targets, with a **DIRTY HACK** label.
2. Use three disjoint group sets for static requests and release answered requests.
3. Stop normalizing an immutable GROUP value when it cannot change.

The first change targets a measured relation multiplier. The second targets
request retention and eager writer work. The third targets CPU and temporary
allocation. No byte saving or speedup is established before implementation.

## 1. Evidence and semantic scope

The [fresh measurements](../reports/bidirectional-immutable-mark-groups-performance-20261020.md)
and [writer census](../reports/bidirectional-thingsboard-static-writer-census-20261020.md)
are the before evidence. Keep their phase boundaries:

| Project | Latest reached | Latest engine COMPLETE / accepted report | Run-1 engine seconds |
| --- | --- | --- | ---: |
| OWASP | run-1, TIMEOUT | none / none | 966.445 |
| conductor | run-1, TIMEOUT | none / none | 1,054.594 |
| thingsboard | run-1, OOM | none / none | 396.256 |
| Stirling-PDF | run-2, OOM | run-1 / run-1 | 487.083 |

The target is **run-1 engine time at most 60 seconds**. Prescan and the report
barrier have separate measurements. A completed engine does not prove report
acceptance. All four projects currently fail the run-1 target.

Thingsboard retains 3,660,632 actor writer keys. FLOW accounts for 96.3%.
A `36 × 36` relation matrix between `EntityType` fields accounts for
3,057,187 keys across 7,475 analysis owners. Those owners correspond to
4,664 physical method objects under different contexts; 4,525 owners have no
reached local static-read registration. Alias target expansion and caller
re-export multiply the relations. This is not a count of Java assignments.

OWASP run-1 normalization occurs on 71.49% of Java samples and 73.09% of
estimated allocation weight, both inclusive. These shares overlap other
families. They do not measure the fraction removed by the GROUP fast paths.

### Selected semantic changes

| Change | Current | New | Required proof scope |
| --- | --- | --- | --- |
| Enum alias filter | Static enum aliases can produce copy-back edges and writer descriptors | Remove those alias targets before edge creation | Prove the exact filter contract; do not claim unrestricted soundness |
| Canonical static group answer | Answer with each matched supplied region and its selected initial shape | Answer with the field's `C.f.* / Group(G), DEMAND` descriptor | Prove candidate inclusion and run-2 demand coverage, not equal run-1 precision |
| Static request scheduling/storage | Retain answered interests and eagerly join all-static writers on pool growth | Retain only pending memberships; ask FLOW writers only for requested groups | Prove the requested closure and both event orders |
| GROUP normalization identities | Rebuild or normalize values already in the required form | Return the same value when the typed same-catalog invariant permits it | Prove equal results, validation, and interning behavior |

The enum filter is an explicit semantic restriction. An enum can have mutable
state, so this hack can remove a real alias-mediated flow. Later refinement
does not guarantee recovery of a path removed from run-1 guidance. Apply the
same filter to both sides of proofs for the other changes.

Keep direct field transfers, field kills, empty run-1 field exclusions,
layer-only demotion, rule identities and conditions, primitive policy,
ordinary caller/ND bindings, and ordinary summary retention unchanged.
Keep `trackFactsReachAnalysisEnd` effectively empty. Keep the run schedule:
run-1 forward/limit 1, run-2 backward/limit 1, then the existing increasing
limits. ZERO satisfies a static premise only on run-1.

Do not add GroundingSupport, an actual-input field validator, a new mark
representation, a reference-manager rewrite, or an IR storage change.
Do not change the memory-effect guard, delete caller summaries, share all
owner rows by premise, or add a new barrier as part of this iteration.

## 2. DIRTY HACK: exclude enum constants from alias targets

Filter alias **targets** in the new JVM core, for GROUP and FULL forms.
The field must have both `isStatic` and `isEnum`; the latter tests `ACC_ENUM`.
Do not exclude every static field declared by an enum class or every field
whose value type is an enum.

```kotlin
class EnumAliasTargets(private val cp: JIRClasspath) {
    fun reject(base: AccessPathBase, path: List<Accessor>): Boolean {
        if (base != AccessPathBase.ClassStatic) return false
        if (path.firstOrNull() !is ClassStaticAccessor) return false
        val field = path.getOrNull(1) as? FieldAccessor ?: return false
        val owner = cp.findClassOrNull(field.className) ?: return false
        val resolved = owner.declaredFields.firstOrNull {
            it.name == field.fieldName && it.type.typeName == field.fieldType
        } ?: return false

        // DIRTY HACK: Drop enum-constant static alias targets to stop the
        // EntityType FLOW matrix. This is not an immutability proof.
        return resolved.isStatic && resolved.isEnum
    }
}
```

Use the field's declaring owner, including for constant-specific subclasses.
Reject paths rooted at the constant even when the path has more accessors.
A failed metadata lookup keeps the alias. Use the existing classpath; do not
load specialized rules to classify a field.

Supply the classifier from `JIRInterpreter`. In `JIRMethodForms`, filter
statement alias results before conversion to accessor IDs:

```kotlin
entry.aliasAnalysis?.forEachAliasPathAtStatement(at, base) { target, path ->
    if (!interp.enumAliasTargets.reject(target, path)) {
        add(target to path.map(interp::idx))
    }
}
```

Pass the same classifier to `JIRCallPlanBuilder`. Filter inside `aliases`,
after every provider lookup, including an injected provider:

```kotlin
for (alias in persistedAliases(local, s)) {
    val path = alias.accessors.map { it.apAccessor() }
    if (enumAliasTargets.reject(alias.base, path)) continue
    if (alias.base == local && path.isEmpty()) continue
    // Build the existing alias edge and its write descriptor here.
}
```

Keep direct static reads, initializer assignments, and explicit rule positions.
Keep the shared alias-analysis computation and legacy core unchanged. Filtering
only the writer store is insufficient: it leaves the alias edges and their
method-summary work in place. Keep the `DIRTY HACK` source comment after
implementation and in the implementation report.

```java
return Set.of(EntityType.TB_RESOURCE);
```

```text
current: admitted read -> argument preservation -> static alias copy-back
         -> TB_RESOURCE/FLOW -> TB_RESOURCE.[any]/FLOW summary and writer
new:     admitted read and Set.of transfers remain
         -> enum static alias target is absent
         -> no edge or writer for that copy-back
```

This removes one observed producer of the enum matrix. It does not authorize
deleting every summary that mentions an enum or promise that all remaining
writer multiplication disappears.

## 3. Static requests: three sets and pending continuations

### 3.1 State and ownership

The normal field key is the class accessor plus field accessor. It has no
mark, tail exclusion, caller, or method owner. Group membership uses `GroupSet`.

```kotlin
data class StaticFieldKey(val klass: AccessorIdx, val field: AccessorIdx)

class FieldState {
    var req = GroupSet.EMPTY        // requested, not written
    var reach = GroupSet.EMPTY      // written, not requested
    var processed = GroupSet.EMPTY  // requested and written
    val pending = PendingRequests() // field/group -> merged pending refs
    val writers = FlowWriters()     // writer ref -> admitted group set
}
```

The container names below are protocol pseudocode, not existing Kotlin APIs.

```text
req ∩ reach = req ∩ processed = reach ∩ processed = {}
everRequested = req ∪ processed
everWritten   = reach ∪ processed
```

`processed` does not mean that every writer finished. Each FLOW writer has
its own set of admitted groups. It admits each writer/group pair once.

| Store | Owner | Contents and lifetime |
| --- | --- | --- |
| Field states | Run-scoped static service | Three group sets, pending references, FLOW writer references; clear after workers join |
| Pending mark requests | Static service | Only unanswered memberships; merge groups for the same complete continuation |
| Resume payloads | Method actor | Site, original initial, current image/layer and call state; retain through reply delivery, then release |
| FLOW writer selectors | Method actor | Current ordinary-summary selector, with complete premise and output identity; retain for future requested groups |
| Conditional TAINT joins | Method actor | A pending complete fixed-group singleton/ND binding; release after its required inputs are answered and checked |
| Ordinary summaries and demand guidance | Existing stores | Keep their present ownership and lifetime |
| FLOW read admission and write presence | Existing static boundary | Keep its spatial matching and late-read behavior; this is separate from answered mark requests |

The service sends immutable owner/ID effects. It does not hold actor callbacks,
mutable actor stores, full call plans, or cached forms. Keep the current
method-queue-empty commit barrier and replace the temporary batch after commit.
Do not add pending-memory caps or another barrier policy.

### 3.2 Atomic transitions

Run these transitions under the existing service lock. Queue effects after
membership admission and deliver them outside the lock. Messages carry merged
group sets; the snippets show one group to make the transitions clear.

```kotlin
fun request(s: FieldState, g: GroupId, ref: PendingRef) {
    when {
        g in s.processed -> answers.add(ref, g)
        g in s.reach -> {
            s.reach -= GroupSet.of(g)
            s.processed += g
            answers.add(ref, g)
            askExistingWriters(s, GroupSet.of(g)) // first actual request
        }
        g in s.req -> s.pending.add(ref, g)
        else -> {
            s.req += g
            s.pending.add(ref, g)
            askExistingWriters(s, GroupSet.of(g))
        }
    }
}

fun groupWrite(s: FieldState, g: GroupId) {
    when {
        g in s.req -> {
            s.req -= GroupSet.of(g)
            s.processed += g
            answers.addAll(s.pending.removeGroup(g), g)
        }
        g in s.reach || g in s.processed -> Unit
        else -> s.reach += g
    }
    // A newly written, unrequested group does not wake existing FLOW writers.
}

fun flowWrite(s: FieldState, w: WriterRef) {
    val row = s.writers.addIfAbsent(w) ?: return
    askOnce(row, s.req + s.processed)
}

fun askOnce(w: FlowWriterRow, offered: GroupSet) {
    val fresh = offered - w.admitted
    if (fresh.isEmpty) return
    w.admitted += fresh
    writerEffects.add(w.ref.owner, w.ref.id, fresh)
}
```

`WriterRef` is immutable owner/ID data. `FlowWriterRow` belongs to the service
and contains that reference plus its admitted `GroupSet`; it has no actor
store or delivered-input map.

Both kinds of write still go through the existing write-presence/read-admission
boundary. A FLOW write can admit a read without supplying any group. A
conditional summary is not a group supply until its complete ordinary binding
matches. Requests and an empty copy cycle cannot create a group.

A request for a group already in `reach` must ask existing writers as it moves
to `processed`. An arriving FLOW writer asks `req + processed`, never `reach`.
Repeated writes do not repeat either broadcast.

### 3.3 Canonical answers and safe release

Each field/group answer has one canonical initial descriptor:

```kotlin
fun canonicalInitial(f: StaticFieldKey, g: GroupId): InitialAp =
    ap.initial(Pattern(PathFact(STATIC, listOf(f.klass, f.field), Tail.STAR,
        MarkSlot.Concrete(ap.marks.group(g))), ExclusionSet.Empty))
```

The answer is `C.f.* / Group(G), DEMAND`, independent of the arriving write's
EXACT/descendant geometry. Use the existing `startFact`/factory normalization
for its current fact. That current marked fact can have an ANY tail. The
initial remains STAR; do not create an ANY initial or a new marked STAR
current-fact representation. No FULL-member enumeration is needed.

Merge reply groups per destination. Retain the exact operation/call resume
state. The method actor applies the existing point repair or call-summary
matcher. The global service does not apply transfer functions.

```kotlin
fun onAnswer(e: Answer) {
    val row = pendingPoints.getValue(e.id)
    val payload = row.payload
    row.waiting -= e.groups
    if (row.waiting.isEmpty) {
        pointKeys.remove(row.key)
        pendingPoints.remove(e.id)
    }
    resumeAtSavedInput(payload, e.field, e.groups)
}
```

The actor's `waiting` set includes replies in flight. Admit a new membership
locally before constructing its temporary service message. A second request
for a group already waiting does not create another membership. A new group
can join that row while an old reply is queued. Determine row removal from
the actor's current waiting set, not a stale service `done` flag. IDs are not
reused. Release call-interest rows in the same way after their pending groups
are consumed; preserve the original caller/member binding.

This replaces the service's permanent answered-interest history. It does not
create an additional per-point answered cache. The existing edge/summary
deduplication still controls repeated semantic propagation.

### 3.4 Writer handling, including all-static premises

The current `StaticFlowWriter` class covers only nonstatic-origin FLOW
summaries. The redesign must also replace the eager all-static FLOW path in
`StaticWriterRegistration`; that path contains most measured writer keys.

The existing K6 invariant gives a useful split:

```text
FLOW conclusion -> one InitialAp with the abstract mark
ND premise      -> all members have concrete marks; conclusion is TAINT
```

For each requested FLOW writer/group pair, run the following actor logic:

```text
ordinary nonstatic initial I/FLOW -> static output:
    submit the existing ordinary request I/Group(G)
    preserve its SummaryStorage propagation and actual caller binding

static initial C.x.*/FLOW -> static output:
    request C.x/G from the field service, with a writer-input continuation
    on answer, borrow the current ordinary summary selected by this writer
    apply the canonical input with ordinary satisfying/applySummary
    publish only the actual resulting group writes
```

Keep `currentValues` replacement checks. An obsolete selector can stutter.
Apply the saved summary's actual mark exclusions; do not clear them to force
an output group. Existing requests emitted before an exclusion and ordinary
point repair remain responsible for the complementary branch. Request routing
itself must not subtract mx or field exclusions from the requested groups.
Ordinary request propagation still starts from a FLOW fact and skips concrete
marked facts. The fixed-group input joins below are service subscriptions,
not request propagation along concrete facts.

For a conditional all-static TAINT summary, its required groups are already
fixed in its complete premise. Use pending mark-request continuations for
those inputs, not a permanent non-FLOW writer row:

```text
retain ordinary conditional summary
    -> announce its computed write region through existing read admission
    -> request each required static field/group at its member index
    -> collect canonical answers in the existing complete-premise matcher
    -> recheck the current summary and apply the complete binding
    -> record resulting actual group membership in the pool
    -> release the pending join and answered continuations
```

One member must never fire an ND conclusion. Mixed nonstatic/static premises
keep the ordinary caller/ND path. ZERO-rooted group writes can publish their
membership immediately. Nonstatic conditional TAINT summaries remain ordinary
summaries until their actual call binding supplies them. After a group write
is admitted, keep its pool membership, not a group-valued writer descriptor.
Announcing conditional write presence does not put its output group in
`reach` or `processed`; that requires the complete matched derivation.

Remove the ordinary-field `InterestRow.delivered` region history and the
all-static writer's `delivered` input-tuple history. Replace the eager
`pool growth -> dirty staticDependents -> candidateBindings` writer loop with
the request-driven paths above. Keep one admitted group set per FLOW writer.
Do not introduce owner-free summary storage or delete caller re-exports.

### 3.5 Existing spatial boundary and run-2

The three sets are field-keyed; they do not authorize a new static AP grammar.
Keep the existing AP match/project boundary for broad root/class data and
class-point rule inputs. An exact class point does not supply its fields.
Keep normalized broad memberships needed for late consumers, and join them
with a field when that field is observed. Do not expand a broad region into
every declared field or store duplicate normal field rows in a second pool.
The existing matching rule decides which field receives a membership; its
answer then uses the selected canonical field descriptor. Bare class-point
requests retain their legal class descriptor and pending-only lifetime.

Keep FLOW read-admission geometry unchanged. Do not infer a broader FLOW
read image merely from the new canonical group answer. These are separate
operations.

Keep ordinary summary/demand publication and `DemandStore.includeStaticGroups`
with its existing `implicitStaticPair` guidance. Do not enumerate groups,
add synthetic executable `S.*` summaries, or remove intermediate nonstatic
marked demands. Replies must drain before run-1 handoff. Run-2 then follows
the existing FULL-mark demand mechanism and ordinary static premise matching.

An unanswered pending request is not runnable work. It must not keep a work
reservation, reschedule itself, or make the engine wait for a group that is
never written. Existing queue accounting covers new deltas and effects in
flight. The engine can finish at quiescence with unanswered requests; release
them with the run-scoped service after workers join.

The proof and tests must include a setter, nested wrappers, a getter, and a
dead local followed by a sink. They must establish the intermediate group
demands, not only the final pool membership.

### 3.6 Example

```text
FLOW writer p -> C.f arrives: store it; ask nothing if no group was requested.
C.f/G arrives first:         reach={G}; do not ask that writer.
Consumer requests G:        move reach -> processed; answer immediately;
                            ask the existing FLOW writer for p/G.
New FLOW writer arrives:    ask it for G because G is in processed.
Another consumer requests G: answer immediately; retain no answered waiter.
```

If the request arrives before the group write, it stays in `req`. The first
write moves it to `processed` and drains its pending answers.

## 4. Stop normalizing unchanged immutable GROUP values

This part preserves the existing mark/AP semantics. It changes only typed
projection and normalization identities in `RuleMarkCatalog.kt`,
`MarkExclusions.kt`, and `ApManager.kt`. Keep the current MarkTable monitor
and interner ownership; do not introduce a new cache of Facts or mark members.

### 4.1 Runtime atom and set projection

Read the immutable atom key once. A GROUP atom in GROUP mode is already the
required atom. A FULL atom still needs its owner's group atom.

```kotlin
fun runtimeAtom(atom: TaintMark, mode: MarkMode): TaintMark {
    if (atom == TaintMark.ZERO) return atom
    val k = key(atom)
    if (mode == MarkMode.FULL) {
        require(k !is MarkAtomKey.Group) {
            "An executable full fact cannot contain a group selector"
        }
        return atom
    }
    return when (k) {
        is MarkAtomKey.Group -> atom
        is MarkAtomKey.Full -> group(k.mark.group)
        MarkAtomKey.ControlZero -> atom
    }
}
```

Map sets lazily. Do not allocate a replacement array until an atom changes.
Still intern an unchanged public input set, which need not be canonical.

```kotlin
fun runtimeMarks(set: MarkSet, mode: ApMode): MarkSet {
    if (mode.markMode == MarkMode.FULL) return set
    var mapped: IntArray? = null
    for (i in set.data.indices) {
        val old = set.data[i]
        val id = marks.runtimeAtom(TaintMark(old), mode.markMode).id
        if (mapped == null && id != old) mapped = set.data.copyOf()
        mapped?.let { it[i] = id }
    }
    return intern(mapped?.let(::MarkSet) ?: set)
}
```

The existing constructor still sorts/deduplicates mapped results. Do not skip
an atom because an earlier atom was GROUP or because the fact is DEMAND.

### 4.2 Exclusion normalization

All attached `MarkExclusions` values come from private normalizing factories.
Their catalog keys and group ownership cannot change after publication.
Validate catalog ownership before the identity branch:

```kotlin
fun withCatalog(table: MarkTable): MarkExclusions {
    require(catalog == null || catalog === table) {
        "Mixed mark catalogs in FLOW exclusions"
    }
    if (catalog === table) return this
    return normalized(atoms, table)
}

fun projected(table: MarkTable, mode: MarkMode): MarkExclusions {
    if (mode == MarkMode.FULL) return withCatalog(table)
    if (catalog === table && atoms.data.all {
            table.key(TaintMark(it)) !is MarkAtomKey.Full
        }) return this
    return normalized(MarkSet(atoms.data.map {
        table.runtimeAtom(TaintMark(it), MarkMode.GROUP).id
    }.toIntArray()), table)
}
```

The fallback is the current projection/normalization body. Keep its behavior
for a null catalog, mixed FULL/GROUP/ZERO values, and a foreign catalog in GROUP projection.
Do not add a rejection to that foreign GROUP fallback. FULL projection still
uses `withCatalog` and its existing ownership validation.

Keep symbolic group exclusion of FULL members first used later. A finite
FULL set must not become a whole-group exclusion. Neither optimization
enumerates known members or changes primitive handling.

### 4.3 Output boundaries remain active

Keep TAINT `mapLeaves` traversal and all current output adapters and factories.
`runtimeFact(FULL)` and `runtimeMarks(FULL)` remain identity functions;
`runtimeAtom(FULL)` still rejects GROUP. The fast paths do not skip layer
forcing, tail/E normalization, static/group demotion, cuts, or field cover.
A DEMAND layer is not proof that every atom in a value is GROUP.

```text
same-catalog normalized mx={G}
    current: attach -> normalize -> project GROUP -> rebuild/normalize again
    new:     attach -> identity; typed GROUP projection -> identity

The cleaner still requests G and emits its existing DEMAND repair.
```

## 5. Proof and test gates

First update the selected semantic scope in the spec. Then build constructive
Lean models and executable references, as required by the proof guide. Do not
reuse an unfiltered equivalence claim after adding the dirty hack.

| Model / proof | Required property |
| --- | --- |
| Enum target selection | Reject exactly resolved static enum-constant roots, including descendant paths; preserve other targets and direct operations |
| Three sets versus logical requested/written sets | Pairwise disjointness, exact set meanings, immediate/late answers, and both event orders |
| Requested writer closure | A new writer joins `req ∪ processed`; a first request joins existing writers; a write without a request creates no writer/group pair |
| Origin and complete joins | A group answer has a derived write; no self-seeding cycle; every ND member and required ordinary binding matches |
| Canonical answer cover | With the same enum filter, former field answers are covered by the canonical DEMAND answer, including start-fact normalization |
| Run-2 demand coverage | The request-driven closure and existing guidance retain every required intermediate group demand in the selected approximation |
| Reply ownership | Pending removal and queued delivery cannot lose a continuation, reuse an ID, or remove a row with a new outstanding group |
| Immutable GROUP identities | Same denotation and validation as old projection/normalization, including later first-use FULL members and foreign-catalog behavior |

Prove the work bound only for the specified protocol: each persistent FLOW
writer/requested-group pair is admitted once. A semantic replacement is a new
writer identity. Pending storage contains unanswered memberships plus replies
in flight at their owners. This is not a fixed memory cap or an end-to-end
60-second proof.

For `F` fields, `G` groups, `W` remaining FLOW writers, and `P` pending
continuation/group memberships, group-state storage is `O(FG)`, pending
storage is `O(P)` plus effects in flight, and writer admission state is at
most `O(WG)`. The three sets do not make `W` constant. The enum filter targets
the observed producer of the large writer matrix; the protocol then reduces
work and history retained for the remaining writers. Measure `W` after both
changes before selecting a wider writer-sharing redesign.

Use existing fixtures and add tests at these boundaries:

- Enum constants versus ordinary static fields in enum classes, enum-typed
  fields, final mutable-object references, unresolved metadata, descendant
  paths, injected providers, and direct enum reads/assignments/model actions.
- Request/write/writer arrival permutations, repeated offers, merged groups,
  `reach -> processed`, and a newly reached but unrequested group.
- Both FLOW writer origins; exclusion-triggered requests; obsolete selectors;
  conditional singleton/ND TAINT summaries; mixed caller premises; late broad
  supplies and class-point controls.
- Point and call replies with a second group added while the first is in
  flight, cancellation, and no retained answered membership after delivery.
- Setter/getter/wrapper/dead-local sink paths, selected cleaners, and run-2
  FULL-mark guidance. Keep `RemoveAllMarks` and structural kills unchanged.
- Atom/set/exclusion projection against an independent old reference:
  GROUP/FULL/ZERO mixtures, noncanonical equal sets, both catalog modes,
  delayed first use, packed leaves, factory headers, and concurrent interning.

The enum hack has a documented counterexample with mutable enum state. It
must not be hidden by weakening the reference tests or relabeling missing
flows as a proof of soundness.

## 6. Implementation order and acceptance

1. Update the spec's change ledger and complete the protocol/normalization
   models. Keep the old transfer, call, and demand rules explicit.
2. Implement and test the enum target filter in both new-core adapters. Keep
   the source label. Do not change the shared alias engine.
3. Implement field group state, pending-member admission, actor reply release,
   both FLOW writer paths, and conditional TAINT joins. Remove the superseded
   answered-interest and eager input-tuple histories. Keep existing barriers.
4. Implement the narrow atom/set/exclusion identities. Compare to the old
   reference before relying on work-reduction assertions.
5. Run the relevant core/JVM and static pipeline gates. Run broader required
   repository checks after these pass. No runtime implementation is part of
   this document change.
6. Retest thingsboard and conductor first, then OWASP and Stirling-PDF with
   the existing pinned workloads, rules, toolchain, timeouts, and heap limits.
   Serialize heavy workloads/readers with the existing three locks. Do not
   improve the result by changing the inputs or measurement boundary.

Record these before/after values with the latest reached, completed, and
accepted run iteration in every report:

```text
run-1 engine seconds / outcome / report-barrier seconds
writer keys: FLOW / TAINT, static / nonstatic origin
EntityType matrix rows and affected owner/method counts
req / reach / processed memberships
pending service memberships and actor replies in flight
FLOW writer/group admissions and candidate visits
conditional TAINT joins: pending / completed
normalization attempts / identity hits / changed projections
allocation and retained owners, when a bound profile/capture is available
strict finding comparison and the known enum-hack semantic limitation
```

Use owner-local or diagnostic counters; do not add a new contended hot-path
counter or retain full Fact histories for measurement. Compare completed
phases separately from censored ones. Lower writer counts alone do not prove
faster completion. A run-1 completion over 60 seconds remains a target failure.
Stirling-PDF's run-2 OOM remains a separate unresolved limit unless the new
measurement demonstrates a change. No selected item authorizes backward
publication batching, call-list coalescing, generic owner-free writer sharing,
or a new cache/reference policy.
