# Run-1 without static-to-static summaries

Date: 22 October 2026. Status: **selected design; not implemented**.
Reference runtime: `30c1b84606695070a79d7ae467703267b9f931c1`.
Read the [proof dossier](run1-static-summary-erasure-proofs.md) and
[proof guide](proof-guide.md).

## 1. Purpose and comparison

Do not store or propagate a run-1 method summary whose nonzero input and
output are both static. Keep its required producer effects. At the
run-1-to-run-2 boundary, replace the removed explicit demand pairs with
query completion on actual run-2 inputs.

The required property is this:

> For each finite supported run-2 derivation of the reference procedure,
> construct a derivation of the new procedure with the same FULL mark,
> source hit, and sink/rule occurrence. A removed run-1 static demand pair
> must not prevent that derivation.

This is relative preservation within the grammar in section 3. It is not
equality of run-1 summary tables. It is not a proof that the reference
analyzer is sound for every program. Compare complete semantic closures,
not the prefixes of an OOM or timeout. Sections 12 and 13 separate checked
models from production adapter obligations.

This document supersedes the per-method static/static relation-preservation
requirement of [the previous protocol-removal spec](run1-static-group-protocol-removal.md)
for this selected design only. That spec still describes the implemented
runtime. All unmodified behavior comes from that runtime and
[the grouped-static contract](grouped-run1-statics.md).

## 2. Selected semantic changes

| Change | Current | New | Required proof |
| --- | --- | --- | --- |
| Run-1 static/static relations | Retain raw rows, publish them through callers, and hand off their selected pairs | Execute producer effects, then erase all these rows | Producer-effect preservation and finite relay compression |
| Run-2 static guidance | Old concrete guide intersections can add starts to the actual-input start | Complete each actual named-field FULL start with its same-field EXACT intersection | Exact old-start coverage, followed by ordinary FULL witness replay |
| Executable concrete static grammar | The general AP API accepts broad root/class values | In run-2 also, concrete statics must be named fields or EXACT class markers | Producer and consumer grammar closure |

The third row is an explicit user-selected restriction. A field limit of
one does not imply it. A general AP constructor must not be mistaken for
a proof of this grammar.

The following behavior must remain unchanged:

- Rule groups, owner IDs, FULL marks, primitive tracking, condition
  specialization, source actions, sink identities, and report admission.
- Selected-mark cleaners request their group and cannot kill in run-1.
  `RemoveAllMarks` can kill. Keep the current exclusion/complement repair.
- Field transfer. For `x.f = y`, an `x.*` keep edge moves to DEMAND with
  empty field exclusion; `x.f.*` is killed; `x.g.*` remains. Do not turn
  these rules into weak updates or add writer-path validation.
- All contributing run-1 origins and the exact all-ZERO cube rule.
  This change does not allow one satisfied origin to satisfy an ND cube.
- Ordinary nonstatic requests, static local FLOW writers, global
  `req/reach/processed` transitions, and exact class-marker handling.
- ZERO source effects, exit rules, controller annotation checks, actual
  call ports/stages, alias classification, later strong updates and FULL
  summary application.
- The schedule: run-1 forward, GROUP, limit 1; run-2 backward, FULL,
  limit 1; run-3 forward, limit 2; later limits increase as before.
- `trackFactsReachAnalysisEnd` is empty. Keep the existing
  `disableRun1Statics` gates. No Spring exit rewrite or ZERO-only exit
  restriction is part of this design.

Do not add GroundingSupport, a new source/GEN eligibility test, IR changes,
reference-manager changes, or a weak cache. An extra exact start can change
work and candidate precision. Do not describe the whole change as a
representation-only optimization.

## 3. Vocabulary and executable grammar

`S` is the static base. `C.f` is a named JVM static field. The class
accessor does not consume field budget. `C` alone can identify an exact
global marker; it does not mean all fields of C.

```text
FLOW       abstract mark, possibly with its existing mark exclusions
G          run-1 group (topLevelRuleId, *)
T          one actual FULL mark (topLevelRuleId, fullMark)
EXACT      position only, written '.' or '$' in older documents
ANY        bounded may-tail, written '[any]'
MUST       current ANY_TAINT tag; distinct from ANY even at DEMAND
STAR       correlated abstract tail, written '*'
E          field exclusion set
```

Run-1 still has empty E. Concrete GROUP edges are DEMAND. A field
initial descriptor can be `S.C.f/STAR/G`; materializing it gives the
existing `S.C.f/ANY/G` current fact. A legal exact-field fallback is not
removed. Exact class inputs remain exact. Keep the current distinction
between an initial descriptor and its current fact.

At the run-2 query boundary, after the existing field bound and normal
form, an executable concrete static input has exactly one of these forms:

```kotlin
sealed interface StaticFullInput {
    data class FieldExact(val cls: ClassId, val field: FieldId, val mark: FullMark) : StaticFullInput
    data class FieldAny(val cls: ClassId, val field: FieldId, val mark: FullMark) : StaticFullInput
    data class ClassExact(val cls: ClassId, val mark: FullMark) : StaticFullInput
}
```

These query initials have empty E under the existing AP normal form.
Backward MUST initials are still forbidden. This does not change the
exclusion rules for other later-run facts.

The following executable concrete inputs are invalid in this profile:

```text
S/ANY/T            broad marked static root
S.C/ANY/T          class-wide marked input
S.C/STAR/T         class-wide marked input
```

FLOW roots/classes, symbolic GROUP demand patterns, and relation
descriptors are not executable broad FULL inputs. Keep those descriptors
where the current algorithm needs them. In particular, do not ban the
implicit root guidance pattern or reinterpret an exact class marker as a
field supplier.

This is the selected JVM static profile. Other frontend encodings and
arbitrary raw AP values need their own adapter proof. They are not covered
by silently treating a one-accessor global field as a JVM class marker.

Validate the grammar at producer boundaries. Prove that field cuts keep
the named-field anchor. Check source/GEN targets, aliases, first caller
bindings, reversed records, native replay and sink seeds. If a configured
producer violates the grammar, do not drop its output or guess a field.
Report the unsupported input and resolve the semantic incompatibility
before implementation. Section 12 gives the complete adapter inventory.

## 4. Exact erasure predicate

Test the complete relation, not only the current output or one member.

```kotlin
fun eraseRun1StaticSummary(run: Run, original: Premise, result: Facts): Boolean =
    run.isRun1 &&
    result.base == STATIC &&
    !original.isZero &&
    original.members.all { it.base == STATIC }
```

Current run-1 production uses singleton contributing-origin arms. The
predicate also states the correct reusable contract for a complete
premise. It does not introduce new run-1 ND objects.

Erase FLOW and GROUP rows, same-group and changed-group rows, identity
rows, named-field rows and exact class-marker rows. Do not limit the
test to canonical STAR field initials. Do not erase a mixed dependency
merely because one member is static.

| Relation | Run-1 action |
| --- | --- |
| static -> static | Required producer effects, then erase |
| ZERO -> static | Retain and publish; preserve actual source supply |
| nonstatic or mixed -> static | Retain ordinary binding and publication |
| static -> nonstatic, return, or control | Retain and publish |
| nonstatic -> nonstatic | Unchanged |
| Any relation in run-2 or later | Unchanged |

ZERO is not a static input. An erased row must not enter the raw summary
store, selected demand store, publication index, pending publication
batch, handoff pair list, or persisted native records. Do not recreate
those rows as a permanent global relation directory.

## 5. Producer effects before erasure

The order below is normative. Names are pseudocode, not a proposed change
to the AP transfer relation.

```kotlin
fun localOperationResult(point: Point, input: OperationInput, raw: Facts) {
    val value = existingBoundedRun1Result(input, raw) ?: return
    if (existingStaticAblationDrops(value)) return

    // This is a real, reached micro-edge. Calls never create local writers.
    localStaticEffects.registerWriteAndFlowWriter(point, input.original, value)
    computedStaticEffects.accept(input.original, value)

    retainAfterEffects(input.original, value)
}

fun ordinaryCallResult(binding: FullCallBinding, raw: Facts) {
    val value = existingBoundedCallResult(binding, raw) ?: return
    if (existingStaticAblationDrops(value)) return

    // A first ordinary binding can be the first actual GROUP supply.
    computedStaticEffects.accept(binding.callerOriginal, value)
    retainAfterEffects(binding.callerOriginal, value)
}

fun retainAfterEffects(original: Premise, value: Facts) {
    if (eraseRun1StaticSummary(run, original, value)) return
    existingRetainAndPublish(original, value)
}
```

Run only the existing applicable computed-supply branch. A hypothetical
nonstatic input alone cannot supply the global pool. Preserve the entire
ordinary call binding before testing erasure. No new actual-writer
validation or support formula is added.

Keep service admissions idempotent. A missing summary insertion delta no
longer tells the caller whether it has secured a local write or supply.
After this split, the retention function must not run those effects again.
Write-region admission must still occur before group dedup where required
by the current service. Preserve the complete region, tail, E, FLOW mark
exclusions and output group. Equal `(field, group)` endpoints alone are
not sufficient to prove equal read activation.

### 5.1 FLOW writers

The existing local descriptor is sufficient:

```kotlin
data class FlowWriterKey(
    val original: Initial,
    val output: ScalarPattern,   // complete AP, FLOW mx, E and tail
    val layer: Layer,
)
```

The service retains a reached write region and a unique local writer.
Keep the existing method-owned dedup key above. A reached operation is
the admission witness; do not add its statement ID as a new key dimension.
It admits each required writer/group request as before. `publishMatched`
can reconstruct the FLOW scalar from this immutable descriptor. It must
not borrow an erased method summary. A nonstatic writer still requests
the group on its nonstatic original through the ordinary request path.

### 5.2 GROUP effects and the delayed fallback

Keep the existing canonical-input reducer for a GROUP original. It must
preserve cross-group source transfer, EXACT/ANY/MUST output tags and
exact class supplies.

The old fixed-GROUP fallback can borrow raw summaries at reply time.
That lookup cannot survive erasure. An unanswered fallback must own its
immutable scalar payload:

```kotlin
data class PendingInputJoin(
    val original: Initial,
    val scalar: ScalarPattern,
    val layer: Layer,
    val inputInterest: InterestKey,
)

fun answer(join: PendingInputJoin, actual: Facts) {
    val results = existingScalarSupplyReduce(join.original, join.scalar, actual)
    publishComputedEffects(results)
    removeAnsweredJoin(join)
}
```

Keep the existing input selector and AP reducer. Deduplicate pending
joins by their complete immutable identity. Remove an answered join.
Do not keep all answered outputs or add a second permanent summary
store. This is the storage repair for an existing pending input join,
not a new condition on source actions. Empty-E scalar persistence in the
reference store justifies saving its scalar payload instead of borrowing
the old root later.

Exact class markers use their current point path. They do not register
field FLOW writers. Preserve their computed-supply and interest path.

### 5.3 First binding versus pure relay

```java
void set(Object p) { C.out = p; }
void caller() { set(C.in); }
```

Keep the callee's `arg0/G -> C.out/G` relation and its ordinary caller
subscription. Applying it can create the first actual
`C.in/G -> C.out/G` result in `caller`. Secure the `C.out/G` effect, then
erase that caller row. Do not create a caller write point. Do not replace
its original by ZERO.

By contrast, applying an already-static callee summary with a static
output only promotes the result. It does not run a caller suffix. After
erasure there is no such publication to relay. The local writer or first
ordinary binding remains the producer. The scalar AP projection proof
must show that repeated canonical relay cannot create a new scalar
region/group effect beyond that producer (including changed groups and
MUST tags). Section P2 of the proof dossier gives this argument.

## 6. Stores and ownership

| Owner/store | Contents after the change | Why it remains |
| --- | --- | --- |
| Method ordinary fact/work stores | Existing nonstatic/control relations and work | Discover operations and execute ordinary transfers |
| Method raw and selected summary stores | Only relations outside the erasure predicate | Calls, retained guidance and reports |
| Method local FLOW writer store | Reached local immutable writer descriptors | Answer present/future group interests without raw summary lookup |
| Method pending input joins | Only unanswered complete scalar bindings | Preserve delayed fallback replies |
| Global static service | Existing reached write regions, FLOW writers, disjoint req/reach/processed sets, read waiters and exact class state | Match real group supplies to local consumers |
| Ordinary call subscriptions | Actual caller input, original, port, stage, layers, alias classification and suffix state | Preserve first bindings and mixed/nonstatic results |
| Rule/report stores | Existing sink occurrences, source identities, rule witnesses | Preserve run-2 seeds and observable results |
| Handoff DemandStore | Retained explicit pairs plus constant-size static query-completion policy | Guide run-2 without static/static pair materialization |
| Run-2 StrongQueries | Interned actual query identities, including same-field completion | Authorize ordinary FULL results |

Do not retain a method-by-field-by-group copy of the erased relation.
The shared policy contains no field catalog, full-mark list, pair cache,
or stored root for each old method. Actual later query identities remain
method-owned and deduplicated, as they are now.

## 7. Run-1 pipeline and barriers

1. Reach the local operation through existing control/ordinary analysis.
2. A reached static read waits for a corresponding published write as
   before. A read alone does not create its write or GROUP supply.
3. Apply the actual static-read micro-edge; continue its nonstatic result.
4. Register reached local FLOW writers. Deliver pending/future interests
   through the same global service. Never register call-created writers.
5. On an actual GROUP answer, execute the saved point and unchanged AP
   transfer. Process computed effects before erasing a static result.
6. Publish retained static-to-nonstatic and other ordinary summaries.
   Calls apply these with their exact saved caller/stage.
7. Flush retained publications at the existing method-queue barrier.
   Keep pending static effect batches and service sends counted as work.
8. Complete the run only after required admitted work drains. Clear
   run-1-only owners after the complete handoff/report barrier.

Keep request-before-write, write-before-request and late-writer behavior:

```text
request G, no write G : req += G; keep consumer; ask relevant FLOW writers
write G, no request  : reach += G
request G in reach  : reach -= G; processed += G; answer; ask writers once
write G in req      : req -= G; processed += G; answer pending consumers
new FLOW writer    : ask for req + processed; do not ask for reach alone
```

Keep exact class interests separate. A request/supply must preserve its
operation, method, group and complete selector. Do not call an actor
under the service lock. Keep cancellation checkpoints. A method with no
retained summaries can still own required counted work.

## 8. Handoff and run-2 query completion

### 8.1 Handoff

At COMPLETE run-1, retain ordinary handoff normalization for every kept
pair. Keep explicit ZERO exits distinct from absent exits. Keep all sink
seeds with their original FULL predicates and rule occurrences. Persist
normal records under the existing rules. No erased row may reappear by
reading a second raw store.

Attach one policy to this incoming run-2 demand:

```kotlin
enum class StaticQueryPolicy { ORDINARY, COMPLETE_RUN1_STATIC_FIELDS }

val nextDemand = buildRetainedDemandPairsAndSinkSeeds()
    .withStaticQueryPolicy(COMPLETE_RUN1_STATIC_FIELDS)
```

The existing implicit GROUP guidance family can remain as metadata. It
does not enumerate FULL marks. The new policy replaces removed
static/static starts; it does not create a concrete input from FLOW.
Do not copy it automatically into the run-2-to-run-3 demand store.

### 8.2 Actual inputs are mandatory; guides are additive

Retain the existing actual-input projection and concrete-guide
intersections. Complete a bounded, canonical actual static FULL input:

```kotlin
fun complete(actual: StaticFullInput): List<Pattern> = when (actual) {
    is FieldExact -> listOf(actual.pattern)
    is FieldAny -> listOf(
        actual.pattern,
        FieldExact(actual.cls, actual.field, actual.mark).pattern,
    )
    is ClassExact -> listOf(actual.pattern)
}

fun admitActual(input: Facts, retainedGuides: Sequence<DemandPattern>) {
    val batch = existingActualAndGuideIntersections(input, retainedGuides)
    if (incoming.staticQueryPolicy == COMPLETE_RUN1_STATIC_FIELDS) {
        for (actual in boundedCanonicalStaticFullLeaves(input)) {
            validateNamedFieldOrExactClass(actual)
            for (pattern in complete(actual)) {
                // Use the existing scalar emitter and initial interner.
                emit(pattern, actual.pattern)?.let { batch += ap.initial(it) }
            }
        }
    }
    installWholeBatchBeforeStartingFreshInitials(batch)
}
```

The extra EXACT query is an actual AP intersection at the same field.
It is not a summary with an invented ZERO original. It keeps the actual
FULL mark T, not only its owner group. Two different FULL marks in one
group stay different. Do not enumerate group members or serialized marks.

FLOW inputs keep their existing ordinary query and exclusions. They do
not gain T from a catalog or from a static pool group. A class marker
gets one EXACT query and cannot supply a descendant field.

Keep complete-premise authorization:

```kotlin
fun permits(p: Premise): Boolean =
    if (p.isZero) admitted.isNotEmpty()
    else p.members.all { it in admitted }
```

This only authorizes computed ZERO outcomes; it does not start a new
ZERO analysis or satisfy a static premise with ZERO. Preserve current
summary backfill when a premise first becomes permitted. Optimizing
repeated backfill is a separate proposal.

### 8.3 Ordinary FULL analysis after admission

Run the existing backward operations from these starts. Build real
run-2 static/static summaries. Apply them only when an actual matching
static input exists. ZERO satisfies a static summary premise **only in
run-1**. Keep call order, ports, stages, strong updates, cleaners, source
actions and all-member ND joins in later FULL analysis.

Current publication already publishes a result with a permitted original
without the old paired-exit restriction. Thus an erased pair's required
extra start is the relevant replacement boundary. Prove exact start
coverage, then replay the same ordinary FULL operations. Do not demand
literal equality of old entry/exit records that this design removes.
Do not add a same-group output veto: an actual H query can produce G
through an ordinary cross-group source transfer.

## 9. Examples and before/after traces

### 9.1 Static copy through wrappers

```java
void copy() { C.h = C.f; }
void wrap() { copy(); }
void main() { wrap(); }
```

After real read activation and G refinement, current run-1 can publish
`C.f/STAR/G -> C.h/ANY/G, D` in all three methods. New run-1 keeps the
local reached writer, request processing and computed `C.h/G` effect.
It stores none of those three rows. No wrapper relay executes.

In run-2 an actual `C.h/ANY/T` demand admits `C.h/ANY/T` and
`C.h/EXACT/T`. Ordinary backward call/field transfer discovers the copy
and produces the real FULL summary. No run-1 method-by-pair index is read.

### 9.2 Setter, getter, dead local and nested consumers

```java
void main() { staticSet(); wrap(); }
void staticSet() { C.f = src(); }
void wrap() { deep(); }
void deep() { Object t = staticGet(); sink(t); }
Object staticGet() { return C.f; }
```

Keep `ZERO -> C.f/G` in `staticSet` and its ordinary caller application.
Keep `C.f/STAR/G -> return/G` in `staticGet`. Keep the local sink witness
in `deep`, even if t is dead after the check. Neither wrapper needs a
run-1 `C.f -> C.f` row. Sink seeds and ZERO control roots remain. When
the backward run reaches a real C.f/T input, it admits that input and
follows the ordinary call and source path. The same argument covers a
consumer which loops after the sink and has no normal-return summary.
Do not add a normal-exit requirement or a ZERO-only exit rule.

### 9.3 Why actual-only admission is insufficient

```text
actual run-2 input          C.f/ANY/T
old removed guide entry    C.f/EXACT/G, owner(T)=G
old extra initial          C.f/EXACT/T
new actual-only initial    C.f/ANY/T       // misses the exact identity
new complete batch         {C.f/ANY/T, C.f/EXACT/T}
```

For a broad raw `S/ANY/T` or `S.C/ANY/T`, that old guide can still emit
the descendant C.f initial. Same-path completion would miss it. These
are executable counterexamples to an unrestricted theorem. The selected
run-2 grammar excludes those broad concrete inputs explicitly.

### 9.4 Changed groups, exact markers and recursion

```text
actual original C.f/G; source transfer writes C.h/H
  keep computed C.h/H; erase C.f/G -> C.h/H
exact global marker C/EXACT/G
  keep its exact-point service path; do not wake all C fields
copy cycle C.f -> C.h -> C.f, no actual GROUP source
  FLOW activation alone supplies no first G
```

Requests and writes can arrive in either order. Keep the same complete
scalar selector and field/marker overlap in both orders. A source-free
recursive call component cannot gain a GROUP by erasing its relays.

## 10. Correctness requirements

The proof dossier must establish these separate properties:

1. **Effects:** each old actual write activation, required writer request,
   computed scalar group supply and retained local consumer outcome has
   a new finite derivation. Include first ordinary caller bindings.
2. **Relay compression:** every erased pure static relay is a finite
   chain from a kept local or first-binding producer. Canonical scalar
   reapplication preserves the complete supply region/group effect.
   A source-free cycle is not a producer.
3. **Storage erasure:** no erased row reaches any raw, selected,
   publication, handoff or record owner. An unanswered join is not an
   answered-row archive.
4. **Query coverage:** for each legal actual run-2 input, every concrete
   static start obtained from an old erased guide occurs in the new
   complete batch with the same full identity. Include exact-field and
   exact class cases and the shared root GROUP guide.
5. **FULL replay:** use those query witnesses and kept seeds/guides to
   reconstruct each finite ordinary run-2 derivation, including call
   application, all-member ND joins, source hits and sink occurrences.
6. **Lifecycle:** required work remains counted, and the existing stock
   iteration policies still enter run-2. Extra exact starts do not
   authorize publication from only one ND member.

No theorem may assume equality of final pool sets, all old demand pairs,
or a complete new demand frontier. Those are conclusions or deliberately
changed representations, not valid starting assumptions.

## 11. Expected work reduction and limits

For k erased scalar relations relayed through h wrappers, remove that
h*k summary-retention/publication/application component. Removed rows
also create zero explicit static/static handoff pairs. No method-level
replacement relation is built.

Per distinct legal actual named-field FULL query, completion offers at
most two initials; an exact class offers one. The existing interner and
admitted set deduplicate them. The policy is constant-size. These bounds
do not bound the number of distinct actual queries in a project, local
writers, pending joins, FULL summaries, or total engine work.

The measured runtime's Thingsboard incoming run-2 demand owner retains
2,683,225,184 bytes. Of 6,956,267 physical DemandPatterns, 5,890,526
(84.68%) have both ends STATIC. STATIC includes roots, markers and
fields; that measurement did not split shapes or groups. It does not
prove that the same fraction of heap or engine time will disappear.
The actual-query policy can add later-run starts at a method which did
not have an exact guide before. Measure the resulting work and precision.

Latest measured results, from the
[reference performance report](../docs/reports/bidirectional-static-protocol-removal-performance-20261022.md):

| Project | Run-1 engine seconds | Latest reached | Latest COMPLETE | Latest accepted forward report |
| --- | ---: | --- | --- | --- |
| Thingsboard | 220.578 | run-2 OOM | run-1 | run-1 |
| Conductor | 18.005 | run-2 OOM | run-1 | run-1 |
| Stirling-PDF | 29.530 | run-2 OOM | run-1 | run-1 |
| OWASP | 239.868 | run-5 TIMEOUT | run-4 | run-3 |

The target is 60 seconds of run-1 engine time. These are baseline results,
not measurements of this design. No new project run was made for this
spec. Existing cleaner, exit-scope, report parity and resource failures
remain failed gates; the relative models do not override them.

## 12. Production correspondence and implementation gates

| Seam in the reference | Required implementation and adapter |
| --- | --- |
| `RunMethodAnalyzer.emitOperationResult`, `retainSummary`, `promoteStaticSummary` | Apply existing bounds/gates; split effects from storage; test complete erasure predicate |
| `Run1PointResume.localWrite`, `computedGroups`, `publishMatched`, `currentValues` | Keep immutable FLOW descriptors; secure computed effects; remove erased-row borrows with unanswered scalar payloads |
| `RunMethodCallServices.applyParts`, `applyStaticSummary` | Keep first ordinary binding and nonstatic suffix; remove pure static-output relay; no call-created local writer |
| AP `Summary.leafKinds`, `EdgeApplication.taint`, satisfying and limit | Scalar canonical projection, including cross-group EXACT/ANY/MUST and exact class cases; preserve tail/region effects |
| `Run1StaticService` | Keep once-admission, req/reach/processed, reached write regions, exact class separation and counted delivery |
| `StrongQueries.admit`, `permits`, `RunMethodAnalyzer.published` | Validate selected actual grammar; add same-field exact intersection; install whole batch; keep all-member permission and actual-input publication |
| Source/GEN, aliases, bind-in/back, reversed/native records, cuts, seed adapters | Prove concrete static anchor and tail closure; explicitly reject unsupported configured broad inputs rather than silently drop them |
| `HandOff`, DemandStore builder and `RecordStore` | Retain mixed/nonstatic/ZERO pairs, seed identities and normal records; attach one boundary policy; do not re-materialize erased rows |
| Work accounting, actor flush/freeze, iteration driver | Keep required service work alive after the last summary; preserve stock policy continuation and report frontier |

Implement against the pinned runtime, not an older hypothetical static
algorithm. Before the runtime patch, pass the constructive models and
audit. During implementation, use focused controls for every adapter:

```text
local FLOW and GROUP writes; pure copy; late write and late request;
selected cleaner mx repair; changed-group source; exact class input/output;
exact-field fallback with raw store empty; first setter binding to static;
dead-local/no-return sink; nested wrappers; multiple and late call ports;
source-free recursion; packed scalar correlation and MUST outputs;
ANY -> EXACT completion; full marks in one group remain distinct;
FLOW cannot manufacture FULL; all ND members required; explicit ZERO exits;
no erased raw/selected/publication/record rows; boundary policy not in run-3;
static ablation; cancellation and counted service work after summary erasure.
```

Check grammar conservation at each listed constructor; a raw AP unit test
alone cannot establish it. Compare complete witness identities, source
hits, seeds and retained demands before comparing performance. The fixed
project cohort must report every reached iteration and the latest
COMPLETE/accepted iteration. Do not claim a twofold gain before measuring it.

## 13. Proof boundary and review gate

The checked Lean models and their exact claims are listed in the
[proof dossier](run1-static-summary-erasure-proofs.md). They construct
producer, query and finite replay witnesses. They do not execute Kotlin
or prove all runtime adapters automatically.

The implementation must discharge the source correspondence gates in
section 12. A discovered need to change field transfer, source/exit
eligibility, mark matching, or later-run calls is a new major semantic
change. Highlight it and obtain the intended behavior. Do not hide it in
a support label, canonicalizer or proof helper.
