# Run-1 static requests with three group sets

Date: 20 October 2026. Status: selected design; not implemented.

The [final next-iteration proposal](bidirectional-run1-next-iteration-20261020.md)
consolidates this note with the GROUP normalization fast paths. Use that
document as the current implementation scope and plan.

This note records the selected change to the
[current static workflow](bidirectional-run1-static-workflow-20261020.md).
It does not describe the current runtime. The writer-storage investigation
is in the [thingsboard report](../reports/bidirectional-thingsboard-static-writer-census-20261020.md).

## 1. Semantic scope

For a static field `C.f`, answer each group request with the canonical fact:

```text
premise: S.C.f.*, Group(G), E={}
layer:   DEMAND
```

Do not select an EXACT or descendant-shaped answer from each arriving write.
The saved continuation applies this canonical answer to its FLOW relation.
The relation retains the local output position and resume point.

This is a selected change in field geometry. The current service answers
with the intersection of the request region and the supplied region. The
new field answer covers all descendants of `C.f`. It can add run-1 DEMAND
candidates. The full-mark backward run must refine these candidates. Do not
describe this change as storage deduplication alone.

A second selected semantic change is the **DIRTY HACK** in section 7:
remove static enum-constant fields from alias targets in the new core.
This removes alias-derived paths. It is not a storage-only optimization or
a general proof that enum instances are immutable.

This note specifies field-keyed answers. It does not add a class-root or
static-root initial. Keep the existing legal initial grammar. Class-point
rule inputs still need their existing handling; a field answer does not
authorize a new class-root FLOW premise.

Keep these mechanisms unchanged:

- Rule groups and primitive tracking have the selected group policy.
- A request is not a group supply. A conditional summary alone is not an
  actual group supply.
- Ordinary summary application checks its complete premise and caller/ND
  binding before it publishes an actual group write.
- A static read needs a corresponding computed write. A write with FLOW can
  admit a read before any group is present.
- Static current facts do not traverse the remaining method CFG on run-1.
- Nonstatic requests, direct field transfer, and ordinary summary retention
  keep their existing rules. The enum alias-target filter also applies to
  the new core's FULL forms; other FULL-run mechanisms keep their rules.

## 2. Per-field state

```kotlin
class FieldState {
    val req: GroupSet         // requested; no group write yet
    val reach: GroupSet       // group write; no request yet
    val processed: GroupSet   // requested and written

    // Only unanswered continuations. A continuation can wait for several groups.
    val pending: PendingRequests

    // Only FLOW write continuations. No group-valued write rows.
    val flowWriters: FlowWriters

    // Separate from the group sets: FLOW can admit a read with no group supply.
    var hasWrite: Boolean
}
```

`req`, `reach`, and `processed` are pairwise disjoint. They have these meanings:

```text
everRequested = req ∪ processed
everWritten   = reach ∪ processed
pendingGroups = req
```

`processed` does not mean that every FLOW writer has completed its work.
It means that a request and a group write have both occurred for this field.
Each FLOW writer separately admits each requested group at most once.

Here, FLOW writer means a write relation with a FLOW output. It includes
all-static premises. The current `StaticFlowWriter` runtime class covers
only nonstatic-origin FLOW writes. The selected policy must also replace
the eager all-static `StaticWriterRegistration` join. Applying it only to
the small current `flowWriters` map would miss the main population measured
on thingsboard.

## 3. Event handling

All state transitions below run in the static-service owner. Deliver immutable
effects after the transition. A queued answer owns the continuation until the
method owner consumes it. Do not delete a continuation that an effect still
names without transferring that ownership to the effect.

```kotlin
fun request(field: Field, group: Group, continuation: Continuation) {
    val s = fields[field]
    when (group) {
        in s.processed -> answerCanonical(field, group, continuation)
        in s.reach -> {
            s.reach.remove(group)
            s.processed.add(group)
            answerCanonical(field, group, continuation)
            requestExistingFlowWriters(s, group) // first request for this group
        }
        in s.req -> s.pending.add(group, continuation) // merge identical members
        else -> {
            s.req.add(group)
            s.pending.add(group, continuation)
            requestExistingFlowWriters(s, group)
        }
    }
}

fun groupWrite(field: Field, group: Group) {
    val s = fields[field]
    admitWriteAndWakeReads(s)
    when (group) {
        in s.req -> {
            s.req.remove(group)
            s.processed.add(group)
            for (c in s.pending.removeAll(group)) answerCanonical(field, group, c)
        }
        in s.reach, in s.processed -> Unit
        else -> s.reach.add(group)
    }
    // No request broadcast here. A write alone does not request its group.
}

fun flowWrite(field: Field, writer: FlowWriter) {
    val s = fields[field]
    admitWriteAndWakeReads(s)
    if (!s.flowWriters.add(writer)) return
    writer.requestOnce(s.req union s.processed)
}

fun requestExistingFlowWriters(s: FieldState, group: Group) {
    for (w in s.flowWriters) w.requestOnce(group)
}
```

The snippets show one group. Real messages carry merged sets and perform the
same transitions in bulk. `requestOnce` removes already admitted memberships
before it constructs an outgoing effect. It does not store a history of
input-region tuples or create one row for each answered read.

For a request that arrives while the group is in `reach`, the answer is
immediate, but existing FLOW writers still receive the first request. This
produces their group demand guidance. Do not skip this step because another
writer has already supplied the group.

## 4. Retention and release

The shared service retains:

```text
field -> (req, reach, processed, pending continuations, FLOW writer handles,
          write presence / waiting reads)
```

The method owner retains ordinary computed summaries. The service does not
copy those summaries or retain group-valued writer registrations after it
records their group membership.

After an answer, remove the answered membership from `pending`. If no groups
remain for that continuation, transfer its resume payload to the queued
answer and release the pending service and actor directory entries. A later
request for a written group gets the canonical answer directly. It does not
need the old request row.

A FLOW writer remains available for future requested groups. Merge its
requested groups. Keep the original complete premise and ordinary transfer
semantics. Selection of a smaller FLOW-writer key requires the census result
and a proof that the key retains the required relation; this note does not
silently replace an ordinary transfer with a destination-only key.

## 5. Trace

```java
void set(Object p) { C.f = p; }
void consume() { sink(C.f); } // requests G
```

```text
set produces p/FLOW -> C.f/FLOW:
    save FLOW writer w; no group request exists; ask nothing

another actual source writes C.f/G:
    reach={G}; req={}; processed={}; do not ask w

consume requests G:
    reach={}; req={}; processed={G}
    answer consume with C.f.*/G, DEMAND
    ask w for p/G through the ordinary request path

another FLOW writer v arrives:
    ask v for G, because G is in processed

another read requests G:
    answer directly; do not retain an answered waiter
    do not ask w or v again
```

If the request arrives before the group write, it enters `req`. The write
then moves it to `processed` and drains the pending answers. The same FLOW
writer/group pair is not requested again.

## 6. Required invariants

The full spec update must retain constructive proofs for these obligations:

1. **Disjoint state.** Each event preserves the three set meanings above.
   A first request moves `reach -> processed`; a first write moves
   `req -> processed`. No other event adds a second membership.
2. **Answer origin.** An answer requires membership in `reach ∪ processed`.
   These sets grow only from actual group writes with the ordinary binding.
   Requests and an empty static copy cycle cannot create a group.
3. **Writer/request coverage.** For each FLOW writer and each group in
   `req ∪ processed`, the pair is admitted once. A new writer joins the
   existing set; the first request joins existing writers. A write without
   a request creates no pair. The two event orders therefore agree.
4. **Late answers.** Each waiting group that is later written is answered.
   A later request for a written group is answered without a retained waiter.
5. **Canonical cover.** With the same enum alias-target filter on both sides,
   each former field-region answer is covered by
   `C.f.*/G, DEMAND`. Prove that continuation composition and the FULL-run
   demand matcher retain this cover. Do not assume equality with the former
   field-sensitive answer set.
6. **Demand coverage.** For the algorithm with enum alias targets excluded,
   the union of ordinary summary guidance, static read
   guidance, and each requested FLOW-writer/group derivation must still guide
   run-2. In particular, guidance cannot depend on the rejected broadcast of
   an unrequested group from `reach`.
7. **Safe release.** Pending removal transfers a valid resume payload to the
   queued effect. It does not leave an ID that points to a removed actor row.

These are obligations for the next complete spec/model revision. This design
note and the heap census do not claim that those formal proofs have been run.

## 7. DIRTY HACK: exclude enum constants from alias targets

This hack is selected for the next implementation. The implementation must
keep a `DIRTY HACK` comment next to the filter. Do not remove that label after
the performance run or describe the filter as an immutability proof.

### Predicate and scope

Reject an alias target when its base is STATIC and its first field is an
enum constant. Use the field's `isEnum` property, which tests `ACC_ENUM`,
together with `isStatic`. Do not use only the declaring class's `isEnum`.
An ordinary static field declared by an enum class is not an enum constant.

```kotlin
import org.opentaint.ir.api.jvm.ext.isEnum

class EnumAliasTargets(private val cp: JIRClasspath) {
    fun reject(base: AccessPathBase, path: List<Accessor>): Boolean {
        if (base != AccessPathBase.ClassStatic) return false
        if (path.firstOrNull() !is ClassStaticAccessor) return false
        val field = path.getOrNull(1) as? FieldAccessor ?: return false
        val owner = cp.findClassOrNull(field.className) ?: return false
        val resolved = owner.declaredFields.firstOrNull {
            it.name == field.fieldName && it.type.typeName == field.fieldType
        } ?: return false

        // DIRTY HACK: Suppress enum-constant static alias targets to stop
        // the EntityType FLOW matrix. This is not an immutability proof.
        return resolved.isStatic && resolved.isEnum
    }
}
```

The snippets use the existing JVM field and AP APIs. Supply one classifier
from the new JVM interpreter, using its existing classpath. A missing class
or field keeps the alias. The helper does not load or specialize taint rules.

Inspect the first field below the static class accessor. This also rejects
an alias rooted at `EntityType.TENANT` with additional member accessors.
An anonymous constant-specific subclass does not change which field declares
the enum constant: resolve the field's declaring owner.

Apply the filter to alias **targets** in the new core, in every iteration.
Do not change the shared alias-analysis computation or the legacy core.
Keep explicit static reads, actual static assignments, and explicit rule
positions. This is not a ban on facts whose base is an enum constant.

### Wire both target-producing adapters

In `JIRMethodForms`, filter statement alias targets before conversion to
accessor IDs:

```kotlin
entry.aliasAnalysis?.forEachAliasPathAtStatement(at, base) { target, path ->
    if (!interp.enumAliasTargets.reject(target, path)) {
        add(target to path.map(interp::idx))
    }
}
```

In `JIRCallPlanBuilder.aliases`, filter the result of `persistedAliases`,
including an injected provider. Do this before creation of the transfer or
its `StaticOperation.Write` descriptor:

```kotlin
for (alias in persistedAliases(local, s)) {
    val path = alias.accessors.map { it.apAccessor() }
    if (enumAliasTargets.reject(alias.base, path)) continue
    if (alias.base == local && path.isEmpty()) continue

    val transfer = starEdge(e.edge.from.base, emptyList(), alias.base,
        path.map { manager.accessors.index(it) })
    builder.edge(transfer)
    if (alias.base == AccessPathBase.ClassStatic) {
        builder.staticOperation(StaticOperation.Write(OperationId(formKey),
            Pattern(transfer.to, ExclusionSet.Empty), transfer))
    }
}
```

Pass the same classifier from `JIRMethodForms` to the call-plan builder.
Apply it when forms are built, for both GROUP and FULL views. The reversed
forms then contain the same selected target set. Do not filter only the
global writer store: that would leave the unwanted alias edges and their
method-summary work in place.

### Current versus new

```java
return Set.of(EntityType.TB_RESOURCE);
```

```text
current:
    admitted static read -> argument 0/FLOW, DEMAND
    argument preservation -> static alias copy-back
    -> conditional TB_RESOURCE/FLOW -> TB_RESOURCE.[any]/FLOW summary
    -> writer registration and caller re-export

new:
    static read and ordinary Set.of transfer remain
    static enum alias target is removed from the lookup result
    -> no transfer or writer descriptor for that alias copy-back
```

The heap also contains 1,703 operation/slot groups with all 36 `EntityType`
constants as static write targets. These groups are qualified by method key,
statement, stage, source slot, and action. The filter removes these enum
alias destinations before they can create the corresponding writer matrix.
It does not promise to remove enum effects produced by other mechanisms.

### Semantic limit and validation

An enum can have mutable state. Removing its static alias targets can lose
a real alias-mediated flow, for example:

```java
enum E { A; Object value; }
E x = E.A;
x.value = source();
sink(E.A.value);
```

This is an accepted limitation of the selected dirty hack. Later refinement
does not guarantee recovery of a path removed from the guiding analysis.
Do not claim unrestricted no-missed-summary equivalence with the unfiltered
analyzer. The request-state proofs in section 6 concern the algorithm with
this same filter. The separate filter proof must establish only its exact
selection contract, not general enum immutability.

Implement the predicate and adapter tests first. Check these cases:

| Alias target or operation | Required result |
| --- | --- |
| Static `EntityType.TENANT`, including a descendant path | Remove alias target |
| Ordinary static field declared by an enum, without `ACC_ENUM` | Keep |
| Ordinary static field whose value type is an enum | Keep |
| Final static reference to an ordinary mutable object | Keep |
| Local or nonstatic alias target | Keep |
| Field metadata cannot be resolved | Keep |
| Injected persisted-alias provider returns an enum constant | Remove alias target |
| Direct enum static read, initializer assignment, or explicit rule position | Keep its direct operation |

Then run the existing JVM alias/call-transfer checks and the run-1 static
pipeline checks. Test GROUP and FULL form construction. After implementation,
retest thingsboard and conductor with the existing workload settings. Report
the latest reached/completed/accepted iteration, engine time, status, writer
keys, enum matrix rows, and service candidate visits. Keep the original
run-1 OOM capture as the before measurement. No speedup is measured yet.
