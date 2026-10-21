# Reduce retained analyzer work: next iteration

Date: 21 October 2026. Status: **proposal; current measurements closed**.

The runtime is `d7244c08cdaefbb157f42e4d89731d301b97e48f`.
Use the [current performance report](../reports/bidirectional-run1-origins-routing-projection-performance-20261021.md).
Older reports and their heap totals are historical evidence. They do not
give the current sizes below. This document implements no runtime change. Evidence is in
`/drive-testcomp/opentaint-go-rules/run1-origins-routing-projection-20261021`.

| Current candidate | Latest reached | Latest COMPLETE | Accepted forward |
| --- | --- | --- | --- |
| OWASP | run-3 TIMEOUT | run-2 | run-1 |
| conductor | run-2 OOM | run-1 | run-1 |
| thingsboard | run-1 OOM | none | none |
| Stirling-PDF | run-2 OOM | run-1 | run-1 |

The run-1 engine times are 368.811, 88.047, 389.906 before OOM, and
45.242 seconds, respectively. Only Stirling-PDF meets the 60-second target.
The four candidate commands fail a resource gate. Fresh main completes
three projects; thingsboard main times out in prescan. This is a comparison
of different cores, not an isolated speed measurement of the last patch.

## Priority by expected memory impact

| Rank | Candidate | Current evidence and expected effect | Confidence |
| --- | --- | --- | --- |
| 1 | Allocate conclusion containers only when used | Millions of empty family/layer containers across all stores. Current layout estimates are about 0.67 GB on conductor run-2 and 1.15 GB on Stirling-PDF run-2. No facts need removal. | High for the object pattern; estimates need the new JVM layout and object lifetimes checked. |
| 2 | Admit summary answers before retaining publication wrappers | Avoid repeated catch-up and selected offers. Pending lists retain 69.6 MB on conductor run-2 and 744.7 MB on Stirling-PDF run-2. Also avoid payload traversal and downstream offers. | Repeated source path is proved; its share of the retained batch is unmeasured. |
| 3 | Store only the remaining field-interest groups | Thingsboard run-1 field pending maps retain 750.3 MB. About 313.8 MB of wrapper/region payload can be removed from the measured cohort, before obsolete wanted-vector savings. | High for the narrow source/effect mapping; not an achieved byte reduction. |
| 4 | Avoid redundant cleaner projection and the selected GROUP no-op | Avoid one known duplicate walk per audited TAINT callback and selected-mark cleaner setup. This targets churn and run-1 time. | Source-proven local paths; retained-memory impact is unmeasured and likely lower. |

GB and MB use decimal units. These are predictions from one current
capture per OOM project, not measured wins or guaranteed OOM fixes. Do not
add overlapping retained sets or predictions from different projects.
No candidate yet proves the 60-second run-1 target.

Prefer avoiding work when its redundant part is established. Candidate 2
does that. Candidate 1 ranks first because its broad empty-object pattern
is measured, while the duplicate fraction of candidate 2 is unknown.
Candidate 3 keeps required pending recipient identities but removes data
the field answer path never uses. A more ambitious writer-acquisition
algorithm remains unready: its exact sharing quotient is unavailable.

## Scope and semantic contract

These candidates must preserve the current analyzer relation after complete
closure. They must not change the approximation to obtain a speed result.
Keep every contributing run-1 origin and the exact complete all-ZERO test.
Keep full sink witnesses, later FULL ND, source geometry and rule identities.
Keep mark groups, primitive tracking, field transfer, overwrites, exclusions,
aliases, layers, limits, static matching and the three disjoint static sets.
Keep report admission and the `1,1,2,...` iteration schedule.

Changes to duplicate offers, work order and batch-dependent diagnostic
counts are permitted. Changes to semantic report/source/sink identities
are not. A resource-limited run can reach a different frontier, which must
be reported rather than called semantic equivalence.

No change to the IR, reference manager, weak cache, writer grounding or
field approximation is proposed. Follow [proof-guide.md](../../spec/proof-guide.md)
before implementation. Each candidate needs a constructive model and
independent differential controls at its actual owner boundary.

## 1. Allocate conclusion containers only when used

**Current:** each ConclusionGroup allocates both family maps and its REACH
array. Each FLOW/TAINT group allocates an outer layer array and two lists,
including the unused family and layer containers.

**New:** allocate a family map and layer list on first insertion. Use
in-object REACH bits and named nullable NORMAL/DEMAND fields. Keep the
existing admission, annotation replacement, subsumption and interning code.

**Why:** avoid objects which represent no data. This reduces memory for
required facts, rather than dropping facts. It is a representation change;
it does not remove the analysis workload which created the facts.

```java
Object relay(Object x) { Object y = x; return y; }
```

```text
Run-2: return/./T -> y/./T, DEMAND
Before: unused FLOW map; TAINT map; false/false REACH array
        layer array; empty NORMAL list; populated DEMAND list
After:  FLOW=null; TAINT map; REACH bits=0
        NORMAL=null; populated DEMAND list
Same fact, layer, add delta and borrowed view.
```

```kotlin
private var reachBits = 0
private var flow: HashMap<Base, FlowGroup>? = null
private var taint: HashMap<Base, TaintGroup>? = null

// Inside a TAINT group; no lazy delegate and no extra layer wrapper.
private var normal: ArrayList<TaintTree>? = null
private var demand: ArrayList<TaintTree>? = null
private fun writeValues(layer: Layer) = when (layer) {
    NORMAL -> normal ?: ArrayList<TaintTree>(1).also { normal = it }
    DEMAND -> demand ?: ArrayList<TaintTree>(1).also { demand = it }
}
```

Read access must not allocate. A borrowed sequence created before first
insertion must still observe that insertion when consumed. Preserve generic
mixed-family use, enumeration order, both layers and actor mutation rules.
Do not infer that every generic ConclusionGroup uses only one family from
MethodEdgeStore's narrower K6 invariant. Verify current JVM layouts and
container populations before giving a byte estimate. Singleton/promotion
storage is a separate, more complex design.

The current global census has 4,866,764 empty family maps on conductor
run-2 and 8,294,954 on Stirling-PDF run-2. One map is 48 bytes. Each
ConclusionGroup also has a 24-byte REACH array. A Flow/TaintGroup currently
uses a 24-byte outer array; two named fields increase its own assumed
layout from 24 to 32 bytes, for a net 16-byte saving per group before
unused lists. An empty list and its allocated one-cell array use at
least 48 bytes.

```text
Illustrative layout estimate for the current physical populations:
  48 * empty family maps
+ 24 * all ConclusionGroups
+ 16 * all Flow/TaintGroups
+ 48 * observed empty layer lists
```

Use only available layer counts. The empty NORMAL FLOW-list query failed
on both projects, so that term is omitted. The resulting estimates are
666,259,344 bytes for conductor and 1,153,941,072 for Stirling-PDF. These
count distinct physical object families, not overlapping retained-owner
unions. Lists temporarily empty during T2 replacement can have been
allocated already; first-use allocation alone does not remove those lists.
The estimate is therefore a layout scenario, not a guaranteed lower bound
or complete-run saving. Keep lists after first use; do not add deletion
or promotion without a separate proof. Verify the new JVM layout.

Keep the current `HashMap(2)` and `ArrayList(1)` constructors. A borrowed
view must look up a nullable field when consumed, rather than capture its
current null value:

```kotlin
fun all(): Sequence<TaintTree> = sequence {
    normal?.let { yieldAll(it) }
    demand?.let { yieldAll(it) }
}
```

## 2. Admit summary answers before retaining publication wrappers

**Current:** a fresh later-run input query opens all raw summary payloads
and offers all permitted answers again. `queuePublications` appends each
offered piece to an ArrayList. Selected guidance also goes through
`summaries.addDemand`, but its deduplicated delta is ignored. Global
publication storage removes repeats after the method queue-empty barrier.

**New:** use two independent work reductions. Open old payloads only when
their premise first becomes permitted. For a selected publication, enqueue
only the delta returned by the existing selected-guidance admission. Keep
unselected NORMAL publication on its current path.

**Why:** reject repeated work before payload enumeration, wrapper allocation,
and pending retention. The first reduction avoids production. The second
rejects duplicate selected offers from any producer. Their savings overlap;
do not add two estimates for the same discarded publication.

```java
Object id(Object p) { return p; }
void a() { sinkT(id(sourceT())); }
void b() { sinkU(id(sourceU())); }
void c() { sinkV(id(sourceV())); }
```

```text
Backward run-2, limit 1:
  P_T = return/./T; raw answer P_T -> arg0/./T, DEMAND
  P_U = return/./U; raw answer P_U -> arg0/./U, DEMAND

Before:
  admit T: offer T
  admit U: offer T again, then U
  admit V: offer T and U again, then V
  pending can retain the repeated Publication objects until the barrier

After:
  admit T: catch up T
  admit U: catch up U
  admit V: catch up V
  a repeated selected T offer: addDemand returns no delta; enqueue nothing
  a later fresh T answer: keep the ordinary raw-summary delta path
```

### Catch up only newly permitted premises

`StrongQueries.permits(P)` currently reads only complete canonical premise
membership. It does not inspect the conclusion. Queries grow during a run.
`admit` installs the whole query batch and returns its fresh initials.

For nonzero P, permission changes from false to true exactly when P is
permitted after admission and contains a fresh member. Backward ZERO already
publishes without a query. Forward ZERO becomes permitted with the first
nonempty query batch. Retain those separate cases.

```kotlin
val hadQuery = currentQueries.permits(ap.zero)
val starts = currentQueries.admit(added, guides) // unchanged normalization
if (starts.isNotEmpty()) {
    val fresh = starts.toHashSet()
    for (p in summaries.rawPremises()) { // proposed raw-key view
        val changed = if (p.isZero) forward && !hadQuery
            else currentQueries.permits(p) && p.hasMemberIn(fresh)
        if (changed) for (facts in summaries.borrow(p))
            queuePublications(p, facts)
    }
    starts.forEach(::addInitial)
}
```

Keep `ops.limit`, actual-leaf query formation, guide intersections and
canonicalization unchanged. Raw-key iteration must not first open each
ConclusionGroup. This variant still scans premise headers per batch. It
does not add a permanent reverse query index.

### Use the selected delta at the queue boundary

```kotlin
private fun queuePublications(p: PremiseKey, facts: Facts) {
    val selected = config.run1 || ops.demandPart(p, facts, direction) != null
    val pieces = published(p, facts) // keep existing restriction
    for (piece in pieces) {
        cancellation.checkpoint()
        val offered = if (selected) summaries.addDemand(p, piece) else piece
        if (offered != null) pending += Publication(p, offered)
    }
    if (pieces.isNotEmpty()) requestWork() // retain current scheduling
}
```

This is not a general publication ledger. Some unselected NORMAL answers
must publish without entering selected guidance. Do not omit those answers.
Use the returned T4 delta verbatim, including a whole annotation replacement.
Do not replace it with scalar leaf subtraction.

The proof must maintain `D <= P union Q`, where D is selected guidance,
P is globally published content and Q is secured pending/detached content.
The counted active offer retains its delta until append. The raw-add path
must continue to publish every future raw delta. A permitted row's old
content must already be absorbed by P or Q; Boolean permission alone is
not that proof.

`flushPublications` must restore the detached saved deltas directly on its
existing failure path. Do not re-admit them through `addDemand`, which
would treat them as answered and drop them. Preserve subscribe-before-link,
late subscriber replay, selected guidance, cancellation and counted Work.
The current terminal partial-notification failure is not a new retry API.

Controls must cover both layers, ZERO in both directions, complete ND
permission, query-before-row and row-before-query, T2 field/mx replacement,
guide-restricted old content, new raw growth, duplicates and failed flush.

Current source audits are in the artifact directory:
`validation/run2-query-catchup-audit` and
`validation/selected-publication-delta-audit`. Stirling-PDF run-2 samples
show summary iteration and `addLink`; samples do not count replay visits
or prove that every pending wrapper came from this path.

## 3. Store only the remaining field-interest groups

**Current:** `FieldState.pending` stores `Key(owner,id) -> InterestRow`.
Each row owns its remaining groups and a full original registration,
including another owner/id, region/path and original wanted snapshot.
After partial answers, the original wanted snapshot remains reachable.

**New:** use `LinkedHashMap<Key, GroupSet>` for field pending interests.
The value is the remaining wanted set. Keep full class-interest rows.

**Why:** the field answer path reads only the registration owner/id,
which the Key already stores. It always replies with `state.region`.
The original registration and region are not required for future answers.
This avoids unused retained payload without changing request propagation.

```java
void consume() { sink(C.f); } // Two configured sink rules request G_T and G_U.
void produceT() { C.f = sourceT(); }
// No U writer has arrived yet.
```

```text
Run-1: read point k awaits {G_T,G_U} for canonical field C.f.
After G_T arrives:
Before: Key(k) -> row(registration(region=C.f,wanted={G_T,G_U}),
                     remaining={G_U})
After:  Key(k) -> {G_U}
Same answer G_T at point k; same req/reach/processed transitions.
After G_U arrives: answer G_U and remove the same key in both versions.
```

```kotlin
private class FieldState(val region: Pattern) {
    var req = GroupSet.EMPTY
    var reach = GroupSet.EMPTY
    var processed = GroupSet.EMPTY
    val pending = LinkedHashMap<Key, GroupSet>()
}

// Keep request routing, three-set transitions and writer effects unchanged.
if (!pendingGroups.isEmpty) {
    val key = Key(interest.owner, interest.id)
    state.pending[key] = state.pending[key]?.plus(pendingGroups) ?: pendingGroups
}

// Keep groupWrite's preceding fresh/answered computation unchanged.
val iterator = state.pending.iterator()
while (iterator.hasNext()) {
    val entry = iterator.next()
    val selected = entry.value intersect answered
    if (!selected.isEmpty) {
        entry.setValue(entry.value - selected)
        out += PointReady(entry.key.owner, entry.key.id,
            RegionGroups(state.region, selected))
    }
    if (entry.value.isEmpty) iterator.remove()
}
```

Keep LinkedHashMap order: replacing a value must not move its key. Keep
one map per canonical field, immutable GroupSet operations and current
synchronization. Update only the field part of pending-membership
statistics from `row.wanted.size` to `value.size`. Full class interests
must keep their registration region for overlap matching. Broad supplies,
class requests, global class markers and per-writer admitted history
retain their current path.

The preservation relation is an ordered-map isomorphism:

```text
same region, req, reach, processed and ordered keys
new.pending[k] = old.pending[k].wanted
k.owner/id = old.pending[k].registration.owner/id
```

Insertion establishes the owner/id equality. Duplicate requests only
union wanted sets. Each group arrival selects and removes the same groups
and emits the same ordered owner/id/region replies. Other service state
and writer effects remain identical. This proves the local transition
mapping at source level. Before implementation, formalize the transitions
constructively and compare complete ordered effects and statistics with
the current service for both arrival orders, repeats, partial answers,
multiple fields, broad supplies and class requests.

Thingsboard run-1 has 2,064,639 field rows. The retained-map census gives
one each of InterestRow, StaticInterest, PathFact, Pattern, path-list
wrapper and two-element path array per row: 313,825,128 bytes. The map
also retains more GroupSets than current rows, because old original
wanted vectors survive after progress. Those extra vectors are not
included in that estimate. Shared objects remain live wherever another
owner holds them. Keys, map entries, current groups and recipient count
remain. Do not claim the whole 750.3 MB disappears or that this removes
the underlying writer-recipient multiplication.

The source audit and pinned ranges are in
`validation/static-field-pending-payload-audit`. The measured cohort is
from the closed `heap-followup-v2` F13 view.

## 4. Avoid redundant cleaner output projection

**Current:** `ops.clean` sends its result through `run1Policy`, which
projects and covers it. Both immediate cleaner consumers call `collect`,
which projects that result again. Reusing the canonical result wrapper
still walks its trie with fresh traversal scratch.

**New:** use the existing `collectAfterRun1Policy` helper at only the two
audited immediate `ops.clean` callbacks. Preserve its layer demotion,
second cover, exact OperationInput and mark-request routing.

**Why:** omit one known redundant GROUP walk per TAINT result callback.
Reach and FLOW results already avoid that walk. No new fact
token, manager field, global cache or general trusted-Facts API is needed.
Unknown producers still use ordinary `collect` and receiver canonicalization.

```java
Object x = source(); // group G in run-1
removeSelectedMark(x, T);
sink(x);
```

```text
I -> x/G, DEMAND
Before: clean -> run1Policy(project,cover) -> collect(project,demote,cover)
After:  clean -> run1Policy(project,cover) -> trusted callback(demote,cover)
Same mark group, paths, layer, requests and downstream effects.
```

```kotlin
core.ops.clean(input.facts, input.premise, cleaner, core.mode,
    core.collectAfterRun1Policy(at(input, step, CLEAN), unchangedSink))
```

Apply this only to the Clean and Field-CLEAN callbacks in
RunMethodCallServices. Keep each callback's existing sink closure. Do not
skip the Field READ/WRITE phases, static MarkInput, static write publication
or unknown inputs. FULL execution remains unchanged. Update the helper's producer-contract
comment to name exactly these two additional callbacks.

A separate local no-op is also source-proven: selected-mark cleaning of a
run-1 GROUP TAINT fact has `gone(marks)=EMPTY` and returns the same fact.
It can return through the unchanged outer policy before constructing
TaintClean/Results. Keep the cleaner entry cancellation checkpoint and base
dispatch. FLOW still excludes/requests the group. RemoveAllMarks,
structural field cleaning and FULL behavior retain their current rules.

```kotlin
is TaintTree -> if (mode.run1 && mode.markMode == GROUP && cl.mark != null)
    out.result(c) // out still has ApOps.clean's run1Policy wrapper
else TaintClean(m, c, cl, mode, out).run()
```

Prove the two changes separately. Test foreign managers, equal uninterned
leaves, mixed FULL/GROUP input, exact/any leaves, field cover, static premises,
requests, both cleaner phases, RemoveAll and FULL. There is no valid empty
TAINT root to optimize. OWASP's whole TAINT-cleaner ancestry is only
3.31% of run-1 samples and 5.87% of allocation weight; the selected subset
is at most this total and is unmeasured. Do not credit this no-op with the entire cleaner
chain or canonical-projection share.

## Future work which is not ready for selection

**Share static writer input acquisition.** For a method which reads C.b
and writes C.a and C.c, both output writers can await the same source
initial/group. The service offers each group to each writer row at most
once, but retains both recipients. Acquiring once per complete source initial
and group, then borrowing current outputs, could avoid dependent waiters.
The current heap has 1,614,699 WriterInputs, but five tuple queries failed.
The exact duplicate quotient is unknown. Do not treat the whole WriterInput
population as removable.

This algorithm needs a complete late-output/late-request replay rule,
current-selector checks, annotation replacement, overlapping spatial
routes and the unchanged per-writer admission suppression. A sketch
which removes a source acquisition after one answer is insufficient:
later outputs must still receive an already available answer. Keep this
as a separate future design, not an implicit part of candidate 3.

**Form and provider retention.** Current profiles show form building and
projection churn; Thingsboard's provider retains about 1.277 GB under
base-provider/configuration ownership. That owner includes strong resolved
FULL-rule caches and other rule/index metadata. Its per-cache byte split
is unavailable. Neither a form-build counter nor the whole provider
retained set measures redundant live forms. A cache redesign
needs an owner/eviction contract and a separate proposal. No reference
manager or weak identity cache change is authorized here. IR is out of
scope. Source findings about unused generic read IDs and per-leaf call
continuations also need separate cardinality and behavior evidence.

## Separate correctness work

The current strict gate has 14 thingsboard omissions after OOM before its
first report and two unmatched Stirling-PDF return coordinates. The latter
involve synthetic-exit sink witnesses and Simple export's last-return
fallback. This is not a primitive exporter-only fix: current live edge
storage has no return derivation links and freeze drops it before reporting.
A future design must preserve or recover supported return provenance.
It must not move sink evaluation, mark all returns, or alias comparison
coordinates to make the gate pass. Keep this semantic reporting change
separate from performance work. Exact missing-flow coverage is unproved.

## Validation for a selected iteration

First prove the selected local laws constructively in Lean. Then write
independent scalar/current-code controls, implement only the selected
scope, and rerun the existing gates. Preserve all-origin and complete
ZERO regressions. Each new runtime path needs a clear ownership contract.

Repeat the same eight project/configuration commands after implementation.
Keep memory/time settings fixed and use Gradle runProjectAnalyzer directly.
Compare full strict reported coordinates with fresh main. Report every
run's engine time, latest reached/COMPLETE/accepted iteration, resource
failure, profile phase and actual retained owners. Count offered/fresh
selected publications and skipped catch-up payloads if a measurement
counter is designed; do not equate scheduler tickets with scalar facts.
Do not broaden the runtime scope to satisfy the 60-second target.
